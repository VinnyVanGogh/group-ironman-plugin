package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Two more kinds inside a {@link VvgGroupIronmanMessage}, next to Group Shopping's ({@link GroupShopping}):
 *
 * <ul>
 * <li>{@code pbs}: the sender's boss personal bests ({@link PersonalBests}), seconds by boss name.</li>
 * <li>{@code ach}: the sender's quest points, quest states and diary tiers ({@link AchievementState}).</li>
 * </ul>
 *
 * <p>A build without them ignores both as unknown kinds. Every field is bounded by the value classes' own caps; a
 * field of the wrong type makes the whole message unreadable (null), never a partial one.
 */
final class MemberProgressCodec
{
	static final String BESTS = "pbs";
	static final String ACHIEVEMENTS = "ach";
	static final int MAX_CHARS = GroupCodec.MAX_PAYLOAD_CHARS * 2;

	private MemberProgressCodec()
	{
	}

	/** A cheap look before decoding: every party message passes through here. */
	static boolean isProgress(String payload)
	{
		return payload != null && (payload.contains("\"k\":\"" + BESTS + "\"")
			|| payload.contains("\"k\":\"" + ACHIEVEMENTS + "\""));
	}

	static String encode(Gson gson, PersonalBests p)
	{
		JsonObject o = GroupCodec.header(BESTS);
		JsonObject b = new JsonObject();
		p.all().forEach(b::addProperty);
		o.add("b", b);
		return gson.toJson(o);
	}

	static String encode(Gson gson, AchievementState a)
	{
		JsonObject o = GroupCodec.header(ACHIEVEMENTS);
		o.addProperty("qp", a.questPoints());
		o.add("f", strings(a.finished()));
		o.add("s", strings(a.inProgress()));
		JsonObject d = new JsonObject();
		a.diaries().forEach(d::addProperty);
		o.add("d", d);
		return gson.toJson(o);
	}

	static PersonalBests decodeBests(Gson gson, String json)
	{
		JsonObject in = GroupCodec.object(gson, json, MAX_CHARS);
		if (in == null || !BESTS.equals(in.has("k") && in.get("k").isJsonPrimitive() ? in.get("k").getAsString() : ""))
		{
			return null;
		}
		try
		{
			JsonElement b = in.get("b");
			if (b == null || !b.isJsonObject())
			{
				return null;
			}
			Map<String, Double> out = new TreeMap<>();
			for (Map.Entry<String, JsonElement> e : b.getAsJsonObject().entrySet())
			{
				if (e.getValue().isJsonPrimitive())
				{
					out.put(e.getKey(), e.getValue().getAsDouble());
				}
			}
			return PersonalBests.of(out);
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	static AchievementState decodeAchievements(Gson gson, String json)
	{
		JsonObject in = GroupCodec.object(gson, json, MAX_CHARS);
		if (in == null || !ACHIEVEMENTS.equals(in.has("k") && in.get("k").isJsonPrimitive()
			? in.get("k").getAsString() : ""))
		{
			return null;
		}
		try
		{
			JsonElement qp = in.get("qp");
			Map<String, Integer> diaries = new TreeMap<>();
			JsonElement d = in.get("d");
			if (d != null)
			{
				if (!d.isJsonObject())
				{
					return null;
				}
				for (Map.Entry<String, JsonElement> e : d.getAsJsonObject().entrySet())
				{
					diaries.put(e.getKey(), e.getValue().getAsInt());
				}
			}
			List<String> f = names(in.get("f"));
			List<String> s = names(in.get("s"));
			if (f == null || s == null)
			{
				return null;
			}
			return AchievementState.of(qp == null ? 0 : qp.getAsInt(), f, s, diaries);
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	/** Null when present and not an array of strings. */
	private static List<String> names(JsonElement e)
	{
		List<String> out = new ArrayList<>();
		if (e == null)
		{
			return out;
		}
		if (!e.isJsonArray())
		{
			return null;
		}
		for (JsonElement s : e.getAsJsonArray())
		{
			if (out.size() >= AchievementState.MAX_QUESTS)
			{
				break;
			}
			if (s.isJsonPrimitive())
			{
				out.add(s.getAsString());
			}
		}
		return out;
	}

	private static JsonArray strings(Iterable<String> values)
	{
		JsonArray out = new JsonArray();
		values.forEach(out::add);
		return out;
	}
}
