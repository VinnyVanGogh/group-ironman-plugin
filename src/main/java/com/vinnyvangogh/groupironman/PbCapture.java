package com.vinnyvangogh.groupironman;

import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads this player's own boss personal bests from their own game chat. The game says the boss in a kill count line
 * and the time in the "Fight duration" line that follows, so the boss is remembered for exactly one time line.
 * Wording is unverified in game (BRIEF.md #33). Not thread-safe: call from one thread.
 */
final class PbCapture
{
	private static final Pattern KILL_COUNT = Pattern.compile("^Your (.{1,60}?) (?:kill|success|completion) count is: ");
	private static final Pattern NEW_BEST = Pattern.compile("(?:duration|time)[^:]*: ([\\d:.]+) \\(new personal best\\)",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern PERSONAL_BEST = Pattern.compile("Personal best: ([\\d:.]+)");

	private final Map<String, Double> bests = new TreeMap<>();
	private String boss;

	/** Starts from bests saved earlier, so a slower kill after a restart never replaces them. */
	void load(Map<String, Double> saved)
	{
		bests.clear();
		bests.putAll(PersonalBests.of(saved).all());
		boss = null;
	}

	/**
	 * One game line.
	 *
	 * @return true when this line made a best new or faster
	 */
	boolean chat(String message)
	{
		String text = EventText.plain(message);
		Matcher kc = KILL_COUNT.matcher(text);
		if (kc.find())
		{
			boss = kc.group(1);
			return false;
		}
		if (boss == null)
		{
			return false;
		}
		Double t = null;
		Matcher fresh = NEW_BEST.matcher(text);
		Matcher pb = PERSONAL_BEST.matcher(text);
		if (fresh.find())
		{
			t = seconds(fresh.group(1));
		}
		else if (pb.find())
		{
			t = seconds(pb.group(1));
		}
		else
		{
			return false;
		}
		String name = boss;
		boss = null;
		Double have = bests.get(name);
		if (t == null || have != null && t >= have - 0.0001)
		{
			return false;
		}
		bests.put(name, t);
		return true;
	}

	PersonalBests bests()
	{
		return PersonalBests.of(bests);
	}

	/** "1:05.4" or "1:02:03" in seconds, or null when it is neither. */
	static Double seconds(String s)
	{
		if (s == null)
		{
			return null;
		}
		String[] parts = s.split(":", -1);
		if (parts.length < 2 || parts.length > 3)
		{
			return null;
		}
		try
		{
			double total = 0;
			for (String p : parts)
			{
				total = total * 60 + Double.parseDouble(p);
			}
			return total;
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}
}
