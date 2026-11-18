package com.vinnyvangogh.groupironman;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Which items the Banks and Storage category pills keep: item stats for gear, names for runes and resources. */
public class ItemCategoryTest
{
	static final int WHIP = 1;
	static final int SCIM = 2;
	static final int SHORTBOW = 3;
	static final int STAFF = 4;
	static final int RUNE_PLATE = 5;
	static final int DHIDE_BODY = 6;
	static final int MYSTIC_TOP = 7;
	static final int ARROWS = 8;
	static final int AMULET = 9;
	static final int COSMETIC = 10;
	static final int AIR_RUNE = 11;
	static final int RUNE_SCIM_NAME = 12;
	static final int RUNE_ESSENCE = 13;
	static final int COAL = 14;
	static final int YEW_LOGS = 15;
	static final int SHARK = 16;
	static final int RAW_SHARK = 17;
	static final int UNKNOWN = 18;

	/** Name, and the styles RuneLite's item stats would give it. */
	static final class Items implements ItemLookup
	{
		final Map<Integer, String> names = new HashMap<>();
		final Map<Integer, Integer> styles = new HashMap<>();

		Items put(int id, String name, int style)
		{
			names.put(id, name);
			styles.put(id, style);
			return this;
		}

		@Override
		public String name(int id)
		{
			return names.getOrDefault(id, "item " + id);
		}

		@Override
		public long price(int id)
		{
			return 1;
		}

		@Override
		public int combatStyles(int id)
		{
			return styles.getOrDefault(id, 0);
		}
	}

	private static Items items()
	{
		return new Items()
			.put(WHIP, "Abyssal whip", ItemStyles.MELEE | ItemStyles.COMBAT)
			.put(SCIM, "Rune scimitar", ItemStyles.MELEE | ItemStyles.COMBAT)
			.put(SHORTBOW, "Magic shortbow", ItemStyles.RANGED | ItemStyles.COMBAT)
			.put(STAFF, "Staff of fire", ItemStyles.MAGIC | ItemStyles.COMBAT)
			.put(RUNE_PLATE, "Rune platebody", ItemStyles.MELEE | ItemStyles.COMBAT)
			.put(DHIDE_BODY, "Green d'hide body", ItemStyles.RANGED | ItemStyles.COMBAT)
			.put(MYSTIC_TOP, "Mystic robe top", ItemStyles.MAGIC | ItemStyles.COMBAT)
			.put(ARROWS, "Rune arrow", ItemStyles.RANGED | ItemStyles.COMBAT)
			.put(AMULET, "Amulet of glory", ItemStyles.COMBAT)
			.put(COSMETIC, "Cabbage cape", 0)
			.put(AIR_RUNE, "Air rune", 0)
			.put(RUNE_ESSENCE, "Rune essence", 0)
			.put(COAL, "Coal", 0)
			.put(YEW_LOGS, "Yew logs", 0)
			.put(SHARK, "Shark", 0)
			.put(RAW_SHARK, "Raw shark", 0);
	}

	@Test
	public void allKeepsEverything()
	{
		assertTrue(ItemCategory.ALL.matches(SHARK, items()));
		assertTrue(ItemCategory.ALL.matches(UNKNOWN, items()));
	}

	@Test
	public void styleBucketsFollowTheItemStats()
	{
		Items i = items();
		assertTrue(ItemCategory.MELEE.matches(WHIP, i));
		assertTrue(ItemCategory.MELEE.matches(RUNE_PLATE, i));
		assertFalse(ItemCategory.MELEE.matches(SHORTBOW, i));
		assertTrue(ItemCategory.RANGED.matches(SHORTBOW, i));
		assertTrue(ItemCategory.RANGED.matches(ARROWS, i));
		assertTrue(ItemCategory.RANGED.matches(DHIDE_BODY, i));
		assertTrue(ItemCategory.MAGIC.matches(STAFF, i));
		assertTrue(ItemCategory.MAGIC.matches(MYSTIC_TOP, i));
		assertFalse(ItemCategory.MAGIC.matches(WHIP, i));
	}

	@Test
	public void combatKeepsAllGearIncludingTheStyleless()
	{
		Items i = items();
		assertTrue(ItemCategory.COMBAT.matches(WHIP, i));
		assertTrue(ItemCategory.COMBAT.matches(STAFF, i));
		assertTrue("an amulet has stats but no style", ItemCategory.COMBAT.matches(AMULET, i));
		assertFalse("a cape with no stats is cosmetic", ItemCategory.COMBAT.matches(COSMETIC, i));
		assertFalse(ItemCategory.COMBAT.matches(SHARK, i));
		assertFalse(ItemCategory.MELEE.matches(AMULET, i));
	}

