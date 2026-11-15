package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.DiscordWebhookTest.URL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Level-ups of one skill as one post edited in place, through the real sender with a fake transport and clock. */
public class LevelPostsTest
{
	private static final long SEC = TimeUnit.SECONDS.toMillis(1);
	private static final long WINDOW = TimeUnit.MINUTES.toMillis(30);

	private final Gson gson = new Gson();
	private long now = 1_000_000;
	private final List<DiscordQueue.Post> sent = new ArrayList<>();
	private final List<DiscordSender.Done> pending = new ArrayList<>();
	private final DiscordSender sender = new DiscordSender((post, done) ->
	{
		sent.add(post);
		pending.add(done);
	}, () -> now, () -> 12);
	/** The Progress poster name the replacement post is sent under. */
	private String poster = "Iron Pals";
	private LevelPosts levels = client();

	/** A client's level posts; a second one is the same client after a restart. */
	private LevelPosts client()
	{
		return new LevelPosts(sender, gson, () -> now, () -> DiscordStyle.EMBED, FakeItems::new, () -> poster);
	}

	private static GroupEvent level(String skill, int n)
	{
		return GroupEvent.of(GroupEvent.Type.LEVEL_UP, 0, "Alex").text(skill).number(n).build();
	}

	/** A level-up that knows the level before it, as the tracker's own events do. */
	private static GroupEvent level(String skill, int from, int n)
	{
		return GroupEvent.of(GroupEvent.Type.LEVEL_UP, 0, "Alex").text(skill).number(n).previous(from).build();
	}

	private void gain(int n)
	{
		levels.level(URL, level("Prayer", n), WINDOW, null, "first " + n);
	}

	/** The engine's once-a-second tick: owed edits, then the next post. */
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

	@Test
	public void threeLevelsAreOnePostAndTwoEditsEndingAtTheLast()
	{
		gain(24);
		tick();
		assertEquals("first 24", sent.get(0).json);
		assertNull(sent.get(0).edit);
		assertTrue("waits for the message id", DiscordSender.target(sent.get(0)).endsWith("?wait=true"));
		reply(200, "m1");

		now += 15 * SEC;
		gain(25);
		tick();
		reply(200, null);
		now += 15 * SEC;
		gain(26);
		tick();
		reply(200, null);

		assertEquals(3, sent.size());
		assertEquals("m1", sent.get(1).edit);
		assertEquals("m1", sent.get(2).edit);
		assertEquals("Prayer 23 → 25 (+2) in under a minute", description(sent.get(1)));
		assertEquals("Prayer 23 → 26 (+3) in under a minute", description(sent.get(2)));
		assertEquals(URL + "/messages/m1", DiscordSender.target(sent.get(2)));
		assertFalse("an edit takes no username", sent.get(2).json.contains("username"));
	}

	@Test
	public void aLevelOutsideTheWindowIsANewPost()
	{
		gain(24);
		tick();
		reply(200, "m1");
		now += WINDOW + SEC;
		gain(25);
		tick();
		assertEquals(2, sent.size());
		assertNull("a new post, not an edit", sent.get(1).edit);
		assertEquals("first 25", sent.get(1).json);
	}

	@Test
	public void aNinetyNineOrTotalLevelIsNeverCombinedButSkillsAndCombatAre()
	{
		assertNotNull(LevelThreads.combinable(Collections.singletonList(level("Prayer", 25))));
		assertNull("a 99", LevelThreads.combinable(Arrays.asList(level("Prayer", 99),
			GroupEvent.of(GroupEvent.Type.MAXED, 0, "Alex").text("Prayer").number(99).build())));
		assertNull("a 99 with its post switched off", LevelThreads.combinable(
			Collections.singletonList(level("Prayer", 99))));
		assertNull("a total level", LevelThreads.combinable(Arrays.asList(level("Prayer", 50),
			GroupEvent.of(GroupEvent.Type.TOTAL_LEVEL, 0, "Alex").number(1000).build())));
		assertNull("nothing", LevelThreads.combinable(Collections.emptyList()));
		assertEquals("a level with its combat level", 2, LevelThreads.combinable(Arrays.asList(level("Prayer", 50),
			GroupEvent.of(GroupEvent.Type.COMBAT_LEVEL, 0, "Alex").number(90).build())).size());
		assertEquals("two skills", 2, LevelThreads.combinable(Arrays.asList(level("Attack", 50),
			level("Hitpoints", 50))).size());
	}

