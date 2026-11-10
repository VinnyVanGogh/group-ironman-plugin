package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * What a Discord webhook post says, and where it goes. Pure: building the
 * JSON needs no network, so its shape is tested.
 *
 * <p>Payload fields follow Discord's "Execute Webhook" endpoint
 * ({@code content}, {@code username}, {@code embeds}, {@code allowed_mentions};
 * a file goes in a multipart form beside {@code payload_json}). Item icons are
 * RuneLite's static item cache, the URL Dink uses
 * ({@code https://static.runelite.net/cache/item/icon/<id>.png},
 * pajlads/DinkPlugin {@code ItemUtils.ITEM_CACHE_BASE_URL}), at the stack's own
 * pile ({@link StackIcons}). Amounts read "450K (450,001)" ({@link Amounts#BOTH}).
 */
final class DiscordWebhook
{
	/** The third-party-server warning AGENTS.md asks for on a setting that sends anything off the client. */
	static final String WARNING = "This feature submits your IP address to a 3rd-party server not controlled or"
		+ " verified by RuneLite developers";
	static final String USERNAME = "GIM Utilities";
	static final String SCREENSHOT = "screenshot.png";
	/** Discord refuses {@code content} over 2,000 characters; lines past this wait out the day and are dropped. */
	static final int MAX_DIGEST = 1_900;
	private static final String ICON = "https://static.runelite.net/cache/item/icon/%d.png";
	/** discord.com (and the old discordapp.com, and the ptb/canary clients' hosts), /api/webhooks/id/token. */
	private static final Pattern URL = Pattern.compile(
		"^https://(?:(?:ptb|canary)\\.)?discord(?:app)?\\.com/api/webhooks/\\d+/[A-Za-z0-9_-]+/?$");

	/** Quotes a pasted link may come wrapped in: straight, backtick and curly. */
	private static final String QUOTES = "\"'`“”‘’";

	private DiscordWebhook()
	{
	}

	static boolean valid(String url)
	{
		return URL.matcher(clean(url)).matches();
	}

	/**
	 * The field as typed, without surrounding whitespace or quotes: a link
	 * pasted with its quotes, straight or curly, is the common mistake. Empty
	 * for null.
	 */
	static String clean(String url)
	{
		String s = url == null ? "" : url.trim();
		while (s.length() >= 2 && QUOTES.indexOf(s.charAt(0)) >= 0 && QUOTES.indexOf(s.charAt(s.length() - 1)) >= 0)
		{
			s = s.substring(1, s.length() - 1).trim();
		}
		return s;
	}

	/** The category's own webhook when it is set and valid, else the main one, else null: nothing is posted. */
	static String route(GroupEvent.Category category, Map<GroupEvent.Category, String> overrides, String main)
	{
		String own = overrides.get(category);
		if (valid(own))
		{
			return clean(own);
		}
		return valid(main) ? clean(main) : null;
	}

	static Map<GroupEvent.Category, String> overrides(GroupIronmanConfig config)
	{
		Map<GroupEvent.Category, String> out = new EnumMap<>(GroupEvent.Category.class);
		out.put(GroupEvent.Category.DROPS, config.discordDropsUrl());
		out.put(GroupEvent.Category.DEATHS, config.discordDeathsUrl());
		out.put(GroupEvent.Category.STORAGE, config.discordStorageUrl());
		out.put(GroupEvent.Category.PROGRESS, config.discordProgressUrl());
		out.put(GroupEvent.Category.GROUP, config.discordGroupUrl());
		out.put(GroupEvent.Category.ACTIVITIES, config.discordActivitiesUrl());
		return out;
	}

	/** One day's held lines for a category as a plain post; null when there are none. Mentions nobody. */
	static String digest(Gson gson, GroupEvent.Category category, List<String> lines)
	{
		if (lines.isEmpty())
		{
			return null;
		}
		StringBuilder text = new StringBuilder("Daily digest, ").append(category.name().toLowerCase(Locale.ROOT));
		for (String line : lines)
		{
			if (text.length() + line.length() + 1 > MAX_DIGEST)
			{
				break;
			}
			text.append('\n').append(line);
		}
		JsonObject o = new JsonObject();
		o.addProperty("username", USERNAME);
		o.addProperty("content", text.toString());
		JsonObject allowed = new JsonObject();
		allowed.add("parse", new JsonArray());
		o.add("allowed_mentions", allowed);
		return gson.toJson(o);
	}

	/** The JSON body ({@code payload_json} when a screenshot is attached). */
	static String payload(Gson gson, GroupEvent e, ItemLookup items, DiscordStyle style, String mention,
		boolean screenshot)
	{
		// "450K (450,001)": the game's words, and nothing lost (Board 2026-11-10).
		ItemLookup lookup = Amounts.BOTH.words(items);
		return payload(gson, DiscordMerge.single(e, lookup), lookup, style, mention, screenshot);
	}

	/**
	 * One moment's post: its lead event's title and line, the other events' lines as fields. {@code lookup}
	 * already words amounts the Discord way ({@link Amounts#BOTH}), as the post's text was built with it.
	 */
	static String payload(Gson gson, DiscordMerge.Post post, ItemLookup lookup, DiscordStyle style, String mention,
		boolean screenshot)
	{
		JsonObject o = new JsonObject();
		o.addProperty("username", USERNAME);
		String tag = post.big && mention != null && !mention.trim().isEmpty() ? mention.trim() : "";
		// Only the configured mention may ping; a member or item name can never.
		JsonObject allowed = new JsonObject();
		JsonArray parse = new JsonArray();
		if (!tag.isEmpty())
		{
			parse.add("roles");
			parse.add("users");
		}
		allowed.add("parse", parse);
		o.add("allowed_mentions", allowed);
		if (style == DiscordStyle.PLAIN)
		{
			o.addProperty("content", (tag.isEmpty() ? "" : tag + " ") + plain(post));
			return gson.toJson(o);
		}
		if (!tag.isEmpty())
		{
			o.addProperty("content", tag);
		}
		JsonArray embeds = new JsonArray();
		embeds.add(embed(post, lookup, screenshot));
		o.add("embeds", embeds);
		return gson.toJson(o);
	}

	/** "Alex: died; lost about 1.5M gp on death": the lead's sentence, then each merged fact. */
	private static String plain(DiscordMerge.Post post)
	{
		StringBuilder text = new StringBuilder(post.lead.member).append(": ").append(post.description);
		for (String[] extra : post.extras)
		{
			text.append("; ").append(extra[1]);
		}
		return text.toString();
	}

	private static JsonObject embed(DiscordMerge.Post post, ItemLookup lookup, boolean screenshot)
	{
		GroupEvent e = post.lead;
		JsonObject embed = new JsonObject();
		embed.addProperty("title", e.member + ": " + post.title);
		embed.addProperty("description", post.description);
		embed.addProperty("color", e.type.category.colour);
		embed.addProperty("timestamp", Instant.ofEpochMilli(e.at).toString());
		JsonArray fields = fields(post, lookup);
		if (fields.size() > 0)
		{
			embed.add("fields", fields);
		}
		Integer icon = post.items.isEmpty() ? null : mostValuable(post.items, lookup);
		if (icon != null)
		{
			JsonObject thumb = new JsonObject();
			thumb.addProperty("url", String.format(ICON, StackIcons.variant(icon, post.items.get(icon))));
			embed.add("thumbnail", thumb);
		}
		if (screenshot)
		{
			JsonObject image = new JsonObject();
			image.addProperty("url", "attachment://" + SCREENSHOT);
			embed.add("image", image);
		}
		JsonObject footer = new JsonObject();
		footer.addProperty("text", USERNAME);
		embed.add("footer", footer);
		return embed;
	}

	/** Value, World and Where (inline), then one full-width field per merged fact. */
	private static JsonArray fields(DiscordMerge.Post post, ItemLookup lookup)
	{
		GroupEvent e = post.lead;
		JsonArray fields = new JsonArray();
		long value = EventText.value(post.items, lookup);
		if (value > 0)
		{
			fields.add(field("Value", lookup.amount(value) + " gp", true));
		}
		if (e.world > 0)
		{
			fields.add(field("World", Integer.toString(e.world), true));
		}
		if (!e.location.isEmpty())
		{
			fields.add(field("Where", e.location, true));
		}
		for (String[] extra : post.extras)
		{
			fields.add(field(extra[0], extra[1], false));
		}
		return fields;
	}

	private static JsonObject field(String name, String value, boolean inline)
	{
		JsonObject f = new JsonObject();
		f.addProperty("name", name);
		f.addProperty("value", value);
		f.addProperty("inline", inline);
		return f;
	}

	private static Integer mostValuable(Map<Integer, Integer> items, ItemLookup lookup)
	{
		Integer best = null;
		long bestValue = -1;
		for (Map.Entry<Integer, Integer> i : items.entrySet())
		{
			long v = lookup.price(i.getKey()) * i.getValue();
			if (v > bestValue)
			{
				best = i.getKey();
				bestValue = v;
			}
		}
		return best;
	}
}
