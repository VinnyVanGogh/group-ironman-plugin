package com.vinnyvangogh.groupironman;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static com.vinnyvangogh.groupironman.FakeItems.*;

public class GroupSearchTest
{
	private static final long NOW = TimeUnit.DAYS.toMillis(100);
	private static final FakeItems ITEMS = new FakeItems();

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
	public void findsAnItemAcrossBanksInventoriesEquipmentAndStorage()
	{
		GroupRoster roster = new GroupRoster();
		roster.putBank("Alex", new BankSnapshot(NOW - TimeUnit.HOURS.toMillis(2), items(SHARK, 120, COAL, 5)));
		roster.putCarried("Sam", new GroupCodec.Carried(items(SHARK, 5), items(WHIP, 1)));
		roster.putStorage(new StorageChange(NOW - 60_000 * 3, "Sam", items(SHARK, 1), items(), items(SHARK, 40)));

		List<String> hits = GroupSearch.search("shark", roster.searchSources(), ITEMS, NOW);
		assertEquals(Collections.singletonList(
			"Shark: Alex bank 120 (2h ago), Sam inventory 5, Group storage 40 (3m ago)"), hits);

		assertEquals(Collections.singletonList("Abyssal whip: Sam equipment 1"),
			GroupSearch.search("WHIP", roster.searchSources(), ITEMS, NOW));
	}

	@Test
	public void substringMatchesEveryVariantSortedByName()
	{
		List<GroupSearch.Source> sources = Collections.singletonList(
			new GroupSearch.Source("Alex", "bank", items(PRAYER_POT_4, 10, PRAYER_POT_3, 2, SHARK, 1), 0));
		assertEquals(Arrays.asList("Prayer potion(3): Alex bank 2 (100d ago)", "Prayer potion(4): Alex bank 10 (100d ago)"),
			GroupSearch.search("prayer pot", sources, ITEMS, NOW));
	}

	@Test
	public void aOneLetterQueryReturnsNothing()
	{
		List<GroupSearch.Source> sources = Collections.singletonList(
			new GroupSearch.Source("Alex", "bank", items(SHARK, 1), 0));
		assertTrue(GroupSearch.search("s", sources, ITEMS, NOW).isEmpty());
		assertTrue(GroupSearch.search("  ", sources, ITEMS, NOW).isEmpty());
		assertTrue(GroupSearch.search(null, sources, ITEMS, NOW).isEmpty());
	}

	@Test
	public void noMatchIsEmpty()
	{
		List<GroupSearch.Source> sources = Collections.singletonList(
			new GroupSearch.Source("Alex", "bank", items(SHARK, 1), 0));
		assertTrue(GroupSearch.search("dragon", sources, ITEMS, NOW).isEmpty());
	}
}
