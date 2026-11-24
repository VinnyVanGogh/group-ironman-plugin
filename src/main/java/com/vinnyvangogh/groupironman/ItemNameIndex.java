package com.vinnyvangogh.groupironman;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Item ids by name, for the items the price search cannot find: a collection log line names an untradeable such as a
 * Giant key, and the Group box wants its picture. Only names that were asked for are looked up, in a scan of the
 * item list a slice at a time on the client thread ({@link #step}), so one new name costs no single long frame.
 */
final class ItemNameIndex
{
	/** The client's items, as the scan reads them. */
	interface Items
	{
		int count();

		/** The item's name, or null for a noted, placeholder or unnamed id (the base item has the same name). */
		String baseName(int id);
	}

	/** Not looked up yet: the scan has it queued. */
	static final int PENDING = -1;
	/** Looked up and not an item. */
	static final int NOT_FOUND = -2;
	private static final int MAX_KEPT = 512;

	private final Map<String, Integer> found = new HashMap<>();
	/** Asked for since the running scan started; they wait for the next one. */
	private final Set<String> queued = new HashSet<>();
	/** What the running scan looks for. */
	private final Set<String> scanning = new HashSet<>();
	private int next;
	private boolean running;

	/** The id of the item called {@code name}, {@link #PENDING} while the scan looks, or {@link #NOT_FOUND}. */
	synchronized int id(String name)
	{
		String key = key(name);
		if (key.isEmpty())
		{
			return NOT_FOUND;
		}
		Integer id = found.get(key);
		if (id != null)
		{
			return id;
		}
		if (!scanning.contains(key))
		{
			queued.add(key);
		}
		return PENDING;
	}

	/** True once when a scan should be started: a name is waiting and none is running. */
	synchronized boolean claim()
	{
		if (running || queued.isEmpty())
		{
			return false;
		}
		running = true;
		return true;
	}

	/** Reads up to {@code budget} items; true when the scan is done and nothing waits (stop calling). */
	synchronized boolean step(Items items, int budget)
	{
		if (scanning.isEmpty())
		{
			scanning.addAll(queued);
			queued.clear();
			next = 0;
		}
		int count = items.count();
		if (count <= 0)
		{
			// The item list is not loaded yet (login screen): try again next frame rather than call everything missing.
			return false;
		}
		int end = Math.min(count, next + budget);
		for (; next < end && !scanning.isEmpty(); next++)
		{
			String name = items.baseName(next);
			if (name != null && scanning.remove(key(name)))
			{
				keep(key(name), next);
			}
		}
		if (next >= count || scanning.isEmpty())
		{
			for (String missing : scanning)
			{
				keep(missing, NOT_FOUND);
			}
			scanning.clear();
			if (queued.isEmpty())
			{
				running = false;
				return true;
			}
		}
		return false;
	}

	private void keep(String key, int id)
	{
		if (found.size() >= MAX_KEPT)
		{
			found.clear();
		}
		found.put(key, id);
	}

	private static String key(String name)
	{
		return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
	}
}
