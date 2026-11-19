package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The maths behind the Compare and Leaderboard tabs ("what should I train"), with no Swing: one selected stat ranked
 * across the group, and one member against another sorted by the biggest gap. A figure nobody shared is unknown: it is
 * never ranked and never counts as a gap.
 */
final class SkillBoard
{
	static final String TOTAL = GroupCompare.TOTAL;
	static final String QUEST_POINTS = GroupCompare.QUEST_POINTS;

	/** The stats the dropdown offers: total level, the 24 skills, then quest points. */
	static final List<String> STATS;

	static
	{
		List<String> s = new ArrayList<>();
		s.add(TOTAL);
		s.addAll(PanelModel.SKILL_ORDER);
		s.add(QUEST_POINTS);
		STATS = Collections.unmodifiableList(s);
	}

	private SkillBoard()
	{
	}

	/** Which gaps the Compare tab lists. */
	enum Filter
	{
		BEHIND("Behind"), AHEAD("Ahead"), ALL("All");

		final String label;

		Filter(String label)
		{
			this.label = label;
		}
	}

	/** One member's place on the selected stat. {@code rank} is 0 when the figure is unknown. */
	static final class Entry
	{
		final String name;
		final Integer value;
		final int rank;
		/** The member just above, or null for the top rank and for unknown figures. */
		final String above;
		/** Levels (or points) needed to reach {@code above}: positive, 0 when there is nobody above. */
		final int toPass;
		/** For the top rank: the lead over the next member, 0 when tied or alone. */
		final int lead;
		/** The other members on the same figure. */
		final List<String> tiedWith;

		Entry(String name, Integer value, int rank, String above, int toPass, int lead, List<String> tiedWith)
		{
			this.name = name;
			this.value = value;
			this.rank = rank;
			this.above = above;
			this.toPass = toPass;
			this.lead = lead;
			this.tiedWith = tiedWith;
		}
	}

	/** One stat where you and another member differ or tie. {@code delta} is yours minus theirs. */
	static final class Gap
	{
		final String stat;
		final int mine;
		final int theirs;
		final int delta;

		Gap(String stat, int mine, int theirs)
		{
			this.stat = stat;
			this.mine = mine;
			this.theirs = theirs;
			this.delta = mine - theirs;
		}
	}

	/** The figure for one stat, or null when the member has not shared it. */
	static Integer value(GroupCompare.Figures f, String stat)
	{
		int v;
		if (TOTAL.equals(stat))
		{
			v = f.total();
		}
		else if (QUEST_POINTS.equals(stat))
		{
			v = f.questPoints;
		}
		else
		{
			Integer l = f.levels.get(stat);
			return l == null || l < 0 ? null : l;
		}
		return v < 0 ? null : v;
	}

	/**
	 * Everyone ranked on {@code stat}, highest first, ties sharing a rank (1, 1, 3) and listed in the members' own
	 * order; members with no figure follow, unranked.
	 */
	static List<Entry> rank(List<GroupCompare.Figures> members, String stat)
	{
		List<GroupCompare.Figures> known = new ArrayList<>();
		List<GroupCompare.Figures> unknown = new ArrayList<>();
		for (GroupCompare.Figures f : members)
		{
			(value(f, stat) == null ? unknown : known).add(f);
		}
		// Stable sort: ties keep the members' own order.
		known.sort((a, b) -> Integer.compare(value(b, stat), value(a, stat)));
		List<Entry> out = new ArrayList<>();
		for (GroupCompare.Figures f : known)
		{
			out.add(entryFor(known, stat, f));
		}
		for (GroupCompare.Figures f : unknown)
		{
			out.add(new Entry(f.name, null, 0, null, 0, 0, Collections.emptyList()));
		}
		return out;
	}

