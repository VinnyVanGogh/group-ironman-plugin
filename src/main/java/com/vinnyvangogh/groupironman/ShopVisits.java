package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * When to say "this shop sells something your group still needs": once as
 * this player comes up on the shop, and not again until they have walked away.
 * The "Shopping: shop alert" setting is off by default; this only decides
 * when, {@link ShoppingBoard#byShop} decides which shops.
 *
 * <p>Like Quest Path's shop approach alert, from the shop's own tile in the
 * quest data. Client thread only.
 */
final class ShopVisits
{
	/** Tiles from the shop's tile that count as being at it. */
	static final int NEAR = 8;
	/** Tiles away that end a visit, so the next approach alerts again. */
	static final int LEFT = 20;

	private final Set<String> inside = new HashSet<>();

	/** The shops among {@code candidates} this player has just come up on. */
	List<String> arrived(int x, int y, int plane, Iterable<String> candidates, QuestData data)
	{
		List<String> out = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (String name : candidates)
		{
			QuestData.Shop shop = data.shop(name);
			if (shop == null)
			{
				continue;
			}
			seen.add(name);
			int d = shop.plane == plane ? Math.max(Math.abs(shop.x - x), Math.abs(shop.y - y)) : Integer.MAX_VALUE;
			if (d <= NEAR && inside.add(name))
			{
				out.add(name);
			}
			else if (d > LEFT)
			{
				inside.remove(name);
			}
		}
		// A shop with nothing left to buy is forgotten too, so a new need there alerts on the next approach.
		inside.retainAll(seen);
		return out;
	}

	void reset()
	{
		inside.clear();
	}
}
