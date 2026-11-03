package com.vinnyvangogh.buildguard;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.Map;

/**
 * What survives a restart: members' last banks, the storage log and the last
 * group storage contents, as one JSON document. Live data (inventories,
 * equipment, stats) is not kept; it is only meaningful while the member is
 * online.
 *
 * <p>Pure, {@code String} in and out. The plugin reads and writes the file
 * ({@code .runelite/build-guard/group-cache.json}) off the client thread. A
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
		return gson.toJson(out);
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
		return true;
	}
}
