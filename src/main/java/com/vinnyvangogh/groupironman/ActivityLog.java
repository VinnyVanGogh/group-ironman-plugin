package com.vinnyvangogh.groupironman;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

/**
 * What the sidebar Log tab shows (Board 2026-11-10): every group event the
 * overlay or Discord can show, yours and members', from the same
 * {@link GroupEvent} objects. Pure: the feed is a {@link DropLog} with this
 * class's bounds, kept in {@link GroupRoster#feed()} and the group cache; this
 * class searches, filters and lays it out.
 */
final class ActivityLog
{
	/** Kept: the newest this many, and none older than {@link #MAX_AGE_MS}, whichever keeps fewer. */
	static final int MAX_ENTRIES = 2000;
	static final long MAX_AGE_MS = TimeUnit.DAYS.toMillis(30);

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

	private ActivityLog()
	{
	}

	/** A new, empty feed with the Log's bounds. */
	static DropLog newFeed()
	{
		return new DropLog(MAX_ENTRIES, MAX_AGE_MS, System::currentTimeMillis);
	}

	/** The time range picker. */
	enum Range
	{
		ALL("All time"), TODAY("Today"), WEEK("Last 7 days");

		final String label;

		Range(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}

		/** The oldest time this range keeps. */
		long from(long now, ZoneId zone)
		{
			switch (this)
			{
				case TODAY:
					return Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
						.toEpochMilli();
				case WEEK:
					return now - TimeUnit.DAYS.toMillis(7);
				default:
					return Long.MIN_VALUE;
			}
		}
	}

	/** The search box, the category chips (none ticked keeps all), the member picker (empty for all) and the range. */
	static final class Filter
	{
		static final Filter ALL = new Filter("", EnumSet.noneOf(GroupEvent.Category.class), "", Range.ALL);

		final String query;
		final Set<GroupEvent.Category> categories;
		final String member;
		final Range range;

		Filter(String query, Set<GroupEvent.Category> categories, String member, Range range)
		{
			this.query = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
			this.categories = categories.isEmpty() ? EnumSet.noneOf(GroupEvent.Category.class)
				: EnumSet.copyOf(categories);
			this.member = member == null ? "" : member.trim();
			this.range = range == null ? Range.ALL : range;
		}

		boolean keeps(GroupEvent e, ItemLookup items, long from)
		{
			return e.at >= from
				&& (categories.isEmpty() || categories.contains(e.type.category))
				&& (member.isEmpty() || member.equalsIgnoreCase(e.member))
				&& (query.isEmpty() || haystack(e, items).contains(query));
		}
	}

	/** {@code events} (newest first) that {@code filter} keeps, in the same order. */
	static List<GroupEvent> filter(List<GroupEvent> events, Filter filter, ItemLookup items, long now, ZoneId zone)
	{
		long from = filter.range.from(now, zone);
		List<GroupEvent> out = new ArrayList<>();
		for (GroupEvent e : events)
		{
			if (filter.keeps(e, items, from))
			{
				out.add(e);
			}
		}
		return out;
	}

	/** Everything the search box matches: member, item names, place, the line itself. Lower case. */
	static String haystack(GroupEvent e, ItemLookup items)
	{
		StringBuilder out = new StringBuilder();
		out.append(e.member).append('\n').append(e.text).append('\n').append(e.detail).append('\n')
			.append(e.location).append('\n').append(EventText.title(e)).append('\n')
			.append(EventText.describe(e, items));
		for (Integer id : e.items.keySet())
		{
			out.append('\n').append(items.name(id));
		}
		for (Integer id : e.taken.keySet())
		{
			out.append('\n').append(items.name(id));
		}
		return out.toString().toLowerCase(Locale.ROOT);
	}

	/** A category chip's label: "Drops", "Activities". */
	static String label(GroupEvent.Category category)
	{
		String name = category.name();
		return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
	}

	/** Members seen in the feed, for the member picker. */
	static List<String> members(List<GroupEvent> events)
	{
		Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (GroupEvent e : events)
		{
			out.add(e.member);
		}
		return new ArrayList<>(out);
	}

	/** One heading and its events. */
	static final class Section
	{
		final String title;
		final List<GroupEvent> events = new ArrayList<>();

		Section(String title)
		{
			this.title = title;
		}
	}

