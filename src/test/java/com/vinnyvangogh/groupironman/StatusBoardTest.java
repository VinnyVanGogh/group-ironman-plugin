package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The live online board: posted once, edited after, posted again when deleted, rate-limited, never leaking the URL. */
public class StatusBoardTest
{
	private static final String URL = "https://discord.com/api/webhooks/123456/secretTOKEN_value";
	private static final String OTHER = "https://discord.com/api/webhooks/999/anotherTOKEN";
	private static final Gson GSON = new Gson();
	private static final long GAP = StatusBoard.MIN_GAP_MS;

	private final StatusBoard board = new StatusBoard();
	private String saved;

	private void target(String url)
	{
		board.target(url, () -> saved);
	}

	/** Posts the board with {@code lines} at {@code at} and answers it with message id 111. */
	private void posted(String lines, long at)
	{
		target(URL);
		board.show(lines, at);
		StatusBoard.Send send = board.next(at, false);
		assertTrue("the first send posts", send.create());
		board.done(send, 200, "111", -1, at);
		saved = board.takeSaved();
	}

	@Test
	public void theBoardIsPostedOnceThenEdited()
	{
		posted("a", NOW);
		assertTrue(saved.endsWith("\t111"));
		assertFalse("only a hash of the webhook is saved", saved.contains("secretTOKEN") || saved.contains("discord"));
		assertNull("nothing changed", board.next(NOW + GAP, false));
		board.show("b", NOW + GAP);
		StatusBoard.Send edit = board.next(NOW + GAP, false);
		assertFalse(edit.create());
		assertEquals("111", edit.messageId);
		assertEquals("b", edit.lines);
		board.done(edit, 200, null, -1, NOW + GAP);
		assertNull("an edit keeps the id", board.takeSaved());
	}

	@Test
	public void aRestartEditsTheSavedBoardAndANewWebhookPostsItsOwn()
	{
		posted("a", NOW);
		StatusBoard restarted = new StatusBoard();
		restarted.target(URL, () -> saved);
		restarted.show("a", NOW);
		assertEquals("111", restarted.next(NOW, false).messageId);

		StatusBoard moved = new StatusBoard();
		moved.target(OTHER, () -> saved);
		moved.show("a", NOW);
		assertTrue("the saved id belongs to the other webhook", moved.next(NOW, false).create());
	}

	@Test
	public void aDeletedBoardIsPostedAgain()
	{
		posted("a", NOW);
		board.show("b", NOW + GAP);
		StatusBoard.Send edit = board.next(NOW + GAP, false);
		board.done(edit, 404, null, -1, NOW + GAP);
		assertEquals("the id is forgotten", "", board.takeSaved());
		assertNull("still at most one request per gap", board.next(NOW + GAP + 1, false));
		StatusBoard.Send again = board.next(NOW + 2 * GAP, false);
		assertTrue(again.create());
		assertEquals("b", again.lines);
		board.done(again, 200, "222", -1, NOW + 2 * GAP);
		assertTrue(board.takeSaved().endsWith("\t222"));
	}

	@Test
	public void atMostOneRequestPerGapAndOnlyTheNewestLines()
	{
		posted("a", NOW);
		board.show("b", NOW + 1_000);
		assertNull(board.next(NOW + 1_000, false));
		board.show("c", NOW + 5_000);
		assertNull(board.next(NOW + GAP - 1, false));
		StatusBoard.Send edit = board.next(NOW + GAP, false);
		assertEquals("c", edit.lines);
		assertEquals("the board says when it last changed", NOW + 5_000, edit.changedAt);
		assertNull("one in flight at a time", board.next(NOW + 3 * GAP, false));
	}

	@Test
	public void quietHoursAndRetryAfterHoldTheEdit()
	{
		posted("a", NOW);
		board.show("b", NOW + GAP);
		assertNull("quiet hours", board.next(NOW + GAP, true));
		StatusBoard.Send edit = board.next(NOW + 2 * GAP, false);
		board.done(edit, 429, null, 60_000, NOW + 2 * GAP);
		assertNull(board.next(NOW + 3 * GAP, false));
		assertEquals("b", board.next(NOW + 2 * GAP + 60_000, false).lines);
	}

	@Test
	public void aBrokenWebhookIsNotRetriedUntilTheLinesChange()
	{
		target(URL);
		board.show("a", NOW);
		board.done(board.next(NOW, false), 401, null, -1, NOW);
		assertNull(board.next(NOW + 10 * GAP, false));
		board.show("b", NOW + 10 * GAP);
		assertTrue(board.next(NOW + 10 * GAP, false).create());
	}