	@Test
	public void aRefusedEditPostsANewMessageAndTracksThatOne()
	{
		gain(24);
		tick();
		reply(200, "m1");
		now += 15 * SEC;
		gain(25);
		tick();
		reply(404, null);
		tick();
		assertEquals(3, sent.size());
		assertNull("posted anew", sent.get(2).edit);
		assertTrue(DiscordSender.target(sent.get(2)).endsWith("?wait=true"));
		assertEquals("Prayer 23 → 25 (+2) in under a minute", description(sent.get(2)));
		assertEquals("a new post is sent under the Progress poster name", "Iron Pals",
			gson.fromJson(sent.get(2).json, JsonObject.class).get("username").getAsString());
		reply(200, "m2");

		now += 15 * SEC;
		gain(26);
		tick();
		assertEquals("m2", sent.get(3).edit);
	}

	@Test
	public void editsAreAtMostOneEveryTenSecondsAndFoldQuickerLevels()
	{
		long start = now;
		gain(24);
		tick();
		reply(200, "m1");
		for (int n = 25; n <= 27; n++)
		{
			now += 2 * SEC;
			gain(n);
			tick();
		}
		assertEquals("no edit inside ten seconds", 1, sent.size());
		now = start + LevelThreads.EDIT_GAP;
		tick();
		assertEquals(2, sent.size());
		assertEquals("Prayer 23 → 27 (+4) in under a minute", description(sent.get(1)));
		reply(200, null);
		now += SEC;
		gain(28);
		tick();
		assertEquals("the next edit waits its ten seconds too", 2, sent.size());
		now += LevelThreads.EDIT_GAP;
		tick();
		assertEquals(3, sent.size());
	}

	@Test
	public void aLevelDuringTheFirstPostWaitsForItsId()
	{
		gain(24);
		tick();
		now += 15 * SEC;
		gain(25);
		tick();
		assertEquals("nothing to edit yet", 1, sent.size());
		reply(200, "m1");
		now += LevelThreads.EDIT_GAP;
		tick();
		assertEquals("m1", sent.get(1).edit);
	}

	@Test
	public void aLogoutStartsANewPostButAnOwedEditStillGoes()
	{
		gain(24);
		tick();
		reply(200, "m1");
		now += 2 * SEC;
		gain(25);
		levels.close();
		now += LevelThreads.EDIT_GAP;
		gain(26);
		tick();
		reply(200, "m2");
		tick();
		assertEquals(3, sent.size());
		assertNull("after the logout, a new post", sent.get(1).edit);
		assertEquals("first 26", sent.get(1).json);
		assertEquals("the owed edit", "m1", sent.get(2).edit);
		assertEquals("Prayer 23 → 25 (+2) in under a minute", description(sent.get(2)));
	}

	@Test
	public void severalLevelsFromOneActionReadFromTheLevelBefore()
	{
		ProgressTracker tracker = new ProgressTracker(23);
		tracker.skill("Prayer", 3, 0, 0, "Alex");
		GroupEvent bones = tracker.skill("Prayer", 6, 1_000, 1, "Alex").get(0);
		assertEquals("the tracker carries the level before", 3, bones.previous);
		assertEquals(3, bones.toBuilder().build().previous);

		levels.level(URL, bones, WINDOW, null, "first 6");
		tick();
		reply(200, "m1");
		now += 15 * SEC;
		levels.level(URL, level("Prayer", 6, 7), WINDOW, null, "first 7");
		tick();
		assertEquals("Prayer 3 → 7 (+4) in under a minute", description(sent.get(1)));
	}

	@Test
	public void aMembersLevelWithoutTheLevelBeforeReadsOneBelow()
	{
		assertEquals(0, level("Prayer", 25).previous);
		gain(25);
		tick();
		reply(200, "m1");
		now += 15 * SEC;
		gain(26);
		tick();
		assertEquals("Prayer 24 → 26 (+2) in under a minute", description(sent.get(1)));
	}

