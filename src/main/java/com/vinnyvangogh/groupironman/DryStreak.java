package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Kills of a boss since its last unique, and the call-out each time the
 * streak reaches another multiple of the drop rate. Pure; saved per account.
 *
 * <p>Rates are the combined chance of any listed unique, read from the OSRS
 * wiki on 2026-11-09:
 * <a href="https://oldschool.runescape.wiki/w/Vorkath">Vorkath</a> (visages
 * 1/5,000 each, necklace 1/1,000, so 7/5,000, about 1/714) and
 * <a href="https://oldschool.runescape.wiki/w/Zulrah">Zulrah</a> (two rolls
 * at 1/1,024 for each of four items, so 8/1,024, 1/128). A boss not listed is
 * not tracked.
 */
final class DryStreak
{
	static final class Rate
	{
		final int oneIn;
		final Set<String> uniques;

		Rate(int oneIn, String... uniques)
		{
			this.oneIn = oneIn;
			Set<String> names = new HashSet<>();
			for (String u : Arrays.asList(uniques))
			{
				names.add(u.toLowerCase(Locale.ROOT));
			}
			this.uniques = Collections.unmodifiableSet(names);
		}
	}

	static final Map<String, Rate> RATES = new TreeMap<>();

	static
	{
		RATES.put("vorkath", new Rate(714, "Draconic visage", "Skeletal visage", "Dragonbone necklace"));
		RATES.put("zulrah", new Rate(128, "Tanzanite fang", "Magic fang", "Serpentine visage", "Uncut onyx"));
	}

	/**
	 * How far apart a kill's count line and its loot may land, in either order.
	 * The real order is not confirmed in game, so neither is assumed. Same
	 * allowance as {@link GroupEventEngine#KC_WINDOW_MS}.
	 */
	static final long HOLD_MS = 10_000L;

	private final Map<String, Integer> kills = new TreeMap<>();
	private final Map<String, GroupEvent> held = new TreeMap<>();
	private final Map<String, Long> lastKillAt = new TreeMap<>();
	private final Map<String, Long> uniqueAt = new TreeMap<>();

	private DryStreak()
	{
	}

	private static String key(String boss)
	{
		return boss == null ? "" : boss.trim().toLowerCase(Locale.ROOT);
	}

	/** One kill. A call-out it earns waits in {@link #due} until the kill's loot has had time to arrive. */
	void kill(String boss, long now, String member)
	{
		String key = key(boss);
		Rate rate = RATES.get(key);
		if (rate == null)
		{
			return;
		}
		Long unique = uniqueAt.remove(key);
		if (unique != null && now - unique <= HOLD_MS)
		{
			// This kill's unique arrived first and already ended the streak.
			return;
		}
		lastKillAt.put(key, now);
		int count = kills.merge(key, 1, Integer::sum);
		if (count % rate.oneIn == 0)
		{
			held.put(key, GroupEvent.of(GroupEvent.Type.DRY_STREAK, now, member).text(boss.trim()).number(count)
				.detail("unique rate about 1/" + rate.oneIn).build());
		}
	}

	/** A drop from {@code boss}: any listed unique among {@code itemNames} ends the streak. */
	void drop(String boss, Set<String> itemNames, long now)
	{
		String key = key(boss);
		Rate rate = RATES.get(key);
		if (rate == null)
		{
			return;
		}
		for (String name : itemNames)
		{
			if (rate.uniques.contains(name.toLowerCase(Locale.ROOT)))
			{
				kills.remove(key);
				held.remove(key);
				Long last = lastKillAt.get(key);
				if (last == null || now - last > HOLD_MS)
				{
					uniqueAt.put(key, now);
				}
				return;
			}
		}
	}

	/** Call-outs whose kill is older than {@link #HOLD_MS}: no unique can still cancel them. */
	List<GroupEvent> due(long now)
	{
		if (held.isEmpty())
		{
			return Collections.emptyList();
		}
		List<GroupEvent> out = new ArrayList<>();
		for (Iterator<Map.Entry<String, GroupEvent>> it = held.entrySet().iterator(); it.hasNext(); )
		{
			Map.Entry<String, GroupEvent> e = it.next();
			if (now - lastKillAt.get(e.getKey()) > HOLD_MS)
			{
				out.add(e.getValue());
				it.remove();
			}
		}
		return out;
	}

	int kills(String boss)
	{
		return kills.getOrDefault(key(boss), 0);
	}

	String save()
	{
		StringBuilder out = new StringBuilder();
		for (Map.Entry<String, Integer> e : kills.entrySet())
		{
			out.append(e.getKey()).append('=').append(e.getValue()).append(';');
		}
		return out.toString();
	}

	static DryStreak parse(String saved)
	{
		DryStreak out = new DryStreak();
		for (String part : (saved == null ? "" : saved).split(";"))
		{
			String[] kv = part.split("=");
			if (kv.length != 2 || !RATES.containsKey(kv[0]))
			{
				continue;
			}
			try
			{
				out.kills.put(kv[0], Math.max(0, Integer.parseInt(kv[1])));
			}
			catch (NumberFormatException e)
			{
				// Saved text from a newer or damaged profile: that boss starts over.
				out.kills.remove(kv[0]);
			}
		}
		return out;
	}
}
