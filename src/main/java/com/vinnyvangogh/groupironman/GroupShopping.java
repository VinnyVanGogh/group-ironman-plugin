package com.vinnyvangogh.groupironman;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.runelite.client.events.PluginMessage;

/**
 * GIM's side of the Group Shopping contract (docs/contracts/group-shopping-v1.md): the Shopping tab (shared shopping
 * lists, requests, quest presets, "Path to" and the shop alert) is the separate Group Shopping plugin's. GIM keeps
 * the group: who is in it, what they hold, and the party. Group Shopping's own party payloads ride inside GIM's
 * {@link VvgGroupIronmanMessage} unchanged, so groups mixing old and new builds still read each other's lists, and
 * its saved state stays in GIM's group cache file under the same {@code shop} entry. No reflection, no shared classes.
 *
 * <p>The builders are pure and static, so {@code GroupShoppingContractTest} checks each message against the shared
 * fixtures. Any thread: the bus posts synchronously on the caller's thread, and every map sent is a fresh copy.
 */
final class GroupShopping
{
	static final String NAMESPACE = "group-shopping";
	static final int VERSION = 1;
	static final String SOURCE = "gim-utilities";
	/** What Group Shopping puts in {@code source} on the messages it sends. */
	static final String COMPANION = "group-shopping";
	/** The one card the Shopping tab shows while Group Shopping has not answered. */
	static final String INSTALL_HINT = "Install Group Shopping for shared shopping lists";
	/** The party payload kinds that are Group Shopping's: lists, quest progress, requests, claims. */
	static final String[] KINDS = {"shop", "qstate", "req", "rqc"};
	/** Payloads waiting for the next connected game tick; a full queue drops the oldest. */
	static final int MAX_QUEUED = 200;
	/** Largest payload passed on either way: party messages are small, and a stray huge one is not ours. */
	static final int MAX_PAYLOAD = 16_384;

	// GIM to Group Shopping.
	static final String HELLO = "hello";
	static final String SNAPSHOT = "snapshot";
	static final String PARTY = "party";
	static final String JOINED = "joined";
	static final String RESTORE = "restore";
	static final String ADD = "add";
	static final String OPEN = "open";
	/** Both ways: the sender is stopping. */
	static final String BYE = "bye";
	// Group Shopping to GIM.
	static final String READY = "ready";
	static final String REQUEST = "request";
	static final String SEND = "send";
	static final String STORE = "store";
	static final String EVENT = "event";

	/** What GIM does with one message from Group Shopping. */
	enum Inbound
	{
		IGNORE, SNAPSHOT, SEND, STORE, EVENT, BYE
	}

	private final Consumer<Object> bus;
	private final Deque<String> outbox = new ArrayDeque<>();
	private volatile boolean installed;
	private volatile String version = "";
	private volatile Map<String, Object> lastSnapshot;

	GroupShopping(Consumer<Object> bus)
	{
		this.bus = bus;
	}

	/** Group Shopping answered and has not said bye. */
	boolean installed()
	{
		return installed;
	}

	String version()
	{
		return version;
	}

	void send(String name, Map<String, Object> data)
	{
		bus.accept(new PluginMessage(NAMESPACE, name, data));
	}

	/** Sends {@code snapshot} unless it equals the last one sent; a ready or request forces it. */
	void sendSnapshot(Map<String, Object> snapshot, boolean force)
	{
		if (!force && snapshot.equals(lastSnapshot))
		{
			return;
		}
		lastSnapshot = snapshot;
		send(SNAPSHOT, snapshot);
	}

	/** Sorts one message from Group Shopping. Anything not its own, ours included, is {@link Inbound#IGNORE}. */
	Inbound receive(PluginMessage m)
	{
		Map<String, Object> data = m.getData();
		if (!NAMESPACE.equals(m.getNamespace()) || data == null || !COMPANION.equals(data.get("source")))
		{
			return Inbound.IGNORE;
		}
		switch (m.getName())
		{
			case BYE:
				installed = false;
				lastSnapshot = null;
				return Inbound.BYE;
			case READY:
				installed = true;
				version = String.valueOf(data.getOrDefault("version", ""));
				return Inbound.SNAPSHOT;
			case REQUEST:
				installed = true;
				return Inbound.SNAPSHOT;
			case SEND:
				return payload(data) == null ? Inbound.IGNORE : Inbound.SEND;
			case STORE:
				return Inbound.STORE;
			case EVENT:
				return Inbound.EVENT;
			default:
				return Inbound.IGNORE;
		}
	}

