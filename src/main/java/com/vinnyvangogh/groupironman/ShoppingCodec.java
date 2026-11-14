package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The Shopping tab's two kinds inside a {@link VvgGroupIronmanMessage}, next
 * to {@link GroupCodec}'s and {@link GovernanceCodec}'s:
 *
 * <ul>
 * <li>{@code shop}: one {@link ShoppingList} revision, removals included.</li>
 * <li>{@code qstate}: the sender's progress in the bundled quests
 * ({@link QuestProgress}), so a preset counts only what each member still has
 * to do.</li>
 * </ul>
 *
 * <p>A build without the Shopping tab (gim-test-3 and older) reads both as an
 * unknown kind and ignores them; this build ignores kinds it does not know the
 * same way. Every field is capped, and the same JSON is what the group cache
 * keeps between sessions.
 */
final class ShoppingCodec
{
	static final String SHOP = "shop";
	static final String QUESTS = "qstate";
	/** Well over the bundled data's quest count; a newer build's extra names are read and then dropped. */
	static final int MAX_QUESTS = 300;

	private ShoppingCodec()
	{
	}

	/** A cheap look before decoding: every party message passes through here. */
	static boolean isShopping(String payload)
	{
		return payload != null && (payload.contains("\"k\":\"" + SHOP + "\"")
			|| payload.contains("\"k\":\"" + QUESTS + "\""));
	}

	/** Exactly one of the fields is set. */
	static final class Message
	{
		final ShoppingList list;
		final QuestProgress quests;

		Message(ShoppingList list, QuestProgress quests)
		{
			this.list = list;
			this.quests = quests;
		}
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

	/** The message in {@code json}, or null when it is neither kind or not readable. */
	static Message decode(Gson gson, String json)
	{
		if (json == null || json.isEmpty() || json.length() > GroupCodec.MAX_PAYLOAD_CHARS * 2)
		{
			return null;
		}
		try
		{
			JsonElement root = gson.fromJson(json, JsonElement.class);
			if (root == null || !root.isJsonObject())
			{
				return null;
			}
			JsonObject in = root.getAsJsonObject();
			String kind = string(in, "k");
			if (SHOP.equals(kind))
			{
				ShoppingList l = list(in);
				return l == null ? null : new Message(l, null);
			}
			return QUESTS.equals(kind) ? new Message(null, QuestProgress.of(names(in.get("f")), names(in.get("s"))))
				: null;
		}
		catch (RuntimeException e)
		{
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
		JsonElement x = in.get("x");
		boolean removed = x != null && x.isJsonPrimitive() && x.getAsBoolean();
		return new ShoppingList(id, string(in, "n"), string(in, "p"), names(in.get("pk")), names(in.get("m")),
			ItemCounts.fromJson(in.get("i"), ShoppingList.MAX_ITEMS), rev, at, string(in, "by"), string(in, "a"),
			removed, created == null ? at : created, string(in, "cb"));
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

	private static String string(JsonObject in, String field)
	{
		JsonElement e = in.get(field);
		return e == null || !e.isJsonPrimitive() ? "" : e.getAsString();
	}

	private static JsonObject header(String kind)
	{
		JsonObject o = new JsonObject();
		o.addProperty("v", 1);
		o.addProperty("k", kind);
		return o;
	}
}
