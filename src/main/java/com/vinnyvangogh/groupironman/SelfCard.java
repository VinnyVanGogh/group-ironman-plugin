package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.Map;
import java.util.Objects;

/**
 * This player's own card, and what of it goes to the party.
 *
 * <p>The card is filled from this client on every capture while logged in,
 * with or without group sync and whatever the share switches say: what you
 * see of yourself never waits on a party or on the saved cache. A payload is
 * sent only while syncing, with that feature shared, and only when it differs
 * from the last one sent.
 *
 * <p>Client thread only.
 */
final class SelfCard
{
	/** Where a payload goes: the group's party. */
	interface Party
	{
		void send(String payload);
	}

	private final GroupRoster roster;
	private final Gson gson;
	private final Party party;
	private final Runnable onChange;

	private String lastStats;
	private String lastCarried;
	private String lastExtras;

	SelfCard(GroupRoster roster, Gson gson, Party party, Runnable onChange)
	{
		this.roster = roster;
		this.gson = gson;
		this.party = party;
		this.onChange = onChange;
	}

	/** Logged in this tick: you are online now. Redraws only when the card was not already online. */
	void seen(String self, long now)
	{
		Long before = roster.lastHeard().get(self);
		roster.heard(self, now);
		if (before == null || !Presence.online(before, now))
		{
			onChange.run();
		}
	}

	/** Forget what was sent, so the next capture of each is sent again. */
	void resend()
	{
		lastStats = null;
		lastCarried = null;
		lastExtras = null;
	}

	/** The heartbeat: the next stats capture is sent even when unchanged. */
	void resendStats()
	{
		lastStats = null;
	}

	/** True when a payload went to the party. */
	boolean stats(String self, MemberStats stats, GroupOutbox.Switches switches, boolean syncing)
	{
		boolean changed = !stats.equals(roster.allStats().get(self));
		roster.putStats(self, stats);
		changed(changed);
		String payload = syncing ? GroupOutbox.stats(gson, switches, stats) : null;
		if (payload == null || payload.equals(lastStats))
		{
			return false;
		}
		party.send(payload);
		lastStats = payload;
		return true;
	}

	/** True when a payload went to the party. */
	boolean carried(String self, Map<Integer, Integer> inventory, Map<Integer, Integer> worn,
		GroupOutbox.Switches switches, boolean syncing)
	{
		GroupCodec.Carried items = new GroupCodec.Carried(inventory, worn);
		GroupCodec.Carried before = roster.allCarried().get(self);
		boolean changed = before == null || !before.inventory.equals(items.inventory) || !before.worn.equals(items.worn);
		roster.putCarried(self, items);
		changed(changed);
		String payload = syncing ? GroupOutbox.carried(gson, switches, inventory, worn) : null;
		if (payload == null || payload.equals(lastCarried))
		{
			return false;
		}
		party.send(payload);
		lastCarried = payload;
		return true;
	}

	/** True when a payload went to the party. */
	boolean extras(String self, MemberExtras extras, GroupOutbox.Switches switches, boolean syncing)
	{
		MemberExtras before = roster.extras().get(self);
		roster.putExtras(self, extras);
		changed(!Objects.equals(before, roster.extras().get(self)));
		String payload = syncing ? GroupOutbox.extras(gson, switches, extras) : null;
		if (payload == null || payload.equals(lastExtras))
		{
			return false;
		}
		party.send(payload);
		lastExtras = payload;
		return true;
	}

	private void changed(boolean changed)
	{
		if (changed)
		{
			onChange.run();
		}
	}
}
