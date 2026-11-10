package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.List;
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

	/** One matching item in one place: "Grimy guam leaf 15 - Alex bank". */
	static final class Hit
	{
		final int itemId;
		final String name;
		final long quantity;
		final String member;
		final String where;
		/** As {@link Source#seenAt}. */
		final long seenAt;

		Hit(int itemId, String name, long quantity, String member, String where, long seenAt)
		{
			this.itemId = itemId;
			this.name = name;
			this.quantity = quantity;
			this.member = member;
			this.where = where;
			this.seenAt = seenAt;
		}
	}

	private GroupSearch()
	{
	}

	/**
	 * Every matching item in every source, sorted by item name and then in
	 * source order, at most {@link #MAX_RESULTS}. Items that share a name (a
	 * noted and an unnoted id) are added up per source. Fewer than
	 * {@link #MIN_QUERY} characters returns nothing rather than the whole
	 * group's holdings.
	 */
	static List<Hit> hits(String query, List<Source> sources, ItemLookup items)
	{
		List<Hit> out = new ArrayList<>();
		ItemQuery q = ItemQuery.parse(query);
		if (q == null)
		{
			return out;
		}
		Map<String, List<Hit>> byItem = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (Source source : sources)
		{
			Map<String, long[]> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			for (Map.Entry<Integer, Integer> e : source.items.entrySet())
			{
				String name = items.name(e.getKey());
				if (q.matches(name))
				{
					// [id, quantity]: the first id seen stands for the icon.
					byName.computeIfAbsent(name, k -> new long[]{e.getKey(), 0})[1] += e.getValue();
				}
			}
			for (Map.Entry<String, long[]> e : byName.entrySet())
			{
				byItem.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(new Hit((int) e.getValue()[0],
					e.getKey(), e.getValue()[1], source.member, source.where, source.seenAt));
			}
		}
		for (List<Hit> list : byItem.values())
		{
			for (Hit h : list)
			{
				if (out.size() >= MAX_RESULTS)
				{
					return out;
				}
				out.add(h);
			}
		}
		return out;
	}

	/**
	 * "Shark: Alex bank 120 (2h ago), Sam inventory 5", one line per matching
	 * item name, sorted by name: {@link #hits} as text for {@code ::find}.
	 */
	static List<String> search(String query, List<Source> sources, ItemLookup items, long now)
	{
		Map<String, List<String>> lines = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (Hit h : hits(query, sources, items))
		{
			String when = h.seenAt < 0 ? "" : " (" + BoostText.ago(now, h.seenAt) + ")";
			lines.computeIfAbsent(h.name, k -> new ArrayList<>()).add(h.member + " " + h.where + " " + items.amount(h.quantity)
				+ when);
		}
		List<String> out = new ArrayList<>();
		for (Map.Entry<String, List<String>> e : lines.entrySet())
		{
			out.add(e.getKey() + ": " + String.join(", ", e.getValue()));
		}
		return out;
	}

	/**
	 * {@link #search} for the chatbox, worded and coloured by {@code style}:
	 * "Trout: 15 in your bank (just now), 140 in group storage (8m ago)".
	 */
	static List<String> searchStyled(String query, List<Source> sources, ItemLookup items, long now, FindStyle style)
	{
		Map<String, List<String>> lines = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (Hit h : hits(query, sources, items))
		{
			String when = h.seenAt < 0 ? "" : " (" + BoostText.ago(now, h.seenAt) + ")";
			lines.computeIfAbsent(h.name, k -> new ArrayList<>()).add(style.place(h) + when);
		}
		List<String> out = new ArrayList<>();
		for (Map.Entry<String, List<String>> e : lines.entrySet())
		{
			out.add(FindStyle.paint(e.getKey(), style.item) + ": " + String.join(", ", e.getValue()));
		}
		return out;
	}

	/** {@link #history} for the chatbox: "You took 15 Trout from storage (8m ago)", in the storage colour. */
	static List<String> historyStyled(String query, List<StorageChange> log, ItemLookup items, long now, int limit,
		FindStyle style)
	{
		List<String> out = new ArrayList<>();
		ItemQuery q = ItemQuery.parse(query);
		if (q == null)
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
				String who = style.isYou(c.member()) ? "You" : c.member();
				out.add(FindStyle.paint(who + " " + String.join(", ", parts), style.storage)
					+ " (" + BoostText.ago(now, c.at()) + ")");
			}
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
		ItemQuery q = ItemQuery.parse(query);
		if (q == null)
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

	private static Map<Integer, Integer> matching(Map<Integer, Integer> counts, ItemQuery q, ItemLookup items)
	{
		Map<Integer, Integer> out = new TreeMap<>();
		for (Map.Entry<Integer, Integer> e : counts.entrySet())
		{
			if (q.matches(items.name(e.getKey())))
			{
				out.put(e.getKey(), e.getValue());
			}
		}
		return out;
	}
}
