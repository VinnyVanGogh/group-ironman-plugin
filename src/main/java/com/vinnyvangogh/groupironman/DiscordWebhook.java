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
 * pajlads/DinkPlugin {@code ItemUtils.ITEM_CACHE_BASE_URL}).
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

	private DiscordWebhook()
	{
	}

	static boolean valid(String url)
	{
		return url != null && URL.matcher(url.trim()).matches();
	}

	/** The category's own webhook when it is set and valid, else the main one, else null: nothing is posted. */
	static String route(GroupEvent.Category category, Map<GroupEvent.Category, String> overrides, String main)
	{
		String own = overrides.get(category);
		if (valid(own))
		{
			return own.trim();
		}
		return valid(main) ? main.trim() : null;
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
	static String payload(Gson gson, GroupEvent e, ItemLookup lookup, DiscordStyle style, String mention,
		boolean screenshot)
	{
		JsonObject o = new JsonObject();
		o.addProperty("username", USERNAME);
		String tag = e.isBig() && mention != null && !mention.trim().isEmpty() ? mention.trim() : "";
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
			o.addProperty("content", (tag.isEmpty() ? "" : tag + " ") + EventText.sentence(e, lookup));
			return gson.toJson(o);
		}
		if (!tag.isEmpty())
		{
			o.addProperty("content", tag);
		}
		JsonArray embeds = new JsonArray();
		embeds.add(embed(e, lookup, screenshot));
		o.add("embeds", embeds);
		return gson.toJson(o);
	}

	private static JsonObject embed(GroupEvent e, ItemLookup lookup, boolean screenshot)
	{
		JsonObject embed = new JsonObject();
		embed.addProperty("title", e.member + ": " + EventText.title(e.type));
		embed.addProperty("description", EventText.describe(e, lookup));
		embed.addProperty("color", e.type.category.colour);
		embed.addProperty("timestamp", Instant.ofEpochMilli(e.at).toString());
		JsonArray fields = new JsonArray();
		long value = EventText.value(e.items, lookup);
		if (value > 0)
		{
			fields.add(field("Value", BoostText.gp(value) + " gp"));
		}
		if (e.world > 0)
		{
			fields.add(field("World", Integer.toString(e.world)));
		}
		if (!e.location.isEmpty())
		{
			fields.add(field("Where", e.location));
		}
		if (fields.size() > 0)
		{
			embed.add("fields", fields);
		}
		Integer icon = e.items.isEmpty() ? null : mostValuable(e.items, lookup);
		if (icon != null)
		{
			JsonObject thumb = new JsonObject();
			thumb.addProperty("url", String.format(ICON, icon));
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

	private static JsonObject field(String name, String value)
	{
		JsonObject f = new JsonObject();
		f.addProperty("name", name);
		f.addProperty("value", value);
		f.addProperty("inline", true);
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
