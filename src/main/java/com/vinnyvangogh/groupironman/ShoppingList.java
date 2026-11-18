package com.vinnyvangogh.groupironman;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * One shared shopping list: a preset (with its picks) or custom items, for
 * one or more members. Immutable; every change is a new revision, and the
 * newest revision wins everywhere ({@link #newerThan}), so the group agrees
 * without a server.
 *
 * <p>A removed list stays as a tombstone ({@link #removed}) so the removal
 * reaches members who were offline, instead of their copy bringing it back.
 */
final class ShoppingList
{
	static final int MAX_NAME = 40;
	static final int MAX_MEMBERS = 10;
	static final int MAX_ITEMS = 60;
	static final int MAX_PICKS = 10;
	/** A display name is at most 12; quest names run longer. */
	static final int MAX_TEXT = 80;
	static final int MAX_QUANTITY = 1_000_000;
	private static final Pattern ID = Pattern.compile("[a-z0-9]{1,16}");
	private static final SecureRandom RANDOM = new SecureRandom();

	static final String ADDED = "added";
	static final String RENAMED = "renamed";
	static final String REMOVED = "removed";

	final String id;
	final String name;
	/** The preset's id, empty for a custom list. */
	final String preset;
	final List<String> picks;
	final List<String> members;
	/** A custom list's items, each per member. */
	final Map<Integer, Integer> items;
	final long rev;
	final long at;
	final String by;
	final String action;
	final boolean removed;
	final long created;
	final String createdBy;

	ShoppingList(String id, String name, String preset, Collection<String> picks, Collection<String> members,
		Map<Integer, Integer> items, long rev, long at, String by, String action, boolean removed, long created,
		String createdBy)
	{
		this.id = id;
		this.name = cap(name, MAX_NAME);
		this.preset = cap(preset, MAX_TEXT);
		this.picks = capList(picks, MAX_PICKS);
		this.members = capList(members, MAX_MEMBERS);
		this.items = capItems(items);
		this.rev = rev;
		this.at = at;
		this.by = cap(by, MAX_TEXT);
		this.action = cap(action, 16);
		this.removed = removed;
		this.created = created;
		this.createdBy = cap(createdBy, MAX_TEXT);
	}

	/** A new list, revision 1. */
	static ShoppingList create(String name, String preset, Collection<String> picks, Collection<String> members,
		Map<Integer, Integer> items, String self, long now)
	{
		return new ShoppingList(newId(), name, preset, picks, members, items, 1, now, self, ADDED, false, now, self);
	}

	static String newId()
	{
		return Long.toString(RANDOM.nextLong() & Long.MAX_VALUE, 36);
	}

	static boolean validId(String id)
	{
		return id != null && ID.matcher(id).matches();
	}

	boolean custom()
	{
		return preset.isEmpty();
	}

	/** True when this revision should replace {@code other}: a higher revision, then later, then by name. */
	boolean newerThan(ShoppingList other)
	{
		if (other == null)
		{
			return true;
		}
		int order = order(rev, at, other.rev, other.at);
		if (order != 0)
		{
			return order > 0;
		}
		return by.compareTo(other.by) > 0 || by.equals(other.by) && removed && !other.removed;
	}

	/** Revisions in order: the higher revision, then the later one; 0 on a tie. Requests share it. */
	static int order(long rev, long at, long otherRev, long otherAt)
	{
		return rev != otherRev ? Long.compare(rev, otherRev) : Long.compare(at, otherAt);
	}

	private ShoppingList next(String newName, Collection<String> newMembers, Map<Integer, Integer> newItems,
		String self, long now, String newAction, boolean gone)
	{
		return new ShoppingList(id, newName, preset, picks, newMembers, newItems, rev + 1, Math.max(now, at), self,
			newAction, gone, created, createdBy);
	}

	ShoppingList renamed(String newName, String self, long now)
	{
		return next(newName, members, items, self, now, RENAMED, false);
	}

	ShoppingList removedBy(String self, long now)
	{
		return next(name, members, items, self, now, REMOVED, true);
	}

	static String cap(String s, int max)
	{
		String t = s == null ? "" : s.trim();
		return t.length() > max ? t.substring(0, max) : t;
	}

	private static List<String> capList(Collection<String> in, int max)
	{
		List<String> out = new ArrayList<>();
		for (String s : in == null ? Collections.<String>emptyList() : in)
		{
			String t = cap(s, MAX_TEXT);
			if (!t.isEmpty() && !out.contains(t) && out.size() < max)
			{
				out.add(t);
			}
		}
		return Collections.unmodifiableList(out);
	}

	/** Real item ids with a positive count, at most {@link #MAX_ITEMS}; requests ({@link RequestList}) share it. */
	static Map<Integer, Integer> capItems(Map<Integer, Integer> in)
	{
		Map<Integer, Integer> out = new TreeMap<>();
		for (Map.Entry<Integer, Integer> e : in == null ? Collections.<Integer, Integer>emptyMap().entrySet()
			: in.entrySet())
		{
			int id = e.getKey() == null ? 0 : e.getKey();
			int qty = e.getValue() == null ? 0 : e.getValue();
			if (id > 0 && id < ItemCounts.MAX_ITEM_ID && qty > 0 && out.size() < MAX_ITEMS)
			{
				out.put(id, Math.min(qty, MAX_QUANTITY));
			}
		}
		return Collections.unmodifiableMap(out);
	}
}
