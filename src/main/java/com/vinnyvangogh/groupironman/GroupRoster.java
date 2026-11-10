package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything the group panel shows beyond builds: each member's last bank,
 * what they carry, their live stats, and group storage.
 *
 * <p>Keyed by display name, not party member id. Ids are new every time a
 * member joins a party, and a bank seen yesterday should still be there
 * today: that is the point of "last seen".
 *
 * <p>Written from the party thread and read on the Swing thread, so every map
 * is concurrent and every read returns a copy.
 */
final class GroupRoster
{
	private static final class Storage
	{
		final long at;
		final String by;
		final Map<Integer, Integer> items;

		Storage(long at, String by, Map<Integer, Integer> items)
		{
			this.at = at;
			this.by = by;
			this.items = items;
		}
	}

	private final Map<String, BankSnapshot> banks = new ConcurrentHashMap<>();
	private final Map<String, GroupCodec.Carried> carried = new ConcurrentHashMap<>();
	private final Map<String, MemberStats> stats = new ConcurrentHashMap<>();
	private final Map<String, MemberExtras> extras = new ConcurrentHashMap<>();
	private final Map<String, Long> heard = new ConcurrentHashMap<>();
	private final Set<String> strangers = ConcurrentHashMap.newKeySet();
	private final StorageLog log = new StorageLog();
	private final DropLog events = new DropLog();
	private volatile Storage storage;
	/** When this player was last seen logged in, by this client; 0 when never. */
	private volatile long lastPlayed;

	/** Something arrived from {@code member} at {@code at}. Only ever moves forward. */
	void heard(String member, long at)
	{
		heard.merge(member, at, Math::max);
	}

	Map<String, Long> lastHeard()
	{
		return new TreeMap<>(heard);
	}

	void markPlayed(long at)
	{
		lastPlayed = Math.max(lastPlayed, at);
	}

	long lastPlayed()
	{
		return lastPlayed;
	}

	/**
	 * Keeps the seed vault already known when the new one is unknown: a member
	 * who restarted and has not opened the vault since still has the same seeds.
	 */
	void putExtras(String member, MemberExtras incoming)
	{
		extras.merge(member, incoming, (old, now) ->
			now.seedVaultAt < 0 && old.seedVaultAt >= 0 ? now.withSeedVault(old.seedVault, old.seedVaultAt) : now);
	}

	Map<String, MemberExtras> extras()
	{
		return new TreeMap<>(extras);
	}

	void clearExtras()
	{
		extras.clear();
	}

	DropLog events()
	{
		return events;
	}

	/** How many members other than {@code self} were heard from recently. */
	int othersOnline(String self, long now)
	{
		int n = 0;
		for (Map.Entry<String, Long> e : heard.entrySet())
		{
			if (!e.getKey().equalsIgnoreCase(self) && Presence.online(e.getValue(), now))
			{
				n++;
			}
		}
		return n;
	}

	/** Positions of members heard from recently, for the world map. */
	Map<String, MemberStats> onlineStats(long now)
	{
		Map<String, MemberStats> out = new TreeMap<>();
		for (Map.Entry<String, MemberStats> e : stats.entrySet())
		{
			Long at = heard.get(e.getKey());
			if (at != null && Presence.online(at, now))
			{
				out.put(e.getKey(), e.getValue());
			}
		}
		return out;
	}

	/** {@link #onlineStats} without {@code self}: your own card is always live, the box is about everyone else. */
	Map<String, MemberStats> othersOnlineStats(String self, long now)
	{
		Map<String, MemberStats> out = onlineStats(now);
		out.keySet().removeIf(name -> name.equalsIgnoreCase(self));
		return out;
	}

	/** Keeps the newer of the two, so a stale resend cannot replace a fresher bank. */
	boolean putBank(String member, BankSnapshot bank)
	{
		BankSnapshot old = banks.get(member);
		if (old != null && old.takenAt() >= bank.takenAt())
		{
			return false;
		}
		banks.put(member, bank);
		return true;
	}

	void putCarried(String member, GroupCodec.Carried items)
	{
		carried.put(member, items);
	}

	void putStats(String member, MemberStats s)
	{
		stats.put(member, s);
	}

