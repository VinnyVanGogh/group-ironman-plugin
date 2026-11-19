package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What each tab of the side panel shows, built from {@link GroupRoster}
 * without any Swing, so every card, grid and line can be unit tested. The
 * panel only turns these into components.
 */
final class PanelModel
{
	/** The skills tab's order, read across its three columns, as {@code Skill.getName()} spells them. */
	static final List<String> SKILL_ORDER = Collections.unmodifiableList(Arrays.asList(
		"Attack", "Hitpoints", "Mining",
		"Strength", "Agility", "Smithing",
		"Defence", "Herblore", "Fishing",
		"Ranged", "Thieving", "Cooking",
		"Prayer", "Crafting", "Firemaking",
		"Magic", "Fletching", "Woodcutting",
		"Runecraft", "Slayer", "Farming",
		"Construction", "Hunter", "Sailing"));

	/** A small bar: HP, Prayer, Run or Spec. */
	static final class Bar
	{
		final String label;
		final int current;
		final int max;
		/** Not shared by the member but assumed (run and spec at 100% from hiscores): drawn dimmed. */
		final boolean assumed;
		/** Where the number came from when it was not shared, or null: "from hiscores". */
		final String note;

		Bar(String label, int current, int max)
		{
			this(label, current, max, false, null);
		}

		Bar(String label, int current, int max, boolean assumed, String note)
		{
			this.label = label;
			this.current = current;
			this.max = max;
			this.assumed = assumed;
			this.note = note;
		}

		String text()
		{
			return current + "/" + max;
		}

		/** "HP 72/99", "HP 99/99 (from hiscores)", "Run 100/100 (assumed, not shared)". */
		String tooltip()
		{
			return label + " " + text() + (note == null ? "" : " (" + note + ")");
		}
	}

	static final class SkillCell
	{
		final String skill;
		final int real;
		final int boosted;

		SkillCell(String skill, int real, int boosted)
		{
			this.skill = skill;
			this.real = real;
			this.boosted = boosted;
		}

		/** "99", or "112" when boosted: the grid shows the boosted level like the game does. */
		String text()
		{
			return String.valueOf(boosted);
		}

		String tooltip()
		{
			return skill + " " + BoostText.level(real, boosted);
		}
	}

	static final class ItemCell
	{
		final int id;
		final String name;
		final long quantity;
		/** Stack value in gp. */
		final long value;

		/** What a log basket, fish barrel or coal bag holds, drawn on its slot; -1 when unknown or not one. */
		final int inside;
		/** What High Alchemy pays for the whole stack in gp, 0 when unknown: the secondary figure in the tooltip. */
		final long highAlch;

		ItemCell(int id, String name, long quantity, long value)
		{
			this(id, name, quantity, value, -1);
		}

		/** The items a container holds, by name, when its sender knows each one; empty otherwise. */
		final List<String> held;
		/** What {@link #held} is worth in gp; 0 when not known or worth nothing. */
		final long heldValue;
		/** Whether {@link #heldValue} belongs in the tooltip (a looting bag's contents are its value). */
		final boolean showHeldValue;

		ItemCell(int id, String name, long quantity, long value, int inside)
		{
			this(id, name, quantity, value, inside, 0);
		}

		ItemCell(int id, String name, long quantity, long value, int inside, long highAlch)
		{
			this(id, name, quantity, value, inside, Collections.emptyList(), 0, false, highAlch);
		}

		ItemCell(int id, String name, long quantity, long value, int inside, List<String> held, long heldValue,
			boolean showHeldValue)
		{
			this(id, name, quantity, value, inside, held, heldValue, showHeldValue, 0);
		}

		ItemCell(int id, String name, long quantity, long value, int inside, List<String> held, long heldValue,
			boolean showHeldValue, long highAlch)
		{
			this.id = id;
			this.name = name;
			this.quantity = quantity;
			this.value = value;
			this.inside = inside;
			this.held = held;
			this.heldValue = heldValue;
			this.showHeldValue = showHeldValue;
			this.highAlch = highAlch;
		}

		/** An empty inventory slot, kept so the items after it stay in their own slots. */
		static final ItemCell EMPTY = new ItemCell(0, "", 0, 0);

		boolean empty()
		{
			return id <= 0;
		}

