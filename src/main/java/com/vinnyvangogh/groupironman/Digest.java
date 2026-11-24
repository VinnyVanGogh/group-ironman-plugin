package com.vinnyvangogh.groupironman;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The daily digest: each category's lines since the last digest, collected
 * alongside the instant posts (never instead of them), and when the digest
 * and the weekly recap are due. Pure and clock-free, so the schedule is
 * tested with fixed times.
 *
 * <p>Only this player's own lines are ever held, the same rule as every post
 * (BRIEF.md #18), so a group's digests are one per member.
 */
final class Digest
{
	/** Most entries kept per category per day; the oldest make way. */
	static final int MAX_LINES = 50;
	/** A saved row with every field: category, line, then {@link Entry}'s eight. */
	private static final int FIELDS = 10;

	private final Map<GroupEvent.Category, LinkedList<Entry>> lines = new EnumMap<>(GroupEvent.Category.class);

	/**
	 * One held event: what it said ({@link #line}, without the member's name) and enough of the event to sum a
	 * member's day ({@link DigestPost}): storage moves netted, level-ups grouped per skill, a request's items in full.
	 */
	static final class Entry
	{
		/** Null for a line an older build saved, which kept only "Member: words". */
		final GroupEvent.Type type;
		final String member;
		final long at;
		final String text;
		final long number;
		final long previous;
		final Map<Integer, Integer> items;
		final Map<Integer, Integer> taken;
		final String line;

		Entry(GroupEvent.Type type, String member, long at, String text, long number, long previous,
			Map<Integer, Integer> items, Map<Integer, Integer> taken, String line)
		{
			this.type = type;
			this.member = member == null ? "" : flat(member).trim();
			this.at = at;
			this.text = text == null ? "" : flat(text);
			this.number = number;
			this.previous = previous;
			this.items = items == null ? Collections.emptyMap() : Collections.unmodifiableMap(new TreeMap<>(items));
			this.taken = taken == null ? Collections.emptyMap() : Collections.unmodifiableMap(new TreeMap<>(taken));
			this.line = line == null ? "" : flat(line).trim();
		}

		static Entry of(GroupEvent e, String line)
		{
			return new Entry(e.type, e.member, e.at, e.text, e.number, e.previous, e.items, e.taken, line);
		}

		/** An older build's "Member: words" line. */
		static Entry legacy(String saved)
		{
			int cut = saved.indexOf(": ");
			return cut <= 0 ? new Entry(null, "", 0, "", 0, 0, null, null, saved)
				: new Entry(null, saved.substring(0, cut), 0, "", 0, 0, null, null, saved.substring(cut + 2));
		}
	}

	/** {@code event}'s line in its category, {@code words} as Discord would say it, without the member's name. */
	synchronized void add(GroupEvent event, String words)
	{
		add(event.type.category, Entry.of(event, words));
	}

	private void add(GroupEvent.Category category, Entry entry)
	{
		LinkedList<Entry> held = lines.computeIfAbsent(category, c -> new LinkedList<>());
		held.addLast(entry);
		while (held.size() > MAX_LINES)
		{
			held.removeFirst();
		}
	}

	/** The held entries, oldest first, and forgets them. */
	synchronized List<Entry> drain(GroupEvent.Category category)
	{
		LinkedList<Entry> held = lines.remove(category);
		return held == null ? new ArrayList<>() : new ArrayList<>(held);
	}

	/**
	 * One row per entry: {@code CATEGORY, line, type code, at, member, text, number, previous, items, taken},
	 * tab separated, items as {@code id:qty;id:qty}. Text holds no tabs or newlines once flattened. A row of only
	 * {@code CATEGORY<tab>line} is an older build's and still reads.
	 */
	synchronized String save()
	{
		StringBuilder out = new StringBuilder();
		for (Map.Entry<GroupEvent.Category, LinkedList<Entry>> e : lines.entrySet())
		{
			for (Entry d : e.getValue())
			{
				out.append(e.getKey().name()).append('\t');
				if (d.type == null)
				{
					// Kept the older way, so a downgraded build still reads it.
					out.append(d.member.isEmpty() ? "" : d.member + ": ").append(d.line).append('\n');
					continue;
				}
				out.append(d.line).append('\t').append(d.type.code).append('\t').append(d.at).append('\t')
					.append(d.member).append('\t').append(d.text).append('\t').append(d.number).append('\t')
					.append(d.previous).append('\t').append(counts(d.items)).append('\t').append(counts(d.taken))
					.append('\n');
			}
		}
		return out.toString();
	}

	private static String flat(String line)
	{
		return line.replace('\t', ' ').replace('\n', ' ');
	}

	private static String counts(Map<Integer, Integer> items)
	{
		StringBuilder out = new StringBuilder();
		for (Map.Entry<Integer, Integer> e : items.entrySet())
		{
			out.append(out.length() == 0 ? "" : ";").append(e.getKey()).append(':').append(e.getValue());
		}
		return out.toString();
	}

	private static Map<Integer, Integer> counts(String saved)
	{
		Map<Integer, Integer> out = new TreeMap<>();
		for (String pair : saved.split(";"))
		{
			String[] p = pair.split(":");
			if (p.length == 2 && out.size() < GroupEvent.MAX_DROP_ITEMS)
			{
				out.put(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
			}
		}
		return out;
	}

	static Digest parse(String saved)
	{
		Digest out = new Digest();
		for (String row : (saved == null ? "" : saved).split("\n"))
		{
			String[] parts = row.split("\t", -1);
			GroupEvent.Category category = parts.length >= 2 ? category(parts[0]) : null;
			if (category == null)
			{
				continue;
			}
			if (parts.length != FIELDS)
			{
				out.add(category, Entry.legacy(parts[1]));
				continue;
			}
			Entry entry = entry(parts);
			// A kind this build does not know keeps its line, under its member.
			out.add(category, entry != null ? entry : new Entry(null, parts[4], 0, "", 0, 0, null, null, parts[1]));
		}
		return out;
	}

	/** A full row's entry; null when a field is unreadable, so the row falls back to its line. */
	private static Entry entry(String[] p)
	{
		GroupEvent.Type type = GroupEvent.Type.of(p[2]);
		if (type == null)
		{
			return null;
		}
		try
		{
			return new Entry(type, p[4], Long.parseLong(p[3]), p[5], Long.parseLong(p[6]), Long.parseLong(p[7]),
				counts(p[8]), counts(p[9]), p[1]);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
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

	/** When the next daily digest is due after {@code now}: today's hour if still ahead, otherwise tomorrow's. */
	static long nextDaily(long now, int hour, ZoneId zone)
	{
		ZonedDateTime today = Instant.ofEpochMilli(now).atZone(zone).withHour(hour).withMinute(0).withSecond(0)
			.withNano(0);
		return (today.toInstant().toEpochMilli() > now ? today : today.plusDays(1)).toInstant().toEpochMilli();
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
