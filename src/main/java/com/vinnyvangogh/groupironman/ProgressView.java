package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * What the Group, Compare and Personal bests views read about members' shared boss bests, quests, quest points and
 * diaries, plus the text built from them. Pure: no Swing, no game. A snapshot, so the panel never reads the live
 * {@link MemberProgressBook} on the Swing thread more than once per redraw.
 */
final class ProgressView
{
	static final ProgressView EMPTY = new ProgressView(Collections.emptyMap(), Collections.emptyMap());
	/** Diary tiers in the game: 12 areas of four. */
	static final int DIARY_TIERS = 48;

	private final Map<String, PersonalBests> bests;
	private final Map<String, AchievementState> achievements;

	ProgressView(Map<String, PersonalBests> bests, Map<String, AchievementState> achievements)
	{
		this.bests = bests;
		this.achievements = achievements;
	}

	/** A snapshot of the book, with this player's own figures added (a member is not sent their own). */
	static ProgressView of(MemberProgressBook book, String self, PersonalBests ownBests, AchievementState ownAch)
	{
		Map<String, PersonalBests> b = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		Map<String, AchievementState> a = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		if (book != null)
		{
			b.putAll(book.allBests());
			for (String name : book.achievementMembers())
			{
				put(a, name, book.achievements(name));
			}
		}
		if (self != null && !self.isEmpty())
		{
			if (ownBests != null && !ownBests.isEmpty())
			{
				b.put(self, ownBests);
			}
			put(a, self, ownAch);
		}
		return new ProgressView(b, a);
	}

	private static void put(Map<String, AchievementState> to, String name, AchievementState s)
	{
		if (s != null)
		{
			to.put(name, s);
		}
	}

	PersonalBests bests(String member)
	{
		PersonalBests b = bests.get(member);
		return b == null ? PersonalBests.EMPTY : b;
	}

	/** Null when this member has shared none. */
	AchievementState achievements(String member)
	{
		return achievements.get(member);
	}

	Collection<String> members()
	{
		List<String> out = new ArrayList<>(bests.keySet());
		for (String n : achievements.keySet())
		{
			if (!bests.containsKey(n))
			{
				out.add(n);
			}
		}
		Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
		return out;
	}

	/** The lines a member card adds: quest points, quests done and diaries, then the fastest bests. Empty if none. */
	List<String> cardLines(String member, int maxBosses)
	{
		List<String> out = new ArrayList<>();
		AchievementState a = achievements.get(member);
		if (a != null)
		{
			out.add("Quest points " + a.questPoints() + " · " + a.finished().size() + " quests done"
				+ (a.inProgress().isEmpty() ? "" : ", " + a.inProgress().size() + " started"));
			out.add("Achievement diaries " + a.diaryTiersDone() + "/" + DIARY_TIERS);
		}
		PersonalBests b = bests(member);
		int shown = 0;
		for (Map.Entry<String, Double> e : b.all().entrySet())
		{
			if (shown++ >= maxBosses)
			{
				out.add("+" + (b.all().size() - maxBosses) + " more bests in Activities");
				break;
			}
			out.add(e.getKey() + " best " + PersonalBests.format(e.getValue()));
		}
		return out;
	}

	/** One boss: every member's time, fastest first, and who holds the group best. */
	static final class BossRow
	{
		final String boss;
		/** Member to seconds, fastest first. */
		final Map<String, Double> times;
		final String holder;
		final double best;

		BossRow(String boss, Map<String, Double> times, String holder, double best)
		{
			this.boss = boss;
			this.times = times;
			this.holder = holder;
			this.best = best;
		}

		/** "Zulrah: 1:05.0 GIM1 (best), 1:10.2 GIM2". */
		String line()
		{
			StringBuilder sb = new StringBuilder(boss).append(": ");
			boolean first = true;
			for (Map.Entry<String, Double> e : times.entrySet())
			{
				if (!first)
				{
					sb.append(", ");
				}
				first = false;
				sb.append(PersonalBests.format(e.getValue())).append(' ').append(e.getKey());
				if (e.getKey().equals(holder))
				{
					sb.append(" (best)");
				}
			}
			return sb.toString();
		}
	}

	/** Every boss any member has a best for, in name order, each with the members ranked fastest first. */
	List<BossRow> bossRows()
	{
		Map<String, Map<String, Double>> byBoss = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (Map.Entry<String, PersonalBests> m : bests.entrySet())
		{
			for (Map.Entry<String, Double> e : m.getValue().all().entrySet())
			{
				byBoss.computeIfAbsent(e.getKey(), k -> new TreeMap<>()).put(m.getKey(), e.getValue());
			}
		}
		List<BossRow> out = new ArrayList<>();
		for (Map.Entry<String, Map<String, Double>> e : byBoss.entrySet())
		{
			List<Map.Entry<String, Double>> sorted = new ArrayList<>(e.getValue().entrySet());
			sorted.sort(Map.Entry.<String, Double>comparingByValue().thenComparing(Map.Entry.comparingByKey()));
			Map<String, Double> ranked = new java.util.LinkedHashMap<>();
			sorted.forEach(s -> ranked.put(s.getKey(), s.getValue()));
			Map.Entry<String, Double> top = sorted.get(0);
			out.add(new BossRow(e.getKey(), ranked, top.getKey(), top.getValue()));
		}
		return out;
	}

	/** Newest first: "GIM1 set a new Zulrah best: 1:05.0" lines from the shared log, at most {@code max}. */
	static List<String> history(List<GroupEvent> feed, int max)
	{
		List<GroupEvent> pbs = new ArrayList<>();
		for (GroupEvent e : feed)
		{
			if (e.type == GroupEvent.Type.PERSONAL_BEST)
			{
				pbs.add(e);
			}
		}
		pbs.sort((x, y) -> Long.compare(y.at, x.at));
		List<String> out = new ArrayList<>();
		for (GroupEvent e : pbs)
		{
			if (out.size() >= max)
			{
				break;
			}
			out.add(String.format(Locale.ROOT, "%s: %s", e.member, e.text));
		}
		return out;
	}
}
