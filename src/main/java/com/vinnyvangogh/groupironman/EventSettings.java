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
	}

	/** Everything off: what a fresh install, and the plugin before startUp, sees. */
	static final EventSettings OFF = new EventSettings(EnumSet.noneOf(GroupEvent.Type.class),
		EnumSet.noneOf(GroupEvent.Type.class), Long.MAX_VALUE, Long.MAX_VALUE, 99, 365, false);

	final Set<GroupEvent.Type> discord;
	final Set<GroupEvent.Type> overlay;
	final long dropMin;
	final long storageMin;
	final int levelMin;
	final int backAfterDays;
	/** "Share group events": members' non-drop events are taken in, and ours sent. */
	final boolean share;

	EventSettings(Set<GroupEvent.Type> discord, Set<GroupEvent.Type> overlay, long dropMin, long storageMin,
		int levelMin, int backAfterDays, boolean share)
	{
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
			config.groupShareEvents());
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

	/** Some output wants this kind; when none does, it is not even detected. */
	boolean wants(GroupEvent.Type type)
	{
		return discord.contains(type) || overlay.contains(type);
	}
}
