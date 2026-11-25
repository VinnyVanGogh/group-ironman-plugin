package com.vinnyvangogh.groupironman;

import java.util.*;

/**
 * Skill levels needed to wield or wear an item: the bundled {@code item-requirements.txt}, from the Old School
 * RuneScape Wiki (see that file and THIRD_PARTY_NOTICES.md). RuneLite's item data carries none, so without this
 * "who benefits most" could not tell a member who is too low from one who is not.
 */
final class ItemRequirements
{
	static final String RESOURCE = "item-requirements.txt";
	private static final Set<String> SKILLS = new HashSet<>(Arrays.asList("Attack", "Strength", "Defence", "Ranged",
		"Magic", "Prayer", "Hitpoints", "Mining", "Smithing", "Fishing", "Cooking", "Firemaking", "Woodcutting",
		"Crafting", "Fletching", "Herblore", "Agility", "Thieving", "Slayer", "Farming", "Runecraft", "Hunter",
		"Construction"));
	private static final ItemRequirements BUNDLED = parse(RegionNames.resource(RESOURCE));

	private final Map<Integer, Map<String, Integer>> byItem;

	private ItemRequirements(Map<Integer, Map<String, Integer>> byItem)
	{
		this.byItem = byItem;
	}

	static ItemRequirements bundled()
	{
		return BUNDLED;
	}

	/** Levels by skill name; empty when the table has nothing for the item. */
	Map<String, Integer> of(int itemId)
	{
		return byItem.getOrDefault(itemId, Collections.emptyMap());
	}

	int size()
	{
		return byItem.size();
	}

	/** "id|name|Skill=level|...", one item a line; "#" lines and anything malformed are skipped. */
	static ItemRequirements parse(String text)
	{
		Map<Integer, Map<String, Integer>> out = new HashMap<>();
		for (String line : text.split("\n"))
		{
			String[] parts = line.trim().split("\\|");
			if (line.trim().startsWith("#") || parts.length < 3)
			{
				continue;
			}
			Map<String, Integer> levels = levels(parts);
			Integer id = number(parts[0]);
			if (id != null && !levels.isEmpty())
			{
				out.put(id, Collections.unmodifiableMap(levels));
			}
		}
		return new ItemRequirements(out);
	}

	private static Map<String, Integer> levels(String[] parts)
	{
		Map<String, Integer> levels = new LinkedHashMap<>();
		for (int i = 2; i < parts.length; i++)
		{
			String[] kv = parts[i].split("=");
			Integer level = kv.length == 2 ? number(kv[1]) : null;
			if (level != null && level > 0 && level <= 99 && SKILLS.contains(kv[0].trim()))
			{
				levels.put(kv[0].trim(), level);
			}
		}
		return levels;
	}

	private static Integer number(String text)
	{
		try
		{
			return Integer.parseInt(text.trim());
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}
}
