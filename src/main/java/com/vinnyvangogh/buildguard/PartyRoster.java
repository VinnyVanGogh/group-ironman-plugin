package com.vinnyvangogh.buildguard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The party members' latest payloads, and the lines the panel draws from them.
 *
 * <p>Party messages arrive off the client thread and the panel is drawn on the
 * Swing thread, so the map is concurrent and every read returns a fresh list.
 */
final class PartyRoster
{
	private static final class Entry
	{
		final String name;
		final PartyBuild build;

		Entry(String name, PartyBuild build)
		{
			this.name = name;
			this.build = build;
		}
	}

	private final Map<Long, Entry> members = new ConcurrentHashMap<>();

	void put(long memberId, String displayName, PartyBuild build)
	{
		String name = displayName == null || displayName.trim().isEmpty() ? "Member " + memberId : displayName;
		members.put(memberId, new Entry(name, build));
	}

	void remove(long memberId)
	{
		members.remove(memberId);
	}

	void clear()
	{
		members.clear();
	}

	boolean isEmpty()
	{
		return members.isEmpty();
	}

	/** One line per member: every rule with its current level, ordered as rules matter. */
	List<String> memberLines()
	{
		List<String> out = new ArrayList<>();
		for (Entry e : sorted())
		{
			if (e.build.rules().isEmpty())
			{
				out.add(e.name + ": no build rules");
				continue;
			}
			List<String> parts = new ArrayList<>();
			for (BuildRule rule : BuildRule.ordered(e.build.rules()))
			{
				Integer level = e.build.levels().get(rule.skillName());
				parts.add(rule.skillName() + " " + rule.describe() + (level == null ? "" : " (now " + level + ")"));
			}
			out.add(e.name + ": " + String.join(", ", parts));
		}
		return out;
	}

	/** "X is at risk of Defence XP (Controlled attack style)", for members who share it. */
	List<String> riskLines()
	{
		List<String> out = new ArrayList<>();
		for (Entry e : sorted())
		{
			if (e.build.atRisk().isEmpty())
			{
				continue;
			}
			String why = e.build.riskReason().isEmpty() ? "" : " (" + e.build.riskReason() + ")";
			out.add(e.name + " is at risk of " + String.join(", ", e.build.atRisk()) + " XP" + why);
		}
		return out;
	}

	/** Item name to the members carrying it, both sorted, for the "who has what" list. */
	Map<String, List<String>> gearIndex()
	{
		Map<String, List<String>> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (Entry e : sorted())
		{
			for (String item : e.build.gear())
			{
				List<String> holders = out.computeIfAbsent(item, k -> new ArrayList<>());
				if (!holders.contains(e.name))
				{
					holders.add(e.name);
				}
			}
		}
		return out;
	}

	private List<Entry> sorted()
	{
		List<Entry> out = new ArrayList<>(members.values());
		Collections.sort(out, (a, b) -> a.name.compareToIgnoreCase(b.name));
		return out;
	}
}
