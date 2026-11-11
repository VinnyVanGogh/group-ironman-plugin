package com.vinnyvangogh.groupironman;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The event settings at one moment: which kinds each output shows, and the
 * thresholds. Read from the config once per change, so the event path never
 * touches the config proxy, and built by hand in tests.
 *
 * <p>The per-kind switches are listed once here, as method references, so a
 * new {@link GroupEvent.Type} without its two switches fails
 * {@code EventSettingsTest} rather than staying silently off.
 */
final class EventSettings
{
	static final Map<GroupEvent.Type, Predicate<GroupIronmanConfig>> DISCORD = new EnumMap<>(GroupEvent.Type.class);
	static final Map<GroupEvent.Type, Predicate<GroupIronmanConfig>> OVERLAY = new EnumMap<>(GroupEvent.Type.class);
	static final Map<GroupEvent.Category, Predicate<GroupIronmanConfig>> DIGEST =
		new EnumMap<>(GroupEvent.Category.class);

	static
	{
		both(GroupEvent.Type.DROP, GroupIronmanConfig::discordValuableDrop, GroupIronmanConfig::overlayValuableDrop);
		both(GroupEvent.Type.PET, GroupIronmanConfig::discordPet, GroupIronmanConfig::overlayPet);
		both(GroupEvent.Type.CLOG, GroupIronmanConfig::discordCollectionLog, GroupIronmanConfig::overlayCollectionLog);
		both(GroupEvent.Type.UNIQUE, GroupIronmanConfig::discordUntradeableUnique,
			GroupIronmanConfig::overlayUntradeableUnique);
		both(GroupEvent.Type.DEATH, GroupIronmanConfig::discordDeath, GroupIronmanConfig::overlayDeath);
		both(GroupEvent.Type.STORAGE_DEPOSIT, GroupIronmanConfig::discordStorageDeposit,
			GroupIronmanConfig::overlayStorageDeposit);
		both(GroupEvent.Type.STORAGE_WITHDRAWAL, GroupIronmanConfig::discordStorageWithdrawal,
			GroupIronmanConfig::overlayStorageWithdrawal);
		both(GroupEvent.Type.STORAGE_SUMMARY, GroupIronmanConfig::discordStorageSummary,
			GroupIronmanConfig::overlayStorageSummary);
		both(GroupEvent.Type.STORAGE_MINIMUM, GroupIronmanConfig::discordStorageMinimum,
			GroupIronmanConfig::overlayStorageMinimum);
		both(GroupEvent.Type.STORAGE_LAST, GroupIronmanConfig::discordStorageLast, GroupIronmanConfig::overlayStorageLast);
		both(GroupEvent.Type.LEVEL_UP, GroupIronmanConfig::discordLevelUp, GroupIronmanConfig::overlayLevelUp);
		both(GroupEvent.Type.MAXED, GroupIronmanConfig::discordMaxed, GroupIronmanConfig::overlayMaxed);
		both(GroupEvent.Type.XP_MILESTONE, GroupIronmanConfig::discordXpMilestone, GroupIronmanConfig::overlayXpMilestone);
		both(GroupEvent.Type.COMBAT_LEVEL, GroupIronmanConfig::discordCombatLevel, GroupIronmanConfig::overlayCombatLevel);
		both(GroupEvent.Type.TOTAL_LEVEL, GroupIronmanConfig::discordTotalLevel, GroupIronmanConfig::overlayTotalLevel);
		both(GroupEvent.Type.QUEST, GroupIronmanConfig::discordQuest, GroupIronmanConfig::overlayQuest);
		both(GroupEvent.Type.QUEST_POINTS, GroupIronmanConfig::discordQuestPoints, GroupIronmanConfig::overlayQuestPoints);
		both(GroupEvent.Type.ONLINE, GroupIronmanConfig::discordOnline, GroupIronmanConfig::overlayOnline);
		both(GroupEvent.Type.BACK, GroupIronmanConfig::discordBack, GroupIronmanConfig::overlayBack);
		both(GroupEvent.Type.BARROWS, GroupIronmanConfig::discordBarrows, GroupIronmanConfig::overlayBarrows);
		both(GroupEvent.Type.RAID_UNIQUE, GroupIronmanConfig::discordRaidUnique, GroupIronmanConfig::overlayRaidUnique);
		both(GroupEvent.Type.CLUE, GroupIronmanConfig::discordClue, GroupIronmanConfig::overlayClue);
		both(GroupEvent.Type.DRY_STREAK, GroupIronmanConfig::discordDryStreak, GroupIronmanConfig::overlayDryStreak);
		both(GroupEvent.Type.DEATH_VALUE, GroupIronmanConfig::discordDeathValue, GroupIronmanConfig::overlayDeathValue);
		both(GroupEvent.Type.GRAVESTONE_EXPIRING, GroupIronmanConfig::discordGravestone,
			GroupIronmanConfig::overlayGravestone);
		both(GroupEvent.Type.HC_LIFE_LOST, GroupIronmanConfig::discordHardcoreLife,
			GroupIronmanConfig::overlayHardcoreLife);
		both(GroupEvent.Type.WILDERNESS, GroupIronmanConfig::discordWilderness, GroupIronmanConfig::overlayWilderness);
		both(GroupEvent.Type.DIARY, GroupIronmanConfig::discordDiary, GroupIronmanConfig::overlayDiary);
		both(GroupEvent.Type.COMBAT_TASK, GroupIronmanConfig::discordCombatTask, GroupIronmanConfig::overlayCombatTask);
		both(GroupEvent.Type.KC_MILESTONE, GroupIronmanConfig::discordKcMilestone, GroupIronmanConfig::overlayKcMilestone);
		both(GroupEvent.Type.PERSONAL_BEST, GroupIronmanConfig::discordPersonalBest,
			GroupIronmanConfig::overlayPersonalBest);
		both(GroupEvent.Type.WEEKLY_RECAP, GroupIronmanConfig::discordWeeklyRecap, GroupIronmanConfig::overlayWeeklyRecap);
		both(GroupEvent.Type.SLAYER, GroupIronmanConfig::discordSlayer, GroupIronmanConfig::overlaySlayer);
		both(GroupEvent.Type.FARMING, GroupIronmanConfig::discordFarming, GroupIronmanConfig::overlayFarming);
		both(GroupEvent.Type.BIRDHOUSE, GroupIronmanConfig::discordBirdhouse, GroupIronmanConfig::overlayBirdhouse);

		DIGEST.put(GroupEvent.Category.DROPS, GroupIronmanConfig::digestDrops);
		DIGEST.put(GroupEvent.Category.DEATHS, GroupIronmanConfig::digestDeaths);
		DIGEST.put(GroupEvent.Category.STORAGE, GroupIronmanConfig::digestStorage);
		DIGEST.put(GroupEvent.Category.PROGRESS, GroupIronmanConfig::digestProgress);
		DIGEST.put(GroupEvent.Category.GROUP, GroupIronmanConfig::digestGroup);
		DIGEST.put(GroupEvent.Category.ACTIVITIES, GroupIronmanConfig::digestActivities);
	}

