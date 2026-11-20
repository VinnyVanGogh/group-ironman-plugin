package com.vinnyvangogh.groupironman;

import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * The safety net under {@link LoginGate}: a login's first minute holds the events {@code counts} picks instead of
 * posting them. When the minute ends, a handful is posted as usual; more than that is the old account's or the
 * chat history's backlog read as new (Board 2026-11-15: an account switch posted four old diaries and three old
 * levels), and nothing is posted. Client thread only.
 */
final class LoginBurst
{
	/** How long after a login events are held. */
	static final long WINDOW_MS = TimeUnit.MINUTES.toMillis(1);
	/** Most held events still posted; one more and the whole minute is dropped. */
	static final int MAX_POSTS = 3;
	/** This player's own kinds a login could replay: progress, the collection log, counts and bests, Slayer. */
	static final Set<GroupEvent.Type> ACHIEVEMENTS = achievements();

	private final Predicate<GroupEvent.Type> counts;
	private final List<GroupEvent> held = new ArrayList<>();
	private long until;
	private int dropped;

	LoginBurst(Predicate<GroupEvent.Type> counts)
	{
		this.counts = counts;
	}

	private static Set<GroupEvent.Type> achievements()
	{
		Set<GroupEvent.Type> kinds = EnumSet.noneOf(GroupEvent.Type.class);
		for (GroupEvent.Type t : GroupEvent.Type.values())
		{
			if (t.category == GroupEvent.Category.PROGRESS)
			{
				kinds.add(t);
			}
		}
		kinds.addAll(EnumSet.of(GroupEvent.Type.CLOG, GroupEvent.Type.PERSONAL_BEST, GroupEvent.Type.KC_MILESTONE,
			GroupEvent.Type.SLAYER));
		return Collections.unmodifiableSet(kinds);
	}

	/** A login (or a new account) starts a minute; what is already held stays counted in it. */
	void open(long now)
	{
		until = now + WINDOW_MS;
	}

	/** True when {@code event} is held: one {@code counts} picks, inside a login's first minute. */
	boolean hold(GroupEvent event, long now)
	{
		if (now >= until || !counts.test(event.type))
		{
			return false;
		}
		held.add(event);
		return true;
	}

	/** Once the minute is over: what was held when it stayed a handful, nothing when it was a burst. */
	List<GroupEvent> due(long now)
	{
		if (now < until || held.isEmpty())
		{
			return Collections.emptyList();
		}
		List<GroupEvent> out = new ArrayList<>(held);
		held.clear();
		if (out.size() > MAX_POSTS)
		{
			dropped += out.size();
			return Collections.emptyList();
		}
		return out;
	}

	/** The session is over (logout, hop): the minute ends now. */
	List<GroupEvent> end()
	{
		until = 0;
		return due(0);
	}

	/** How many held events were dropped as a burst since the last call; for the log. */
	int takeDropped()
	{
		int n = dropped;
		dropped = 0;
		return n;
	}
}
