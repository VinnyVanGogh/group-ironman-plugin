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

	private final LinkedList<Toast> toasts = new LinkedList<>();
	private final LinkedList<GroupEvent> recent = new LinkedList<>();

	synchronized void add(GroupEvent event, long now)
	{
		toasts.addFirst(new Toast(event, now));
		recent.addFirst(event);
		while (toasts.size() > MAX_KEPT)
		{
			toasts.removeLast();
		}
		while (recent.size() > MAX_KEPT)
		{
			recent.removeLast();
		}
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

	/** The newest {@code max} events seen, banners expired or not, for the group box. */
	synchronized List<GroupEvent> recent(int max)
	{
		return new ArrayList<>(recent.subList(0, Math.min(max, recent.size())));
	}

	synchronized void clear()
	{
		toasts.clear();
		recent.clear();
	}
}
