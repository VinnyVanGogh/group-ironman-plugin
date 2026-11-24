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
import static com.vinnyvangogh.groupironman.GroupCodec.flag;
import static com.vinnyvangogh.groupironman.GroupCodec.header;
import static com.vinnyvangogh.groupironman.GroupCodec.string;

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
	/** Well over the seven webhooks: a newer build's extra ones are kept for the signature, then not used. */
	private static final int MAX_ENTRIES = 32;

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
		for (String[] e : w.entries())
		{
			JsonArray s = new JsonArray();
			s.add(e[1]);
			s.add(e[2]);
			s.add(e[3]);
			hooks.add(e[0], s);
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
		r.editors.forEach(eds::addProperty);
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
		s.values.forEach(vals::addProperty);
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

	/** The governance message in {@code json}, or null when it is not one (or not readable). */
	static Message decode(Gson gson, String json)
	{
		JsonObject root = GroupCodec.object(gson, json, GroupCodec.MAX_PAYLOAD_CHARS * 2);
		try
		{
			return root == null ? null : decode(root);
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
				return new Message(MEMBER, null, null, string(in, "pk"), new MemberPrefs(flag(in, "all"),
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

	/**
	 * Every entry is kept as sent for the signature check, so a newer leader's webhook or poster-name choice this
	 * build does not know costs only that entry: an unknown webhook is not used, an unknown choice falls back to the
	 * webhook's own name. Null for a signed form newer than {@link GroupWebhooks#FORMAT}: this build cannot check it.
	 */
	private static GroupWebhooks webhooks(JsonObject in)
	{
		if (number(in, "cv") > GroupWebhooks.FORMAT)
		{
			return null;
		}
		Map<Webhook, Webhooks.Setting> hooks = new EnumMap<>(Webhook.class);
		List<String[]> signed = new ArrayList<>();
		JsonElement wh = in.get("wh");
		if (wh != null && wh.isJsonObject())
		{
			for (Map.Entry<String, JsonElement> e : wh.getAsJsonObject().entrySet())
			{
				if (signed.size() >= MAX_ENTRIES)
				{
					break;
				}
				JsonArray a = e.getValue().getAsJsonArray();
				String url = a.get(0).getAsString();
				String modeName = a.get(1).getAsString();
				String name = a.get(2).getAsString();
				signed.add(new String[]{e.getKey(), url, modeName, name});
				Webhook w = known(Webhook.values(), e.getKey());
				if (w != null && url.length() <= MAX_URL)
				{
					hooks.put(w, new Webhooks.Setting(url, known(DiscordPosterName.values(), modeName),
						cap(name, PosterNames.MAX * 2)));
				}
			}
		}
		return new GroupWebhooks(string(in, "g"), number(in, "ver"), number(in, "ts"), cap(string(in, "by")),
			string(in, "bk"), flag(in, "sh"), hooks, string(in, "sig"), signed);
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

	private static long number(JsonObject in, String field)
	{
		JsonElement e = in.get(field);
		return e == null || !e.isJsonPrimitive() ? 0 : e.getAsLong();
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
