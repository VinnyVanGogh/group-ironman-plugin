package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * "Bring this": a small table of gear worth carrying to a boss, a raid lobby or the Revenant Caves, checked against
 * what this player carries now, and for each gap who in the group holds it. Pure logic: the panel draws it, the
 * plugin supplies the player's region and the group's items.
 *
 * <p>Items are matched by name, never by id, so a dose or charge variant ("Super combat potion(3)") still counts and
 * the table needs no item ids to go stale. The table is a starting set of well-known gear, not a guide: the group
 * owns what each rule asks for.
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
		/** Not carried at all. */
		MISSING
	}

	/** One thing to bring: any one of {@link #names} satisfies it, the first name stands for it when requested. */
	static final class Req
	{
		final String label;
		final List<String> names;
		final Need need;

		Req(String label, Need need, String... names)
		{
			this.label = label;
			this.need = need;
			this.names = Collections.unmodifiableList(Arrays.asList(names));
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

		Rule(String id, String title, int[] regions, Req... reqs)
		{
			this.id = id;
			this.title = title;
			this.regions = regions;
			this.reqs = Collections.unmodifiableList(Arrays.asList(reqs));
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
		/** Members (never this player) holding a match, with where: "Alex (bank)"; empty when nobody does. */
		final List<String> holders;
		/** The name from {@link Req#names} a teammate holds, to request or list; the first name when nobody does. */
		final String itemName;

		Line(Req req, State state, List<String> holders, String itemName)
		{
			this.req = req;
			this.state = state;
			this.holders = holders;
			this.itemName = itemName;
		}

		/** A teammate has it and this player does not: the panel offers "Request from group". */
		boolean requestable()
		{
			return state == State.MISSING && !holders.isEmpty();
		}

		/** Nobody has it: the panel offers to put it on a shopping list. */
		boolean nobody()
		{
			return state == State.MISSING && holders.isEmpty();
		}
	}

	private static final int[] NONE = {};

	/** Revenant Caves: 12701-12703 and 12957-12959 are the cave regions Plugin Hub revenant plugins use (unverified in game). */
	private static final int[] REV_CAVES = {12701, 12702, 12703, 12957, 12958, 12959};

	/** The starting table, in the order the selector lists it. */
	static final List<Rule> RULES = Collections.unmodifiableList(Arrays.asList(
		new Rule("revs", "Revenant Caves", REV_CAVES,
			new Req("Salve amulet", Need.WORN, "Salve amulet(ei)", "Salve amulet(e)", "Salve amulet(i)", "Salve amulet"),
			new Req("Ranged or magic weapon", Need.WORN, "Craw's bow", "Webweaver bow", "Thammaron's sceptre",
				"Accursed sceptre", "Toxic blowpipe", "Twisted bow"),
			new Req("Prayer restore", Need.CARRIED, "Prayer potion", "Super restore"),
			new Req("Food", Need.CARRIED, "Shark", "Manta ray", "Anglerfish", "Dark crab"),
			new Req("Teleport out", Need.CARRIED, "Ring of dueling", "Amulet of glory", "Ring of wealth")),
		new Rule("vorkath", "Vorkath", new int[] {9023},
			new Req("Antifire", Need.CARRIED, "Extended super antifire", "Super antifire potion", "Extended antifire",
				"Antifire potion", "Anti-dragon shield", "Dragonfire shield", "Ancient wyvern shield"),
			new Req("Antivenom", Need.CARRIED, "Anti-venom+", "Anti-venom", "Antidote++"),
			new Req("Ranged weapon", Need.WORN, "Twisted bow", "Toxic blowpipe", "Dragon crossbow", "Armadyl crossbow",
				"Zaryte crossbow"),
			new Req("Ranging potion", Need.CARRIED, "Ranging potion", "Divine ranging potion", "Bastion potion"),
			new Req("Food", Need.CARRIED, "Shark", "Manta ray", "Anglerfish", "Dark crab")),
		new Rule("zulrah", "Zulrah", new int[] {9007},
			new Req("Antivenom", Need.CARRIED, "Anti-venom+", "Anti-venom", "Antidote++"),
			new Req("Magic weapon", Need.WORN, "Trident of the swamp", "Sanguinesti staff", "Trident of the seas",
				"Tumeken's shadow"),
			new Req("Ranged weapon", Need.CARRIED, "Toxic blowpipe", "Twisted bow", "Armadyl crossbow"),
			new Req("Prayer restore", Need.CARRIED, "Prayer potion", "Super restore"),
			new Req("Food", Need.CARRIED, "Shark", "Manta ray", "Anglerfish", "Dark crab")),
		new Rule("barrows", "Barrows", new int[] {14131, 14231},
			new Req("Spade", Need.CARRIED, "Spade"),
			new Req("Prayer restore", Need.CARRIED, "Prayer potion", "Super restore"),
			new Req("Melee weapon", Need.WORN, "Abyssal whip", "Abyssal tentacle", "Dragon scimitar",
				"Ghrazi rapier", "Scythe of vitur"),
			new Req("Food", Need.CARRIED, "Shark", "Manta ray", "Anglerfish", "Dark crab")),
		new Rule("cox", "Chambers of Xeric", new int[] {4662, 4663, 4918, 4919, 12889},
			new Req("Super combat", Need.CARRIED, "Super combat potion", "Divine super combat potion"),
			new Req("Prayer restore", Need.CARRIED, "Prayer potion", "Super restore"),
			new Req("Rope", Need.CARRIED, "Rope"),
			new Req("Pickaxe", Need.CARRIED, "Dragon pickaxe", "Rune pickaxe", "Crystal pickaxe", "Infernal pickaxe"),
			new Req("Food", Need.CARRIED, "Shark", "Manta ray", "Anglerfish", "Karambwan")),
		new Rule("tob", "Theatre of Blood", new int[] {14642},
			new Req("Super combat", Need.CARRIED, "Super combat potion", "Divine super combat potion"),
			new Req("Prayer restore", Need.CARRIED, "Prayer potion", "Super restore"),
			new Req("Food", Need.CARRIED, "Shark", "Manta ray", "Anglerfish", "Karambwan"),
			new Req("Rune pouch", Need.CARRIED, "Rune pouch", "Divine rune pouch")),
		new Rule("toa", "Tombs of Amascut", new int[] {13454, 14160, 14162, 14164},
			new Req("Prayer restore", Need.CARRIED, "Prayer potion", "Super restore"),
			new Req("Super combat", Need.CARRIED, "Super combat potion", "Divine super combat potion"),
			new Req("Food", Need.CARRIED, "Shark", "Manta ray", "Anglerfish", "Karambwan"),
			new Req("Stamina", Need.CARRIED, "Stamina potion", "Energy potion")),
		new Rule("slayer-fire", "Slayer: dragons and fire-breathers", NONE,
			new Req("Antifire", Need.CARRIED, "Extended super antifire", "Super antifire potion", "Extended antifire",
				"Antifire potion", "Anti-dragon shield", "Dragonfire shield"),
			new Req("Slayer helmet or switch", Need.WORN, "Slayer helmet (i)", "Slayer helmet", "Black mask (i)",
				"Black mask")),
		new Rule("slayer-gargoyle", "Slayer: gargoyles", NONE,
			new Req("Rock hammer", Need.CARRIED, "Rock hammer", "Granite hammer")),
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
		for (Rule r : RULES)
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

	private static boolean any(Map<Integer, Integer> items, Req req, ItemLookup lookup)
	{
		for (Map.Entry<Integer, Integer> e : items.entrySet())
		{
			if (e.getValue() <= 0)
			{
				continue;
			}
			String name = lookup.name(e.getKey());
			for (String wanted : req.names)
			{
				if (matches(name, wanted))
				{
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Every line of {@code rule} for this player: worn and inventory as the roster last had them.
	 * {@code sources} is {@link GroupRoster#searchSources()}; a teammate's inventory, equipment, bank, containers
	 * and the group storage count as "has it", and the player's own sources never do.
	 */
	static List<Line> check(Rule rule, Map<Integer, Integer> inventory, Map<Integer, Integer> worn,
		List<GroupSearch.Source> sources, String self, ItemLookup lookup)
	{
		List<Line> out = new ArrayList<>();
		for (Req req : rule.reqs)
		{
			boolean wearing = any(worn, req, lookup);
			boolean carrying = any(inventory, req, lookup);
			State state;
			if (wearing || carrying && req.need == Need.CARRIED)
			{
				state = State.OK;
			}
			else if (carrying)
			{
				state = State.EQUIP;
			}
			else
			{
				state = State.MISSING;
			}
			List<String> holders = state == State.MISSING ? holders(req, sources, self, lookup)
				: Collections.<String>emptyList();
			out.add(new Line(req, state, holders, holders.isEmpty() ? req.names.get(0)
				: heldName(req, sources, self, lookup)));
		}
		return out;
	}

	/** The first of the requirement's names that a teammate holds. */
	private static String heldName(Req req, List<GroupSearch.Source> sources, String self, ItemLookup lookup)
	{
		for (String wanted : req.names)
		{
			Req one = new Req(req.label, req.need, wanted);
			for (GroupSearch.Source s : sources)
			{
				if (!s.member.equalsIgnoreCase(self) && any(s.items, one, lookup))
				{
					return wanted;
				}
			}
		}
		return req.names.get(0);
	}

	private static List<String> holders(Req req, List<GroupSearch.Source> sources, String self, ItemLookup lookup)
	{
		Map<String, String> byMember = new LinkedHashMap<>();
		for (GroupSearch.Source s : sources)
		{
			if (s.member.equalsIgnoreCase(self) || !any(s.items, req, lookup))
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

	/** Lines the player still has to act on. */
	static int problems(List<Line> lines)
	{
		int n = 0;
		for (Line l : lines)
		{
			if (l.state != State.OK)
			{
				n++;
			}
		}
		return n;
	}
}
