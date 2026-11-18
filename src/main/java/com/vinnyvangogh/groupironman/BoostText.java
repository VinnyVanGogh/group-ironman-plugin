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
}
