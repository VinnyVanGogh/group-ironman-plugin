package com.vinnyvangogh.groupironman;

import java.util.Map;

/**
 * One open group storage window: the contents the game sent first, then
 * every change up to each save.
 *
 * <p>The game only keeps what is saved (Board 2026-11-14: closing without
 * Save throws the changes away), and Save can be pressed more than once per
 * visit, so each save records what moved since the previous one and starts
 * the next diff from there. Closing records nothing: a save the game makes
 * as the window closes still arrives as a save, within {@link #LATE_SAVE_MS}.
 *
 * <p>Truth check: the first contents after the next open are what the game
 * actually kept. When they differ from what was last committed, they come
 * back as a snapshot (an empty change), never as a logged move. Pure, so the
 * log can be tested without a client.
 */
final class StorageSession
{
	/** How long after the window closes a "Saving..." still belongs to that visit. */
	static final long LATE_SAVE_MS = 5_000;

	private boolean open;
	/** The contents as of the open or the last save: what the game holds. */
	private Map<Integer, Integer> base;
	/** The contents now, saved or not. */
	private Map<Integer, Integer> latest;
	/** When the window closed; -1 while open or never opened. */
	private long closedAt = -1;
	/** What was last handed back to be committed as the storage snapshot; null this session. */
	private Map<Integer, Integer> committed;

	/** The storage interface opened: the next contents are the "before". */
	void open()
	{
		open = true;
		base = null;
		latest = null;
		closedAt = -1;
	}

	boolean isOpen()
	{
		return open;
	}

	/**
	 * The group storage container changed while open. The first contents of a
	 * visit are the game's truth: returned as a snapshot when they differ from
	 * what was last committed (or nothing was yet), null otherwise.
	 */
	StorageChange contents(long at, String member, Map<Integer, Integer> now)
	{
		if (!open)
		{
			return null;
		}
		Map<Integer, Integer> copy = ItemCounts.sorted(now);
		latest = copy;
		if (base != null)
		{
			return null;
		}
		base = copy;
		if (copy.equals(committed))
		{
			return null;
		}
		committed = copy;
		return StorageDiff.diff(at, copy, copy).withMember(member);
	}

	/**
	 * The game saved (Save, or "Saving..." shown): what moved since the last
	 * save (or the open), by {@code member}. Null when nothing arrived yet, or
	 * the window has been closed for longer than {@link #LATE_SAVE_MS}; an
	 * empty change when nothing moved, which still carries the contents as a
	 * snapshot.
	 */
	StorageChange save(long at, String member)
	{
		if (base == null || latest == null)
		{
			return null;
		}
		if (!open && (closedAt < 0 || at - closedAt > LATE_SAVE_MS))
		{
			return null;
		}
		StorageChange change = StorageDiff.diff(at, base, latest).withMember(member);
		base = latest;
		committed = latest;
		return change;
	}

	/** The window closed: nothing is recorded, unsaved changes are the game's to discard. */
	void close(long at)
	{
		if (open)
		{
			open = false;
			closedAt = at;
		}
	}

	/** Logged out or stopped: forget everything, including what was last committed. */
	void reset()
	{
		open = false;
		base = null;
		latest = null;
		closedAt = -1;
		committed = null;
	}
}
