package com.vinnyvangogh.groupironman;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A snapshot of the seven webhooks' settings (URL, poster-name choice, custom name). Discord Relay owns the
 * webhooks and does every post; GIM keeps this only for the group: the leader's "Share webhooks with the group"
 * signs Discord Relay's own webhooks into a {@link GroupWebhooks} document, and the live board's "who keeps it"
 * rule reads where the Online status posts would go ({@link BoardLines#keeps}).
 *
 * <ul>
 * <li>URL: the webhook's own when set and valid, else Main's, else none.</li>
 * <li>A member's own webhook wins over the leader's shared one for that webhook ({@link #withGroup}).</li>
 * </ul>
 */
final class Webhooks
{
	/** Discord's limit on a webhook username. */
	static final int MAX_NAME = 80;
	/** discord.com (and the old discordapp.com, and the ptb/canary clients' hosts), /api/webhooks/id/token. */
	private static final Pattern URL = Pattern.compile(
		"^https://(?:(?:ptb|canary)\\.)?discord(?:app)?\\.com/api/webhooks/\\d+/[A-Za-z0-9_-]+/?$");
	/** Quotes a pasted link may come wrapped in: straight, backtick and curly. */
	private static final String QUOTES = "\"'`“”‘’";

	/** One webhook's three settings, as typed. */
	static final class Setting
	{
		final String url;
		final DiscordPosterName mode;
		final String name;

		Setting(String url, DiscordPosterName mode, String name)
		{
			this.url = url == null ? "" : url;
			this.mode = mode == null ? DiscordPosterName.WEBHOOK : mode;
			this.name = name == null ? "" : name;
		}
	}

	/** No webhooks at all: Discord Relay not installed, or nothing set. */
	static final Webhooks NONE = new Webhooks(Collections.<Webhook, Setting>emptyMap());

	private static final Setting UNSET = new Setting("", DiscordPosterName.WEBHOOK, "");

	private final Map<Webhook, Setting> settings;
	/** The webhooks whose setting came from the leader's shared ones ({@link #withGroup}), not this member's. */
	private final Set<Webhook> fromGroup;

	Webhooks(Map<Webhook, Setting> settings)
	{
		this(settings, EnumSet.noneOf(Webhook.class));
	}

	private Webhooks(Map<Webhook, Setting> settings, Set<Webhook> fromGroup)
	{
		this.settings = new EnumMap<>(Webhook.class);
		this.settings.putAll(settings);
		this.fromGroup = fromGroup.isEmpty() ? EnumSet.noneOf(Webhook.class) : EnumSet.copyOf(fromGroup);
	}

	static boolean valid(String url)
	{
		return URL.matcher(clean(url)).matches();
	}

	/** The field as typed, without surrounding whitespace or quotes. Empty for null. */
	static String clean(String url)
	{
		String s = url == null ? "" : url.trim();
		while (s.length() >= 2 && QUOTES.indexOf(s.charAt(0)) >= 0 && QUOTES.indexOf(s.charAt(s.length() - 1)) >= 0)
		{
			s = s.substring(1, s.length() - 1).trim();
		}
		return s;
	}

	/** A poster name trimmed and cut to {@link #MAX_NAME} characters, never through a surrogate pair. */
	static String cleanName(String name)
	{
		String s = name == null ? "" : name.trim();
		if (s.length() > MAX_NAME)
		{
			int end = Character.isHighSurrogate(s.charAt(MAX_NAME - 1)) ? MAX_NAME - 1 : MAX_NAME;
			s = s.substring(0, end).trim();
		}
		return s;
	}

	/**
	 * These (a member's own) with the leader's shared webhooks filling in: per webhook, the member's own setting
	 * when its URL field is not empty, else the group's. Unchanged when the group shares none.
	 */
	Webhooks withGroup(GroupWebhooks group)
	{
		if (group == null || !group.shared)
		{
			return this;
		}
		Map<Webhook, Setting> merged = new EnumMap<>(settings);
		Set<Webhook> borrowed = EnumSet.noneOf(Webhook.class);
		for (Map.Entry<Webhook, Setting> e : group.settings.entrySet())
		{
			if (clean(setting(e.getKey()).url).isEmpty())
			{
				merged.put(e.getKey(), e.getValue());
				borrowed.add(e.getKey());
			}
		}
		return new Webhooks(merged, borrowed);
	}

	/** True when {@link #url}{@code (webhook)} is the leader's shared URL, not one this member typed. */
	boolean fromGroup(Webhook webhook)
	{
		Webhook used = valid(setting(webhook).url) ? webhook : Webhook.MAIN;
		return fromGroup.contains(used) && url(webhook) != null;
	}

	private Setting setting(Webhook webhook)
	{
		return settings.getOrDefault(webhook, UNSET);
	}

	Map<Webhook, Setting> settings()
	{
		return Collections.unmodifiableMap(settings);
	}

	/** Where {@code webhook}'s posts go: its own URL, else Main's; null when neither is a Discord webhook. */
	String url(Webhook webhook)
	{
		String own = setting(webhook).url;
		if (valid(own))
		{
			return clean(own);
		}
		String main = setting(Webhook.MAIN).url;
		return valid(main) ? clean(main) : null;
	}

	/** At least one webhook to post to. */
	boolean any()
	{
		for (Webhook w : Webhook.values())
		{
			if (url(w) != null)
			{
				return true;
			}
		}
		return false;
	}
}
