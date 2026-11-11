package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/**
 * The settings a group shares: thresholds, which events post to Discord and
 * show as overlays, the daily digest, Easter eggs, quiet hours and the
 * offline grace. Each is one number (switches are 0 or 1) under a key, and
 * each knows how to read its value from a member's own config, which is how
 * a group's first settings are seeded from the leader's (and what a member
 * uses while their group has none).
 *
 * <p><b>What is not here, on purpose.</b> Webhook URLs, the mention, "Post to
 * Discord" itself and every "Share ..." switch stay each member's own: they
 * are where a member's posts go and what of theirs leaves their client, and a
 * webhook is a secret. {@code GroupSettingsSchemaTest} checks no key here
 * names one.
 */
final class GroupSettingsSchema
{
	static final String THRESHOLDS = "What counts";
	static final String DISCORD = "Discord: which events";
	static final String OVERLAY = "Overlays: which events";
	static final String DIGEST = "Daily digest";
	static final String TIMING = "Discord timing and extras";

	/** One shared setting. */
	static final class Setting
	{
		final String key;
		final String label;
		final String section;
		/** A switch, 0 or 1. */
		final boolean flag;
		final long min;
		final long max;
		final ToLongFunction<GroupIronmanConfig> seed;

		private Setting(String key, String label, String section, boolean flag, long min, long max,
			ToLongFunction<GroupIronmanConfig> seed)
		{
			this.key = key;
			this.label = label;
			this.section = section;
			this.flag = flag;
			this.min = min;
			this.max = max;
			this.seed = seed;
		}

		long clamp(long value)
		{
			return Math.max(min, Math.min(max, value));
		}
	}

	private static final long GP_MAX = Integer.MAX_VALUE;
	private static final Map<String, Setting> BY_KEY = new LinkedHashMap<>();
	/** In the order the panel lists them. */
	static final List<Setting> ALL;

	static
	{
		number("eventDropMinValue", "Valuable drop at least (gp)", 0, GP_MAX, GroupIronmanConfig::eventDropMinValue);
		number("eventStorageMinValue", "Big storage move at least (gp)", 0, GP_MAX,
			GroupIronmanConfig::eventStorageMinValue);
		number("eventLevelMin", "Level ups from level", 1, 99, GroupIronmanConfig::eventLevelMin);
		number("eventLevelEvery", "Post every N levels", 1, 98, GroupIronmanConfig::eventLevelEvery);
		number("eventBackAfterDays", "Back after (days)", 1, 365, GroupIronmanConfig::eventBackAfterDays);
		number("eventClueMinValue", "Clue reward at least (gp)", 0, GP_MAX, GroupIronmanConfig::eventClueMinValue);
		number("eventKcEvery", "Kill count milestone every", 0, 100_000, GroupIronmanConfig::eventKcEvery);
		number("eventGravestoneWarnMinutes", "Gravestone warning (minutes left)", 1, 14,
			GroupIronmanConfig::eventGravestoneWarnMinutes);

		for (GroupEvent.Type t : GroupEvent.Type.values())
		{
			flag(discordKey(t), EventText.title(t), DISCORD, EventSettings.DISCORD.get(t));
		}
		for (GroupEvent.Type t : GroupEvent.Type.values())
		{
			flag(overlayKey(t), EventText.title(t), OVERLAY, EventSettings.OVERLAY.get(t));
		}
		for (GroupEvent.Category c : GroupEvent.Category.values())
		{
			flag(digestKey(c), title(c.name()), DIGEST, EventSettings.DIGEST.get(c));
		}

		timing("discordDigestHour", "Digest hour", 0, 23, GroupIronmanConfig::discordDigestHour);
		timing("discordQuietStart", "Quiet hours from", 0, 23, GroupIronmanConfig::discordQuietStart);
		timing("discordQuietEnd", "Quiet hours until", 0, 23, GroupIronmanConfig::discordQuietEnd);
		timing("discordOfflineGrace", "Offline grace (minutes)", 0, 10, GroupIronmanConfig::discordOfflineGrace);
		flag("discordEasterEggs", "Easter eggs", TIMING, GroupIronmanConfig::discordEasterEggs);
		timing("discordLegendAt", "Legend at (gp)", 0, GP_MAX, GroupIronmanConfig::discordLegendAt);
		timing("discordFreeloaderAt", "Freeloader at (gp)", 0, GP_MAX, GroupIronmanConfig::discordFreeloaderAt);

		ALL = Collections.unmodifiableList(new ArrayList<>(BY_KEY.values()));
	}

	private GroupSettingsSchema()
	{
	}

	private static void number(String key, String label, long min, long max, ToLongFunction<GroupIronmanConfig> seed)
	{
		add(new Setting(key, label, THRESHOLDS, false, min, max, seed));
	}

	private static void timing(String key, String label, long min, long max, ToLongFunction<GroupIronmanConfig> seed)
	{
		add(new Setting(key, label, TIMING, false, min, max, seed));
	}

	private static void flag(String key, String label, String section, Predicate<GroupIronmanConfig> seed)
	{
		add(new Setting(key, label, section, true, 0, 1, c -> seed.test(c) ? 1 : 0));
	}

	private static void add(Setting s)
	{
		if (BY_KEY.put(s.key, s) != null)
		{
			throw new IllegalStateException("duplicate group setting " + s.key);
		}
	}

	private static String title(String enumName)
	{
		String lower = enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	static String discordKey(GroupEvent.Type type)
	{
		return "discord." + type.code;
	}

	static String overlayKey(GroupEvent.Type type)
	{
		return "overlay." + type.code;
	}

	static String digestKey(GroupEvent.Category category)
	{
		return "digest." + category.name().toLowerCase(Locale.ROOT);
	}

	/** Null for a key this build does not know. */
	static Setting get(String key)
	{
		return BY_KEY.get(key);
	}

	/** Every group setting as {@code config} has it: what the first save publishes. */
	static Map<String, Long> seed(GroupIronmanConfig config)
	{
		Map<String, Long> out = new TreeMap<>();
		for (Setting s : ALL)
		{
			out.put(s.key, s.clamp(s.seed.applyAsLong(config)));
		}
		return out;
	}

	/** Known keys only, each clamped to its range: what a received document is reduced to. */
	static Map<String, Long> sanitize(Map<String, Long> values)
	{
		Map<String, Long> out = new TreeMap<>();
		for (Map.Entry<String, Long> e : values.entrySet())
		{
			Setting s = BY_KEY.get(e.getKey());
			if (s != null && e.getValue() != null)
			{
				out.put(s.key, s.clamp(e.getValue()));
			}
		}
		return out;
	}

	/** The value of each group setting now: the group's when it has settings, else this member's own config. */
	static final class Resolved
	{
		private final GroupIronmanConfig config;
		private final Map<String, Long> group;

		Resolved(GroupIronmanConfig config, GroupSettings settings)
		{
			this.config = config;
			this.group = settings == null ? Collections.emptyMap() : settings.values;
		}

		long value(String key)
		{
			Setting s = BY_KEY.get(key);
			if (s == null)
			{
				throw new IllegalArgumentException("not a group setting: " + key);
			}
			Long v = group.get(key);
			return v != null ? s.clamp(v) : s.clamp(s.seed.applyAsLong(config));
		}

		int intValue(String key)
		{
			return (int) Math.min(Integer.MAX_VALUE, value(key));
		}

		boolean on(String key)
		{
			return value(key) != 0;
		}
	}
}
