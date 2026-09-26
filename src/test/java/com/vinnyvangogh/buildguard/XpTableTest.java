package com.vinnyvangogh.buildguard;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * The same anchors {@code internal/osrs/xp_test.go} asserts in the questpath
 * Go app.
 *
 * Levels 92 and 99 matter most: 92 is the halfway point to 99 in experience,
 * and both sit far enough up the curve to catch the per-term-floor misreading
 * of the formula, which agrees with the correct one only for the first few
 * levels.
 */
public class XpTableTest
{
	@Test
	public void xpForMatchesThePublishedTable()
	{
		assertEquals(0, XpTable.xpFor(1));
		assertEquals(83, XpTable.xpFor(2));
		assertEquals(174, XpTable.xpFor(3));
		assertEquals(276, XpTable.xpFor(4));
		assertEquals(388, XpTable.xpFor(5));
		assertEquals(1_154, XpTable.xpFor(10));
		assertEquals(4_470, XpTable.xpFor(20));
		assertEquals(13_363, XpTable.xpFor(30));
		assertEquals(37_224, XpTable.xpFor(40));
		assertEquals(101_333, XpTable.xpFor(50));
		assertEquals(273_742, XpTable.xpFor(60));
		assertEquals(737_627, XpTable.xpFor(70));
		assertEquals(1_986_068, XpTable.xpFor(80));
		assertEquals(5_346_332, XpTable.xpFor(90));
		assertEquals(6_517_253, XpTable.xpFor(92));
		assertEquals(13_034_431, XpTable.xpFor(99));
		assertEquals(188_884_740, XpTable.xpFor(126));
	}

	@Test
	public void xpForClampsRatherThanThrowing()
	{
		assertEquals("level 0 clamps to zero xp", 0, XpTable.xpFor(0));
		assertEquals("negative level clamps to zero xp", 0, XpTable.xpFor(-5));
		assertEquals("above virtual max clamps",
			XpTable.xpFor(XpTable.MAX_VIRTUAL_LEVEL), XpTable.xpFor(500));
	}

	@Test
	public void levelForMatchesTheGoVectors()
	{
		assertEquals("zero xp is level 1", 1, XpTable.levelFor(0));
		assertEquals("negative xp is level 1", 1, XpTable.levelFor(-1));
		assertEquals("one below level 2", 1, XpTable.levelFor(82));
		assertEquals("exactly level 2", 2, XpTable.levelFor(83));
		assertEquals("one above level 2", 2, XpTable.levelFor(84));
		assertEquals("one below level 99", 98, XpTable.levelFor(13_034_430));
		assertEquals("exactly level 99", 99, XpTable.levelFor(13_034_431));
		assertEquals("live account thieving", 10, XpTable.levelFor(1_298));
		assertEquals("live account strength", 82, XpTable.levelFor(2_464_249));
	}

	/**
	 * Every level's exact threshold must round-trip, and one XP short of it
	 * must be the level below. This is the off-by-one the whole class exists
	 * to prevent.
	 */
	@Test
	public void levelForRoundTripsXpFor()
	{
		for (int level = 2; level <= XpTable.MAX_VIRTUAL_LEVEL; level++)
		{
			long xp = XpTable.xpFor(level);
			assertEquals("level " + level + " round trip at exact threshold",
				level, XpTable.levelFor(xp));
			assertEquals("level " + level + " round trip one xp short",
				level - 1, XpTable.levelFor(xp - 1));
		}
	}

	@Test
	public void xpBetweenMatchesTheGoVectors()
	{
		assertEquals("1 to 99", 13_034_431, XpTable.xpBetween(1, 99));
		assertEquals("92 to 99 is half the grind", 6_517_178, XpTable.xpBetween(92, 99));
		assertEquals("10 to 50", 100_179, XpTable.xpBetween(10, 50));
		assertEquals("same level is zero", 0, XpTable.xpBetween(50, 50));
		assertEquals("backwards is zero, never negative", 0, XpTable.xpBetween(99, 50));
	}

	@Test
	public void formatStaysExactWhereThePreciseNumberIsStillUseful()
	{
		assertEquals("83", XpTable.format(83));
		assertEquals("9,999", XpTable.format(9_999));
		assertEquals("10.0k", XpTable.format(10_000));
		assertEquals("101.3k", XpTable.format(101_333));
		assertEquals("13.03m", XpTable.format(13_034_431));
	}
}
