package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import static com.vinnyvangogh.groupironman.GroupCodec.flag;
import static com.vinnyvangogh.groupironman.GroupCodec.header;
import static com.vinnyvangogh.groupironman.GroupCodec.string;

/**
 * The Shopping tab's two kinds inside a {@link VvgGroupIronmanMessage}, next
 * to {@link GroupCodec}'s and {@link GovernanceCodec}'s:
 *
 * <ul>
 * <li>{@code shop}: one {@link ShoppingList} revision, removals included.</li>
 * <li>{@code qstate}: the sender's progress in the bundled quests
 * ({@link QuestProgress}), so a preset counts only what each member still has
 * to do.</li>
 * <li>{@code req}: one {@link RequestList} revision, a member's own request.</li>
 * <li>{@code rqc}: one {@link RequestClaim}, a member's "I'll get it".</li>
 * </ul>
 *
 * <p>A build without the Shopping tab (gim-test-3 and older) reads all four as
 * an unknown kind and ignores them, and the first Shopping tab build looks
 * only for {@code shop} and {@code qstate}, so requests pass it by the same
 * way; this build ignores kinds it does not know the same way. Every field is
 * capped, and the same JSON is what the group cache keeps between sessions.
 */
final class ShoppingCodec
{
	static final String SHOP = "shop";
	static final String QUESTS = "qstate";
	static final String REQUEST = "req";
	static final String CLAIM = "rqc";
	/** Well over the bundled data's quest count; a newer build's extra names are read and then dropped. */
	static final int MAX_QUESTS = 300;

	private ShoppingCodec()
	{
	}

	/** A cheap look before decoding: every party message passes through here. */
	static boolean isShopping(String payload)
	{
		if (payload == null)
		{
			return false;
		}
		for (String kind : new String[]{SHOP, QUESTS, REQUEST, CLAIM})
		{
			if (payload.contains("\"k\":\"" + kind + "\""))
			{
				return true;
			}
		}
		return false;
	}

	/** Exactly one of the fields is set. */
	static final class Message
	{
		final ShoppingList list;
		final QuestProgress quests;
		final RequestList request;
		final RequestClaim claim;

		Message(ShoppingList list, QuestProgress quests)
		{
			this(list, quests, null, null);
		}

		Message(ShoppingList list, QuestProgress quests, RequestList request, RequestClaim claim)
		{
			this.list = list;
			this.quests = quests;
			this.request = request;
			this.claim = claim;
		}
	}

	static String encode(Gson gson, RequestList r)
	{
		JsonObject o = header(REQUEST);
		o.addProperty("id", r.id);
		o.addProperty("o", r.owner);
		o.addProperty("n", r.name);
		o.add("i", ItemCounts.toJson(r.items));
		JsonArray shut = new JsonArray();
		r.closedItems.forEach(shut::add);
		o.add("ci", shut);
		if (r.closed)
		{
			o.addProperty("cl", true);
		}
		if (r.removed)
		{
			o.addProperty("x", true);
		}
		o.addProperty("r", r.rev);
		o.addProperty("ts", r.at);
		o.addProperty("c", r.created);
		o.addProperty("a", r.action);
		return gson.toJson(o);
	}

	static String encode(Gson gson, RequestClaim c)
	{
		JsonObject o = header(CLAIM);
		o.addProperty("id", c.requestId);
		o.addProperty("i", c.itemId);
		o.addProperty("m", c.member);
		o.addProperty("y", c.claimed);
		o.addProperty("ts", c.at);
		return gson.toJson(o);
	}

	static String encode(Gson gson, ShoppingList l)
	{
		JsonObject o = header(SHOP);
		o.addProperty("id", l.id);
		o.addProperty("n", l.name);
		o.addProperty("p", l.preset);
		o.add("pk", strings(l.picks));
		o.add("m", strings(l.members));
		o.add("i", ItemCounts.toJson(l.items));
		o.addProperty("r", l.rev);
		o.addProperty("ts", l.at);
		o.addProperty("by", l.by);
		o.addProperty("a", l.action);
		if (l.removed)
		{
			o.addProperty("x", true);
		}
		o.addProperty("c", l.created);
		o.addProperty("cb", l.createdBy);
		return gson.toJson(o);
	}

	static String encode(Gson gson, QuestProgress p)
	{
		JsonObject o = header(QUESTS);
		o.add("f", strings(p.finished()));
		o.add("s", strings(p.inProgress()));
		return gson.toJson(o);
	}

