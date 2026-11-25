package com.vinnyvangogh.groupironman;

import java.util.*;

/**
 * What the player opened on the Shopping tab (Board 2026-11-14: lists start
 * collapsed with a preview, and opening one is remembered). Everything starts
 * closed: a list, a request, a list's "N done" section and an item's details.
 *
 * <p>Kept on this account only, never sent to the group: the plugin saves it in
 * the RuneLite profile of the logged-in character ({@link Store}). Keys of
 * lists that are gone are dropped on the next change, so the saved value never
 * grows past what is on screen.
 */
final class ShopFolds
{
	/** Where the open set is saved; {@code load} is null when nothing is (or no character is logged in). */
	interface Store
	{
		String load();

		void save(String value);
	}

	private final Store store;
	private final Set<String> open = new TreeSet<>();
	/** The saved value last read or written: a different one (another character) is read again. */
	private String seen;

	ShopFolds(Store store)
	{
		this.store = store;
	}

	static String list(String id)
	{
		return "l:" + id;
	}

	static String request(String id)
	{
		return "r:" + id;
	}

	/** A card's "N done" section. */
	static String done(String cardKey)
	{
		return "d:" + cardKey;
	}

	/** One item's details on one card. */
	static String item(String cardKey, int itemId)
	{
		return "i:" + cardKey + ":" + itemId;
	}

	boolean open(String key)
	{
		sync();
		return open.contains(key);
	}

	/** Opens a closed {@code key} or closes an open one, keeping only keys in {@code live}, then saves. */
	void toggle(String key, Collection<String> live)
	{
		sync();
		if (!open.remove(key))
		{
			open.add(key);
		}
		open.retainAll(live);
		seen = String.join(",", open);
		store.save(seen.isEmpty() ? null : seen);
	}

	private void sync()
	{
		String saved = store.load();
		if (saved == null || saved.equals(seen))
		{
			return;
		}
		open.clear();
		for (String key : saved.split(","))
		{
			if (!key.isEmpty())
			{
				open.add(key);
			}
		}
		seen = saved;
	}
}
