package com.vinnyvangogh.groupironman;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The Find tab's quick-find: category, then sub-type, then who or where, plus the one-click presets. */
public class FindQuickTest
{
	private static final long NOW = 1_000_000_000L;

	private static ItemCategoryTest.Items items()
	{
		ItemCategoryTest.Items i = new ItemCategoryTest.Items();
		int id = 1;
		for (String n : new String[]{"Air rune", "Fire rune", "Nature rune", "Rune scimitar", "Shark", "Raw shark",
			"Apple pie", "Prayer potion(4)", "Super restore(3)", "Stamina potion(2)", "Guam potion (unf)",
			"Varrock teleport", "Teleport to house", "Ring of dueling(8)", "Amulet of glory(4)", "Coal", "Yew logs",
			"Iron ore", "Grimy ranarr weed", "Big bones", "Trout", "Ranging potion(4)"})
		{
			i.put(id++, n, 0);
		}
		i.put(100, "Abyssal whip", ItemStyles.MELEE | ItemStyles.COMBAT);
		i.put(101, "Magic shortbow", ItemStyles.RANGED | ItemStyles.COMBAT);
		return i;
	}

	private static int id(ItemCategoryTest.Items i, String name)
	{
		for (Map.Entry<Integer, String> e : i.names.entrySet())
		{
			if (e.getValue().equals(name))
			{
				return e.getKey();
			}
		}
		throw new AssertionError(name);
	}

	private static boolean has(FindFilter f, String name)
	{
		ItemCategoryTest.Items i = items();
		return f.matches(id(i, name), i);
	}

	private static FindFilter of(FindQuick.Kind kind, String sub)
	{
		return new FindFilter(kind, sub, null);
	}

	@Test
	public void runesNarrowToOneRune()
	{
		FindFilter all = of(FindQuick.Kind.RUNES, FindQuick.ANY);
		assertTrue(has(all, "Air rune"));
		assertTrue(has(all, "Nature rune"));
		assertFalse("rune gear is not a rune", has(all, "Rune scimitar"));
		FindFilter nat = of(FindQuick.Kind.RUNES, "Nature");
		assertTrue(has(nat, "Nature rune"));
		assertFalse(has(nat, "Air rune"));
	}

	@Test
	public void foodIsCookedFoodNotRawOrPotions()
	{
		FindFilter food = of(FindQuick.Kind.FOOD, FindQuick.ANY);
		assertTrue(has(food, "Shark"));
		assertTrue(has(food, "Trout"));
		assertTrue(has(food, "Apple pie"));
		assertFalse(has(food, "Raw shark"));
		assertFalse(has(food, "Prayer potion(4)"));
		assertTrue(has(of(FindQuick.Kind.FOOD, "Fish"), "Shark"));
		assertFalse(has(of(FindQuick.Kind.FOOD, "Fish"), "Apple pie"));
		assertTrue(has(of(FindQuick.Kind.FOOD, "Pies & cakes"), "Apple pie"));
	}

	@Test
	public void potionsAreTheDosedOnesAndNarrowByUse()
	{
		FindFilter potions = of(FindQuick.Kind.POTIONS, FindQuick.ANY);
		assertTrue(has(potions, "Prayer potion(4)"));
		assertTrue(has(potions, "Super restore(3)"));
		assertFalse("an unfinished potion has no dose", has(potions, "Guam potion (unf)"));
		assertTrue(has(of(FindQuick.Kind.POTIONS, "Prayer & restore"), "Super restore(3)"));
		assertFalse(has(of(FindQuick.Kind.POTIONS, "Prayer & restore"), "Stamina potion(2)"));
		assertTrue(has(of(FindQuick.Kind.POTIONS, "Stamina"), "Stamina potion(2)"));
		assertTrue(has(of(FindQuick.Kind.POTIONS, "Combat boosts"), "Ranging potion(4)"));
	}

	@Test
	public void teleportsAreTabletsAndJewellery()
	{
		FindFilter tp = of(FindQuick.Kind.TELEPORTS, FindQuick.ANY);
		assertTrue(has(tp, "Varrock teleport"));
		assertTrue(has(tp, "Teleport to house"));
		assertTrue(has(tp, "Ring of dueling(8)"));
		assertTrue(has(tp, "Amulet of glory(4)"));
		assertFalse(has(tp, "Shark"));
		assertTrue(has(of(FindQuick.Kind.TELEPORTS, "Tablets"), "Varrock teleport"));
		assertFalse(has(of(FindQuick.Kind.TELEPORTS, "Tablets"), "Ring of dueling(8)"));
		assertTrue(has(of(FindQuick.Kind.TELEPORTS, "Jewellery"), "Ring of dueling(8)"));
	}