		/** Draws the same as {@code o}: same item, stack, value and contents. */
		boolean sameAs(ItemCell o)
		{
			return o != null && id == o.id && quantity == o.quantity && value == o.value && inside == o.inside
				&& highAlch == o.highAlch && heldValue == o.heldValue && showHeldValue == o.showHeldValue
				&& name.equals(o.name) && held.equals(o.held);
		}

		static boolean same(List<ItemCell> a, List<ItemCell> b)
		{
			return sameLists(a, b, ItemCell::sameAs);
		}

		/** "450,001 x Coins": the exact amount, which the icon's own stack text shortens; "Log basket (19 inside)". */
		String tooltip()
		{
			String base = quantity > 1 ? Amounts.EXACT.of(quantity) + " x " + name : name;
			if (inside < 0)
			{
				return base;
			}
			String what = held.isEmpty() ? "" : ": " + String.join(", ", held);
			String worth = showHeldValue && heldValue > 0 ? ", worth " + Amounts.EXACT.of(heldValue) + " gp" : "";
			return base + " (" + Amounts.EXACT.of(inside) + " inside" + what + worth + ")";
		}
	}

	/** One titled icon grid inside a member card: "Worn", "Inventory", "Rune pouch", "Quiver". */
	static final class ItemGroup
	{
		final String title;
		final List<ItemCell> items;

		ItemGroup(String title, List<ItemCell> items)
		{
			this.title = title;
			this.items = items;
		}
	}

	static final class MemberCard
	{
		final String name;
		final boolean you;
		final boolean online;
		/** "Online - W302, Lumbridge" or "Offline - last seen W303, Draynor, 2h ago". */
		final String status;
		final List<Bar> bars;
		final List<SkillCell> skills;
		final List<ItemGroup> items;
		/** "Seed vault: 12 kinds, 340 seeds (seen 3d ago)", or null. */
		final String seedVault;
		/**
		 * Where the stats came from when the member shares none: "From hiscores, 2h ago", "Hiscores unavailable",
		 * or null for shared stats or nothing at all.
		 */
		final String source;
		/** "Ring of dueling 6, Games necklace 3": jewellery charges the member shares, or null. */
		final String charges;

		MemberCard(String name, boolean you, boolean online, String status, List<Bar> bars, List<SkillCell> skills,
			List<ItemGroup> items, String seedVault, String source)
		{
			this(name, you, online, status, bars, skills, items, seedVault, source, null);
		}

		MemberCard(String name, boolean you, boolean online, String status, List<Bar> bars, List<SkillCell> skills,
			List<ItemGroup> items, String seedVault, String source, String charges)
		{
			this.charges = charges;
			this.name = name;
			this.you = you;
			this.online = online;
			this.status = status;
			this.bars = bars;
			this.skills = skills;
			this.items = items;
			this.seedVault = seedVault;
			this.source = source;
		}
	}

	static final String FROM_HISCORES = "from hiscores";
	static final String ASSUMED = "assumed, not shared";

	static final class BankCard
	{
		final String member;
		/** "812 items, 45.2M gp, seen 2h ago". */
		final String summary;
		/** {@link #summary} with every digit, for its tooltip: "812 items, 45,201,337 gp, seen 2h ago". */
		final String exact;
		final long value;
		/** The cells drawn: the preview's top stacks, or every match when searching or "Show all". */
		final List<ItemCell> items;
		/** Matching stacks left out of {@link #items} by the preview; 0 when everything matching is drawn. */
		final int hidden;

		BankCard(String member, String summary, String exact, long value, List<ItemCell> items, int hidden)
		{
			this.member = member;
			this.summary = summary;
			this.exact = exact;
			this.value = value;
			this.items = items;
			this.hidden = hidden;
		}

		/** Draws the same card as {@code o}: the Banks tab leaves it alone. */
		boolean sameAs(BankCard o)
		{
			return member.equals(o.member) && summary.equals(o.summary) && exact.equals(o.exact)
				&& hidden == o.hidden && ItemCell.same(items, o.items);
		}

		/** Whether two Banks tabs draw the same; null is "bank sharing is off". */
		static boolean same(List<BankCard> a, List<BankCard> b)
		{
			if (a == null || b == null)
			{
				return a == b;
			}
			return sameLists(a, b, BankCard::sameAs);
		}
	}

