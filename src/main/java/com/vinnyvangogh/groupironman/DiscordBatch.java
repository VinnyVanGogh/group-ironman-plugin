package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Holds the Discord-bound events of one moment until the rest of it has
 * arrived, one open moment per {@link DiscordMerge.Family}. A kill's pet line,
 * a tick's combat level and a death's value all come a little after the first
 * event; {@link DiscordMerge} then makes the moment one post.
 *
 * <p>A second event of a kind already held (the next kill's drop) starts a
 * new moment rather than joining the old one; levels, collection log slots
 * and minimums come several at once, so for them only the same skill, slot
 * or item twice does. Client thread
 * only, apart from {@link Pending#screenshot}, which the frame encoder sets.
 */
final class DiscordBatch
{
	static final class Pending
	{
		final DiscordMerge.Family family;
		final List<GroupEvent> events = new ArrayList<>();
		final long due;
		/** The frame drawn when the moment began; null until it is encoded, or when none was asked for. */
		volatile byte[] screenshot;

		private Pending(DiscordMerge.Family family, long due)
		{
			this.family = family;
			this.due = due;
		}

		private boolean repeats(GroupEvent e)
		{
			for (GroupEvent held : events)
			{
				if (held.type == e.type && (held.text.equalsIgnoreCase(e.text) || !sharesMoment(e.type)))
				{
					return true;
				}
			}
			return false;
		}

		/** Kinds that come several to a moment, one per skill, slot or item: only the same one twice is a repeat. */
		private static boolean sharesMoment(GroupEvent.Type type)
		{
			return DiscordMerge.family(type) == DiscordMerge.Family.LEVEL || type == GroupEvent.Type.CLOG
				|| type == GroupEvent.Type.STORAGE_MINIMUM;
		}
	}

	private final Map<DiscordMerge.Family, Pending> open = new EnumMap<>(DiscordMerge.Family.class);

	/**
	 * Adds an event of a family to its open moment, or opens one that stays open {@code window} ms. Moments this
	 * finishes go into {@code done}. Returns the moment the event joined; it was just opened when it holds only
	 * this event.
	 */
	Pending add(GroupEvent e, long now, long window, List<Pending> done)
	{
		DiscordMerge.Family family = DiscordMerge.family(e.type);
		Pending p = open.get(family);
		if (p != null && p.repeats(e))
		{
			done.add(open.remove(family));
			p = null;
		}
		if (p == null)
		{
			p = new Pending(family, now + window);
			open.put(family, p);
		}
		p.events.add(e);
		if (DiscordMerge.closes(e.type))
		{
			done.add(open.remove(family));
		}
		return p;
	}

	/** Lends an open moment an event that is not posted on its own (a storage visit's summary). */
	void attach(GroupEvent e)
	{
		Pending p = open.get(DiscordMerge.family(e.type));
		if (p != null && p.events.stream().noneMatch(e::sameAs))
		{
			p.events.add(e);
		}
	}

	/** The moments whose wait is over, removed. */
	List<Pending> due(long now)
	{
		List<Pending> out = new ArrayList<>();
		for (Iterator<Pending> it = open.values().iterator(); it.hasNext(); )
		{
			Pending p = it.next();
			if (now >= p.due)
			{
				out.add(p);
				it.remove();
			}
		}
		return out;
	}

	/** Every open moment, removed: at logout, nothing more of any of them is coming. */
	List<Pending> drain()
	{
		List<Pending> out = new ArrayList<>(open.values());
		open.clear();
		return out;
	}
}
