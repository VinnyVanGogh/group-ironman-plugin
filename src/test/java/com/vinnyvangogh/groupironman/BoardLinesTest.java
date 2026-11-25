package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The live board's lines, which GIM works out from the group and hands to Discord Relay ({@link BoardLines}).
 * Posting and editing the message is Discord Relay's, tested there (StatusBoardTest moved with it).
 */
public class BoardLinesTest
{
	private static final Gson GSON = new Gson();

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
		BoardLines.Self me = new BoardLines.Self("Alex", false, 301, NOW - 120_000);
		String lines = BoardLines.lines(roster, heard, stats, me, NOW);
		assertEquals(String.join("\n",
			BoardLines.OFFLINE + " **Alex** last seen <t:" + (NOW - 120_000) / 1000 + ":R> on world 301",
			BoardLines.OFFLINE + " **Jo** last seen <t:" + (NOW - Presence.ONLINE_MS - 60_000) / 1000 + ":R> on world 330",
			BoardLines.UNKNOWN + " **Kim** unknown",
			BoardLines.ONLINE + " **Sam** online (world 302)"), lines);

		BoardLines.Self hopped = new BoardLines.Self("Alex", true, 444, 0);
		assertTrue(BoardLines.lines(roster, heard, stats, hopped, NOW)
			.startsWith(BoardLines.ONLINE + " **Alex** online (world 444)"));
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
		BoardLines.Self me = new BoardLines.Self("GroupShip", true, 303, 0);
		String lines = BoardLines.lines(GamePresenceTest.ROSTER, heard, stats, me, GamePresenceTest.twoOfFive(), NOW);
		assertEquals(String.join("\n",
			// Heard 30 seconds ago, but the game says logged out: the game wins.
			BoardLines.OFFLINE + " **GroupArk** last seen <t:" + (NOW - 30_000) / 1000 + ":R> on world 330",
			BoardLines.ONLINE + " **GroupOtt** online (world 615)" + BoardLines.NO_PLUGIN,
			BoardLines.OFFLINE + " **GroupPNK** offline" + BoardLines.NO_PLUGIN,
			BoardLines.ONLINE + " **GroupShip** online (world 303)",
			BoardLines.OFFLINE + " **GroupTrashy** offline" + BoardLines.NO_PLUGIN), lines);
		assertEquals(2, lines.split(BoardLines.ONLINE, -1).length - 1);
		assertEquals(3, lines.split(BoardLines.OFFLINE, -1).length - 1);
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
		BoardLines.Self me = new BoardLines.Self("GroupShip", true, 303, 0);
		String lines = BoardLines.lines(GamePresenceTest.ROSTER, heard, seen, stats, me,
			GamePresenceTest.twoOfFive(), NOW);
		assertEquals(String.join("\n",
			BoardLines.OFFLINE + " **GroupArk** last seen " + t(NOW - 60_000) + " on world 331",
			BoardLines.ONLINE + " **GroupOtt** online (world 615)" + BoardLines.NO_PLUGIN,
			BoardLines.OFFLINE + " **GroupPNK** last seen " + t(NOW - 3_600_000) + " on world 420"
				+ BoardLines.NO_PLUGIN,
			BoardLines.ONLINE + " **GroupShip** online (world 303)",
			BoardLines.OFFLINE + " **GroupTrashy** offline" + BoardLines.NO_PLUGIN), lines);
		assertFalse("a plugin user is online", lines.contains(BoardLines.STALE));
	}

	/**
	 * Board 2026-11-11: the last plugin user logs out. Its final lines keep every last seen, show the members no one
	 * can see now as unknown, and say the board may be out of date as of the logout; they do not change as time
	 * passes, so Discord Relay is not asked to edit again.
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
		BoardLines.Self me = new BoardLines.Self("GroupShip", false, 303, logout);
		String lines = BoardLines.lines(GamePresenceTest.ROSTER, heard, seen, stats, me, GamePresence.UNKNOWN,
			NOW);
		assertEquals(String.join("\n",
			BoardLines.OFFLINE + " **GroupArk** last seen " + t(NOW - 7_200_000) + " on world 330",
			BoardLines.UNKNOWN + " **GroupOtt** last seen " + t(logout - 600) + " on world 615"
				+ BoardLines.NO_PLUGIN,
			BoardLines.UNKNOWN + " **GroupPNK** unknown",
			BoardLines.OFFLINE + " **GroupShip** last seen " + t(logout) + " on world 303",
			BoardLines.UNKNOWN + " **GroupTrashy** unknown",
			"*" + BoardLines.STALE + " (as of " + t(logout) + ")*"), lines);
		assertEquals(lines, BoardLines.lines(GamePresenceTest.ROSTER, heard, seen, stats, me, GamePresence.UNKNOWN,
			NOW + 3_600_000));
	}

	@Test
	public void anotherPluginUserOnlineKeepsTheBoardCurrent()
	{
		Map<String, Long> heard = Collections.singletonMap("GroupArk", NOW - 1_000);
		BoardLines.Self me = new BoardLines.Self("GroupShip", false, 303, NOW - 5_000);
		String lines = BoardLines.lines(GamePresenceTest.ROSTER, heard, Collections.emptyMap(),
			Collections.emptyMap(), me, GamePresence.UNKNOWN, NOW);
		assertFalse(lines, lines.contains(BoardLines.STALE));
		assertTrue(lines.contains(BoardLines.ONLINE + " **GroupArk** online"));
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

		String lines = BoardLines.lines(GroupMembers.UNKNOWN, after.lastHeard(), after.lastSeen(),
			after.allStats(), null, GamePresence.UNKNOWN, NOW);
		assertEquals(String.join("\n",
			BoardLines.UNKNOWN + " **GroupOtt** last seen " + t(NOW - 3_600_000) + " on world 615"
				+ BoardLines.NO_PLUGIN,
			BoardLines.OFFLINE + " **GroupShip** last seen " + t(NOW - 60_000) + " on world 303",
			"*" + BoardLines.STALE + " (as of " + t(NOW - 60_000) + ")*"), lines);
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
		BoardLines.Self me = new BoardLines.Self("GroupShip", true, 303, 0);
		String lines = BoardLines.lines(GamePresenceTest.ROSTER, Collections.emptyMap(), Collections.emptyMap(), me,
			GamePresence.UNKNOWN, NOW);
		assertTrue(lines, lines.contains(BoardLines.UNKNOWN + " **GroupOtt** unknown"));
		assertFalse(lines.contains(BoardLines.NO_PLUGIN));
	}

	@Test
	public void withoutTheRosterTheChannelStillListsWhoIsOn()
	{
		BoardLines.Self me = new BoardLines.Self("GroupShip", true, 303, 0);
		GamePresence game = GamePresence.read(GroupMembers.UNKNOWN,
			GamePresenceTest.worlds("GroupOtt", 615, "GroupShip", 303), null);
		assertEquals(BoardLines.ONLINE + " **GroupOtt** online (world 615)" + BoardLines.NO_PLUGIN + "\n"
			+ BoardLines.ONLINE + " **GroupShip** online (world 303)",
			BoardLines.lines(GroupMembers.UNKNOWN, Collections.emptyMap(), Collections.emptyMap(), me, game, NOW));
	}

	@Test
	public void withoutTheRosterOnlyMembersHeardFromAreListed()
	{
		Map<String, Long> heard = Collections.singletonMap("Sam", NOW);
		BoardLines.Self me = new BoardLines.Self("Alex", true, 301, 0);
		assertEquals(BoardLines.ONLINE + " **Alex** online (world 301)\n" + BoardLines.ONLINE + " **Sam** online",
			BoardLines.lines(GroupMembers.UNKNOWN, heard, Collections.emptyMap(), me, NOW));
		assertEquals("", BoardLines.lines(GroupMembers.UNKNOWN, Collections.emptyMap(), Collections.emptyMap(), null,
			NOW));
	}

	@Test
	public void theBoardWaitsForItsInputsToSettle()
	{
		assertFalse("before a login", BoardLines.settled(0, NOW, true));
		assertFalse("too soon", BoardLines.settled(NOW, NOW + BoardLines.SETTLE_MS - 1, true));
		assertFalse("cache not read", BoardLines.settled(NOW, NOW + BoardLines.SETTLE_MS, false));
		assertTrue(BoardLines.settled(NOW, NOW + BoardLines.SETTLE_MS, true));
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
