package com.vinnyvangogh.groupironman;

import java.util.HashMap;
import java.util.Map;

/** A tiny item table for tests, standing in for the plugin's ItemManager-backed lookup. */
final class FakeItems implements ItemLookup
{
	static final int SHARK = 385;
	static final int COAL = 453;
	static final int RUNE_SCIM = 1333;
	static final int PRAYER_POT_4 = 2434;
	static final int PRAYER_POT_3 = 139;
	static final int WHIP = 4151;

	private final Map<Integer, String> names = new HashMap<>();
	private final Map<Integer, Long> prices = new HashMap<>();

	FakeItems()
	{
		add(SHARK, "Shark", 800);
		add(COAL, "Coal", 150);
		add(RUNE_SCIM, "Rune scimitar", 15_000);
		add(PRAYER_POT_4, "Prayer potion(4)", 9_000);
		add(PRAYER_POT_3, "Prayer potion(3)", 6_500);
		add(WHIP, "Abyssal whip", 1_500_000);
	}

	private void add(int id, String name, long price)
	{
		names.put(id, name);
		prices.put(id, price);
	}

	@Override
	public String name(int id)
	{
		return names.getOrDefault(id, "item " + id);
	}

	@Override
	public long price(int id)
	{
		return prices.getOrDefault(id, 0L);
	}
}
