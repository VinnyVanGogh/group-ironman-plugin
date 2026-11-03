package com.vinnyvangogh.buildguard;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * The rolling group storage change log: the newest {@link #MAX_ENTRIES}
 * visits, newest first.
 *
 * <p>Only visits by members running Build Guard with storage sharing on are
 * here. A member without it changes storage silently, and the next visit by
 * someone who has it sees the result as part of their own "before".
 */
final class StorageLog
{
	static final int MAX_ENTRIES = 200;

	private final LinkedList<StorageChange> entries = new LinkedList<>();

	/** Adds a visit. Empty visits and exact repeats (a duplicate delivery) are dropped. */
	synchronized boolean add(StorageChange change)
	{
		if (change.isEmpty())
		{
			return false;
		}
		int index = 0;
		for (StorageChange e : entries)
		{
			if (e.at() == change.at() && e.member().equalsIgnoreCase(change.member()))
			{
				return false;
			}
			if (e.at() < change.at())
			{
				break;
			}
			index++;
		}
		entries.add(index, change);
		while (entries.size() > MAX_ENTRIES)
		{
			entries.removeLast();
		}
		return true;
	}

	synchronized List<StorageChange> entries()
	{
		return new ArrayList<>(entries);
	}

	synchronized void clear()
	{
		entries.clear();
	}

	/** "Alex took 5 x Shark, deposited 100 x Coal (3m ago)", newest first. */
	List<String> lines(ItemLookup items, long now)
	{
		List<String> out = new ArrayList<>();
		for (StorageChange e : entries())
		{
			List<String> parts = new ArrayList<>();
			if (!e.took().isEmpty())
			{
				parts.add("took " + describe(e.took(), items));
			}
			if (!e.deposited().isEmpty())
			{
				parts.add("deposited " + describe(e.deposited(), items));
			}
			out.add(e.member() + " " + String.join(", ", parts) + " (" + BoostText.ago(now, e.at()) + ")");
		}
		return out;
	}

	static String describe(Map<Integer, Integer> counts, ItemLookup items)
	{
		List<String> parts = new ArrayList<>();
		for (Map.Entry<Integer, Integer> e : counts.entrySet())
		{
			parts.add(e.getValue() + " x " + items.name(e.getKey()));
		}
		return String.join(", ", parts);
	}
}