	/**
	 * {@code shown} (newest first) in sections: "Since you last played" on
	 * top when {@code since} is known (members' events after it), then the
	 * rest by day. An event is in one section only.
	 */
	static List<Section> sections(List<GroupEvent> shown, long since, String self, long now, ZoneId zone)
	{
		List<Section> out = new ArrayList<>();
		Section top = since > 0 ? new Section("Since you last played (" + BoostText.ago(now, since) + ")") : null;
		if (top != null)
		{
			out.add(top);
		}
		Section day = null;
		LocalDate today = date(now, zone);
		for (GroupEvent e : shown)
		{
			if (top != null && e.at > since && !e.member.equalsIgnoreCase(self))
			{
				top.events.add(e);
				continue;
			}
			String title = dayTitle(date(e.at, zone), today);
			if (day == null || !day.title.equals(title))
			{
				day = new Section(title);
				out.add(day);
			}
			day.events.add(e);
		}
		return out;
	}

	private static LocalDate date(long at, ZoneId zone)
	{
		return Instant.ofEpochMilli(at).atZone(zone).toLocalDate();
	}

	static String dayTitle(LocalDate day, LocalDate today)
	{
		if (day.equals(today))
		{
			return "Today";
		}
		return day.equals(today.minusDays(1)) ? "Yesterday" : DAY.format(day);
	}

	/** One row: an icon, the member, a short line and how long ago; the full text on hover; the text Copy puts on the clipboard. */
	static final class Row
	{
		final GroupEvent event;
		/** "Vorkath dropped 1 x Draconic visage (2.3M gp)", game amounts. */
		final String text;
		final String ago;
		/** The hover: title, the full line, the value and the time. Amounts in the game's style with every digit. */
		final String full;
		final String copy;
		/** The item to draw, -1 for none. */
		final int itemId;
		final long quantity;
		/** The skill to draw for a level or XP event, null for none. */
		final String skill;

		private Row(GroupEvent e, ItemLookup items, long now, ZoneId zone)
		{
			this.event = e;
			this.text = EventText.describe(e, items);
			this.ago = BoostText.ago(now, e.at);
			long value = value(e, items);
			String when = WHEN.format(Instant.ofEpochMilli(e.at).atZone(zone));
			String sentence = EventText.sentence(e, Amounts.BOTH.words(items));
			String worth = value > 0 ? "Value: " + Amounts.BOTH.of(value) + " gp" : "";
			this.full = EventText.title(e) + "\n" + sentence + (worth.isEmpty() ? "" : "\n" + worth) + "\n" + when;
			this.copy = "[" + when + "] " + sentence + (worth.isEmpty() ? "" : " (" + worth + ")");
			Map.Entry<Integer, Integer> icon = iconItem(e, items);
			this.itemId = icon == null ? -1 : icon.getKey();
			this.quantity = icon == null ? 0 : icon.getValue();
			this.skill = skillOf(e);
		}
	}

	static Row row(GroupEvent e, ItemLookup items, long now, ZoneId zone)
	{
		return new Row(e, items, now, zone);
	}

	/** The gp the event is worth: its items, or the number for a death's loss; 0 when none. */
	static long value(GroupEvent e, ItemLookup items)
	{
		if (e.type == GroupEvent.Type.DEATH_VALUE)
		{
			return e.number;
		}
		return EventText.value(e.items, items) + EventText.value(e.taken, items);
	}

	/** The event's most valuable item, or its first when no price is known; null with none. */
	static Map.Entry<Integer, Integer> iconItem(GroupEvent e, ItemLookup items)
	{
		Map<Integer, Integer> from = e.items.isEmpty() ? e.taken : e.items;
		Map.Entry<Integer, Integer> best = null;
		long bestValue = -1;
		for (Map.Entry<Integer, Integer> i : from.entrySet())
		{
			long v = items.price(i.getKey()) * i.getValue();
			if (v > bestValue)
			{
				best = i;
				bestValue = v;
			}
		}
		return best;
	}

	/** The skill a level, 99 or XP event is about; null for the rest and for a max total. */
	static String skillOf(GroupEvent e)
	{
		switch (e.type)
		{
			case LEVEL_UP:
			case XP_MILESTONE:
				return e.text;
			case MAXED:
				return "total".equalsIgnoreCase(e.text) ? null : e.text;
			default:
				return null;
		}
	}
}
