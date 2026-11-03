package com.vinnyvangogh.buildguard;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntUnaryOperator;

/**
 * Item id to quantity, the one shape every group feature moves around.
 *
 * <p>Slots, tabs and order are deliberately thrown away: a bank snapshot, a
 * storage diff and a search all only care how many of each item there are.
 * Maps are sorted by id so two equal holdings encode to the same string.
 */
final class ItemCounts
{
	/** Ids above this are not items the game has; a peer sending one is broken. */
	static final int MAX_ITEM_ID = 1 << 20;

	private ItemCounts()
	{
	}

	/**
	 * Adds one slot to a tally, after mapping the id through {@code canonical}
	 * (noted to unnoted, placeholder to item). Empty slots, placeholders with
	 * quantity 0 and anything that canonicalises to an invalid id are skipped.
	 */
	static void add(Map<Integer, Integer> into, int id, int quantity, IntUnaryOperator canonical)
	{
		if (id <= 0 || quantity <= 0)
		{
			return;
		}
		int key = canonical.applyAsInt(id);
		if (key <= 0 || key >= MAX_ITEM_ID)
		{
			return;
		}
		into.merge(key, quantity, ItemCounts::saturatingAdd);
	}

	static int saturatingAdd(int a, int b)
	{
		long sum = (long) a + b;
		return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
	}

	static Map<Integer, Integer> sorted(Map<Integer, Integer> counts)
	{
		return Collections.unmodifiableMap(new TreeMap<>(counts));
	}

	/** {@code [id, qty, id, qty, ...]}, ascending by id. */
	static JsonArray toJson(Map<Integer, Integer> counts)
	{
		JsonArray out = new JsonArray();
		for (Map.Entry<Integer, Integer> e : new TreeMap<>(counts).entrySet())
		{
			out.add(e.getKey());
			out.add(e.getValue());
		}
		return out;
	}

	/**
	 * Reads a flat pair array from a peer. Bad pairs are dropped rather than
	 * failing the whole list, duplicates are summed, and at most {@code max}
	 * distinct items are kept.
	 */
	static Map<Integer, Integer> fromJson(JsonElement e, int max)
	{
		Map<Integer, Integer> out = new TreeMap<>();
		if (e == null || !e.isJsonArray())
		{
			return out;
		}
		JsonArray array = e.getAsJsonArray();
		for (int i = 0; i + 1 < array.size(); i += 2)
		{
			Long id = longValue(array.get(i));
			Long qty = longValue(array.get(i + 1));
			if (id == null || qty == null || id <= 0 || id >= MAX_ITEM_ID || qty <= 0)
			{
				continue;
			}
			int key = id.intValue();
			if (!out.containsKey(key) && out.size() >= max)
			{
				continue;
			}
			int q = qty > Integer.MAX_VALUE ? Integer.MAX_VALUE : qty.intValue();
			out.merge(key, q, ItemCounts::saturatingAdd);
		}
		return out;
	}

	static Long longValue(JsonElement e)
	{
		if (e == null || !e.isJsonPrimitive() || !((JsonPrimitive) e).isNumber())
		{
			return null;
		}
		try
		{
			double d = e.getAsDouble();
			if (d != Math.floor(d) || Double.isInfinite(d))
			{
				return null;
			}
			return e.getAsLong();
		}
		catch (NumberFormatException ex)
		{
			return null;
		}
	}
}
