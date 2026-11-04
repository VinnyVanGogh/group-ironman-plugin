package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Group storage minimums the player typed in, and which of them a storage
 * close has just left short.
 *
 * <p>Text is one rule per line or comma, "Shark: 100". Items are matched by
 * name, case-insensitively, summing every id with that name, so the rule does
 * not care which dose or charge variant is stored under which id. An item
 * missing from storage counts as 0.
 *
 * <p>Each rule alerts once when it drops below its minimum and stays quiet
 * until storage is back at or above it, so every later visit does not repeat
 * the same notification.
 */
final class LowStock
{
	static final class Alert
	{
		final String item;
		final int have;
		final int minimum;

		Alert(String item, int have, int minimum)
		{
			this.item = item;
			this.have = have;
			this.minimum = minimum;
		}

		String message()
		{
			return "Group storage is low on " + item + ": " + have + " left, minimum " + minimum;
		}
	}

	static final int MAX_RULES = 50;

	private final Map<String, Integer> minimums;
	private final Map<String, String> displayNames;
	private final Set<String> alerted = new HashSet<>();

	private LowStock(Map<String, Integer> minimums, Map<String, String> displayNames)
	{
		this.minimums = minimums;
		this.displayNames = displayNames;
	}

	/** Lines that do not read as "name: number" with a positive number are ignored. */
	static LowStock parse(String text)
	{
		Map<String, Integer> minimums = new LinkedHashMap<>();
		Map<String, String> names = new LinkedHashMap<>();
		if (text != null)
		{
			for (String raw : text.split("[\\n,]"))
			{
				int colon = raw.lastIndexOf(':');
				if (colon <= 0 || minimums.size() >= MAX_RULES)
				{
					continue;
				}
				String name = raw.substring(0, colon).trim();
				String number = raw.substring(colon + 1).trim();
				int min;
				try
				{
					min = Integer.parseInt(number);
				}
				catch (NumberFormatException e)
				{
					continue;
				}
				if (name.isEmpty() || min <= 0)
				{
					continue;
				}
				String key = name.toLowerCase(Locale.ROOT);
				minimums.put(key, min);
				names.put(key, name);
			}
		}
		return new LowStock(minimums, names);
	}

	boolean isEmpty()
	{
		return minimums.isEmpty();
	}

	/** Rules newly below their minimum after this storage state. Updates which rules have alerted. */
	synchronized List<Alert> check(Map<Integer, Integer> storage, ItemLookup items)
	{
		Map<String, Integer> byName = new LinkedHashMap<>();
		for (Map.Entry<Integer, Integer> e : storage.entrySet())
		{
			String name = items.name(e.getKey()).toLowerCase(Locale.ROOT);
			byName.merge(name, e.getValue(), ItemCounts::saturatingAdd);
		}
		List<Alert> out = new ArrayList<>();
		for (Map.Entry<String, Integer> rule : minimums.entrySet())
		{
			int have = byName.getOrDefault(rule.getKey(), 0);
			if (have >= rule.getValue())
			{
				alerted.remove(rule.getKey());
			}
			else if (alerted.add(rule.getKey()))
			{
				out.add(new Alert(displayNames.get(rule.getKey()), have, rule.getValue()));
			}
		}
		return out;
	}
}
