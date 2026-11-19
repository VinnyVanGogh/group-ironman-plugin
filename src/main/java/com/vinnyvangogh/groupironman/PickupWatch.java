package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Posted drops waiting to be picked up ("Add picked-up screenshot"): a drop posted to Discord is watched for
 * {@code window} ms; when the member's inventory then holds more of one of its items than when it was posted, and
 * they stand near where it dropped, the post is owed a second picture. Pure and clock-driven; every method holds the
 * lock. A watch is used up by its first pickup.
 */
final class PickupWatch
{
	/** "The same tile area": this many tiles, either way, on the same plane. */
	static final int NEAR = 20;

	/** One posted drop. Immutable apart from being removed. */
	static final class Watch
	{
		final String url;
		final String messageId;
		/** The post's body, as sent: the edit is built from it. */
		final String json;
		/** The post's own screenshot, resent with the edit; null when it had none. */
		final byte[] shot;
		final Map<Integer, Integer> items;
		/** The inventory's counts when the drop posted. */
		final Map<Integer, Integer> baseline;
		final int x;
		final int y;
		final int plane;
		final long expires;

		Watch(String url, String messageId, String json, byte[] shot, Map<Integer, Integer> items,
			Map<Integer, Integer> baseline, int x, int y, int plane, long expires)
		{
			this.url = url;
			this.messageId = messageId;
			this.json = json;
			this.shot = shot;
			this.items = new HashMap<>(items);
			this.baseline = new HashMap<>(baseline);
			this.x = x;
			this.y = y;
			this.plane = plane;
			this.expires = expires;
		}
	}

	private final List<Watch> watching = new ArrayList<>();

	synchronized void add(Watch watch)
	{
		watching.add(watch);
	}

	/**
	 * The inventory changed ({@code inventory}: item id to amount, canonical ids) with the member at x, y, plane:
	 * the watches it picked something up for, now used up. Expired watches are dropped.
	 */
	synchronized List<Watch> inventory(Map<Integer, Integer> inventory, int x, int y, int plane, long now)
	{
		List<Watch> done = new ArrayList<>();
		for (Iterator<Watch> it = watching.iterator(); it.hasNext(); )
		{
			Watch w = it.next();
			if (now >= w.expires)
			{
				it.remove();
			}
			else if (picked(w, inventory) && near(w, x, y, plane))
			{
				it.remove();
				done.add(w);
			}
		}
		return done;
	}

	private static boolean picked(Watch w, Map<Integer, Integer> inventory)
	{
		for (Integer id : w.items.keySet())
		{
			if (inventory.getOrDefault(id, 0) > w.baseline.getOrDefault(id, 0))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean near(Watch w, int x, int y, int plane)
	{
		return plane == w.plane && Math.abs(x - w.x) <= NEAR && Math.abs(y - w.y) <= NEAR;
	}

	synchronized int size()
	{
		return watching.size();
	}

	synchronized void clear()
	{
		watching.clear();
	}
}
