package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.runelite.client.events.PluginMessage;

/**
 * GIM's side of the Player Compare group contract (docs/contracts/player-compare-group-v1.md): the Compare tab's
 * views (Leaderboard, Players, Banked, Group vs group, Item) are drawn by the separate Player Compare plugin as its
 * "Group" source. GIM decides who is in the group and what they have shared, and hands that over as a RuneLite
 * {@link PluginMessage} on the injected event bus. No reflection, no shared classes.
 *
 * <p>The builders are pure and static, so {@code PlayerCompareContractTest} checks each message against the shared
 * fixtures. Any thread: the bus posts synchronously on the caller's thread, and every map sent is a fresh copy.
 */
final class PlayerCompare
{
	static final String NAMESPACE = "player-compare";
	static final int VERSION = 1;
	static final String SOURCE = "gim-utilities";
	/** What Player Compare puts in {@code source} on the messages it sends. */
	static final String PLAYER_COMPARE = "player-compare";
	/** The one card the Compare tab shows while Player Compare has not answered. */
	static final String INSTALL_HINT = "Install Player Compare to compare your group";

	// GIM to Player Compare.
	static final String HELLO = "hello";
	static final String SNAPSHOT = "snapshot";
	static final String OPEN = "open";
	/** Both ways: the sender is stopping. */
	static final String BYE = "bye";
	// Player Compare to GIM.
	static final String READY = "ready";
	static final String REQUEST = "request";

	/** One member's shared figures, as the roster and the shared progress hold them. */
	static final class Member
	{
		final String name;
		final Map<String, Integer> levels;
		final int questPoints;
		final Map<String, Double> bests;
		/** Hiscore counts by hiscore name; null when never read. */
		final Map<String, Integer> scores;
		/** Null when this member has not shared what they carry. */
		final Map<Integer, Integer> worn;
		final Map<Integer, Integer> inventory;
		/** The last bank they closed; null when never shared. */
		final Map<Integer, Integer> bank;

		Member(String name, Map<String, Integer> levels, int questPoints, Map<String, Double> bests,
			Map<String, Integer> scores, Map<Integer, Integer> worn, Map<Integer, Integer> inventory,
			Map<Integer, Integer> bank)
		{
			this.name = name;
			this.levels = levels;
			this.questPoints = questPoints;
			this.bests = bests;
			this.scores = scores;
			this.worn = worn;
			this.inventory = inventory;
			this.bank = bank;
		}
	}

	private final Consumer<Object> bus;
	private volatile boolean installed;
	private volatile String version = "";

	PlayerCompare(Consumer<Object> bus)
	{
		this.bus = bus;
	}

	/** Player Compare answered and has not said bye. */
	boolean installed()
	{
		return installed;
	}

	String version()
	{
		return version;
	}

	void send(String name, Map<String, Object> data)
	{
		bus.accept(new PluginMessage(NAMESPACE, name, data));
	}

	/**
	 * A message from Player Compare. Returns true when GIM should send a fresh {@link #SNAPSHOT}: a {@link #READY}
	 * (a restarted Player Compare holds nothing) or a {@link #REQUEST}. Anything else, ours included, is false.
	 */
	boolean receive(PluginMessage m)
	{
		Map<String, Object> data = m.getData();
		if (!NAMESPACE.equals(m.getNamespace()) || data == null || !PLAYER_COMPARE.equals(data.get("source")))
		{
			return false;
		}
		switch (m.getName())
		{
			case BYE:
				installed = false;
				return false;
			case READY:
				installed = true;
				version = String.valueOf(data.getOrDefault("version", ""));
				return true;
			case REQUEST:
				// A request proves it is there, even if its ready was missed.
				installed = true;
				return true;
			default:
				return false;
		}
	}

	private static Map<String, Object> envelope()
	{
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("v", VERSION);
		m.put("source", SOURCE);
		return m;
	}

	static Map<String, Object> hello(String version)
	{
		Map<String, Object> m = envelope();
		m.put("version", version);
		return m;
	}

	static Map<String, Object> bye()
	{
		return envelope();
	}

	/** Ask Player Compare to show its panel on the group view. */
	static Map<String, Object> open()
	{
		Map<String, Object> m = envelope();
		m.put("view", "group");
		return m;
	}

	/**
	 * Everything the Compare tab read: the members in the group's order (that order picks each member's colour),
	 * this player's name, and the group storage's items (counted once for banked experience).
	 */
	static Map<String, Object> snapshot(String self, List<Member> members, Map<Integer, Integer> storage)
	{
		Map<String, Object> m = envelope();
		m.put("self", self);
		List<Object> list = new ArrayList<>();
		for (Member member : members)
		{
			Map<String, Object> one = new LinkedHashMap<>();
			one.put("name", member.name);
			one.put("levels", new TreeMap<>(member.levels));
			one.put("questPoints", member.questPoints);
			one.put("bests", new TreeMap<>(member.bests));
			one.put("scores", member.scores == null ? null : new TreeMap<>(member.scores));
			one.put("carried", member.worn != null);
			one.put("worn", items(member.worn));
			one.put("inventory", items(member.inventory));
			one.put("bank", member.bank == null ? null : items(member.bank));
			list.add(one);
		}
		m.put("members", list);
		m.put("storage", items(storage));
		return m;
	}

	/** Item id (as a decimal string, so the map reads the same in JSON) to quantity, in id order. */
	static Map<String, Object> items(Map<Integer, Integer> in)
	{
		Map<String, Object> out = new LinkedHashMap<>();
		if (in != null)
		{
			for (Map.Entry<Integer, Integer> e : new TreeMap<>(in).entrySet())
			{
				out.put(String.valueOf(e.getKey()), e.getValue());
			}
		}
		return out;
	}

	/** The group's names with this player added when missing, in the group's order. */
	static List<String> names(Collection<String> group, String self)
	{
		List<String> names = new ArrayList<>(group);
		if (self != null && !self.isEmpty() && names.stream().noneMatch(n -> n.equalsIgnoreCase(self)))
		{
			names.add(self);
		}
		return names;
	}
}
