package com.vinnyvangogh.groupironman;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static com.vinnyvangogh.groupironman.FakeItems.*;

public class GroupRosterTest
{
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
	public void theGroupBoxListsOthersOnlineNotYou()
	{
		GroupRoster roster = new GroupRoster();
		MemberStats s = new MemberStats(Collections.emptyMap(), 0, 0, 302, 0);
		roster.putStats("GroupShip", s);
		roster.heard("GroupShip", 1000);
		roster.putStats("Alex", s);
		roster.heard("Alex", 1000);
		roster.putStats("Zed", s);
		roster.heard("Zed", 1000 - Presence.ONLINE_MS - 1);

		assertEquals(Collections.singleton("Alex"), roster.othersOnlineStats("groupship", 1000).keySet());
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
	public void clearLiveDropsStatsAndCarriedButKeepsBanks()
	{
		GroupRoster roster = new GroupRoster();
		roster.putBank("Alex", new BankSnapshot(1, items(SHARK, 1)));
		roster.putCarried("Alex", new GroupCodec.Carried(items(COAL, 1), items()));
		roster.putStats("Alex", new MemberStats(Collections.emptyMap(), 0, 0, 0, 0));
		roster.clearLive();
		assertEquals(1, roster.banks().size());
		assertEquals(1, roster.searchSources().size());
		assertEquals(0, roster.allStats().size());
		assertEquals(0, roster.allCarried().size());
	}

	@Test
	public void othersOnlineLeavesOutYouAndTheLongGone()
	{
		long now = 10_000_000L;
		GroupRoster roster = new GroupRoster();
		roster.heard("Me", now);
		roster.heard("Alex", now - 1_000);
		roster.heard("Sam", now - Presence.ONLINE_MS - 1);
		assertEquals(1, roster.othersOnline("me", now));
	}

	@Test
	public void strangersAreListedUntilThePartyChanges()
	{
		GroupRoster roster = new GroupRoster();
		roster.stranger("Mallory");
		assertEquals(Collections.singleton("Mallory"), roster.strangers());
		roster.clearStrangers();
		assertEquals(0, roster.strangers().size());
	}
}
