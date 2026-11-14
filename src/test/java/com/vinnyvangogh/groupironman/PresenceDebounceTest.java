package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Online and offline for Discord (Board 2026-11-10): a short grace after a
 * logout, nothing for a hop or quick relog, one offline with the session
 * length, and never "You" or a swapped title. {@code NOW} is 22:13 UTC.
 */
public class PresenceDebounceTest
{
	private static final long MIN = TimeUnit.MINUTES.toMillis(1);
	private static final int GRACE = 2;
	private final PresenceDebounce p = new PresenceDebounce(ZoneOffset.UTC);
	/** Everything the debounce asked to post, in order: what Discord would get. */
	private final List<GroupEvent> posted = new ArrayList<>();

	private void login(long at, int world, String member, int grace)
	{
		posted.addAll(p.login(at, world, member, grace));
	}

	/** The executor's once-a-second poll, from {@code from} to {@code to}. */
	private void tickThrough(long from, long to, int grace)
	{
		for (long t = from; t <= to; t += 1000)
		{
			GroupEvent e = p.poll(t, grace);
			if (e != null)
			{
				posted.add(e);
			}
		}
	}

	private static boolean offline(GroupEvent e)
	{
		return "offline".equals(e.text);
	}

	@Test
	public void loginIsOnlineWithTheWorldAndTheName()
	{
		login(NOW, 302, "GroupShip", GRACE);
		assertEquals(1, posted.size());
		GroupEvent e = posted.get(0);
		assertEquals(GroupEvent.Type.ONLINE, e.type);
		assertEquals("online", e.text);
		assertEquals("GroupShip", e.member);
		assertEquals("Online", EventText.title(e));
		assertEquals("GroupShip: is online on world 302", EventText.sentence(e, new FakeItems()));
	}

	@Test
	public void relogWithinTheGracePostsNothing()
	{
		login(NOW, 302, "GroupShip", GRACE);
		posted.clear();
		p.logout(NOW + 60 * MIN);
		tickThrough(NOW + 60 * MIN, NOW + 61 * MIN, GRACE);
		login(NOW + 61 * MIN + 59_000, 330, "GroupShip", GRACE);
		tickThrough(NOW + 62 * MIN, NOW + 75 * MIN, GRACE);
		assertTrue("no offline and no online: " + posted, posted.isEmpty());
	}

	@Test
	public void offThreeMinutesIsOneOfflineWithTitleNameAndSession()
	{
		login(NOW, 303, "GroupShip", GRACE);
		posted.clear();
		long out = NOW + 72 * MIN;
		p.logout(out);
		tickThrough(out, out + 3 * MIN, GRACE);
		assertEquals(1, posted.size());
		GroupEvent off = posted.get(0);
		assertTrue(offline(off));
		assertEquals("GroupShip", off.member);
		assertEquals("stamped with the logout, not the post", out, off.at);
		assertEquals("Offline", EventText.title(off));
		assertEquals("GroupShip: offline, played 1h 12m on world 303, logged off at 11:25 PM",
			EventText.sentence(off, new FakeItems()));
		assertNull("only once", p.poll(out + 60 * MIN, GRACE));
	}

	@Test
	public void offlineWaitsForTheGraceThenPosts()
	{
		login(NOW, 303, "GroupShip", GRACE);
		posted.clear();
		long out = NOW + 30 * MIN;
		p.logout(out);
		assertNull(p.poll(out + GRACE * MIN - 1, GRACE));
		assertTrue(offline(p.poll(out + GRACE * MIN, GRACE)));
	}

	@Test
	public void aLoginAfterTheOfflineIsOneOnline()
	{
		login(NOW, 303, "GroupShip", GRACE);
		long out = NOW + 72 * MIN;
		p.logout(out);
		tickThrough(out, out + 3 * MIN, GRACE);
		posted.clear();
		login(out + 5 * MIN, 420, "GroupShip", GRACE);
		assertEquals(1, posted.size());
		assertEquals("online", posted.get(0).text);
		assertEquals(420, posted.get(0).world);
	}

