package com.vinnyvangogh.groupironman;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;

/**
 * Skill levels from the OSRS hiscores for members who do not share their own (Board 2026-11-11): what the Group
 * tab shows for a member who never ran GIM Utilities. Shared plugin data always wins; the panel only reads this
 * when a member has no shared stats.
 *
 * <p>Looked up with RuneLite's own {@code HiscoreClient}, the lookup the base Hiscore plugin uses, never on the
 * client thread. Each member is looked up at most once every {@link #REFRESH_MS} (6 hours, Board), and only when
 * something asks: this player logging in, or the member coming online. A failed lookup is retried on its own,
 * after a backoff that doubles from {@link #FIRST_BACKOFF_MS} up to the same 6 hours, so a hiscore outage never
 * turns into a storm of requests.
 *
 * <p>Results are kept in the group cache, so a member's levels show across restarts with when they were read.
 * Failures are not kept: a restart starts the backoff over. Keyed by {@link GroupMembers#key}. Every method is
 * synchronized: lookups finish on OkHttp's threads, the panel reads on Swing's.
 */
final class HiscoreBook
{
	static final long REFRESH_MS = 6 * 60 * 60 * 1000L;
	static final long FIRST_BACKOFF_MS = 15 * 60 * 1000L;
	/** More skills than the game has, so a hostile cache file cannot grow the map without limit. */
	static final int MAX_SKILLS = 40;
	static final int MAX_LEVEL = 126;

	/** One successful lookup: when, and the level per skill as {@code Skill.getName()} spells it. */
	static final class Entry
	{
		final long at;
		final Map<String, Integer> levels;

		Entry(long at, Map<String, Integer> levels)
		{
			this.at = at;
			this.levels = Collections.unmodifiableMap(new LinkedHashMap<>(levels));
		}
	}

	private static final class Failure
	{
		final int count;
		final long at;

		Failure(int count, long at)
		{
			this.count = count;
			this.at = at;
		}
	}

	private final Map<String, Entry> entries = new HashMap<>();
	private final Map<String, Failure> failures = new HashMap<>();
	private final Set<String> inFlight = new HashSet<>();

	/**
	 * True, and the member marked as being looked up, when a lookup for {@code name} should start now.
	 * {@code asked}: this player just logged in or the member just came online. Without it only a failed
	 * lookup's retry can start.
	 */
	synchronized boolean begin(String name, long now, boolean asked)
	{
		String key = GroupMembers.key(name);
		if (key.isEmpty() || inFlight.contains(key))
		{
			return false;
		}
		Failure f = failures.get(key);
		boolean due = f != null ? now - f.at >= backoff(f.count) : asked && stale(entries.get(key), now);
		if (due)
		{
			inFlight.add(key);
		}
		return due;
	}

	private static boolean stale(Entry e, long now)
	{
		// A time in the future (a changed clock) counts as stale rather than blocking lookups for good.
		return e == null || now - e.at >= REFRESH_MS || now < e.at;
	}

	/** How long to wait after the {@code failures}th failure in a row: 15 min, 30 min, 1 h, ... at most 6 h. */
	static long backoff(int failures)
	{
		int doublings = Math.max(0, Math.min(failures - 1, 10));
		return Math.min(REFRESH_MS, FIRST_BACKOFF_MS << doublings);
	}

	synchronized void succeeded(String name, Map<String, Integer> levels, long now)
	{
		String key = GroupMembers.key(name);
		inFlight.remove(key);
		failures.remove(key);
		entries.put(key, new Entry(now, levels));
	}

	/** The lookup failed or the member is not on the hiscores. A level already read stays shown. */
	synchronized void failed(String name, long now)
	{
		String key = GroupMembers.key(name);
		inFlight.remove(key);
		Failure f = failures.get(key);
		failures.put(key, new Failure(f == null ? 1 : f.count + 1, now));
	}

	/** The last successful lookup for {@code name}, or null. */
	synchronized Entry entry(String name)
	{
		return entries.get(GroupMembers.key(name));
	}

	/** True when the last lookup for {@code name} failed. */
	synchronized boolean unavailable(String name)
	{
		return failures.containsKey(GroupMembers.key(name));
	}

	/** Every stored lookup, for the group cache. */
	synchronized Map<String, Entry> all()
	{
		return new TreeMap<>(entries);
	}

	/** From the group cache. Levels out of range, and skills past {@link #MAX_SKILLS}, are dropped. */
	synchronized void restore(String name, long at, Map<String, Integer> levels)
	{
		String key = GroupMembers.key(name);
		if (!key.isEmpty() && at > 0)
		{
			entries.put(key, new Entry(at, clean(levels)));
		}
	}

	synchronized void clear()
	{
		entries.clear();
		failures.clear();
		inFlight.clear();
	}

	static Map<String, Integer> clean(Map<String, Integer> levels)
	{
		Map<String, Integer> out = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> e : levels.entrySet())
		{
			Integer level = e.getValue();
			if (out.size() < MAX_SKILLS && e.getKey() != null && level != null && level > 0 && level <= MAX_LEVEL)
			{
				out.put(e.getKey(), level);
			}
		}
		return out;
	}

	/**
	 * The skill levels in a hiscore result, named as {@code net.runelite.api.Skill.getName()} names them so they
	 * line up with shared stats. Unranked skills (level -1) are left out. Empty for a null result.
	 */
	static Map<String, Integer> levels(HiscoreResult result)
	{
		Map<String, Integer> out = new LinkedHashMap<>();
		if (result == null || result.getSkills() == null)
		{
			return out;
		}
		for (Map.Entry<HiscoreSkill, net.runelite.client.hiscore.Skill> e : result.getSkills().entrySet())
		{
			String name = skillName(e.getKey());
			if (name != null && e.getValue() != null)
			{
				out.put(name, e.getValue().getLevel());
			}
		}
		return clean(out);
	}

	private static String skillName(HiscoreSkill skill)
	{
		if (skill == null || skill.getType() != HiscoreSkillType.SKILL)
		{
			return null;
		}
		try
		{
			return net.runelite.api.Skill.valueOf(skill.name()).getName();
		}
		catch (IllegalArgumentException e)
		{
			// A skill the hiscores have and this client's API does not.
			return null;
		}
	}
}
