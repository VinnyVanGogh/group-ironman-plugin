package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.runelite.api.EquipmentInventorySlot;

/**
 * "Bring this": a small table of gear worth carrying to a boss, a raid lobby or the Revenant Caves, checked against
 * what this player carries now, and for each gap who in the group holds it. Pure logic: the panel draws it, the
 * plugin supplies the player's region and the group's items. {@link GearBook} holds the player's own edits.
 *
 * <p>Items are matched by name, so a dose or charge variant ("Super combat potion(3)") still counts, or by
 * {@link Kind}: "any ranged weapon" reads the item's equipment slot and {@link ItemStyles} bits, so a weapon the
 * table never names still satisfies it. The table is a starting set of well-known gear, not a guide: the group
 * owns what each rule asks for, and every line can be edited.
 */
final class GearChecks
{
	/** Where an item must be for its line to be satisfied. */
	enum Need
	{
		/** Worn: in the inventory it reads "Equip". */
		WORN,
		/** Anywhere on the player: worn or in the inventory. */
		CARRIED
	}

	/** How a line stands for the player. */
	enum State
	{
		OK,
		/** In the inventory, but the rule wants it worn. */
		EQUIP,
		/** Some carried, fewer than the line asks for. */
		SHORT,
		/** Not carried at all. */
		MISSING
	}

	/** "Any one of" a kind of item, worked out from the item's stats rather than its name. */
	enum Kind
	{
		NONE("Named items only"),
		ANY_WEAPON("Any weapon"),
		MELEE_WEAPON("Any melee weapon"),
		RANGED_WEAPON("Any ranged weapon"),
		MAGIC_WEAPON("Any magic weapon"),
		/** Ahrim at Barrows: anything that is not a magic weapon. */
		NON_MAGIC_WEAPON("Any melee or ranged weapon");

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

		/** Whether an item worn in {@code slot} with {@link ItemStyles} bits {@code styles} is this kind. */
		boolean accepts(int slot, int styles)
		{
			if (this == NONE || slot != WEAPON_SLOT)
			{
				return false;
			}
			boolean melee = (styles & ItemStyles.MELEE) != 0;
			boolean ranged = (styles & ItemStyles.RANGED) != 0;
			boolean magic = (styles & ItemStyles.MAGIC) != 0;
			switch (this)
			{
				case ANY_WEAPON:
					return melee || ranged || magic;
				case MELEE_WEAPON:
					return melee && !ranged && !magic;
				case RANGED_WEAPON:
					return ranged;
				case MAGIC_WEAPON:
					return magic;
				case NON_MAGIC_WEAPON:
					return (melee || ranged) && !magic;
				default:
					return false;
			}
		}
	}

	static final int WEAPON_SLOT = EquipmentInventorySlot.WEAPON.getSlotIdx();
	static final int MAX_COUNT = 1000;

	/**
	 * One thing to bring: any one of {@link #names}, or any item of {@link #kind}, satisfies it; the first name
	 * stands for it when requested or listed.
	 */
	static final class Req
	{
		final String label;
		final List<String> names;
		final Need need;
		/** How many to carry (doses and charges count as one item each); worn lines need one. */
		final int count;
		final Kind kind;
		/** Shown, but never counted as a problem nor put on a shopping list. */
		final boolean optional;
		/** The sub-fight this line is for ("Ahrim"), empty for the whole trip; skipping the fight drops it. */
		final String fight;

		Req(String label, Need need, String... names)
		{
			this(label, Arrays.asList(names), need, 1, Kind.NONE, false, "");
		}

		Req(String label, List<String> names, Need need, int count, Kind kind, boolean optional, String fight)
		{
			this.label = label;
			this.names = Collections.unmodifiableList(new ArrayList<>(names));
			this.need = need == null ? Need.CARRIED : need;
			this.count = Math.max(1, Math.min(MAX_COUNT, count));
			this.kind = kind == null ? Kind.NONE : kind;
			this.optional = optional;
			this.fight = fight == null ? "" : fight.trim();
		}

		Req count(int n)
		{
			return new Req(label, names, need, n, kind, optional, fight);
		}

		Req kind(Kind k)
		{
			return new Req(label, names, need, count, k, optional, fight);
		}

		Req optional(boolean on)
		{
			return new Req(label, names, need, count, kind, on, fight);
		}

