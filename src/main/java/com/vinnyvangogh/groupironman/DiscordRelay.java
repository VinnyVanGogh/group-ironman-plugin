package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.runelite.client.events.PluginMessage;

/**
 * GIM's side of the Discord Relay contract (docs/contracts/discord-relay-v1.md): every Discord post is the
 * separate Discord Relay plugin's. GIM decides what happened and whether this client is the one to post it (own
 * events, the leader's board, the one plugin user who posts for members without it), then hands it over as a
 * RuneLite {@link PluginMessage} on the injected event bus. No reflection, no shared classes: the contract is
 * the namespace, the message names and the JSON-shaped maps documented there.
 *
 * <p>The builders are pure and static, so {@code DiscordRelayContractTest} checks each message against the
 * shared fixtures. Any thread: the bus posts synchronously on the caller's thread.
 */
final class DiscordRelay
{
	static final String NAMESPACE = "discord-relay";
	static final int VERSION = 1;
	static final String SOURCE = "gim-utilities";
	/** What Discord Relay puts in {@code source} on the messages it sends. */
	static final String RELAY = "discord-relay";
	/** The one line GIM shows while Discord Relay has not answered. */
	static final String INSTALL_HINT = "Install Discord Relay to post to Discord";

	// GIM to Discord Relay.
	static final String HELLO = "hello";
	static final String EVENT = "event";
	static final String BOARD = "board";
	static final String SETTINGS = "settings";
	static final String CAPTURE = "capture";
	static final String RELEASE = "release";
	static final String LEGACY = "legacy";
	/** Both ways: the sender is stopping. */
	static final String BYE = "bye";
	// Discord Relay to GIM.
	static final String READY = "ready";

	/** {@code screenshot}: none, the next frame, or the frame held under {@link #GRAVE}. */
	static final String SHOT_NONE = "none";
	static final String SHOT_FRAME = "frame";
	/** The capture key for the death frame the gravestone warning may attach. */
	static final String GRAVE = "grave";

	private final Consumer<Object> bus;
	private volatile boolean installed;
	private volatile boolean enabled;
	private volatile String relayVersion = "";
	/** Last data sent per message name, for the ones sent only when changed. Guarded by this. */
	private final Map<String, Map<String, Object>> lastSent = new HashMap<>();

	DiscordRelay(Consumer<Object> bus)
	{
		this.bus = bus;
	}

	/** Discord Relay answered and has not said bye. */
	boolean installed()
	{
		return installed;
	}

	/** Discord Relay is installed and its own "Post to Discord" switch is on. */
	boolean posting()
	{
		return installed && enabled;
	}

	String relayVersion()
	{
		return relayVersion;
	}

	void send(String name, Map<String, Object> data)
	{
		bus.accept(new PluginMessage(NAMESPACE, name, data));
	}

	/** Sends {@code data} only when it differs from the last one sent under {@code name}. */
	void sendIfChanged(String name, Map<String, Object> data)
	{
		synchronized (this)
		{
			if (data.equals(lastSent.get(name)))
			{
				return;
			}
			lastSent.put(name, data);
		}
		send(name, data);
	}

	/**
	 * A message from Discord Relay. Returns its webhooks when it is a {@link #READY} (so the caller can share them
	 * and resend its state); {@link Webhooks#NONE} for a {@link #BYE}; null for anything else, ours included.
	 */
	Webhooks receive(PluginMessage m)
	{
		Map<String, Object> data = m.getData();
		if (!NAMESPACE.equals(m.getNamespace()) || data == null || !RELAY.equals(data.get("source")))
		{
			return null;
		}
		if (BYE.equals(m.getName()))
		{
			installed = false;
			enabled = false;
			forget();
			return Webhooks.NONE;
		}
		if (!READY.equals(m.getName()))
		{
			return null;
		}
		installed = true;
		enabled = Boolean.TRUE.equals(data.get("enabled"));
		relayVersion = String.valueOf(data.getOrDefault("version", ""));
		// A (re)started Discord Relay holds nothing yet: everything is sent again.
		forget();
		return webhooks(data.get("channels"));
	}

	private synchronized void forget()
	{
		lastSent.clear();
	}

