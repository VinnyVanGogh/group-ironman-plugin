package com.vinnyvangogh.groupironman;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * The daily digest: lines held per category instead of posted one by one, and
 * when the digest and the weekly recap are due. Pure and clock-free, so the
 * schedule is tested with fixed times.
 *
 * <p>Only this player's own lines are ever held, the same rule as every post
 * (BRIEF.md #18), so a group's digests are one per member.
 */
final class Digest
{
	/** Most lines kept per category per day; the oldest make way. */
	static final int MAX_LINES = 50;

	private final Map<GroupEvent.Category, LinkedList<String>> lines = new EnumMap<>(GroupEvent.Category.class);

	synchronized void add(GroupEvent.Category category, String line)
	{
		LinkedList<String> held = lines.computeIfAbsent(category, c -> new LinkedList<>());
		held.addLast(line);
		while (held.size() > MAX_LINES)
		{
			held.removeFirst();
		}
	}

	/** The held lines, oldest first, and forgets them. */
	synchronized List<String> drain(GroupEvent.Category category)
	{
		LinkedList<String> held = lines.remove(category);
		return held == null ? new ArrayList<>() : new ArrayList<>(held);
	}

	/** One {@code CATEGORY<tab>line} per line. Event text holds no tabs or newlines once flattened. */
	synchronized String save()
	{
		StringBuilder out = new StringBuilder();
		for (Map.Entry<GroupEvent.Category, LinkedList<String>> e : lines.entrySet())
		{
			for (String line : e.getValue())
			{
				out.append(e.getKey().name()).append('\t').append(flat(line)).append('\n');
			}
		}
		return out.toString();
	}

	private static String flat(String line)
	{
		return line.replace('\t', ' ').replace('\n', ' ');
	}

	static Digest parse(String saved)
	{
		Digest out = new Digest();
		for (String row : (saved == null ? "" : saved).split("\n"))
		{
			String[] parts = row.split("\t", 2);
			GroupEvent.Category category = parts.length == 2 ? category(parts[0]) : null;
			if (category != null)
			{
				out.add(category, parts[1]);
			}
		}
		return out;
	}

	private static GroupEvent.Category category(String name)
	{
		for (GroupEvent.Category c : GroupEvent.Category.values())
		{
			if (c.name().equals(name))
			{
				return c;
			}
		}
		return null;
	}

	/** Today's digest time has passed and the last one was before it. A first run ({@code last} 0) only baselines. */
	static boolean dailyDue(long last, long now, int hour, ZoneId zone)
	{
		if (last <= 0)
		{
			return false;
		}
		ZonedDateTime today = Instant.ofEpochMilli(now).atZone(zone).withHour(hour).withMinute(0).withSecond(0)
			.withNano(0);
		return now >= today.toInstant().toEpochMilli() && last < today.toInstant().toEpochMilli();
	}

	/** This Monday's recap time has passed and the last recap was before it. */
	static boolean weeklyDue(long last, long now, int hour, ZoneId zone)
	{
		if (last <= 0)
		{
			return false;
		}
		ZonedDateTime monday = Instant.ofEpochMilli(now).atZone(zone)
			.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).withHour(hour).withMinute(0).withSecond(0)
			.withNano(0);
		long at = monday.toInstant().toEpochMilli();
		return now >= at && last < at;
	}

	/** One player's week: XP since the first reading, and counts. Saved per account. */
	static final class Week
	{
		private long xpStart = -1;
		private long xpNow;
		private int drops;
		private int kills;
		private int deposits;
		private int withdrawals;

		private Week()
		{
		}

		void xp(long total)
		{
			if (xpStart < 0)
			{
				xpStart = total;
			}
			xpNow = total;
		}

		void drop()
		{
			drops++;
		}

		void kill()
		{
			kills++;
		}

		void storage(boolean deposit)
		{
			if (deposit)
			{
				deposits++;
			}
			else
			{
				withdrawals++;
			}
		}

		GroupEvent recap(long at, String member)
		{
			return GroupEvent.of(GroupEvent.Type.WEEKLY_RECAP, at, member)
				.text(drops + " drops, " + kills + " kills, " + deposits + " deposits, " + withdrawals + " withdrawals")
				.number(xpStart < 0 ? 0 : Math.max(0, xpNow - xpStart))
				.build();
		}

		void startOver()
		{
			xpStart = xpNow;
			drops = 0;
			kills = 0;
			deposits = 0;
			withdrawals = 0;
		}

		String save()
		{
			return xpStart + "," + xpNow + "," + drops + "," + kills + "," + deposits + "," + withdrawals;
		}

		static Week parse(String saved)
		{
			Week w = new Week();
			String[] p = (saved == null ? "" : saved).split(",");
			if (p.length != 6)
			{
				return w;
			}
			try
			{
				w.xpStart = Long.parseLong(p[0]);
				w.xpNow = Long.parseLong(p[1]);
				w.drops = Integer.parseInt(p[2]);
				w.kills = Integer.parseInt(p[3]);
				w.deposits = Integer.parseInt(p[4]);
				w.withdrawals = Integer.parseInt(p[5]);
				return w;
			}
			catch (NumberFormatException e)
			{
				return new Week();
			}
		}
	}
}
