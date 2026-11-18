package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Board 2026-11-16: the Banks tab previews each bank's top 20 by stack value; the rest wait for a search. */
public class BankPreviewTest
{
	private static final long NOW = 1_000_000_000L;
	/** Ids 1000+ are 30 distinct runes-of-nothing: "Item n", worth n gp each, so the order is the id's. */
	private static final int FIRST = 1000;

	private static final class Shop implements ItemLookup
	{
		final ItemCategoryTest.Items gear = new ItemCategoryTest.Items();

		@Override
		public String name(int id)
		{
			return id >= FIRST ? "Thing " + id : gear.name(id);
		}

		@Override
		public long price(int id)
		{
			return id >= FIRST ? id - FIRST + 1 : 100;
		}

		@Override
		public long highAlch(int id)
		{
			return id >= FIRST ? 7 : 0;
		}

		@Override
		public int combatStyles(int id)
		{
			return gear.combatStyles(id);
		}
	}

	private static GroupRoster bankOf(Map<Integer, Integer> items)
	{
		GroupRoster roster = new GroupRoster();
		roster.putBank("Alex", new BankSnapshot(NOW - 60_000, items));
		return roster;
	}

	private static Map<Integer, Integer> thirty()
	{
		Map<Integer, Integer> m = new TreeMap<>();
		for (int i = 0; i < 30; i++)
		{
			m.put(FIRST + i, 1);
		}
		return m;
	}

	private static List<Integer> ids(PanelModel.BankCard c)
	{
		List<Integer> out = new ArrayList<>();
		c.items.forEach(i -> out.add(i.id));
		return out;
	}

	@Test
	public void noSearchShowsOnlyTheTwentyMostValuableStacks()
	{
		PanelModel.BankCard c = PanelModel.banks(bankOf(thirty()), new Shop(), GridFilter.of(""), NOW).get(0);
		assertEquals(PanelModel.BANK_PREVIEW, c.items.size());
		assertEquals("most valuable first", FIRST + 29, (int) ids(c).get(0));
		assertEquals(FIRST + 10, (int) ids(c).get(19));
		assertEquals(10, c.hidden);
		assertEquals("the summary still counts the whole bank", "30 items, 465 gp, seen 1m ago", c.summary);
	}

	@Test
	public void valueIsPriceTimesQuantityNotPrice()
	{
		Map<Integer, Integer> m = thirty();
		m.put(FIRST, 100_000);
		PanelModel.BankCard c = PanelModel.banks(bankOf(m), new Shop(), GridFilter.of(""), NOW).get(0);
		assertEquals("a cheap item in bulk outranks a dear single", FIRST, (int) ids(c).get(0));
		assertEquals(100_000, c.items.get(0).value);
		assertEquals("high alchemy rides along per stack", 700_000, c.items.get(0).highAlch);
	}

	@Test
	public void aBankOfTwentyOrFewerHidesNothing()
	{
		Map<Integer, Integer> m = new TreeMap<>(thirty());
		m.keySet().removeIf(id -> id >= FIRST + 20);
		PanelModel.BankCard c = PanelModel.banks(bankOf(m), new Shop(), GridFilter.of(""), NOW).get(0);
		assertEquals(20, c.items.size());
		assertEquals(0, c.hidden);
	}

	@Test
	public void aSearchFindsItemsBeyondThePreview()
	{
		PanelModel.BankCard c = PanelModel.banks(bankOf(thirty()), new Shop(), GridFilter.of("thing"), NOW).get(0);
		assertEquals("a query is not previewed", 30, c.items.size());
		assertEquals(0, c.hidden);
		PanelModel.BankCard one = PanelModel.banks(bankOf(thirty()), new Shop(), GridFilter.of("thing 1000"), NOW).get(0);
		assertEquals(1, one.items.size());
	}

	@Test
	public void aCategoryWithoutASearchIsItsOwnTopTwentyUntilShowAll()
	{
		Shop shop = new Shop();
		Map<Integer, Integer> m = new TreeMap<>();
		for (int i = 0; i < 25; i++)
		{
			int id = 2000 + i;
			shop.gear.put(id, "Sword " + i, ItemStyles.MELEE | ItemStyles.COMBAT);
			m.put(id, 1);
		}
		m.putAll(thirty());
		GroupRoster roster = bankOf(m);

		PanelModel.BankCard top = PanelModel.banks(roster, shop, new GridFilter("", ItemCategory.MELEE, false), NOW).get(0);
		assertEquals(20, top.items.size());
		assertEquals(5, top.hidden);
		assertTrue(ids(top).stream().allMatch(id -> id >= 2000));

		PanelModel.BankCard all = PanelModel.banks(roster, shop, new GridFilter("", ItemCategory.MELEE, true), NOW).get(0);
		assertEquals(25, all.items.size());
		assertEquals(0, all.hidden);

		PanelModel.BankCard none = PanelModel.banks(roster, shop, new GridFilter("", ItemCategory.MAGIC, false), NOW).get(0);
		assertTrue(none.items.isEmpty());
		assertEquals(0, none.hidden);
	}

	@Test
	public void showAllWithNoCategoryDrawsEverything()
	{
		PanelModel.BankCard c = PanelModel.banks(bankOf(thirty()), new Shop(), new GridFilter("", ItemCategory.ALL, true),
			NOW).get(0);
		assertEquals(30, c.items.size());
	}

	@Test
	public void aCategoryAndASearchCombine()
	{
		Shop shop = new Shop();
		shop.gear.put(1, "Abyssal whip", ItemStyles.MELEE | ItemStyles.COMBAT);
		shop.gear.put(2, "Abyssal bludgeon", ItemStyles.MELEE | ItemStyles.COMBAT);
		shop.gear.put(3, "Abyssal tentacle", 0);
		Map<Integer, Integer> m = new TreeMap<>();
		m.put(1, 1);
		m.put(2, 1);
		m.put(3, 1);
		PanelModel.BankCard c = PanelModel.banks(bankOf(m), shop, new GridFilter("abyssal", ItemCategory.MELEE, false),
			NOW).get(0);
		assertEquals(2, c.items.size());
		assertFalse(ids(c).contains(3));
	}

	@Test
	public void theCardRedrawsWhenOnlyTheHiddenCountChanges()
	{
		PanelModel.BankCard a = PanelModel.banks(bankOf(thirty()), new Shop(), GridFilter.of(""), NOW).get(0);
		PanelModel.BankCard b = PanelModel.banks(bankOf(thirty()), new Shop(), new GridFilter("", ItemCategory.ALL, true),
			NOW).get(0);
		assertFalse(a.sameAs(b));
		assertTrue(a.sameAs(PanelModel.banks(bankOf(thirty()), new Shop(), GridFilter.of(""), NOW).get(0)));
	}

	@Test
	public void storageTakesTheCategoryButNotThePreview()
	{
		Shop shop = new Shop();
		shop.gear.put(1, "Abyssal whip", ItemStyles.MELEE | ItemStyles.COMBAT);
		GroupRoster roster = new GroupRoster();
		Map<Integer, Integer> in = thirty();
		in.put(1, 1);
		roster.putStorage(new StorageChange(NOW - 1000, "Sam", new TreeMap<>(), in, in));
		assertEquals(31, PanelModel.storage(roster, shop, "Me", GridFilter.of(""), NOW).items.size());
		List<PanelModel.ItemCell> melee = PanelModel.storage(roster, shop, "Me",
			new GridFilter("", ItemCategory.MELEE, false), NOW).items;
		assertEquals(1, melee.size());
		assertEquals(1, melee.get(0).id);
	}
}
