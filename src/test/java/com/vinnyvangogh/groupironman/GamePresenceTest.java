package com.vinnyvangogh.groupironman;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Online and world for every group member, from the game's own Ironman Group channel (Board 2026-11-11): members
 * in the channel are online on their world, roster members not in it are offline, and nothing is known before
 * the channel loads. The Friends list is the fallback.
 */
public class GamePresenceTest
{
	static final GroupMembers ROSTER = GroupMembers.of("Iron Pals",
		Arrays.asList("GroupOtt", "GroupArk", "GroupPNK", "GroupTrashy", "GroupShip"));

	static Map<String, Integer> worlds(Object... nameWorld)
	{
		Map<String, Integer> out = new LinkedHashMap<>();
		for (int i = 0; i < nameWorld.length; i += 2)
		{
			out.put((String) nameWorld[i], (Integer) nameWorld[i + 1]);
		}
		return out;
	}

	/** The screenshot: GroupOtt on W615 and GroupShip on W303, the other three '---'. */
	static GamePresence twoOfFive()
	{
		return GamePresence.read(ROSTER, worlds("GroupOtt", 615, "GroupShip", 303), null);
	}

	@Test
	public void channelMembersAreOnlineAndTheRestOfTheRosterOffline()
	{
		GamePresence game = twoOfFive();
		assertTrue(game.known());
		assertEquals(GamePresence.Source.CHANNEL, game.source());
		assertEquals(GamePresence.State.ONLINE, game.state("GroupOtt"));
		assertEquals(615, game.world("GroupOtt"));
		assertEquals(GamePresence.State.ONLINE, game.state("groupship"));
		assertEquals(303, game.world("GroupShip"));
		for (String off : Arrays.asList("GroupArk", "GroupPNK", "GroupTrashy"))
		{
			assertEquals(off, GamePresence.State.OFFLINE, game.state(off));
			assertEquals(0, game.world(off));
		}
		assertEquals(GamePresence.State.UNKNOWN, game.state("Stranger"));
		assertEquals(worlds("GroupOtt", 615, "GroupShip", 303), game.online());
	}

	@Test
	public void beforeTheChannelLoadsNothingIsKnown()
	{
		GamePresence game = GamePresence.read(ROSTER, null, null);
		assertFalse(game.known());
		assertEquals(GamePresence.State.UNKNOWN, game.state("GroupOtt"));
		assertTrue(game.online().isEmpty());
		assertFalse(GamePresence.UNKNOWN.known());
	}

	@Test
	public void theFriendsListIsTheFallbackOnlyWithoutTheChannel()
	{
		Map<String, Integer> friends = worlds("GroupOtt", 615, "GroupArk", 0, "Someone Else", 420);
		GamePresence game = GamePresence.read(ROSTER, null, friends);
		assertEquals(GamePresence.Source.FRIENDS, game.source());
		assertEquals(GamePresence.State.ONLINE, game.state("GroupOtt"));
		assertEquals(615, game.world("GroupOtt"));
		assertEquals(GamePresence.State.OFFLINE, game.state("GroupArk"));
		// Not a friend: the list says nothing about them.
		assertEquals(GamePresence.State.UNKNOWN, game.state("GroupPNK"));
		// A friend outside the group is never listed.
		assertEquals(GamePresence.State.UNKNOWN, game.state("Someone Else"));
		assertEquals(worlds("GroupOtt", 615), game.online());

		assertEquals(GamePresence.Source.CHANNEL, GamePresence.read(ROSTER, worlds("GroupShip", 303), friends).source());
		// Without the roster, friends cannot tell who is in the group.
		assertFalse(GamePresence.read(GroupMembers.UNKNOWN, null, friends).known());
	}

	@Test
	public void theChannelWithoutTheRosterSpeaksOnlyForWhoIsInIt()
	{
		GamePresence game = GamePresence.read(GroupMembers.UNKNOWN, worlds("GroupOtt", 615), null);
		assertEquals(GamePresence.State.ONLINE, game.state("GroupOtt"));
		assertEquals(GamePresence.State.UNKNOWN, game.state("GroupArk"));
	}

	@Test
	public void namesMatchTheWayTheGameSpellsThem()
	{
		GamePresence game = GamePresence.read(GroupMembers.of("g", Collections.singletonList("Iron Pal")),
			worlds("Iron Pal", 302), null);
		assertEquals(GamePresence.State.ONLINE, game.state("iron_pal"));
		assertEquals(worlds("Iron Pal", 302), game.online());
	}

	@Test
	public void othersOnlineJoinsThePluginAndTheGameWithoutYou()
	{
		Map<String, MemberStats> plugin = new LinkedHashMap<>();
		plugin.put("GroupShip", new MemberStats(Collections.emptyMap(), 100, 100, 300, 0));
		plugin.put("GroupArk", new MemberStats(Collections.emptyMap(), 100, 100, 330, 0));
		Map<String, Integer> others = GamePresence.othersOnline(plugin, twoOfFive(), "GroupShip");
		// GroupOtt from the game; GroupArk's client went quiet but the game says offline; you are left out.
		assertEquals(worlds("GroupOtt", 615), others);

		Map<String, Integer> unknown = GamePresence.othersOnline(plugin, GamePresence.UNKNOWN, "GroupShip");
		assertEquals(worlds("GroupArk", 330), unknown);
	}
}
