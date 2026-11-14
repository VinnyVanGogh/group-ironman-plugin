package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The three governance kinds inside a {@link VvgGroupIronmanMessage}, next to
 * {@link GroupCodec}'s six. A build without them reads them as an unknown kind
 * and ignores them.
 *
 * <ul>
 * <li>{@code roles}: {@link GroupRoles}, signed by the leader.</li>
 * <li>{@code gset}: {@link GroupSettings}, signed by the leader or an editor.
 * Holds the shared settings only; a webhook URL has no field here.</li>
 * <li>{@code member}: a member's public key and their posting opt-outs.</li>
 * <li>{@code gweb}: {@link GroupWebhooks}, signed by the leader only, and only ever sent once the leader has
 * turned on "Share webhooks with the group". The one kind that carries webhook URLs.</li>
 * </ul>
 *
 * <p>The same JSON is what {@link GroupService} keeps in the config between
 * sessions. Every list and map is capped, and the signature is checked by
 * {@link GroupGovernance}, not here.
 */
final class GovernanceCodec
{
	static final String ROLES = "roles";
	static final String SETTINGS = "gset";
	static final String MEMBER = "member";
	static final String WEBHOOKS = "gweb";
	/** A little over {@link GroupWebhooks#MAX_URL}: a longer field cannot be one the leader signed. */
	private static final int MAX_URL = GroupWebhooks.MAX_URL + 1;
	/** Longer than any RuneLite display name (12) and than any key in the schema. */
	private static final int MAX_NAME = 64;
	/** Well over the schema's size: a newer build's extra keys are read and then dropped. */
	private static final int MAX_VALUES = 400;

	private GovernanceCodec()
	{
	}

	/** Exactly one of the fields is set, by {@link #kind}. */
	static final class Message
	{
		final String kind;
		final GroupRoles roles;
		final GroupSettings settings;
		final String memberKey;
		final MemberPrefs prefs;
		final GroupWebhooks webhooks;

		private Message(String kind, GroupRoles roles, GroupSettings settings, String memberKey, MemberPrefs prefs)
		{
			this(kind, roles, settings, memberKey, prefs, null);
		}

		private Message(String kind, GroupRoles roles, GroupSettings settings, String memberKey, MemberPrefs prefs,
			GroupWebhooks webhooks)
		{
			this.kind = kind;
			this.roles = roles;
			this.settings = settings;
			this.memberKey = memberKey;
			this.prefs = prefs;
			this.webhooks = webhooks;
		}
	}

	static String encode(Gson gson, GroupWebhooks w)
	{
		JsonObject o = header(WEBHOOKS);
		o.addProperty("g", w.group);
		o.addProperty("ver", w.version);
		o.addProperty("ts", w.at);
		o.addProperty("by", w.by);
		o.addProperty("bk", w.byKey);
		o.addProperty("sh", w.shared);
		JsonObject hooks = new JsonObject();
		for (Map.Entry<Webhook, Webhooks.Setting> e : w.settings.entrySet())
		{
			JsonArray s = new JsonArray();
			s.add(e.getValue().url);
			s.add(e.getValue().mode.name());
			s.add(e.getValue().name);
			hooks.add(e.getKey().name(), s);
		}
		o.add("wh", hooks);
		o.addProperty("sig", w.signature);
		return gson.toJson(o);
	}

	static String encode(Gson gson, GroupRoles roles)
	{
		return gson.toJson(json(roles));
	}

	static String encode(Gson gson, GroupSettings settings)
	{
		return gson.toJson(json(settings));
	}

	static String encodeMember(Gson gson, String publicKey, MemberPrefs prefs)
	{
		JsonObject o = header(MEMBER);
		o.addProperty("pk", publicKey);
		o.addProperty("all", prefs.all);
		o.addProperty("np", MemberPrefs.format(prefs.noPost));
		return gson.toJson(o);
	}

	static JsonObject json(GroupRoles r)
	{
		JsonObject o = header(ROLES);
		o.addProperty("g", r.group);
		o.addProperty("ver", r.version);
		o.addProperty("ts", r.at);
		o.addProperty("ld", r.leader);
		o.addProperty("lk", r.leaderKey);
		JsonObject eds = new JsonObject();
		for (Map.Entry<String, String> e : r.editors.entrySet())
		{
			eds.addProperty(e.getKey(), e.getValue());
		}
		o.add("ed", eds);
		o.addProperty("sig", r.signature);
		return o;
	}

	static JsonObject json(GroupSettings s)
	{
		JsonObject o = header(SETTINGS);
		o.addProperty("ver", s.version);
		o.addProperty("ts", s.at);
		o.addProperty("by", s.by);
		o.addProperty("bk", s.byKey);
		JsonObject vals = new JsonObject();
		for (Map.Entry<String, Long> e : s.values.entrySet())
		{
			vals.addProperty(e.getKey(), e.getValue());
		}
		o.add("val", vals);
		JsonArray hist = new JsonArray();
		for (GroupSettings.Change c : s.history)
		{
			JsonArray line = new JsonArray();
			line.add(c.at);
			line.add(c.by);
			line.add(c.key);
			line.add(c.from);
			line.add(c.to);
			hist.add(line);
		}
		o.add("h", hist);
		o.addProperty("sig", s.signature);
		return o;
	}

	private static JsonObject header(String kind)
	{
		JsonObject o = new JsonObject();
		o.addProperty("v", 1);
		o.addProperty("k", kind);
		return o;
	}