		Req need(Need n)
		{
			return new Req(label, names, n, count, kind, optional, fight);
		}

		Req fight(String f)
		{
			return new Req(label, names, need, count, kind, optional, f);
		}

		/** What the tooltip lists: the names, or the kind when there are none. */
		String describe()
		{
			StringBuilder sb = new StringBuilder();
			if (kind != Kind.NONE)
			{
				sb.append(kind.label);
				if (!names.isEmpty())
				{
					sb.append(", e.g. ");
				}
			}
			else if (!names.isEmpty())
			{
				sb.append("Any of: ");
			}
			sb.append(String.join(", ", names));
			if (count > 1)
			{
				sb.append(" (").append(count).append(')');
			}
			if (need == Need.WORN)
			{
				sb.append(" (worn)");
			}
			if (optional)
			{
				sb.append(" (optional)");
			}
			if (!fight.isEmpty())
			{
				sb.append(" (for ").append(fight).append(')');
			}
			return sb.toString();
		}
	}

	/** One place or activity and what to bring there. */
	static final class Rule
	{
		final String id;
		final String title;
		/** Regions that trigger it on entry; empty for a rule picked by hand only. */
		final int[] regions;
		final List<Req> reqs;
		/** A player's own place, not one of {@link #RULES}. */
		final boolean custom;

		Rule(String id, String title, int[] regions, Req... reqs)
		{
			this(id, title, regions, Arrays.asList(reqs), false);
		}

		Rule(String id, String title, int[] regions, List<Req> reqs, boolean custom)
		{
			this.id = id;
			this.title = title;
			this.regions = regions == null ? NONE : regions.clone();
			this.reqs = Collections.unmodifiableList(new ArrayList<>(reqs));
			this.custom = custom;
		}

		Rule withReqs(List<Req> next)
		{
			return new Rule(id, title, regions, next, custom);
		}

		Rule withTitle(String next)
		{
			return new Rule(id, next, regions, reqs, custom);
		}

		Rule withRegions(int[] next)
		{
			return new Rule(id, title, next, reqs, custom);
		}

		/** The sub-fights its lines name, in table order: the panel's skip toggles. */
		List<String> fights()
		{
			Set<String> out = new LinkedHashSet<>();
			for (Req r : reqs)
			{
				if (!r.fight.isEmpty())
				{
					out.add(r.fight);
				}
			}
			return new ArrayList<>(out);
		}

		@Override
		public String toString()
		{
			return title;
		}
	}

	/** One checked line. */
	static final class Line
	{
		final Req req;
		final State state;
		/** How many matching items the player carries (worn ones included). */
		final int have;
		/** Members (never this player) holding a match, with where: "Alex (bank)"; empty when nobody does. */
		final List<String> holders;
		/**
		 * The item to request or list: a name from {@link Req#names} a teammate holds, a teammate's item of the
		 * line's kind, or the first name; empty for a kind-only line nobody holds.
		 */
		final String itemName;
		/** An item id to draw for the line (the player's own match, else a teammate's), or -1. */
		final int itemId;

		Line(Req req, State state, int have, List<String> holders, String itemName, int itemId)
		{
			this.req = req;
			this.state = state;
			this.have = have;
			this.holders = holders;
			this.itemName = itemName;
			this.itemId = itemId;
		}

		boolean lacking()
		{
			return state == State.MISSING || state == State.SHORT;
		}

		/** How many more to get. */
		int short_()
		{
			return Math.max(0, req.count - have);
		}

		/** A teammate has it and this player is short: the panel offers "Request from group". */
		boolean requestable()
		{
			return lacking() && !holders.isEmpty();
		}

		/** Nobody has it: the panel offers to put it on a shopping list. */
		boolean nobody()
		{
			return lacking() && holders.isEmpty();
		}
	}

	/** One item of a "Make shopping list" plan. */
	static final class Want
	{
		final String name;
		final int quantity;

		Want(String name, int quantity)
		{
			this.name = name;
			this.quantity = quantity;
		}
	}

	/** What "Make shopping list" sends: requests to the group first, then what nobody holds. No name twice. */
	static final class Plan
	{
		final List<Want> request;
		final List<Want> shop;
		/** Missing lines with nothing nameable to list (a kind nobody holds): the player picks an item first. */
		final List<String> unnamed;

		Plan(List<Want> request, List<Want> shop, List<String> unnamed)
		{
			this.request = request;
			this.shop = shop;
			this.unnamed = unnamed;
		}