	@Test
	public void theWebhooksOwnNameSendsNoUsername()
	{
		poster = null;
		gain(24);
		tick();
		reply(200, "m1");
		now += 15 * SEC;
		gain(25);
		tick();
		reply(404, null);
		tick();
		assertNull(sent.get(2).edit);
		assertFalse(sent.get(2).json.contains("username"));
	}

	/** Posts Prayer 24 as m1, then gains 25 so its edit is in flight. */
	private void editInFlight()
	{
		gain(24);
		tick();
		reply(200, "m1");
		now += 15 * SEC;
		gain(25);
		tick();
		assertEquals("m1", sent.get(sent.size() - 1).edit);
	}

	/**
	 * The edit in flight lost for good: every one of the queue's {@link DiscordQueue#MAX_ATTEMPTS} tries answered
	 * 503. Returns how many requests went meanwhile: the retries, plus the streak's next edit if it lives on.
	 */
	private int loseEdit()
	{
		int before = sent.size();
		for (int attempt = 1; attempt <= DiscordQueue.MAX_ATTEMPTS; attempt++)
		{
			assertEquals("m1", sent.get(sent.size() - 1).edit);
			reply(503, null);
			now += LevelThreads.EDIT_GAP;
			tick();
		}
		return sent.size() - before;
	}

	@Test
	public void threeLostEditsEndTheStreakAndTheNextLevelPostsFresh()
	{
		editInFlight();
		assertEquals("lost once: tried again", DiscordQueue.MAX_ATTEMPTS, loseEdit());
		assertEquals("lost twice: tried again", DiscordQueue.MAX_ATTEMPTS, loseEdit());
		assertEquals("lost a third time: the streak ends", DiscordQueue.MAX_ATTEMPTS - 1, loseEdit());
		now += LevelThreads.EDIT_GAP;
		tick();
		assertTrue(pending.isEmpty());

		now += 15 * SEC;
		gain(28);
		tick();
		assertNull("a fresh post", sent.get(sent.size() - 1).edit);
		assertEquals("first 28", sent.get(sent.size() - 1).json);
	}

	@Test
	public void anEditThatGoesThroughResetsTheFailureCount()
	{
		editInFlight();
		loseEdit();
		loseEdit();
		reply(200, null);
		now += 15 * SEC;
		gain(27);
		tick();
		assertEquals("one more loss is the first in a row: tried again", DiscordQueue.MAX_ATTEMPTS, loseEdit());
		assertEquals("m1", sent.get(sent.size() - 1).edit);
	}

	@Test
	public void aFirstPostAFullQueueDropsReleasesTheStreak()
	{
		gain(24);
		for (int i = 0; i < DiscordQueue.MAX_QUEUED; i++)
		{
			sender.post(URL, "filler " + i, null);
		}
		gain(25);
		while (sender.queued() > 0)
		{
			now += 60 * SEC;
			sender.pump();
			reply(204, null);
		}
		for (DiscordQueue.Post post : sent)
		{
			assertFalse("the dropped post never went", "first 24".equals(post.json));
		}
		assertEquals("the next level posted fresh", "first 25", sent.get(sent.size() - 1).json);
	}

	@Test
	public void aDroppedEditEndsTheStreak()
	{
		LevelThreads threads = new LevelThreads();
		LevelThreads.Streak s = threads.level(level("Prayer", 24), URL, 0, WINDOW, null);
		threads.posted(s, 200, "m1", 0);
		assertNull("joins", threads.level(level("Prayer", 25), URL, SEC, WINDOW, null));
		LevelThreads.Edit edit = threads.due(LevelThreads.EDIT_GAP).get(0);
		assertNull(threads.edited(edit.streak, DiscordQueue.DROPPED));
		assertTrue(threads.due(2 * LevelThreads.EDIT_GAP).isEmpty());
		assertNotNull("the next level posts fresh", threads.level(level("Prayer", 26), URL, 2 * SEC, WINDOW, null));
	}

	@Test
	public void theTimeSpanReadsInMinutesAndHours()
	{
		assertEquals("Prayer 23 → 25 (+2) in 4 min", LevelThreads.text("Prayer", 23, 25, 4 * 60 * SEC + 5 * SEC));
		assertEquals("Prayer 23 → 30 (+7) in 1 h 5 min", LevelThreads.text("Prayer", 23, 30, 65 * 60 * SEC));
	}