	/** {@code channels} of a {@link #READY}: webhook name to {@code url}, {@code poster} and {@code name}. */
	static Webhooks webhooks(Object channels)
	{
		if (!(channels instanceof Map))
		{
			return Webhooks.NONE;
		}
		Map<Webhook, Webhooks.Setting> out = new EnumMap<>(Webhook.class);
		for (Webhook w : Webhook.values())
		{
			Object c = ((Map<?, ?>) channels).get(w.name());
			if (c instanceof Map)
			{
				Map<?, ?> s = (Map<?, ?>) c;
				out.put(w, new Webhooks.Setting(text(s.get("url")), poster(s.get("poster")), text(s.get("name"))));
			}
		}
		return new Webhooks(out);
	}

	private static String text(Object o)
	{
		return o instanceof String ? (String) o : "";
	}

	private static DiscordPosterName poster(Object o)
	{
		for (DiscordPosterName p : DiscordPosterName.values())
		{
			if (p.name().equals(o))
			{
				return p;
			}
		}
		return DiscordPosterName.WEBHOOK;
	}

	// ---- Message builders ----

	private static Map<String, Object> envelope()
	{
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("v", VERSION);
		m.put("source", SOURCE);
		return m;
	}

	static Map<String, Object> hello(String version)
	{
		Map<String, Object> m = envelope();
		m.put("version", version);
		return m;
	}

	static Map<String, Object> bye()
	{
		return envelope();
	}

	/**
	 * One event. {@code post}: post it now; {@code digest}: a line for its category's daily digest; {@code attach}:
	 * not a post of its own, it joins this member's open moment (a storage visit's summary under a big move).
	 */
	static Map<String, Object> event(GroupEvent e, ItemLookup lookup, boolean post, boolean digest, boolean attach)
	{
		Map<String, Object> m = envelope();
		m.put("channel", Webhook.of(e.type).name());
		m.put("title", EventText.title(e));
		m.put("text", EventText.describe(e, lookup));
		List<Object> fields = new ArrayList<>();
		if (!e.items.isEmpty())
		{
			fields.add(field("Items", EventText.items(e, lookup)));
		}
		m.put("fields", fields);
		m.put("iconItemId", mostValuable(e.items, lookup));
		m.put("screenshot", screenshot(e));
		m.put("mergeKey", e.member);
		m.put("mention", e.type.big);
		m.put("post", post);
		m.put("digest", digest);
		m.put("attach", attach);
		m.put("event", eventData(e, lookup));
		return m;
	}

	/** Never a frame of this screen for another member's moment, or for a login; a gravestone may take the death's. */
	static String screenshot(GroupEvent e)
	{
		if (e.broadcast || e.type == GroupEvent.Type.ONLINE || e.type == GroupEvent.Type.EVERYONE_ONLINE)
		{
			return SHOT_NONE;
		}
		return e.type == GroupEvent.Type.GRAVESTONE_EXPIRING ? GRAVE : SHOT_FRAME;
	}

	private static Map<String, Object> field(String name, String value)
	{
		Map<String, Object> f = new LinkedHashMap<>();
		f.put("name", name);
		f.put("value", value);
		f.put("inline", false);
		return f;
	}

	private static Integer mostValuable(Map<Integer, Integer> items, ItemLookup lookup)
	{
		Integer best = null;
		long bestValue = -1;
		for (Map.Entry<Integer, Integer> i : items.entrySet())
		{
			long value = lookup.price(i.getKey()) * i.getValue();
			if (value > bestValue)
			{
				best = i.getKey();
				bestValue = value;
			}
		}
		return best;
	}

	/** Every {@link GroupEvent} field, with each item's name and price, so Discord Relay renders what GIM did. */
	static Map<String, Object> eventData(GroupEvent e, ItemLookup lookup)
	{
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("type", e.type.name());
		m.put("code", e.type.code);
		m.put("category", e.type.category.name());
		m.put("at", e.at);
		m.put("member", e.member);
		m.put("text", e.text);
		m.put("detail", e.detail);
		m.put("number", e.number);
		m.put("previous", e.previous);
		m.put("world", e.world);
		m.put("location", e.location);
		m.put("broadcast", e.broadcast);
		m.put("items", items(e.items, lookup));
		m.put("taken", items(e.taken, lookup));
		return m;
	}

