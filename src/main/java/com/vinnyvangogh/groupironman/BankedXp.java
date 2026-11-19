package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Banked experience: what the items a player already owns would give if used, and the levels that would reach.
 * The per-item figures are the bundled {@code banked-xp.txt} (see that file and THIRD_PARTY_NOTICES.md); nothing
 * is fetched at runtime. No Swing and no game, so the maths is tested on its own.
 *
 * <p>Only levels are shared between members, not experience, so a member's current experience is taken as the
 * start of their level. That makes every "reaches level N" an "at least": real experience is never below it.
 */
final class BankedXp
{
	static final String RESOURCE = "banked-xp.txt";
	static final int MAX_LEVEL = 99;
	private static final BankedXp BUNDLED = parse(RegionNames.resource(RESOURCE));

	/** Experience at the start of each level, index 1 to 99, by the game's own formula. */
	private static final int[] LEVEL_XP = levelTable();

	private final Map<Integer, Entry> byItem;

	private BankedXp(Map<Integer, Entry> byItem)
	{
		this.byItem = byItem;
	}

	/** One item's skill and the experience a single one gives. */
	static final class Entry
	{
		final String skill;
		final double xp;

		Entry(String skill, double xp)
		{
			this.skill = skill;
			this.xp = xp;
		}
	}

	/** One skill's banked experience and the level it would reach. */
	static final class Gain
	{
		final String skill;
		final double xp;
		final int from;
		final int to;

		Gain(String skill, double xp, int from, int to)
		{
			this.skill = skill;
			this.xp = xp;
			this.from = from;
			this.to = to;
		}

		/** Levels gained, never negative. */
		int levels()
		{
			return to - from;
		}
	}

	static BankedXp bundled()
	{
		return BUNDLED;
	}

	int size()
	{
		return byItem.size();
	}

	/** The entry for an item, or null when the table has nothing for it. */
	Entry of(int itemId)
	{
		return byItem.get(itemId);
	}

	/** "id|name|Skill|xp", one item a line; "#" lines and anything malformed are skipped. */
	static BankedXp parse(String text)
	{
		Map<Integer, Entry> out = new HashMap<>();
		for (String line : text.split("\n"))
		{
			String t = line.trim();
			String[] parts = t.split("\\|");
			if (t.startsWith("#") || parts.length != 4)
			{
				continue;
			}
			try
			{
				int id = Integer.parseInt(parts[0].trim());
				double xp = Double.parseDouble(parts[3].trim());
				if (id > 0 && xp > 0 && xp < 100000 && !parts[2].trim().isEmpty())
				{
					out.put(id, new Entry(parts[2].trim(), xp));
				}
			}
			catch (NumberFormatException e)
			{
				// A malformed line is skipped.
			}
		}
		return new BankedXp(Collections.unmodifiableMap(out));
	}

	/** Experience per skill if every counted item were used once; items the table does not know add nothing. */
	Map<String, Double> xp(Map<Integer, Integer> items)
	{
		Map<String, Double> out = new TreeMap<>();
		for (Map.Entry<Integer, Integer> e : items.entrySet())
		{
			Entry entry = byItem.get(e.getKey());
			if (entry != null && e.getValue() > 0)
			{
				out.merge(entry.skill, entry.xp * e.getValue(), Double::sum);
			}
		}
		return out;
	}

	/** Item counts added together; {@code null} maps count as empty. */
	@SafeVarargs
	static Map<Integer, Integer> combine(Map<Integer, Integer>... holdings)
	{
		Map<Integer, Integer> out = new HashMap<>();
		for (Map<Integer, Integer> h : holdings)
		{
			if (h != null)
			{
				h.forEach((id, n) -> out.merge(id, n, ItemCounts::saturatingAdd));
			}
		}
		return out;
	}

	/**
	 * What banked experience reaches for one player: every skill with any, the levels it starts at and ends at,
	 * most levels first, then most experience.
	 *
	 * @param levels the player's real level per skill; a skill missing here is not reported
	 */
	static List<Gain> gains(Map<String, Double> banked, Map<String, Integer> levels)
	{
		List<Gain> out = new ArrayList<>();
		for (Map.Entry<String, Double> e : banked.entrySet())
		{
			Integer from = levels.get(e.getKey());
			if (from == null || from < 1 || e.getValue() <= 0)
			{
				continue;
			}
			int start = Math.min(from, MAX_LEVEL);
			out.add(new Gain(e.getKey(), e.getValue(), from, levelAt(xpAt(start) + e.getValue(), from)));
		}
		out.sort((a, b) -> a.levels() != b.levels() ? Integer.compare(b.levels(), a.levels())
			: Double.compare(b.xp, a.xp));
		return out;
	}

	/** The level reached with {@code xp}, 99 at most and never below {@code floor}. */
	static int levelAt(double xp, int floor)
	{
		int level = 1;
		while (level < MAX_LEVEL && xp >= LEVEL_XP[level + 1])
		{
			level++;
		}
		return Math.max(level, floor);
	}

	/** Experience at the start of {@code level} (1 to 99). */
	static int xpAt(int level)
	{
		return LEVEL_XP[Math.max(1, Math.min(MAX_LEVEL, level))];
	}

	/** The next level a skill would reach with its banked experience, for the one-line summary. */
	static String describe(Gain g)
	{
		long xp = Math.round(g.xp);
		return g.skill + " " + (g.levels() > 0 ? g.from + " > " + g.to + " (+" + g.levels() + ")" : g.from)
			+ ", " + String.format("%,d", xp) + " xp";
	}

	/**
	 * What Compare's Banked XP mode shows: a block per member (levels their bank and inventory would reach), then
	 * the group's total per skill, with the shared storage counted once when {@code withStorage}.
	 *
	 * @param banked each member's banked experience per skill (bank plus inventory)
	 * @param storage the group storage's banked experience per skill, counted once
	 */
	static List<String> lines(Map<String, Map<String, Double>> banked, Map<String, Double> storage,
		Map<String, Map<String, Integer>> levels, boolean withStorage)
	{
		List<String> out = new ArrayList<>();
		Map<String, Double> group = new TreeMap<>();
		for (Map.Entry<String, Map<String, Double>> m : banked.entrySet())
		{
			m.getValue().forEach((skill, xp) -> group.merge(skill, xp, Double::sum));
			List<Gain> gains = gains(m.getValue(), levels.getOrDefault(m.getKey(), Collections.emptyMap()));
			out.add(m.getKey() + ":");
			if (gains.isEmpty())
			{
				out.add("  nothing banked that gives experience, or no bank shared yet");
			}
			gains.forEach(g -> out.add("  " + describe(g)));
		}
		if (withStorage)
		{
			storage.forEach((skill, xp) -> group.merge(skill, xp, Double::sum));
		}
		out.add("Group total" + (withStorage ? " (shared storage counted once):" : ":"));
		if (group.isEmpty())
		{
			out.add("  nothing yet");
		}
		group.forEach((skill, xp) -> out.add("  " + skill + ": " + String.format("%,d", Math.round(xp)) + " xp"));
		return out;
	}

	private static int[] levelTable()
	{
		int[] t = new int[MAX_LEVEL + 2];
		double points = 0;
		for (int level = 1; level <= MAX_LEVEL; level++)
		{
			t[level] = (int) Math.floor(points / 4);
			points += Math.floor(level + 300 * Math.pow(2, level / 7.0));
		}
		t[MAX_LEVEL + 1] = Integer.MAX_VALUE;
		return t;
	}
}
