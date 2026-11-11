package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * A log of group events, newest first, without duplicates. As the drops log
 * it holds every member's drops and new collection log slots, the newest
 * {@link #MAX_ENTRIES}, and is what each client resends. The Log tab's feed
 * of every event is one too, with {@link ActivityLog}'s bounds.
 *
 * <p>There is no server to hold events for a member who is offline. Instead
 * each client keeps its own events for {@link #RESEND_WINDOW_MS} and resends
 * them whenever someone joins the party, so a member who logs in later still
 * gets them as long as the other member is online at some point after. Resends
 * are recognised with {@link GroupEvent#sameAs} and logged once.
 */
final class DropLog
{
	static final int MAX_ENTRIES = 300;
	static final int MAX_RESEND = 50;
	static final long RESEND_WINDOW_MS = TimeUnit.DAYS.toMillis(7);

	private final LinkedList<GroupEvent> entries = new LinkedList<>();
	private final int maxEntries;
	/** Oldest an entry may be, against {@link #clock}; {@link Long#MAX_VALUE} for no age limit. */
	private final long maxAgeMs;
	private final LongSupplier clock;

	/** The drops log: the newest {@link #MAX_ENTRIES}, any age. */
	DropLog()
	{
		this(MAX_ENTRIES, Long.MAX_VALUE, System::currentTimeMillis);
	}

	/** A log of the newest {@code maxEntries} no older than {@code maxAgeMs}, whichever keeps fewer. */
	DropLog(int maxEntries, long maxAgeMs, LongSupplier clock)
	{
		this.maxEntries = maxEntries;
		this.maxAgeMs = maxAgeMs;
		this.clock = clock;
	}

	/** Adds an event in time order. Invalid, too old and duplicate events are dropped. */
	synchronized boolean add(GroupEvent event)
	{
		long now = clock.getAsLong();
		if (!event.isValid() || event.member.isEmpty() || tooOld(event, now))
		{
			return false;
		}
		int index = insertionIndex(event);
		if (index < 0)
		{
			return false;
		}
		entries.add(index, event);
		while (entries.size() > maxEntries || tooOld(entries.getLast(), now))
		{
			entries.removeLast();
		}
		return true;
	}

	/** Where {@code event} goes, newest first; -1 when it is already here. */
	private int insertionIndex(GroupEvent event)
	{
		// Newest first, so every entry at the same time or later is passed, and checked, before the insertion point.
		int index = 0;
		for (GroupEvent e : entries)
		{
			if (e.at < event.at)
			{
				break;
			}
			if (e.sameAs(event))
			{
				return -1;
			}
			index++;
		}
		return index;
	}

	private boolean tooOld(GroupEvent event, long now)
	{
		return maxAgeMs != Long.MAX_VALUE && now - event.at > maxAgeMs;
	}

	synchronized List<GroupEvent> entries()
	{
		return new ArrayList<>(entries);
	}

	synchronized void clear()
	{
		entries.clear();
	}

	/** {@code member}'s own events from the resend window, newest first, at most {@link #MAX_RESEND}. */
	List<GroupEvent> recentBy(String member, long now)
	{
		List<GroupEvent> out = new ArrayList<>();
		for (GroupEvent e : entries())
		{
			if (out.size() >= MAX_RESEND || now - e.at > RESEND_WINDOW_MS)
			{
				break;
			}
			if (e.member.equalsIgnoreCase(member))
			{
				out.add(e);
			}
		}
		return out;
	}
}
