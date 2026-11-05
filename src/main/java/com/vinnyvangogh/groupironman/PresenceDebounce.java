package com.vinnyvangogh.groupironman;

import java.util.concurrent.TimeUnit;

/**
 * Online and offline for this player, without the noise of a world hop or a
 * quick relog: a logout only becomes "offline" when no login follows within
 * {@link #SETTLE_MS}, and a login while still counted online says nothing.
 */
final class PresenceDebounce
{
	static final long SETTLE_MS = TimeUnit.MINUTES.toMillis(10);

	private boolean online;
	private long loggedOutAt = -1;
	private int lastWorld;

	/** Logged in on {@code world}; the "online" event, or null for a hop or relog. */
	GroupEvent login(long at, int world, String member)
	{
		loggedOutAt = -1;
		lastWorld = world;
		if (online)
		{
			return null;
		}
		online = true;
		return GroupEvent.of(GroupEvent.Type.ONLINE, at, member).text("online").world(world).build();
	}

	void logout(long at)
	{
		if (online && loggedOutAt < 0)
		{
			loggedOutAt = at;
		}
	}

	/** The "offline" event once a logout has settled, else null. Called on a timer. */
	GroupEvent poll(long now, String member)
	{
		if (!online || loggedOutAt < 0 || now - loggedOutAt < SETTLE_MS)
		{
			return null;
		}
		online = false;
		long at = loggedOutAt;
		loggedOutAt = -1;
		return GroupEvent.of(GroupEvent.Type.ONLINE, at, member).text("offline").world(lastWorld).build();
	}

	/** "Back after N days": null unless {@code lastPlayed} is known and at least {@code days} ago. */
	static GroupEvent back(long lastPlayed, long now, int days, String member)
	{
		if (lastPlayed <= 0)
		{
			return null;
		}
		long away = (now - lastPlayed) / TimeUnit.DAYS.toMillis(1);
		return away >= days ? GroupEvent.of(GroupEvent.Type.BACK, now, member).number(away).build() : null;
	}
}
