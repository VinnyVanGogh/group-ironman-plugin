package com.vinnyvangogh.groupironman;

import java.util.*;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * What a group still has to get for one shopping list, item by item: "you all
 * need 6 buckets, the group holds 2, get 4".
 *
 * <p>Ported from Quest Path's {@code groupshop.GroupShoppingList}
 * (questpath-plugin ee638ff). Holdings are spent in the order a group would
 * spend them:
 * <ol>
 * <li><b>Your own first.</b> What a member holds covers their own need before
 * anyone else's. The only offset an untradeable item ever makes.</li>
 * <li><b>Then spares between members</b>: a member who needs none of an item
 * spares all of it. Every member of a Group Ironman group can trade with the
 * others, so there is no account-type check here.</li>
 * <li><b>Then group storage</b>, from the last snapshot anyone saw.</li>
 * </ol>
 *
 * <p>A member who shares no item counts is <em>unknown</em>: their need is in
 * {@link Line#unknownNeed()}, never in {@link Line#stillNeeded()}, because
 * telling the group to get what may be in that member's bank is the confident
 * wrong answer.
 */
final class ShopAllocation
{
	/** {@link GroupRoster#searchSources} files group storage under this. */
	static final String STORAGE_WHERE = "storage";

	private ShopAllocation()
	{
	}

	/** One member's part of one line. */
	static final class Share
	{
		final String member;
		final long need;
		final long held;
		final long received;
		final long fromStorage;
		final long shortBy;
		final boolean unknown;

		Share(String member, long need, long held, long received, long fromStorage, long shortBy, boolean unknown)
		{
			this.member = member;
			this.need = need;
			this.held = held;
			this.received = received;
			this.fromStorage = fromStorage;
			this.shortBy = shortBy;
			this.unknown = unknown;
		}
	}

	/** One item across the group. */
	static final class Line
	{
		final int itemId;
		final String name;
		final boolean tradeable;
		final boolean stated;
		final boolean obtainable;
		final Set<String> quests;
		final List<Share> shares;
		/** Everyone holding any, needed or not, in member order. */
		final Map<String, Long> holders;
		final long storageHeld;
		final long storageUsed;

		Line(int itemId, String name, boolean tradeable, boolean stated, boolean obtainable, Set<String> quests,
			List<Share> shares, Map<String, Long> holders, long storageHeld, long storageUsed)
		{
			this.itemId = itemId;
			this.name = name;
			this.tradeable = tradeable;
			this.stated = stated;
			this.obtainable = obtainable;
			this.quests = Collections.unmodifiableSet(quests);
			this.shares = Collections.unmodifiableList(shares);
			this.holders = Collections.unmodifiableMap(holders);
			this.storageHeld = storageHeld;
			this.storageUsed = storageUsed;
		}

		long totalNeeded()
		{
			long total = 0;
			for (Share s : shares)
			{
				total += s.need;
			}
			return total;
		}

		/** The headline: how many more the group has to get, members without shared items left out. */
		long stillNeeded()
		{
			long total = 0;
			for (Share s : shares)
			{
				total += s.shortBy;
			}
			return total;
		}

		/** Need from members who share no item counts, kept out of {@link #stillNeeded()}. */
		long unknownNeed()
		{
			long total = 0;
			for (Share s : shares)
			{
				total += s.unknown ? s.need : 0;
			}
			return total;
		}

		/** Ticked off: every known member is covered and nobody's need is unknown. */
		boolean done()
		{
			return stillNeeded() == 0 && unknownNeed() == 0;
		}

		/** What the group holds towards it: members' own and spares plus storage used. */
		long have()
		{
			return totalNeeded() - unknownNeed() - stillNeeded();
		}
	}

	/**
	 * Who holds what, as the group's shared data shows it. Names match the way the roster does ({@link
	 * GroupMembers#key}): "Iron_Bob" on a list and "Iron Bob" from the party are one member.
	 */
	static final class Holdings
	{
		/** By member key; a member absent here shares no item counts. */
		private final Map<String, Map<Integer, Long>> members = new LinkedHashMap<>();
		/** Each member key's name as the party spells it. */
		private final Map<String, String> names = new LinkedHashMap<>();
		/** Group storage as last seen; empty when nobody has seen it. */
		final Map<Integer, Long> storage;

		Holdings(Map<String, Map<Integer, Long>> members, Map<Integer, Long> storage)
		{
			members.forEach((name, items) ->
			{
				this.members.put(GroupMembers.key(name), items);
				this.names.putIfAbsent(GroupMembers.key(name), name);
			});
			this.storage = storage;
		}

		long held(String member, int itemId)
		{
			Map<Integer, Long> m = members.get(GroupMembers.key(member));
			return m == null ? 0 : m.getOrDefault(itemId, 0L);
		}

		/** What the whole group holds of {@code itemId}: every member who shares counts, and group storage. */
		long total(int itemId)
		{
			long sum = storage.getOrDefault(itemId, 0L);
			for (Map<Integer, Long> m : members.values())
			{
				sum += m.getOrDefault(itemId, 0L);
			}
			return sum;
		}

		boolean known(String member)
		{
			return members.containsKey(GroupMembers.key(member));
		}

		/**
		 * From everything "Who has it?" searches ({@link GroupRoster#searchSources}): each member's bank,
		 * inventory, equipment, rune pouch, quiver and seed vault added up, and group storage apart. Counts there
		 * are already canonical (noted counted as the item, {@link ItemCounts#add}).
		 */
		static Holdings from(List<GroupSearch.Source> sources)
		{
			Map<String, Map<Integer, Long>> members = new LinkedHashMap<>();
			Map<Integer, Long> storage = new LinkedHashMap<>();
			for (GroupSearch.Source s : sources)
			{
				Map<Integer, Long> into = STORAGE_WHERE.equals(s.where) ? storage
					: members.computeIfAbsent(s.member, k -> new LinkedHashMap<>());
				s.items.forEach((id, qty) -> into.merge(id, qty.longValue(), Long::sum));
			}
			return new Holdings(members, storage);
		}

		/** Every member who shares item counts, as the party spells them. */
		List<String> names()
		{
			return new ArrayList<>(names.values());
		}
	}

	/**
	 * @param needs every member the list is for, in display order, with what they need by item id
	 * @param tradeable false spends only each member's own holding; unknown items should answer false
	 */
	static List<Line> of(Map<String, Map<Integer, ShopNeeds.Need>> needs, Holdings holdings, IntPredicate tradeable)
	{
		Map<Integer, ShopNeeds.Need> items = new LinkedHashMap<>();
		for (Map<Integer, ShopNeeds.Need> m : needs.values())
		{
			for (ShopNeeds.Need n : m.values())
			{
				items.merge(n.itemId, n, ShopAllocation::describe);
			}
		}
		List<Line> lines = new ArrayList<>();
		for (ShopNeeds.Need item : items.values())
		{
			lines.add(new Allocation(item, needs, holdings, tradeable.test(item.itemId)).line());
		}
		lines.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
		return lines;
	}

	/** Merges two members' needs for one item into the line's description; counts stay per member. */
	private static ShopNeeds.Need describe(ShopNeeds.Need a, ShopNeeds.Need b)
	{
		Set<String> quests = new TreeSet<>(a.quests);
		quests.addAll(b.quests);
		return new ShopNeeds.Need(a.itemId, a.name, 0, a.stated && b.stated, a.obtainable && b.obtainable, quests);
	}

	/** Spends holdings against one item's needs, in the order the class doc gives. */
	private static final class Allocation
	{
		private final ShopNeeds.Need item;
		private final Holdings holdings;
		private final boolean tradeable;
		private final List<String> order = new ArrayList<>();
		private final Map<String, long[]> slots = new LinkedHashMap<>();
		private final Map<String, Long> spare = new LinkedHashMap<>();
		private final long storageHeld;
		private long storageLeft;

		private static final int NEED = 0;
		private static final int HELD = 1;
		private static final int RECEIVED = 2;
		private static final int STORAGE = 3;
		private static final int SHORT = 4;

		Allocation(ShopNeeds.Need item, Map<String, Map<Integer, ShopNeeds.Need>> needs, Holdings holdings,
			boolean tradeable)
		{
			this.item = item;
			this.holdings = holdings;
			this.tradeable = tradeable;
			this.storageHeld = holdings.storage.getOrDefault(item.itemId, 0L);
			this.storageLeft = tradeable ? storageHeld : 0L;
			Set<String> keys = new java.util.HashSet<>();
			for (String m : needs.keySet())
			{
				order.add(m);
				keys.add(GroupMembers.key(m));
			}
			for (String m : holdings.names())
			{
				if (keys.add(GroupMembers.key(m)))
				{
					order.add(m);
				}
			}
			spendOwn(needs);
			if (tradeable)
			{
				pool();
				spendStorage();
			}
		}

		private void spendOwn(Map<String, Map<Integer, ShopNeeds.Need>> needs)
		{
			for (String member : order)
			{
				long held = holdings.held(member, item.itemId);
				Map<Integer, ShopNeeds.Need> theirs = needs.get(member);
				ShopNeeds.Need n = theirs == null ? null : theirs.get(item.itemId);
				long need = n == null ? 0 : n.count;
				long own = Math.min(held, need);
				boolean known = holdings.known(member);
				if (known && held > own)
				{
					spare.put(member, held - own);
				}
				if (need > 0)
				{
					slots.put(member, new long[]{need, held, 0L, 0L, known ? need - own : 0L});
				}
			}
		}

		private void pool()
		{
			for (Map.Entry<String, long[]> entry : slots.entrySet())
			{
				long[] slot = entry.getValue();
				for (String giver : order)
				{
					long available = spare.getOrDefault(giver, 0L);
					if (slot[SHORT] == 0 || giver.equals(entry.getKey()) || available == 0)
					{
						continue;
					}
					long given = Math.min(available, slot[SHORT]);
					spare.put(giver, available - given);
					slot[RECEIVED] += given;
					slot[SHORT] -= given;
				}
			}
		}

		private void spendStorage()
		{
			for (long[] slot : slots.values())
			{
				long taken = Math.min(storageLeft, slot[SHORT]);
				storageLeft -= taken;
				slot[STORAGE] += taken;
				slot[SHORT] -= taken;
			}
		}

		Line line()
		{
			List<Share> shares = new ArrayList<>();
			for (Map.Entry<String, long[]> entry : slots.entrySet())
			{
				long[] s = entry.getValue();
				shares.add(new Share(entry.getKey(), s[NEED], s[HELD], s[RECEIVED], s[STORAGE], s[SHORT],
					!holdings.known(entry.getKey())));
			}
			Map<String, Long> holders = new LinkedHashMap<>();
			for (String member : order)
			{
				long held = holdings.held(member, item.itemId);
				if (held > 0)
				{
					holders.put(member, held);
				}
			}
			long used = (tradeable ? storageHeld : 0L) - storageLeft;
			return new Line(item.itemId, item.name, tradeable, item.stated, item.obtainable, new TreeSet<>(item.quests),
				shares, holders, storageHeld, used);
		}
	}
}