	/** Everything off: what a fresh install, and the plugin before startUp, sees. */
	static final EventSettings OFF = new EventSettings(EnumSet.noneOf(GroupEvent.Type.class),
		EnumSet.noneOf(GroupEvent.Type.class), Long.MAX_VALUE, Long.MAX_VALUE, 99, 365, false, 0, 100, 5,
		EnumSet.noneOf(GroupEvent.Category.class), 20, false);

	final Set<GroupEvent.Type> discord;
	final Set<GroupEvent.Type> overlay;
	final long dropMin;
	final long storageMin;
	final int levelMin;
	final int backAfterDays;
	/** "Share group events": members' non-drop events are taken in, and ours sent. */
	final boolean share;
	final long clueMin;
	/** A kill count milestone every this many kills; 0 for none. */
	final int kcEvery;
	final int graveWarnMinutes;
	/**
	 * Categories with a once-a-day summary post. In addition to the instant
	 * posts, never instead of them: a category's digest lists every event of
	 * it, ticked for instant posting or not.
	 */
	final Set<GroupEvent.Category> digest;
	final int digestHour;
	/**
	 * The sidebar Log takes every kind (Board 2026-11-10), so every kind is
	 * detected while the plugin runs. False only in {@link #OFF}.
	 */
	final boolean log;

	EventSettings(Set<GroupEvent.Type> discord, Set<GroupEvent.Type> overlay, long dropMin, long storageMin,
		int levelMin, int backAfterDays, boolean share)
	{
		this(discord, overlay, dropMin, storageMin, levelMin, backAfterDays, share, 0, 100, 5,
			EnumSet.noneOf(GroupEvent.Category.class), 20);
	}