	static final class LogRow
	{
		/** "You deposited 5 Trout" or "Alex took 40K Coins". */
		final String text;
		/** {@link #text} with every digit, for its tooltip: "Alex took 40,000 Coins". */
		final String exact;
		final String ago;

		LogRow(String text, String exact, String ago)
		{
			this.text = text;
			this.exact = exact;
			this.ago = ago;
		}

		boolean sameAs(LogRow o)
		{
			return text.equals(o.text) && exact.equals(o.exact) && ago.equals(o.ago);
		}
	}

	static final class StorageView
	{
		/** "Last seen 3m ago, after You saved", or null when storage was never opened. */
		final String snapshot;
		final long value;
		final List<ItemCell> items;
		final List<LogRow> log;

		StorageView(String snapshot, long value, List<ItemCell> items, List<LogRow> log)
		{
			this.snapshot = snapshot;
			this.value = value;
			this.items = items;
			this.log = log;
		}

		/** Draws the same Storage tab as {@code o}; {@code o} may be null (nothing drawn yet). */
		boolean sameAs(StorageView o)
		{
			return o != null && value == o.value && java.util.Objects.equals(snapshot, o.snapshot)
				&& ItemCell.same(items, o.items) && sameLists(log, o.log, LogRow::sameAs);
		}
	}

	/** Same length and {@code same} pair by pair: what decides a panel tab needs no redraw. */
	static <T> boolean sameLists(List<T> a, List<T> b, java.util.function.BiPredicate<T, T> same)
	{
		if (a.size() != b.size())
		{
			return false;
		}
		for (int i = 0; i < a.size(); i++)
		{
			if (!same.test(a.get(i), b.get(i)))
			{
				return false;
			}
		}
		return true;
	}

	static final class FindRow
	{
		final int itemId;
		/** The stack's size, so the icon is the game's own pile for it. */
		final long quantity;
		/** "Coins - 450K in group storage". */
		final String text;
		/** {@link #text} with every digit: "Coins - 450,001 in group storage". */
		final String tooltip;
		/** "2h ago", or "" for live data. */
		final String ago;

		FindRow(int itemId, long quantity, String text, String tooltip, String ago)
		{
			this.itemId = itemId;
			this.quantity = quantity;
			this.text = text;
			this.tooltip = tooltip;
			this.ago = ago;
		}
	}

	private PanelModel()
	{
	}

	/**
	 * One card per member: the in-game roster first, then anyone else this
	 * client holds data for, in {@link #MEMBER_ORDER}. Offline members keep
	 * their last stats and items.
	 */
	static List<MemberCard> members(GroupRoster group, Collection<String> roster, RegionNames regions, ItemLookup items,
		String self, long now)
	{
		return members(group, roster, regions, items, self, GamePresence.UNKNOWN, now);
	}

	/**
	 * As above, with online and world from the game's Group channel ({@link GamePresence}) where it knows them,
	 * so members without the plugin show too; party data still fills everything else.
	 */
	static List<MemberCard> members(GroupRoster group, Collection<String> roster, RegionNames regions, ItemLookup items,
		String self, GamePresence game, long now)
	{
		Map<String, MemberStats> stats = group.allStats();
		Map<String, GroupCodec.Carried> carried = group.allCarried();
		Map<String, MemberExtras> extras = group.extras();
		Map<String, Long> heard = group.lastHeard();

		Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		names.addAll(roster);
		names.addAll(stats.keySet());
		names.addAll(carried.keySet());
		names.addAll(heard.keySet());

		List<MemberCard> out = new ArrayList<>();
		for (String name : names)
		{
			boolean you = name.equalsIgnoreCase(self);
			long at = heard.getOrDefault(name, 0L);
			GamePresence.State g = game.state(name);
			boolean online = g == GamePresence.State.ONLINE || g == GamePresence.State.UNKNOWN && Presence.online(at, now);
			MemberStats s = stats.get(name);
			String st = status(s, at, g, game.world(name), regions, now);
			List<ItemGroup> held = itemGroups(carried.get(name), extras.get(name), items);
			String vault = seedVault(extras.get(name), now);
			GroupCodec.Carried worn = carried.get(name);
			String charges = worn == null || worn.charges.isEmpty() ? null : JewelleryCharges.text(worn.charges);
			out.add(s != null
				? new MemberCard(name, you, online, st, bars(s), skills(s), held, vault, null, charges)
				: fromHiscores(name, you, online, st, held, vault, group.hiscores(), now, charges));
		}
		out.sort(MEMBER_ORDER);
		return out;
	}

