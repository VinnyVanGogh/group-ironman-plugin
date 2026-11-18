package com.vinnyvangogh.groupironman;

import java.util.Locale;

/**
 * A carried container whose contents the game only tells through chat lines
 * and its Check dialog: log basket, fish barrel and coal bag. Their count is
 * drawn on the container's inventory slot (Board 2026-11-14).
 *
 * Copied from Combat Vitals' {@code SkillingContainer} (same author, not a
 * dependency), trimmed to the chat-tracked kinds. Capacities from the OSRS
 * wiki ("Log basket", "Fish barrel", "Coal bag", read 2026-11-14). The gem
 * bag, herb sack, seed box and essence pouches are left out: the first three
 * say nothing countable in chat, and the pouches' counts are already live in
 * varbits the game sends, which this plugin does not share yet; with no
 * number known, none is drawn.
 *
 * The enum name is what goes on the wire ({@code cc} in a carried message),
 * so it freezes with the first build that sends it.
 */
enum SkillingContainer
{
	/** Log basket and Forestry basket: 28 logs of any kind. */
	LOG_BASKET("Log basket", 28, "log basket", "forestry basket"),
	/** Fish barrel and Fish sack barrel: 28 raw fish of any kind. */
	FISH_BARREL("Fish barrel", 28, "fish barrel", "fish sack barrel"),
	/** 27 coal, 36 with a Smithing cape. */
	COAL_BAG("Coal bag", 27, "coal bag"),
	/**
	 * Looting bag: 28 slots, read from the game's own container when the bag is viewed, not from chat. Its
	 * count is the slots in use, since a stack of coins would pass any item count.
	 */
	LOOTING_BAG("Looting bag", 28, "looting bag");

	static final int COAL_BAG_CAPE_CAPACITY = 36;
	private static final String OPEN = "open ";

	/** "Log basket": the container's name in Find ("log basket"). */
	final String label;
	final int capacity;
	private final String[] names;

	SkillingContainer(String label, int capacity, String... names)
	{
		this.label = label;
		this.capacity = capacity;
		this.names = names;
	}

	/** The container an item is, by its name, open or closed; null for anything else. */
	static SkillingContainer fromItemName(String name)
	{
		if (name == null)
		{
			return null;
		}
		String lower = name.toLowerCase(Locale.ROOT).trim();
		if (lower.startsWith(OPEN))
		{
			lower = lower.substring(OPEN.length());
		}
		for (SkillingContainer container : values())
		{
			for (String n : container.names)
			{
				if (n.equals(lower))
				{
					return container;
				}
			}
		}
		return null;
	}

	/** "Open log basket": gathered items go straight in while it is open. */
	static boolean isOpen(String name)
	{
		return name != null && name.toLowerCase(Locale.ROOT).startsWith(OPEN);
	}

	/** The kind a wire key names, or null for a key from a newer build. */
	static SkillingContainer fromKey(String key)
	{
		for (SkillingContainer container : values())
		{
			if (container.name().equals(key))
			{
				return container;
			}
		}
		return null;
	}
}
