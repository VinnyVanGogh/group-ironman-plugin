package com.vinnyvangogh.groupironman;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * The one path this plugin asked Shortest Path for, and what it was for. The
 * panel's buttons start it ({@link #go}) and stop it ({@link #stop}); each
 * redraw {@link #recheck}s it, so the path goes away by itself once the item is
 * done, its list is removed or the request closes, and "Path to next" moves on
 * to the next stop once the current one has nothing left to get.
 *
 * <p>Any thread: the panel calls it from Swing, redraws come from several
 * threads, and the bridge hands every post to the client thread itself.
 */
final class PathSession
{
	private final ShortestPathBridge bridge;
	private ItemPaths.Target target;
	private ItemPaths.Stop stop;

	PathSession(ShortestPathBridge bridge)
	{
		this.bridge = bridge;
	}

	/** Paths to {@code to} for {@code why}; the line the panel shows either way. */
	synchronized String go(ItemPaths.Target why, ItemPaths.Stop to, boolean located)
	{
		if (!located)
		{
			return "Log in first: the path starts where you stand.";
		}
		ShortestPathBridge.Availability a = bridge.availability();
		if (a != ShortestPathBridge.Availability.AVAILABLE)
		{
			return unavailable(a);
		}
		if (!send(to))
		{
			return "Shortest Path did not take the path: update it, or try again.";
		}
		target = why;
		stop = to;
		return to.label() + ".";
	}

	/** Clears the path; "Path cleared." or empty when there was none. */
	synchronized String stop()
	{
		if (target == null)
		{
			return "";
		}
		bridge.clear();
		forget();
		return "Path cleared.";
	}

	/**
	 * Checks the path against where its target leads now ({@code stops},
	 * nearest first). Still a stop for it: left alone, even if another is now
	 * nearer, so walking never re-routes. Not any more: the nearest remaining
	 * stop, or cleared when there is none.
	 *
	 * @return whether the path changed
	 */
	synchronized boolean recheck(Function<ItemPaths.Target, List<ItemPaths.Stop>> stops)
	{
		if (target == null)
		{
			return false;
		}
		List<ItemPaths.Stop> now = stops.apply(target);
		if (now == null)
		{
			now = Collections.emptyList();
		}
		if (ItemPaths.named(now, stop.name))
		{
			return false;
		}
		if (!now.isEmpty() && send(now.get(0)))
		{
			stop = now.get(0);
			return true;
		}
		bridge.clear();
		forget();
		return true;
	}

	/** The stop being pathed to; empty for none. */
	synchronized String active()
	{
		return stop == null ? "" : stop.name;
	}

	static String unavailable(ShortestPathBridge.Availability a)
	{
		switch (a)
		{
			case NOT_INSTALLED:
				return a.menuLabel() + ": get it from the Plugin Hub to path to items.";
			case NOT_ENABLED:
				return a.menuLabel() + ": turn it on in the plugin list to path to items.";
			default:
				return a.menuLabel() + ".";
		}
	}

	private boolean send(ItemPaths.Stop to)
	{
		AtomicReference<ShortestPathBridge.RouteOutcome> out = new AtomicReference<>();
		bridge.route(Collections.singleton(to.at), out::set);
		return out.get() == ShortestPathBridge.RouteOutcome.SENT;
	}

	private void forget()
	{
		target = null;
		stop = null;
	}
}