		boolean empty()
		{
			return request.isEmpty() && shop.isEmpty();
		}
	}

	private static final int[] NONE = {};

	/** Revenant Caves: 12701-12703 and 12957-12959 are the cave regions Plugin Hub revenant plugins use (unverified in game). */
	private static final int[] REV_CAVES = {12701, 12702, 12703, 12957, 12958, 12959};

	private static final List<String> FOOD = Arrays.asList("Shark", "Manta ray", "Anglerfish", "Dark crab", "Karambwan");
	private static final List<String> PRAYER = Arrays.asList("Prayer potion", "Super restore");
	private static final List<String> SUPER_COMBAT = Arrays.asList("Super combat potion", "Divine super combat potion");
	private static final List<String> ANTIVENOM = Arrays.asList("Anti-venom+", "Anti-venom", "Antidote++");
	private static final List<String> ANTIFIRE_POTION = Arrays.asList("Extended super antifire",
		"Super antifire potion", "Extended antifire", "Antifire potion");
	private static final List<String> DRAGONFIRE_SHIELD = Arrays.asList("Dragonfire shield", "Anti-dragon shield",
		"Ancient wyvern shield", "Dragonfire ward");
	private static final List<String> ANTIFIRE = concat(ANTIFIRE_POTION, DRAGONFIRE_SHIELD);

	private static List<String> concat(List<String> a, List<String> b)
	{
		List<String> out = new ArrayList<>(a);
		out.addAll(b);
		return Collections.unmodifiableList(out);
	}

	private static Req req(String label, Need need, int count, List<String> names)
	{
		return new Req(label, names, need, count, Kind.NONE, false, "");
	}

	private static Req kind(String label, Need need, Kind kind, String... examples)
	{
		return new Req(label, Arrays.asList(examples), need, 1, kind, false, "");
	}

