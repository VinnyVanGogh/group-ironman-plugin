package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * The rolling group storage change log: the newest {@link #MAX_ENTRIES}
 * visits, newest first.
 *
 * <p>Members' visits are here only from members running GIM Utilities with
 * storage sharing on. A member without it changes storage silently, and the
 * next visit by someone who has it sees the result as part of their own
 * "before". This player's own visits are always here; one made with sharing
 * off is marked {@link StorageChange#shared() not shared} and was never sent.
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

	/** True when this member's visit at {@code at} is here and was kept to this client (not shared). */
	synchronized boolean notShared(long at, String member)
	{
		for (StorageChange e : entries)
		{
			if (e.at() == at && e.member().equalsIgnoreCase(member))
			{
				return !e.shared();
			}
		}
		return false;
	}

	synchronized List<StorageChange> entries()
	{
		return new ArrayList<>(entries);
	}

	synchronized void clear()
	{
		entries.clear();
	}

	/** "5 x Shark, 40K x Coins", for {@code ::find}'s storage history; amounts in {@code items}' style. */
	static String describe(Map<Integer, Integer> counts, ItemLookup items)
	{
		List<String> parts = new ArrayList<>();
		for (Map.Entry<Integer, Integer> e : counts.entrySet())
		{
			parts.add(items.amount(e.getValue()) + " x " + items.name(e.getKey()));
		}
		return String.join(", ", parts);
	}
}
