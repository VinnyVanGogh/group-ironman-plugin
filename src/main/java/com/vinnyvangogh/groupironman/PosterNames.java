package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The {@code username} a Discord post is sent with, from the 'Poster name' setting. Null means no
 * {@code username} key, so Discord shows the name and avatar set on the webhook itself.
 *
 * <p>Discord's Execute Webhook takes a username of 1 to 80 characters and answers 400 to the names in
 * {@link #refused}, so a longer name is cut to 80 and a refused one is never sent: the post goes out under the
 * webhook's own name (not the main custom name) and the player gets one chat line naming the field, never the
 * name. Pure, so the rules and the once-only warning are tested.
 */
final class PosterNames
{
	/** Discord's limit on a webhook username. */
	static final int MAX = 80;
	private static final String[] REFUSED = {"discord", "clyde", "@", "#", ":", "```"};
	private static final List<String> EXACT = Arrays.asList("everyone", "here");

	/** Field label to the refused name already warned about. In memory only, never shown or logged. */
	private final Map<String, String> warned = new HashMap<>();

	/**
	 * The name for a post in {@code category}. Custom: the category's own name when set; otherwise, when the post
	 * goes to the main webhook, the main name; otherwise none, so a category's own webhook keeps its own name.
	 */
	static String name(DiscordPosterName mode, GroupEvent.Category category, Map<GroupEvent.Category, String> urls,
		String mainUrl, Map<GroupEvent.Category, String> names, String mainName)
	{
		if (mode == DiscordPosterName.GIM_UTILITIES)
		{
			return DiscordWebhook.USERNAME;
		}
		if (mode != DiscordPosterName.CUSTOM)
		{
			return null;
		}
		String own = clean(names.get(category));
		if (!own.isEmpty())
		{
			// A refused own name is the webhook's own, as the warning says: never the main custom name.
			return refused(own) ? null : own;
		}
		boolean onMain = !DiscordWebhook.valid(urls.get(category)) && DiscordWebhook.valid(mainUrl);
		return onMain ? usable(mainName) : null;
	}

	/** The name as sent: null when blank or refused. */
	private static String usable(String name)
	{
		String s = clean(name);
		return s.isEmpty() || refused(s) ? null : s;
	}

	/** Trimmed and cut to {@link #MAX} characters, never through a surrogate pair. Empty for null. */
	static String clean(String name)
	{
		String s = name == null ? "" : name.trim();
		if (s.length() > MAX)
		{
			int end = Character.isHighSurrogate(s.charAt(MAX - 1)) ? MAX - 1 : MAX;
			s = s.substring(0, end).trim();
		}
		return s;
	}

	/**
	 * Discord answers 400 to a webhook username containing "discord", "clyde", '@', '#', ':' or "```", or one
	 * that is exactly "everyone" or "here" (any case, checked after {@link #clean}).
	 */
	static boolean refused(String name)
	{
		String lower = name.toLowerCase(Locale.ROOT);
		if (EXACT.contains(lower))
		{
			return true;
		}
		for (String part : REFUSED)
		{
			if (lower.contains(part))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The chat lines to show now: one per field (label to name) whose name Discord would refuse, once per name.
	 * Only with Custom picked, the only time the names are used. A field that is cleared or fixed is forgotten,
	 * so breaking it again warns again.
	 */
	synchronized List<String> check(DiscordPosterName mode, Map<String, String> fields)
	{
		List<String> out = new ArrayList<>();
		for (Map.Entry<String, String> field : fields.entrySet())
		{
			String value = clean(field.getValue());
			if (mode != DiscordPosterName.CUSTOM || !refused(value))
			{
				warned.remove(field.getKey());
			}
			else if (!value.equals(warned.put(field.getKey(), value)))
			{
				out.add(message(field.getKey()));
			}
		}
		return out;
	}

	static String message(String label)
	{
		return GroupIronmanPlugin.NAME + ": the " + label + " Discord custom name can't contain 'discord', 'clyde',"
			+ " @, #, : or ```, or be 'everyone' or 'here'; posts use the webhook's own name instead";
	}

	/** Each category's custom name field. */
	static Map<GroupEvent.Category, String> names(GroupIronmanConfig config)
	{
		Map<GroupEvent.Category, String> out = new EnumMap<>(GroupEvent.Category.class);
		out.put(GroupEvent.Category.DROPS, config.discordNameDrops());
		out.put(GroupEvent.Category.DEATHS, config.discordNameDeaths());
		out.put(GroupEvent.Category.STORAGE, config.discordNameStorage());
		out.put(GroupEvent.Category.PROGRESS, config.discordNameProgress());
		out.put(GroupEvent.Category.GROUP, config.discordNameGroup());
		out.put(GroupEvent.Category.ACTIVITIES, config.discordNameActivities());
		return out;
	}

	/** Every custom name field, by the label the warning uses: "main", then each category's. */
	static Map<String, String> fields(GroupIronmanConfig config)
	{
		Map<String, String> out = new LinkedHashMap<>();
		out.put("main", config.discordNameMain());
		for (Map.Entry<GroupEvent.Category, String> e : names(config).entrySet())
		{
			out.put(e.getKey().name().toLowerCase(Locale.ROOT), e.getValue());
		}
		return out;
	}

	/** The name for a post in {@code category}, read from the settings. */
	static String name(GroupIronmanConfig config, GroupEvent.Category category)
	{
		return name(config.discordPosterName(), category, DiscordWebhook.overrides(config), config.discordWebhookUrl(),
			names(config), config.discordNameMain());
	}
}