	private static List<Object> items(Map<Integer, Integer> items, ItemLookup lookup)
	{
		List<Object> out = new ArrayList<>();
		for (Map.Entry<Integer, Integer> i : items.entrySet())
		{
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("id", i.getKey());
			item.put("quantity", i.getValue());
			item.put("name", lookup.name(i.getKey()));
			item.put("price", lookup.price(i.getKey()));
			out.add(item);
		}
		return out;
	}

	/**
	 * The live board: {@code keep}, this client keeps it (GIM's rule: the board is on, the Online status webhook
	 * is set, and on a shared webhook only the leader); {@code settled}, the inputs that pick the webhook are in;
	 * {@code lines}, one per member, empty while not kept.
	 */
	static Map<String, Object> board(boolean keep, boolean settled, String lines)
	{
		Map<String, Object> m = envelope();
		m.put("channel", Webhook.ONLINE.name());
		m.put("keep", keep);
		m.put("settled", settled);
		m.put("lines", keep ? lines : "");
		return m;
	}

	/**
	 * The group's settings that shape Discord posts. They are the group's (the leader's, signed and shared over
	 * the party), so GIM resolves them and Discord Relay only reads them.
	 */
	static Map<String, Object> settings(EventSettings s, int quietStart, int quietEnd, int offlineGraceMinutes,
		EasterEggs.Settings eggs, OnlineStatus online, GroupWebhooks shared)
	{
		Map<String, Object> m = envelope();
		m.put("discordTypes", names(s.discord));
		m.put("digestCategories", categories(s.digest));
		m.put("digestHour", s.digestHour);
		m.put("quietStart", quietStart);
		m.put("quietEnd", quietEnd);
		m.put("offlineGraceMinutes", offlineGraceMinutes);
		m.put("onlineStatus", online == null ? OnlineStatus.POSTS.name() : online.name());
		m.put("dropMin", s.dropMin);
		m.put("storageMin", s.storageMin);
		m.put("clueMin", s.clueMin);
		Map<String, Object> egg = new LinkedHashMap<>();
		egg.put("on", eggs.on);
		egg.put("legendAt", eggs.legendAt);
		egg.put("freeloaderAt", eggs.freeloaderAt);
		m.put("easterEggs", egg);
		m.put("shared", shared(shared));
		return m;
	}

	private static List<Object> names(Set<GroupEvent.Type> types)
	{
		List<Object> out = new ArrayList<>();
		for (GroupEvent.Type t : GroupEvent.Type.values())
		{
			if (types.contains(t))
			{
				out.add(t.name());
			}
		}
		return out;
	}

	private static List<Object> categories(Set<GroupEvent.Category> categories)
	{
		List<Object> out = new ArrayList<>();
		for (GroupEvent.Category c : GroupEvent.Category.values())
		{
			if (categories.contains(c))
			{
				out.add(c.name());
			}
		}
		return out;
	}

	/** The leader's shared webhooks, or null when the leader shares none. A member's own still wins in Relay. */
	private static Map<String, Object> shared(GroupWebhooks w)
	{
		if (w == null || !w.shared)
		{
			return null;
		}
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("by", w.by);
		Map<String, Object> channels = new LinkedHashMap<>();
		for (Webhook hook : Webhook.values())
		{
			Webhooks.Setting s = w.settings.get(hook);
			if (s != null)
			{
				Map<String, Object> c = new LinkedHashMap<>();
				c.put("url", s.url);
				c.put("poster", s.mode.name());
				c.put("name", s.name);
				channels.put(hook.name(), c);
			}
		}
		out.put("channels", channels);
		return out;
	}

	/** Take the next frame and hold it under {@code key}, for a later event's {@code screenshot}. */
	static Map<String, Object> capture(String key)
	{
		Map<String, Object> m = envelope();
		m.put("key", key);
		return m;
	}

	/** The frame held under {@code key} is no longer wanted. */
	static Map<String, Object> release(String key)
	{
		return capture(key);
	}

	/**
	 * Once per run after a {@link #READY}: what GIM kept for its own Discord posts before the split, so Discord
	 * Relay carries on editing the same messages. Each value is the file's text as GIM wrote it, or null.
	 */
	static Map<String, Object> legacy(String boards, String levelStreaks, String groupedPosts)
	{
		Map<String, Object> m = envelope();
		m.put("statusBoard", boards);
		m.put("levelStreaks", levelStreaks);
		m.put("groupedPosts", groupedPosts);
		return Collections.unmodifiableMap(m);
	}
}
