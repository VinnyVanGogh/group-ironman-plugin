package com.vinnyvangogh.groupironman;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * One member's quest points, quest states and achievement diary tiers, shared with the group so the Compare tab,
 * the member card and the quest-needs list can read them. Built through {@link #of}, which clamps what a hostile
 * sender could use to hurt a receiver.
 *
 * <p>Diaries are one 4-bit mask per area, bit 0 easy up to bit 3 elite ({@link Tier}).
 */
final class AchievementState
{
	static final int MAX_QUESTS = 400;
	static final int MAX_AREAS = 30;
	static final int MAX_NAME = 80;
	static final int MAX_QP = 1000;
	static final int TIER_MASK = 0b1111;

	enum Tier
	{
		EASY, MEDIUM, HARD, ELITE
	}

	private final int questPoints;
	private final Set<String> finished;
	private final Set<String> started;
	private final Map<String, Integer> diaries;

	private AchievementState(int qp, Set<String> finished, Set<String> started, Map<String, Integer> diaries)
	{
		this.questPoints = qp;
		this.finished = Collections.unmodifiableSet(finished);
		this.started = Collections.unmodifiableSet(started);
		this.diaries = Collections.unmodifiableMap(diaries);
	}

	static AchievementState of(int qp, Collection<String> finished, Collection<String> started,
		Map<String, Integer> diaries)
	{
		Set<String> f = names(finished);
		Set<String> s = names(started);
		s.removeAll(f);
		Map<String, Integer> d = new TreeMap<>();
		if (diaries != null)
		{
			for (Map.Entry<String, Integer> e : new TreeMap<>(diaries).entrySet())
			{
				String name = e.getKey() == null ? "" : e.getKey().trim();
				int mask = e.getValue() == null ? 0 : e.getValue() & TIER_MASK;
				if (d.size() < MAX_AREAS && !name.isEmpty() && name.length() <= MAX_NAME)
				{
					d.put(name, mask);
				}
			}
		}
		return new AchievementState(Math.max(0, Math.min(MAX_QP, qp)), f, s, d);
	}

	private static Set<String> names(Collection<String> in)
	{
		Set<String> out = new TreeSet<>();
		if (in != null)
		{
			for (String n : in)
			{
				if (out.size() >= MAX_QUESTS)
				{
					break;
				}
				if (n != null && !n.trim().isEmpty() && n.length() <= MAX_NAME)
				{
					out.add(n);
				}
			}
		}
		return out;
	}

	int questPoints()
	{
		return questPoints;
	}

	boolean finished(String quest)
	{
		return finished.contains(quest);
	}

	Set<String> finished()
	{
		return finished;
	}

	/** Started and not finished. */
	Set<String> inProgress()
	{
		return started;
	}

	Map<String, Integer> diaries()
	{
		return diaries;
	}

	/** The tier mask for an area, 0 when unknown. */
	int diaryTiers(String area)
	{
		Integer m = diaries.get(area);
		return m == null ? 0 : m;
	}

	/** Tiers done across every area, for a one-number comparison. */
	int diaryTiersDone()
	{
		int n = 0;
		for (int m : diaries.values())
		{
			n += Integer.bitCount(m);
		}
		return n;
	}

	static boolean tierDone(int mask, Tier t)
	{
		return (mask & (1 << t.ordinal())) != 0;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof AchievementState))
		{
			return false;
		}
		AchievementState a = (AchievementState) o;
		return questPoints == a.questPoints && finished.equals(a.finished) && started.equals(a.started)
			&& diaries.equals(a.diaries);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(questPoints, finished, started, diaries);
	}
}