	@Test
	public void anEditWithANewScreenshotReplacesTheOldOne()
	{
		JsonObject o = gson.fromJson(DiscordWebhook.levelsEdit(gson, level("Prayer", 25), "Prayer 23 → 25 (+2)",
			new FakeItems(), DiscordStyle.EMBED, true), JsonObject.class);
		assertEquals(DiscordWebhook.SCREENSHOT,
			o.getAsJsonArray("attachments").get(0).getAsJsonObject().get("filename").getAsString());
		assertFalse(o.has("username"));
	}

	@Test
	public void theMessageIdIsReadFromTheWaitReply()
	{
		assertEquals("1234", DiscordSender.messageId(gson,
			"{\"attachments\":[{\"id\":\"9\"}],\"id\":\"1234\",\"channel_id\":\"5\"}"));
		assertNull(DiscordSender.messageId(gson, ""));
		assertNull(DiscordSender.messageId(gson, "not json"));
		assertEquals(URL + "/messages/7",
			DiscordSender.target(new DiscordQueue.Post(URL + "/", "{}", null, "7", null)));
	}

	// Board 2026-11-14: Strength 36 and 37, sixteen minutes apart inside a 30 min window, were two posts because
	// the client was restarted between them. The open streaks now survive a restart.

	/** The same client after a restart: new level posts, restored from what the old ones saved. */
	private void restart(String saved)
	{
		levels = client();
		levels.restore(saved, WINDOW);
	}

	/** Prayer 24 posted as m1; what the client saved for it. */
	private String postAndSave()
	{
		gain(24);
		tick();
		reply(200, "m1");
		return levels.saved();
	}

	@Test
	public void theNextLevelAfterARestartEditsTheSamePost()
	{
		restart(postAndSave());
		now += 16 * 60 * SEC;
		gain(25);
		tick();
		assertEquals(2, sent.size());
		assertEquals("m1", sent.get(1).edit);
		assertEquals("Prayer 23 → 25 (+2) in 16 min", description(sent.get(1)));
		assertEquals(URL + "/messages/m1", DiscordSender.target(sent.get(1)));
	}

	@Test
	public void aLevelOutsideTheWindowAfterARestartIsANewPost()
	{
		restart(postAndSave());
		now += WINDOW + SEC;
		gain(25);
		tick();
		assertEquals(2, sent.size());
		assertNull("a new post, not an edit", sent.get(1).edit);
		assertEquals("first 25", sent.get(1).json);
	}

	@Test
	public void aDifferentWebhookAfterARestartIsANewPost()
	{
		restart(postAndSave());
		now += 60 * SEC;
		levels.level("https://discord.com/api/webhooks/987654321098765432/ZyX-wvu_TSR987", level("Prayer", 25),
			WINDOW, null, "first 25");
		tick();
		assertEquals(2, sent.size());
		assertNull(sent.get(1).edit);
		assertEquals("first 25", sent.get(1).json);
	}

	@Test
	public void aRestoredPostDiscordNoLongerHasIsPostedAnew()
	{
		restart(postAndSave());
		now += 60 * SEC;
		gain(25);
		tick();
		reply(404, null);
		tick();
		assertEquals(3, sent.size());
		assertNull("posted anew", sent.get(2).edit);
		reply(200, "m2");
		now += LevelThreads.EDIT_GAP;
		gain(26);
		tick();
		assertEquals("m2", sent.get(3).edit);
	}

	@Test
	public void savedStreaksPastTheWindowExpire()
	{
		String saved = postAndSave();
		now += WINDOW;
		restart(saved);
		assertEquals("the expired streak is dropped from the file", "", levels.saved());
		gain(25);
		tick();
		assertNull(sent.get(1).edit);
	}

	@Test
	public void aLogoutEndsTheStreakAcrossARestartToo()
	{
		postAndSave();
		levels.close();
		String saved = levels.saved();
		assertEquals("", saved);
		restart(saved);
		now += 60 * SEC;
		gain(25);
		tick();
		assertNull(sent.get(1).edit);
	}

