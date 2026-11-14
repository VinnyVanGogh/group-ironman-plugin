package com.vinnyvangogh.groupironman;

import net.runelite.client.util.QuantityFormatter;

/**
 * How an item quantity or a gp value reads (Board 2026-11-10: "450001 in
 * group storage" should read the way the game's inventory does).
 *
 * <p>The short form is RuneLite's {@code QuantityFormatter.quantityToStackSize}
 * (runelite-client {@code net/runelite/client/util/QuantityFormatter.java}):
 * under 10,000 it is the comma-grouped number; from 10,000 it divides by the
 * largest of 1K/1M/1B the amount reaches, formats with English grouping and at
 * most three decimals, keeps the first four characters and drops a trailing
 * point, then adds the suffix. So 13,280 is "13.2K", 450,001 "450K" and
 * 9,999,999 "10M" (the three-decimal rounding carries it over).
 */
enum Amounts
{
	/** The game's stack text: "13.2K", "450K", "10M". The panel, chat and overlays. */
	GAME,
	/** Every digit, comma grouped: "450,001". Panel tooltips. */
	EXACT,
	/** The game's text, then every digit when the short form dropped any: "450K (450,001)". Discord. */
	BOTH;

	/** Amounts below this read in full in the game's own format, so there is nothing to add. */
	private static final long SHORTENED_FROM = 10_000;

	String of(long amount)
	{
		switch (this)
		{
			case EXACT:
				return QuantityFormatter.formatNumber(amount);
			case BOTH:
				String game = QuantityFormatter.quantityToStackSize(amount);
				return Math.abs(amount) < SHORTENED_FROM ? game
					: game + " (" + QuantityFormatter.formatNumber(amount) + ")";
			default:
				return QuantityFormatter.quantityToStackSize(amount);
		}
	}

	/** {@code base}'s names and prices, with amounts in this style. */
	ItemLookup words(ItemLookup base)
	{
		Amounts style = this;
		return new ItemLookup()
		{
			@Override
			public String name(int id)
			{
				return base.name(id);
			}

			@Override
			public long price(int id)
			{
				return base.price(id);
			}

			@Override
			public int slot(int id)
			{
				return base.slot(id);
			}

			@Override
			public String amount(long amount)
			{
				return style.of(amount);
			}
		};
	}
}
