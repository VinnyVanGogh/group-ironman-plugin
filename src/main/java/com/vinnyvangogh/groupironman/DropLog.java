package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The "since you last played" log: every member's drops and new collection
 * log slots, newest first, the newest {@link #MAX_ENTRIES} kept.
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

	/** Adds an event in time order. Invalid events and duplicates are dropped. */
	synchronized boolean add(GroupEvent event)
	{
		if (!event.isValid() || event.member.isEmpty())
		{
			return false;
		}
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
				return false;
			}
			index++;
		}
		entries.add(index, event);
		while (entries.size() > MAX_ENTRIES)
		{
			entries.removeLast();
		}
		return true;
	}

	synchronized List<GroupEvent> entries()
	{
		return new ArrayList<>(entries);
	}

	synchronized void clear()
	{
		entries.clear();
	}

	/** Events after {@code since}, newest first, leaving out {@code self}'s own. */
	List<GroupEvent> since(long since, String self)
	{
		List<GroupEvent> out = new ArrayList<>();
		for (GroupEvent e : entries())
		{
			if (e.at <= since)
			{
				break;
			}
			if (self == null || !e.member.equalsIgnoreCase(self))
			{
				out.add(e);
			}
		}
		return out;
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

	/** "Alex: Vorkath dropped 1 x Draconic visage (2h ago)". */
	static List<String> lines(List<GroupEvent> events, ItemLookup items, long now)
	{
		List<String> out = new ArrayList<>();
		for (GroupEvent e : events)
		{
			out.add(e.member + ": " + e.describe(items) + " (" + BoostText.ago(now, e.at) + ")");
		}
		return out;
	}
}
