package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

/**
 * The group side of the live online board (Board 2026-11-10): which client keeps it, when its inputs have
 * settled, and what its lines say. Discord Relay posts and edits the message itself
 * ({@code docs/contracts/discord-relay-v1.md}, the {@code board} message); GIM decides everything that needs
 * the group: the roster, presence, the leader's role and the members' opt-outs.
 */
final class BoardLines
{
	static final String ONLINE = "🟢";
	static final String OFFLINE = "🔴";
	static final String UNKNOWN = "⚪";
	/** After a member the game shows but whose client never reported: they do not run the plugin. */
	static final String NO_PLUGIN = " *(no plugin)*";
	/** The board's last line when no plugin user is online to keep it current (Board 2026-11-11). */
	static final String STALE = "Nobody with GIM Utilities is online, so these may be out of date";
	/**
	 * How long after the session's first login before the board may send (Board 2026-11-14): the leader's role,
	 * the shared webhooks and the group cache are in by then, so the first send goes to the webhook the board
	 * settles on, not the Main fallback a moment before.
	 */
	static final long SETTLE_MS = TimeUnit.SECONDS.toMillis(20);

	private BoardLines()
	{
	}

	/** This client's own player, which it knows better than the party does. */
	static final class Self
	{
		final String name;
		final boolean online;
		final int world;
		/** When it logged out; 0 while online. */
		final long seenAt;

		Self(String name, boolean online, int world, long seenAt)
		{
			this.name = name;
			this.online = online;
			this.world = world;
			this.seenAt = seenAt;
		}
	}

	/**
	 * The Online status in force: this member's own, except on a webhook the leader shares, where the group's
	 * setting decides, so a group whose members all pick the board on the shared webhook still gets one board.
	 */
	static OnlineStatus effective(OnlineStatus own, GroupSettingsSchema.Resolved values, Webhooks webhooks)
	{
		if (!webhooks.fromGroup(Webhook.ONLINE))
		{
			return own;
		}
		OnlineStatus[] all = OnlineStatus.values();
		return all[(int) Math.min(all.length - 1, values.value(GroupSettingsSchema.ONLINE_STATUS))];
	}

	/**
	 * Whether this client keeps the live board: 'Online status' is the board and the Online status webhook (or
	 * Main) is set. On a webhook the leader shares, only the leader's client does.
	 */
	static boolean keeps(OnlineStatus status, Webhooks webhooks, boolean leader)
	{
		if (status != OnlineStatus.LIVE_BOARD || webhooks.url(Webhook.ONLINE) == null)
		{
			return false;
		}
		return leader || !webhooks.fromGroup(Webhook.ONLINE);
	}

	/**
	 * True once the inputs that decide the board's webhook have settled: the session's first login was
	 * {@link #SETTLE_MS} ago ({@code loginAt}, 0 before one) and the group cache is read.
	 */
	static boolean settled(long loginAt, long now, boolean loaded)
	{
		return loaded && loginAt > 0 && now - loginAt >= SETTLE_MS;
	}

	/** {@link #lines(GroupMembers, Map, Map, Self, GamePresence, long)} without the game's Group channel. */
	static String lines(GroupMembers roster, Map<String, Long> heard, Map<String, MemberStats> stats, Self self,
		long now)
	{
		return lines(roster, heard, stats, self, GamePresence.UNKNOWN, now);
	}

	/**
	 * One line per member: the game's roster when known, else everyone heard from or in the channel. When the
	 * game says ({@link GamePresence}, the in-game Group channel) a member is online or offline, that is the line,
	 * with " (no plugin)" for a member whose client was never heard from. Otherwise a member is online while their
	 * client is heard from ({@link Presence}), offline with when it last was, and unknown when it never was.
	 * Empty when there is no one to list.
	 */
	static String lines(GroupMembers roster, Map<String, Long> heard, Map<String, MemberStats> stats, Self self,
		GamePresence game, long now)
	{
		return lines(roster, heard, Collections.emptyMap(), stats, self, game, now);
	}

