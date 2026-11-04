package com.vinnyvangogh.groupironman;

import java.util.concurrent.TimeUnit;

/** Small text formats the group panel uses, kept pure so they are tested. */
final class BoostText
{
	private BoostText()
	{
	}

	/** "90" when unboosted, "90 (+8)" when boosted, "90 (-3)" when drained. */
	static String level(int real, int boosted)
	{
		int diff = boosted - real;
		if (diff == 0)
		{
			return Integer.toString(real);
		}
		return real + " (" + (diff > 0 ? "+" : "") + diff + ")";
	}

	/** "just now", "5m ago", "3h ago", "2d ago". A time ahead of now (clock skew) reads as just now. */
	static String ago(long now, long then)
	{
		long ms = now - then;
		if (ms < TimeUnit.MINUTES.toMillis(1))
		{
			return "just now";
		}
		if (ms < TimeUnit.HOURS.toMillis(1))
		{
			return TimeUnit.MILLISECONDS.toMinutes(ms) + "m ago";
		}
		if (ms < TimeUnit.DAYS.toMillis(1))
		{
			return TimeUnit.MILLISECONDS.toHours(ms) + "h ago";
		}
		return TimeUnit.MILLISECONDS.toDays(ms) + "d ago";
	}

	/** "12.3M", "45.6K", "999": a gp total short enough for the side panel. */
	static String gp(long value)
	{
		if (value >= 10_000_000_000L)
		{
			return (value / 1_000_000_000L) + "B";
		}
		if (value >= 1_000_000_000L)
		{
			return String.format(java.util.Locale.ROOT, "%.1fB", value / 1e9);
		}
		if (value >= 1_000_000L)
		{
			return String.format(java.util.Locale.ROOT, "%.1fM", value / 1e6);
		}
		if (value >= 10_000L)
		{
			return String.format(java.util.Locale.ROOT, "%.1fK", value / 1e3);
		}
		return Long.toString(value);
	}
}