	/**
	 * You, then members online, then offline and never-seen ones, each by name (Board 2026-11-13). Online is the
	 * card's own: the game's Group channel where it knows, plugin presence otherwise.
	 */
	static final Comparator<MemberCard> MEMBER_ORDER = Comparator.comparing((MemberCard c) -> !c.you)
		.thenComparing(c -> !c.online)
		.thenComparing(c -> c.name, String.CASE_INSENSITIVE_ORDER);

	/**
	 * A member who shares no stats: their levels from the hiscores, when looked up. Online, HP and Prayer show
	 * full at their hiscore level and Run and Spec at an assumed 100%, dimmed (Board 2026-11-11). Offline, levels
	 * only. Items are whatever they shared before, or none: nothing is made up.
	 */
	private static MemberCard fromHiscores(String name, boolean you, boolean online, String status,
		List<ItemGroup> held, String vault, HiscoreBook hiscores, long now, String charges)
	{
		HiscoreBook.Entry h = hiscores.entry(name);
		List<Bar> bars = h != null && online ? hiscoreBars(h) : new ArrayList<>();
		List<SkillCell> skills = new ArrayList<>();
		if (h != null)
		{
			Map<String, MemberStats.Level> levels = new TreeMap<>();
			h.levels.forEach((skill, level) -> levels.put(skill, new MemberStats.Level(level, level)));
			skills = skills(new MemberStats(levels, 0, 0, 0, 0));
		}
		return new MemberCard(name, you, online, status, bars, skills, held, vault,
			hiscoreSource(h, hiscores.unavailable(name), now), charges);
	}

	static List<Bar> hiscoreBars(HiscoreBook.Entry h)
	{
		List<Bar> out = new ArrayList<>();
		Integer hp = h.levels.get("Hitpoints");
		Integer prayer = h.levels.get("Prayer");
		if (hp != null)
		{
			out.add(new Bar("HP", hp, hp, false, FROM_HISCORES));
		}
		if (prayer != null)
		{
			out.add(new Bar("Prayer", prayer, prayer, false, FROM_HISCORES));
		}
		out.add(new Bar("Run", 100, 100, true, ASSUMED));
		out.add(new Bar("Spec", 100, 100, true, ASSUMED));
		return out;
	}

	/** "From hiscores, 2h ago", with " (hiscores unavailable)" after a failed refresh; null when neither. */
	static String hiscoreSource(HiscoreBook.Entry h, boolean unavailable, long now)
	{
		if (h == null)
		{
			return unavailable ? "Hiscores unavailable" : null;
		}
		return "From hiscores, " + BoostText.ago(now, h.at) + (unavailable ? " (hiscores unavailable)" : "");
	}

	/**
	 * The card's line with the game's word on it: online on the game's world when only the game says so, offline
	 * when the game says so, and " (no plugin)" for a member whose client never reported.
	 */
	private static String status(MemberStats s, long heardAt, GamePresence.State game, int gameWorld,
		RegionNames regions, long now)
	{
		String tag = heardAt > 0 || s != null ? "" : " (no plugin)";
		boolean heard = Presence.online(heardAt, now);
		if (game == GamePresence.State.ONLINE && !heard && heardAt > 0)
		{
			// In the game but not sending: their events stay on their client until they sync (Board 2026-11-17).
			tag = " (not syncing, last synced " + BoostText.ago(now, heardAt) + ")";
		}
		if (game == GamePresence.State.ONLINE && !heard)
		{
			return (gameWorld > 0 ? "Online - W" + gameWorld : "Online") + tag;
		}
		if (game == GamePresence.State.OFFLINE)
		{
			return heardAt > 0 ? status(s, heardAt, false, regions, now) : "Offline" + tag;
		}
		return status(s, heardAt, heard, regions, now);
	}

	static String status(MemberStats s, long heardAt, boolean online, RegionNames regions, long now)
	{
		String where = where(s, regions);
		if (online)
		{
			return where.isEmpty() ? "Online" : "Online - " + where;
		}
		if (heardAt <= 0)
		{
			return "Not seen yet";
		}
		return "Offline - last seen " + (where.isEmpty() ? "" : where + ", ") + BoostText.ago(now, heardAt);
	}

