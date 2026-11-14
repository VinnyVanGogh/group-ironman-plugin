package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Who in the group is online, and on which world, as the game itself shows it (Board 2026-11-11): the same list
 * as the in-game Group tab, so members without the plugin are shown too.
 *
 * <p>Read on the client thread from the game's own Ironman Group channel,
 * {@code client.getClanChannel(ClanID.GROUP_IRONMAN)}: every {@code ClanChannelMember} in it is online on
 * {@code getWorld()}, and a roster member ({@link GroupMembers}, from {@code getClanSettings}) not in it is
 * offline. Without the channel, the Friends list ({@code client.getFriendContainer()}, a {@code Friend}'s world
 * is above 0 while online) speaks for the members on it. Before either loads nothing is known.
 *
 * <p>Presence and world only: stats, items and the rest still come from the party. Immutable, so any thread may
 * read the one {@link GroupService} holds.
 */
final class GamePresence
{
	enum Source
	{
		NONE,
		CHANNEL,
		FRIENDS
	}

	enum State
	{
		ONLINE,
		OFFLINE,
		/** The game has not said: the channel has not loaded, or the member is not on the list read. */
		UNKNOWN
	}

	static final GamePresence UNKNOWN = new GamePresence(Source.NONE, Collections.emptyMap(), Collections.emptyMap());

	private final Source source;
	/** Every member the game speaks for, by {@link GroupMembers#key}: their name as the game spells it. */
	private final Map<String, String> covered;
	/** The online ones' worlds, by key. */
	private final Map<String, Integer> worlds;

	private GamePresence(Source source, Map<String, String> covered, Map<String, Integer> worlds)
	{
		this.source = source;
		this.covered = covered;
		this.worlds = worlds;
	}

	/**
	 * {@code channel}: the Group channel's members and worlds, null when it has not loaded. {@code friends}: the
	 * Friends list's names and worlds (0 offline), null when not loaded; read only without the channel.
	 */
	static GamePresence read(GroupMembers roster, Map<String, Integer> channel, Map<String, Integer> friends)
	{
		if (channel != null)
		{
			Map<String, String> covered = new HashMap<>();
			roster.names().forEach(n -> covered.put(GroupMembers.key(n), GroupMembers.display(n)));
			return of(Source.CHANNEL, covered, channel, n -> true);
		}
		if (friends != null && roster.known())
		{
			return of(Source.FRIENDS, new HashMap<>(), friends, roster::allows);
		}
		return UNKNOWN;
	}

	/** {@code listed}'s names (world 0 offline) that {@code wanted} keeps, on top of {@code covered}. */
	private static GamePresence of(Source source, Map<String, String> covered, Map<String, Integer> listed,
		Predicate<String> wanted)
	{
		Map<String, Integer> worlds = new HashMap<>();
		listed.forEach((name, world) ->
		{
			String key = GroupMembers.key(name);
			if (!key.isEmpty() && wanted.test(name))
			{
				covered.put(key, GroupMembers.display(name));
				if (world != null && world > 0)
				{
					worlds.put(key, world);
				}
			}
		});
		return new GamePresence(source, Collections.unmodifiableMap(covered), Collections.unmodifiableMap(worlds));
	}

	boolean known()
	{
		return source != Source.NONE;
	}

	Source source()
	{
		return source;
	}

	State state(String name)
	{
		String key = GroupMembers.key(name);
		if (worlds.containsKey(key))
		{
			return State.ONLINE;
		}
		return covered.containsKey(key) ? State.OFFLINE : State.UNKNOWN;
	}

	/** Those of {@code names} that {@code after} has online and {@code before} did not: just came online. */
	static List<String> cameOnline(Collection<String> names, GamePresence before, GamePresence after)
	{
		List<String> out = new ArrayList<>();
		for (String name : names)
		{
			if (after.state(name) == State.ONLINE && before.state(name) != State.ONLINE)
			{
				out.add(name);
			}
		}
		return out;
	}

	/** The world they are on, 0 when not online or not known. */
	int world(String name)
	{
		return worlds.getOrDefault(GroupMembers.key(name), 0);
	}

	/** Every member the game speaks for, online or not, as the game spells them. */
	Set<String> names()
	{
		return new HashSet<>(covered.values());
	}

	/** The online members and their worlds, sorted by name. */
	Map<String, Integer> online()
	{
		Map<String, Integer> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		worlds.forEach((key, world) -> out.put(covered.get(key), world));
		return new LinkedHashMap<>(out);
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof GamePresence))
		{
			return false;
		}
		GamePresence p = (GamePresence) o;
		return source == p.source && covered.equals(p.covered) && worlds.equals(p.worlds);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(source, covered, worlds);
	}

	/**
	 * The others online, sorted by name, and their worlds (0 when not known): members whose clients are heard
	 * ({@code plugin}, from the party) unless the game says they logged out, and everyone the game says is on.
	 * The game's world wins. {@code self} is left out.
	 */
	static Map<String, Integer> othersOnline(Map<String, MemberStats> plugin, GamePresence game, String self)
	{
		Map<String, String> names = new HashMap<>();
		Map<String, Integer> worlds = new HashMap<>();
		plugin.forEach((name, stats) ->
		{
			if (game.state(name) != State.OFFLINE)
			{
				names.put(GroupMembers.key(name), name);
				worlds.put(GroupMembers.key(name), stats.world());
			}
		});
		game.worlds.forEach((key, world) ->
		{
			names.putIfAbsent(key, game.covered.get(key));
			worlds.put(key, world);
		});
		names.remove(GroupMembers.key(self));
		Map<String, Integer> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		names.forEach((key, name) -> out.put(name, worlds.get(key)));
		return new LinkedHashMap<>(out);
	}
}
