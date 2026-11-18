package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Who benefits most", end to end without Swing: finds the item a member typed, reads its stats and category, builds
 * each member's candidate figures from what they share (levels, worn gear, carried items) and ranks them with
 * {@link ItemBenefit}. The game's item data comes through {@link Source}, so this is testable without a client.
 */
final class BenefitFinder
{
	/** Equipment slot index (the game's EquipmentInventorySlot) to the {@link ItemBenefit} category. */
	private static final Map<Integer, String> CATEGORY = new HashMap<>();

	static
	{
		CATEGORY.put(0, "head");
		CATEGORY.put(1, "cape");
		CATEGORY.put(2, "neck");
		CATEGORY.put(3, "weapon");
		CATEGORY.put(4, "body");
		CATEGORY.put(5, "shield");
		CATEGORY.put(7, "legs");
		CATEGORY.put(9, "hands");
		CATEGORY.put(10, "feet");
		CATEGORY.put(12, "ring");
		CATEGORY.put(13, "ammo");
	}

	private BenefitFinder()
	{
	}

	/** The game's item data, answered from RuneLite's item manager in the plugin. */
	interface Source
	{
		/** The best match for typed text, or -1 when none. */
		int find(String query);

		String name(int id);

		/** The equipment slot index, or -1 for something not worn. */
		int slot(int id);

		/** Stat values by {@link ItemBenefit#ALL_STATS} key; empty for an item with none. */
		Map<String, Integer> stats(int id);

		/** Skill requirements by skill name; empty when none are known. */
		Map<String, Integer> requirements(int id);
	}

	/** What one member shares that the ranking reads. */
	static final class Member
	{
		final String name;
		final Map<String, Integer> levels;
		final Collection<Integer> worn;
		final Collection<Integer> carried;

		Member(String name, Map<String, Integer> levels, Collection<Integer> worn, Collection<Integer> carried)
		{
			this.name = name;
			this.levels = levels;
			this.worn = worn;
			this.carried = carried;
		}
	}

	/** The ranking, or a reason there is none. */
	static final class Outcome
	{
		final String item;
		final List<ItemBenefit.Result> ranked;
		/** Empty when there is a ranking. */
		final String message;
		/** True when no requirement is known for the item, so "can use it now" means only "not known to be blocked". */
		final boolean requirementsUnknown;

		Outcome(String item, List<ItemBenefit.Result> ranked, String message, boolean requirementsUnknown)
		{
			this.item = item;
			this.ranked = ranked;
			this.message = message;
			this.requirementsUnknown = requirementsUnknown;
		}
	}

	static Outcome run(String query, Source src, Collection<Member> members, ItemBenefit.Settings settings)
	{
		if (query == null || query.trim().isEmpty())
		{
			return new Outcome("", Collections.emptyList(), "Type an item name.", false);
		}
		int id = src.find(query.trim());
		if (id < 0)
		{
			return new Outcome(query.trim(), Collections.emptyList(), "No item found for \"" + query.trim() + "\".",
				false);
		}
		String name = src.name(id);
		int slot = src.slot(id);
		String category = slot < 0 ? "inventory" : CATEGORY.getOrDefault(slot, "inventory");
		if (!settings.categories().contains(category))
		{
			return new Outcome(name, Collections.emptyList(),
				name + " is a " + category + " item, and that category is switched off in the Compare settings.",
				false);
		}
		Map<String, Integer> reqs = src.requirements(id);
		ItemBenefit.Item item = new ItemBenefit.Item(name, category, reqs, src.stats(id));
		List<ItemBenefit.Candidate> candidates = new ArrayList<>();
		for (Member m : members)
		{
			candidates.add(new ItemBenefit.Candidate(m.name, m.levels, currentStats(src, m, slot),
				owns(m, id)));
		}
		return new Outcome(name, ItemBenefit.rank(item, candidates, settings), "", reqs.isEmpty());
	}

	/** Stats of what the member wears in the same slot, empty for no slot or an empty one. */
	private static Map<String, Integer> currentStats(Source src, Member m, int slot)
	{
		if (slot < 0)
		{
			return Collections.emptyMap();
		}
		for (int worn : m.worn)
		{
			if (src.slot(worn) == slot)
			{
				return src.stats(worn);
			}
		}
		return Collections.emptyMap();
	}

	/** Whether the member holds this very item, worn or carried. An equal-or-better item is {@link ItemBenefit}'s. */
	private static boolean owns(Member m, int id)
	{
		return m.worn.contains(id) || m.carried.contains(id);
	}
}
