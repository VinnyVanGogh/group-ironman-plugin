package com.vinnyvangogh.groupironman;

import java.util.concurrent.TimeUnit;

/**
 * Online or last seen, from when a member's client last sent anything.
 *
 * <p>A member sharing stats sends at least once a minute while logged in
 * ({@link GroupService#STATS_HEARTBEAT_TICKS}), so three minutes of silence
 * means they logged out, closed the client or left the party.
 */
final class Presence
{
	static final long ONLINE_MS = TimeUnit.MINUTES.toMillis(3);

	private Presence()
	{
	}

	static boolean online(long lastHeard, long now)
	{
		return lastHeard > 0 && now - lastHeard <= ONLINE_MS;
	}

	/** "online", "last seen 2h ago" or "not seen yet". */
	static String describe(long lastHeard, long now)
	{
		if (lastHeard <= 0)
		{
			return "not seen yet";
		}
		return online(lastHeard, now) ? "online" : "last seen " + BoostText.ago(now, lastHeard);
	}
}