	/** {@code f}'s place in {@code known}, which is sorted highest first. */
	private static Entry entryFor(List<GroupCompare.Figures> known, String stat, GroupCompare.Figures f)
	{
		int v = value(f, stat);
		int start = 0;
		while (value(known.get(start), stat) != v)
		{
			start++;
		}
		// The member directly above is the first holder of the nearest higher figure.
		String above = null;
		int toPass = 0;
		if (start > 0)
		{
			int higher = value(known.get(start - 1), stat);
			above = firstWith(known, stat, higher);
			toPass = higher - v;
		}
		return new Entry(f.name, v, start + 1, above, toPass, start == 0 ? leadOver(known, stat, v) : 0,
			tied(known, stat, f));
	}

	/** How far {@code v}, the top figure, is ahead of the best figure below it; 0 when everyone ties. */
	private static int leadOver(List<GroupCompare.Figures> known, String stat, int v)
	{
		for (GroupCompare.Figures o : known)
		{
			if (value(o, stat) < v)
			{
				return v - value(o, stat);
			}
		}
		return 0;
	}

	private static String firstWith(List<GroupCompare.Figures> known, String stat, int v)
	{
		for (GroupCompare.Figures f : known)
		{
			if (value(f, stat) == v)
			{
				return f.name;
			}
		}
		return null;
	}

	private static List<String> tied(List<GroupCompare.Figures> known, String stat, GroupCompare.Figures self)
	{
		List<String> out = new ArrayList<>();
		int v = value(self, stat);
		for (GroupCompare.Figures f : known)
		{
			if (f != self && value(f, stat) == v)
			{
				out.add(f.name);
			}
		}
		return out;
	}

	/** {@code name}'s entry, or null when they are not in the list. */
	static Entry entry(List<Entry> ranked, String name)
	{
		for (Entry e : ranked)
		{
			if (e.name.equals(name))
			{
				return e;
			}
		}
		return null;
	}

	/** How many members have a figure. */
	static int rankedCount(List<Entry> ranked)
	{
		int n = 0;
		for (Entry e : ranked)
		{
			if (e.rank > 0)
			{
				n++;
			}
		}
		return n;
	}

	/**
	 * Every stat both members shared, filtered, biggest gap first (ties and equal gaps in dropdown order). Behind
	 * lists only where {@code me} is lower, Ahead only where higher, All also the ties.
	 */
	static List<Gap> compare(GroupCompare.Figures me, GroupCompare.Figures other, Filter filter)
	{
		List<Gap> out = new ArrayList<>();
		for (String stat : STATS)
		{
			Integer a = value(me, stat);
			Integer b = value(other, stat);
			if (a == null || b == null)
			{
				continue;
			}
			Gap g = new Gap(stat, a, b);
			if (filter == Filter.ALL || filter == Filter.BEHIND && g.delta < 0 || filter == Filter.AHEAD && g.delta > 0)
			{
				out.add(g);
			}
		}
		// Stable, so equal gaps stay in dropdown order.
		out.sort((x, y) -> Integer.compare(Math.abs(y.delta), Math.abs(x.delta)));
		return out;
	}

	/** The stat {@code steps} places on from {@code stat}, wrapping at both ends; an unknown stat starts at the top. */
	static String step(String stat, int steps)
	{
		int i = STATS.indexOf(stat);
		if (i < 0)
		{
			return STATS.get(0);
		}
		return STATS.get(Math.floorMod(i + steps, STATS.size()));
	}

	/** "3 to pass Member 1", "leading by 4", "tied with Member 2", or "" when nothing can be said. */
	static String hint(Entry e)
	{
		if (e.rank == 0)
		{
			return "not shared";
		}
		if (e.above != null)
		{
			return e.toPass + " to pass " + e.above;
		}
		if (!e.tiedWith.isEmpty())
		{
			return "tied with " + String.join(", ", e.tiedWith);
		}
		return e.lead > 0 ? "leading by " + e.lead : "";
	}

	/** The bar's full scale: 99 for a skill, the best figure for total level and quest points. */
	static int scale(String stat, List<Entry> ranked)
	{
		if (!TOTAL.equals(stat) && !QUEST_POINTS.equals(stat))
		{
			return 99;
		}
		int max = 1;
		for (Entry e : ranked)
		{
			if (e.value != null)
			{
				max = Math.max(max, e.value);
			}
		}
		return max;
	}
}
