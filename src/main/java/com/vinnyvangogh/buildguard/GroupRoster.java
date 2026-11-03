package com.vinnyvangogh.buildguard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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

	MemberStats statsFor(String member)
	{
		return stats.get(member);
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

	/**
	 * "Alex: online, world 302, Lumbridge" or "Sam: last seen 3h ago", one per
	 * member this client has anything for, sorted by name. Where a member is
	 * is only shown while they are online.
	 */
	List<String> memberLines(RegionNames regions, long now)
	{
		Map<String, Long> seen = new TreeMap<>(heard);
		for (Map.Entry<String, BankSnapshot> e : banks.entrySet())
		{
			seen.merge(e.getKey(), e.getValue().takenAt(), Math::max);
		}
		for (String name : stats.keySet())
		{
			seen.putIfAbsent(name, 0L);
		}
		List<String> out = new ArrayList<>();
		for (Map.Entry<String, Long> e : seen.entrySet())
		{
			StringBuilder line = new StringBuilder(e.getKey()).append(": ")
				.append(Presence.describe(e.getValue(), now));
			MemberStats s = stats.get(e.getKey());
			if (s != null && Presence.online(e.getValue(), now))
			{
				if (s.world() > 0)
				{
					line.append(", world ").append(s.world());
				}
				String place = regions.describe(s.region());
				if (place != null)
				{
					line.append(", ").append(place);
				}
			}
			out.add(line.toString());
		}
		return out;
	}

	/** Per member: worn, carried, rune pouch, quiver and seed vault, each only when known. */
	List<String> itemLines(ItemLookup items, long now)
	{
		Map<String, Boolean> names = new TreeMap<>();
		for (String n : carried.keySet())
		{
			names.put(n, true);
		}
		for (String n : extras.keySet())
		{
			names.put(n, true);
		}
		List<String> out = new ArrayList<>();
		for (String name : names.keySet())
		{
			GroupCodec.Carried c = carried.get(name);
			if (c != null)
			{
				addItems(out, name + " wearing: ", c.worn, items);
				addItems(out, name + " carrying: ", c.inventory, items);
			}
			MemberExtras x = extras.get(name);
			if (x != null)
			{
				addItems(out, name + " rune pouch: ", x.runePouch, items);
				addItems(out, name + " quiver: ", x.quiver, items);
				if (x.seedVaultAt >= 0)
				{
					long seeds = 0;
					for (int n : x.seedVault.values())
					{
						seeds += n;
					}
					out.add(name + " seed vault: " + x.seedVault.size() + " kinds, " + seeds + " seeds (last seen "
						+ BoostText.ago(now, x.seedVaultAt) + ")");
				}
			}
		}
		return out;
	}

	private static void addItems(List<String> out, String prefix, Map<Integer, Integer> counts, ItemLookup items)
	{
		if (!counts.isEmpty())
		{
			out.add(prefix + StorageLog.describe(counts, items));
		}
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

	/** Live data goes when the party does; banks, the log and storage are kept as "last seen". */
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

	void forgetLive(String member)
	{
		carried.remove(member);
		stats.remove(member);
	}

	void clearAll()
	{
		clearLive();
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

	/** "Alex: 812 items, 45.2M (last seen 2h ago)", sorted by name. */
	List<String> bankLines(ItemLookup items, long now)
	{
		List<String> out = new ArrayList<>();
		for (Map.Entry<String, BankSnapshot> e : banks().entrySet())
		{
			long value = 0;
			long count = 0;
			for (Map.Entry<Integer, Integer> item : e.getValue().items().entrySet())
			{
				value += items.price(item.getKey()) * item.getValue();
				count += item.getValue();
			}
			out.add(e.getKey() + ": " + e.getValue().items().size() + " kinds, " + count + " items, "
				+ BoostText.gp(value) + " gp (last seen " + BoostText.ago(now, e.getValue().takenAt()) + ")");
		}
		return out;
	}

	/**
	 * Per member: a header line ("Alex: world 302, region 12850"), a status
	 * line ("HP 72/99, Prayer 43/70, Run 100%, Spec 50%"), then every skill
	 * as "Attack 99 (+19)".
	 */
	List<List<String>> statsBlocks()
	{
		return statsBlocks(RegionNames.parse(""));
	}

	/** As {@link #statsBlocks()}, with the region as a place name where {@code regions} knows it. */
	List<List<String>> statsBlocks(RegionNames regions)
	{
		List<List<String>> out = new ArrayList<>();
		for (Map.Entry<String, MemberStats> e : new TreeMap<>(stats).entrySet())
		{
			MemberStats s = e.getValue();
			List<String> block = new ArrayList<>();
			String where = s.world() > 0 ? "world " + s.world() : "world ?";
			String place = regions.describe(s.region());
			if (place != null)
			{
				where += ", " + place;
			}
			block.add(e.getKey() + ": " + where);

			List<String> status = new ArrayList<>();
			MemberStats.Level hp = s.skills().get("Hitpoints");
			MemberStats.Level prayer = s.skills().get("Prayer");
			if (hp != null)
			{
				status.add("HP " + hp.boosted + "/" + hp.real);
			}
			if (prayer != null)
			{
				status.add("Prayer " + prayer.boosted + "/" + prayer.real);
			}
			status.add("Run " + s.runEnergy() + "%");
			status.add("Spec " + s.specialAttack() + "%");
			block.add(String.join(", ", status));

			List<String> levels = new ArrayList<>();
			for (Map.Entry<String, MemberStats.Level> skill : s.skills().entrySet())
			{
				levels.add(skill.getKey() + " " + BoostText.level(skill.getValue().real, skill.getValue().boosted));
			}
			block.add(String.join(", ", levels));
			out.add(block);
		}
		return out;
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
			out.add(new GroupSearch.Source(e.getKey(), "equipment", e.getValue().worn, -1));
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