	/** The message in {@code json}, or null when it is none of the kinds or not readable. */
	static Message decode(Gson gson, String json)
	{
		JsonObject in = GroupCodec.object(gson, json, GroupCodec.MAX_PAYLOAD_CHARS * 2);
		if (in == null)
		{
			return null;
		}
		try
		{
			return read(string(in, "k"), in);
		}
		catch (RuntimeException e)
		{
			// A field of the wrong type: not a list anyone sent.
			return null;
		}
	}

	private static Message read(String kind, JsonObject in)
	{
		switch (kind)
		{
			case SHOP:
				ShoppingList l = list(in);
				return l == null ? null : new Message(l, null);
			case REQUEST:
				RequestList r = request(in);
				return r == null ? null : new Message(null, null, r, null);
			case CLAIM:
				RequestClaim c = claim(in);
				return c == null ? null : new Message(null, null, null, c);
			case QUESTS:
				return new Message(null, QuestProgress.of(names(in.get("f")), names(in.get("s"))));
			default:
				return null;
		}
	}

	private static ShoppingList list(JsonObject in)
	{
		String id = string(in, "id");
		Long rev = ItemCounts.longValue(in.get("r"));
		Long at = ItemCounts.longValue(in.get("ts"));
		if (!ShoppingList.validId(id) || rev == null || rev < 1 || at == null || at < 0)
		{
			return null;
		}
		Long created = ItemCounts.longValue(in.get("c"));
		boolean removed = flag(in, "x");
		return new ShoppingList(id, string(in, "n"), string(in, "p"), names(in.get("pk")), names(in.get("m")),
			ItemCounts.fromJson(in.get("i"), ShoppingList.MAX_ITEMS), rev, at, string(in, "by"), string(in, "a"),
			removed, created == null ? at : created, string(in, "cb"));
	}

	private static RequestList request(JsonObject in)
	{
		String id = string(in, "id");
		String owner = string(in, "o");
		Long rev = ItemCounts.longValue(in.get("r"));
		Long at = ItemCounts.longValue(in.get("ts"));
		if (!ShoppingList.validId(id) || owner.trim().isEmpty() || rev == null || rev < 1 || at == null || at < 0)
		{
			return null;
		}
		Long created = ItemCounts.longValue(in.get("c"));
		return new RequestList(id, owner, string(in, "n"), ItemCounts.fromJson(in.get("i"), ShoppingList.MAX_ITEMS),
			ids(in.get("ci")), flag(in, "cl"), flag(in, "x"), rev, at, created == null ? at : created,
			string(in, "a"));
	}

	private static RequestClaim claim(JsonObject in)
	{
		String id = string(in, "id");
		String member = string(in, "m");
		Long item = ItemCounts.longValue(in.get("i"));
		Long at = ItemCounts.longValue(in.get("ts"));
		if (!ShoppingList.validId(id) || member.trim().isEmpty() || item == null || item <= 0
			|| item >= ItemCounts.MAX_ITEM_ID || at == null || at < 0)
		{
			return null;
		}
		return new RequestClaim(id, item.intValue(), member, flag(in, "y"), at);
	}

	private static List<Integer> ids(JsonElement e)
	{
		List<Integer> out = new ArrayList<>();
		if (e == null || !e.isJsonArray())
		{
			return out;
		}
		for (JsonElement i : e.getAsJsonArray())
		{
			Long id = out.size() < ShoppingList.MAX_ITEMS ? ItemCounts.longValue(i) : null;
			if (id != null && id > 0 && id < ItemCounts.MAX_ITEM_ID)
			{
				out.add(id.intValue());
			}
		}
		return out;
	}

	private static List<String> names(JsonElement e)
	{
		List<String> out = new ArrayList<>();
		if (e == null || !e.isJsonArray())
		{
			return out;
		}
		for (JsonElement s : e.getAsJsonArray())
		{
			if (out.size() >= MAX_QUESTS)
			{
				break;
			}
			if (s.isJsonPrimitive())
			{
				out.add(ShoppingList.cap(s.getAsString(), ShoppingList.MAX_TEXT));
			}
		}
		return out;
	}

	private static JsonArray strings(Collection<String> values)
	{
		JsonArray out = new JsonArray();
		values.forEach(out::add);
		return out;
	}
}
