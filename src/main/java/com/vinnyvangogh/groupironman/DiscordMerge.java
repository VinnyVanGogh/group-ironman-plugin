package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One moment, one Discord post. Kinds that describe the same moment (a kill's
 * drop, unique, collection log slot and pet; a tick's level up, 99, total and
 * combat level; a death and what it cost; one storage visit) are one
 * {@link Family}: {@link DiscordBatch} holds them for a moment and this turns
 * them into one {@link Post}, titled after the highest-ranked kind, with the
 * rest as extra lines. Pure, so every merge is tested without a client.
 *
 * <p>Discord only: the overlay and chat still get every event on its own.
 */
final class DiscordMerge
{
	/** Kinds that can share a moment. Each family lists its kinds best first: the first one present titles the post. */
	enum Family
	{
		DROP(1_800, GroupEvent.Type.PET, GroupEvent.Type.CLOG, GroupEvent.Type.UNIQUE, GroupEvent.Type.DROP),
		LEVEL(1_200, GroupEvent.Type.MAXED, GroupEvent.Type.TOTAL_LEVEL, GroupEvent.Type.COMBAT_LEVEL,
			GroupEvent.Type.LEVEL_UP, GroupEvent.Type.XP_MILESTONE),
		DEATH(1_200, GroupEvent.Type.DEATH, GroupEvent.Type.HC_LIFE_LOST, GroupEvent.Type.DEATH_VALUE),
		// The minimum and the visit reach the engine in one client-thread call: the next tick has them all.
		STORAGE(0, GroupEvent.Type.STORAGE_MINIMUM, GroupEvent.Type.STORAGE_LAST, GroupEvent.Type.STORAGE_DEPOSIT,
			GroupEvent.Type.STORAGE_WITHDRAWAL, GroupEvent.Type.STORAGE_SUMMARY);

		/** How long, in ms, the first event of a moment waits for the rest. */
		final long wait;
		final List<GroupEvent.Type> ranked;

		Family(long wait, GroupEvent.Type... ranked)
		{
			this.wait = wait;
			this.ranked = Collections.unmodifiableList(java.util.Arrays.asList(ranked));
		}
	}

	/** A death's value is counted {@link EventWatchers#DEATH_SETTLE_TICKS} ticks after it; wait past that. */
	static final long DEATH_VALUE_WAIT = 12_000;
	static final String BIG_VISIT = "Big storage visit";
	/** Discord refuses a field value over 1,024 characters. */
	static final int MAX_FIELD = 1_024;
	/** Discord allows 25 fields; Value, World and Where come first. */
	static final int MAX_EXTRAS = 20;

	private static final Map<GroupEvent.Type, Family> FAMILY = new EnumMap<>(GroupEvent.Type.class);
	private static final Map<GroupEvent.Type, Integer> RANK = new EnumMap<>(GroupEvent.Type.class);

	static
	{
		for (Family f : Family.values())
		{
			for (int i = 0; i < f.ranked.size(); i++)
			{
				FAMILY.put(f.ranked.get(i), f);
				RANK.put(f.ranked.get(i), i);
			}
		}
	}

	private DiscordMerge()
	{
	}

	/** The family a kind belongs to; null for a kind that is always its own post. */
	static Family family(GroupEvent.Type type)
	{
		return FAMILY.get(type);
	}

	/** How long a moment of this family stays open. A death waits for its value only when that is posted. */
	static long window(Family family, EventSettings settings)
	{
		if (family == Family.DEATH && settings.discord.contains(GroupEvent.Type.DEATH_VALUE))
		{
			return DEATH_VALUE_WAIT;
		}
		return family == null ? 0 : family.wait;
	}

	/** The last kind a moment can have: once it is in, nothing else is coming. */
	static boolean closes(GroupEvent.Type type)
	{
		return type == GroupEvent.Type.DEATH_VALUE;
	}

	/** What one Discord post says. A single event's post reads exactly as it did before merging existed. */
	static final class Post
	{
		/** Whose and when, the colour and the webhook: the highest-ranked event. */
		final GroupEvent lead;
		final String title;
		final String description;
		/** For the Value field and the thumbnail. */
		final Map<Integer, Integer> items;
		/** The other facts of the moment, each a field: {title, line}. */
		final List<String[]> extras;
		/** Worth the configured mention. */
		final boolean big;

		Post(GroupEvent lead, String title, String description, Map<Integer, Integer> items, List<String[]> extras,
			boolean big)
		{
			this.lead = lead;
			this.title = title;
			this.description = description;
			this.items = items;
			this.extras = extras;
			this.big = big;
		}
	}

	static Post single(GroupEvent e, ItemLookup lookup)
	{
		return new Post(e, EventText.title(e.type), EventText.describe(e, lookup), e.items,
			Collections.emptyList(), e.isBig());
	}

