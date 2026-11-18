package com.vinnyvangogh.groupironman;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The category pills on the Banks and Storage tabs. Gear is sorted by its item stats
 * ({@link ItemStyles}); runes are the game's closed set of rune names; resources are what is not gear, runes
 * or a cooked meal and is named like a skilling material.
 */
enum ItemCategory
{
	ALL("All"),
	RESOURCES("Resources"),
	COMBAT("Combat"),
	MELEE("Melee"),
	RANGED("Ranged"),
	MAGIC("Magic"),
	RUNES("Runes");

	/** Every rune the game has, by name: a closed set, so a list is exact where a heuristic would not be. */
	private static final Set<String> RUNE_NAMES = new HashSet<>(Arrays.asList(
		"air", "water", "earth", "fire", "mind", "body", "cosmic", "chaos", "nature", "law", "death", "blood", "soul",
		"astral", "wrath", "dust", "mist", "mud", "smoke", "steam", "lava", "sunfire"));

	private static final Pattern RESOURCE_NAME = Pattern.compile(
		"(^| )(ore|bar|bars|logs|plank|planks|coal|essence|hide|leather|bones|bone|seed|seeds|feather|feathers|"
			+ "clay|flax|limestone|sandstone|bowstring|sapling|nest|bird nest)$|^(raw|uncut|grimy|clean) |"
			+ "^(dragon|green|blue|red|black) dragonhide|(^| )(hide|hard leather|soft leather)( |$)");

	final String label;

	ItemCategory(String label)
	{
		this.label = label;
	}

	/** Whether the item belongs in this category. An item whose stats are not read yet matches only {@link #ALL}. */
	boolean matches(int id, ItemLookup items)
	{
		switch (this)
		{
			case ALL:
				return true;
			case RUNES:
				return isRune(items.name(id));
			case COMBAT:
				return (items.combatStyles(id) & ItemStyles.COMBAT) != 0;
			case MELEE:
				return (items.combatStyles(id) & ItemStyles.MELEE) != 0;
			case RANGED:
				return (items.combatStyles(id) & ItemStyles.RANGED) != 0;
			case MAGIC:
				return (items.combatStyles(id) & ItemStyles.MAGIC) != 0;
			default:
				return isResource(id, items);
		}
	}

	static boolean isRune(String name)
	{
		String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
		return n.endsWith(" rune") && RUNE_NAMES.contains(n.substring(0, n.length() - " rune".length()));
	}

	private static boolean isResource(int id, ItemLookup items)
	{
		String name = items.name(id).toLowerCase(Locale.ROOT);
		return items.combatStyles(id) == 0 && !isRune(name) && RESOURCE_NAME.matcher(name).find();
	}
}