	@Test
	public void aWorldHopIsNothingEvenWithNoGrace()
	{
		login(NOW, 302, "GroupShip", 0);
		posted.clear();
		// A hop is LOGGED_IN -> HOPPING -> LOGGED_IN: the engine never calls logout for it.
		login(NOW + 20 * MIN, 330, "GroupShip", 0);
		tickThrough(NOW + 20 * MIN, NOW + 40 * MIN, 0);
		assertTrue(posted.isEmpty());
		p.logout(NOW + 41 * MIN);
		assertEquals("the hop moved the world", 330, p.poll(NOW + 41 * MIN, 0).world);
	}

	@Test
	public void graceZeroPostsAtOnce()
	{
		login(NOW, 302, "GroupShip", 0);
		posted.clear();
		p.logout(NOW + 45 * MIN);
		GroupEvent off = p.poll(NOW + 45 * MIN, 0);
		assertTrue(offline(off));
		assertEquals("GroupShip: offline, played 45m on world 302, logged off at 10:58 PM",
			EventText.sentence(off, new FakeItems()));
		login(NOW + 46 * MIN, 302, "GroupShip", 0);
		assertEquals("online", posted.get(0).text);
	}

	@Test
	public void aFlakyConnectionIsAtMostOnePairPerTenMinutes()
	{
		login(NOW, 302, "GroupShip", 0);
		// Drops and comes back every minute and a half: the offline is held until ten minutes after the online.
		for (int i = 0; i < 6; i++)
		{
			long out = NOW + (i * 90 + 30) * 1000L;
			p.logout(out);
			tickThrough(out, out + 60_000, 0);
			login(out + 60_000, 302, "GroupShip", 0);
		}
		assertEquals("just the first online", 1, posted.size());
		p.logout(NOW + 9 * MIN + 30_000);
		tickThrough(NOW + 9 * MIN + 30_000, NOW + 11 * MIN, 0);
		assertEquals(2, posted.size());
		GroupEvent off = posted.get(1);
		assertTrue(offline(off));
		assertEquals("posted no sooner than ten minutes after the online", NOW + 9 * MIN + 30_000, off.at);
	}

	@Test
	public void titleIsNeverYouAndNeverSwapped()
	{
		login(NOW, 303, "GroupShip", GRACE);
		p.logout(NOW + 30 * MIN);
		tickThrough(NOW + 30 * MIN, NOW + 33 * MIN, GRACE);
		login(NOW + 40 * MIN, 303, "GroupShip", GRACE);
		assertEquals(3, posted.size());
		Gson gson = new Gson();
		String[] titles = {"GroupShip: Online", "GroupShip: Offline", "GroupShip: Online"};
		for (int i = 0; i < 3; i++)
		{
			GroupEvent e = posted.get(i);
			JsonObject o = new JsonParser().parse(
				DiscordWebhook.payload(gson, e, new FakeItems(), DiscordStyle.EMBED, "", false)).getAsJsonObject();
			JsonObject embed = o.getAsJsonArray("embeds").get(0).getAsJsonObject();
			assertEquals(titles[i], embed.get("title").getAsString());
			String description = embed.get("description").getAsString();
			assertFalse(description, description.contains("You"));
			assertEquals(i == 1, description.startsWith("offline"));
			String plain = new JsonParser().parse(
				DiscordWebhook.payload(gson, e, new FakeItems(), DiscordStyle.PLAIN, "", false))
				.getAsJsonObject().get("content").getAsString();
			assertTrue(plain, plain.startsWith("GroupShip: " + (i == 1 ? "offline" : "is online")));
		}
	}

	@Test
	public void aLoginWithNoNameIsIgnored()
	{
		login(NOW, 303, "", GRACE);
		login(NOW, 303, null, GRACE);
		login(NOW, 303, "You", GRACE);
		assertTrue(posted.isEmpty());
	}