	@Test
	public void onlyAChangeIsSavedAndNeverTheWebhookToken()
	{
		assertNull("nothing changed yet", levels.saved());
		gain(24);
		assertNull("no message id yet: nothing to keep", levels.saved());
		tick();
		reply(200, "m1");
		String saved = levels.saved();
		assertTrue(saved.startsWith("w123456789012345678\tAlex\t"));
		assertTrue(saved.endsWith("\tm1\tPrayer\t23\t24"));
		assertFalse("the webhook's token stays out of the file", saved.contains("AbC-def_GHI123"));
		assertNull("saved once", levels.saved());
		now += 60 * SEC;
		gain(25);
		assertTrue("a joined level is saved", levels.saved().endsWith("\tPrayer\t23\t25"));
	}

	// Board 2026-11-14: GroupOtt (no plugin) Thieving 45 at 7:03 PM and 46 at 7:13 PM were two posts read from the
	// game's group broadcast. A broadcast level now joins that member's streak as a plugin user's own does.

	/** A level the game's group broadcast read, as GroupEventEngine hands the poster's client one. */
	private static GroupEvent heard(String line)
	{
		GroupEvent e = LevelThreads.combinableBroadcast(GroupBroadcasts.parse(line, 0, name -> null));
		assertNotNull(line, e);
		return e;
	}

	/** The broadcast's level into its streak on {@code on}, its first post the usual one for it. */
	private void hear(LevelPosts on, String line)
	{
		GroupEvent e = heard(line);
		on.level(URL, e, WINDOW, null, DiscordWebhook.payload(gson, e, new FakeItems(), DiscordStyle.EMBED, null,
			false));
	}

	private static String footer(DiscordQueue.Post post)
	{
		JsonObject o = new Gson().fromJson(post.json, JsonObject.class);
		return o.getAsJsonArray("embeds").get(0).getAsJsonObject().getAsJsonObject("footer").get("text")
			.getAsString();
	}

	@Test
	public void twoBroadcastLevelsInOneSkillInsideTheWindowAreOnePostThenOneEdit()
	{
		hear(levels, "GroupOtt has reached Thieving level 45.");
		tick();
		reply(200, "m1");
		now += 10 * 60 * SEC;
		hear(levels, "GroupOtt has reached Thieving level 46.");
		tick();

		assertEquals(2, sent.size());
		assertNull(sent.get(0).edit);
		assertEquals("m1", sent.get(1).edit);
		assertEquals("read one below the first, as a member's level is", "Thieving 44 → 46 (+2) in 10 min",
			description(sent.get(1)));
		assertTrue("the edit keeps the broadcast footer", footer(sent.get(1)).endsWith(GroupBroadcasts.SOURCE));
		assertTrue(footer(sent.get(0)).endsWith(GroupBroadcasts.SOURCE));
	}

	@Test
	public void aBroadcastLevelOutsideTheWindowIsANewPost()
	{
		hear(levels, "GroupOtt has reached Thieving level 45.");
		tick();
		reply(200, "m1");
		now += WINDOW + SEC;
		hear(levels, "GroupOtt has reached Thieving level 46.");
		tick();
		assertEquals(2, sent.size());
		assertNull("a new post, not an edit", sent.get(1).edit);
	}

	@Test
	public void broadcastLevelsInDifferentSkillsJoinTheMembersPost()
	{
		hear(levels, "GroupOtt has reached Thieving level 45.");
		tick();
		reply(200, "m1");
		now += 60 * SEC;
		hear(levels, "GroupOtt has reached Agility level 30.");
		tick();
		assertEquals(2, sent.size());
		assertEquals("m1", sent.get(1).edit);
		assertEquals("Agility 29 → 30 (+1), Thieving 44 → 45 (+1) in 1 min", description(sent.get(1)));
	}

	@Test
	public void differentMembersAreSeparatePosts()
	{
		hear(levels, "GroupOtt has reached Thieving level 45.");
		tick();
		reply(200, "m1");
		now += 60 * SEC;
		hear(levels, "IronPal has reached Thieving level 45.");
		tick();
		assertEquals(2, sent.size());
		assertNull(sent.get(1).edit);
	}

