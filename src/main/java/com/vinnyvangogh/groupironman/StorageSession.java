package com.vinnyvangogh.groupironman;

import java.util.Map;

/**
 * One open group storage window: the contents the game sent first, then
 * every change up to each Save and the close.
 *
 * <p>The game only keeps what is saved, and Save can be pressed more than
 * once per visit, so each Save (and the close) records what moved since the
 * previous one and starts the next diff from there. Pure, so the log can be
 * tested without a client.
 */
final class StorageSession
{
	private boolean open;
	private Map<Integer, Integer> base;
	private Map<Integer, Integer> latest;

	/** The storage interface opened: the next contents are the "before". */
	void open()
	{
		open = true;
		base = null;
		latest = null;
	}

	boolean isOpen()
	{
		return open;
	}

	/** The group storage container changed while open. */
	void contents(Map<Integer, Integer> now)
	{
		if (!open)
		{
			return;
		}
		Map<Integer, Integer> copy = ItemCounts.sorted(now);
		if (base == null)
		{
			base = copy;
		}
		latest = copy;
	}

	/** What the storage looks like now, null when nothing has arrived since opening. */
	Map<Integer, Integer> latest()
	{
		return latest;
	}

	/**
	 * Save was pressed: what moved since the last Save (or the open), by
	 * {@code member}. Null when nothing arrived yet; an empty change when
	 * nothing moved, which still carries the contents as a snapshot.
	 */
	StorageChange save(long at, String member)
	{
		if (!open || base == null || latest == null)
		{
			return null;
		}
		StorageChange change = StorageDiff.diff(at, base, latest).withMember(member);
		base = latest;
		return change;
	}

	/** The window closed: as {@link #save}, then nothing more until the next open. */
	StorageChange close(long at, String member)
	{
		StorageChange change = save(at, member);
		open = false;
		base = null;
		latest = null;
		return change;
	}
}
