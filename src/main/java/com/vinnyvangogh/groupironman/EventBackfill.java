package com.vinnyvangogh.groupironman;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Events a member recorded while not synced, uploaded when they join the group's sync (Board 2026-11-17).
 *
 * <p>Why they were missing: an own event is always kept in this client's Log, but is sent only while synced and
 * only live. Only drops and collection log slots were resent (to someone who joined after), so Fight Caves, slayer,
 * levels and logins made unsynced never reached anyone, and a member who joined sync later never uploaded theirs.
 *
 * <p>An event's identity is its type, time, member, text, items and number ({@link GroupEvent#sameAs}), so a
 * re-upload is recognised and logged once, with the original time. There is no new wire format: a receiver tells a
 * backfill from a live event by its age, and shows a backfilled one in the Log only, never as a toast.
 */
final class EventBackfill
{
	/** An event older than this on arrival was recorded earlier and is being backfilled, not happening now. */
	static final long LIVE_MS = TimeUnit.MINUTES.toMillis(2);
	/** Most events uploaded in one join: the feed's own limit, so a long unsynced stretch fits. */
	static final int MAX_UPLOAD = 300;

	private EventBackfill()
	{
	}

	enum Arrival
	{
		/** Happening now: logged, shown and routed as before. */
		LIVE,
		/** Recorded earlier: logged with its own time and nothing else. */
		BACKFILL,
		/** Already in the Log. */
		DUPLICATE
	}

	/** How a member's event arriving at {@code now} is treated, given the Log {@code feed} already holds. */
	static Arrival classify(DropLog feed, GroupEvent event, long now)
	{
		if (feed.contains(event))
		{
			return Arrival.DUPLICATE;
		}
		return now - event.at > LIVE_MS ? Arrival.BACKFILL : Arrival.LIVE;
	}

	/**
	 * {@code self}'s own events worth uploading on joining, newest first: drops and log slots from the drops log,
	 * and every other shareable kind from the Log's feed.
	 */
	static List<GroupEvent> upload(GroupRoster roster, String self, long now)
	{
		List<GroupEvent> out = roster.events().recentBy(self, now, e -> true, MAX_UPLOAD);
		out.addAll(roster.feed().recentBy(self, now, e -> e.type.travels() && !e.type.logged(), MAX_UPLOAD));
		return out;
	}

	/**
	 * True for a name to remove from every log: not on a known roster. Never the local player, a roster member, or
	 * the group's own entries ("Group", "Group storage", the group's name), and nothing at all while the roster is unknown.
	 */
	static boolean offRoster(GroupMembers roster, String self, String name)
	{
		if (!roster.known() || name == null || name.trim().isEmpty())
		{
			return false;
		}
		String key = GroupMembers.key(name);
		return !roster.allows(name) && !key.equals(GroupMembers.key(self))
			&& !key.equals("group") && !key.equals("group storage") && !key.equals(GroupMembers.key(roster.group()));
	}
}
