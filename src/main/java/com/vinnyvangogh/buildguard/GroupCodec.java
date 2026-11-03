package com.vinnyvangogh.buildguard;

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
 * The JSON inside a {@link BuildGuardGroupMessage}: one of four kinds, each
 * tagged {@code k}.
 *
 * <ul>
 * <li>{@code bank}: one part of a bank snapshot. A bank is split into parts
 * so no single message exceeds {@link #MAX_PAYLOAD_CHARS}.</li>
 * <li>{@code carried}: inventory and worn items.</li>
 * <li>{@code storage}: one group storage visit, see {@link StorageChange}.</li>
 * <li>{@code stats}: live levels and status, see {@link MemberStats}.</li>
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
 * <p><b>Version tolerance</b> is the same as {@link PartyBuildCodec}: any
 * {@code v >= 1} is read, unknown kinds and fields are ignored, missing
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

	enum Kind
	{
		BANK, CARRIED, STORAGE, STATS
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

		private Decoded(Kind kind, BankPart bank, Carried carried, StorageChange storage, MemberStats stats)
		{
			this.kind = kind;
			this.bank = bank;
			this.carried = carried;
			this.storage = storage;
			this.stats = stats;
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
		return gson.toJson(o);
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
				return new Decoded(Kind.CARRIED, null, new Carried(
					ItemCounts.fromJson(in.get("inv"), MAX_CARRIED_ITEMS),
					ItemCounts.fromJson(in.get("eq"), MAX_CARRIED_ITEMS)), null, null);
			case "storage":
				return new Decoded(Kind.STORAGE, null, null, new StorageChange(
					longOr(in.get("ts"), 0),
					"",
					ItemCounts.fromJson(in.get("took"), MAX_STORAGE_ITEMS),
					ItemCounts.fromJson(in.get("dep"), MAX_STORAGE_ITEMS),
					ItemCounts.fromJson(in.get("now"), MAX_STORAGE_ITEMS)), null);
			case "stats":
				return decodeStats(in);
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
		return new Decoded(Kind.BANK, new BankPart(ts, (int) part, (int) of, items), null, null, null);
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
		return new Decoded(Kind.STATS, null, null, null, new MemberStats(skills,
			clamp(longOr(in.get("run"), 0), 0, 100),
			clamp(longOr(in.get("spec"), 0), 0, 100),
			clamp(longOr(in.get("w"), 0), 0, 9999),
			clamp(longOr(in.get("r"), 0), 0, 1 << 16)));
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
