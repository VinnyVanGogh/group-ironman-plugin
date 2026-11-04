package com.vinnyvangogh.groupironman;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static com.vinnyvangogh.groupironman.FakeItems.*;

public class GroupRosterTest
{
	private static final long NOW = TimeUnit.DAYS.toMillis(10);

	private static Map<Integer, Integer> items(int... pairs)
	{
		Map<Integer, Integer> out = new TreeMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			out.put(pairs[i], pairs[i + 1]);
		}
		return out;
	}

	@Test
	public void bankLineHasCountValueAndLastSeen()
	{
		GroupRoster roster = new GroupRoster();
		roster.putBank("Alex", new BankSnapshot(NOW - TimeUnit.HOURS.toMillis(2), items(SHARK, 100, WHIP, 2)));
		// 100 x 800 + 2 x 1,500,000 = 3,080,000
		assertEquals(Collections.singletonList("Alex: 2 kinds, 102 items, 3.1M gp (last seen 2h ago)"),
			roster.bankLines(new FakeItems(), NOW));
	}

	@Test
	public void anOlderBankDoesNotReplaceANewerOne()
	{
		GroupRoster roster = new GroupRoster();
		roster.putBank("Alex", new BankSnapshot(200, items(SHARK, 1)));
		assertFalse(roster.putBank("Alex", new BankSnapshot(100, items(COAL, 1))));
		assertEquals(items(SHARK, 1), roster.banks().get("Alex").items());
	}

	@Test
	public void statsShowBoostsDrainsAndStatus()
	{
		Map<String, MemberStats.Level> skills = new LinkedHashMap<>();
		skills.put("Attack", new MemberStats.Level(90, 98));
		skills.put("Defence", new MemberStats.Level(1, 1));
		skills.put("Hitpoints", new MemberStats.Level(99, 72));
		skills.put("Prayer", new MemberStats.Level(70, 67));
		GroupRoster roster = new GroupRoster();
		roster.putStats("Alex", new MemberStats(skills, 87, 50, 302, 12850));

		List<List<String>> blocks = roster.statsBlocks();
		assertEquals(Arrays.asList(
			"Alex: world 302, region 12850",
			"HP 72/99, Prayer 67/70, Run 87%, Spec 50%",
			"Attack 90 (+8), Defence 1, Hitpoints 99 (-27), Prayer 70 (-3)"), blocks.get(0));
	}

	@Test
	public void leavingThePartyKeepsBanksButDropsLiveData()
	{
		GroupRoster roster = new GroupRoster();
		roster.putBank("Alex", new BankSnapshot(1, items(SHARK, 1)));
		roster.putCarried("Alex", new GroupCodec.Carried(items(COAL, 1), items()));
		roster.putStats("Alex", new MemberStats(Collections.emptyMap(), 0, 0, 0, 0));
		roster.clearLive();
		assertEquals(1, roster.banks().size());
		assertEquals(1, roster.searchSources().size());
		assertEquals(0, roster.statsBlocks().size());
	}
}