	@Test
	public void aBroadcastStreakSurvivesARestart()
	{
		hear(levels, "GroupOtt has reached Thieving level 45.");
		tick();
		reply(200, "m1");
		restart(levels.saved());
		now += 16 * 60 * SEC;
		hear(levels, "GroupOtt has reached Thieving level 46.");
		tick();
		assertEquals("m1", sent.get(1).edit);
		assertEquals("Thieving 44 → 46 (+2) in 16 min", description(sent.get(1)));
		assertTrue(footer(sent.get(1)).endsWith(GroupBroadcasts.SOURCE));
	}

	@Test
	public void broadcastCombatLevelsGroupTooAcrossARestart()
	{
		hear(levels, "GroupOtt has reached combat level 100.");
		tick();
		reply(200, "m1");
		String saved = levels.saved();
		assertTrue(saved.contains("\tGroupOtt\t"));
		assertTrue(saved.endsWith("\tm1\tCombat\t99\t100"));
		restart(saved);
		now += 60 * SEC;
		hear(levels, "GroupOtt has reached combat level 101.");
		tick();
		assertEquals("m1", sent.get(1).edit);
		assertEquals("Combat 99 → 101 (+2) in 1 min", description(sent.get(1)));
	}

	@Test
	public void aNewPosterMidStreakStartsANewPost()
	{
		hear(levels, "GroupOtt has reached Thieving level 45.");
		tick();
		reply(200, "m1");
		// The leader logged out: the backup's client, with no streak of its own, hears the next one.
		LevelPosts backup = client();
		now += 60 * SEC;
		hear(backup, "GroupOtt has reached Thieving level 46.");
		backup.tick();
		sender.pump();
		assertEquals(2, sent.size());
		assertNull("the backup posts anew", sent.get(1).edit);
	}

	@Test
	public void onlyABroadcastsSkillOrCombatLevelJoinsAStreak()
	{
		assertNull("a plugin user's own level is decided by combinable", LevelThreads.combinableBroadcast(
			level("Thieving", 46)));
		assertNotNull("a plugin user's own combat level joins their post too", LevelThreads.combinable(
			Collections.singletonList(GroupEvent.of(GroupEvent.Type.COMBAT_LEVEL, 0, "Alex").number(90).build())));
		assertNull("a 99", LevelThreads.combinableBroadcast(GroupBroadcasts.parse(
			"GroupOtt has reached Thieving level 99.", 0, name -> null)));
		assertNull("a total level", LevelThreads.combinableBroadcast(GroupBroadcasts.parse(
			"GroupOtt has reached a total level of 1,000.", 0, name -> null)));
		assertNull("a quest", LevelThreads.combinableBroadcast(GroupBroadcasts.parse(
			"GroupOtt has completed a quest: Black Knights' Fortress.", 0, name -> null)));
		assertNull(LevelThreads.combinableBroadcast(null));
	}

	// Board 2026-11-14: one post per member per window, not per skill: every level that member gains in the window,
	// in any skill or their combat level, is edited into the same post, the skill that just levelled listed first.

	private void gain(String skill, int n)
	{
		levels.level(URL, level(skill, n), WINDOW, null, "first " + skill + " " + n);
	}

	/** Thieving 46 posts as m1, then Ranged 66 a gap later and is sent. */
	private void thievingThenRanged(long gap)
	{
		gain("Thieving", 46);
		tick();
		reply(200, "m1");
		now += gap;
		gain("Ranged", 66);
		tick();
	}

	private static GroupEvent combat(int n)
	{
		return GroupEvent.of(GroupEvent.Type.COMBAT_LEVEL, 0, "Alex").number(n).build();
	}

	@Test
	public void twoSkillsInsideTheWindowAreOnePostEditedToListBoth()
	{
		thievingThenRanged(5 * 60 * SEC);
		assertEquals(2, sent.size());
		assertEquals("m1", sent.get(1).edit);
		assertEquals("Ranged 65 → 66 (+1), Thieving 45 → 46 (+1) in 5 min", description(sent.get(1)));
	}

