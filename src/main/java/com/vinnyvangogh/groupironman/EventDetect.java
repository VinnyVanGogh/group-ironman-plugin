package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntPredicate;

/**
 * Turns what the game already told this client into events: a group storage
 * visit into its storage events, a drop's untradeables into a unique, a
 * storage minimum into its event. Pure; the client reads live in
 * {@link GroupEventEngine}.
 */
final class EventDetect
{
	/** Untradeables that drop all the time and are not what anyone means by a unique. */
	static final String DEFAULT_UNTRADEABLE_IGNORE = "Clue scroll, Scroll box, Brimstone key, Larran's key,"
		+ " Ancient shard, Dark totem, Mossy key, Giant key, Ecumenical key, Looting bag";

	/** The account type varbit's value for a Hardcore Group Ironman (Varbits.ACCOUNT_TYPE javadoc, runelite-api). */
	static final int HARDCORE_GROUP_IRONMAN = 5;

	private EventDetect()
	{
	}

	/** A member's storage visit as events: deposit, withdrawal, summary and "took the last". Thresholds come later. */
	static List<GroupEvent> storage(StorageChange change, ItemLookup lookup)
	{
		List<GroupEvent> out = new ArrayList<>();
		if (change == null || change.isEmpty())
		{
			return out;
		}
		String by = change.member();
		long at = change.at();
		if (!change.deposited().isEmpty())
		{
			out.add(GroupEvent.of(GroupEvent.Type.STORAGE_DEPOSIT, at, by).items(change.deposited())
				.number(EventText.value(change.deposited(), lookup)).build());
		}
		if (!change.took().isEmpty())
		{
			out.add(GroupEvent.of(GroupEvent.Type.STORAGE_WITHDRAWAL, at, by).items(change.took())
				.number(EventText.value(change.took(), lookup)).build());
		}
		out.add(GroupEvent.of(GroupEvent.Type.STORAGE_SUMMARY, at, by).items(change.deposited())
			.taken(change.took()).build());
		Map<Integer, Integer> last = new TreeMap<>();
		for (Map.Entry<Integer, Integer> e : change.took().entrySet())
		{
			if (change.after().getOrDefault(e.getKey(), 0) <= 0)
			{
				last.put(e.getKey(), e.getValue());
			}
		}
		if (!last.isEmpty())
		{
			out.add(GroupEvent.of(GroupEvent.Type.STORAGE_LAST, at, by).items(last).build());
		}
		return out;
	}

	static GroupEvent minimum(long at, String member, LowStock.Alert alert)
	{
		return GroupEvent.of(GroupEvent.Type.STORAGE_MINIMUM, at, member).text(alert.item).number(alert.have)
			.detail("minimum " + alert.minimum).build();
	}

	/** The untradeable items in a drop, less the ignored names; empty when there are none. */
	static Map<Integer, Integer> uniques(Map<Integer, Integer> drop, IntPredicate tradeable, ItemLookup lookup,
		String ignore)
	{
		List<String> skip = new ArrayList<>();
		for (String s : (ignore == null ? "" : ignore).split(","))
		{
			if (!s.trim().isEmpty())
			{
				skip.add(s.trim().toLowerCase(Locale.ROOT));
			}
		}
		Map<Integer, Integer> out = new TreeMap<>();
		for (Map.Entry<Integer, Integer> e : drop.entrySet())
		{
			if (tradeable.test(e.getKey()))
			{
				continue;
			}
			String name = lookup.name(e.getKey()).toLowerCase(Locale.ROOT);
			if (skip.stream().noneMatch(name::startsWith))
			{
				out.put(e.getKey(), e.getValue());
			}
		}
		return out;
	}
}