	/** "W302, Lumbridge", "W302", or "". */
	private static String where(MemberStats s, RegionNames regions)
	{
		if (s == null)
		{
			return "";
		}
		List<String> parts = new ArrayList<>();
		if (s.world() > 0)
		{
			parts.add("W" + s.world());
		}
		String place = regions.describe(s.region());
		if (place != null)
		{
			parts.add(place);
		}
		return String.join(", ", parts);
	}

	static List<Bar> bars(MemberStats s)
	{
		List<Bar> out = new ArrayList<>();
		if (s == null)
		{
			return out;
		}
		MemberStats.Level hp = s.skills().get("Hitpoints");
		MemberStats.Level prayer = s.skills().get("Prayer");
		if (hp != null)
		{
			out.add(new Bar("HP", hp.boosted, hp.real));
		}
		if (prayer != null)
		{
			out.add(new Bar("Prayer", prayer.boosted, prayer.real));
		}
		out.add(new Bar("Run", s.runEnergy(), 100));
		out.add(new Bar("Spec", s.specialAttack(), 100));
		return out;
	}

	/** In the skills tab's order; skills this build does not know go last. */
	static List<SkillCell> skills(MemberStats s)
	{
		List<SkillCell> out = new ArrayList<>();
		if (s == null)
		{
			return out;
		}
		Map<String, MemberStats.Level> left = new TreeMap<>(s.skills());
		for (String name : SKILL_ORDER)
		{
			MemberStats.Level l = left.remove(name);
			if (l != null)
			{
				out.add(new SkillCell(name, l.real, l.boosted));
			}
		}
		for (Map.Entry<String, MemberStats.Level> e : left.entrySet())
		{
			out.add(new SkillCell(e.getKey(), e.getValue().real, e.getValue().boosted));
		}
		return out;
	}

	private static List<ItemGroup> itemGroups(GroupCodec.Carried c, MemberExtras x, ItemLookup items)
	{
		List<ItemGroup> out = new ArrayList<>();
		if (c != null)
		{
			addGroup(out, "Worn", c.worn, items);
			if (c.slots.isEmpty() || c.inventory.isEmpty())
			{
				// A sender from before slots: one cell per item, as before.
				addGroup(out, "Inventory", c.inventory, items);
			}
			else
			{
				out.add(new ItemGroup("Inventory", slotCells(c, items)));
			}
		}
		if (x != null)
		{
			addGroup(out, "Rune pouch", x.runePouch, items);
			addGroup(out, "Quiver", x.quiver, items);
		}
		return out;
	}

	private static void addGroup(List<ItemGroup> out, String title, Map<Integer, Integer> counts, ItemLookup items)
	{
		if (!counts.isEmpty())
		{
			out.add(new ItemGroup(title, cells(counts, items, GridFilter.NONE, false)));
		}
	}

	/** One cell per slot in the game's order, {@link ItemCell#EMPTY} for an empty one; noted ids stay noted. */
	/** A known container's contents go on its slot (Board 2026-11-14); unknown draws no number. */
	static List<ItemCell> slotCells(GroupCodec.Carried c, ItemLookup items)
	{
		List<ItemCell> out = new ArrayList<>();
		for (GroupCodec.Slot s : c.slots)
		{
			if (s.empty())
			{
				out.add(ItemCell.EMPTY);
				continue;
			}
			String name = items.name(s.id);
			SkillingContainer kind = SkillingContainer.fromItemName(name);
			GroupCodec.Held h = kind == null ? null : c.contents.get(kind);
			if (h == null)
			{
				out.add(new ItemCell(s.id, name, s.quantity, items.price(s.id) * s.quantity, -1));
				continue;
			}
			List<String> held = new ArrayList<>();
			long worth = 0;
			for (Map.Entry<Integer, Integer> e : h.items.entrySet())
			{
				held.add(Amounts.EXACT.of(e.getValue()) + " x " + items.name(e.getKey()));
				worth += items.price(e.getKey()) * e.getValue();
			}
			out.add(new ItemCell(s.id, name, s.quantity, items.price(s.id) * s.quantity, h.count, held, worth,
				kind == SkillingContainer.LOOTING_BAG));
		}
		return out;
	}

