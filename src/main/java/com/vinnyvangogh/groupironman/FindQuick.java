package com.vinnyvangogh.groupironman;

import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The Find tab's quick-find: what the category and sub-type dropdowns offer, how each narrows the items, who
 * the holder dropdown lists, and the one-click presets. Combat and resources reuse {@link ItemCategory}, which
 * reads item stats; food, potions and teleports have no stats to read, so they are named patterns.
 */
final class FindQuick
{
	static final String ANY = "Any";
	static final String ANYWHERE = "Anywhere";
	static final String GROUP_STORAGE = "Group storage";
	/** The name group storage goes by as a search source. */
	static final String STORAGE_OWNER = "Group";

	enum Kind
	{
		ANYTHING("Anything"),
		RUNES("Runes"),
		FOOD("Food"),
		POTIONS("Potions"),
		TELEPORTS("Teleports"),
		RESOURCES("Resources"),
		COMBAT("Combat");

		final String label;

		Kind(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	static final FindFilter RUNES_ANYWHERE = new FindFilter(Kind.RUNES, ANY, null);
	static final FindFilter FOOD = new FindFilter(Kind.FOOD, ANY, null);
	static final FindFilter TELEPORTS = new FindFilter(Kind.TELEPORTS, ANY, null);

	private static final Map<Kind, Map<String, Pattern>> PATTERNS = new EnumMap<>(Kind.class);
	private static final Map<Kind, List<String>> SUBTYPES = new EnumMap<>(Kind.class);

	private static final List<String> RUNE_SUBTYPES = Arrays.asList("Air", "Water", "Earth", "Fire", "Mind", "Body",
		"Cosmic", "Chaos", "Nature", "Law", "Death", "Blood", "Soul", "Astral", "Wrath");

	static
	{
		// Cooked and ready: the raw and burnt versions are resources and rubbish.
		String fish = "shark|lobster|swordfish|monkfish|karambwan(?!\\s*paste)|manta ray|anglerfish|trout|salmon|tuna|"
			+ "bass|sea turtle|dark crab|cod|pike|herring|sardine|shrimps|anchovies|mackerel|bluegill|tench|eel";
		put(Kind.FOOD, "Fish", "(^| )(" + fish + ")$");
		put(Kind.FOOD, "Pies & cakes", "( pie|cake|pizza|bread)( |$)|(^| )(half an? .*pie)");
		put(Kind.FOOD, "Meals", "stew|curry|potato|wine|ugthanki kebab|kebab|jug of wine");
		String dose = "\\([1-8]\\)$";
		put(Kind.POTIONS, "Prayer & restore", "(prayer|restore|sanfew serum)[^(]*" + dose);
		put(Kind.POTIONS, "Stamina", "stamina[^(]*" + dose);
		put(Kind.POTIONS, "Combat boosts", "(attack|strength|defence|combat|ranging|magic|bastion|battlemage|"
			+ "divine|overload|saradomin brew|zamorak brew)[^(]*" + dose);
		put(Kind.POTIONS, "Antidotes", "(antidote|antipoison|antivenom|anti-venom|antifire|extended)[^(]*" + dose);
		put(Kind.TELEPORTS, "Tablets", "teleport|teletab|tablet");
		put(Kind.TELEPORTS, "Jewellery", "(ring of dueling|games necklace|amulet of glory|ring of wealth|skills necklace|"
			+ "combat bracelet|necklace of passage|burning amulet|digsite pendant|ring of the elements|slayer ring|"
			+ "ring of returning|xeric's talisman|ectophial|drakan's medallion)");
		put(Kind.RESOURCES, "Ores & bars", "(^| )(ore|bar|bars|coal)$");
		put(Kind.RESOURCES, "Logs", "(^| )(logs|plank|planks)$");
		put(Kind.RESOURCES, "Herbs", "^(grimy|clean) |(^| )(weed|leaf)$");
		put(Kind.RESOURCES, "Bones & hides", "bones?$|hide$|leather$|dragonhide");
		SUBTYPES.put(Kind.RUNES, withAny(RUNE_SUBTYPES));
		SUBTYPES.put(Kind.COMBAT, Arrays.asList(ANY, "Melee", "Ranged", "Magic"));
		SUBTYPES.put(Kind.ANYTHING, Collections.singletonList(ANY));
		for (Kind k : Kind.values())
		{
			if (!SUBTYPES.containsKey(k))
			{
				List<String> subs = new ArrayList<>();
				subs.add(ANY);
				subs.addAll(PATTERNS.get(k).keySet());
				SUBTYPES.put(k, Collections.unmodifiableList(subs));
			}
		}
	}

	private static final Pattern FOOD_ANY = Pattern.compile("");
	private static final Pattern DOSED = Pattern.compile("\\([1-8]\\)$");
	private static final Pattern RAW_OR_BURNT = Pattern.compile("^(raw|burnt|uncooked) ");

	private FindQuick()
	{
	}

	private static void put(Kind kind, String sub, String regex)
	{
		PATTERNS.computeIfAbsent(kind, k -> new java.util.LinkedHashMap<>()).put(sub, Pattern.compile(regex));
	}

	private static List<String> withAny(List<String> subs)
	{
		List<String> out = new ArrayList<>();
		out.add(ANY);
		out.addAll(subs);
		return Collections.unmodifiableList(out);
	}

	/** The sub-type dropdown for {@code kind}: {@link #ANY} first. */
	static List<String> subtypes(Kind kind)
	{
		return SUBTYPES.get(kind);
	}

	/** Whether the item is of {@code kind} and {@code sub}; {@link #ANY} matches the whole kind. */
	static boolean matches(Kind kind, String sub, int id, ItemLookup items)
	{
		return RULES.get(kind).test(sub, id, items.name(id).toLowerCase(Locale.ROOT), items);
	}

	/** One kind's test: its sub-type, the item, its lower-cased name, and the lookup for stats. */
	private interface Rule
	{
		boolean test(String sub, int id, String name, ItemLookup items);
	}

	private static final Map<Kind, Rule> RULES = new EnumMap<>(Kind.class);

	static
	{
		RULES.put(Kind.ANYTHING, (sub, id, name, items) -> true);
		RULES.put(Kind.RUNES, (sub, id, name, items) -> ItemCategory.isRune(name)
			&& (ANY.equals(sub) || name.startsWith(sub.toLowerCase(Locale.ROOT) + " ")));
		RULES.put(Kind.COMBAT, (sub, id, name, items) -> combat(sub).matches(id, items));
		RULES.put(Kind.RESOURCES, (sub, id, name, items) -> ItemCategory.RESOURCES.matches(id, items)
			&& named(Kind.RESOURCES, sub, name));
		RULES.put(Kind.FOOD, (sub, id, name, items) -> food(name, sub));
		RULES.put(Kind.POTIONS, (sub, id, name, items) -> DOSED.matcher(name).find()
			&& named(Kind.POTIONS, sub, name));
		RULES.put(Kind.TELEPORTS, (sub, id, name, items) -> teleport(name, sub));
	}

	/** {@link #ANY}, or the sub-type's pattern is found in the name. */
	private static boolean named(Kind kind, String sub, String name)
	{
		return ANY.equals(sub) || sub(kind, sub).matcher(name).find();
	}

	private static ItemCategory combat(String sub)
	{
		switch (sub)
		{
			case "Melee":
				return ItemCategory.MELEE;
			case "Ranged":
				return ItemCategory.RANGED;
			case "Magic":
				return ItemCategory.MAGIC;
			default:
				return ItemCategory.COMBAT;
		}
	}

	private static Pattern sub(Kind kind, String sub)
	{
		Pattern p = PATTERNS.get(kind).get(sub);
		return p == null ? FOOD_ANY : p;
	}

	private static boolean food(String name, String sub)
	{
		if (RAW_OR_BURNT.matcher(name).find() || DOSED.matcher(name).find())
		{
			return false;
		}
		if (!ANY.equals(sub))
		{
			return sub(Kind.FOOD, sub).matcher(name).find();
		}
		for (Pattern p : PATTERNS.get(Kind.FOOD).values())
		{
			if (p.matcher(name).find())
			{
				return true;
			}
		}
		return false;
	}

	private static boolean teleport(String name, String sub)
	{
		if (!ANY.equals(sub))
		{
			return sub(Kind.TELEPORTS, sub).matcher(name).find();
		}
		for (Pattern p : PATTERNS.get(Kind.TELEPORTS).values())
		{
			if (p.matcher(name).find())
			{
				return true;
			}
		}
		return false;
	}

	/** The holder dropdown: anywhere, group storage, then each member the group has anything from. */
	static List<String> holders(List<GroupSearch.Source> sources)
	{
		TreeSet<String> members = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (GroupSearch.Source s : sources)
		{
			if (!STORAGE_OWNER.equals(s.member))
			{
				members.add(s.member);
			}
		}
		List<String> out = new ArrayList<>();
		out.add(ANYWHERE);
		out.add(GROUP_STORAGE);
		out.addAll(members);
		return out;
	}
}
