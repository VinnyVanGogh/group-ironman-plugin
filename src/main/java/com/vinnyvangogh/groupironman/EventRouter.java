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
	private final Consumer<GroupEvent> digest;
	private final Consumer<GroupEvent> log;

	EventRouter(Consumer<GroupEvent> discord, Consumer<GroupEvent> overlay)
	{
		this(discord, overlay, e ->
		{
		});
	}

	/**
	 * {@code digest} takes this player's own events for the daily digest. It
	 * is a separate output: an event can post now and be in tonight's digest,
	 * and a ticked digest never holds an instant post back.
	 */
	EventRouter(Consumer<GroupEvent> discord, Consumer<GroupEvent> overlay, Consumer<GroupEvent> digest)
	{
		this(discord, overlay, digest, e ->
		{
		});
	}

	/**
	 * {@code log} is the sidebar Log tab. It takes every event that passes the
	 * thresholds, yours and members', whichever Discord and overlay switches
	 * are on (Board 2026-11-10): the Log has no switch of its own.
	 */
	EventRouter(Consumer<GroupEvent> discord, Consumer<GroupEvent> overlay, Consumer<GroupEvent> digest,
		Consumer<GroupEvent> log)
	{
		this.discord = discord;
		this.overlay = overlay;
		this.digest = digest;
		this.log = log;
	}

	/** {@code own}: this player's event, from this client; false for a member's from the party. */
	void route(GroupEvent event, boolean own, EventSettings settings, ItemLookup lookup)
	{
		GroupEvent kept = threshold(event, settings, lookup);
		if (kept == null || !kept.isValid() || kept.member.isEmpty())
		{
			return;
		}
		log.accept(kept);
		if (settings.overlay.contains(kept.type))
		{
			overlay.accept(kept);
		}
		if (own && settings.discord.contains(kept.type))
		{
			discord.accept(kept);
		}
		if (own && settings.digests(kept.type))
		{
			digest.accept(kept);
		}
	}

	/** The event as the thresholds leave it: a drop keeps only its valuable stacks; null when nothing is left. */
	static GroupEvent threshold(GroupEvent event, EventSettings settings, ItemLookup lookup)
	{
		switch (event.type)
		{
			case DROP:
				return valuableStacks(event, settings.dropMin, lookup);
			case CLUE:
				return atLeast(event, settings.clueMin);
			case LEVEL_UP:
				return everyN(atLeast(event, settings.levelMin), settings.levelEvery);
			case STORAGE_DEPOSIT:
			case STORAGE_WITHDRAWAL:
				return atLeast(event, settings.storageMin);
			default:
				return event;
		}
	}

	private static GroupEvent atLeast(GroupEvent event, long min)
	{
		return event.number >= min ? event : null;
	}

	/**
	 * "Post every N levels": only levels that are a multiple of {@code every}.
	 * 99 always counts; 99s, max total, total level milestones and combat
	 * levels are kinds of their own and never come through here. One action
	 * that gains several levels (low Prayer, 3 → 7 with N = 5) counts when it
	 * passes a multiple, so a jump never skips the level it crossed.
	 *
	 * <p>This filter runs before "Combine level-ups": a skill's streak post is
	 * made of the levels that pass it, so with N = 5 one post reads 59 → 65
	 * and is edited when 70 comes, not at every level in between.
	 */
	private static GroupEvent everyN(GroupEvent levelUp, int every)
	{
		if (levelUp == null || every <= 1 || levelUp.number >= ProgressTracker.MAX_LEVEL)
		{
			return levelUp;
		}
		long before = levelUp.previous > 0 && levelUp.previous < levelUp.number ? levelUp.previous
			: levelUp.number - 1;
		// The highest multiple of N at or below the new level is above the old one.
		return levelUp.number / every * every > before ? levelUp : null;
	}

	private static GroupEvent valuableStacks(GroupEvent event, long min, ItemLookup lookup)
	{
		Map<Integer, Integer> valuable = new TreeMap<>();
		for (Map.Entry<Integer, Integer> e : event.items.entrySet())
		{
			if (lookup.price(e.getKey()) * e.getValue() >= min)
			{
				valuable.put(e.getKey(), e.getValue());
			}
		}
		return valuable.isEmpty() ? null : event.toBuilder().items(valuable).build();
	}
}
