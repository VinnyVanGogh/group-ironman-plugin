package com.vinnyvangogh.groupironman;

import static org.junit.Assert.assertEquals;

import net.runelite.client.util.QuantityFormatter;
import org.junit.Test;

public class AmountsTest
{
	private static final long[] SAMPLES = {3, 13_280, 99_999, 100_000, 450_001, 9_999_999, 10_000_000, 2_147_483_647};

	@Test
	public void gameMatchesQuantityFormatter()
	{
		for (long n : SAMPLES)
		{
			assertEquals("amount " + n, QuantityFormatter.quantityToStackSize(n), Amounts.GAME.of(n));
		}
	}

	@Test
	public void gameReadsLikeTheInventory()
	{
		// Pinned so a RuneLite change to the formatter shows up here, not in a player's panel.
		String[] expected = {"3", "13.2K", "99.9K", "100K", "450K", "10M", "10M", "2.14B"};
		for (int i = 0; i < SAMPLES.length; i++)
		{
			assertEquals("amount " + SAMPLES[i], expected[i], Amounts.GAME.of(SAMPLES[i]));
		}
	}

	@Test
	public void exactKeepsEveryDigit()
	{
		assertEquals("450,001", Amounts.EXACT.of(450_001));
		assertEquals("3", Amounts.EXACT.of(3));
		assertEquals("2,147,483,647", Amounts.EXACT.of(2_147_483_647));
	}

	@Test
	public void bothAddsTheExactNumberOnlyWhenShortened()
	{
		assertEquals("450K (450,001)", Amounts.BOTH.of(450_001));
		assertEquals("10M (10,000,000)", Amounts.BOTH.of(10_000_000));
		assertEquals("9,999", Amounts.BOTH.of(9_999));
		assertEquals("3", Amounts.BOTH.of(3));
	}

	@Test
	public void wordsKeepNamesAndPricesAndChangeOnlyTheAmount()
	{
		ItemLookup base = new FakeItems();
		ItemLookup discord = Amounts.BOTH.words(base);
		assertEquals("Coins", discord.name(995));
		assertEquals(1L, discord.price(995));
		assertEquals("450K (450,001)", discord.amount(450_001));
		assertEquals("450K", base.amount(450_001));
		assertEquals("450,001", Amounts.EXACT.words(base).amount(450_001));
	}
}
