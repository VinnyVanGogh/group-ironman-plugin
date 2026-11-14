package com.vinnyvangogh.groupironman;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The optional funny line under a Discord post. Pure and table-driven: {@link #RULES} is checked in order and the
 * first rule with lines wins; the words live in {@link EasterEggLines}. The line is extra, never a replacement:
 * the post keeps every fact it had.
 *
 * <p>The phrasing is picked by {@link #pick}, a fixed hash of a seed taken from the event, so the same event always
 * gets the same line on every client and a test with a fixed seed is stable.
 */
final class EasterEggs
{
	/** One rule: the lines it offers for this event, or null when it does not apply. */
	interface Rule
	{
		String[] lines(GroupEvent e, ItemLookup lookup, Settings settings);
	}

	/** The Discord section's Easter egg settings. A threshold of 0 turns that egg off. */
	static final class Settings
	{
		final boolean on;
		final long legendAt;
		final long freeloaderAt;

		Settings(boolean on, long legendAt, long freeloaderAt)
		{
			this.on = on;
			this.legendAt = legendAt;
			this.freeloaderAt = freeloaderAt;
		}

		/** The group's Easter egg settings when it has them, else this member's own. */
		static Settings of(GroupSettingsSchema.Resolved values)
		{
			return new Settings(values.on("discordEasterEggs"), values.value("discordLegendAt"),
				values.value("discordFreeloaderAt"));
		}
	}

	/** Lost alongside a spade, these still make it a clue hunter's death. Matched on the item name's start. */
	private static final List<String> CLUE_ITEMS = Arrays.asList("clue ", "scroll box", "challenge scroll",
		"casket");
	/** Event types whose items are a drop the item table reads by id. */
	private static final Set<GroupEvent.Type> DROPS = Collections.unmodifiableSet(EnumSet.of(GroupEvent.Type.DROP,
		GroupEvent.Type.UNIQUE, GroupEvent.Type.CLUE, GroupEvent.Type.BARROWS));
	/** Event types that carry the item only as its name. */
	private static final Set<GroupEvent.Type> NAMED = Collections.unmodifiableSet(EnumSet.of(
		GroupEvent.Type.RAID_UNIQUE, GroupEvent.Type.CLOG));

	/**
	 * Checked in order; the first that applies wins. Deaths are first so a spade outranks a free trip. The memes
	 * each key on one event type, so their order only matters where noted: the total-level "maxed" post comes
	 * before the one-skill 99, and the gold ring comes after Legend so a big deposit stays legendary.
	 */
	private static final List<Rule> RULES = Collections.unmodifiableList(Arrays.<Rule>asList(
		(e, l, s) -> clueDeath(e, l) ? EasterEggLines.CLUE_HUNTERS : null,
		(e, l, s) -> nothingLost(e) ? EasterEggLines.FREE_TRIP : null,
		(e, l, s) -> e.type == GroupEvent.Type.PET ? EasterEggLines.PET : null,
		at(GroupEvent.Type.LEVEL_UP, 73, EasterEggLines.LEVEL_73),
		at(GroupEvent.Type.LEVEL_UP, 92, EasterEggLines.LEVEL_92),
		named(GroupEvent.Type.MAXED, "total", EasterEggLines.MAXED_TOTAL),
		(e, l, s) -> e.type == GroupEvent.Type.MAXED ? EasterEggLines.SKILL_99 : null,
		at(GroupEvent.Type.COMBAT_LEVEL, 126, EasterEggLines.COMBAT_126),
		at(GroupEvent.Type.TOTAL_LEVEL, 2000, EasterEggLines.TOTAL_2000),
		at(GroupEvent.Type.XP_MILESTONE, 200_000_000L, EasterEggLines.XP_200M),
		named(GroupEvent.Type.QUEST, "Cook's Assistant", EasterEggLines.COOKS_ASSISTANT),
		named(GroupEvent.Type.QUEST, "Dragon Slayer I", EasterEggLines.DRAGON_SLAYER),
		named(GroupEvent.Type.QUEST, "Recipe for Disaster", EasterEggLines.RECIPE_FOR_DISASTER),
		named(GroupEvent.Type.DEATH, "TzTok-Jad", EasterEggLines.JAD),
		(e, l, s) -> e.type == GroupEvent.Type.DRY_STREAK ? EasterEggLines.SIT : null,
		(e, l, s) -> bonesOnly(e) ? EasterEggLines.BONES_ONLY : null,
		EasterEggs::itemRow,
		(e, l, s) -> freeloader(e, l, s) ? EasterEggLines.FREELOADER : null,
		(e, l, s) -> legend(e, l, s) ? EasterEggLines.LEGEND : null,
		(e, l, s) -> deposited(e).containsKey(EasterEggLines.GOLD_RING) ? EasterEggLines.BUYING_GF : null));

	private EasterEggs()
	{
	}

	/** This type of event at exactly this number: a level, a total, an XP milestone. */
	private static Rule at(GroupEvent.Type type, long number, String[] lines)
	{
		return (e, l, s) -> e.type == type && e.number == number ? lines : null;
	}

	/** This type of event with this text, any case: a quest name, a killer, "total". */
	private static Rule named(GroupEvent.Type type, String text, String[] lines)
	{
		return (e, l, s) -> e.type == type && text.equalsIgnoreCase(e.text) ? lines : null;
	}

	/** The egg for one Discord post's events (a merged moment): the first event with one wins. */
	static String line(List<GroupEvent> events, ItemLookup lookup, Settings settings)
	{
		for (GroupEvent e : events)
		{
			String line = line(e, lookup, settings);
			if (line != null)
			{
				return line;
			}
		}
		return null;
	}

	/** The egg for this event, or null: the toggle is off or no rule applies. */
	static String line(GroupEvent e, ItemLookup lookup, Settings settings)
	{
		return line(e, lookup, settings, seed(e));
	}

	static String line(GroupEvent e, ItemLookup lookup, Settings settings, long seed)
	{
		if (!settings.on)
		{
			return null;
		}
		for (Rule rule : RULES)
		{
			String[] lines = rule.lines(e, lookup, settings);
			if (lines != null && lines.length > 0)
			{
				return lines[pick(seed, lines.length)];
			}
		}
		return null;
	}

	/**
	 * Which of {@code n} phrasings a seed picks. MurmurHash3's 64-bit finalizer, so nearby seeds spread out;
	 * {@code new Random(seed).nextInt(n)} does not: its first value barely moves between close seeds.
	 */
	static int pick(long seed, int n)
	{
		long z = seed;
		z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
		z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
		z ^= z >>> 33;
		return (int) Math.floorMod(z, (long) n);
	}

	/** The same on every client that sees the event: the sender's time, the member and the kind. */
	static long seed(GroupEvent e)
	{
		return 31L * (31L * e.at + e.member.toLowerCase(Locale.ROOT).hashCode()) + e.type.code.hashCode();
	}

	/** A death that cost 0 gp: its "lost about 0 gp" line is never posted. */
	static boolean nothingLost(GroupEvent e)
	{
		return e.type == GroupEvent.Type.DEATH_VALUE && e.number <= 0;
	}

	private static boolean clueDeath(GroupEvent e, ItemLookup lookup)
	{
		if (e.type != GroupEvent.Type.DEATH_VALUE || !e.items.containsKey(EasterEggLines.SPADE))
		{
			return false;
		}
		for (int id : e.items.keySet())
		{
			if (id != EasterEggLines.SPADE && !clueItem(lookup.name(id)))
			{
				return false;
			}
		}
		return true;
	}

	private static boolean clueItem(String name)
	{
		String n = name.toLowerCase(Locale.ROOT);
		return CLUE_ITEMS.stream().anyMatch(n::startsWith);
	}

	private static boolean bonesOnly(GroupEvent e)
	{
		return e.type == GroupEvent.Type.DROP && e.items.size() == 1 && e.items.containsKey(EasterEggLines.BONES);
	}

	private static String[] itemRow(GroupEvent e, ItemLookup lookup, Settings settings)
	{
		for (Map.Entry<Integer, String[]> row : EasterEggLines.ITEMS.entrySet())
		{
			int id = row.getKey();
			if ((DROPS.contains(e.type) && e.items.containsKey(id))
				|| (NAMED.contains(e.type) && lookup.name(id).equalsIgnoreCase(e.text)))
			{
				return row.getValue();
			}
		}
		return null;
	}

	/**
	 * Took at least the 'Freeloader at' value, and at least as much as was given back on the same visit. At, not
	 * just over, like {@link #legend}: both settings read "at".
	 */
	private static boolean freeloader(GroupEvent e, ItemLookup lookup, Settings settings)
	{
		long took = EventText.value(taken(e), lookup);
		return settings.freeloaderAt > 0 && took >= settings.freeloaderAt
			&& took >= EventText.value(deposited(e), lookup);
	}

	private static boolean legend(GroupEvent e, ItemLookup lookup, Settings settings)
	{
		return settings.legendAt > 0 && EventText.value(deposited(e), lookup) >= settings.legendAt;
	}

	private static Map<Integer, Integer> deposited(GroupEvent e)
	{
		boolean gave = e.type == GroupEvent.Type.STORAGE_DEPOSIT || e.type == GroupEvent.Type.STORAGE_SUMMARY;
		return gave ? e.items : Collections.emptyMap();
	}

	private static Map<Integer, Integer> taken(GroupEvent e)
	{
		switch (e.type)
		{
			case STORAGE_WITHDRAWAL:
				return e.items;
			case STORAGE_SUMMARY:
				return e.taken;
			default:
				return Collections.emptyMap();
		}
	}
}
