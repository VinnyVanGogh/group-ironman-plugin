package com.vinnyvangogh.groupironman;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Group holdings subtract from what a list needs: own first, then spares, then storage. */
public class ShopAllocationTest
{
	private static final int ROPE = 954;
	private static final int ROPE_NOTED = 955;
	private static final int AMULET = 1478;

	private static ShopNeeds.Need need(int id, String name, long count)
	{
		return new ShopNeeds.Need(id, name, count, true, false, Collections.singleton("Quest"));
	}

	private static Map<String, Map<Integer, ShopNeeds.Need>> needs(String... members)
	{
		Map<String, Map<Integer, ShopNeeds.Need>> out = new LinkedHashMap<>();
		for (String m : members)
		{
			Map<Integer, ShopNeeds.Need> n = new LinkedHashMap<>();
			n.put(ROPE, need(ROPE, "Rope", 2));
			out.put(m, n);
		}
		return out;
	}

	private static Map<Integer, Long> items(int id, long qty)
	{
		Map<Integer, Long> m = new TreeMap<>();
		m.put(id, qty);
		return m;
	}

	@Test
	public void holdingsSubtractAcrossMembersAndStorage()
	{
		Map<String, Map<Integer, Long>> held = new LinkedHashMap<>();
		held.put("Alice", items(ROPE, 1));
		held.put("Bob", Collections.emptyMap());
		held.put("Cara", items(ROPE, 1));
		ShopAllocation.Holdings h = new ShopAllocation.Holdings(held, items(ROPE, 1));

		ShopAllocation.Line line = ShopAllocation.of(needs("Alice", "Bob"), h, id -> true).get(0);

		assertEquals(4, line.totalNeeded());
		// Alice's own 1, Cara's spare 1 (she needs none), then 1 from storage: 3 held, 1 to get.
		assertEquals(3, line.have());
		assertEquals(1, line.stillNeeded());
		assertEquals(1, line.storageUsed);
		assertFalse(line.done());
		assertEquals(Arrays.asList("Alice", "Cara"), Arrays.asList(line.holders.keySet().toArray()));
	}

	@Test
	public void aLineTicksOffWhenTheGroupHoldsEnough()
	{
		Map<String, Map<Integer, Long>> held = new LinkedHashMap<>();
		held.put("Alice", items(ROPE, 4));
		held.put("Bob", Collections.emptyMap());
		ShopAllocation.Line line = ShopAllocation.of(needs("Alice", "Bob"), new ShopAllocation.Holdings(held,
			Collections.emptyMap()), id -> true).get(0);
		assertTrue(line.done());
		assertEquals(0, line.stillNeeded());
	}

	@Test
	public void untradeableItemsOnlyCountTheirOwnHolder()
	{
		Map<String, Map<Integer, Long>> held = new LinkedHashMap<>();
		held.put("Alice", items(ROPE, 4));
		held.put("Bob", Collections.emptyMap());
		ShopAllocation.Line line = ShopAllocation.of(needs("Alice", "Bob"), new ShopAllocation.Holdings(held,
			items(ROPE, 10)), id -> false).get(0);
		assertEquals(2, line.stillNeeded());
		assertEquals(0, line.storageUsed);
	}

	@Test
	public void aMemberWhoSharesNoItemsIsNotJudged()
	{
		Map<String, Map<Integer, Long>> held = new LinkedHashMap<>();
		held.put("Alice", Collections.emptyMap());
		ShopAllocation.Line line = ShopAllocation.of(needs("Alice", "Bob"), new ShopAllocation.Holdings(held,
			Collections.emptyMap()), id -> true).get(0);
		assertEquals(2, line.stillNeeded());
		assertEquals(2, line.unknownNeed());
		assertFalse(line.done());
	}

	@Test
	public void notedItemsCountAsTheItem()
	{
		Map<Integer, Integer> bank = new TreeMap<>();
		// The capture path: every container slot goes through ItemCounts.add with the item manager's canonicalize.
		ItemCounts.add(bank, ROPE_NOTED, 3, id -> id == ROPE_NOTED ? ROPE : id);
		ItemCounts.add(bank, ROPE, 1, id -> id == ROPE_NOTED ? ROPE : id);
		List<GroupSearch.Source> sources = Arrays.asList(
			new GroupSearch.Source("Alice", "bank", bank, 1L),
			new GroupSearch.Source("Group", ShopAllocation.STORAGE_WHERE, Collections.singletonMap(AMULET, 1), 1L));

		ShopAllocation.Holdings h = ShopAllocation.Holdings.from(sources);

		assertEquals(4, h.held("Alice", ROPE));
		assertEquals(Long.valueOf(1), h.storage.get(AMULET));
		assertFalse("storage is not a member", h.known("Group"));
	}

	@Test
	public void membersMatchTheWayTheRosterDoes()
	{
		Map<String, Map<Integer, Long>> held = new LinkedHashMap<>();
		held.put("Iron Bob", items(ROPE, 2));
		ShopAllocation.Holdings h = new ShopAllocation.Holdings(held, Collections.emptyMap());
		assertTrue(h.known("iron_bob"));
		assertEquals(2, h.held("Iron_Bob", ROPE));
	}
}
