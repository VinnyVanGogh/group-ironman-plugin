package com.vinnyvangogh.groupironman;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Online and offline for this player (Board 2026-11-10, "a happy medium"):
 *
 * <ul>
 * <li>A logout starts a grace of 0 to 10 minutes. A login inside it (a quick
 * relog, a disconnect) says nothing at all, for the logout or the login.</li>
 * <li>Once the grace passes, one "offline" with the session length, stamped
 * with the logout, not the post.</li>
 * <li>A world hop is never a logout: the engine does not call {@link #logout}
 * for one.</li>
 * <li>At most one online/offline pair per {@link #PAIR_MS}: an offline is held
 * until that long after its online was posted, and a login while it is held
 * cancels it, so a flaky connection posts nothing and the channel never says
 * offline while the player is on.</li>
 * <li>A logout saved by a client that closed in the grace is posted on the
 * next start only while it is fresh ({@link #STALE_MS}); never hours later.</li>
 * </ul>
 *
 * The name is the one the login gave, so the post never says "You". Not
 * thread-safe: the engine holds the lock.
 */
final class PresenceDebounce
{
	static final int MAX_GRACE_MINUTES = 10;
	static final long PAIR_MS = TimeUnit.MINUTES.toMillis(10);
	static final long STALE_MS = TimeUnit.MINUTES.toMillis(15);
	/** What GroupService falls back to with no player; never a name to post. */
	private static final String NO_NAME = "You";
	private static final String SEP = "\t";
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

	private final ZoneId zone;
	private String member = "";
	private boolean online;
	private long sessionStart = -1;
	private long loggedOutAt = -1;
	private long onlinePostedAt = -1;
	private int world;
	private boolean dirty;

	PresenceDebounce(ZoneId zone)
	{
		this.zone = zone;
	}

	/**
	 * Logged in as {@code name} on {@code world}: the posts it makes, in order. Empty for a relog inside
	 * the grace or a hop; an offline first when a different account was still counted online.
	 */
	List<GroupEvent> login(long at, int world, String name, int graceMinutes)
	{
		if (name == null || name.isEmpty() || NO_NAME.equals(name))
		{
			return Collections.emptyList();
		}
		List<GroupEvent> out = new ArrayList<>(2);
		GroupEvent due = poll(at, graceMinutes);
		if (due != null)
		{
			out.add(due);
		}
		if (online && !member.equalsIgnoreCase(name))
		{
			// Another account on this client: the first one really left.
			out.add(offline(loggedOutAt < 0 ? at : loggedOutAt));
		}
		dirty = true;
		this.world = world;
		if (online)
		{
			loggedOutAt = -1;
			return out;
		}
		member = name;
		online = true;
		sessionStart = at;
		loggedOutAt = -1;
		onlinePostedAt = at;
		out.add(GroupEvent.of(GroupEvent.Type.ONLINE, at, name).text("online").world(world).build());
		return out;
	}

	/** At the login screen, or the client closing while logged in. Not for a world hop. */
	void logout(long at)
	{
		if (online && loggedOutAt < 0)
		{
			loggedOutAt = at;
			dirty = true;
		}
	}

	/** The plugin restarted while logged in: a saved logout was not one. */
	void resume()
	{
		if (loggedOutAt >= 0)
		{
			loggedOutAt = -1;
			dirty = true;
		}
	}

	/** The "offline" event once a logout is past the grace and the pair limit, else null. Called on a timer. */
	GroupEvent poll(long now, int graceMinutes)
	{
		if (!online || loggedOutAt < 0)
		{
			return null;
		}
		if (now - loggedOutAt > STALE_MS)
		{
			// Only reachable from a saved logout: timing too old to post honestly.
			clear();
			return null;
		}
		long grace = TimeUnit.MINUTES.toMillis(Math.max(0, Math.min(MAX_GRACE_MINUTES, graceMinutes)));
		long due = Math.max(loggedOutAt + grace, onlinePostedAt + PAIR_MS);
		return now < due ? null : offline(loggedOutAt);
	}

	private GroupEvent offline(long at)
	{
		long minutes = sessionStart < 0 ? -1 : Math.max(0, at - sessionStart) / TimeUnit.MINUTES.toMillis(1);
		GroupEvent e = GroupEvent.of(GroupEvent.Type.ONLINE, at, member).text("offline").world(world)
			.number(minutes).detail(CLOCK.format(Instant.ofEpochMilli(at).atZone(zone))).build();
		clear();
		return e;
	}

	private void clear()
	{
		online = false;
		loggedOutAt = -1;
		sessionStart = -1;
		dirty = true;
	}

	/** True once after any change, so the engine saves only then. */
	boolean takeDirty()
	{
		boolean was = dirty;
		dirty = false;
		return was;
	}

	/** "1h 12m", "45m", "under a minute". */
	static String played(long minutes)
	{
		if (minutes < 1)
		{
			return "under a minute";
		}
		return minutes < 60 ? minutes + "m" : minutes / 60 + "h " + minutes % 60 + "m";
	}

	/** One line for the config: empty when there is nothing worth keeping across a restart. */
	String save()
	{
		if (!online)
		{
			return "";
		}
		return String.join(SEP, member, Long.toString(sessionStart), Long.toString(loggedOutAt),
			Long.toString(onlinePostedAt), Integer.toString(world));
	}

	/** {@link #save}'s line back; anything unreadable is a fresh start, which posts no offline. */
	static PresenceDebounce parse(String saved, ZoneId zone)
	{
		PresenceDebounce p = new PresenceDebounce(zone);
		String[] f = saved == null ? new String[0] : saved.split(SEP, -1);
		if (f.length != 5 || f[0].isEmpty())
		{
			return p;
		}
		try
		{
			p.sessionStart = Long.parseLong(f[1]);
			p.loggedOutAt = Long.parseLong(f[2]);
			p.onlinePostedAt = Long.parseLong(f[3]);
			p.world = Integer.parseInt(f[4]);
			p.member = f[0];
			p.online = true;
		}
		catch (NumberFormatException e)
		{
			return new PresenceDebounce(zone);
		}
		return p;
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
