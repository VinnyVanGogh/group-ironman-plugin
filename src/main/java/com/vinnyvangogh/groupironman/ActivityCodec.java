package com.vinnyvangogh.groupironman;

import com.google.gson.*;
import java.util.regex.Pattern;

/**
 * The live activity message, {@code "k":"activity"} (docs/plans/activities.md §5.1). Its own kind, not one of
 * {@link GroupCodec}'s: an older client's {@code GroupCodec.decode} returns null for a kind it does not know, and
 * {@code GroupService.notData} then drops it with a debug line, so the new message costs an older build nothing.
 *
 * <p>Everything read is bounded: the payload's size, every string (control characters and angle brackets removed,
 * so nothing a member sends can become Swing HTML), and every number.
 */
final class ActivityCodec
{
	static final String KIND = "activity";
	/** Longer payloads are dropped unread. */
	static final int MAX_CHARS = 4096;
	static final int MAX_ROTATION = 24;
	/** 70 waves of up to five characters and a comma. */
	static final int MAX_SPLITS = 420;
	static final int MAX_NAME = 40;
	static final int MAX_MODE = 24;
	static final int MAX_PHASE = 40;
	static final int MAX_BOARD = 64;
	static final int MAX_STATS = 64;
	static final int MAX_MODS = 120;
	static final int MAX_TEAM = 64;
	static final int MAX_WAVE = 1000;
	static final long MAX_ELAPSED_MS = 86_400_000L;
	private static final Pattern ID = Pattern.compile("[a-z0-9_]{0,24}");
	private static final Pattern UNSAFE = Pattern.compile("[\\p{Cntrl}<>]");

	private ActivityCodec()
	{
	}

	/** This player's state, or a clear frame ({@code "id":""}) for {@link ActivityState#NONE}. */
	static String encode(Gson gson, ActivityState s)
	{
		JsonObject o = new JsonObject();
		o.addProperty("v", 1);
		o.addProperty("k", KIND);
		o.addProperty("at", s.at);
		o.addProperty("id", s.id);
		if (s.active())
		{
			o.addProperty("cat", s.cat);
			o.addProperty("name", s.name);
			o.addProperty("mode", s.mode);
			o.addProperty("phase", s.phase);
			o.addProperty("board", s.board);
			o.addProperty("wave", s.wave);
			o.addProperty("max", s.max);
			o.addProperty("startedAt", s.startedAt);
			o.addProperty("elapsedMs", s.elapsedMs);
			o.addProperty("stats", s.stats);
			o.addProperty("mods", s.mods);
			o.addProperty("team", s.team);
			o.addProperty("rotation", s.rotation);
			o.addProperty("waveMs", s.waveMs);
			o.addProperty("splits", s.splits);
			o.addProperty("bests", s.bests);
		}
		return gson.toJson(o);
	}

	/** A cheap first look, so other kinds are not parsed twice. */
	static boolean isActivity(String json)
	{
		return json != null && json.contains("\"" + KIND + "\"");
	}

	/** The state sent, {@link ActivityState#NONE} for a clear frame, or null for anything malformed. */
	static ActivityState decode(Gson gson, String json)
	{
		JsonObject o = GroupCodec.object(gson, json, MAX_CHARS);
		if (o == null || !KIND.equals(string(o, "k", 16)) || number(o, "v", 0, Integer.MAX_VALUE) < 1)
		{
			return null;
		}
		JsonElement idElement = o.get("id");
		if (idElement == null || !idElement.isJsonPrimitive() || !ID.matcher(idElement.getAsString()).matches())
		{
			return null;
		}
		long at = number(o, "at", 0, Long.MAX_VALUE);
		String id = idElement.getAsString();
		if (id.isEmpty())
		{
			return new ActivityState.Builder("", at).build();
		}
		String name = string(o, "name", MAX_NAME);
		if (name.isEmpty())
		{
			return null;
		}
		return new ActivityState.Builder(id, at).cat(string(o, "cat", 16)).name(name)
			.mode(string(o, "mode", MAX_MODE)).phase(string(o, "phase", MAX_PHASE)).board(string(o, "board", MAX_BOARD))
			.wave((int) number(o, "wave", 0, MAX_WAVE)).max((int) number(o, "max", 0, MAX_WAVE))
			.startedAt(number(o, "startedAt", 0, Long.MAX_VALUE)).elapsedMs(number(o, "elapsedMs", 0, MAX_ELAPSED_MS))
			.stats(string(o, "stats", MAX_STATS)).mods(string(o, "mods", MAX_MODS)).team(string(o, "team", MAX_TEAM))
			.rotation(string(o, "rotation", MAX_ROTATION)).waveMs(number(o, "waveMs", 0, MAX_ELAPSED_MS))
			.splits(digits(string(o, "splits", MAX_SPLITS))).bests(digits(string(o, "bests", MAX_SPLITS))).build();
	}

	/** A splits string with anything but digits and commas removed: it is only ever read as numbers. */
	private static String digits(String s)
	{
		return s.replaceAll("[^0-9,]", "");
	}

	/** The field as safe display text, at most {@code max} characters; empty when missing or not a string. */
	static String string(JsonObject o, String key, int max)
	{
		JsonElement e = o.get(key);
		if (e == null || !e.isJsonPrimitive())
		{
			return "";
		}
		String clean = UNSAFE.matcher(e.getAsString()).replaceAll("").trim();
		return clean.length() > max ? clean.substring(0, max) : clean;
	}

	/** The field clamped to {@code [min, max]}; {@code min} when missing or not a number. */
	static long number(JsonObject o, String key, long min, long max)
	{
		JsonElement e = o.get(key);
		if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber())
		{
			return min;
		}
		double d = e.getAsDouble();
		if (Double.isNaN(d) || d <= min)
		{
			return min;
		}
		return d >= max ? max : e.getAsLong();
	}
}