	/**
	 * As above, with {@code seen}: when this client last saw each member online ({@link LastSeen}, kept in the
	 * group cache). When nobody running the plugin is online ({@link #nobodyOnline}) the board ends with
	 * {@link #STALE} and when that became true.
	 */
	static String lines(GroupMembers roster, Map<String, Long> heard, Map<String, LastSeen> seen,
		Map<String, MemberStats> stats, Self self, GamePresence game, long now)
	{
		return lines(roster, heard, seen, stats, self, game, Collections.emptyMap(), now);
	}

	/**
	 * As above, with {@code activities}: each online member's coarse activity clause by {@link GroupMembers#key}
	 * ({@link #activitySuffixes}), " - in Fight Caves, wave 35", appended to their online line only.
	 */
	static String lines(GroupMembers roster, Map<String, Long> heard, Map<String, LastSeen> seen,
		Map<String, MemberStats> stats, Self self, GamePresence game, Map<String, String> activities, long now)
	{
		List<String> base = baseLines(roster, heard, seen, stats, self, game, now);
		List<String> names = new ArrayList<>(names(roster, heard, seen.keySet(), self, game));
		for (int i = 0; i < names.size() && i < base.size(); i++)
		{
			String suffix = activities.get(GroupMembers.key(names.get(i)));
			if (suffix != null && base.get(i).startsWith(ONLINE))
			{
				base.set(i, base.get(i) + suffix);
			}
		}
		return String.join("\n", base);
	}

	/**
	 * Board clauses for members in an activity: " - in Fight Caves, wave 35". A member who opted out of activity
	 * posts is left off, and this player too unless they share group events: the board is Discord, and their
	 * choice covers it.
	 */
	static Map<String, String> activitySuffixes(Map<String, ActivityState> activities, Map<String, MemberPrefs> prefs,
		String self, MemberPrefs mine, boolean shareMine)
	{
		Map<String, MemberPrefs> prefsBy = new HashMap<>();
		prefs.forEach((name, p) -> prefsBy.put(GroupMembers.key(name), p));
		String selfKey = GroupMembers.key(self == null ? "" : self);
		Map<String, String> out = new HashMap<>();
		activities.forEach((name, s) ->
		{
			String key = GroupMembers.key(name);
			boolean you = key.equals(selfKey);
			MemberPrefs p = you ? mine : prefsBy.getOrDefault(key, MemberPrefs.NONE);
			if (s.active() && !s.board.isEmpty() && p.posts(GroupEvent.Type.ACTIVITY_START) && (!you || shareMine))
			{
				out.put(key, " - " + s.board);
			}
		});
		return out;
	}

	private static List<String> baseLines(GroupMembers roster, Map<String, Long> heard, Map<String, LastSeen> seen,
		Map<String, MemberStats> stats, Self self, GamePresence game, long now)
	{
		Map<String, Long> heardBy = new HashMap<>();
		heard.forEach((name, at) -> heardBy.merge(GroupMembers.key(name), at, Math::max));
		Map<String, LastSeen> seenBy = new HashMap<>();
		seen.forEach((name, s) -> seenBy.merge(GroupMembers.key(name), s, LastSeen::merge));
		Map<String, Integer> worldBy = new HashMap<>();
		stats.forEach((name, s) -> worldBy.put(GroupMembers.key(name), s.world()));
		List<String> out = new ArrayList<>();
		for (String name : names(roster, heard, seen.keySet(), self, game))
		{
			String key = GroupMembers.key(name);
			LastSeen last = seenBy.get(key);
			if (self != null && key.equals(GroupMembers.key(self.name)))
			{
				long at = Math.max(self.seenAt, last == null ? 0 : last.at);
				out.add(self.online ? online(name, self.world) : gone(OFFLINE, name, at, self.world));
				continue;
			}
			out.add(member(name, heardBy.getOrDefault(key, 0L), last, worldBy.get(key), game, now));
		}
		if (!out.isEmpty() && nobodyOnline(heardBy, self, game, now))
		{
			out.add(stale(heardBy, seenBy, self));
		}
		return out;
	}