	/**
	 * The starting table, in the order the selector lists it. Reviewed against the wiki on 2026-11-24:
	 * <ul>
	 * <li>Barrows, https://oldschool.runescape.wiki/w/Barrows/Strategies : a spade digs into the mounds; the
	 * crypts drain Prayer; any style works on the brothers, but "Ahrim is weakest to ranged and will be harder to
	 * hit without Air spells", so his line asks for a melee or ranged weapon and can be skipped; the Barrows
	 * teleport tablet lands next to the entrance (optional).</li>
	 * <li>Vorkath, https://oldschool.runescape.wiki/w/Vorkath/Strategies : "Full damage protection requires both a
	 * super antifire potion and a dragonfire protection shield", and anti-venom; ranged (crossbows, blowpipe) is the
	 * usual style, a lance the melee one (edit the line to use it).</li>
	 * <li>Zulrah, https://oldschool.runescape.wiki/w/Zulrah/Strategies : anti-venom, ranged and magic, 1-3 prayer
	 * potions or super restores, a ring of recoil or suffering for the snakelings (optional).</li>
	 * <li>Revenant Caves, https://oldschool.runescape.wiki/w/Revenant_Caves : the bracelet of ethereum cuts their
	 * damage by 75% (optional). The page does not say a Salve amulet works there, so that line is optional
	 * (unverified).</li>
	 * <li>Chambers of Xeric, https://oldschool.runescape.wiki/w/Chambers_of_Xeric : potions are made inside from
	 * gourds and a pickaxe spawns in the Guardian room, so both are optional.</li>
	 * <li>Theatre of Blood and Tombs of Amascut, https://oldschool.runescape.wiki/w/Theatre_of_Blood/Strategies and
	 * https://oldschool.runescape.wiki/w/Tombs_of_Amascut/Strategies : setups carry super restores and super combat;
	 * ToA hands out supplies inside, so stamina stays optional.</li>
	 * <li>Slayer: https://oldschool.runescape.wiki/w/Gargoyle (a rock hammer, rock thrownhammer or granite hammer),
	 * https://oldschool.runescape.wiki/w/Banshee (earmuffs or a slayer helmet, worn),
	 * https://oldschool.runescape.wiki/w/Dragonfire (an anti-dragon shield or antifire).</li>
	 * </ul>
	 */
	static final List<Rule> RULES = Collections.unmodifiableList(Arrays.asList(
		new Rule("revs", "Revenant Caves", REV_CAVES,
			new Req("Salve amulet", Need.WORN, "Salve amulet(ei)", "Salve amulet(e)", "Salve amulet(i)", "Salve amulet")
				.optional(true),
			new Req("Bracelet of ethereum", Need.WORN, "Bracelet of ethereum").optional(true),
			new Req("Ranged or magic weapon", Need.WORN, "Craw's bow", "Webweaver bow", "Thammaron's sceptre",
				"Accursed sceptre", "Toxic blowpipe", "Twisted bow"),
			req("Prayer restore", Need.CARRIED, 2, PRAYER),
			req("Food", Need.CARRIED, 6, FOOD),
			new Req("Teleport out", Need.CARRIED, "Ring of dueling", "Amulet of glory", "Ring of wealth")),
		new Rule("vorkath", "Vorkath", new int[] {9023},
			req("Antifire potion", Need.CARRIED, 1, ANTIFIRE_POTION),
			req("Dragonfire shield", Need.WORN, 1, DRAGONFIRE_SHIELD),
			req("Antivenom", Need.CARRIED, 1, ANTIVENOM),
			kind("Ranged weapon", Need.WORN, Kind.RANGED_WEAPON, "Dragon hunter crossbow", "Armadyl crossbow",
				"Rune crossbow", "Toxic blowpipe", "Twisted bow"),
			new Req("Ranging potion", Need.CARRIED, "Ranging potion", "Divine ranging potion", "Bastion potion"),
			req("Food", Need.CARRIED, 8, FOOD)),
		new Rule("zulrah", "Zulrah", new int[] {9007},
			req("Antivenom", Need.CARRIED, 1, ANTIVENOM),
			kind("Magic weapon", Need.WORN, Kind.MAGIC_WEAPON, "Trident of the swamp", "Sanguinesti staff",
				"Trident of the seas", "Tumeken's shadow"),
			kind("Ranged weapon", Need.CARRIED, Kind.RANGED_WEAPON, "Toxic blowpipe", "Twisted bow",
				"Armadyl crossbow", "Magic shortbow"),
			req("Prayer restore", Need.CARRIED, 2, PRAYER),
			req("Food", Need.CARRIED, 8, FOOD),
			new Req("Recoil ring", Need.WORN, "Ring of suffering (ri)", "Ring of suffering (r)", "Ring of recoil")
				.optional(true)),
		new Rule("barrows", "Barrows", new int[] {14131, 14231},
			new Req("Spade", Need.CARRIED, "Spade"),
			kind("Weapon (any style)", Need.WORN, Kind.ANY_WEAPON, "Abyssal whip", "Toxic blowpipe",
				"Trident of the seas", "Rune crossbow"),
			kind("Not magic, for Ahrim", Need.CARRIED, Kind.NON_MAGIC_WEAPON, "Rune crossbow", "Toxic blowpipe",
				"Abyssal whip").fight("Ahrim"),
			req("Prayer restore", Need.CARRIED, 4, PRAYER),
			req("Food", Need.CARRIED, 6, FOOD),
			new Req("Barrows teleport", Need.CARRIED, "Barrows teleport").optional(true)),
		new Rule("cox", "Chambers of Xeric", new int[] {4662, 4663, 4918, 4919, 12889},
			req("Super combat", Need.CARRIED, 1, SUPER_COMBAT).optional(true),
			req("Prayer restore", Need.CARRIED, 2, PRAYER).optional(true),
			new Req("Pickaxe", Need.CARRIED, "Dragon pickaxe", "Rune pickaxe", "Crystal pickaxe", "Infernal pickaxe")
				.optional(true),
			req("Food", Need.CARRIED, 4, FOOD)),
		new Rule("tob", "Theatre of Blood", new int[] {14642},
			req("Super combat", Need.CARRIED, 1, SUPER_COMBAT),
			req("Prayer restore", Need.CARRIED, 2, PRAYER),
			req("Food", Need.CARRIED, 4, FOOD),
			new Req("Rune pouch", Need.CARRIED, "Rune pouch", "Divine rune pouch")),
		new Rule("toa", "Tombs of Amascut", new int[] {13454, 14160, 14162, 14164},
			req("Prayer restore", Need.CARRIED, 2, PRAYER),
			req("Super combat", Need.CARRIED, 1, SUPER_COMBAT),
			req("Food", Need.CARRIED, 4, FOOD),
			new Req("Stamina", Need.CARRIED, "Stamina potion", "Energy potion").optional(true)),
		new Rule("slayer-fire", "Slayer: dragons and fire-breathers", NONE,
			req("Antifire", Need.CARRIED, 1, ANTIFIRE),
			new Req("Slayer helmet or switch", Need.WORN, "Slayer helmet (i)", "Slayer helmet", "Black mask (i)",
				"Black mask")),
		new Rule("slayer-gargoyle", "Slayer: gargoyles", NONE,
			new Req("Rock hammer", Need.CARRIED, "Rock hammer", "Granite hammer", "Rock thrownhammer")),
		new Rule("slayer-banshee", "Slayer: banshees and cave horrors", NONE,
			new Req("Earmuffs or helmet", Need.WORN, "Earmuffs", "Slayer helmet (i)", "Slayer helmet",
				"Witchwood icon"))));