	/** A {@code send}: queued for the party until the next connected tick. */
	synchronized void queue(String payload)
	{
		if (!isShopping(payload))
		{
			return;
		}
		while (outbox.size() >= MAX_QUEUED)
		{
			outbox.removeFirst();
		}
		outbox.addLast(payload);
	}

	/** Game tick, connected: every queued payload, oldest first. */
	List<String> drain()
	{
		synchronized (this)
		{
			List<String> out = new ArrayList<>(outbox);
			outbox.clear();
			return out;
		}
	}

	/** A cheap look before decoding: every party message passes through here. */
	static boolean isShopping(String payload)
	{
		if (payload == null || payload.length() > MAX_PAYLOAD)
		{
			return false;
		}
		for (String kind : KINDS)
		{
			if (payload.contains("\"k\":\"" + kind + "\""))
			{
				return true;
			}
		}
		return false;
	}

	/** The {@code payload} string of a {@code send}, or null when it is missing or not a shopping kind. */
	static String payload(Map<String, Object> data)
	{
		Object p = data.get("payload");
		return p instanceof String && isShopping((String) p) ? (String) p : null;
	}

	/**
	 * A request event of this player's own, as the Shopping tab used to post it ({@link RequestBoard}); null when it
	 * is not one or names someone else, so Group Shopping can never speak for another member.
	 */
	static GroupEvent event(Map<String, Object> data, String self, long now)
	{
		Object member = data.get("member");
		Object kind = data.get("kind");
		if (!"REQUEST".equals(data.get("type")) || !(member instanceof String) || self.isEmpty()
			|| !GroupMembers.key((String) member).equals(GroupMembers.key(self)) || !(kind instanceof Number))
		{
			return null;
		}
		long k = ((Number) kind).longValue();
		if (k < RequestBoard.KIND_MADE || k > RequestBoard.KIND_DONE)
		{
			return null;
		}
		GroupEvent.Builder b = GroupEvent.of(GroupEvent.Type.REQUEST, now, self)
			.text(string(data.get("list"), 80)).detail(string(data.get("detail"), 200)).number(k);
		Object item = data.get("itemId");
		if (item instanceof Number && ((Number) item).intValue() > 0)
		{
			b.items(java.util.Collections.singletonMap(((Number) item).intValue(), 1));
		}
		return b.build();
	}

	private static String string(Object o, int max)
	{
		String s = o instanceof String ? (String) o : "";
		return s.length() > max ? s.substring(0, max) : s;
	}

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

	/** Ask Group Shopping to show its panel on the group's lists. */
	static Map<String, Object> open()
	{
		Map<String, Object> m = envelope();
		m.put("view", "lists");
		return m;
	}

	/** Someone joined or the party changed: Group Shopping resends every list, removals included. */
	static Map<String, Object> joined()
	{
		return envelope();
	}

	/** A Group Shopping payload from {@code member}, a roster member, exactly as the party carried it. */
	static Map<String, Object> party(String member, String payload)
	{
		Map<String, Object> m = envelope();
		m.put("member", member);
		m.put("payload", payload);
		return m;
	}

	/** The {@code shop} entry of GIM's group cache file as JSON text, or null when there is none. */
	static Map<String, Object> restore(String cache)
	{
		Map<String, Object> m = envelope();
		m.put("cache", cache);
		return m;
	}

	/** The Gear tab's "Add to shopping list" ({@code list}) or "Ask the group" ({@code request}) for one item. */
	static Map<String, Object> add(boolean list, int itemId, String itemName)
	{
		Map<String, Object> m = envelope();
		m.put("kind", list ? "list" : "request");
		m.put("itemId", itemId);
		m.put("itemName", itemName);
		m.put("quantity", 1);
		return m;
	}

	/**
	 * Who is in the group (in its order), this player, whether the party is connected, and everything each member
	 * has shared that the lists count: banks, carried items and the rest of "Who has it?", and group storage.
	 */
	static Map<String, Object> snapshot(String self, List<String> members, boolean syncing,
		List<GroupSearch.Source> sources)
	{
		Map<String, Object> m = envelope();
		m.put("self", self);
		m.put("members", new ArrayList<>(members));
		m.put("syncing", syncing);
		List<Object> list = new ArrayList<>();
		for (GroupSearch.Source s : sources)
		{
			Map<String, Object> one = new LinkedHashMap<>();
			one.put("member", s.member);
			one.put("where", s.where);
			one.put("items", PlayerCompare.items(s.items));
			list.add(one);
		}
		m.put("sources", list);
		return m;
	}
}