	/** Another member's line: the game first, then their client, else unknown with when they were last seen. */
	private static String member(String name, long heardAt, LastSeen seen, Integer statsWorld, GamePresence game,
		long now)
	{
		boolean plugin = heardAt > 0 || statsWorld != null || seen != null && seen.plugin;
		long lastAt = Math.max(heardAt, seen == null ? 0 : seen.at);
		int lastWorld = lastWorld(heardAt, seen, statsWorld);
		String tag = plugin ? "" : NO_PLUGIN;
		switch (game.state(name))
		{
			case ONLINE:
				return online(name, game.world(name) > 0 ? game.world(name) : lastWorld) + tag;
			case OFFLINE:
				return gone(OFFLINE, name, lastAt, lastWorld) + tag;
			default:
				break;
		}
		if (Presence.online(heardAt, now))
		{
			return online(name, lastWorld);
		}
		if (plugin)
		{
			// Their own client would be heard from if they were on.
			return gone(OFFLINE, name, lastAt, lastWorld);
		}
		return lastAt > 0 ? gone(UNKNOWN, name, lastAt, lastWorld) + tag : UNKNOWN + " " + bold(name) + " unknown";
	}

	/** The world of the newer sighting: the game's or this client's, else their client's last stats. */
	private static int lastWorld(long heardAt, LastSeen seen, Integer statsWorld)
	{
		if (seen != null && seen.world > 0 && (seen.at >= heardAt || statsWorld == null))
		{
			return seen.world;
		}
		return statsWorld == null ? 0 : statsWorld;
	}

	/**
	 * True when no one running the plugin is online as far as this client knows: not this player, and no member
	 * heard from lately that the game does not say logged out. Then nothing can keep the board current.
	 */
	static boolean nobodyOnline(Map<String, Long> heardBy, Self self, GamePresence game, long now)
	{
		if (self != null && self.online)
		{
			return false;
		}
		String me = self == null ? null : GroupMembers.key(self.name);
		for (Map.Entry<String, Long> e : heardBy.entrySet())
		{
			if (!e.getKey().equals(me) && Presence.online(e.getValue(), now)
				&& game.state(e.getKey()) != GamePresence.State.OFFLINE)
			{
				return false;
			}
		}
		return true;
	}

	/** The line under a board no one can edit: as of the last moment a plugin user was known online. */
	private static String stale(Map<String, Long> heardBy, Map<String, LastSeen> seenBy, Self self)
	{
		long asOf = self == null ? 0 : self.seenAt;
		for (long at : heardBy.values())
		{
			asOf = Math.max(asOf, at);
		}
		for (LastSeen s : seenBy.values())
		{
			asOf = s.plugin ? Math.max(asOf, s.at) : asOf;
		}
		return "*" + STALE + (asOf > 0 ? " (as of " + when(asOf) + ")" : "") + "*";
	}

	private static Set<String> names(GroupMembers roster, Map<String, Long> heard, Set<String> seen, Self self,
		GamePresence game)
	{
		if (roster.known())
		{
			return roster.names();
		}
		Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		names.addAll(heard.keySet());
		names.addAll(seen);
		names.addAll(game.names());
		if (self != null && !self.name.isEmpty())
		{
			names.add(self.name);
		}
		return new LinkedHashSet<>(names);
	}

	private static String online(String name, int world)
	{
		return ONLINE + " " + bold(name) + " online" + (world > 0 ? " (world " + world + ")" : "");
	}

	/** "last seen 2 hours ago on world 302", or "offline" when never seen; {@code icon} offline or unknown. */
	private static String gone(String icon, String name, long seenAt, int world)
	{
		if (seenAt <= 0)
		{
			return icon + " " + bold(name) + " offline";
		}
		return icon + " " + bold(name) + " last seen " + when(seenAt) + (world > 0 ? " on world " + world : "");
	}

	/** Discord's relative timestamp: "5 minutes ago", kept current by Discord itself. */
	private static String when(long at)
	{
		return "<t:" + at / 1000 + ":R>";
	}

	private static String bold(String name)
	{
		return "**" + name.replaceAll("([\\\\*_~`|>])", "\\\\$1") + "**";
	}
}
