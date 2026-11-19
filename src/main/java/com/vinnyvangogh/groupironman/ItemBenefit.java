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
		/** What the member wears in the slot ("Dragon scimitar"), or null when unnamed. */
		final String currentName;
		/** False when the member's gear was never shared: nothing is compared. */
		final boolean gearKnown;
		/** False when the member's levels were never shared: nothing is checked against them. */
		final boolean levelsKnown;

		Candidate(String name, Map<String, Integer> levels, Map<String, Integer> currentStats, boolean owns)
		{
			this(name, levels, currentStats, owns, null, true, true);
		}

		Candidate(String name, Map<String, Integer> levels, Map<String, Integer> currentStats, boolean owns,
			String currentName, boolean gearKnown, boolean levelsKnown)
		{
			this.name = name;
			this.levels = levels;
			this.currentStats = currentStats;
			this.owns = owns;
			this.currentName = currentName;
			this.gearKnown = gearKnown;
			this.levelsKnown = levelsKnown;
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
		/** One "needs 70 Attack, has 40" per level the member lacks; empty when none or unknown. */
		final List<String> missing;
		/** What the upgrade is measured against ("Dragon scimitar", "an empty slot"); null when not compared. */
		final String comparedTo;
		final boolean gearKnown;
		final boolean levelsKnown;

		Result(String name, boolean usableNow, int levelsShort, int upgrade, boolean alreadyOwned)
		{
			this(name, usableNow, levelsShort, upgrade, alreadyOwned, Collections.emptyList(), null, true, true);
		}

		Result(String name, boolean usableNow, int levelsShort, int upgrade, boolean alreadyOwned,
			List<String> missing, String comparedTo, boolean gearKnown, boolean levelsKnown)
		{
			this.name = name;
			this.usableNow = usableNow;
			this.levelsShort = levelsShort;
			this.upgrade = upgrade;
			this.alreadyOwned = alreadyOwned;
			this.missing = missing;
			this.comparedTo = comparedTo;
			this.gearKnown = gearKnown;
			this.levelsKnown = levelsKnown;
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
		boolean blindToLevels = !c.levelsKnown && !item.requirements.isEmpty();
		List<String> missing = blindToLevels ? Collections.<String>emptyList() : missing(item, c);
		int short_ = blindToLevels ? 0 : levelsShort(item, c);
		int upgrade = c.gearKnown ? upgrade(item, c, settings) : 0;
		boolean owned = c.owns || c.gearKnown && upgrade == 0 && counts(item, settings);
		return new Result(c.name, !blindToLevels && short_ == 0, short_, owned ? 0 : upgrade, owned, missing,
			comparedTo(c), c.gearKnown, !blindToLevels);
	}

	/** "needs 70 Attack, has 40" per requirement the member does not meet. */
	private static List<String> missing(Item item, Candidate c)
	{
		List<String> out = new ArrayList<>();
		for (Map.Entry<String, Integer> r : item.requirements.entrySet())
		{
			int has = c.levels.getOrDefault(r.getKey(), 1);
			if (has < r.getValue())
			{
				out.add(String.format(java.util.Locale.ROOT, "needs %d %s, has %d", r.getValue(), r.getKey(), has));
			}
		}
		return out;
	}

	private static int levelsShort(Item item, Candidate c)
	{
		int out = 0;
		for (Map.Entry<String, Integer> r : item.requirements.entrySet())
		{
			out += Math.max(0, r.getValue() - c.levels.getOrDefault(r.getKey(), 1));
		}
		return out;
	}

	/** Summed stat gain over what the member wears in the slot, for the stats that count. */
	private static int upgrade(Item item, Candidate c, Settings settings)
	{
		int out = 0;
		for (Map.Entry<String, Integer> s : item.stats.entrySet())
		{
			if (settings.stats().contains(s.getKey()) && s.getValue() > 0)
			{
				out += Math.max(0, s.getValue() - c.currentStats.getOrDefault(s.getKey(), 0));
			}
		}
		return out;
	}

	/** Whether the item has any positive stat that counts. */
	private static boolean counts(Item item, Settings settings)
	{
		return item.stats.entrySet().stream()
			.anyMatch(s -> settings.stats().contains(s.getKey()) && s.getValue() > 0);
	}

	private static String comparedTo(Candidate c)
	{
		if (!c.gearKnown)
		{
			return null;
		}
		return c.currentName != null ? c.currentName : c.currentStats.isEmpty() ? "an empty slot" : null;
	}

	/** Usable and not owned, then not yet usable, then owned. */
	private static int group(Result r)
	{
		return r.alreadyOwned ? 2 : r.usableNow ? 0 : 1;
	}

	/**
	 * Within a group: biggest upgrade first, or fewest levels short, members whose levels are unknown last. Ties
	 * keep the caller's order (stable sort).
	 */
	private static Integer within(Result r)
	{
		if (r.alreadyOwned)
		{
			return 0;
		}
		if (r.usableNow)
		{
			return -r.upgrade;
		}
		return r.levelsKnown ? r.levelsShort : Integer.MAX_VALUE;
	}
}
