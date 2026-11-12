package com.vinnyvangogh.groupironman;

import java.util.EnumMap;
import java.util.Map;

/**
 * The seven Discord webhooks, in the order the settings show them (Board 2026-11-11): Main, the fallback and home
 * of anything not listed, then six categories. Each category's URL may be left empty to post to Main's, and each
 * has its own poster name choice, kept even on Main's URL (Board pre-run answer).
 *
 * <p>The default names are RuneScape-flavoured, under Discord's 80 characters and free of the words Discord refuses
 * in a webhook username ({@link PosterNames#refused}); WebhooksTest holds them to that.
 *
 * <p>Separate from {@link GroupEvent.Category}, which picks embed colours, Log filters and digest ticks: the
 * webhooks are where a post goes, which the Board split differently.
 */
enum Webhook
{
	MAIN("main", "Town Crier"),
	ONLINE("online status", "World Hopper"),
	DROPS("drops & loot", "Party Pete"),
	PROGRESS("progress", "Skillcape Seller"),
	DEATHS("deaths & danger", "Death's Office"),
	STORAGE("group storage", "Gielinor Banker"),
	/** The daily digest only: no instant post comes here. */
	DIGEST("daily digest", "Varrock Herald");

	private static final Map<GroupEvent.Type, Webhook> BY_TYPE = new EnumMap<>(GroupEvent.Type.class);

	static
	{
		on(ONLINE, GroupEvent.Type.ONLINE);
		on(DROPS, GroupEvent.Type.DROP, GroupEvent.Type.CLOG, GroupEvent.Type.PET, GroupEvent.Type.UNIQUE,
			GroupEvent.Type.BARROWS, GroupEvent.Type.RAID_UNIQUE, GroupEvent.Type.STORAGE_LAST);
		on(PROGRESS, GroupEvent.Type.LEVEL_UP, GroupEvent.Type.MAXED, GroupEvent.Type.XP_MILESTONE,
			GroupEvent.Type.COMBAT_LEVEL, GroupEvent.Type.TOTAL_LEVEL, GroupEvent.Type.QUEST,
			GroupEvent.Type.QUEST_POINTS);
		on(DEATHS, GroupEvent.Type.DEATH, GroupEvent.Type.DEATH_VALUE, GroupEvent.Type.GRAVESTONE_EXPIRING,
			GroupEvent.Type.HC_LIFE_LOST);
		on(STORAGE, GroupEvent.Type.STORAGE_DEPOSIT, GroupEvent.Type.STORAGE_WITHDRAWAL,
			GroupEvent.Type.STORAGE_SUMMARY, GroupEvent.Type.STORAGE_MINIMUM);
	}

	/** The word the chat warnings use for this webhook's fields. */
	final String label;
	/** The name 'Default name' posts under, and what the Custom box starts with. */
	final String defaultName;

	Webhook(String label, String defaultName)
	{
		this.label = label;
		this.defaultName = defaultName;
	}

	private static void on(Webhook webhook, GroupEvent.Type... types)
	{
		for (GroupEvent.Type t : types)
		{
			BY_TYPE.put(t, webhook);
		}
	}

	/** Where an event's instant post goes: its category's webhook, Main for anything not listed. Never null. */
	static Webhook of(GroupEvent.Type type)
	{
		return BY_TYPE.getOrDefault(type, MAIN);
	}
}