	private static String seedVault(MemberExtras x, long now)
	{
		if (x == null || x.seedVaultAt < 0)
		{
			return null;
		}
		long seeds = 0;
		for (int n : x.seedVault.values())
		{
			seeds += n;
		}
		return "Seed vault: " + x.seedVault.size() + " kinds, " + Amounts.GAME.of(seeds) + " seeds (seen "
			+ BoostText.ago(now, x.seedVaultAt) + ")";
	}

	/**
	 * Icon cells for {@code counts}, only the ones whose name matches
	 * {@code filter} when it is a query, in container order or, with
	 * {@code byValue}, most valuable stack first.
	 */
	static List<ItemCell> cells(Map<Integer, Integer> counts, ItemLookup items, String filter, boolean byValue)
	{
		return cells(counts, items, GridFilter.of(filter), byValue);
	}

	/** {@link #cells(Map, ItemLookup, String, boolean)} for the search box and the category pill of {@code filter}. */
	static List<ItemCell> cells(Map<Integer, Integer> counts, ItemLookup items, GridFilter filter, boolean byValue)
	{
		ItemQuery q = ItemQuery.parse(filter.query);
		List<ItemCell> out = new ArrayList<>();
		for (Map.Entry<Integer, Integer> e : counts.entrySet())
		{
			int id = e.getKey();
			String name = items.name(id);
			if ((q == null || q.matches(name)) && filter.category.matches(id, items))
			{
				out.add(new ItemCell(id, name, e.getValue(), items.price(id) * e.getValue(), -1,
					items.highAlch(id) * e.getValue()));
			}
		}
		if (byValue)
		{
			out.sort(Comparator.comparingLong((ItemCell c) -> -c.value).thenComparing(c -> c.name));
		}
		return out;
	}

	private static long total(Map<Integer, Integer> counts, ItemLookup items)
	{
		long value = 0;
		for (Map.Entry<Integer, Integer> e : counts.entrySet())
		{
			value += items.price(e.getKey()) * e.getValue();
		}
		return value;
	}

	/** Stacks a bank card draws before a search, a category's "Show all" or the search box asks for more. */
	static final int BANK_PREVIEW = 20;

	static List<BankCard> banks(GroupRoster group, ItemLookup items, String filter, long now)
	{
		return banks(group, items, GridFilter.of(filter), now);
	}

	/**
	 * One card per member's last bank, sorted by name, each grid most valuable first (Grand Exchange price times
	 * quantity). Without a search or "Show all" a card draws only its {@link #BANK_PREVIEW} top stacks of the chosen
	 * category and counts the rest in {@link BankCard#hidden}; the summary always describes the whole bank.
	 */
	static List<BankCard> banks(GroupRoster group, ItemLookup items, GridFilter filter, long now)
	{
		List<BankCard> out = new ArrayList<>();
		for (Map.Entry<String, BankSnapshot> e : group.banks().entrySet())
		{
			Map<Integer, Integer> bank = e.getValue().items();
			long count = 0;
			for (int n : bank.values())
			{
				count += n;
			}
			long value = total(bank, items);
			String seen = " gp, seen " + BoostText.ago(now, e.getValue().takenAt());
			String summary = Amounts.GAME.of(count) + " items, " + Amounts.GAME.of(value) + seen;
			String exact = Amounts.EXACT.of(count) + " items, " + Amounts.EXACT.of(value) + seen;
			List<ItemCell> matching = cells(bank, items, filter, true);
			int shown = filter.previewing() ? Math.min(BANK_PREVIEW, matching.size()) : matching.size();
			out.add(new BankCard(e.getKey(), summary, exact, value, new ArrayList<>(matching.subList(0, shown)),
				matching.size() - shown));
		}
		return out;
	}

	/** The last storage contents as seen, with when and by whom, and the change log as lines. */
	static StorageView storage(GroupRoster group, ItemLookup items, String self, GridFilter filter, long now)
	{
		List<LogRow> log = new ArrayList<>();
		for (StorageChange entry : group.log().entries())
		{
			StorageChange c = filtered(entry, items, filter);
			if (c == null)
			{
				continue;
			}
			log.add(new LogRow(change(c, items, self), change(c, Amounts.EXACT.words(items), self),
				BoostText.ago(now, c.at())));
		}
		long at = group.storageAt();
		if (at < 0)
		{
			return new StorageView(null, 0, Collections.emptyList(), log);
		}
		String by = group.storageBy();
		String snapshot = "Last seen " + BoostText.ago(now, at)
			+ (by.isEmpty() ? "" : ", by " + (by.equalsIgnoreCase(self) ? "you" : by));
		Map<Integer, Integer> contents = group.storageItems();
		return new StorageView(snapshot, total(contents, items), cells(contents, items, filter, true), log);
	}

