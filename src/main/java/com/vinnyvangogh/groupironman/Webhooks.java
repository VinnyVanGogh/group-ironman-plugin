package com.vinnyvangogh.groupironman;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The one place a post's webhook URL and poster name are decided. Every sender asks this, never a webhook setting
 * directly. Pure over a snapshot of the settings, so the rules are tested.
 *
 * <ul>
 * <li>URL: the webhook's own when set and valid, else Main's, else none (nothing is posted).</li>
 * <li>Name: the webhook's own choice, also when its URL fell back to Main's. Webhook's own sends no
 * {@code username}; Default name sends {@link Webhook#defaultName}; Custom sends the box, or the default name
 * when the box is blank or a name Discord refuses ({@link PosterNames#refused}).</li>
 * </ul>
 */
final class Webhooks
{
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

	private static final Setting UNSET = new Setting("", DiscordPosterName.WEBHOOK, "");

	private final Map<Webhook, Setting> settings;

	Webhooks(Map<Webhook, Setting> settings)
	{
		this.settings = new EnumMap<>(Webhook.class);
		this.settings.putAll(settings);
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
		if (DiscordWebhook.valid(own))
		{
			return DiscordWebhook.clean(own);
		}
		String main = setting(Webhook.MAIN).url;
		return DiscordWebhook.valid(main) ? DiscordWebhook.clean(main) : null;
	}

	/** The {@code username} {@code webhook}'s posts go out under; null sends none, so the webhook's own shows. */
	String name(Webhook webhook)
	{
		Setting s = setting(webhook);
		switch (s.mode)
		{
			case CUSTOM:
				String custom = PosterNames.clean(s.name);
				return custom.isEmpty() || PosterNames.refused(custom) ? webhook.defaultName : custom;
			case GIM_UTILITIES:
				return webhook.defaultName;
			default:
				return null;
		}
	}

	/** Every URL field, by the label its warning uses, in settings order. */
	Map<String, String> urlFields()
	{
		Map<String, String> out = new LinkedHashMap<>();
		for (Webhook w : Webhook.values())
		{
			out.put(w.label, setting(w).url);
		}
		return out;
	}

	/** Every Custom box by label; blank for a webhook not on Custom, whose box is unused and so never warns. */
	Map<String, String> customNames()
	{
		Map<String, String> out = new LinkedHashMap<>();
		for (Webhook w : Webhook.values())
		{
			Setting s = setting(w);
			out.put(w.label, s.mode == DiscordPosterName.CUSTOM ? s.name : "");
		}
		return out;
	}

	/** The seven webhooks' settings. The only reader of the webhook and poster-name keys. */
	static Webhooks of(GroupIronmanConfig config)
	{
		Map<Webhook, Setting> s = new EnumMap<>(Webhook.class);
		s.put(Webhook.MAIN, new Setting(config.discordWebhookUrl(), config.discordPosterName(),
			config.discordNameMain()));
		s.put(Webhook.ONLINE, new Setting(config.discordGroupUrl(), config.discordPosterOnline(),
			config.discordNameGroup()));
		s.put(Webhook.DROPS, new Setting(config.discordDropsUrl(), config.discordPosterDrops(),
			config.discordNameDrops()));
		s.put(Webhook.PROGRESS, new Setting(config.discordProgressUrl(), config.discordPosterProgress(),
			config.discordNameProgress()));
		s.put(Webhook.DEATHS, new Setting(config.discordDeathsUrl(), config.discordPosterDeaths(),
			config.discordNameDeaths()));
		s.put(Webhook.STORAGE, new Setting(config.discordStorageUrl(), config.discordPosterStorage(),
			config.discordNameStorage()));
		s.put(Webhook.DIGEST, new Setting(config.discordDigestUrl(), config.discordPosterDigest(),
			config.discordNameDigest()));
		return new Webhooks(s);
	}
}
