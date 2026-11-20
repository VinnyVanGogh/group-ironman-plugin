package com.vinnyvangogh.groupironman;

import java.util.*;
import java.util.List;

/**
 * One member's boss personal bests: boss name to best time in seconds. Shared with the group so a member card and a
 * boss page can show them, and so a beaten best can be posted ({@link #beaten}).
 *
 * <p>Built only through {@link #of}, which drops anything a hostile or newer sender could use to hurt a receiver:
 * non-finite or non-positive times, times over a day, blank or overlong names, and more than {@link #MAX_BOSSES}
 * entries.
 */
final class PersonalBests
{
	static final int MAX_BOSSES = 200;
	static final double MAX_SECONDS = 24 * 60 * 60;
	static final int MAX_NAME = 60;
	static final PersonalBests EMPTY = new PersonalBests(new TreeMap<>());

	/** A best that is new this time: {@code previous} is null for a boss with no earlier best. */
	static final class Beaten
	{
		final String boss;
		final Double previous;
		final double now;

		Beaten(String boss, Double previous, double now)
		{
			this.boss = boss;
			this.previous = previous;
			this.now = now;
		}
	}

	private final Map<String, Double> best;

	private PersonalBests(Map<String, Double> best)
	{
		this.best = Collections.unmodifiableMap(best);
	}

	static PersonalBests of(Map<String, Double> in)
	{
		Map<String, Double> out = new TreeMap<>();
		if (in != null)
		{
			// Sorted first so the cap keeps the same bosses whatever order the sender's map had.
			for (Map.Entry<String, Double> e : new TreeMap<>(in).entrySet())
			{
				String name = e.getKey() == null ? "" : e.getKey().trim();
				Double t = e.getValue();
				if (out.size() >= MAX_BOSSES)
				{
					break;
				}
				if (name.isEmpty() || name.length() > MAX_NAME || t == null || Double.isNaN(t)
					|| Double.isInfinite(t) || t <= 0 || t > MAX_SECONDS)
				{
					continue;
				}
				out.put(name, t);
			}
		}
		return new PersonalBests(out);
	}

	Map<String, Double> all()
	{
		return best;
	}

	Double get(String boss)
	{
		return best.get(boss);
	}

	boolean isEmpty()
	{
		return best.isEmpty();
	}

	/** Bests here that are faster than {@code earlier}'s, or that {@code earlier} did not have, by boss name. */
	List<Beaten> beaten(PersonalBests earlier)
	{
		List<Beaten> out = new ArrayList<>();
		for (Map.Entry<String, Double> e : best.entrySet())
		{
			Double prev = earlier.get(e.getKey());
			if (prev == null || e.getValue() < prev - 0.0001)
			{
				out.add(new Beaten(e.getKey(), prev, e.getValue()));
			}
		}
		return out;
	}

	/** "1:05.0" under an hour, "1:02:03" from an hour. */
	static String format(double seconds)
	{
		if (seconds >= 3600)
		{
			long s = Math.round(seconds);
			return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
		}
		long tenths = Math.round(seconds * 10);
		return String.format(Locale.ROOT, "%d:%02d.%d", tenths / 600, tenths / 10 % 60, tenths % 10);
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof PersonalBests && best.equals(((PersonalBests) o).best);
	}

	@Override
	public int hashCode()
	{
		return best.hashCode();
	}
}