	private GearChecks()
	{
	}

	static Rule byId(String id)
	{
		for (Rule r : RULES)
		{
			if (r.id.equals(id))
			{
				return r;
			}
		}
		return null;
	}

	/** The rule whose region is {@code region}, or null. */
	static Rule forRegion(int region)
	{
		return forRegion(RULES, region);
	}

	static Rule forRegion(Collection<Rule> rules, int region)
	{
		for (Rule r : rules)
		{
			for (int id : r.regions)
			{
				if (id == region)
				{
					return r;
				}
			}
		}
		return null;
	}

	/** Whether {@code itemName} is {@code wanted}: the same name, or it with a dose or charge count after it. */
	static boolean matches(String itemName, String wanted)
	{
		String n = itemName.toLowerCase(Locale.ROOT).trim();
		String w = wanted.toLowerCase(Locale.ROOT).trim();
		return n.equals(w) || n.startsWith(w + "(") && !w.endsWith(")");
	}

	/** Whether item {@code id} satisfies {@code req}: one of its names, or its kind. */
	static boolean satisfies(int id, Req req, ItemLookup lookup)
	{
		String name = lookup.name(id);
		for (String wanted : req.names)
		{
			if (matches(name, wanted))
			{
				return true;
			}
		}
		return req.kind.accepts(lookup.slot(id), lookup.combatStyles(id));
	}

	/** How many items in {@code items} satisfy {@code req}; the first match's id goes in {@code first[0]}. */
	private static int count(Map<Integer, Integer> items, Req req, ItemLookup lookup, int[] first)
	{
		int n = 0;
		for (Map.Entry<Integer, Integer> e : items.entrySet())
		{
			if (e.getValue() <= 0 || !satisfies(e.getKey(), req, lookup))
			{
				continue;
			}
			if (first[0] < 0)
			{
				first[0] = e.getKey();
			}
			n = (int) Math.min(MAX_COUNT, (long) n + e.getValue());
		}
		return n;
	}

	static List<Line> check(Rule rule, Map<Integer, Integer> inventory, Map<Integer, Integer> worn,
		List<GroupSearch.Source> sources, String self, ItemLookup lookup)
	{
		return check(rule, Collections.emptySet(), inventory, worn, sources, self, lookup);
	}

	/**
	 * Every line of {@code rule} for this player, less those of a sub-fight in {@code skipped}: worn and inventory
	 * as the roster last had them. {@code sources} is {@link GroupRoster#searchSources()}; a teammate's inventory,
	 * equipment, bank, containers and the group storage count as "has it", and the player's own sources never do.
	 */
	static List<Line> check(Rule rule, Set<String> skipped, Map<Integer, Integer> inventory,
		Map<Integer, Integer> worn, List<GroupSearch.Source> sources, String self, ItemLookup lookup)
	{
		List<Line> out = new ArrayList<>();
		for (Req req : rule.reqs)
		{
			if (!req.fight.isEmpty() && containsIgnoreCase(skipped, req.fight))
			{
				continue;
			}
			int[] mine = {-1};
			int wearing = count(worn, req, lookup, mine);
			int carrying = count(inventory, req, lookup, mine);
			State state;
			int have;
			if (req.need == Need.WORN)
			{
				have = wearing + carrying;
				state = wearing > 0 ? State.OK : carrying > 0 ? State.EQUIP : State.MISSING;
			}
			else
			{
				have = Math.min(MAX_COUNT, wearing + carrying);
				state = have >= req.count ? State.OK : have > 0 ? State.SHORT : State.MISSING;
			}
			boolean lacking = state == State.MISSING || state == State.SHORT;
			List<String> holders = lacking ? holders(req, sources, self, lookup) : Collections.<String>emptyList();
			String itemName = req.names.isEmpty() ? "" : req.names.get(0);
			int itemId = mine[0];
			if (!holders.isEmpty())
			{
				int[] held = {-1};
				itemName = heldName(req, sources, self, lookup, held);
				if (itemId < 0)
				{
					itemId = held[0];
				}
			}
			out.add(new Line(req, state, have, holders, itemName, itemId));
		}
		return out;
	}

