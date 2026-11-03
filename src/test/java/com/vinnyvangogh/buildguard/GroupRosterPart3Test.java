package com.vinnyvangogh.buildguard;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static com.vinnyvangogh.buildguard.FakeItems.*;

/** Part 3 in the roster: online / last seen, where members are, their extras, and what survives a restart. */
public class GroupRosterPart3Test
{
	private static final Gson GSON = new Gson();
	private static final long NOW = 1_762_000_000_000L;
	private static final long MIN = TimeUnit.MINUTES.toMillis(1);
	private static final int LAW = 563;
	private static final int SEED = 5295;

	private static Map<Integer, Integer> items(int... pairs)
	{
		Map<Integer, Integer> out = new TreeMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			out.put(pairs[i], pairs[i + 1]);
		}
		return out;
	}

	private static MemberStats at(int world, int region)
	{
		return new MemberStats(Collections.emptyMap(), 100, 100, world, region, 3222, 3218, 0);
	}

	@Test
	public void presenceReadsOnlineOrLastSeen()
	{
		assertEquals("not seen yet", Presence.describe(0, NOW));
		assertEquals("online", Presence.describe(NOW - MIN, NOW));
		assertTrue(Presence.online(NOW - Presence.ONLINE_MS, NOW));
		assertEquals("last seen 10m ago", Presence.describe(NOW - 10 * MIN, NOW));
	}

	@Test
	public void memberLinesSayWhereOnlineMembersAre()
	{
		GroupRoster roster = new GroupRoster();
		RegionNames regions = RegionNames.parse("Lumbridge=12850\n");
		roster.heard("Alex", NOW - MIN);
		roster.putStats("Alex", at(302, 12850));
		roster.heard("Sam", NOW - 3 * 60 * MIN);
		roster.putStats("Sam", at(330, 12850));
		roster.heard("Kim", NOW - MIN);
		roster.putStats("Kim", at(303, 99));
		List<String> lines = roster.memberLines(regions, NOW);
		assertEquals("Alex: online, world 302, Lumbridge", lines.get(0));
		assertEquals("Kim: online, world 303, region 99", lines.get(1));
		assertEquals("an offline member's old position is not shown", "Sam: last seen 3h ago", lines.get(2));
	}

	@Test
	public void heardOnlyMovesForward()
	{
		GroupRoster roster = new GroupRoster();
		roster.heard("Alex", NOW);
		roster.heard("Alex", NOW - 60 * MIN);
		assertEquals(NOW, (long) roster.lastHeard().get("Alex"));
	}

	@Test
	public void aRestartedSenderDoesNotWipeTheSeedVaultWeKnow()
	{
		GroupRoster roster = new GroupRoster();
		roster.putExtras("Alex", new MemberExtras(items(LAW, 10), items(), items(SEED, 5), NOW - 60 * MIN));
		// Alex restarted and has not opened the vault since: the vault is unknown, not empty.
		roster.putExtras("Alex", new MemberExtras(items(LAW, 9), items(), items(), -1));
		MemberExtras now = roster.extras().get("Alex");
		assertEquals(items(LAW, 9), now.runePouch);
		assertEquals(items(SEED, 5), now.seedVault);
		assertEquals(NOW - 60 * MIN, now.seedVaultAt);
	}

	@Test
	public void extrasAreSearchable()
	{
		GroupRoster roster = new GroupRoster();
		roster.putExtras("Alex", new MemberExtras(items(SHARK, 1), items(COAL, 2), items(WHIP, 3), NOW));
		List<String> hits = GroupSearch.search("sha", roster.searchSources(), new FakeItems(), NOW);
		assertEquals(1, hits.size());
		assertTrue(hits.get(0), hits.get(0).contains("rune pouch"));
		assertTrue(GroupSearch.search("coal", roster.searchSources(), new FakeItems(), NOW).get(0).contains("quiver"));
		assertTrue(GroupSearch.search("whip", roster.searchSources(), new FakeItems(), NOW).get(0).contains("seed vault"));
	}

	@Test
	public void carriedLinesListWornAndCarriedItems()
	{
		GroupRoster roster = new GroupRoster();
		roster.putCarried("Alex", new GroupCodec.Carried(items(SHARK, 20), items(WHIP, 1)));
		roster.putExtras("Alex", new MemberExtras(items(COAL, 100), items(), items(SHARK, 4), NOW - 120 * MIN));
		List<String> lines = roster.itemLines(new FakeItems(), NOW);
		assertEquals("Alex wearing: 1 x Abyssal whip", lines.get(0));
		assertEquals("Alex carrying: 20 x Shark", lines.get(1));
		assertEquals("Alex rune pouch: 100 x Coal", lines.get(2));
		assertEquals("Alex seed vault: 1 kinds, 4 seeds (last seen 2h ago)", lines.get(3));
	}

	@Test
	public void extrasEventsHeardAndLastPlayedSurviveARestart()
	{
		GroupRoster before = new GroupRoster();
		before.putExtras("Alex", new MemberExtras(items(LAW, 10), items(), items(SEED, 5), NOW - MIN));
		before.events().add(GroupEvent.drop(NOW - 5 * MIN, "Alex", "Vorkath", items(WHIP, 1)));
		before.events().add(GroupEvent.clog(NOW - 4 * MIN, "Sam", "Draconic visage"));
		before.heard("Sam", NOW - 2 * MIN);
		before.markPlayed(NOW - 3 * MIN);
		before.putStats("Sam", at(301, 12850));

		GroupRoster after = new GroupRoster();
		assertTrue(GroupCache.read(GSON, GroupCache.write(GSON, before), after));
		assertEquals(before.extras(), after.extras());
		assertEquals(2, after.events().entries().size());
		assertEquals("Draconic visage", after.events().entries().get(0).text);
		assertEquals("Alex", after.events().entries().get(1).member);
		assertEquals(items(WHIP, 1), after.events().entries().get(1).items);
		assertEquals(NOW - 2 * MIN, (long) after.lastHeard().get("Sam"));
		assertEquals(NOW - 3 * MIN, after.lastPlayed());
		assertTrue("live stats are not kept", after.statsFor("Sam") == null);
	}

	@Test
	public void aPart2CacheStillLoads()
	{
		GroupRoster after = new GroupRoster();
		assertTrue(GroupCache.read(GSON, "{\"v\":1,\"banks\":{},\"log\":[]}", after));
		assertEquals(0, after.lastPlayed());
		assertTrue(after.events().entries().isEmpty());
	}
}