	@Test
	public void resourcesAndCombatReuseTheBankCategories()
	{
		assertTrue(has(of(FindQuick.Kind.RESOURCES, FindQuick.ANY), "Coal"));
		assertTrue(has(of(FindQuick.Kind.RESOURCES, "Ores & bars"), "Iron ore"));
		assertFalse(has(of(FindQuick.Kind.RESOURCES, "Ores & bars"), "Yew logs"));
		assertTrue(has(of(FindQuick.Kind.RESOURCES, "Logs"), "Yew logs"));
		assertTrue(has(of(FindQuick.Kind.COMBAT, "Melee"), "Abyssal whip"));
		assertFalse(has(of(FindQuick.Kind.COMBAT, "Melee"), "Magic shortbow"));
		assertTrue(has(of(FindQuick.Kind.COMBAT, FindQuick.ANY), "Magic shortbow"));
	}

	@Test
	public void everyKindListsAnyFirstThenItsSubTypes()
	{
		for (FindQuick.Kind k : FindQuick.Kind.values())
		{
			assertEquals(k.name(), FindQuick.ANY, FindQuick.subtypes(k).get(0));
		}
		assertTrue(FindQuick.subtypes(FindQuick.Kind.RUNES).contains("Cosmic"));
		assertEquals(Arrays.asList(FindQuick.ANY, "Melee", "Ranged", "Magic"), FindQuick.subtypes(FindQuick.Kind.COMBAT));
		assertEquals(Arrays.asList(FindQuick.ANY), FindQuick.subtypes(FindQuick.Kind.ANYTHING));
	}

	@Test
	public void anUnsetFilterIsInactiveAndAKindMakesItActive()
	{
		assertFalse(FindFilter.NONE.active());
		assertTrue(of(FindQuick.Kind.FOOD, FindQuick.ANY).active());
		assertTrue(new FindFilter(FindQuick.Kind.ANYTHING, FindQuick.ANY, "Alex").active());
	}

	@Test
	public void presetsSetTheDropdownsForOneClick()
	{
		FindFilter runes = FindQuick.RUNES_ANYWHERE;
		assertEquals(FindQuick.Kind.RUNES, runes.kind);
		assertNull("anywhere", runes.holder);
		assertEquals(FindQuick.Kind.FOOD, FindQuick.FOOD.kind);
		assertEquals(FindQuick.Kind.TELEPORTS, FindQuick.TELEPORTS.kind);
	}

	@Test
	public void holdersListGroupStorageThenEachMemberOnce()
	{
		GroupRoster roster = new GroupRoster();
		roster.putBank("Alex", new BankSnapshot(NOW, new TreeMap<>()));
		roster.putCarried("Sam", new GroupCodec.Carried(new TreeMap<>(), new TreeMap<>()));
		roster.putStorage(new StorageChange(NOW, "Sam", new TreeMap<>(), new TreeMap<>(), new TreeMap<>()));
		List<String> holders = FindQuick.holders(roster.searchSources());
		assertEquals(Arrays.asList(FindQuick.ANYWHERE, FindQuick.GROUP_STORAGE, "Alex", "Sam"), holders);
	}

	@Test
	public void aKindFindsItemsWithNoSearchTextAndAHolderNarrowsWhere()
	{
		ItemCategoryTest.Items i = items();
		GroupRoster roster = new GroupRoster();
		Map<Integer, Integer> alexBank = new TreeMap<>();
		alexBank.put(id(i, "Air rune"), 500);
		alexBank.put(id(i, "Shark"), 40);
		roster.putBank("Alex", new BankSnapshot(NOW - 60_000, alexBank));
		Map<Integer, Integer> storage = new TreeMap<>();
		storage.put(id(i, "Air rune"), 1000);
		roster.putStorage(new StorageChange(NOW - 1000, "Sam", new TreeMap<>(), storage, storage));

		List<PanelModel.FindRow> runes = PanelModel.find("", roster, i, NOW, "Me", FindQuick.RUNES_ANYWHERE);
		assertEquals(2, runes.size());
		assertTrue(runes.get(0).text.startsWith("Air rune - "));

		List<PanelModel.FindRow> inStorage = PanelModel.find("", roster, i, NOW, "Me",
			new FindFilter(FindQuick.Kind.RUNES, FindQuick.ANY, FindQuick.GROUP_STORAGE));
		assertEquals(1, inStorage.size());
		assertEquals("Air rune - 1,000 in group storage", inStorage.get(0).text);

		List<PanelModel.FindRow> alex = PanelModel.find("", roster, i, NOW, "Me",
			new FindFilter(FindQuick.Kind.ANYTHING, FindQuick.ANY, "Alex"));
		assertEquals("anything Alex holds", 2, alex.size());

		assertTrue("no filter and no text finds nothing, as before", PanelModel.find("", roster, i, NOW, "Me",
			FindFilter.NONE).isEmpty());
		List<PanelModel.FindRow> typed = PanelModel.find("shark", roster, i, NOW, "Me",
			new FindFilter(FindQuick.Kind.RUNES, FindQuick.ANY, null));
		assertTrue("a kind and a text must both match", typed.isEmpty());
	}
}
