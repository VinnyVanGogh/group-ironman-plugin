package com.vinnyvangogh.groupironman;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** What each Shopping line says. */
public class ShoppingTabTextTest
{
	private static ShopAllocation.Line line(long need, long heldByAlice, long storage, boolean stated)
	{
		Map<String, Map<Integer, ShopNeeds.Need>> needs = new LinkedHashMap<>();
		needs.put("Alice", Collections.singletonMap(954,
			new ShopNeeds.Need(954, "Rope", need, stated, false, Collections.singleton("Q"))));
		Map<String, Map<Integer, Long>> held = new LinkedHashMap<>();
		held.put("Alice", Collections.singletonMap(954, heldByAlice));
		return ShopAllocation.of(needs, new ShopAllocation.Holdings(held, Collections.singletonMap(954, storage)),
			id -> true).get(0);
	}

	private static ShoppingBoard.Row row(ShopAllocation.Line l)
	{
		return new ShoppingBoard.Row(l, new ShopSources.Options(Collections.emptyList(), 0));
	}

	@Test
	public void aLineSaysNeedHaveAndGet()
	{
		assertEquals("Rope: need 5, have 3, get 2", ShoppingTab.rowText(row(line(5, 1, 2, true))));
		assertEquals("Rope: done (at least 1)", ShoppingTab.rowText(row(line(1, 1, 0, false))));
	}

	@Test
	public void whoHoldsIt()
	{
		assertEquals("Held: Alice 1, group storage 2", ShoppingTab.heldText(line(5, 1, 2, true)));
		assertEquals("", ShoppingTab.heldText(line(5, 0, 0, true)));
	}

	@Test
	public void whereToGetIt()
	{
		ShopSources.Options o = new ShopSources.Options(Arrays.asList(
			new ShopSources.Source(true, "General Store", 5, ""),
			new ShopSources.Source(false, "Goblin", -1, "Always"),
			new ShopSources.Source(false, "Imp", -1, "")), 1);
		assertEquals("Get: Shop: General Store (5 gp); Drop: Goblin (Always); 1 more", ShoppingTab.sourceText(o));
		assertEquals("No shop you can use: 2 need levels or quests you lack.",
			ShoppingTab.sourceText(new ShopSources.Options(Collections.emptyList(), 2)));
	}

	@Test
	public void theBylineSaysWhoDidIt()
	{
		ShoppingList l = ShoppingList.create("Ropes", "", Collections.emptyList(), Arrays.asList("Alice", "Bob"),
			Collections.singletonMap(954, 1), "Alice", 0).renamed("More ropes", "Bob", 0);
		assertEquals("For Alice, Bob. Renamed by Bob, " + BoostText.ago(0, 0) + ".", ShoppingTab.byline(l, 0));
	}
}
