package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The maths behind the Leaderboard and Compare tabs, with no Swing: one selected stat ranked across the group, and the
 * figures two members put side by side. A figure nobody shared is unknown: it is never ranked and never counts as a
 * loss.
 */
final class SkillBoard
{
	static final String TOTAL = GroupCompare.TOTAL;
	static final String QUEST_POINTS = GroupCompare.QUEST_POINTS;
	static final String COMBAT = "Combat level";

	/** The skill grid's tiles: the 24 skills read across three columns, then total level, quest points and combat. */
	static final List<String> STATS;

	/** The Compare table's rows: the skills, then total level, combat level and quest points. */
	static final List<String> COMPARE_ROWS;

	static
	{
		List<String> s = new ArrayList<>(PanelModel.SKILL_ORDER);
		s.add(TOTAL);
		s.add(QUEST_POINTS);
		s.add(COMBAT);
		STATS = Collections.unmodifiableList(s);
		List<String> rows = new ArrayList<>(PanelModel.SKILL_ORDER);
		rows.add(TOTAL);
		rows.add(COMBAT);
		rows.add(QUEST_POINTS);
		COMPARE_ROWS = Collections.unmodifiableList(rows);
	}

	private SkillBoard()
	{
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

	/**
	 * The OSRS combat level from the seven combat skills, or -1 when any of them is not known. Base is a quarter of
	 * Defence + Hitpoints + half Prayer (rounded down); the best of melee, ranged and magic adds to it, ranged and
	 * magic counting one and a half times their level (rounded down), each at 0.325.
	 */
	static int combat(Map<String, Integer> levels)
	{
		String[] needed = {"Attack", "Strength", "Defence", "Hitpoints", "Prayer", "Ranged", "Magic"};
		for (String s : needed)
		{
			Integer l = levels.get(s);
			if (l == null || l < 1)
			{
				return -1;
			}
		}
		double base = 0.25 * (levels.get("Defence") + levels.get("Hitpoints") + levels.get("Prayer") / 2);
		double melee = 0.325 * (levels.get("Attack") + levels.get("Strength"));
		double ranged = 0.325 * (levels.get("Ranged") * 3 / 2);
		double magic = 0.325 * (levels.get("Magic") * 3 / 2);
		return (int) Math.floor(base + Math.max(melee, Math.max(ranged, magic)));
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
		else if (COMBAT.equals(stat))
		{
			v = combat(f.levels);
		}
		else
		{
			// A skill reads the shared level; anything else is a hiscore count (kills, clues, minigames).
			Integer l = PanelModel.SKILL_ORDER.contains(stat) ? f.levels.get(stat) : f.score(stat);
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

	/** The counts in {@code category} that at least one member has above zero, in the hiscores' order. */
	static List<String> activeCounts(List<GroupCompare.Figures> members, StatCategory category)
	{
		List<String> out = new ArrayList<>();
		for (String name : category.names())
		{
			for (GroupCompare.Figures f : members)
			{
				Integer n = f.score(name);
				if (n != null && n > 0)
				{
					out.add(name);
					break;
				}
			}
		}
		return out;
	}

	/** Whether {@code a} is above {@code b} for a Compare cell; false when either is unknown or they tie. */
	static boolean higher(Integer a, Integer b)
	{
		return a != null && b != null && a > b;
	}

	/** The leader's index in {@code values} (null = unknown), or -1 when nobody has a figure or the top is tied. */
	static int leader(List<Integer> values)
	{
		int best = -1;
		boolean tied = false;
		for (int i = 0; i < values.size(); i++)
		{
			Integer v = values.get(i);
			if (v == null)
			{
				continue;
			}
			if (best < 0 || v > values.get(best))
			{
				best = i;
				tied = false;
			}
			else if (v.equals(values.get(best)))
			{
				tied = true;
			}
		}
		return tied ? -1 : best;
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

	/** The bar's full scale: 99 for a skill, 126 for combat, all skills at 99 for total, else the best figure. */
	static int scale(String stat, List<Entry> ranked)
	{
		if (COMBAT.equals(stat))
		{
			return 126;
		}
		if (TOTAL.equals(stat))
		{
			return PanelModel.SKILL_ORDER.size() * 99;
		}
		if (!QUEST_POINTS.equals(stat))
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
