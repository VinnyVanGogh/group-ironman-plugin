package com.vinnyvangogh.groupironman;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * One banner per event, however many clients or sources report it. The party resends a member's events whenever
 * someone joins, and a login can be told by the member's own client and read from the game's Group channel too.
 *
 * <p>Two rules. An announcement, like any event with its own timestamp, is the same event when its member, text and
 * time are the same (a resend). A login, logout or "everyone online" has no identity beyond who and what, so the same
 * one within {@link #WINDOW_MS} is the same event. Pure and clock-driven; memory is bounded by {@link #MAX_KEPT}.
 */
final class AlertDedup
{
	/** Online-style events by the same member and text closer than this are one. */
	static final long WINDOW_MS = TimeUnit.MINUTES.toMillis(1);
	static final int MAX_KEPT = 200;
	/** Resent events are remembered this long: longer than any party resend, shorter than a session. */
	static final long REMEMBER_MS = TimeUnit.HOURS.toMillis(1);

	private final Map<String, Long> seen = new LinkedHashMap<>();

	/** True the first time this event is offered, false for a repeat. {@code now} is this client's clock. */
	synchronized boolean fresh(GroupEvent e, long now)
	{
		expire(now);
		boolean windowed = e.type == GroupEvent.Type.ONLINE || e.type == GroupEvent.Type.EVERYONE_ONLINE;
		String key = e.type.code + '|' + GroupMembers.key(e.member) + '|' + e.text + (windowed ? "" : "|" + e.at);
		Long before = seen.get(key);
		if (before != null && (!windowed || now - before < WINDOW_MS))
		{
			return false;
		}
		seen.remove(key);
		seen.put(key, now);
		while (seen.size() > MAX_KEPT)
		{
			seen.remove(seen.keySet().iterator().next());
		}
		return true;
	}

	private void expire(long now)
	{
		for (Iterator<Long> it = seen.values().iterator(); it.hasNext(); )
		{
			if (now - it.next() < REMEMBER_MS)
			{
				// Oldest first: the rest are newer.
				return;
			}
			it.remove();
		}
	}

	synchronized void clear()
	{
		seen.clear();
	}
}
