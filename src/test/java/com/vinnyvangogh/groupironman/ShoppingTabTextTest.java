package com.vinnyvangogh.groupironman;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** What each Shopping line says: one compact line, plain words, details behind a click. */
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

	/** Alice needs 5 and holds 1; Tin needs 3 and shares no items. */
	private static ShopAllocation.Line withUnshared(long aliceHolds)
	{
		Map<String, Map<Integer, ShopNeeds.Need>> needs = new LinkedHashMap<>();
		needs.put("Alice", Collections.singletonMap(954,
			new ShopNeeds.Need(954, "Rope", 5, true, false, Collections.singleton("Q"))));
		needs.put("Tin", Collections.singletonMap(954,
			new ShopNeeds.Need(954, "Rope", 3, true, false, Collections.singleton("Q"))));
		return ShopAllocation.of(needs, new ShopAllocation.Holdings(Collections.singletonMap("Alice",
			Collections.singletonMap(954, aliceHolds)), Collections.emptyMap()), id -> true).get(0);
	}

	private static ShoppingBoard.Row row(ShopAllocation.Line l)
	{
		return new ShoppingBoard.Row(l, new ShopSources.Options(Collections.emptyList(), 0));
	}

	@Test
	public void aLineSaysHaveOfNeedThenHowManyMore()
	{
		assertEquals("Rope  3 / 5", ShoppingTab.summary(line(5, 1, 2, true)));
		assertEquals("Need 2 more", ShoppingTab.needText(line(5, 1, 2, true)));
		assertEquals("Rope  0 / 30", ShoppingTab.summary(line(30, 0, 0, true)));
		assertEquals("Need 30 more", ShoppingTab.needText(line(30, 0, 0, true)));
	}

	@Test
	public void aDoneLineSaysDoneAndAnUnstatedCountIsAPlus()
	{
		assertEquals("Rope  1 / 1+", ShoppingTab.summary(line(1, 1, 0, false)));
		assertEquals("Done", ShoppingTab.needText(line(1, 1, 0, false)));
	}

	@Test
	public void aMemberWhoSharesNoItemsIsExplainedNotJudged()
	{
		assertEquals("Need 4 more, plus 3 for Tin (shares no items, may already have them)",
			ShoppingTab.needText(withUnshared(1)));
		assertEquals("Need 3 for Tin (shares no items, may already have them)", ShoppingTab.needText(withUnshared(5)));
	}

	@Test
	public void detailsSayWhoHoldsItAndWhereToGetIt()
	{
		assertEquals(Arrays.asList("Held by Alice 1, group storage 2",
			"Not sold in any shop, and no drop on file: check the wiki."), ShoppingTab.details(row(line(5, 1, 2, true))));
		// Done: nothing more to get, so no "where".
		assertEquals(Collections.singletonList("Held by group storage 1"), ShoppingTab.details(row(line(1, 0, 1, true))));
		assertEquals("", ShoppingTab.heldText(line(5, 0, 0, true)));
	}

	@Test
	public void whereToGetIt()
	{
		ShopSources.Options o = new ShopSources.Options(Arrays.asList(
			new ShopSources.Source(true, "General Store", 5, ""),
			new ShopSources.Source(false, "Goblin", -1, "Always"),
			new ShopSources.Source(false, "Imp", -1, ""),
			new ShopSources.Source(false, "Rat", -1, ""),
			new ShopSources.Source(false, "Cow", -1, "")), 1);
		assertEquals("Get: Shop: General Store (5 gp); Drop: Goblin (Always); Drop: Imp; Drop: Rat; 1 more",
			ShoppingTab.sourceText(o));
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

	@Test
	public void progressReadsAsDoneOfTotal()
	{
		assertEquals("0 of 39 done", ShopCards.progress(0, 39));
		assertEquals("Done: 39 of 39", ShopCards.progress(39, 39));
	}
}
