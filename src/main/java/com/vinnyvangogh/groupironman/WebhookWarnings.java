package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tells the player, once, that a webhook field is set but is not a Discord
 * webhook link, rather than posting nothing without a word. The line names
 * the field, never its value: a webhook URL holds the token that posts to
 * the channel. Pure, so the once-only rule is tested.
 */
final class WebhookWarnings
{
	/** Field label to the bad value already warned about. In memory only, never shown. */
	private final Map<String, String> warned = new HashMap<>();

	/**
	 * The chat lines to show now, for each field (label to value) that is set
	 * and invalid and has not been warned about with this value. A field that
	 * is cleared or fixed is forgotten, so breaking it again warns again.
	 */
	synchronized List<String> check(Map<String, String> fields)
	{
		List<String> out = new ArrayList<>();
		for (Map.Entry<String, String> field : fields.entrySet())
		{
			String value = DiscordWebhook.clean(field.getValue());
			if (value.isEmpty() || DiscordWebhook.valid(value))
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
		return GroupIronmanPlugin.NAME + ": the " + label + " Discord webhook isn't a valid Discord webhook link";
	}

	/** Every webhook field, by the label the warning uses: "main", then each category's. */
	static Map<String, String> fields(GroupIronmanConfig config)
	{
		Map<String, String> out = new LinkedHashMap<>();
		out.put("main", config.discordWebhookUrl());
		for (Map.Entry<GroupEvent.Category, String> e : DiscordWebhook.overrides(config).entrySet())
		{
			out.put(e.getKey().name().toLowerCase(Locale.ROOT), e.getValue());
		}
		return out;
	}
}
