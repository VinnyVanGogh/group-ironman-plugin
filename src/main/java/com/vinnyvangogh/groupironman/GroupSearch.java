package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * "Who has it?": an item name search across every member's last bank,
 * inventory and worn items, and group storage.
 */
final class GroupSearch
{
	static final int MIN_QUERY = 2;
	static final int MAX_RESULTS = 50;

	/** One place items can be: a member's bank, inventory or equipment, or group storage. */
	static final class Source
	{
		final String member;
		final String where;
		final Map<Integer, Integer> items;
		/** When the source was last seen, or -1 for "live". */
		final long seenAt;

		Source(String member, String where, Map<Integer, Integer> items, long seenAt)
		{
			this.member = member;
			this.where = where;
			this.items = items;
			this.seenAt = seenAt;
		}
	}

	private GroupSearch()
	{
	}

	/**
	 * "Shark: Alex bank 120 (2h ago), Sam inventory 5", one line per matching
	 * item name, sorted by name. Fewer than {@link #MIN_QUERY} characters
	 * returns nothing rather than the whole group's holdings.
	 */
	static List<String> search(String query, List<Source> sources, ItemLookup items, long now)
	{
		List<String> out = new ArrayList<>();
		String q = normalize(query);
		if (q.length() < MIN_QUERY)
		{
			return out;
		}
		Map<String, List<String>> hits = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (Source source : sources)
		{
			Map<String, Long> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			for (Map.Entry<Integer, Integer> e : source.items.entrySet())
			{
				String name = items.name(e.getKey());
				if (name.toLowerCase(Locale.ROOT).contains(q))
				{
					byName.merge(name, (long) e.getValue(), Long::sum);
				}
			}
			for (Map.Entry<String, Long> e : byName.entrySet())
			{
				String when = source.seenAt < 0 ? "" : " (" + BoostText.ago(now, source.seenAt) + ")";
				hits.computeIfAbsent(e.getKey(), k -> new ArrayList<>())
					.add(source.member + " " + source.where + " " + e.getValue() + when);
			}
		}
		for (Map.Entry<String, List<String>> e : hits.entrySet())
		{
			if (out.size() >= MAX_RESULTS)
			{
				break;
			}
			out.add(e.getKey() + ": " + String.join(", ", e.getValue()));
		}
		return out;
	}

	/**
	 * The newest storage log visits that took or deposited a matching item,
	 * newest first, at most {@code limit}: "Alex took 1 x Dragon hunter wand
	 * from storage (3d ago)". {@code log} is {@link StorageLog#entries()},
	 * already newest first.
	 */
	static List<String> history(String query, List<StorageChange> log, ItemLookup items, long now, int limit)
	{
		List<String> out = new ArrayList<>();
		String q = normalize(query);
		if (q.length() < MIN_QUERY)
		{
			return out;
		}
		for (StorageChange c : log)
		{
			if (out.size() >= limit)
			{
				break;
			}
			Map<Integer, Integer> took = matching(c.took(), q, items);
			Map<Integer, Integer> deposited = matching(c.deposited(), q, items);
			List<String> parts = new ArrayList<>();
			if (!took.isEmpty())
			{
				parts.add("took " + StorageLog.describe(took, items) + " from storage");
			}
			if (!deposited.isEmpty())
			{
				parts.add("deposited " + StorageLog.describe(deposited, items) + " into storage");
			}
			if (!parts.isEmpty())
			{
				out.add(c.member() + " " + String.join(", ", parts) + " (" + BoostText.ago(now, c.at()) + ")");
			}
		}
		return out;
	}

	static String normalize(String query)
	{
		return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
	}

	private static Map<Integer, Integer> matching(Map<Integer, Integer> counts, String q, ItemLookup items)
	{
		Map<Integer, Integer> out = new TreeMap<>();
		for (Map.Entry<Integer, Integer> e : counts.entrySet())
		{
			if (items.name(e.getKey()).toLowerCase(Locale.ROOT).contains(q))
			{
				out.put(e.getKey(), e.getValue());
			}
		}
		return out;
	}
}
