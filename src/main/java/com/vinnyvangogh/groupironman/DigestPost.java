package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The daily digest as Discord posts: one embed per section (Storage, Progress, Group, ...) in the live posts' colours,
 * each member's lines as a field, the section's notable item (or skill) as its thumbnail and its totals as its
 * description (Board 2026-11-20: "the digest posts should probably look better too"). Pure: no network, no clock.
 *
 * <p>A member's storage moves are netted over the day, so taking 799,999 coins and putting them back reads as one
 * round trip, not two big moves; level-ups group per skill ("Magic 61 → 62"); a request lists its items in full.
 *
 * <p>Discord's limits (developer docs, "Embed Limits": a field value 1,024 characters, a field name 256, 25 fields per
 * embed, 6,000 characters across every embed of a message, 10 embeds per message; {@code content} 2,000) are kept by
 * splitting, never by cutting: a long member's lines go on in a "(cont.)" field, a full embed in a "(cont.)" embed and
 * a full message in the next post, each break between lines or, inside one long line, between words.
 */
final class DigestPost
{
	static final int FIELD_VALUE = 1_024;
	static final int FIELD_NAME = 256;
	static final int FIELDS = 25;
	static final int MESSAGE_CHARS = 6_000;
	static final int EMBEDS = 10;
	static final int CONTENT = 2_000;
	/** Most items one line names; the rest read "+N more", beside the whole list's total. */
	static final int LISTED = 25;
	/** The OSRS Wiki's skill icons, e.g. {@code Magic_icon.png}: Discord loads them, never this client. */
	private static final String SKILL_ICON = "https://oldschool.runescape.wiki/images/%s_icon.png";
	private static final String ITEM_ICON = DiscordWebhook.ICON;
	private static final String CONTINUED = " (cont.)";
	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH);

	private DigestPost()
	{
	}

	/** One section's words: its title and totals, each member's lines in the order they first appear, its picture. */
	static final class Section
	{
		final GroupEvent.Category category;
		final Map<String, List<String>> members = new LinkedHashMap<>();
		String summary = "";
		String thumbnail;

		Section(GroupEvent.Category category)
		{
			this.category = category;
		}

		String title()
		{
			return icon(category) + " " + name(category);
		}
	}

	// ---- What each section says ----

	/**
	 * The sections worth posting, in {@link GroupEvent.Category} order: a category with nothing held, or only round
	 * trips that {@code hideRoundTrips} leaves out, has none.
	 */
	static List<Section> sections(Map<GroupEvent.Category, List<Digest.Entry>> held, ItemLookup lookup,
		boolean hideRoundTrips)
	{
		List<Section> out = new ArrayList<>();
		for (GroupEvent.Category c : GroupEvent.Category.values())
		{
			List<Digest.Entry> entries = held.get(c);
			if (entries == null || entries.isEmpty())
			{
				continue;
			}
			Section s;
			switch (c)
			{
				case STORAGE:
					s = storage(entries, lookup, hideRoundTrips);
					break;
				case PROGRESS:
					s = progress(entries);
					break;
				default:
					s = lines(c, entries, lookup);
					break;
			}
			if (!s.members.isEmpty())
			{
				out.add(s);
			}
		}
		return out;
	}

	private static Map<String, List<Digest.Entry>> byMember(List<Digest.Entry> entries)
	{
		Map<String, List<Digest.Entry>> out = new LinkedHashMap<>();
		for (Digest.Entry e : entries)
		{
			out.computeIfAbsent(e.member, m -> new ArrayList<>()).add(e);
		}
		return out;
	}

	/** One item's moves by one member over the day. */
	private static final class Moved
	{
		long in;
		long out;
		/** Taken before it was ever put in: "moved ... and put them back" rather than "... and took them back". */
		boolean tookFirst;
	}

	/**
	 * Each member's storage moves netted per item: what they left in, what they took away, and the round trips
	 * (the same amount out and back in). A visit's summary already holds its deposit and withdrawal, so those two
	 * count only when the visit has no summary held.
	 */
	static Section storage(List<Digest.Entry> entries, ItemLookup lookup, boolean hideRoundTrips)
	{
		Section s = new Section(GroupEvent.Category.STORAGE);
		long netIn = 0;
		long netOut = 0;
		int hidden = 0;
		long bestValue = 0;
		int bestId = -1;
		long bestQty = 0;
		for (Map.Entry<String, List<Digest.Entry>> m : byMember(entries).entrySet())
		{
			Set<Long> visits = new HashSet<>();
			for (Digest.Entry e : m.getValue())
			{
				if (e.type == GroupEvent.Type.STORAGE_SUMMARY)
				{
					visits.add(e.at);
				}
			}
			Map<Integer, Moved> moves = new LinkedHashMap<>();
			List<Digest.Entry> others = new ArrayList<>();
			for (Digest.Entry e : m.getValue())
			{
				if (e.type == GroupEvent.Type.STORAGE_SUMMARY)
				{
					move(moves, e.items, true);
					move(moves, e.taken, false);
				}
				else if (e.type == GroupEvent.Type.STORAGE_DEPOSIT || e.type == GroupEvent.Type.STORAGE_WITHDRAWAL)
				{
					if (!visits.contains(e.at))
					{
						move(moves, e.items, e.type == GroupEvent.Type.STORAGE_DEPOSIT);
					}
				}
				else
				{
					others.add(e);
				}
			}
			Map<Integer, Integer> putIn = new LinkedHashMap<>();
			Map<Integer, Integer> tookOut = new LinkedHashMap<>();
			List<String> lines = new ArrayList<>();
			List<String> trips = new ArrayList<>();
			for (Map.Entry<Integer, Moved> i : moves.entrySet())
			{
				int id = i.getKey();
				Moved mv = i.getValue();
				long net = mv.in - mv.out;
				long value = lookup.price(id) * Math.abs(net);
				if (net > 0)
				{
					putIn.put(id, clampInt(net));
					netIn += value;
				}
				else if (net < 0)
				{
					tookOut.put(id, clampInt(-net));
					netOut += value;
				}
				else if (mv.in > 0)
				{
					if (hideRoundTrips)
					{
						hidden++;
					}
					else
					{
						String what = item(id, mv.in, lookup);
						trips.add(mv.tookFirst ? "moved " + what + " and put them back"
							: "put in " + what + " and took them back out");
					}
				}
				if (net != 0 && value > bestValue)
				{
					bestValue = value;
					bestId = id;
					bestQty = Math.abs(net);
				}
			}
			if (!putIn.isEmpty())
			{
				lines.add("Put in " + list(putIn, lookup, true));
			}
			if (!tookOut.isEmpty())
			{
				lines.add("Took out " + list(tookOut, lookup, true));
			}
			for (String trip : trips)
			{
				lines.add(capital(trip));
			}
			for (Digest.Entry e : others)
			{
				// "Took the last of" an item they put back by the end of the day is no longer true.
				if (e.type == GroupEvent.Type.STORAGE_LAST && !anyTaken(e.items, tookOut))
				{
					continue;
				}
				lines.add(capital(e.line));
			}
			if (!lines.isEmpty())
			{
				s.members.put(m.getKey(), lines);
			}
		}
		List<String> totals = new ArrayList<>();
		if (netIn > 0)
		{
			totals.add("Net in " + gp(netIn));
		}
		if (netOut > 0)
		{
			totals.add("Net out " + gp(netOut));
		}
		if (hidden > 0)
		{
			totals.add(hidden + (hidden == 1 ? " round trip" : " round trips") + " not shown");
		}
		s.summary = String.join(" · ", totals);
		if (bestId >= 0)
		{
			s.thumbnail = String.format(ITEM_ICON, StackIcons.variant(bestId, bestQty));
		}
		return s;
	}

	private static void move(Map<Integer, Moved> moves, Map<Integer, Integer> items, boolean in)
	{
		for (Map.Entry<Integer, Integer> i : items.entrySet())
		{
			Moved mv = moves.get(i.getKey());
			if (mv == null)
			{
				mv = new Moved();
				mv.tookFirst = !in;
				moves.put(i.getKey(), mv);
			}
			if (in)
			{
				mv.in += i.getValue();
			}
			else
			{
				mv.out += i.getValue();
			}
		}
	}

	private static boolean anyTaken(Map<Integer, Integer> items, Map<Integer, Integer> tookOut)
	{
		for (Integer id : items.keySet())
		{
			if (tookOut.containsKey(id))
			{
				return true;
			}
		}
		return items.isEmpty();
	}

	/**
	 * Level-ups grouped per member and skill, from the level before the first to the last ("Magic 58 → 62 (+4)"),
	 * in the order each skill first levelled; every other progress line as it was. The skill that gained the most
	 * is the thumbnail.
	 */
	static Section progress(List<Digest.Entry> entries)
	{
		Section s = new Section(GroupEvent.Category.PROGRESS);
		long gained = 0;
		long bestGain = 0;
		String bestSkill = null;
		for (Map.Entry<String, List<Digest.Entry>> m : byMember(entries).entrySet())
		{
			Map<String, long[]> skills = new LinkedHashMap<>();
			List<String> others = new ArrayList<>();
			for (Digest.Entry e : m.getValue())
			{
				if (e.type == GroupEvent.Type.LEVEL_UP && !e.text.isEmpty() && e.number > 0)
				{
					long from = e.previous > 0 && e.previous < e.number ? e.previous : e.number - 1;
					long[] range = skills.get(e.text);
					if (range == null)
					{
						skills.put(e.text, new long[]{from, e.number});
					}
					else
					{
						range[0] = Math.min(range[0], from);
						range[1] = Math.max(range[1], e.number);
					}
				}
				else
				{
					others.add(capital(e.line));
				}
			}
			List<String> lines = new ArrayList<>();
			for (Map.Entry<String, long[]> k : skills.entrySet())
			{
				long from = k.getValue()[0];
				long to = k.getValue()[1];
				long gain = to - from;
				gained += gain;
				lines.add(k.getKey() + " " + from + " → " + to + (gain > 1 ? " (+" + gain + ")" : ""));
				if (gain > bestGain)
				{
					bestGain = gain;
					bestSkill = k.getKey();
				}
			}
			lines.addAll(others);
			if (!lines.isEmpty())
			{
				s.members.put(m.getKey(), lines);
			}
		}
		if (gained > 0)
		{
			s.summary = gained + (gained == 1 ? " level" : " levels") + " gained";
		}
		if (bestSkill != null)
		{
			s.thumbnail = String.format(SKILL_ICON, bestSkill.trim().replace(' ', '_'));
		}
		return s;
	}

	/**
	 * Every other section: each member's lines as they were, a request made listing all its items, the most valuable
	 * item as the thumbnail and the items' worth as the total.
	 */
	static Section lines(GroupEvent.Category category, List<Digest.Entry> entries, ItemLookup lookup)
	{
		Section s = new Section(category);
		long worth = 0;
		long bestValue = 0;
		int bestId = -1;
		long bestQty = 0;
		int count = 0;
		for (Map.Entry<String, List<Digest.Entry>> m : byMember(entries).entrySet())
		{
			List<String> lines = new ArrayList<>();
			for (Digest.Entry e : m.getValue())
			{
				boolean request = e.type == GroupEvent.Type.REQUEST && e.number == RequestBoard.KIND_MADE
					&& !e.items.isEmpty();
				if (request)
				{
					lines.add("Made a request, '" + e.text + "': " + list(e.items, lookup, false));
				}
				else if (!e.line.isEmpty())
				{
					lines.add(capital(e.line));
				}
				count++;
				if (request || e.type == GroupEvent.Type.REQUEST)
				{
					// What someone asks for is no one's worth.
					continue;
				}
				for (Map.Entry<Integer, Integer> i : e.items.entrySet())
				{
					long value = lookup.price(i.getKey()) * i.getValue();
					worth += value;
					if (value > bestValue)
					{
						bestValue = value;
						bestId = i.getKey();
						bestQty = i.getValue();
					}
				}
			}
			if (!lines.isEmpty())
			{
				s.members.put(m.getKey(), lines);
			}
		}
		s.summary = count + (count == 1 ? " event" : " events") + (worth > 0 ? " · " + gp(worth) : "");
		if (bestId >= 0)
		{
			s.thumbnail = String.format(ITEM_ICON, StackIcons.variant(bestId, bestQty));
		}
		return s;
	}

	/**
	 * "500 x Shark, 20 x Prayer potion(4) (110K gp)": whole items, the most valuable first when {@code byValue}
	 * (otherwise as given), at most {@link #LISTED}, then "+N more" and the whole list's total.
	 */
	static String list(Map<Integer, Integer> items, ItemLookup lookup, boolean byValue)
	{
		List<Map.Entry<Integer, Integer>> order = new ArrayList<>(items.entrySet());
		if (byValue)
		{
			order.sort((a, b) -> Long.compare(lookup.price(b.getKey()) * b.getValue(),
				lookup.price(a.getKey()) * a.getValue()));
		}
		List<String> parts = new ArrayList<>();
		long total = 0;
		long count = 0;
		for (Map.Entry<Integer, Integer> i : order)
		{
			total += lookup.price(i.getKey()) * i.getValue();
			count += i.getValue();
			if (parts.size() < LISTED)
			{
				parts.add(item(i.getKey(), i.getValue(), lookup));
			}
		}
		int rest = order.size() - parts.size();
		StringBuilder out = new StringBuilder(String.join(", ", parts));
		if (rest > 0)
		{
			out.append(" +").append(rest).append(" more (").append(Amounts.GAME.of(count)).append(" items")
				.append(total > 0 ? ", " + gp(total) : "").append(')');
		}
		else if (total > 0)
		{
			out.append(" (").append(gp(total)).append(')');
		}
		return out.toString();
	}

	private static String item(int id, long qty, ItemLookup lookup)
	{
		return Amounts.GAME.of(qty) + " x " + lookup.name(id);
	}

	private static String gp(long value)
	{
		return Amounts.GAME.of(value) + " gp";
	}

	private static int clampInt(long v)
	{
		return (int) Math.min(Integer.MAX_VALUE, v);
	}

	private static String capital(String line)
	{
		return line.isEmpty() ? line : Character.toUpperCase(line.charAt(0)) + line.substring(1);
	}

	private static String name(GroupEvent.Category c)
	{
		String n = c.name().toLowerCase(Locale.ROOT);
		return Character.toUpperCase(n.charAt(0)) + n.substring(1);
	}

	/** Each section's emoji, beside its name in the title. */
	private static String icon(GroupEvent.Category c)
	{
		switch (c)
		{
			case DROPS:
				return "💰";
			case DEATHS:
				return "💀";
			case STORAGE:
				return "📦";
			case PROGRESS:
				return "⭐";
			case GROUP:
				return "👥";
			default:
				return "⚔️";
		}
	}

	/** "Daily digest · Nov 23, 20:00 to Nov 24, 20:00 · next Nov 25, 20:00". */
	static String footer(long from, long to, long next, ZoneId zone)
	{
		return "Daily digest · " + when(from, zone) + " to " + when(to, zone) + " · next " + when(next, zone);
	}

	private static String when(long at, ZoneId zone)
	{
		return WHEN.format(Instant.ofEpochMilli(at).atZone(zone));
	}

	// ---- As Discord posts ----

	/**
	 * The digest's posts, in order, each a webhook body under {@code username} (null for the webhook's own) that
	 * mentions nobody. Empty when no section has anything to say.
	 */
	static List<String> payloads(Gson gson, List<Section> sections, DiscordStyle style, String username,
		String footer, long at)
	{
		if (sections.isEmpty())
		{
			return Collections.emptyList();
		}
		return style == DiscordStyle.PLAIN ? plain(gson, sections, username, footer)
			: embeds(gson, sections, username, footer, at);
	}

	private static List<String> embeds(Gson gson, List<Section> sections, String username, String footer, long at)
	{
		List<JsonObject> embeds = new ArrayList<>();
		List<Integer> sizes = new ArrayList<>();
		// Every embed leaves room for the footer, so any one of them fits a message with it.
		int budget = MESSAGE_CHARS - footer.length();
		for (Section s : sections)
		{
			JsonObject embed = start(s, true, at);
			JsonArray fields = new JsonArray();
			int size = size(embed);
			for (String[] f : fields(s))
			{
				int add = f[0].length() + f[1].length();
				if (fields.size() >= FIELDS || size + add > budget)
				{
					embed.add("fields", fields);
					embeds.add(embed);
					sizes.add(size);
					embed = start(s, false, at);
					fields = new JsonArray();
					size = size(embed);
				}
				fields.add(field(f[0], f[1]));
				size += add;
			}
			if (fields.size() > 0)
			{
				embed.add("fields", fields);
			}
			embeds.add(embed);
			sizes.add(size);
		}
		List<String> out = new ArrayList<>();
		JsonArray message = new JsonArray();
		int used = footer.length();
		for (int i = 0; i < embeds.size(); i++)
		{
			if (message.size() >= EMBEDS || message.size() > 0 && used + sizes.get(i) > MESSAGE_CHARS)
			{
				out.add(message(gson, message, username, footer));
				message = new JsonArray();
				used = footer.length();
			}
			message.add(embeds.get(i));
			used += sizes.get(i);
		}
		out.add(message(gson, message, username, footer));
		return out;
	}

	private static JsonObject start(Section s, boolean first, long at)
	{
		JsonObject embed = new JsonObject();
		embed.addProperty("title", s.title() + (first ? "" : CONTINUED));
		embed.addProperty("color", s.category.colour);
		embed.addProperty("timestamp", Instant.ofEpochMilli(at).toString());
		if (first && !s.summary.isEmpty())
		{
			embed.addProperty("description", s.summary);
		}
		if (first && s.thumbnail != null)
		{
			JsonObject thumb = new JsonObject();
			thumb.addProperty("url", s.thumbnail);
			embed.add("thumbnail", thumb);
		}
		return embed;
	}

	/** The characters Discord counts toward an embed's 6,000: title and description here; fields are added on. */
	private static int size(JsonObject embed)
	{
		int n = embed.get("title").getAsString().length();
		return embed.has("description") ? n + embed.get("description").getAsString().length() : n;
	}

	/** A message of {@code embeds}; the last carries the footer (the date range and the next digest). */
	private static String message(Gson gson, JsonArray embeds, String username, String footer)
	{
		JsonObject last = embeds.get(embeds.size() - 1).getAsJsonObject();
		JsonObject f = new JsonObject();
		f.addProperty("text", footer);
		last.add("footer", f);
		JsonObject o = DiscordWebhook.poster(username);
		DiscordWebhook.allowMentions(o, false);
		o.add("embeds", embeds);
		return gson.toJson(o);
	}

	/** Each member's lines as fields of at most {@link #FIELD_VALUE}, split between lines, a long line between words. */
	static List<String[]> fields(Section s)
	{
		List<String[]> out = new ArrayList<>();
		for (Map.Entry<String, List<String>> m : s.members.entrySet())
		{
			String name = m.getKey().isEmpty() ? "Earlier" : m.getKey();
			List<String> values = pack(m.getValue(), FIELD_VALUE);
			for (int i = 0; i < values.size(); i++)
			{
				out.add(new String[]{clip(i == 0 ? name : name + CONTINUED, FIELD_NAME), values.get(i)});
			}
		}
		return out;
	}

	private static JsonObject field(String name, String value)
	{
		JsonObject f = new JsonObject();
		f.addProperty("name", name);
		f.addProperty("value", value);
		f.addProperty("inline", false);
		return f;
	}

	/** Plain style: the same sections as text, a member's lines under the section's title, each post 2,000 at most. */
	private static List<String> plain(Gson gson, List<Section> sections, String username, String footer)
	{
		List<String> lines = new ArrayList<>();
		for (Section s : sections)
		{
			lines.add("**" + s.title() + "**" + (s.summary.isEmpty() ? "" : ": " + s.summary));
			for (Map.Entry<String, List<String>> m : s.members.entrySet())
			{
				for (String line : m.getValue())
				{
					lines.add(m.getKey().isEmpty() ? line : m.getKey() + ": " + line);
				}
			}
		}
		lines.add(footer);
		List<String> out = new ArrayList<>();
		for (String content : pack(lines, CONTENT))
		{
			JsonObject o = DiscordWebhook.poster(username);
			DiscordWebhook.allowMentions(o, false);
			o.addProperty("content", content);
			out.add(gson.toJson(o));
		}
		return out;
	}

	/** {@code lines} joined by newlines into chunks of at most {@code max}, breaking only between lines or words. */
	static List<String> pack(List<String> lines, int max)
	{
		List<String> out = new ArrayList<>();
		StringBuilder chunk = new StringBuilder();
		for (String line : lines)
		{
			for (String piece : wrap(line, max))
			{
				if (chunk.length() > 0 && chunk.length() + 1 + piece.length() > max)
				{
					out.add(chunk.toString());
					chunk.setLength(0);
				}
				chunk.append(chunk.length() == 0 ? "" : "\n").append(piece);
			}
		}
		if (chunk.length() > 0)
		{
			out.add(chunk.toString());
		}
		return out;
	}

	/**
	 * {@code text} in pieces of at most {@code max}, each break at a space. Only a single word longer than
	 * {@code max}, which no item or member name is, is ever split inside.
	 */
	static List<String> wrap(String text, int max)
	{
		List<String> out = new ArrayList<>();
		String rest = text.trim();
		while (rest.length() > max)
		{
			int cut = rest.lastIndexOf(' ', max);
			if (cut <= 0)
			{
				cut = max;
			}
			out.add(rest.substring(0, cut).trim());
			rest = rest.substring(cut).trim();
		}
		if (!rest.isEmpty())
		{
			out.add(rest);
		}
		return out;
	}

	private static String clip(String text, int max)
	{
		return text.length() <= max ? text : wrap(text, max).get(0);
	}
}