	/** Every member's last stats, online or not. */
	Map<String, MemberStats> allStats()
	{
		return new TreeMap<>(stats);
	}

	/** Every member's last inventory and worn items, online or not. */
	Map<String, GroupCodec.Carried> allCarried()
	{
		return new TreeMap<>(carried);
	}

	/** A party member not on the in-game group roster sent something; it was dropped. */
	void stranger(String name)
	{
		strangers.add(name);
	}

	Set<String> strangers()
	{
		return new TreeSet<>(strangers);
	}

	void clearStrangers()
	{
		strangers.clear();
	}

	/** Records a storage visit in the log and, when it is the newest seen, as the current storage. */
	boolean putStorage(StorageChange change)
	{
		Storage s = storage;
		if (s == null || change.at() >= s.at)
		{
			storage = new Storage(change.at(), change.member(), change.after());
		}
		return log.add(change);
	}

	void clearLive()
	{
		clearCarried();
		clearStats();
	}

	void clearCarried()
	{
		carried.clear();
	}

	void clearStats()
	{
		stats.clear();
	}

	void clearAll()
	{
		clearLive();
		strangers.clear();
		banks.clear();
		log.clear();
		storage = null;
		extras.clear();
		heard.clear();
		events.clear();
		lastPlayed = 0;
	}

	Map<String, BankSnapshot> banks()
	{
		return new TreeMap<>(banks);
	}

	StorageLog log()
	{
		return log;
	}

	/** Current group storage contents, empty when never seen. */
	Map<Integer, Integer> storageItems()
	{
		Storage s = storage;
		return s == null ? Collections.emptyMap() : s.items;
	}

	long storageAt()
	{
		Storage s = storage;
		return s == null ? -1 : s.at;
	}

	void restoreStorage(long at, String by, Map<Integer, Integer> items)
	{
		storage = new Storage(at, by, ItemCounts.sorted(items));
	}

	String storageBy()
	{
		Storage s = storage;
		return s == null ? "" : s.by;
	}

	boolean hasAnything()
	{
		return !banks.isEmpty() || !carried.isEmpty() || !stats.isEmpty() || storage != null || !extras.isEmpty()
			|| !events.entries().isEmpty();
	}

	/** Everything "who has it?" searches. */
	List<GroupSearch.Source> searchSources()
	{
		List<GroupSearch.Source> out = new ArrayList<>();
		for (Map.Entry<String, BankSnapshot> e : banks().entrySet())
		{
			out.add(new GroupSearch.Source(e.getKey(), "bank", e.getValue().items(), e.getValue().takenAt()));
		}
		for (Map.Entry<String, GroupCodec.Carried> e : new TreeMap<>(carried).entrySet())
		{
			out.add(new GroupSearch.Source(e.getKey(), "inventory", e.getValue().inventory, -1));
			// Reads "GroupShip equipped 1" in ::find and the Find tab (Board 2026-11-04).
			out.add(new GroupSearch.Source(e.getKey(), "equipped", e.getValue().worn, -1));
		}
		for (Map.Entry<String, MemberExtras> e : extras().entrySet())
		{
			out.add(new GroupSearch.Source(e.getKey(), "rune pouch", e.getValue().runePouch, -1));
			out.add(new GroupSearch.Source(e.getKey(), "quiver", e.getValue().quiver, -1));
			if (e.getValue().seedVaultAt >= 0)
			{
				out.add(new GroupSearch.Source(e.getKey(), "seed vault", e.getValue().seedVault,
					e.getValue().seedVaultAt));
			}
		}
		Storage s = storage;
		if (s != null)
		{
			out.add(new GroupSearch.Source("Group", "storage", s.items, s.at));
		}
		return out;
	}

	/** Every item id the panel may need a name for. */
	List<Integer> knownItemIds()
	{
		List<Integer> out = new ArrayList<>();
		for (GroupSearch.Source s : searchSources())
		{
			out.addAll(s.items.keySet());
		}
		for (StorageChange c : log.entries())
		{
			out.addAll(c.took().keySet());
			out.addAll(c.deposited().keySet());
		}
		for (GroupEvent e : events.entries())
		{
			out.addAll(e.items.keySet());
		}
		return out;
	}
}
