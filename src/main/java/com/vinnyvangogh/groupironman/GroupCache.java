package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What survives a restart: members' last banks, the storage log, the last
 * group storage contents, members' rune pouch, quiver and seed vault, the
 * drops log, the Log tab's feed of every event ({@code feed}), when each member was last heard from and when this player last
 * played, as one JSON document. Each member's last stats (world, place, HP,
 * prayer, run, levels) and carried items are kept too, so an offline member's
 * card shows where they were and what they had the last time you were both
 * online. Hiscore levels for members who do not share stats ({@link HiscoreBook}) are kept with when they were
 * read, so they show across restarts too.
 *
 * <p>Pure, {@code String} in and out. The plugin reads and writes the file
 * ({@code .runelite/gim-utilities/group-cache.json}) off the client thread. A
 * file this build cannot read is treated as empty, never as an error: losing
 * a cache costs one bank visit per member.
 */
final class GroupCache
{
	static final int VERSION = 1;
	static final int MAX_MEMBERS = 50;

	private GroupCache()
	{
	}

	static String write(Gson gson, GroupRoster roster)
	{
		JsonObject out = new JsonObject();
		out.addProperty("v", VERSION);

		JsonObject banks = new JsonObject();
		for (Map.Entry<String, BankSnapshot> e : roster.banks().entrySet())
		{
			JsonObject bank = new JsonObject();
			bank.addProperty("ts", e.getValue().takenAt());
			bank.add("i", ItemCounts.toJson(e.getValue().items()));
			banks.add(e.getKey(), bank);
		}
		out.add("banks", banks);

		JsonArray log = new JsonArray();
		for (StorageChange c : roster.log().entries())
		{
			JsonObject entry = new JsonObject();
			entry.addProperty("ts", c.at());
			entry.addProperty("by", c.member());
			entry.add("took", ItemCounts.toJson(c.took()));
			entry.add("dep", ItemCounts.toJson(c.deposited()));
			log.add(entry);
		}
		out.add("log", log);

		if (roster.storageAt() >= 0)
		{
			JsonObject storage = new JsonObject();
			storage.addProperty("ts", roster.storageAt());
			storage.addProperty("by", roster.storageBy());
			storage.add("i", ItemCounts.toJson(roster.storageItems()));
			out.add("storage", storage);
		}

		JsonObject extras = new JsonObject();
		for (Map.Entry<String, MemberExtras> e : roster.extras().entrySet())
		{
			JsonObject x = new JsonObject();
			x.add("rp", ItemCounts.toJson(e.getValue().runePouch));
			x.add("q", ItemCounts.toJson(e.getValue().quiver));
			x.add("sv", ItemCounts.toJson(e.getValue().seedVault));
			x.addProperty("svts", e.getValue().seedVaultAt);
			extras.add(e.getKey(), x);
		}
		out.add("extras", extras);

		JsonArray events = new JsonArray();
		for (GroupEvent e : roster.events().entries())
		{
			JsonObject entry = new JsonObject();
			entry.addProperty("t", e.type.code);
			entry.addProperty("ts", e.at);
			entry.addProperty("by", e.member);
			entry.addProperty("s", e.text);
			entry.add("i", ItemCounts.toJson(e.items));
			events.add(entry);
		}
		out.add("events", events);
		out.add("feed", feedJson(roster));

		JsonObject heard = new JsonObject();
		for (Map.Entry<String, Long> e : roster.lastHeard().entrySet())
		{
			heard.addProperty(e.getKey(), e.getValue());
		}
		out.add("heard", heard);
		out.addProperty("played", roster.lastPlayed());

		// Round 2: the last stats and carried items per member, for offline cards. Stored as
		// the party payload itself so the one decoder (and its limits) reads both.
		JsonObject stats = new JsonObject();
		for (Map.Entry<String, MemberStats> e : roster.allStats().entrySet())
		{
			stats.addProperty(e.getKey(), GroupCodec.encodeStats(gson, e.getValue()));
		}
		out.add("stats", stats);
		JsonObject carried = new JsonObject();
		for (Map.Entry<String, GroupCodec.Carried> e : roster.allCarried().entrySet())
		{
			carried.addProperty(e.getKey(), GroupCodec.encodeCarried(gson, e.getValue().inventory, e.getValue().worn));
		}
		out.add("carried", carried);
		out.add("hiscores", hiscoresJson(roster.hiscores()));
		return gson.toJson(out);
	}

	/** Each member's last hiscore lookup: {@code {"name": {"ts": 1700000000000, "l": {"Attack": 99}}}}. */
	private static JsonObject hiscoresJson(HiscoreBook book)
	{
		JsonObject out = new JsonObject();
		for (Map.Entry<String, HiscoreBook.Entry> e : book.all().entrySet())
		{
			JsonObject entry = new JsonObject();
			entry.addProperty("ts", e.getValue().at);
			JsonObject levels = new JsonObject();
			e.getValue().levels.forEach(levels::addProperty);
			entry.add("l", levels);
			out.add(e.getKey(), entry);
		}
		return out;
	}