	@Test
	public void runesAreTheRunesAndNotRuneGear()
	{
		Items i = items();
		assertTrue(ItemCategory.RUNES.matches(AIR_RUNE, i));
		assertFalse("rune scimitar is a weapon", ItemCategory.RUNES.matches(SCIM, i));
		assertFalse("rune essence is a resource", ItemCategory.RUNES.matches(RUNE_ESSENCE, i));
		assertFalse(ItemCategory.RUNES.matches(COAL, i));
		for (String name : new String[]{"Water rune", "Fire rune", "Earth rune", "Mind rune", "Body rune", "Cosmic rune",
			"Chaos rune", "Nature rune", "Law rune", "Death rune", "Blood rune", "Soul rune", "Astral rune",
			"Wrath rune", "Dust rune", "Mist rune", "Mud rune", "Smoke rune", "Steam rune", "Lava rune",
			"Sunfire rune"})
		{
			assertTrue(name, ItemCategory.isRune(name));
		}
		assertFalse(ItemCategory.isRune("Rune pouch"));
		assertFalse(ItemCategory.isRune("Air runes"));
	}

	@Test
	public void resourcesAreSkillingMaterialsNotGearRunesOrFood()
	{
		Items i = items();
		assertTrue(ItemCategory.RESOURCES.matches(COAL, i));
		assertTrue(ItemCategory.RESOURCES.matches(YEW_LOGS, i));
		assertTrue(ItemCategory.RESOURCES.matches(RUNE_ESSENCE, i));
		assertTrue(ItemCategory.RESOURCES.matches(RAW_SHARK, i));
		assertFalse(ItemCategory.RESOURCES.matches(SHARK, i));
		assertFalse(ItemCategory.RESOURCES.matches(AIR_RUNE, i));
		assertFalse("gear is not a resource", ItemCategory.RESOURCES.matches(WHIP, i));
	}

	@Test
	public void anItemWithNoStatsYetMatchesOnlyAll()
	{
		Items i = items();
		for (ItemCategory c : new ItemCategory[]{ItemCategory.MELEE, ItemCategory.RANGED, ItemCategory.MAGIC,
			ItemCategory.COMBAT, ItemCategory.RUNES})
		{
			assertFalse(c.name(), c.matches(UNKNOWN, i));
		}
	}

	@Test
	public void gearIsSortedByItsStats()
	{
		// slot, aMelee, aRange, aMagic, str, rstr, mdmg, dMelee, dRange, dMagic, prayer
		assertEquals("whip", ItemStyles.MELEE | ItemStyles.COMBAT, ItemStyles.classify(3, 82, 0, 0, 82, 0, 0, 0, 0, 0, 0));
		assertEquals("staff", ItemStyles.MAGIC | ItemStyles.COMBAT, ItemStyles.classify(3, 0, 0, 10, 0, 0, 0, 0, 0, 0, 0));
		assertEquals("bow", ItemStyles.RANGED | ItemStyles.COMBAT, ItemStyles.classify(3, 0, 69, 0, 0, 0, 0, 0, 0, 0, 0));
		assertEquals("arrows by their strength", ItemStyles.RANGED | ItemStyles.COMBAT,
			ItemStyles.classify(13, 0, 0, 0, 0, 49, 0, 0, 0, 0, 0));
		assertEquals("rune platebody: melee defence leads", ItemStyles.MELEE | ItemStyles.COMBAT,
			ItemStyles.classify(4, 0, 0, 0, 0, 0, 0, 84, 80, -6, 0));
		assertEquals("d'hide body: range defence leads", ItemStyles.RANGED | ItemStyles.COMBAT,
			ItemStyles.classify(4, 0, -15, -10, 0, 0, 0, 30, 47, 30, 0));
		assertEquals("mystic top: magic defence leads", ItemStyles.MAGIC | ItemStyles.COMBAT,
			ItemStyles.classify(4, 0, -15, 20, 0, 0, 0, 20, 0, 20, 0));
		assertEquals("amulet: stats, no style", ItemStyles.COMBAT, ItemStyles.classify(2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3));
		assertEquals("a cape with no stats", 0, ItemStyles.classify(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
		assertEquals("not equipment", 0, ItemStyles.classify(-1, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5));
	}
}
