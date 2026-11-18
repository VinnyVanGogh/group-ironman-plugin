package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every member's requests and every claim on them, as this client holds them.
 * Part of {@link ShoppingSync}: it travels in the same party messages, is sent
 * on the same tick and kept in the same cache entry, so there is one sync path.
 * Not thread-safe by itself: {@link ShoppingSync} holds its lock around every
 * call.
 *
 * <p><b>Owner-only.</b> A request revision is taken only when it names the
 * same owner as the one held, so a member can never rewrite or remove another
 * member's list; claims are each member's own records and never touch it.
 */
final class RequestBook
{
	/** Requests kept, removed ones included; past it the oldest removed, then closed, one is forgotten. */
	static final int MAX_REQUESTS = 40;
	/** Claim records kept; a full book forgets ones on requests it no longer has, or taken-back ones. */
	static final int MAX_CLAIMS = 400;

	private final Map<String, RequestList> requests = new LinkedHashMap<>();
	/** By {@link RequestClaim#key}. */
	private final Map<String, RequestClaim> claims = new LinkedHashMap<>();
	private final Set<String> dirtyRequests = new LinkedHashSet<>();
	private final Set<String> dirtyClaims = new LinkedHashSet<>();

	/**
	 * Keeps {@code r} when it is newer than what is held and from the same owner; {@code own} marks it to send.
	 * Returns the history line ("" for nothing to say), or null when it was not taken.
	 */
	String accept(RequestList r, boolean own)
	{
		RequestList current = requests.get(r.id);
		if (!r.newerThan(current) || current != null && !current.ownedBy(r.owner))
		{
			return null;
		}
		if (current == null && !roomFor())
		{
			return null;
		}
		requests.put(r.id, r);
		if (own)
		{
			dirtyRequests.add(r.id);
		}
		return describe(current, r);
	}

	/** Keeps {@code c} when it is newer than this member's last word on that item; false when not taken. */
	boolean accept(RequestClaim c, boolean own)
	{
		RequestClaim current = claims.get(c.key());
		if (!c.newerThan(current) || current == null && !claimRoom())
		{
			return false;
		}
		claims.put(c.key(), c);
		if (own)
		{
			dirtyClaims.add(c.key());
		}
		return true;
	}

	/** "Alice asked for 'Fire cape'"; empty when there is nothing to say. */
	static String describe(RequestList before, RequestList after)
	{
		String who = after.owner;
		if (after.removed)
		{
			return before != null && before.removed ? "" : who + " removed '" + after.name + "'";
		}
		if (before == null)
		{
			return who + " asked for '" + after.name + "'";
		}
		if (after.closed && !before.closed)
		{
			return "'" + after.name + "' is done";
		}
		if (!before.name.equals(after.name))
		{
			return who + " renamed '" + before.name + "' to '" + after.name + "'";
		}
		return "";
	}

	RequestList get(String id)
	{
		return requests.get(id);
	}

	/** Not removed, oldest first; closed ones included so the group sees them done. */
	List<RequestList> live()
	{
		List<RequestList> out = new ArrayList<>();
		for (RequestList r : requests.values())
		{
			if (!r.removed)
			{
				out.add(r);
			}
		}
		out.sort((a, b) -> a.created != b.created ? Long.compare(a.created, b.created) : a.id.compareTo(b.id));
		return out;
	}

	/** Standing claims ("I'll get it") on {@code id}, in the order they came. */
	List<RequestClaim> claims(String id)
	{
		List<RequestClaim> out = new ArrayList<>();
		for (RequestClaim c : claims.values())
		{
			if (c.claimed && c.requestId.equals(id))
			{
				out.add(c);
			}
		}
		return out;
	}

	void resendAll()
	{
		dirtyRequests.addAll(requests.keySet());
		dirtyClaims.addAll(claims.keySet());
	}

	/** Everything marked to send, as party messages; the marks are cleared. */
	void drain(Gson gson, List<String> out)
	{
		for (String id : dirtyRequests)
		{
			RequestList r = requests.get(id);
			if (r != null)
			{
				out.add(ShoppingCodec.encode(gson, r));
			}
		}
		for (String key : dirtyClaims)
		{
			RequestClaim c = claims.get(key);
			if (c != null)
			{
				out.add(ShoppingCodec.encode(gson, c));
			}
		}
		dirtyRequests.clear();
		dirtyClaims.clear();
	}

	JsonArray requestsToCache(Gson gson)
	{
		JsonArray out = new JsonArray();
		requests.values().forEach(r -> out.add(ShoppingCodec.encode(gson, r)));
		return out;
	}

	JsonArray claimsToCache(Gson gson)
	{
		JsonArray out = new JsonArray();
		claims.values().forEach(c -> out.add(ShoppingCodec.encode(gson, c)));
		return out;
	}

	/** One cached record; a removal past {@code tombstoneMs} is dropped, and so is a claim on a request not held. */
	void fromCache(ShoppingCodec.Message m, long now, long tombstoneMs)
	{
		if (m == null)
		{
			return;
		}
		if (m.request != null && !(m.request.removed && now - m.request.at > tombstoneMs))
		{
			accept(m.request, false);
		}
		else if (m.claim != null && requests.containsKey(m.claim.requestId))
		{
			accept(m.claim, false);
		}
	}

	private boolean roomFor()
	{
		return ShoppingSync.makeRoom(requests, MAX_REQUESTS, r -> r.removed, r -> r.at)
			|| ShoppingSync.makeRoom(requests, MAX_REQUESTS, r -> r.closed, r -> r.at);
	}

	private boolean claimRoom()
	{
		return ShoppingSync.makeRoom(claims, MAX_CLAIMS, c -> !c.claimed || !requests.containsKey(c.requestId),
			c -> c.at);
	}
}
