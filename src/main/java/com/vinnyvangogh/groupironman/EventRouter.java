package com.vinnyvangogh.groupironman;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Sends each event to the outputs whose switch for its kind is on, after the
 * thresholds. Pure: the outputs are plain consumers, so every rule here is
 * tested without a client or a network.
 *
 * <p><b>One poster per event.</b> Only this player's own events go to
 * Discord. A member's event reaches this client over the party, and that
 * member's own client is the one that posts it (if they turned Discord on),
 * so a group with everyone posting still posts each event once.
 */
final class EventRouter
{
	private final Consumer<GroupEvent> discord;
	private final Consumer<GroupEvent> overlay;

	EventRouter(Consumer<GroupEvent> discord, Consumer<GroupEvent> overlay)
	{
		this.discord = discord;
		this.overlay = overlay;
	}

	/** {@code own}: this player's event, from this client; false for a member's from the party. */
	void route(GroupEvent event, boolean own, EventSettings settings, ItemLookup lookup)
	{
		GroupEvent kept = threshold(event, settings, lookup);
		if (kept == null || !kept.isValid() || kept.member.isEmpty())
		{
			return;
		}
		if (settings.overlay.contains(kept.type))
		{
			overlay.accept(kept);
		}
		if (own && settings.discord.contains(kept.type))
		{
			discord.accept(kept);
		}
	}

	/** The event as the thresholds leave it: a drop keeps only its valuable stacks; null when nothing is left. */
	static GroupEvent threshold(GroupEvent event, EventSettings settings, ItemLookup lookup)
	{
		switch (event.type)
		{
			case DROP:
				Map<Integer, Integer> valuable = new TreeMap<>();
				for (Map.Entry<Integer, Integer> e : event.items.entrySet())
				{
					if (lookup.price(e.getKey()) * e.getValue() >= settings.dropMin)
					{
						valuable.put(e.getKey(), e.getValue());
					}
				}
				return valuable.isEmpty() ? null : event.toBuilder().items(valuable).build();
			case CLUE:
				return event.number >= settings.clueMin ? event : null;
			case LEVEL_UP:
				return event.number >= settings.levelMin ? event : null;
			case STORAGE_DEPOSIT:
			case STORAGE_WITHDRAWAL:
				return event.number >= settings.storageMin ? event : null;
			default:
				return event;
		}
	}
}
