package com.vinnyvangogh.groupironman;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The need and shop alerts a member shares with the group: a shop they have come up on that sells something the
 * group still needs ({@link GroupEvent.Type#SHOP_NEAR}), a group storage item running short
 * ({@link GroupEvent.Type#STOCK_SHORT}), a new item request and a request that is done
 * ({@link GroupEvent.Type#REQUEST}). They travel as ordinary group events, so the sync backend and the RuneLite
 * Party carry them as they carry every other event, and {@link AlertDedup} makes a member in both hear one.
 * This class holds the rules that are only theirs: which events these are, how often one member may repeat one,
 * and the chat line. Pure.
 */
final class SharedAlerts
{
	/** A member's alert for the same shop is not repeated inside this, unless what it names has changed. */
	static final long COOLDOWN_MS = TimeUnit.MINUTES.toMillis(15);
	/**
	 * A shared alert older than this is history, not news: the party resends events whenever someone joins, and a
	 * chat line for something from earlier would read as happening now.
	 */
	static final long MAX_AGE_MS = TimeUnit.MINUTES.toMillis(5);
	/** Most (member, key) pairs the cooldown remembers. */
	static final int MAX_KEPT = 200;

	private SharedAlerts()
	{
	}

	/** Whether this event is one of the shared need and shop alerts. */
	static boolean isShared(GroupEvent e)
	{
		switch (e.type)
		{
			case SHOP_NEAR:
			case STOCK_SHORT:
				return true;
			case REQUEST:
				return e.number == RequestBoard.KIND_MADE || e.number == RequestBoard.KIND_DONE;
			default:
				return false;
		}
	}

	/** Recent enough for a chat line: within {@link #MAX_AGE_MS} either side of {@code now} (clocks differ). */
	static boolean recent(GroupEvent e, long now)
	{
		return Math.abs(now - e.at) <= MAX_AGE_MS;
	}

	/** {@code member} came up on {@code shop}, which sells {@code items} (see {@link ShoppingBoard#items}). */
	static GroupEvent shopNear(String member, String shop, String items, long now)
	{
		return GroupEvent.of(GroupEvent.Type.SHOP_NEAR, now, member).text(shop).detail(items).build();
	}

	/** Group storage ran short of the item in {@code alert}, as {@code member}'s visit left it. */
	static GroupEvent stockShort(String member, LowStock.Alert alert, long now)
	{
		return GroupEvent.of(GroupEvent.Type.STOCK_SHORT, now, member).text(alert.item).number(alert.have)
			.detail(Amounts.GAME.of(alert.minimum)).build();
	}

	/** The chat line: "Alex: your group needs 10 more Shark, at Gnome Stronghold shop" and its like. */
	static String chatLine(GroupEvent e, ItemLookup lookup)
	{
		return EventText.sentence(e, lookup);
	}

	/**
	 * Per (member, subject) throttle: a shop the member keeps running past must not fill the group's chat. An alert
	 * goes out when there was none before, when {@link #COOLDOWN_MS} has passed, or when what it names (the needed
	 * items) is not what the last one named. Thread-safe.
	 */
	static final class Cooldown
	{
		private static final class Last
		{
			final String signature;
			final long at;

			Last(String signature, long at)
			{
				this.signature = signature;
				this.at = at;
			}
		}

		private final Map<String, Last> last = new HashMap<>();

		/** True, and remembered, when the alert may go out now; false when it is a repeat inside the cooldown. */
		synchronized boolean allow(String member, String subject, String signature, long now)
		{
			expire(now);
			String key = GroupMembers.key(member) + '|' + subject.toLowerCase(Locale.ROOT);
			Last before = last.get(key);
			if (before != null && now - before.at < COOLDOWN_MS && before.signature.equals(signature))
			{
				return false;
			}
			if (last.size() >= MAX_KEPT && before == null)
			{
				// Bounded: drop the oldest.
				String oldest = null;
				long oldestAt = Long.MAX_VALUE;
				for (Map.Entry<String, Last> e : last.entrySet())
				{
					if (e.getValue().at < oldestAt)
					{
						oldestAt = e.getValue().at;
						oldest = e.getKey();
					}
				}
				last.remove(oldest);
			}
			last.put(key, new Last(signature, now));
			return true;
		}

		private void expire(long now)
		{
			for (Iterator<Last> it = last.values().iterator(); it.hasNext(); )
			{
				if (now - it.next().at >= COOLDOWN_MS)
				{
					it.remove();
				}
			}
		}

		synchronized void clear()
		{
			last.clear();
		}
	}
}