	@Test
	public void noWebhookNoRequest()
	{
		target(null);
		board.show("a", NOW);
		assertNull(board.next(NOW, false));
	}

	@Test
	public void thePayloadNeverCarriesTheWebhook()
	{
		StatusBoard.Send create = new StatusBoard.Send(URL, null, "line", NOW);
		StatusBoard.Send edit = new StatusBoard.Send(URL, "111", "line", NOW);
		for (StatusBoard.Send send : Arrays.asList(create, edit))
		{
			for (DiscordStyle style : DiscordStyle.values())
			{
				String json = StatusBoard.payload(GSON, send, style, "Iron Pals");
				assertFalse(json, json.contains("secretTOKEN") || json.contains("123456") || json.contains("webhooks"));
				JsonObject o = GSON.fromJson(json, JsonObject.class);
				assertEquals("mentions nobody", 0, o.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").size());
				assertEquals("an edit takes no username", send.create(), o.has("username"));
				assertFalse("the webhook's own name sends none",
					StatusBoard.payload(GSON, send, style, null).contains("username"));
				String text = style == DiscordStyle.PLAIN ? o.get("content").getAsString()
					: o.getAsJsonArray("embeds").get(0).getAsJsonObject().get("description").getAsString();
				assertEquals("line\nUpdated <t:" + NOW / 1000 + ":R>", text.substring(text.indexOf("line")));
			}
		}
	}

	@Test
	public void eachMemberOnlineOfflineOrUnknown()
	{
		GroupMembers roster = GroupMembers.of("Iron Pals", Arrays.asList("Sam", "Alex", "Kim", "Jo"));
		Map<String, Long> heard = new HashMap<>();
		heard.put("Sam", NOW - 10_000);
		heard.put("Jo", NOW - Presence.ONLINE_MS - 60_000);
		Map<String, MemberStats> stats = new HashMap<>();
		stats.put("Sam", new MemberStats(Collections.emptyMap(), 100, 100, 302, 0));
		stats.put("Jo", new MemberStats(Collections.emptyMap(), 100, 100, 330, 0));
		StatusBoard.Self me = new StatusBoard.Self("Alex", false, 301, NOW - 120_000);
		String lines = StatusBoard.lines(roster, heard, stats, me, NOW);
		assertEquals(String.join("\n",
			StatusBoard.OFFLINE + " **Alex** last seen <t:" + (NOW - 120_000) / 1000 + ":R> on world 301",
			StatusBoard.OFFLINE + " **Jo** last seen <t:" + (NOW - Presence.ONLINE_MS - 60_000) / 1000 + ":R> on world 330",
			StatusBoard.UNKNOWN + " **Kim** unknown",
			StatusBoard.ONLINE + " **Sam** online (world 302)"), lines);

		StatusBoard.Self hopped = new StatusBoard.Self("Alex", true, 444, 0);
		assertTrue(StatusBoard.lines(roster, heard, stats, hopped, NOW)
			.startsWith(StatusBoard.ONLINE + " **Alex** online (world 444)"));
	}

	/** Board 2026-11-11: the game's Group channel fills in members without the plugin; 2 of 5 online. */
	@Test
	public void theGroupChannelShowsMembersWithoutThePlugin()
	{
		Map<String, Long> heard = new HashMap<>();
		heard.put("GroupShip", NOW - 5_000);
		heard.put("GroupArk", NOW - 30_000);
		Map<String, MemberStats> stats = new HashMap<>();
		stats.put("GroupShip", new MemberStats(Collections.emptyMap(), 100, 100, 303, 0));
		stats.put("GroupArk", new MemberStats(Collections.emptyMap(), 100, 100, 330, 0));
		StatusBoard.Self me = new StatusBoard.Self("GroupShip", true, 303, 0);
		String lines = StatusBoard.lines(GamePresenceTest.ROSTER, heard, stats, me, GamePresenceTest.twoOfFive(), NOW);
		assertEquals(String.join("\n",
			// Heard 30 seconds ago, but the game says logged out: the game wins.
			StatusBoard.OFFLINE + " **GroupArk** last seen <t:" + (NOW - 30_000) / 1000 + ":R> on world 330",
			StatusBoard.ONLINE + " **GroupOtt** online (world 615)" + StatusBoard.NO_PLUGIN,
			StatusBoard.OFFLINE + " **GroupPNK** offline" + StatusBoard.NO_PLUGIN,
			StatusBoard.ONLINE + " **GroupShip** online (world 303)",
			StatusBoard.OFFLINE + " **GroupTrashy** offline" + StatusBoard.NO_PLUGIN), lines);
		assertEquals(2, lines.split(StatusBoard.ONLINE, -1).length - 1);
		assertEquals(3, lines.split(StatusBoard.OFFLINE, -1).length - 1);
		assertFalse(lines.contains("unknown"));
	}

	private static String t(long at)
	{
		return "<t:" + at / 1000 + ":R>";
	}

	/** Board 2026-11-11: offline reads "last seen", on the world known; a no-plugin member's comes from the channel. */
	@Test
	public void offlineMembersReadLastSeenWithTheirWorld()
	{
		Map<String, Long> heard = new HashMap<>();
		heard.put("GroupArk", NOW - 7_200_000);
		Map<String, MemberStats> stats = new HashMap<>();
		stats.put("GroupArk", new MemberStats(Collections.emptyMap(), 100, 100, 330, 0));
		Map<String, LastSeen> seen = new HashMap<>();
		seen.put("GroupPNK", new LastSeen(NOW - 3_600_000, 420, false));
		// Seen on the channel after their client was last heard: the newer world wins.
		seen.put("GroupArk", new LastSeen(NOW - 60_000, 331, false));
		StatusBoard.Self me = new StatusBoard.Self("GroupShip", true, 303, 0);
		String lines = StatusBoard.lines(GamePresenceTest.ROSTER, heard, seen, stats, me,
			GamePresenceTest.twoOfFive(), NOW);
		assertEquals(String.join("\n",
			StatusBoard.OFFLINE + " **GroupArk** last seen " + t(NOW - 60_000) + " on world 331",
			StatusBoard.ONLINE + " **GroupOtt** online (world 615)" + StatusBoard.NO_PLUGIN,
			StatusBoard.OFFLINE + " **GroupPNK** last seen " + t(NOW - 3_600_000) + " on world 420"
				+ StatusBoard.NO_PLUGIN,
			StatusBoard.ONLINE + " **GroupShip** online (world 303)",
			StatusBoard.OFFLINE + " **GroupTrashy** offline" + StatusBoard.NO_PLUGIN), lines);
		assertFalse("a plugin user is online", lines.contains(StatusBoard.STALE));
	}

	/**
	 * Board 2026-11-11: the last plugin user logs out. Its final edit keeps every last seen, shows the members no one
	 * can see now as unknown, and says the board may be out of date as of the logout.
	 */
	@Test
	public void theLastPluginUserLoggingOutMarksTheBoardStale()
	{
		long logout = NOW - 5_000;
		Map<String, Long> heard = new HashMap<>();
		heard.put("GroupArk", NOW - 7_200_000);
		Map<String, MemberStats> stats = new HashMap<>();
		stats.put("GroupArk", new MemberStats(Collections.emptyMap(), 100, 100, 330, 0));
		Map<String, LastSeen> seen = new HashMap<>();
		seen.put("GroupOtt", new LastSeen(logout - 600, 615, false));
		seen.put("GroupShip", new LastSeen(logout - 600, 303, true));
		StatusBoard.Self me = new StatusBoard.Self("GroupShip", false, 303, logout);
		String lines = StatusBoard.lines(GamePresenceTest.ROSTER, heard, seen, stats, me, GamePresence.UNKNOWN,
			NOW);
		assertEquals(String.join("\n",
			StatusBoard.OFFLINE + " **GroupArk** last seen " + t(NOW - 7_200_000) + " on world 330",
			StatusBoard.UNKNOWN + " **GroupOtt** last seen " + t(logout - 600) + " on world 615"
				+ StatusBoard.NO_PLUGIN,
			StatusBoard.UNKNOWN + " **GroupPNK** unknown",
			StatusBoard.OFFLINE + " **GroupShip** last seen " + t(logout) + " on world 303",
			StatusBoard.UNKNOWN + " **GroupTrashy** unknown",
			"*" + StatusBoard.STALE + " (as of " + t(logout) + ")*"), lines);

		// The edit goes out, and an unchanged board is not edited again as time passes.
		posted("before", NOW - GAP);
		board.show(lines, NOW);
		StatusBoard.Send edit = board.next(NOW, false);
		assertFalse(edit.create());
		assertTrue(StatusBoard.payload(GSON, edit, DiscordStyle.PLAIN, null).contains(StatusBoard.STALE));
		board.done(edit, 200, null, -1, NOW);
		assertEquals(lines, StatusBoard.lines(GamePresenceTest.ROSTER, heard, seen, stats, me, GamePresence.UNKNOWN,
			NOW + 3_600_000));
		board.show(lines, NOW + 3_600_000);
		assertNull(board.next(NOW + 3_600_000, false));
	}

	@Test
	public void anotherPluginUserOnlineKeepsTheBoardCurrent()
	{
		Map<String, Long> heard = Collections.singletonMap("GroupArk", NOW - 1_000);
		StatusBoard.Self me = new StatusBoard.Self("GroupShip", false, 303, NOW - 5_000);
		String lines = StatusBoard.lines(GamePresenceTest.ROSTER, heard, Collections.emptyMap(),
			Collections.emptyMap(), me, GamePresence.UNKNOWN, NOW);
		assertFalse(lines, lines.contains(StatusBoard.STALE));
		assertTrue(lines.contains(StatusBoard.ONLINE + " **GroupArk** online"));
	}

	/** Board 2026-11-11: last seen is in the group cache, so a restarted client, logged out, still shows it. */
	@Test
	public void aRestartKeepsLastSeen()
	{
		GroupRoster before = new GroupRoster();
		before.seen("GroupOtt", new LastSeen(NOW - 3_600_000, 615, false));
		before.seen("GroupShip", new LastSeen(NOW - 60_000, 303, true));
		before.seen("GroupShip", new LastSeen(NOW - 120_000, 0, false));
		GroupRoster after = new GroupRoster();
		assertTrue(GroupCache.read(GSON, GroupCache.write(GSON, before), after));
		LastSeen ship = after.lastSeen().get("GroupShip");
		assertEquals(NOW - 60_000, ship.at);
		assertEquals(303, ship.world);
		assertTrue(ship.plugin);

		String lines = StatusBoard.lines(GroupMembers.UNKNOWN, after.lastHeard(), after.lastSeen(),
			after.allStats(), null, GamePresence.UNKNOWN, NOW);
		assertEquals(String.join("\n",
			StatusBoard.UNKNOWN + " **GroupOtt** last seen " + t(NOW - 3_600_000) + " on world 615"
				+ StatusBoard.NO_PLUGIN,
			StatusBoard.OFFLINE + " **GroupShip** last seen " + t(NOW - 60_000) + " on world 303",
			"*" + StatusBoard.STALE + " (as of " + t(NOW - 60_000) + ")*"), lines);
	}

	@Test
	public void anOlderCacheWithoutLastSeenStillLoads()
	{
		GroupRoster before = new GroupRoster();
		before.heard("Sam", NOW);
		JsonObject old = GSON.fromJson(GroupCache.write(GSON, before), JsonObject.class);
		old.remove("seen");
		GroupRoster after = new GroupRoster();
		assertTrue(GroupCache.read(GSON, GSON.toJson(old), after));
		assertTrue(after.lastSeen().isEmpty());
		assertEquals(Long.valueOf(NOW), after.lastHeard().get("Sam"));
	}

	@Test
	public void lastSeenOnlyMovesForward()
	{
		LastSeen merged = LastSeen.merge(new LastSeen(200, 0, true), new LastSeen(100, 301, false));
		assertEquals(200, merged.at);
		assertEquals("the older world when the newer has none", 301, merged.world);
		assertTrue(merged.plugin);
	}

	@Test
	public void beforeTheChannelLoadsMembersWithoutThePluginAreUnknown()
	{
		StatusBoard.Self me = new StatusBoard.Self("GroupShip", true, 303, 0);
		String lines = StatusBoard.lines(GamePresenceTest.ROSTER, Collections.emptyMap(), Collections.emptyMap(), me,
			GamePresence.UNKNOWN, NOW);
		assertTrue(lines, lines.contains(StatusBoard.UNKNOWN + " **GroupOtt** unknown"));
		assertFalse(lines.contains(StatusBoard.NO_PLUGIN));
	}

	@Test
	public void withoutTheRosterTheChannelStillListsWhoIsOn()
	{
		StatusBoard.Self me = new StatusBoard.Self("GroupShip", true, 303, 0);
		GamePresence game = GamePresence.read(GroupMembers.UNKNOWN,
			GamePresenceTest.worlds("GroupOtt", 615, "GroupShip", 303), null);
		assertEquals(StatusBoard.ONLINE + " **GroupOtt** online (world 615)" + StatusBoard.NO_PLUGIN + "\n"
			+ StatusBoard.ONLINE + " **GroupShip** online (world 303)",
			StatusBoard.lines(GroupMembers.UNKNOWN, Collections.emptyMap(), Collections.emptyMap(), me, game, NOW));
	}

	@Test
	public void withoutTheRosterOnlyMembersHeardFromAreListed()
	{
		Map<String, Long> heard = Collections.singletonMap("Sam", NOW);
		StatusBoard.Self me = new StatusBoard.Self("Alex", true, 301, 0);
		assertEquals(StatusBoard.ONLINE + " **Alex** online (world 301)\n" + StatusBoard.ONLINE + " **Sam** online",
			StatusBoard.lines(GroupMembers.UNKNOWN, heard, Collections.emptyMap(), me, NOW));
		assertEquals("", StatusBoard.lines(GroupMembers.UNKNOWN, Collections.emptyMap(), Collections.emptyMap(), null,
			NOW));
	}

	/** The board through the real sender, with a fake transport: the requests it makes, and their answers. */
	private final List<DiscordQueue.Post> requests = new ArrayList<>();
	private final List<DiscordSender.Done> answers = new ArrayList<>();
	private long now = NOW;
	private final DiscordSender sender = new DiscordSender((post, done) ->
	{
		requests.add(post);
		answers.add(done);
	}, () -> now, () -> 12);

	private void sendNext()
	{
		StatusBoard.Send send = board.next(now, false);
		board.send(sender, send, StatusBoard.payload(GSON, send, DiscordStyle.EMBED, null), () -> now);
		sender.pump();
	}

	@Test
	public void theBoardGoesThroughTheSendersQueuePostThenEdit()
	{
		String thread = URL + "?thread_id=42";
		board.target(thread, () -> null);
		board.show("a", now);
		sendNext();
		DiscordQueue.Post post = requests.get(0);
		assertNull("a new board is a post", post.edit);
		assertEquals("waits for the id, keeps the thread", URL + "?thread_id=42&wait=true", DiscordSender.target(post));
		answers.remove(0).done(200, null, "111");
		assertTrue(board.takeSaved().endsWith("\t111"));

		now += GAP;
		board.show("b", now);
		sendNext();
		DiscordQueue.Post edit = requests.get(1);
		assertEquals("then edits it in place", "111", edit.edit);
		assertEquals(URL + "/messages/111?thread_id=42", DiscordSender.target(edit));
		answers.remove(0).done(404, null);
		assertEquals("a deleted board is forgotten", "", board.takeSaved());
		now += GAP;
		sendNext();
		assertNull("and posted again", requests.get(2).edit);
	}

	@Test
	public void theBoardSharesThePerMinuteLimit()
	{
		for (int i = 0; i < DiscordQueue.PER_MINUTE; i++)
		{
			sender.post(URL, "post " + i, null);
			sender.pump();
			answers.remove(0).done(204, null);
		}
		target(URL);
		board.show("a", now);
		sendNext();
		assertEquals("the minute's posts used the limit", DiscordQueue.PER_MINUTE, requests.size());
		now += 60_000;
		sender.pump();
		assertEquals("the board goes when the minute is up", DiscordQueue.PER_MINUTE + 1, requests.size());
	}

	@Test
	public void aBoardRequestAFullQueueDropsIsSentAgain()
	{
		target(URL);
		board.show("a", now);
		StatusBoard.Send send = board.next(now, false);
		board.send(sender, send, "{}", () -> now);
		for (int i = 0; i < DiscordQueue.MAX_QUEUED; i++)
		{
			sender.post(URL, "post " + i, null);
		}
		assertNull("not before the gap", board.next(now + 1, false));
		assertTrue("dropped, so tried again", board.next(now + GAP, false).create());
	}

	@Test
	public void anUpgradeKeepsWhatItsOldSwitchDid()
	{
		assertEquals(OnlineStatus.POSTS, OnlineStatus.upgrading(true, true));
		assertEquals(OnlineStatus.OFF, OnlineStatus.upgrading(false, true));
		assertNull("a new install takes the default", OnlineStatus.upgrading(false, false));
		assertEquals("a new install posts; a member opts into the board (Board 2026-11-10)", OnlineStatus.POSTS,
			new GroupIronmanConfig()
			{
			}.discordOnlineStatus());
		assertFalse("the board is not a post", EventSettings.from(new GroupIronmanConfig()
		{
			@Override
			public OnlineStatus discordOnlineStatus()
			{
				return OnlineStatus.LIVE_BOARD;
			}
		}).discord.contains(GroupEvent.Type.ONLINE));
	}
}
