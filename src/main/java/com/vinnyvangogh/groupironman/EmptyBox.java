package com.vinnyvangogh.groupironman;

import java.time.*;
import java.util.*;
import java.util.List;

/**
 * The one line the Group box shows when nobody else is online. Pure: the clock, the rotation step and the group's
 * state come in, so a given moment always picks the same line.
 */
final class EmptyBox
{
	static final String CLASSIC = "Nobody else online";
	/** The line changes this often. */
	static final long ROTATE_MS = 30_000;
	/** One rotation step in this many shows the rare line. */
	static final int EGG_ONE_IN = 50;
	static final String EGG = "You found the secret line. Tell no one.";

	private EmptyBox()
	{
	}

	/** Which part of the day {@code hour} (0-23) falls in. */
	static int window(int hour)
	{
		if (hour >= 5 && hour < 12)
		{
			return 0;
		}
		if (hour >= 12 && hour < 17)
		{
			return 1;
		}
		return hour >= 17 && hour < 22 ? 2 : 3;
	}

	static List<String> fun(int hour, boolean weekend)
	{
		List<String> out = new ArrayList<>();
		switch (window(hour))
		{
			case 0:
				out.add("Early bird gets the logs");
				out.add("Good morning. Bank run?");
				break;
			case 1:
				out.add("Quiet afternoon. Grind time");
				out.add("Lunch break? Stack some XP");
				break;
			case 2:
				out.add("Evening shift, solo");
				out.add("Prime time, empty group");
				break;
			default:
				out.add("The group is asleep. Your bank isn't");
				out.add("Night owl mode");
				break;
		}
		if (weekend)
		{
			out.add("Weekend. Where is everyone?");
		}
		return out;
	}

	/** "Alex last on 3h ago" for each member, newest sighting first (the caller leaves this player out). */
	static List<String> lastOn(Map<String, LastSeen> seen, long now)
	{
		List<Map.Entry<String, LastSeen>> rows = new ArrayList<>(seen.entrySet());
		rows.sort((a, b) -> Long.compare(b.getValue().at, a.getValue().at));
		List<String> out = new ArrayList<>();
		for (Map.Entry<String, LastSeen> e : rows)
		{
			if (e.getValue().at > 0)
			{
				out.add(e.getKey() + " last on " + BoostText.ago(now, e.getValue().at));
			}
		}
		return out;
	}

	/**
	 * @param latest the newest event already worded ("Alex: Level 40 Mining"), or null
	 * @param step the rotation step, {@code now / ROTATE_MS}
	 */
	static String pick(EmptyBoxMode mode, ZonedDateTime when, long now, Map<String, LastSeen> seen, String latest,
		long step)
	{
		if (mode == EmptyBoxMode.CLASSIC)
		{
			return CLASSIC;
		}
		boolean useful = mode != EmptyBoxMode.FUN;
		boolean fun = mode != EmptyBoxMode.USEFUL;
		if (fun && Math.floorMod(step * 2654435761L, (long) EGG_ONE_IN) == 0)
		{
			return EGG;
		}
		List<String> pool = new ArrayList<>();
		if (useful)
		{
			pool.addAll(lastOn(seen, now));
			if (latest != null && !latest.isEmpty())
			{
				pool.add("Latest: " + latest);
			}
		}
		if (fun)
		{
			DayOfWeek d = when.getDayOfWeek();
			pool.addAll(fun(when.getHour(), d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY));
		}
		return pool.isEmpty() ? CLASSIC : pool.get((int) Math.floorMod(step, (long) pool.size()));
	}
}
