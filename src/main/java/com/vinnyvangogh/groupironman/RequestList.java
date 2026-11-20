package com.vinnyvangogh.groupironman;

import java.util.*;

/**
 * One member's request to the group: their own named list of items and how
 * many of each they want ("Fire cape": 500 sharks, 20 prayer potions). The
 * group fills it; see {@link RequestBoard} for what is held and claimed.
 *
 * <p>Only its {@link #owner} ever writes a revision, so revisions never
 * conflict: the newest wins ({@link #newerThan}), as {@link ShoppingList}'s
 * do, and a revision naming a different owner is never taken
 * ({@link RequestBook}). Claims are separate records ({@link RequestClaim})
 * so that anyone can claim without touching the owner's list.
 *
 * <p>Items close two ways: the owner closes one, or the owner's client
 * closes it once the group holds the amount ({@link #closedItems}). The list
 * is {@link #closed} when every item is.
 */
final class RequestList
{
	static final String ADDED = "added";
	static final String RENAMED = "renamed";
	static final String CLOSED = "closed";
	static final String REMOVED = "removed";

	final String id;
	final String owner;
	final String name;
	final Map<Integer, Integer> items;
	/** Items done for good: reached or closed by the owner. Only ever ids on {@link #items}. */
	final java.util.Set<Integer> closedItems;
	final boolean closed;
	final boolean removed;
	final long rev;
	final long at;
	final long created;
	final String action;

	RequestList(String id, String owner, String name, Map<Integer, Integer> items, Collection<Integer> closedItems,
		boolean closed, boolean removed, long rev, long at, long created, String action)
	{
		this.id = id;
		this.owner = ShoppingList.cap(owner, ShoppingList.MAX_TEXT);
		this.items = ShoppingList.capItems(items);
		this.name = defaultName(name, this.owner);
		TreeSet<Integer> shut = new TreeSet<>();
		for (Integer i : closedItems == null ? Collections.<Integer>emptySet() : closedItems)
		{
			if (i != null && this.items.containsKey(i))
			{
				shut.add(i);
			}
		}
		this.closedItems = Collections.unmodifiableSet(shut);
		this.closed = closed || !this.items.isEmpty() && shut.size() == this.items.size();
		this.removed = removed;
		this.rev = rev;
		this.at = at;
		this.created = created;
		this.action = ShoppingList.cap(action, 16);
	}

	/** A new request, revision 1; a blank name becomes "Alice's requests". */
	static RequestList create(String name, Map<Integer, Integer> items, String owner, long now)
	{
		return new RequestList(ShoppingList.newId(), owner, name, items, Collections.emptySet(), false, false, 1, now,
			now, ADDED);
	}

	static String defaultName(String name, String owner)
	{
		String n = ShoppingList.cap(name, ShoppingList.MAX_NAME);
		return n.isEmpty() ? ShoppingList.cap(owner + "'s requests", ShoppingList.MAX_NAME) : n;
	}

	boolean ownedBy(String member)
	{
		return member != null && !owner.isEmpty() && GroupMembers.key(owner).equals(GroupMembers.key(member));
	}

	/** Open for claims: not closed and not removed. */
	boolean open()
	{
		return !closed && !removed;
	}

	/** True when this revision should replace {@code other}: a higher revision, then later, then a removal. */
	boolean newerThan(RequestList other)
	{
		if (other == null)
		{
			return true;
		}
		int order = ShoppingList.order(rev, at, other.rev, other.at);
		return order != 0 ? order > 0 : removed && !other.removed || closed && !other.closed;
	}

	private RequestList next(String newName, Collection<Integer> shut, boolean close, boolean gone, long now,
		String newAction)
	{
		return new RequestList(id, owner, newName, items, shut, close, gone, rev + 1, Math.max(now, at), created,
			newAction);
	}

	RequestList renamed(String newName, long now)
	{
		return next(newName, closedItems, closed, false, now, RENAMED);
	}

	/** These items closed as well; the list closes with its last one. */
	RequestList withClosedItems(Collection<Integer> ids, long now)
	{
		TreeSet<Integer> shut = new TreeSet<>(closedItems);
		shut.addAll(ids);
		return next(name, shut, closed, false, now, CLOSED);
	}

	RequestList closedBy(long now)
	{
		return next(name, closedItems, true, false, now, CLOSED);
	}

	RequestList removedBy(long now)
	{
		return next(name, closedItems, closed, true, now, REMOVED);
	}
}
