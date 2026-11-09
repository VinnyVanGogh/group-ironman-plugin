package com.vinnyvangogh.groupironman;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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

	private final Map<String, Integer> kills = new TreeMap<>();

	private DryStreak()
	{
	}

	private static String key(String boss)
	{
		return boss == null ? "" : boss.trim().toLowerCase(Locale.ROOT);
	}

	/** One kill; the call-out when this kill lands on a multiple of the rate, else null. */
	GroupEvent kill(String boss, long now, String member)
	{
		String key = key(boss);
		Rate rate = RATES.get(key);
		if (rate == null)
		{
			return null;
		}
		int count = kills.merge(key, 1, Integer::sum);
		if (count % rate.oneIn != 0)
		{
			return null;
		}
		return GroupEvent.of(GroupEvent.Type.DRY_STREAK, now, member).text(boss.trim()).number(count)
			.detail("unique rate about 1/" + rate.oneIn).build();
	}

	/** A drop from {@code boss}: any listed unique among {@code itemNames} ends the streak. */
	void drop(String boss, Set<String> itemNames)
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
				return;
			}
		}
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
