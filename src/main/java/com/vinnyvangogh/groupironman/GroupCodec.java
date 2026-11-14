package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The JSON inside a {@link VvgGroupIronmanMessage}: one of six kinds, each
 * tagged {@code k}.
 *
 * <ul>
 * <li>{@code bank}: one part of a bank snapshot. A bank is split into parts
 * so no single message exceeds {@link #MAX_PAYLOAD_CHARS}.</li>
 * <li>{@code carried}: inventory and worn items.</li>
 * <li>{@code storage}: one group storage visit, see {@link StorageChange}.</li>
 * <li>{@code stats}: live levels, status and position, see {@link MemberStats}.</li>
 * <li>{@code extras}: rune pouch, quiver and seed vault, see {@link MemberExtras}.</li>
 * <li>{@code events}: drops and new collection log slots, split like a bank,
 * see {@link GroupEvent}.</li>
 * </ul>
 *
 * <p><b>Why a size cap of our own.</b> RuneLite 1.12.39's {@code WSClient}
 * puts the Gson JSON of a party message into a protobuf {@code Party.Data}
 * and hands it to OkHttp's {@code WebSocket.send(ByteString)}; the client
 * itself imposes no limit. OkHttp 3.14.9 only refuses a send that would push
 * its outgoing queue past {@code RealWebSocket.MAX_QUEUE_SIZE} (16 MiB), and
 * the party relay's own limit is not published. So this plugin picks a budget
 * far below anything a relay is likely to enforce and splits to fit it.
 *
 * <p><b>Version tolerance</b>: any {@code v >= 1} is read, unknown kinds and fields are ignored, missing
 * fields decode to empty, and every list is capped.
 */
final class GroupCodec
{
	static final int PROTOCOL = 1;

	/** The largest payload this build will send in one message. */
	static final int MAX_PAYLOAD_CHARS = 6000;

	/** A bank holds at most 1,410 distinct items with every expansion bought; the rest is headroom. */
	static final int MAX_BANK_ITEMS = 1600;
	static final int MAX_BANK_PARTS = 16;
	static final int MAX_STORAGE_ITEMS = 500;
	static final int MAX_CARRIED_ITEMS = 64;
	static final int MAX_SKILLS = 40;

	/** Room for the header fields of a bank part, generously. */
	private static final int BANK_HEADER_CHARS = 120;

	/** The rune pouch has four slots, six with the divine upgrade; the rest is headroom. */
	static final int MAX_POUCH_ITEMS = 8;
	static final int MAX_QUIVER_ITEMS = 2;
	/**
	 * Chosen so the fullest extras message fits {@link #MAX_PAYLOAD_CHARS}: an
	 * item pair is at most 19 characters, and 250 of them is 4,750. The game has
	 * well under 250 kinds of seed.
	 */
	static final int MAX_SEED_VAULT_ITEMS = 250;
	static final int MAX_EVENTS_PER_MESSAGE = 100;
	/** World tiles stay well under this on both axes. */
	static final int MAX_COORD = 1 << 15;

	/** Room for {@code {"v":1,"k":"events","e":[]}} and change. */
	private static final int EVENTS_HEADER_CHARS = 40;

	/**
	 * The {@code k} on the wire. {@link #EXTRAS} and {@link #EVENTS} came in
	 * part 3; a part 2 build reads them as an unknown kind and ignores them.
	 */
	enum Kind
	{
		BANK, CARRIED, STORAGE, STATS, EXTRAS, EVENTS
	}

	/** One part of a split bank snapshot. */
	static final class BankPart
	{
		final long takenAt;
		final int part;
		final int of;
		final Map<Integer, Integer> items;

		BankPart(long takenAt, int part, int of, Map<Integer, Integer> items)
		{
			this.takenAt = takenAt;
			this.part = part;
			this.of = of;
			this.items = items;
		}
	}

	static final class Carried
	{
		final Map<Integer, Integer> inventory;
		final Map<Integer, Integer> worn;

		Carried(Map<Integer, Integer> inventory, Map<Integer, Integer> worn)
		{
			this.inventory = ItemCounts.sorted(inventory);
			this.worn = ItemCounts.sorted(worn);
		}
	}

	/** Exactly one of the fields is set, matching {@link #kind}. */
	static final class Decoded
	{
		final Kind kind;
		final BankPart bank;
		final Carried carried;
		final StorageChange storage;
		final MemberStats stats;
		final MemberExtras extras;
		/** Each with an empty member: the receiver files them under the party member they came from. */
		final List<GroupEvent> events;

		private Decoded(Kind kind, BankPart bank, Carried carried, StorageChange storage, MemberStats stats,
			MemberExtras extras, List<GroupEvent> events)
		{
			this.kind = kind;
			this.bank = bank;
			this.carried = carried;
			this.storage = storage;
			this.stats = stats;
			this.extras = extras;
			this.events = events;
		}

		static Decoded of(BankPart bank)
		{
			return new Decoded(Kind.BANK, bank, null, null, null, null, null);
		}

		static Decoded of(Carried carried)
		{
			return new Decoded(Kind.CARRIED, null, carried, null, null, null, null);
		}

		static Decoded of(StorageChange storage)
		{
			return new Decoded(Kind.STORAGE, null, null, storage, null, null, null);
		}

		static Decoded of(MemberStats stats)
		{
			return new Decoded(Kind.STATS, null, null, null, stats, null, null);
		}

		static Decoded of(MemberExtras extras)
		{
			return new Decoded(Kind.EXTRAS, null, null, null, null, extras, null);
		}

		static Decoded of(List<GroupEvent> events)
		{
			return new Decoded(Kind.EVENTS, null, null, null, null, null, events);
		}
	}

	private GroupCodec()
	{
	}

	/** One payload per part, each within {@link #MAX_PAYLOAD_CHARS}. An empty bank is one empty part. */
	static List<String> encodeBank(Gson gson, BankSnapshot bank)
	{
		List<Map<Integer, Integer>> chunks = new ArrayList<>();
		Map<Integer, Integer> current = new TreeMap<>();
		int chars = BANK_HEADER_CHARS;
		int kept = 0;
		for (Map.Entry<Integer, Integer> e : bank.items().entrySet())
		{
			if (kept >= MAX_BANK_ITEMS)
			{
				break;
			}
			int pair = digits(e.getKey()) + digits(e.getValue()) + 2;
			if (chars + pair > MAX_PAYLOAD_CHARS && !current.isEmpty())
			{
				chunks.add(current);
				current = new TreeMap<>();
				chars = BANK_HEADER_CHARS;
			}
			current.put(e.getKey(), e.getValue());
			chars += pair;
			kept++;
		}
		chunks.add(current);

		List<String> out = new ArrayList<>();
		for (int i = 0; i < chunks.size() && i < MAX_BANK_PARTS; i++)
		{
			JsonObject o = header(Kind.BANK);
			o.addProperty("ts", bank.takenAt());
			o.addProperty("p", i);
			o.addProperty("n", Math.min(chunks.size(), MAX_BANK_PARTS));
			o.add("i", ItemCounts.toJson(chunks.get(i)));
			out.add(gson.toJson(o));
		}
		return out;
	}

	static String encodeCarried(Gson gson, Map<Integer, Integer> inventory, Map<Integer, Integer> worn)
	{
		JsonObject o = header(Kind.CARRIED);
		o.add("inv", ItemCounts.toJson(inventory));
		o.add("eq", ItemCounts.toJson(worn));
		return gson.toJson(o);
	}

	static String encodeStorage(Gson gson, StorageChange change)
	{
		JsonObject o = header(Kind.STORAGE);
		o.addProperty("ts", change.at());
		o.add("took", ItemCounts.toJson(change.took()));
		o.add("dep", ItemCounts.toJson(change.deposited()));
		o.add("now", ItemCounts.toJson(change.after()));
		return gson.toJson(o);
	}

	static String encodeStats(Gson gson, MemberStats stats)
	{
		JsonObject o = header(Kind.STATS);
		JsonObject skills = new JsonObject();
		for (Map.Entry<String, MemberStats.Level> e : stats.skills().entrySet())
		{
			JsonArray pair = new JsonArray();
			pair.add(e.getValue().real);
			pair.add(e.getValue().boosted);
			skills.add(e.getKey(), pair);
		}
		o.add("s", skills);
		o.addProperty("run", stats.runEnergy());
		o.addProperty("spec", stats.specialAttack());
		o.addProperty("w", stats.world());
		o.addProperty("r", stats.region());
		if (stats.hasPosition())
		{
			o.addProperty("x", stats.x());
			o.addProperty("y", stats.y());
			o.addProperty("pl", stats.plane());
		}
		return gson.toJson(o);
	}

	/** One message; the seed vault is cut to {@link #MAX_SEED_VAULT_ITEMS} so it always fits. */
	static String encodeExtras(Gson gson, MemberExtras extras)
	{
		JsonObject o = header(Kind.EXTRAS);
		o.add("rp", ItemCounts.toJson(first(extras.runePouch, MAX_POUCH_ITEMS)));
		o.add("q", ItemCounts.toJson(first(extras.quiver, MAX_QUIVER_ITEMS)));
		o.add("sv", ItemCounts.toJson(first(extras.seedVault, MAX_SEED_VAULT_ITEMS)));
		o.addProperty("svts", extras.seedVaultAt);
		return gson.toJson(o);
	}

	/**
	 * As many messages as it takes to fit {@link #MAX_PAYLOAD_CHARS}, none for
	 * no events. Sizes are measured on the JSON Gson actually writes, so names
	 * Gson escapes are counted at their escaped length.
	 */
	static List<String> encodeEvents(Gson gson, List<GroupEvent> events)
	{
		List<String> out = new ArrayList<>();
		JsonArray current = new JsonArray();
		int chars = EVENTS_HEADER_CHARS;
		for (GroupEvent e : events)
		{
			if (!e.type.travels())
			{
				continue;
			}
			JsonObject event = eventJson(e);
			int size = gson.toJson(event).length() + 1;
			if (current.size() > 0 && (chars + size > MAX_PAYLOAD_CHARS || current.size() >= MAX_EVENTS_PER_MESSAGE))
			{
				out.add(eventsPayload(gson, current));
				current = new JsonArray();
				chars = EVENTS_HEADER_CHARS;
			}
			current.add(event);
			chars += size;
		}
		if (current.size() > 0)
		{
			out.add(eventsPayload(gson, current));
		}
		return out;
	}

	/**
	 * One event: {@code t} type code, {@code ts}, {@code s} text, {@code i}
	 * items, and only when set {@code n} number, {@code x} detail, {@code w}
	 * world, {@code l} location. A 1.0.0 build reads {@code d} and {@code c}
	 * exactly as before and skips every other code.
	 */
	static JsonObject eventJson(GroupEvent e)
	{
		JsonObject event = new JsonObject();
		event.addProperty("t", e.type.code);
		event.addProperty("ts", e.at);
		event.addProperty("s", e.text);
		if (!e.items.isEmpty() || e.type == GroupEvent.Type.DROP)
		{
			event.add("i", ItemCounts.toJson(e.items));
		}
		if (e.number != 0)
		{
			event.addProperty("n", e.number);
		}
		if (!e.detail.isEmpty())
		{
			event.addProperty("x", e.detail);
		}
		if (e.world > 0)
		{
			event.addProperty("w", e.world);
		}
		if (!e.location.isEmpty())
		{
			event.addProperty("l", e.location);
		}
		return event;
	}

	/** The event in {@code o}, filed under {@code member}; null when a required field is missing or the code unknown. */
	static GroupEvent eventFrom(JsonObject o, String member)
	{
		Long ts = ItemCounts.longValue(o.get("ts"));
		GroupEvent.Type type = GroupEvent.Type.of(string(o.get("t")));
		JsonElement s = o.get("s");
		if (ts == null || ts < 0 || type == null || s == null || !s.isJsonPrimitive())
		{
			return null;
		}
		return GroupEvent.of(type, ts, member).text(s.getAsString())
			.items(ItemCounts.fromJson(o.get("i"), GroupEvent.MAX_DROP_ITEMS))
			.number(number(o.get("n"), Long.MIN_VALUE, Long.MAX_VALUE))
			.detail(string(o.get("x")))
			.world((int) number(o.get("w"), 0, MAX_COORD))
			.location(string(o.get("l")))
			.build();
	}

	private static String string(JsonElement e)
	{
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	/** The whole number in {@code e}, 0 when missing or outside {@code min..max}. */
	private static long number(JsonElement e, long min, long max)
	{
		Long n = ItemCounts.longValue(e);
		return n == null || n < min || n > max ? 0 : n;
	}

	private static String eventsPayload(Gson gson, JsonArray events)
	{
		JsonObject o = header(Kind.EVENTS);
		o.add("e", events);
		return gson.toJson(o);
	}

	private static Map<Integer, Integer> first(Map<Integer, Integer> counts, int max)
	{
		Map<Integer, Integer> out = new TreeMap<>();
		for (Map.Entry<Integer, Integer> e : new TreeMap<>(counts).entrySet())
		{
			if (out.size() >= max)
			{
				break;
			}
			out.put(e.getKey(), e.getValue());
		}
		return out;
	}

	/** The decoded message, or null when it is not one this build can read. */
	static Decoded decode(Gson gson, String json)
	{
		if (json == null || json.isEmpty() || json.length() > MAX_PAYLOAD_CHARS * 4)
		{
			return null;
		}
		JsonObject in;
		try
		{
			JsonElement root = gson.fromJson(json, JsonElement.class);
			if (root == null || !root.isJsonObject())
			{
				return null;
			}
			in = root.getAsJsonObject();
		}
		catch (RuntimeException e)
		{
			return null;
		}

		Long v = ItemCounts.longValue(in.get("v"));
		if (v == null || v < 1)
		{
			return null;
		}
		JsonElement k = in.get("k");
		if (k == null || !k.isJsonPrimitive())
		{
			return null;
		}
		switch (k.getAsString())
		{
			case "bank":
				return decodeBank(in);
			case "carried":
				return Decoded.of(new Carried(
					ItemCounts.fromJson(in.get("inv"), MAX_CARRIED_ITEMS),
					ItemCounts.fromJson(in.get("eq"), MAX_CARRIED_ITEMS)));
			case "storage":
				return Decoded.of(new StorageChange(
					longOr(in.get("ts"), 0),
					"",
					ItemCounts.fromJson(in.get("took"), MAX_STORAGE_ITEMS),
					ItemCounts.fromJson(in.get("dep"), MAX_STORAGE_ITEMS),
					ItemCounts.fromJson(in.get("now"), MAX_STORAGE_ITEMS)));
			case "stats":
				return decodeStats(in);
			case "extras":
				return Decoded.of(new MemberExtras(
					ItemCounts.fromJson(in.get("rp"), MAX_POUCH_ITEMS),
					ItemCounts.fromJson(in.get("q"), MAX_QUIVER_ITEMS),
					ItemCounts.fromJson(in.get("sv"), MAX_SEED_VAULT_ITEMS),
					longOr(in.get("svts"), -1)));
			case "events":
				return decodeEvents(in);
			default:
				// A kind from a newer build.
				return null;
		}
	}

	private static Decoded decodeBank(JsonObject in)
	{
		long ts = longOr(in.get("ts"), -1);
		long part = longOr(in.get("p"), -1);
		long of = longOr(in.get("n"), -1);
		if (ts < 0 || of < 1 || of > MAX_BANK_PARTS || part < 0 || part >= of)
		{
			return null;
		}
		Map<Integer, Integer> items = ItemCounts.fromJson(in.get("i"), MAX_BANK_ITEMS);
		return Decoded.of(new BankPart(ts, (int) part, (int) of, items));
	}

	/** Bad events are skipped one at a time, like bad item pairs. */
	private static Decoded decodeEvents(JsonObject in)
	{
		List<GroupEvent> events = new ArrayList<>();
		JsonElement e = in.get("e");
		if (e != null && e.isJsonArray())
		{
			for (JsonElement item : e.getAsJsonArray())
			{
				if (events.size() >= MAX_EVENTS_PER_MESSAGE)
				{
					break;
				}
				if (!item.isJsonObject())
				{
					continue;
				}
				GroupEvent event = eventFrom(item.getAsJsonObject(), "");
				if (event != null && event.type.travels() && event.isValid())
				{
					events.add(event);
				}
			}
		}
		return Decoded.of(events);
	}

	private static Decoded decodeStats(JsonObject in)
	{
		Map<String, MemberStats.Level> skills = new LinkedHashMap<>();
		JsonElement s = in.get("s");
		if (s != null && s.isJsonObject())
		{
			for (Map.Entry<String, JsonElement> e : s.getAsJsonObject().entrySet())
			{
				if (skills.size() >= MAX_SKILLS || !e.getValue().isJsonArray())
				{
					continue;
				}
				JsonArray pair = e.getValue().getAsJsonArray();
				if (pair.size() < 2)
				{
					continue;
				}
				Long real = ItemCounts.longValue(pair.get(0));
				Long boosted = ItemCounts.longValue(pair.get(1));
				// Real levels are 1-99 (Hitpoints starts at 10); boosts never pass 99 + 30 or go below 0.
				if (real == null || boosted == null || real < 1 || real > 99 || boosted < 0 || boosted > 150)
				{
					continue;
				}
				String name = e.getKey().trim();
				if (!name.isEmpty() && name.length() <= 32)
				{
					skills.put(name, new MemberStats.Level(real.intValue(), boosted.intValue()));
				}
			}
		}
		return Decoded.of(new MemberStats(skills,
			clamp(longOr(in.get("run"), 0), 0, 100),
			clamp(longOr(in.get("spec"), 0), 0, 100),
			clamp(longOr(in.get("w"), 0), 0, 9999),
			clamp(longOr(in.get("r"), 0), 0, 1 << 16),
			clamp(longOr(in.get("x"), 0), 0, MAX_COORD),
			clamp(longOr(in.get("y"), 0), 0, MAX_COORD),
			clamp(longOr(in.get("pl"), 0), 0, 3)));
	}

	private static JsonObject header(Kind kind)
	{
		JsonObject o = new JsonObject();
		o.addProperty("v", PROTOCOL);
		o.addProperty("k", kind.name().toLowerCase(java.util.Locale.ROOT));
		return o;
	}

	private static long longOr(JsonElement e, long fallback)
	{
		Long v = ItemCounts.longValue(e);
		return v == null ? fallback : v;
	}

	private static int clamp(long v, int lo, int hi)
	{
		return (int) Math.max(lo, Math.min(hi, v));
	}

	private static int digits(int n)
	{
		return Integer.toString(n).length();
	}
}
