package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.DiscordWebhookTest.URL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A level streak's post kept across a client restart (Board 2026-11-14: Strength 36 and 37, sixteen minutes apart
 * inside a 30 min window, were two posts because the client was restarted between them).
 */
public class LevelStreakSaveTest
{
	private static final long SEC = TimeUnit.SECONDS.toMillis(1);
	private static final long MIN = TimeUnit.MINUTES.toMillis(1);
	private static final long WINDOW = 30 * MIN;
	private static final String OTHER = "https://discord.com/api/webhooks/987654321098765432/ZyX-wvu_TSR987";

	private final Gson gson = new Gson();
	private long now = 1_700_000_000_000L;
	private final List<DiscordQueue.Post> sent = new ArrayList<>();
	private final List<DiscordSender.Done> pending = new ArrayList<>();
	private final DiscordSender sender = new DiscordSender((post, done) ->
	{
		sent.add(post);
		pending.add(done);
	}, () -> now, () -> 12);
	private LevelPosts levels = client();

	private LevelPosts client()
	{
		return new LevelPosts(sender, gson, () -> now, () -> DiscordStyle.EMBED, FakeItems::new, () -> null);
	}

	private static GroupEvent level(String skill, int from, int n)
	{
		return GroupEvent.of(GroupEvent.Type.LEVEL_UP, 0, "Alex").text(skill).number(n).previous(from).build();
	}

	private void gain(String url, int n)
	{
		levels.level(url, level("Strength", n - 1, n), WINDOW, null, "first " + n);
	}

	private void tick()
	{
		levels.tick();
		sender.pump();
	}

	private void reply(int status, String messageId)
	{
		pending.remove(0).done(status, null, messageId);
	}

	private static String description(DiscordQueue.Post post)
	{
		JsonObject o = new Gson().fromJson(post.json, JsonObject.class);
		return o.getAsJsonArray("embeds").get(0).getAsJsonObject().get("description").getAsString();
	}

	/** Strength 36 posted as m1, then the client restarts: a new LevelPosts restored from what the old one saved. */
	private void postThenRestart()
	{
		gain(URL, 36);
		tick();
		reply(200, "m1");
		String saved = levels.saved();
		levels = client();
		levels.restore(saved, WINDOW);
	}

	@Test
	public void theNextLevelAfterARestartEditsTheSamePost()
	{
		postThenRestart();
		now += 16 * MIN;
		gain(URL, 37);
		tick();
		assertEquals(2, sent.size());
		assertEquals("m1", sent.get(1).edit);
		assertEquals("Strength 35 → 37 (+2) in 16 min", description(sent.get(1)));
		assertEquals(URL + "/messages/m1", DiscordSender.target(sent.get(1)));
	}

	@Test
	public void aLevelOutsideTheWindowAfterARestartIsANewPost()
	{
		postThenRestart();
		now += WINDOW + SEC;
		gain(URL, 37);
		tick();
		assertEquals(2, sent.size());
		assertNull("a new post, not an edit", sent.get(1).edit);
		assertEquals("first 37", sent.get(1).json);
	}

	@Test
	public void aDifferentWebhookAfterARestartIsANewPost()
	{
		postThenRestart();
		now += MIN;
		gain(OTHER, 37);
		tick();
		assertEquals(2, sent.size());
		assertNull(sent.get(1).edit);
		assertEquals("first 37", sent.get(1).json);
	}

	@Test
	public void aRestoredPostDiscordNoLongerHasIsPostedAnew()
	{
		postThenRestart();
		now += MIN;
		gain(URL, 37);
		tick();
		reply(404, null);
		tick();
		assertEquals(3, sent.size());
		assertNull("posted anew", sent.get(2).edit);
		reply(200, "m2");
		now += LevelThreads.EDIT_GAP;
		gain(URL, 38);
		tick();
		assertEquals("m2", sent.get(3).edit);
	}

	@Test
	public void savedStreaksPastTheWindowExpire()
	{
		gain(URL, 36);
		tick();
		reply(200, "m1");
		String saved = levels.saved();
		now += WINDOW;
		levels = client();
		levels.restore(saved, WINDOW);
		assertEquals("the expired streak is dropped from the file", "", levels.saved());
		gain(URL, 37);
		tick();
		assertNull(sent.get(1).edit);
	}

	@Test
	public void aLogoutEndsTheStreakAcrossARestartToo()
	{
		gain(URL, 36);
		tick();
		reply(200, "m1");
		levels.saved();
		levels.close();
		String saved = levels.saved();
		assertEquals("", saved);
		levels = client();
		levels.restore(saved, WINDOW);
		now += MIN;
		gain(URL, 37);
		tick();
		assertNull(sent.get(1).edit);
	}

	@Test
	public void onlyAChangeIsSavedAndNeverTheWebhookToken()
	{
		assertNull("nothing changed yet", levels.saved());
		gain(URL, 36);
		assertNull("no message id yet: nothing to keep", levels.saved());
		tick();
		reply(200, "m1");
		String saved = levels.saved();
		assertTrue(saved.contains("m1"));
		assertFalse("the webhook's token stays out of the file", saved.contains("AbC-def_GHI123"));
		assertNull("saved once", levels.saved());
		now += MIN;
		gain(URL, 37);
		assertTrue("a joined level is saved", levels.saved().contains("\t37\t"));
	}

	@Test
	public void aDamagedFileIsIgnored()
	{
		levels.restore("w1\tAlex\tStrength\tx\n\nnot a line\nw1\tAlex\tStrength\t35\t36\tz\t0\tm1", WINDOW);
		gain(URL, 37);
		tick();
		assertEquals(1, sent.size());
		assertNull(sent.get(0).edit);
		levels.restore(null, WINDOW);
	}
}