	@Test
	public void anotherAccountLoggingInPostsTheFirstOneOffline()
	{
		login(NOW, 303, "GroupShip", GRACE);
		p.logout(NOW + 30 * MIN);
		login(NOW + 31 * MIN, 303, "Alt", GRACE);
		assertEquals(3, posted.size());
		assertEquals("GroupShip", posted.get(1).member);
		assertTrue(offline(posted.get(1)));
		assertEquals("Alt", posted.get(2).member);
		assertEquals("online", posted.get(2).text);
	}

	@Test
	public void clientClosedInTheGracePostsOnNextStartWhenFresh()
	{
		login(NOW, 303, "GroupShip", GRACE);
		posted.clear();
		long closed = NOW + 60 * MIN;
		p.logout(closed);
		String saved = p.save();

		PresenceDebounce fresh = PresenceDebounce.parse(saved, ZoneOffset.UTC);
		GroupEvent off = fresh.poll(closed + 5 * MIN, GRACE);
		assertTrue(offline(off));
		assertEquals("GroupShip", off.member);
		assertEquals("played 1h 0m", 60, off.number);

		PresenceDebounce stale = PresenceDebounce.parse(saved, ZoneOffset.UTC);
		assertNull("never a stale offline hours later", stale.poll(closed + 3 * TimeUnit.HOURS.toMillis(1), GRACE));
		assertNull(stale.poll(closed + 4 * TimeUnit.HOURS.toMillis(1), GRACE));
	}

	@Test
	public void closedAndBackWithinTheGraceIsNothing()
	{
		login(NOW, 303, "GroupShip", GRACE);
		posted.clear();
		p.logout(NOW + 60 * MIN);
		PresenceDebounce restored = PresenceDebounce.parse(p.save(), ZoneOffset.UTC);
		assertTrue(restored.login(NOW + 61 * MIN, 303, "GroupShip", GRACE).isEmpty());
		assertNull(restored.poll(NOW + 90 * MIN, GRACE));
	}

	@Test
	public void unknownTimingIsSkipped()
	{
		assertNull(PresenceDebounce.parse(null, ZoneOffset.UTC).poll(NOW, GRACE));
		assertNull(PresenceDebounce.parse("garbage", ZoneOffset.UTC).poll(NOW, GRACE));
		assertNull(PresenceDebounce.parse("", ZoneOffset.UTC).poll(NOW, GRACE));
	}

	@Test
	public void restartWhileLoggedInIsNotALogout()
	{
		login(NOW, 303, "GroupShip", GRACE);
		posted.clear();
		p.logout(NOW + 60 * MIN);
		PresenceDebounce restored = PresenceDebounce.parse(p.save(), ZoneOffset.UTC);
		restored.resume();
		assertNull(restored.poll(NOW + 90 * MIN, GRACE));
		assertTrue(restored.login(NOW + 90 * MIN, 303, "GroupShip", GRACE).isEmpty());
	}

	@Test
	public void graceIsClampedToTenMinutes()
	{
		login(NOW, 303, "GroupShip", 99);
		p.logout(NOW + 60 * MIN);
		assertTrue(offline(p.poll(NOW + 70 * MIN, 99)));
	}

	@Test
	public void sessionLength()
	{
		assertEquals("under a minute", PresenceDebounce.played(0));
		assertEquals("45m", PresenceDebounce.played(45));
		assertEquals("1h 0m", PresenceDebounce.played(60));
		assertEquals("1h 12m", PresenceDebounce.played(72));
	}

	@Test
	public void backAfterDays()
	{
		long day = TimeUnit.DAYS.toMillis(1);
		assertNull(PresenceDebounce.back(0, NOW, 7, "Alex"));
		assertNull(PresenceDebounce.back(NOW - 6 * day, NOW, 7, "Alex"));
		GroupEvent back = PresenceDebounce.back(NOW - 9 * day - 5, NOW, 7, "Alex");
		assertEquals(GroupEvent.Type.BACK, back.type);
		assertEquals(9, back.number);
	}
}
