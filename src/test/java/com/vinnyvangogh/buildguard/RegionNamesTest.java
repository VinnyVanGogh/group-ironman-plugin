package com.vinnyvangogh.buildguard;

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
