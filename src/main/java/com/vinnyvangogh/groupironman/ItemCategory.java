package com.vinnyvangogh.groupironman;

import java.util.*;
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
	FOOD("Food"),
	POTIONS("Potions"),
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

	/** Cooked food by name; raw and burnt versions are excluded in {@link #isFood}. */
	private static final Pattern FOOD_NAME = Pattern.compile(
		"(^| )(shrimps|anchovies|sardine|herring|trout|salmon|tuna|bass|pike|cod|lobster|swordfish|monkfish|"
			+ "karambwan|shark|manta ray|sea turtle|anglerfish|dark crab|bread|cake|pie|pizza|stew|cheese|"
			+ "potato|chocolate bar|chocolate slice|jug of wine|wine|cooked [a-z ]+|meat|chicken|"
			+ "kebab|bagel|sandwich|tea|curry|wrap|crab meat|cave eel|eel)( |$)");

	/** A potion: a dose in brackets, or named like one. Vials and unfinished potions are not potions to drink. */
	private static final Pattern POTION_NAME = Pattern.compile(
		"\\([1-6]\\)$|(^| )(potion|brew|restore|antidote\\+*|antipoison|antifire|serum)( |$)");

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
			case FOOD:
				return isFood(id, items);
			case POTIONS:
				return isPotion(id, items);
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

	private static boolean isFood(int id, ItemLookup items)
	{
		String name = items.name(id).toLowerCase(Locale.ROOT);
		return items.combatStyles(id) == 0 && !name.startsWith("raw ") && !name.startsWith("burnt ")
			&& !name.startsWith("uncooked ") && !name.contains("(unf)") && !POTION_NAME.matcher(name).find()
			&& FOOD_NAME.matcher(name).find();
	}

	private static boolean isPotion(int id, ItemLookup items)
	{
		String name = items.name(id).toLowerCase(Locale.ROOT);
		return items.combatStyles(id) == 0 && !name.contains("(unf)") && POTION_NAME.matcher(name).find();
	}

	private static boolean isResource(int id, ItemLookup items)
	{
		String name = items.name(id).toLowerCase(Locale.ROOT);
		return items.combatStyles(id) == 0 && !isRune(name) && RESOURCE_NAME.matcher(name).find();
	}
}