	/**
	 * {@code c} cut down to the items the search box and category pill keep; null when none of the visit's items
	 * match, so the change log narrows with the grid. An unfiltered view returns {@code c} itself.
	 */
	static StorageChange filtered(StorageChange c, ItemLookup items, GridFilter filter)
	{
		if (!filter.searching() && filter.category == ItemCategory.ALL)
		{
			return c;
		}
		ItemQuery q = ItemQuery.parse(filter.query);
		Map<Integer, Integer> took = keep(c.took(), items, q, filter.category);
		Map<Integer, Integer> deposited = keep(c.deposited(), items, q, filter.category);
		if (took.isEmpty() && deposited.isEmpty())
		{
			return null;
		}
		return new StorageChange(c.at(), c.member(), took, deposited, c.after(), c.shared());
	}

	private static Map<Integer, Integer> keep(Map<Integer, Integer> counts, ItemLookup items, ItemQuery q,
		ItemCategory category)
	{
		Map<Integer, Integer> out = new java.util.LinkedHashMap<>();
		for (Map.Entry<Integer, Integer> e : counts.entrySet())
		{
			if ((q == null || q.matches(items.name(e.getKey()))) && category.matches(e.getKey(), items))
			{
				out.put(e.getKey(), e.getValue());
			}
		}
		return out;
	}

	/**
	 * "You deposited 5 Trout", "Alex took 2 Shark, deposited 100 Coal", and "You took 4 Prayer potion(4) (not
	 * shared: Share group storage changes is off)" for an own visit kept to this client.
	 */
	static String change(StorageChange c, ItemLookup items, String self)
	{
		List<String> parts = new ArrayList<>();
		if (!c.took().isEmpty())
		{
			parts.add("took " + counts(c.took(), items));
		}
		if (!c.deposited().isEmpty())
		{
			parts.add("deposited " + counts(c.deposited(), items));
		}
		String who = c.member().equalsIgnoreCase(self) ? "You" : c.member();
		return who + " " + String.join(", ", parts) + (c.shared() ? "" : " (" + StorageChange.NOT_SHARED + ")");
	}

	/** "5 Trout, 40K Coins", amounts in {@code items}' style. */
	static String counts(Map<Integer, Integer> counts, ItemLookup items)
	{
		List<String> parts = new ArrayList<>();
		for (Map.Entry<Integer, Integer> e : counts.entrySet())
		{
			parts.add(items.amount(e.getValue()) + " " + items.name(e.getKey()));
		}
		return String.join(", ", parts);
	}

	/** "Grimy guam leaf 15 - GroupShip bank", one row per item and place. */
	static List<FindRow> find(String query, GroupRoster group, ItemLookup items, long now)
	{
		return find(query, group, items, now, "");
	}

	/** Rows read from your side: "Trout - 15 in your bank", "Trout - 140 in group storage". */
	static List<FindRow> find(String query, GroupRoster group, ItemLookup items, long now, String self)
	{
		return find(query, group, items, now, self, FindFilter.NONE);
	}

	/** {@link #find(String, GroupRoster, ItemLookup, long, String)} narrowed by the quick-find dropdowns. */
	static List<FindRow> find(String query, GroupRoster group, ItemLookup items, long now, String self,
		FindFilter filter)
	{
		FindStyle words = new FindStyle(self, null, null, null, null, null, null);
		List<FindRow> out = new ArrayList<>();
		for (GroupSearch.Hit h : GroupSearch.hits(query, group.searchSources(), items, filter))
		{
			out.add(new FindRow(h.itemId, h.quantity, h.name + " - " + words.place(h, Amounts.GAME),
				h.name + " - " + words.place(h, Amounts.EXACT), h.seenAt < 0 ? "" : BoostText.ago(now, h.seenAt)));
		}
		return out;
	}
}
