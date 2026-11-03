package com.vinnyvangogh.buildguard;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The JSON a member's client puts in a {@link BuildGuardPartyMessage}.
 *
 * <p>Pure, {@code String} in and out, so the wire format is tested without a
 * party or a client. RuneLite's party relay forwards the message to every
 * member's client as-is; nothing here leaves the party.
 *
 * <p><b>Version tolerance.</b> Members update at different times, so a peer
 * may be older or newer than this build. The payload carries {@code v}; any
 * {@code v >= 1} is read, fields this build does not know are ignored, and
 * fields an older peer did not send decode to empty. A change that cannot be
 * read that way is not a version bump: it is a new message class, because the
 * relay routes by class name and an old client then simply never sees it.
 *
 * <p><b>Peers are not trusted to be well-behaved.</b> Lists and strings are
 * trimmed to fixed limits and levels outside the game's range are dropped, so
 * a broken or hostile client cannot flood the panel.
 */
final class PartyBuildCodec
{
	/** The version this build writes. */
	static final int PROTOCOL = 1;

	static final int MAX_GEAR = 64;
	static final int MAX_NAME = 64;
	static final int MAX_RULES = 30;

	private PartyBuildCodec()
	{
	}

	static String encode(Gson gson, PartyBuild build)
	{
		JsonObject out = new JsonObject();
		out.addProperty("v", PROTOCOL);
		out.add("rules", gson.toJsonTree(build.rules().toArray(new BuildRule[0]), BuildRule[].class));

		JsonObject levels = new JsonObject();
		for (Map.Entry<String, Integer> e : build.levels().entrySet())
		{
			levels.addProperty(e.getKey(), e.getValue());
		}
		out.add("levels", levels);
		out.add("gear", strings(build.gear()));
		out.add("risk", strings(build.atRisk()));
		out.addProperty("reason", build.riskReason());
		return gson.toJson(out);
	}

	/** The decoded build, or null when the payload is not one this build can read. */
	static PartyBuild decode(Gson gson, String json)
	{
		if (json == null || json.trim().isEmpty())
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
			// JsonSyntaxException and friends: a peer sent garbage.
			return null;
		}

		JsonElement v = in.get("v");
		if (!isNumber(v) || v.getAsInt() < 1)
		{
			return null;
		}

		List<BuildRule> rules = Collections.emptyList();
		JsonElement rulesJson = in.get("rules");
		if (rulesJson != null && rulesJson.isJsonArray())
		{
			rules = BuildRuleCodec.decode(gson, rulesJson.toString());
			if (rules.size() > MAX_RULES)
			{
				rules = rules.subList(0, MAX_RULES);
			}
		}

		Map<String, Integer> levels = new LinkedHashMap<>();
		JsonElement levelsJson = in.get("levels");
		if (levelsJson != null && levelsJson.isJsonObject())
		{
			for (Map.Entry<String, JsonElement> e : levelsJson.getAsJsonObject().entrySet())
			{
				if (levels.size() >= MAX_RULES || !isNumber(e.getValue()))
				{
					continue;
				}
				int level = e.getValue().getAsInt();
				if (level >= 1 && level <= 99)
				{
					levels.put(trim(e.getKey()), level);
				}
			}
		}

		List<String> gear = readStrings(in.get("gear"), MAX_GEAR);
		List<String> risk = readStrings(in.get("risk"), MAX_RULES);
		JsonElement reason = in.get("reason");
		String riskReason = reason != null && reason.isJsonPrimitive() ? trim(reason.getAsString()) : "";

		return new PartyBuild(rules, levels, gear, risk, riskReason);
	}

	private static boolean isNumber(JsonElement e)
	{
		return e != null && e.isJsonPrimitive() && ((JsonPrimitive) e).isNumber();
	}

	private static JsonArray strings(List<String> values)
	{
		JsonArray out = new JsonArray();
		for (String value : values)
		{
			out.add(value);
		}
		return out;
	}

	private static List<String> readStrings(JsonElement e, int max)
	{
		List<String> out = new ArrayList<>();
		if (e == null || !e.isJsonArray())
		{
			return out;
		}
		for (JsonElement item : e.getAsJsonArray())
		{
			if (out.size() >= max)
			{
				break;
			}
			if (item != null && item.isJsonPrimitive())
			{
				String s = trim(item.getAsString());
				if (!s.isEmpty())
				{
					out.add(s);
				}
			}
		}
		return out;
	}

	private static String trim(String s)
	{
		String t = s == null ? "" : s.trim();
		return t.length() > MAX_NAME ? t.substring(0, MAX_NAME) : t;
	}
}
