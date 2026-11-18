package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Who benefits most": ranks members for one item by how much it helps them. Pure maths over figures the caller
 * supplies (requirements and stats from the game's item data, current best in slot from each member's shared
 * equipment), so it is testable without a client.
 *
 * <p>Order: members who can use it now come first, the biggest stat upgrade over their current gear leading; then
 * members who cannot yet, the fewest levels short leading; members who already own an equal or better item are
 * last, with no gain. An item with no counted stats has an upgrade of 0 for everyone, so the order falls to
 * usability.
 */
final class ItemBenefit
{
	static final Set<String> ALL_STATS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
		"melee_attack", "melee_strength", "ranged_attack", "ranged_strength", "magic_attack", "magic_damage",
		"defence", "prayer")));
	static final Set<String> ALL_CATEGORIES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
		"head", "cape", "neck", "ammo", "weapon", "body", "shield", "legs", "hands", "feet", "ring", "inventory")));

	private ItemBenefit()
	{
	}

	/** The item: skill requirements by skill name, stats by stat key, category as one of {@link #ALL_CATEGORIES}. */
	static final class Item
	{
		final String name;
		final String category;
		final Map<String, Integer> requirements;
		final Map<String, Integer> stats;

		Item(String name, String category, Map<String, Integer> requirements, Map<String, Integer> stats)
		{
			this.name = name;
			this.category = category;
			this.requirements = requirements;
			this.stats = stats;
		}
	}

	/** One member: real levels, the stats of what they wear in that slot now, and whether they own the item. */
	static final class Candidate
	{
		final String name;
		final Map<String, Integer> levels;
		final Map<String, Integer> currentStats;
		final boolean owns;

		Candidate(String name, Map<String, Integer> levels, Map<String, Integer> currentStats, boolean owns)
		{
			this.name = name;
			this.levels = levels;
			this.currentStats = currentStats;
			this.owns = owns;
		}
	}

	/** Which stats count and which item categories are ranked at all. */
	static final class Settings
	{
		private final Set<String> stats;
		private final Set<String> categories;

		Settings(Set<String> stats, Set<String> categories)
		{
			this.stats = stats;
			this.categories = categories;
		}

		static Settings all()
		{
			return new Settings(ALL_STATS, ALL_CATEGORIES);
		}

		/** From the two comma separated config strings: blank means all, and a name nobody knows is ignored. */
		static Settings parse(String stats, String categories)
		{
			return new Settings(pick(stats, ALL_STATS), pick(categories, ALL_CATEGORIES));
		}

		private static Set<String> pick(String text, Set<String> all)
		{
			Set<String> out = new LinkedHashSet<>();
			if (text != null)
			{
				for (String s : text.split(","))
				{
					String k = s.trim().toLowerCase(java.util.Locale.ROOT);
					if (all.contains(k))
					{
						out.add(k);
					}
				}
			}
			return out.isEmpty() ? all : out;
		}

		Set<String> stats()
		{
			return stats;
		}

		Set<String> categories()
		{
			return categories;
		}
	}

	static final class Result
	{
		final String name;
		final boolean usableNow;
		final int levelsShort;
		final int upgrade;
		final boolean alreadyOwned;

		Result(String name, boolean usableNow, int levelsShort, int upgrade, boolean alreadyOwned)
		{
			this.name = name;
			this.usableNow = usableNow;
			this.levelsShort = levelsShort;
			this.upgrade = upgrade;
			this.alreadyOwned = alreadyOwned;
		}
	}

	/** Empty when the item's category is switched off. */
	static List<Result> rank(Item item, Collection<Candidate> members, Settings settings)
	{
		if (!settings.categories().contains(item.category))
		{
			return Collections.emptyList();
		}
		List<Result> out = new ArrayList<>();
		for (Candidate c : members)
		{
			out.add(assess(item, c, settings));
		}
		out.sort(Comparator.comparingInt(ItemBenefit::group).thenComparing(ItemBenefit::within));
		return out;
	}

	private static Result assess(Item item, Candidate c, Settings settings)
	{
		int short_ = 0;
		for (Map.Entry<String, Integer> r : item.requirements.entrySet())
		{
			short_ += Math.max(0, r.getValue() - c.levels.getOrDefault(r.getKey(), 1));
		}
		int upgrade = 0;
		boolean positive = false;
		boolean covered = true;
		for (Map.Entry<String, Integer> s : item.stats.entrySet())
		{
			if (!settings.stats().contains(s.getKey()) || s.getValue() <= 0)
			{
				continue;
			}
			positive = true;
			int gain = s.getValue() - c.currentStats.getOrDefault(s.getKey(), 0);
			if (gain > 0)
			{
				upgrade += gain;
				covered = false;
			}
		}
		boolean owned = c.owns || positive && covered;
		return new Result(c.name, short_ == 0, short_, owned ? 0 : upgrade, owned);
	}

	/** Usable and not owned, then not yet usable, then owned. */
	private static int group(Result r)
	{
		return r.alreadyOwned ? 2 : r.usableNow ? 0 : 1;
	}

	/** Within a group: biggest upgrade first, or fewest levels short. Ties keep the caller's order (stable sort). */
	private static Integer within(Result r)
	{
		if (r.alreadyOwned)
		{
			return 0;
		}
		return r.usableNow ? -r.upgrade : r.levelsShort;
	}
}
