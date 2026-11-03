package com.vinnyvangogh.buildguard;

import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What changed in group storage between the local player opening it and
 * closing it.
 *
 * <p>Works on counts, not slots: moving a stack to another slot is not a
 * change, taking 3 of a 10 stack is "took 3". Both sides should already be
 * canonicalised (see {@link ItemCounts#add}) so depositing noted items, which
 * the game stores unnoted, reads as a deposit of the item and not as one item
 * vanishing and another appearing.
 */
final class StorageDiff
{
	private StorageDiff()
	{
	}

	static StorageChange diff(long at, Map<Integer, Integer> before, Map<Integer, Integer> after)
	{
		Map<Integer, Integer> took = new TreeMap<>();
		Map<Integer, Integer> deposited = new TreeMap<>();
		TreeSet<Integer> ids = new TreeSet<>(before.keySet());
		ids.addAll(after.keySet());
		for (int id : ids)
		{
			long delta = (long) after.getOrDefault(id, 0) - before.getOrDefault(id, 0);
			if (delta > 0)
			{
				deposited.put(id, (int) Math.min(delta, Integer.MAX_VALUE));
			}
			else if (delta < 0)
			{
				took.put(id, (int) Math.min(-delta, Integer.MAX_VALUE));
			}
		}
		return new StorageChange(at, "", took, deposited, after);
	}
}
