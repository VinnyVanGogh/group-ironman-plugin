package com.vinnyvangogh.groupironman;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The item whose icon the game draws for a stack (Board 2026-11-10: a coin
 * thumbnail was always one coin). Discord's thumbnails are RuneLite's static
 * icon per item id, so the pile is picked here by id.
 *
 * <p>The game keeps this as an item's {@code countObj}/{@code countCo} arrays
 * (runelite cache {@code ItemDefinition}); RuneLite's API {@code ItemComposition}
 * does not expose them, and {@code ItemManager.getImage(id, quantity, stackable)}
 * only passes the quantity to the client's sprite drawing. So the table below
 * mirrors the cache, and {@link #variant} is the cache's own selection loop
 * (runelite cache {@code ItemSpriteFactory.createSpritePixels}): above a stack
 * of one, the last threshold the quantity reaches wins. Ids are
 * {@code net.runelite.api.gameval.ItemID}: COINS 995, COINS_2..COINS_10000
 * 996..1004. An item not listed keeps its own id, as before.
 */
final class StackIcons
{
	private static final Map<Integer, long[][]> VARIANTS;

	static
	{
		Map<Integer, long[][]> v = new HashMap<>();
		// {countCo thresholds}, {countObj ids}
		v.put(995, new long[][]{{2, 3, 4, 5, 25, 100, 250, 1_000, 10_000}, {996, 997, 998, 999, 1000, 1001, 1002, 1003,
			1004}});
		VARIANTS = Collections.unmodifiableMap(v);
	}

	private StackIcons()
	{
	}

	/** The id whose icon shows a stack of {@code quantity} of {@code itemId}. */
	static int variant(int itemId, long quantity)
	{
		long[][] table = VARIANTS.get(itemId);
		if (table == null || quantity <= 1)
		{
			return itemId;
		}
		int out = itemId;
		for (int i = 0; i < table[0].length; i++)
		{
			if (quantity >= table[0][i])
			{
				out = (int) table[1][i];
			}
		}
		return out;
	}
}
