package com.vinnyvangogh.groupironman;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StackIconsTest
{
	private static final int COINS = 995;

	@Test
	public void coinPilesGrowWithTheStack()
	{
		// gameval ItemID: COINS 995, COINS_2..COINS_10000 996..1004.
		assertEquals(995, StackIcons.variant(COINS, 1));
		assertEquals(999, StackIcons.variant(COINS, 5));
		assertEquals(1000, StackIcons.variant(COINS, 25));
		assertEquals(1001, StackIcons.variant(COINS, 100));
		assertEquals(1002, StackIcons.variant(COINS, 250));
		assertEquals(1003, StackIcons.variant(COINS, 1000));
		assertEquals(1004, StackIcons.variant(COINS, 10_000));
	}

	@Test
	public void betweenThresholdsTheLowerPileShows()
	{
		assertEquals(996, StackIcons.variant(COINS, 2));
		assertEquals(999, StackIcons.variant(COINS, 24));
		assertEquals(1003, StackIcons.variant(COINS, 9_999));
		assertEquals(1004, StackIcons.variant(COINS, 450_001));
		assertEquals(1004, StackIcons.variant(COINS, Long.MAX_VALUE));
	}

	@Test
	public void noStackOrNoVariantsKeepsTheItem()
	{
		assertEquals(995, StackIcons.variant(COINS, 0));
		assertEquals(995, StackIcons.variant(COINS, -5));
		assertEquals(FakeItems.SHARK, StackIcons.variant(FakeItems.SHARK, 10_000));
	}
}