	private static boolean containsIgnoreCase(Set<String> set, String s)
	{
		for (String x : set)
		{
			if (x.equalsIgnoreCase(s))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The first of the requirement's names that a teammate holds, else the name of a teammate's item of its kind;
	 * the id goes in {@code id[0]}.
	 */
	private static String heldName(Req req, List<GroupSearch.Source> sources, String self, ItemLookup lookup,
		int[] id)
	{
		for (String wanted : req.names)
		{
			Req one = new Req(req.label, Collections.singletonList(wanted), req.need, 1, Kind.NONE, false, "");
			for (GroupSearch.Source s : sources)
			{
				if (!s.member.equalsIgnoreCase(self) && count(s.items, one, lookup, id) > 0)
				{
					return wanted;
				}
			}
		}
		for (GroupSearch.Source s : sources)
		{
			if (!s.member.equalsIgnoreCase(self) && count(s.items, req, lookup, id) > 0)
			{
				return lookup.name(id[0]);
			}
		}
		return req.names.isEmpty() ? "" : req.names.get(0);
	}

	private static List<String> holders(Req req, List<GroupSearch.Source> sources, String self, ItemLookup lookup)
	{
		Map<String, String> byMember = new LinkedHashMap<>();
		for (GroupSearch.Source s : sources)
		{
			if (s.member.equalsIgnoreCase(self) || count(s.items, req, lookup, new int[] {-1}) == 0)
			{
				continue;
			}
			byMember.merge(s.member, s.where, (a, b) -> a.equals(b) || a.contains(b) ? a : a + ", " + b);
		}
		TreeSet<String> sorted = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (Map.Entry<String, String> e : byMember.entrySet())
		{
			String where = e.getValue();
			sorted.add(e.getKey() + " (" + where + ")");
		}
		return new ArrayList<>(sorted);
	}

	/** Lines the player still has to act on; optional lines never count. */
	static int problems(List<Line> lines)
	{
		int n = 0;
		for (Line l : lines)
		{
			if (l.state != State.OK && !l.req.optional)
			{
				n++;
			}
		}
		return n;
	}

	/**
	 * "Make shopping list": every missing or short line that is not optional, those a teammate holds as a request
	 * to the group, the rest for this player's own list. An item named by two lines appears once, as a request if
	 * either line is requestable, at the larger of the two quantities.
	 */
	static Plan plan(List<Line> lines)
	{
		Map<String, Want> request = new LinkedHashMap<>();
		Map<String, Want> shop = new LinkedHashMap<>();
		List<String> unnamed = new ArrayList<>();
		for (Line l : lines)
		{
			if (!l.lacking() || l.req.optional)
			{
				continue;
			}
			if (l.itemName.isEmpty())
			{
				unnamed.add(l.req.label);
				continue;
			}
			String key = l.itemName.toLowerCase(Locale.ROOT);
			int qty = Math.max(1, l.short_());
			if (l.requestable())
			{
				Want had = shop.remove(key);
				Want want = had == null ? new Want(l.itemName, qty) : new Want(had.name, Math.max(qty, had.quantity));
				request.merge(key, want, GearChecks::larger);
			}
			else if (request.containsKey(key))
			{
				request.merge(key, new Want(l.itemName, qty), GearChecks::larger);
			}
			else
			{
				shop.merge(key, new Want(l.itemName, qty), GearChecks::larger);
			}
		}
		return new Plan(new ArrayList<>(request.values()), new ArrayList<>(shop.values()), unnamed);
	}

	/** The first line's spelling of the name, at the larger quantity. */
	private static Want larger(Want first, Want next)
	{
		return new Want(first.name, Math.max(first.quantity, next.quantity));
	}
}
