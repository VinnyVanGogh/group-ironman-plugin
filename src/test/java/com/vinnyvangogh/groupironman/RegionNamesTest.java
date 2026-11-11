package com.vinnyvangogh.groupironman;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Region id to place name, from the bundled table. */
public class RegionNamesTest
{
	@Test
	public void theBundledTableLoadsAndKnowsLumbridge()
	{
		RegionNames names = RegionNames.load();
		assertTrue("the table should have hundreds of regions, has " + names.size(), names.size() > 500);
		assertEquals("Lumbridge", names.name(12850));
		assertEquals("Varrock", names.name(12853));
		assertEquals("Grand Exchange", names.name(12598));
		assertEquals("Vorkath", names.name(9023));
	}

	/** Board 2026-11-10: a death at the Chaos Temple read "region 11835". */
	@Test
	public void wildernessPlacesHaveNames()
	{
		RegionNames names = RegionNames.load();
		assertEquals("Chaos Temple", names.describe(11835));
		assertEquals("Ferox Enclave", names.name(12600));
		assertEquals("Edgeville Wilderness", names.name(12343));
		assertEquals("Lava Maze", names.name(12348));
		assertEquals("Graveyard of Shadows", names.name(12601));
		assertEquals("Bandit Camp (Wilderness)", names.name(12089));
		assertEquals("Mage Arena", names.name(12349));
		// The desert's Bandit Camp keeps its name; the city section comes first.
		assertEquals("Bandit Camp", names.name(12590));
		// Every other Wilderness region says so rather than a number.
		for (int x = 46; x <= 52; x++)
		{
			for (int y = 55; y <= 61; y++)
			{
				assertTrue(x + "," + y, names.name(x << 8 | y) != null);
			}
		}
		assertEquals("Wilderness", names.name(12093));
		assertEquals("Edgeville", names.name(12342));
	}

	@Test
	public void anUnknownRegionHasNoName()
	{
		assertNull(RegionNames.load().name(1));
		assertNull(RegionNames.load().name(0));
	}

	@Test
	public void theFirstNameForARegionWins()
	{
		RegionNames names = RegionNames.parse("# comment\nCity=1,2\n\nDungeon=2,3\nbroken line\nBad=x,4\n");
		assertEquals("City", names.name(1));
		assertEquals("City", names.name(2));
		assertEquals("Dungeon", names.name(3));
		assertEquals("Bad", names.name(4));
		assertEquals(4, names.size());
	}
}