	/** One post for a moment's events; null when none of them is worth posting on its own. */
	static Post merge(List<GroupEvent> events, EventSettings settings, ItemLookup lookup)
	{
		Post visit = visit(events, settings, lookup);
		if (visit != null)
		{
			return visit;
		}
		List<GroupEvent> kept = new ArrayList<>();
		for (GroupEvent e : events)
		{
			// A visit's summary rides along for the big post's item list; on its own it needs its switch.
			if (e.type != GroupEvent.Type.STORAGE_SUMMARY || settings.discord.contains(e.type))
			{
				kept.add(e);
			}
		}
		if (kept.size() > 1)
		{
			// A death that cost 0 gp adds no line to its death's post; alone, DiscordWebhook decides.
			kept.removeIf(EasterEggs::nothingLost);
		}
		if (kept.isEmpty())
		{
			return null;
		}
		kept.sort(Comparator.comparingInt(e -> RANK.getOrDefault(e.type, 0)));
		GroupEvent lead = kept.get(0);
		List<GroupEvent> rest = kept.subList(1, kept.size());
		return new Post(lead, EventText.title(lead.type), EventText.describe(lead, lookup), richest(kept, lookup),
			extras(rest, lookup), anyBig(kept));
	}

	/**
	 * A storage visit as one post: every item moved, the big stacks marked, the visit's total value, and the
	 * minimum and last-one lines. Null when the visit had no big move and the summary is off.
	 */
	private static Post visit(List<GroupEvent> events, EventSettings settings, ItemLookup lookup)
	{
		GroupEvent summary = find(events, GroupEvent.Type.STORAGE_SUMMARY);
		boolean deposit = find(events, GroupEvent.Type.STORAGE_DEPOSIT) != null;
		boolean withdrawal = find(events, GroupEvent.Type.STORAGE_WITHDRAWAL) != null;
		if (summary == null || !deposit && !withdrawal && !settings.discord.contains(GroupEvent.Type.STORAGE_SUMMARY))
		{
			return null;
		}
		StringBuilder text = new StringBuilder("group storage:");
		moved(text, "took", summary.taken, settings.storageMin, lookup);
		moved(text, "deposited", summary.items, settings.storageMin, lookup);
		Map<Integer, Integer> all = new TreeMap<>(summary.taken);
		summary.items.forEach((id, n) -> all.merge(id, n, Integer::sum));
		return new Post(summary, visitTitle(deposit, withdrawal), text.toString(), all,
			extras(folded(events), lookup), anyBig(events));
	}

	private static String visitTitle(boolean deposit, boolean withdrawal)
	{
		if (deposit && withdrawal)
		{
			return BIG_VISIT;
		}
		if (withdrawal)
		{
			return EventText.title(GroupEvent.Type.STORAGE_WITHDRAWAL);
		}
		return EventText.title(deposit ? GroupEvent.Type.STORAGE_DEPOSIT : GroupEvent.Type.STORAGE_SUMMARY);
	}

	/** A visit's minimum and last-one events, best first: lines in its post. */
	private static List<GroupEvent> folded(List<GroupEvent> events)
	{
		List<GroupEvent> rest = new ArrayList<>();
		for (GroupEvent e : events)
		{
			if (e.type == GroupEvent.Type.STORAGE_MINIMUM || e.type == GroupEvent.Type.STORAGE_LAST)
			{
				rest.add(e);
			}
		}
		rest.sort(Comparator.comparingInt(e -> RANK.get(e.type)));
		return rest;
	}

	/** " took 40000 x Coins (big, 40.0K gp), 5 x Lobster", after a "; " when something came before. */
	private static void moved(StringBuilder text, String verb, Map<Integer, Integer> items, long min,
		ItemLookup lookup)
	{
		if (items.isEmpty())
		{
			return;
		}
		text.append(text.charAt(text.length() - 1) == ':' ? " " : "; ").append(verb).append(' ');
		List<String> parts = new ArrayList<>();
		for (Map.Entry<Integer, Integer> e : items.entrySet())
		{
			long value = lookup.price(e.getKey()) * e.getValue();
			String part = e.getValue() + " x " + lookup.name(e.getKey());
			parts.add(value >= min && value > 0 ? part + " (big, " + BoostText.gp(value) + " gp)" : part);
		}
		text.append(String.join(", ", parts));
	}

	private static GroupEvent find(List<GroupEvent> events, GroupEvent.Type type)
	{
		for (GroupEvent e : events)
		{
			if (e.type == type)
			{
				return e;
			}
		}
		return null;
	}

	private static List<String[]> extras(List<GroupEvent> events, ItemLookup lookup)
	{
		List<String[]> out = new ArrayList<>();
		for (GroupEvent e : events)
		{
			if (out.size() >= MAX_EXTRAS)
			{
				break;
			}
			String line = EventText.describe(e, lookup);
			out.add(new String[]{EventText.title(e.type), line.length() > MAX_FIELD ? line.substring(0, MAX_FIELD) : line});
		}
		return out;
	}

	/** The items of the event worth the most: the drop over its untradeable subset, the death's losses. */
	private static Map<Integer, Integer> richest(List<GroupEvent> events, ItemLookup lookup)
	{
		Map<Integer, Integer> best = Collections.emptyMap();
		long bestValue = -1;
		for (GroupEvent e : events)
		{
			long v = EventText.value(e.items, lookup);
			if (!e.items.isEmpty() && v > bestValue)
			{
				best = e.items;
				bestValue = v;
			}
		}
		return best;
	}

	private static boolean anyBig(List<GroupEvent> events)
	{
		for (GroupEvent e : events)
		{
			if (e.isBig())
			{
				return true;
			}
		}
		return false;
	}
}
