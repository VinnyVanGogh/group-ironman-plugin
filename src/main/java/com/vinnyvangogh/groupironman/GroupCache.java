package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * What survives a restart: members' last banks, the storage log, the last
 * group storage contents, members' rune pouch, quiver and seed vault, the
 * drops log, the Log tab's feed of every event ({@code feed}), when each member was last heard from and last seen
 * online ({@code seen}, for the live board) and when this player last
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
	/** A world number past this in the file is not a world: read as unknown. */
	private static final int MAX_WORLD = 65535;

	private GroupCache()
	{
	}

	/** One storage log entry. */
	private static JsonObject logEntry(StorageChange c)
	{
		JsonObject entry = new JsonObject();
		entry.addProperty("ts", c.at());
		entry.addProperty("by", c.member());
		entry.add("took", ItemCounts.toJson(c.took()));
		entry.add("dep", ItemCounts.toJson(c.deposited()));
		if (!c.shared())
		{
			// Only written when set, so a file without it reads as shared, as every visit was before.
			entry.addProperty("ns", true);
		}
		return entry;
	}

	static String write(Gson gson, GroupRoster roster)
	{
		return write(gson, roster, null);
	}

	/**
	 * With {@code webhooks}, the leader's signed {@link GroupWebhooks} document as the party carries it (null for
	 * none): the one place a member keeps the shared webhooks between sessions, never their RuneLite config.
	 */
	static String write(Gson gson, GroupRoster roster, String webhooks)
	{
		return write(gson, roster, webhooks, null);
	}

	/**
	 * With {@code shop}, the Shopping tab's lists, their history and members' quest progress ({@link
	 * ShoppingSync#toCache}); null for none. A build from before the Shopping tab ignores the key.
	 */
	static String write(Gson gson, GroupRoster roster, String webhooks, JsonObject shop)
	{
		JsonObject out = json(gson, roster);
		if (webhooks != null)
		{
			out.addProperty("gweb", webhooks);
		}
		if (shop != null)
		{
			out.add("shop", shop);
		}
		return gson.toJson(out);
	}

	/** The {@code shop} entry {@link #write} kept, or null when there is none or the text is unreadable. */
	static JsonElement shop(Gson gson, String json)
	{
		JsonObject root = root(gson, json);
		return root == null ? null : root.get("shop");
	}

	/** The document's JSON object; null for no text, unreadable text or anything but an object. */
	private static JsonObject root(Gson gson, String json)
	{
		return GroupCodec.object(gson, json, Integer.MAX_VALUE);
	}

	private static JsonObject json(Gson gson, GroupRoster roster)
	{
		JsonObject out = new JsonObject();
		out.addProperty("v", VERSION);

		out.add("banks", byMember(roster.banks(), b ->
		{
			JsonObject bank = new JsonObject();
			bank.addProperty("ts", b.takenAt());
			bank.add("i", ItemCounts.toJson(b.items()));
			return bank;
		}));

		JsonArray log = new JsonArray();
		for (StorageChange c : roster.log().entries())
		{
			log.add(logEntry(c));
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

		out.add("extras", byMember(roster.extras(), e ->
		{
			JsonObject x = new JsonObject();
			x.add("rp", ItemCounts.toJson(e.runePouch));
			x.add("q", ItemCounts.toJson(e.quiver));
			x.add("sv", ItemCounts.toJson(e.seedVault));
			x.addProperty("svts", e.seedVaultAt);
			return x;
		}));

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
		out.add("heard", byMember(roster.lastHeard(), JsonPrimitive::new));
		// When each member was last seen online ({@link LastSeen}): ts, w the world, p plugin.
		out.add("seen", byMember(roster.lastSeen(), s ->
		{
			JsonObject seen = new JsonObject();
			seen.addProperty("ts", s.at);
			seen.addProperty("w", s.world);
			seen.addProperty("p", s.plugin);
			return seen;
		}));
		out.addProperty("played", roster.lastPlayed());

		// Round 2: the last stats and carried items per member, for offline cards. Stored as
		// the party payload itself so the one decoder (and its limits) reads both.
		out.add("stats", byMember(roster.allStats(), s -> new JsonPrimitive(GroupCodec.encodeStats(gson, s))));
		// The whole payload, slots included: counts alone drew a noted stack unnoted until the member
		// resent (Board 2026-11-14). A build from before slots ignores the extra fields.
		out.add("carried", byMember(roster.allCarried(), c -> new JsonPrimitive(GroupCodec.encodeCarried(gson, c))));
		// Each member's last hiscore lookup: {"name": {"ts": 1700000000000, "l": {"Attack": 99}}}.
		out.add("hiscores", byMember(roster.hiscores().all(), h ->
		{
			JsonObject entry = new JsonObject();
			entry.addProperty("ts", h.at);
			JsonObject levels = new JsonObject();
			h.levels.forEach(levels::addProperty);
			entry.add("l", levels);
			return entry;
		}));
		return out;
	}

	/** {@code {"name": entry, ...}} in the map's order. */
	private static <T> JsonObject byMember(Map<String, T> members, Function<T, JsonElement> entry)
	{
		JsonObject out = new JsonObject();
		members.forEach((name, value) -> out.add(name, entry.apply(value)));
		return out;
	}

	/**
	 * Each of the first {@link #MAX_MEMBERS} entries of a by-member object; nothing when {@code in} is not one (a
	 * file from before that key). An entry that is not readable still counts toward the limit.
	 */
	private static void eachMember(JsonElement in, BiConsumer<String, JsonElement> read)
	{
		if (in == null || !in.isJsonObject())
		{
			return;
		}
		int n = 0;
		for (Map.Entry<String, JsonElement> e : in.getAsJsonObject().entrySet())
		{
			if (n++ < MAX_MEMBERS)
			{
				read.accept(e.getKey(), e.getValue());
			}
		}
	}

	/** {@link #eachMember}, for the entries that are objects. */
	private static void eachMemberObject(JsonElement in, BiConsumer<String, JsonObject> read)
	{
		eachMember(in, (name, value) ->
		{
			if (value.isJsonObject())
			{
				read.accept(name, value.getAsJsonObject());
			}
		});
	}

	/** Each object in the array {@code in}; nothing when it is not an array. */
	private static void eachObject(JsonElement in, Consumer<JsonObject> read)
	{
		if (in == null || !in.isJsonArray())
		{
			return;
		}
		for (JsonElement item : in.getAsJsonArray())
		{
			if (item.isJsonObject())
			{
				read.accept(item.getAsJsonObject());
			}
		}
	}

	/** {@code by}, the member an entry is filed under; null when missing. */
	private static String by(JsonObject entry)
	{
		JsonElement by = entry.get("by");
		return by == null || !by.isJsonPrimitive() ? null : by.getAsString();
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

	/** One {@code seen} entry; null without a time. */
	private static LastSeen lastSeen(JsonElement item)
	{
		if (!item.isJsonObject())
		{
			return null;
		}
		JsonObject s = item.getAsJsonObject();
		Long at = ItemCounts.longValue(s.get("ts"));
		if (at == null || at <= 0)
		{
			return null;
		}
		Long world = ItemCounts.longValue(s.get("w"));
		int w = world == null || world < 0 || world > MAX_WORLD ? 0 : world.intValue();
		return new LastSeen(at, w, isTrue(s.get("p")));
	}

	private static boolean isTrue(JsonElement p)
	{
		return p != null && p.isJsonPrimitive() && p.getAsJsonPrimitive().isBoolean() && p.getAsBoolean();
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
		String by = item.isJsonObject() ? by(item.getAsJsonObject()) : null;
		GroupEvent event = by == null ? null : GroupCodec.eventFrom(item.getAsJsonObject(), by);
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
		eachMember(in, (name, value) ->
		{
			GroupCodec.Decoded d = value.isJsonPrimitive() ? GroupCodec.decode(gson, value.getAsString()) : null;
			if (d != null && d.kind == GroupCodec.Kind.STATS)
			{
				roster.putStats(name, d.stats);
			}
			else if (d != null && d.kind == GroupCodec.Kind.CARRIED)
			{
				roster.putCarried(name, d.carried);
			}
		});
	}

	/** The webhooks document {@link #write(Gson, GroupRoster, String)} kept, or null; its signature is checked later. */
	static String webhooks(Gson gson, String json)
	{
		JsonObject root = root(gson, json);
		JsonElement w = root == null ? null : root.get("gweb");
		return w != null && w.isJsonPrimitive() ? w.getAsString() : null;
	}

	/** Loads into {@code roster}. Returns false, leaving it untouched, when the text is unreadable. */
	static boolean read(Gson gson, String json, GroupRoster roster)
	{
		JsonObject in = root(gson, json);
		Long v = in == null ? null : ItemCounts.longValue(in.get("v"));
		if (v == null || v < 1)
		{
			return false;
		}

		eachMemberObject(in.get("banks"), (name, bank) ->
		{
			Long ts = ItemCounts.longValue(bank.get("ts"));
			if (ts != null)
			{
				roster.putBank(name, new BankSnapshot(ts, ItemCounts.fromJson(bank.get("i"), GroupCodec.MAX_BANK_ITEMS)));
			}
		});

		eachObject(in.get("log"), entry ->
		{
			Long ts = ItemCounts.longValue(entry.get("ts"));
			String by = by(entry);
			if (ts != null && by != null)
			{
				roster.log().add(new StorageChange(ts, by,
					ItemCounts.fromJson(entry.get("took"), GroupCodec.MAX_STORAGE_ITEMS),
					ItemCounts.fromJson(entry.get("dep"), GroupCodec.MAX_STORAGE_ITEMS),
					Collections.emptyMap(), !isTrue(entry.get("ns"))));
			}
		});

		JsonElement storage = in.get("storage");
		if (storage != null && storage.isJsonObject())
		{
			JsonObject s = storage.getAsJsonObject();
			Long ts = ItemCounts.longValue(s.get("ts"));
			String by = by(s);
			if (ts != null)
			{
				roster.restoreStorage(ts, by == null ? "" : by,
					ItemCounts.fromJson(s.get("i"), GroupCodec.MAX_STORAGE_ITEMS));
			}
		}

		// Part 3. A part 2 file has none of these keys and loads as before.
		eachMemberObject(in.get("extras"), (name, x) ->
		{
			Long svts = ItemCounts.longValue(x.get("svts"));
			roster.putExtras(name, new MemberExtras(
				ItemCounts.fromJson(x.get("rp"), GroupCodec.MAX_POUCH_ITEMS),
				ItemCounts.fromJson(x.get("q"), GroupCodec.MAX_QUIVER_ITEMS),
				ItemCounts.fromJson(x.get("sv"), GroupCodec.MAX_SEED_VAULT_ITEMS),
				svts == null ? -1 : svts));
		});

		eachObject(in.get("events"), entry ->
		{
			String by = by(entry);
			GroupEvent event = by == null ? null : GroupCodec.eventFrom(entry, by);
			if (event != null && event.type.logged())
			{
				roster.events().add(event);
			}
		});

		// After "log" and "events": an older file's feed starts from them.
		readFeed(in.get("feed"), roster);

		eachMember(in.get("heard"), (name, value) ->
		{
			Long at = ItemCounts.longValue(value);
			if (at != null && at > 0)
			{
				roster.heard(name, at);
			}
		});

		// A file from before last-seen was kept has none, and loads as before.
		eachMember(in.get("seen"), (name, value) ->
		{
			LastSeen s = lastSeen(value);
			if (s != null)
			{
				roster.seen(name, s);
			}
		});

		Long played = ItemCounts.longValue(in.get("played"));
		if (played != null && played > 0)
		{
			roster.markPlayed(played);
		}
		readPayloads(gson, in.get("stats"), roster);
		readPayloads(gson, in.get("carried"), roster);
		// A file from before hiscores has no key and loads as before.
		eachMemberObject(in.get("hiscores"), (name, entry) ->
		{
			Long ts = ItemCounts.longValue(entry.get("ts"));
			JsonElement l = entry.get("l");
			if (ts != null && l != null && l.isJsonObject())
			{
				roster.hiscores().restore(name, ts, levelsFrom(l.getAsJsonObject()));
			}
		});
		return true;
	}
}
