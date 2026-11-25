package com.vinnyvangogh.groupironman;

import java.util.*;
import java.util.List;

/**
 * The maths behind Compare, with no Swing and no game: two players side by side (boss times; the skill ranking is {@link SkillBoard}), and a
 * group's skill totals against another group's (the figures come from the hiscores, looked up with RuneLite's own
 * {@code HiscoreClient}). A figure nobody has shared is unknown, never a loss.
 */
final class GroupCompare
{
	static final String TOTAL = "Total level";
	static final String QUEST_POINTS = "Quest points";

	private GroupCompare()
	{
	}

	enum Category
	{
		OVERALL, SKILL, BOSS, QUESTS
	}

	enum Verdict
	{
		A, B, TIE, UNKNOWN
	}

	/** What one member has shared. {@code questPoints} is -1 when unknown. */
	static final class Figures
	{
		final String name;
		final Map<String, Integer> levels;
		final PersonalBests bests;
		final int questPoints;
		/** Hiscore counts (kills, clues, minigames) by hiscore name; null until a lookup has read them. */
		final Map<String, Integer> scores;

		Figures(String name, Map<String, Integer> levels, PersonalBests bests, int questPoints)
		{
			this(name, levels, bests, questPoints, null);
		}

		Figures(String name, Map<String, Integer> levels, PersonalBests bests, int questPoints,
			Map<String, Integer> scores)
		{
			this.name = name;
			this.levels = levels == null ? Collections.emptyMap() : levels;
			this.bests = bests == null ? PersonalBests.EMPTY : bests;
			this.questPoints = questPoints;
			this.scores = scores;
		}

		/** The count for a hiscore name: 0 when the hiscores were read and list none, null when never read. */
		Integer score(String hiscoreName)
		{
			return scores == null ? null : scores.getOrDefault(hiscoreName, 0);
		}

		/** Sum of the shared skill levels, or -1 when none were shared. */
		int total()
		{
			if (levels.isEmpty())
			{
				return -1;
			}
			int t = 0;
			for (int l : levels.values())
			{
				t += l;
			}
			return t;
		}
	}

	/** One stat. A value is null when unknown. For a boss the value is seconds and lower wins. */
	static final class Row
	{
		final String label;
		final Category category;
		final Double a;
		final Double b;
		final Verdict verdict;

		Row(String label, Category category, Double a, Double b, boolean lowerWins)
		{
			this.label = label;
			this.category = category;
			this.a = a;
			this.b = b;
			this.verdict = verdict(a, b, lowerWins);
		}
	}

	/** One skill's gap between two groups: {@code delta} is mine minus theirs, so negative is behind. */
	static final class Gap
	{
		final String label;
		final long mine;
		final long theirs;
		final long delta;

		Gap(String label, long mine, long theirs)
		{
			this.label = label;
			this.mine = mine;
			this.theirs = theirs;
			this.delta = mine - theirs;
		}
	}

	private static Verdict verdict(Double a, Double b, boolean lowerWins)
	{
		if (a == null || b == null)
		{
			return Verdict.UNKNOWN;
		}
		int c = Double.compare(a, b);
		if (c == 0)
		{
			return Verdict.TIE;
		}
		return (c > 0) != lowerWins ? Verdict.A : Verdict.B;
	}

	private static Double known(int v)
	{
		return v < 0 ? null : (double) v;
	}

	/** Every stat of a stat table for any of {@code all}, in display order: overall, quests, skills, bosses. */
	private static Map<String, Category> labels(List<Figures> all)
	{
		Map<String, Category> out = new java.util.LinkedHashMap<>();
		out.put(TOTAL, Category.OVERALL);
		out.put(QUEST_POINTS, Category.QUESTS);
		Set<String> skills = new TreeSet<>();
		Set<String> bosses = new TreeSet<>();
		for (Figures f : all)
		{
			skills.addAll(f.levels.keySet());
			bosses.addAll(f.bests.all().keySet());
		}
		skills.forEach(s -> out.put(s, Category.SKILL));
		bosses.forEach(s -> out.put(s, Category.BOSS));
		return out;
	}

	private static Double value(Figures f, String label, Category c)
	{
		switch (c)
		{
			case OVERALL:
				return known(f.total());
			case QUESTS:
				return known(f.questPoints);
			case SKILL:
				Integer l = f.levels.get(label);
				return l == null ? null : (double) l;
			default:
				return f.bests.get(label);
		}
	}

	static List<Row> players(Figures a, Figures b)
	{
		List<Row> out = new ArrayList<>();
		for (Map.Entry<String, Category> e : labels(java.util.Arrays.asList(a, b)).entrySet())
		{
			out.add(new Row(e.getKey(), e.getValue(), value(a, e.getKey(), e.getValue()),
				value(b, e.getKey(), e.getValue()), e.getValue() == Category.BOSS));
		}
		return out;
	}

	/** Skill by skill, {@code mine} against {@code theirs}, over every skill either has. */
	static List<Gap> groups(Map<String, Long> mine, Map<String, Long> theirs)
	{
		Set<String> skills = new TreeSet<>(mine.keySet());
		skills.addAll(theirs.keySet());
		List<Gap> out = new ArrayList<>();
		for (String s : skills)
		{
			out.add(new Gap(s, mine.getOrDefault(s, 0L), theirs.getOrDefault(s, 0L)));
		}
		return out;
	}
}
