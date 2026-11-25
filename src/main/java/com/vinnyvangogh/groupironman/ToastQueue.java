package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The event banners on screen and the group box's latest events. Pure and
 * clock-driven, so showing, expiring and stacking are tested without a
 * client.
 *
 * <p><b>Situational kinds.</b> {@link #visible} takes a filter: today it lets
 * everything through, and a later part can hide a kind outside where it
 * matters (only in the Wilderness, only in a boss room) without changing how
 * banners are kept.
 */
final class ToastQueue
{
	/** Banners kept at most, whatever the setting; the group box keeps as many recent events. */
	static final int MAX_KEPT = 20;

	static final class Toast
	{
		final GroupEvent event;
		final long shownAt;

		Toast(GroupEvent event, long shownAt)
		{
			this.event = event;
			this.shownAt = shownAt;
		}
	}

	/** Banners shown at most in any {@link #RATE_WINDOW_MS}; the rest are counted into one "+N more" line. */
	static final int MAX_PER_WINDOW = 6;
	static final long RATE_WINDOW_MS = 60_000;

	private final LinkedList<Toast> toasts = new LinkedList<>();
	private final LinkedList<GroupEvent> recent = new LinkedList<>();
	/** When each banner in the current window was shown, oldest first. */
	private final LinkedList<Long> shownTimes = new LinkedList<>();
	private int held;
	private long heldAt;

	/**
	 * A banner for {@code event}, unless {@link #MAX_PER_WINDOW} have shown in the last minute (RUNELITE-510): then
	 * it is counted for the "+N more" line instead. The group box lists it either way. False when held back.
	 */
	synchronized boolean add(GroupEvent event, long now)
	{
		recent.addFirst(event);
		while (recent.size() > MAX_KEPT)
		{
			recent.removeLast();
		}
		while (!shownTimes.isEmpty() && now - shownTimes.peekFirst() >= RATE_WINDOW_MS)
		{
			shownTimes.removeFirst();
		}
		if (shownTimes.size() >= MAX_PER_WINDOW)
		{
			held++;
			heldAt = now;
			return false;
		}
		shownTimes.addLast(now);
		toasts.addFirst(new Toast(event, now));
		while (toasts.size() > MAX_KEPT)
		{
			toasts.removeLast();
		}
		return true;
	}

	/** The banner line for {@code n} held-back events. */
	static String heldLine(int n)
	{
		return "+" + n + " more in the Log";
	}

	/** Banners held back by the rate limit, shown as "+N more" for {@code durationMs} after the last one. */
	synchronized int held(long now, long durationMs)
	{
		if (held > 0 && now - heldAt >= durationMs)
		{
			held = 0;
		}
		return held;
	}

	/** The newest {@code max} banners still within {@code durationMs}, newest first. Expired ones are dropped. */
	synchronized List<Toast> visible(long now, long durationMs, int max, Predicate<GroupEvent> situation)
	{
		List<Toast> out = new ArrayList<>();
		for (Iterator<Toast> it = toasts.iterator(); it.hasNext(); )
		{
			Toast t = it.next();
			if (now - t.shownAt >= durationMs)
			{
				it.remove();
			}
			else if (out.size() < max && situation.test(t.event))
			{
				out.add(t);
			}
		}
		return out;
	}

	/** Clears every banner on screen; the group box's recent events stay. Returns how many were showing. */
	synchronized int dismissAll()
	{
		int n = toasts.size();
		toasts.clear();
		held = 0;
		return n;
	}

	/** Clears the newest banner only. False when none was showing. */
	synchronized boolean dismissNewest()
	{
		return toasts.pollFirst() != null;
	}

	/** The newest {@code max} events seen, banners expired or not, for the group box. */
	synchronized List<GroupEvent> recent(int max)
	{
		return new ArrayList<>(recent.subList(0, Math.min(max, recent.size())));
	}

	synchronized void clear()
	{
		toasts.clear();
		recent.clear();
		shownTimes.clear();
		held = 0;
	}
}