	@Test
	public void aThirdSkillJoinsTheSamePostAtTheTop()
	{
		thievingThenRanged(5 * 60 * SEC);
		reply(200, null);
		now += 7 * 60 * SEC;
		levels.level(URL, combat(70), WINDOW, null, "first combat 70");
		tick();
		assertEquals(3, sent.size());
		assertEquals("m1", sent.get(2).edit);
		assertEquals("Combat 69 → 70 (+1), Ranged 65 → 66 (+1), Thieving 45 → 46 (+1) in 12 min",
			description(sent.get(2)));
	}

	@Test
	public void theSkillThatJustLevelledMovesToTheTop()
	{
		thievingThenRanged(60 * SEC);
		reply(200, null);
		now += 60 * SEC;
		gain("Thieving", 47);
		tick();
		assertEquals("most recent first, not most levels first",
			"Thieving 45 → 47 (+2), Ranged 65 → 66 (+1) in 2 min", description(sent.get(2)));
		reply(200, null);
		now += 60 * SEC;
		gain("Ranged", 67);
		tick();
		assertEquals("Ranged 65 → 67 (+2), Thieving 45 → 47 (+2) in 3 min", description(sent.get(3)));
	}

	@Test
	public void anotherSkillAfterTheWindowIsANewPost()
	{
		gain("Thieving", 46);
		tick();
		reply(200, "m1");
		now += WINDOW + SEC;
		gain("Ranged", 66);
		tick();
		assertEquals(2, sent.size());
		assertNull("a new post, not an edit", sent.get(1).edit);
		assertEquals("first Ranged 66", sent.get(1).json);
	}

	@Test
	public void aMomentOfALevelAndItsCombatLevelStartsOnePostBothJoinLater()
	{
		levels.level(URL, Arrays.asList(level("Attack", 69, 70), combat(80)), WINDOW, null, "first moment");
		tick();
		assertEquals("first moment", sent.get(0).json);
		reply(200, "m1");
		now += 60 * SEC;
		levels.level(URL, Arrays.asList(level("Strength", 70, 71), combat(81)), WINDOW, null, "second moment");
		tick();
		assertEquals(2, sent.size());
		assertEquals("m1", sent.get(1).edit);
		assertEquals("Combat 79 → 81 (+2), Strength 70 → 71 (+1), Attack 69 → 70 (+1) in 1 min",
			description(sent.get(1)));
	}

	@Test
	public void aRepeatedLevelChangesNothing()
	{
		gain("Thieving", 46);
		tick();
		reply(200, "m1");
		levels.saved();
		now += 60 * SEC;
		gain("Thieving", 46);
		now += LevelThreads.EDIT_GAP;
		tick();
		assertEquals("no edit", 1, sent.size());
		assertNull("nothing to save", levels.saved());
	}

	@Test
	public void aMembersSkillsSurviveARestartInTheirOrder()
	{
		thievingThenRanged(60 * SEC);
		reply(200, null);
		restart(levels.saved());
		now += 60 * SEC;
		gain("Agility", 30);
		tick();
		assertEquals("m1", sent.get(2).edit);
		assertEquals("Agility 29 → 30 (+1), Ranged 65 → 66 (+1), Thieving 45 → 46 (+1) in 2 min",
			description(sent.get(2)));
	}

	@Test
	public void aPerSkillLineSavedBeforeTheRegroupingIsDropped()
	{
		levels.restore("w123456789012345678\tAlex\tPrayer\t23\t24\t" + now + "\t" + now + "\tm1", WINDOW);
		assertEquals("the old line is dropped from the file", "", levels.saved());
		gain(25);
		tick();
		assertEquals(1, sent.size());
		assertNull(sent.get(0).edit);
	}

	@Test
	public void aDamagedFileIsIgnored()
	{
		levels.restore(null, WINDOW);
		levels.restore("w1\tAlex\tPrayer\tx\n\nnot a line\nw123456789012345678\tAlex\tPrayer\t23\t24\tz\t0\tm1\n"
			+ "w123456789012345678\tAlex\t" + now + "\t" + now + "\tm1\tPrayer\t23\n"
			+ "w123456789012345678\tAlex\t" + now + "\t" + now + "\tm1\t\t23\t24", WINDOW);
		gain(25);
		tick();
		assertEquals(1, sent.size());
		assertNull(sent.get(0).edit);
	}
}