	/** The governance message in {@code json}, or null when it is not one (or not readable). */
	static Message decode(Gson gson, String json)
	{
		if (json == null || json.isEmpty() || json.length() > GroupCodec.MAX_PAYLOAD_CHARS * 2)
		{
			return null;
		}
		try
		{
			JsonElement root = gson.fromJson(json, JsonElement.class);
			return root != null && root.isJsonObject() ? decode(root.getAsJsonObject()) : null;
		}
		catch (RuntimeException e)
		{
			// Malformed JSON or a field of the wrong type: not a document anyone signed.
			return null;
		}
	}

	private static Message decode(JsonObject in)
	{
		switch (string(in, "k"))
		{
			case ROLES:
				return new Message(ROLES, roles(in), null, null, null);
			case SETTINGS:
				return new Message(SETTINGS, null, settings(in), null, null);
			case MEMBER:
				return new Message(MEMBER, null, null, string(in, "pk"), new MemberPrefs(bool(in, "all"),
					MemberPrefs.parse(string(in, "np")), MemberPrefs.NONE.mute));
			case WEBHOOKS:
				return new Message(WEBHOOKS, null, null, null, null, webhooks(in));
			default:
				return null;
		}
	}

	private static GroupRoles roles(JsonObject in)
	{
		Map<String, String> editors = new TreeMap<>();
		JsonElement eds = in.get("ed");
		if (eds != null && eds.isJsonObject())
		{
			for (Map.Entry<String, JsonElement> e : eds.getAsJsonObject().entrySet())
			{
				if (editors.size() > GroupRoles.MAX_EDITORS)
				{
					break;
				}
				editors.put(cap(e.getKey()), e.getValue().getAsString());
			}
		}
		return new GroupRoles(string(in, "g"), number(in, "ver"), number(in, "ts"), cap(string(in, "ld")),
			string(in, "lk"), editors, string(in, "sig"));
	}

	private static GroupSettings settings(JsonObject in)
	{
		Map<String, Long> values = new TreeMap<>();
		JsonElement vals = in.get("val");
		if (vals != null && vals.isJsonObject())
		{
			for (Map.Entry<String, JsonElement> e : vals.getAsJsonObject().entrySet())
			{
				if (values.size() >= MAX_VALUES)
				{
					break;
				}
				values.put(cap(e.getKey()), e.getValue().getAsLong());
			}
		}
		List<GroupSettings.Change> history = new ArrayList<>();
		JsonElement hist = in.get("h");
		if (hist != null && hist.isJsonArray())
		{
			for (JsonElement line : hist.getAsJsonArray())
			{
				if (history.size() >= GroupSettings.MAX_HISTORY)
				{
					break;
				}
				JsonArray a = line.getAsJsonArray();
				history.add(new GroupSettings.Change(a.get(0).getAsLong(), cap(a.get(1).getAsString()),
					cap(a.get(2).getAsString()), a.get(3).getAsLong(), a.get(4).getAsLong()));
			}
		}
		// Values are kept as signed, unsanitized, so the signature still checks; readers clamp on use.
		return new GroupSettings(number(in, "ver"), number(in, "ts"), cap(string(in, "by")), string(in, "bk"), values,
			history, string(in, "sig"));
	}

	/** As signed: a field that is no webhook or no poster-name choice this build knows fails the signature check. */
	private static GroupWebhooks webhooks(JsonObject in)
	{
		Map<Webhook, Webhooks.Setting> hooks = new EnumMap<>(Webhook.class);
		JsonElement wh = in.get("wh");
		if (wh != null && wh.isJsonObject())
		{
			for (Map.Entry<String, JsonElement> e : wh.getAsJsonObject().entrySet())
			{
				Webhook w = known(Webhook.values(), e.getKey());
				JsonArray a = e.getValue().getAsJsonArray();
				DiscordPosterName mode = known(DiscordPosterName.values(), a.get(1).getAsString());
				String url = a.get(0).getAsString();
				if (w != null && mode != null && url.length() <= MAX_URL)
				{
					hooks.put(w, new Webhooks.Setting(url, mode, cap(a.get(2).getAsString(), PosterNames.MAX * 2)));
				}
			}
		}
		return new GroupWebhooks(string(in, "g"), number(in, "ver"), number(in, "ts"), cap(string(in, "by")),
			string(in, "bk"), bool(in, "sh"), hooks, string(in, "sig"));
	}

	private static <E extends Enum<E>> E known(E[] values, String name)
	{
		for (E e : values)
		{
			if (e.name().equals(name))
			{
				return e;
			}
		}
		return null;
	}

	private static String string(JsonObject in, String field)
	{
		JsonElement e = in.get(field);
		return e == null || !e.isJsonPrimitive() ? "" : e.getAsString();
	}

	private static long number(JsonObject in, String field)
	{
		JsonElement e = in.get(field);
		return e == null || !e.isJsonPrimitive() ? 0 : e.getAsLong();
	}

	private static boolean bool(JsonObject in, String field)
	{
		JsonElement e = in.get(field);
		return e != null && e.isJsonPrimitive() && e.getAsBoolean();
	}

	private static String cap(String s)
	{
		return cap(s, MAX_NAME);
	}

	private static String cap(String s, int max)
	{
		return s.length() > max ? s.substring(0, max) : s;
	}
}
