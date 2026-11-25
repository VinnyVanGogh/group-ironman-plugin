package com.vinnyvangogh.groupironman;

import com.google.gson.*;

/**
 * "This client keeps the live status board", sent to the party about once a minute while it does, so the other
 * members' clients stop posting separate online and offline messages beside the board. Its own kind, not one of
 * {@link GroupCodec}'s: an older client drops a kind it does not know ({@code GroupService.notData}), so the
 * message costs an older build nothing (it keeps posting, as before).
 *
 * <p>Carries no data of the sender's own and no webhook. A receiver only notes when it last heard one, by its own
 * clock, never the sender's.
 */
final class BoardBeacon
{
	static final String KIND = "board";
	/** Longer payloads are dropped unread. */
	static final int MAX_CHARS = 256;
	/** A beacon not heard again for this long means no client is keeping the board any more. */
	static final long STALE_MS = 5 * 60_000L;

	private volatile long heardAt;

	/** The message a board-keeping client sends. */
	static String encode(Gson gson, long at)
	{
		JsonObject o = new JsonObject();
		o.addProperty("v", 1);
		o.addProperty("k", KIND);
		o.addProperty("at", at);
		return gson.toJson(o);
	}

	/** A cheap first look, so other kinds are not parsed twice. */
	static boolean isBeacon(String json)
	{
		return json != null && json.contains("\"" + KIND + "\"");
	}

	/** True for a well-formed beacon; anything else is dropped. */
	static boolean valid(Gson gson, String json)
	{
		JsonObject o = GroupCodec.object(gson, json, MAX_CHARS);
		return o != null && KIND.equals(ActivityCodec.string(o, "k", 16))
			&& ActivityCodec.number(o, "v", 0, Integer.MAX_VALUE) >= 1;
	}

	void heard(long now)
	{
		heardAt = now;
	}

	/** Whether another client's board beacon was heard within {@link #STALE_MS} of {@code now}. */
	boolean fresh(long now)
	{
		long at = heardAt;
		return at > 0 && now - at < STALE_MS && now >= at;
	}

	/** Forgets what was heard: the party was left or the group changed. */
	void clear()
	{
		heardAt = 0;
	}
}