	EventSettings(Set<GroupEvent.Type> discord, Set<GroupEvent.Type> overlay, long dropMin, long storageMin,
		int levelMin, int backAfterDays, boolean share, long clueMin, int kcEvery, int graveWarnMinutes,
		Set<GroupEvent.Category> digest, int digestHour)
	{
		this(discord, overlay, dropMin, storageMin, levelMin, backAfterDays, share, clueMin, kcEvery, graveWarnMinutes,
			digest, digestHour, true);
	}

	private EventSettings(Set<GroupEvent.Type> discord, Set<GroupEvent.Type> overlay, long dropMin, long storageMin,
		int levelMin, int backAfterDays, boolean share, long clueMin, int kcEvery, int graveWarnMinutes,
		Set<GroupEvent.Category> digest, int digestHour, boolean log)
	{
		this.log = log;
		this.clueMin = Math.max(0, clueMin);
		this.kcEvery = Math.max(0, kcEvery);
		this.graveWarnMinutes = Math.max(1, Math.min(14, graveWarnMinutes));
		this.digest = Collections.unmodifiableSet(digest.isEmpty()
			? EnumSet.noneOf(GroupEvent.Category.class) : EnumSet.copyOf(digest));
		this.digestHour = Math.max(0, Math.min(23, digestHour));
		this.discord = Collections.unmodifiableSet(copy(discord));
		this.overlay = Collections.unmodifiableSet(copy(overlay));
		this.dropMin = Math.max(0, dropMin);
		this.storageMin = Math.max(0, storageMin);
		this.levelMin = levelMin;
		this.backAfterDays = Math.max(1, backAfterDays);
		this.share = share;
	}

	private static void both(GroupEvent.Type type, Predicate<GroupIronmanConfig> discord,
		Predicate<GroupIronmanConfig> overlay)
	{
		DISCORD.put(type, discord);
		OVERLAY.put(type, overlay);
	}

	private static Set<GroupEvent.Type> copy(Set<GroupEvent.Type> types)
	{
		return types.isEmpty() ? EnumSet.noneOf(GroupEvent.Type.class) : EnumSet.copyOf(types);
	}

	static EventSettings from(GroupIronmanConfig config)
	{
		return new EventSettings(on(DISCORD, config), on(OVERLAY, config), config.eventDropMinValue(),
			config.eventStorageMinValue(), config.eventLevelMin(), config.eventBackAfterDays(),
			config.groupShareEvents(), config.eventClueMinValue(), config.eventKcEvery(),
			config.eventGravestoneWarnMinutes(), digestOn(config), config.discordDigestHour());
	}

	private static Set<GroupEvent.Category> digestOn(GroupIronmanConfig config)
	{
		Set<GroupEvent.Category> out = EnumSet.noneOf(GroupEvent.Category.class);
		for (Map.Entry<GroupEvent.Category, Predicate<GroupIronmanConfig>> e : DIGEST.entrySet())
		{
			if (e.getValue().test(config))
			{
				out.add(e.getKey());
			}
		}
		return out;
	}

	private static Set<GroupEvent.Type> on(Map<GroupEvent.Type, Predicate<GroupIronmanConfig>> switches,
		GroupIronmanConfig config)
	{
		Set<GroupEvent.Type> out = EnumSet.noneOf(GroupEvent.Type.class);
		for (Map.Entry<GroupEvent.Type, Predicate<GroupIronmanConfig>> e : switches.entrySet())
		{
			if (e.getValue().test(config))
			{
				out.add(e.getKey());
			}
		}
		return out;
	}

	/** Discord, the overlay or the digest wants this kind. */
	boolean wants(GroupEvent.Type type)
	{
		return discord.contains(type) || overlay.contains(type) || digests(type);
	}

	/** Worth detecting: the Log takes every kind, so any settings but {@link #OFF} detect all of them. */
	boolean detects(GroupEvent.Type type)
	{
		return log || wants(type);
	}

	/** This kind goes in its category's daily digest. The weekly recap is a post of its own. */
	boolean digests(GroupEvent.Type type)
	{
		return type != GroupEvent.Type.WEEKLY_RECAP && digest.contains(type.category);
	}
}