	/** A file from before hiscores has no key and loads as before. */
	private static void readHiscores(JsonElement in, HiscoreBook book)
	{
		if (in == null || !in.isJsonObject())
		{
			return;
		}
		int n = 0;
		for (Map.Entry<String, JsonElement> e : in.getAsJsonObject().entrySet())
		{
			if (n++ >= MAX_MEMBERS || !e.getValue().isJsonObject())
			{
				continue;
			}
			JsonObject entry = e.getValue().getAsJsonObject();
			Long ts = ItemCounts.longValue(entry.get("ts"));
			JsonElement l = entry.get("l");
			if (ts != null && l != null && l.isJsonObject())
			{
				book.restore(e.getKey(), ts, levelsFrom(l.getAsJsonObject()));
			}
		}
	}

	/** The numbers in {@code l}; {@link HiscoreBook#restore} drops the ones out of range. */
	private static Map<String, Integer> levelsFrom(JsonObject l)
	{
		Map<String, Integer> levels = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> s : l.entrySet())
		{
			Long level = ItemCounts.longValue(s.getValue());
			if (level != null)
			{
				levels.put(s.getKey(), (int) Math.max(0, Math.min(level, Integer.MAX_VALUE)));
			}
		}
		return levels;
	}

	/**
	 * The Log tab's feed: every event as the party sends it ({@link GroupCodec#eventJson}),
	 * plus {@code by} and, for a storage summary, {@code tk} its withdrawals.
	 */
	private static JsonArray feedJson(GroupRoster roster)
	{
		JsonArray feed = new JsonArray();
		for (GroupEvent e : roster.feed().entries())
		{
			JsonObject entry = GroupCodec.eventJson(e);
			entry.addProperty("by", e.member);
			if (!e.taken.isEmpty())
			{
				entry.add("tk", ItemCounts.toJson(e.taken));
			}
			feed.add(entry);
		}
		return feed;
	}

	/**
	 * The feed. A file from before it existed has none: the feed starts from
	 * that file's drops log and storage log, so the Log tab keeps its history.
	 */
	private static void readFeed(JsonElement feed, GroupRoster roster)
	{
		if (feed == null || !feed.isJsonArray())
		{
			seedFeed(roster);
			return;
		}
		for (JsonElement item : feed.getAsJsonArray())
		{
			GroupEvent event = feedEvent(item);
			if (event != null)
			{
				roster.feed().add(event);
			}
		}
	}

	/** One feed entry; null when it has no member or is not an event this build reads. */
	private static GroupEvent feedEvent(JsonElement item)
	{
		JsonElement by = item.isJsonObject() ? item.getAsJsonObject().get("by") : null;
		GroupEvent event = by == null || !by.isJsonPrimitive() ? null
			: GroupCodec.eventFrom(item.getAsJsonObject(), by.getAsString());
		return event == null ? null : event.toBuilder()
			.taken(ItemCounts.fromJson(item.getAsJsonObject().get("tk"), GroupCodec.MAX_STORAGE_ITEMS)).build();
	}

	/** A feed from an older file's drops log and storage log, already read into {@code roster}. */
	private static void seedFeed(GroupRoster roster)
	{
		for (GroupEvent e : roster.events().entries())
		{
			roster.feed().add(e);
		}
		for (StorageChange c : roster.log().entries())
		{
			roster.feed().add(GroupEvent.of(GroupEvent.Type.STORAGE_SUMMARY, c.at(), c.member())
				.items(c.deposited()).taken(c.took()).build());
		}
	}

	private static void readPayloads(Gson gson, JsonElement in, GroupRoster roster)
	{
		if (in == null || !in.isJsonObject())
		{
			return;
		}
		int n = 0;
		for (Map.Entry<String, JsonElement> e : in.getAsJsonObject().entrySet())
		{
			if (n++ >= MAX_MEMBERS || !e.getValue().isJsonPrimitive())
			{
				continue;
			}
			GroupCodec.Decoded d = GroupCodec.decode(gson, e.getValue().getAsString());
			if (d != null && d.kind == GroupCodec.Kind.STATS)
			{
				roster.putStats(e.getKey(), d.stats);
			}
			else if (d != null && d.kind == GroupCodec.Kind.CARRIED)
			{
				roster.putCarried(e.getKey(), d.carried);
			}
		}
	}

	/** Loads into {@code roster}. Returns false, leaving it untouched, when the text is unreadable. */
	static boolean read(Gson gson, String json, GroupRoster roster)
	{
		JsonObject in;
		try
		{
			JsonElement root = json == null ? null : gson.fromJson(json, JsonElement.class);
			if (root == null || !root.isJsonObject())
			{
				return false;
			}
			in = root.getAsJsonObject();
		}
		catch (RuntimeException e)
		{
			return false;
		}
		Long v = ItemCounts.longValue(in.get("v"));
		if (v == null || v < 1)
		{
			return false;
		}

		JsonElement banks = in.get("banks");
		if (banks != null && banks.isJsonObject())
		{
			int n = 0;
			for (Map.Entry<String, JsonElement> e : banks.getAsJsonObject().entrySet())
			{
				if (n++ >= MAX_MEMBERS || !e.getValue().isJsonObject())
				{
					continue;
				}
				JsonObject bank = e.getValue().getAsJsonObject();
				Long ts = ItemCounts.longValue(bank.get("ts"));
				if (ts != null)
				{
					roster.putBank(e.getKey(), new BankSnapshot(ts,
						ItemCounts.fromJson(bank.get("i"), GroupCodec.MAX_BANK_ITEMS)));
				}
			}
		}

		JsonElement log = in.get("log");
		if (log != null && log.isJsonArray())
		{
			for (JsonElement item : log.getAsJsonArray())
			{
				if (!item.isJsonObject())
				{
					continue;
				}
				JsonObject entry = item.getAsJsonObject();
				Long ts = ItemCounts.longValue(entry.get("ts"));
				JsonElement by = entry.get("by");
				if (ts == null || by == null || !by.isJsonPrimitive())
				{
					continue;
				}
				roster.log().add(new StorageChange(ts, by.getAsString(),
					ItemCounts.fromJson(entry.get("took"), GroupCodec.MAX_STORAGE_ITEMS),
					ItemCounts.fromJson(entry.get("dep"), GroupCodec.MAX_STORAGE_ITEMS),
					Collections.emptyMap()));
			}
		}

		JsonElement storage = in.get("storage");
		if (storage != null && storage.isJsonObject())
		{
			JsonObject s = storage.getAsJsonObject();
			Long ts = ItemCounts.longValue(s.get("ts"));
			JsonElement by = s.get("by");
			if (ts != null)
			{
				roster.restoreStorage(ts, by != null && by.isJsonPrimitive() ? by.getAsString() : "",
					ItemCounts.fromJson(s.get("i"), GroupCodec.MAX_STORAGE_ITEMS));
			}
		}

		// Part 3. A part 2 file has none of these keys and loads as before.
		JsonElement extras = in.get("extras");
		if (extras != null && extras.isJsonObject())
		{
			int n = 0;
			for (Map.Entry<String, JsonElement> e : extras.getAsJsonObject().entrySet())
			{
				if (n++ >= MAX_MEMBERS || !e.getValue().isJsonObject())
				{
					continue;
				}
				JsonObject x = e.getValue().getAsJsonObject();
				Long svts = ItemCounts.longValue(x.get("svts"));
				roster.putExtras(e.getKey(), new MemberExtras(
					ItemCounts.fromJson(x.get("rp"), GroupCodec.MAX_POUCH_ITEMS),
					ItemCounts.fromJson(x.get("q"), GroupCodec.MAX_QUIVER_ITEMS),
					ItemCounts.fromJson(x.get("sv"), GroupCodec.MAX_SEED_VAULT_ITEMS),
					svts == null ? -1 : svts));
			}
		}

		JsonElement events = in.get("events");
		if (events != null && events.isJsonArray())
		{
			for (JsonElement item : events.getAsJsonArray())
			{
				if (!item.isJsonObject())
				{
					continue;
				}
				JsonObject entry = item.getAsJsonObject();
				JsonElement by = entry.get("by");
				GroupEvent event = by == null || !by.isJsonPrimitive() ? null
					: GroupCodec.eventFrom(entry, by.getAsString());
				if (event != null && event.type.logged())
				{
					roster.events().add(event);
				}
			}
		}

		// After "log" and "events": an older file's feed starts from them.
		readFeed(in.get("feed"), roster);

		JsonElement heard = in.get("heard");
		if (heard != null && heard.isJsonObject())
		{
			int n = 0;
			for (Map.Entry<String, JsonElement> e : heard.getAsJsonObject().entrySet())
			{
				Long at = ItemCounts.longValue(e.getValue());
				if (n++ < MAX_MEMBERS && at != null && at > 0)
				{
					roster.heard(e.getKey(), at);
				}
			}
		}

		Long played = ItemCounts.longValue(in.get("played"));
		if (played != null && played > 0)
		{
			roster.markPlayed(played);
		}
		readPayloads(gson, in.get("stats"), roster);
		readPayloads(gson, in.get("carried"), roster);
		readHiscores(in.get("hiscores"), roster.hiscores());
		return true;
	}
}
