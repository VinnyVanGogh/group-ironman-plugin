package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The live connection to the group's sync server (server/README.md): one WebSocket, opened on login with no
 * click, so a member is synced as soon as they are in game. RuneLite Party stays the fallback.
 * <p>
 * Minimal on purpose (Board 2026-11-18, Cloudflare's free plan): at most one frame every {@link #MIN_GAP_MS};
 * a payload identical to the last one sent for its slot is never sent again; a newer payload for a slot replaces
 * the older one still waiting; nothing polls. A refused or lost connection backs off from 1 s to 5 minutes and
 * honours the server's Retry-After, and a quota or kill-switch refusal just waits: nothing errors in game.
 * <p>
 * Thread safe: the socket's callbacks arrive on OkHttp's threads, sends on the client thread. {@link Listener}
 * methods are called with no lock held, on whichever thread got the frame; the caller moves them to the client
 * thread.
 */
final class ServerLink
{
	/** The Worker's address. A constant, never a setting: no user-entered URLs (plan §4.4). Set at deploy. */
	static final String BASE_URL = "wss://gim-utilities-server.vinnyvangogh.workers.dev";
	static final String WARNING = "Off by default. Sends your IP address, your name and what you share with your"
		+ " group to the GIM Utilities sync server, a 3rd-party server not controlled or verified by RuneLite"
		+ " developers";

	static final long MIN_GAP_MS = 5_000;
	static final long BACKOFF_MIN_MS = 1_000;
	static final long BACKOFF_MAX_MS = 5 * 60_000;
	/** The server's own caps (server/src/protocol.ts): payloads per frame, and characters per frame. */
	static final int MAX_BATCH = 32;
	static final int MAX_FRAME_CHARS = 120 * 1024;
	/** Waiting payloads with no slot (events, claims): the oldest go first past this. */
	static final int MAX_QUEUED = 200;

	/** Close codes the server uses (server/src/hub.ts CLOSE). */
	static final int CLOSE_REPLACED = 4001;
	static final int CLOSE_DELETED = 4010;
	static final int CLOSE_QUOTA = 4029;

	/** Kinds whose latest payload replaces an older one: everything that is a state, not an occurrence. */
	private static final Set<String> SLOTTED = new LinkedHashSet<>(Arrays.asList(
		"bank", "stats", "carried", "extras", "activity", "roles", "gset", "gweb", "member", "shop", "qstate"));
	/** Kinds the server keeps for a member who logs in later, re-sent with "p":1 at logout. */
	private static final Set<String> LIVE = new LinkedHashSet<>(Arrays.asList("stats", "carried", "extras"));

	enum State
	{
		/** Not wanted: server sync off, logged out, or no group key. */
		OFF,
		CONNECTING,
		CONNECTED,
		/** Waiting to try again (backoff, Retry-After or the daily quota). */
		WAITING,
		/** Refused for this login: a wrong key, the group full, or this name signed in elsewhere. */
		REFUSED
	}

	interface Socket
	{
		boolean send(String text);

		void close(int code, String reason);
	}

	/** What the socket tells the link. */
	interface Events
	{
		void opened(Socket socket);

		void text(Socket socket, String text);

		/** Closed by either side, with the WebSocket close code. */
		void closed(Socket socket, int code);

		/** Never opened, or dropped: {@code status} is the HTTP status when the upgrade was refused, else 0. */
		void failed(Socket socket, int status, long retryAfterMs);
	}

	interface Dialer
	{
		Socket dial(String url, String auth, String name, Events events);
	}

	interface Clock
	{
		long now();

		void schedule(Runnable task, long delayMs);
	}

	interface Listener
	{
		/** A payload from another member, under the name the server vouches for. */
		void payload(String from, String payload);

		/** Connected, with who else is online now. */
		void connected(List<String> online);

		/** A member connected: they want this member's live state. */
		void joined(String name);

		void left(String name);

		/** The group's data was deleted on the server by a member. */
		void deleted();

		void stateChanged(State state);
	}

	private final Gson gson;
	private final Dialer dialer;
	private final Clock clock;
	private final Listener listener;

	// All guarded by this.
	private State state = State.OFF;
	private String key = "";
	private String name = "";
	private Socket socket;
	/** Bumped on every start and stop, so a stale socket's or timer's callback does nothing. */
	private int generation;
	private long backoffMs = BACKOFF_MIN_MS;
	private long lastFrameAt = Long.MIN_VALUE / 2;
	private boolean flushScheduled;
	private final LinkedHashMap<String, String> pending = new LinkedHashMap<>();
	private final Map<String, String> lastSent = new HashMap<>();
	private final Set<String> online = new LinkedHashSet<>();
	private int unslotted;
	private long seq;

	ServerLink(Gson gson, Dialer dialer, Clock clock, Listener listener)
	{
		this.gson = gson;
		this.dialer = dialer;
		this.clock = clock;
		this.listener = listener;
	}

	synchronized State state()
	{
		return state;
	}

	synchronized boolean connected()
	{
		return state == State.CONNECTED;
	}

	/** Whether {@code member} ({@link GroupMembers#key}) is connected to the server right now. */
	synchronized boolean online(String member)
	{
		return online.contains(GroupMembers.key(member));
	}

	/**
	 * Connects as {@code name} for {@code groupKey}, or keeps the connection already open for them. A different
	 * key or name starts over. A refusal for this same key and name stands until {@link #stop}.
	 */
	void start(String groupKey, String name)
	{
		ServerKeys keys = ServerKeys.of(groupKey);
		if (keys == null || name == null || name.trim().isEmpty())
		{
			stop(false);
			return;
		}
		synchronized (this)
		{
			if (groupKey.equals(key) && name.equals(this.name) && state != State.OFF)
			{
				return;
			}
		}
		stop(false);
		synchronized (this)
		{
			key = groupKey;
			this.name = name;
			backoffMs = BACKOFF_MIN_MS;
		}
		dial(keys);
	}

	/** Closes the connection. With {@code persist}, the last live state goes first, for members who log in later. */
	void stop(boolean persist)
	{
		Socket s;
		String last = null;
		synchronized (this)
		{
			s = socket;
			if (persist && state == State.CONNECTED && s != null)
			{
				last = finalFrame();
			}
			reset();
		}
		if (s != null)
		{
			if (last != null)
			{
				s.send(last);
			}
			s.close(1000, "bye");
		}
		notifyState();
	}

	private void reset()
	{
		generation++;
		state = State.OFF;
		key = "";
		name = "";
		socket = null;
		pending.clear();
		lastSent.clear();
		online.clear();
		unslotted = 0;
		flushScheduled = false;
	}

	/** Queues one payload; it goes out with others in at most {@link #MIN_GAP_MS}. False when not connected. */
	boolean send(String payload)
	{
		synchronized (this)
		{
			if (state != State.CONNECTED || payload == null)
			{
				return false;
			}
			String slot = slot(payload);
			if (slot == null)
			{
				if (unslotted >= MAX_QUEUED)
				{
					dropOldestUnslotted();
				}
				pending.put("#" + seq++, payload);
				unslotted++;
			}
			else
			{
				if (payload.equals(lastSent.get(slot)))
				{
					pending.remove(slot);
					return true;
				}
				// Re-inserted at the end, so a slot's latest goes after the events sent before it.
				pending.remove(slot);
				pending.put(slot, payload);
			}
		}
		flushSoon();
		return true;
	}

	private void dropOldestUnslotted()
	{
		for (String k : pending.keySet())
		{
			if (k.startsWith("#"))
			{
				pending.remove(k);
				unslotted--;
				return;
			}
		}
	}

	private void flushSoon()
	{
		long wait;
		int gen;
		synchronized (this)
		{
			if (flushScheduled || pending.isEmpty() || state != State.CONNECTED)
			{
				return;
			}
			wait = Math.max(0, lastFrameAt + MIN_GAP_MS - clock.now());
			if (wait > 0)
			{
				flushScheduled = true;
			}
			gen = generation;
		}
		if (wait > 0)
		{
			clock.schedule(() -> flushNow(gen), wait);
		}
		else
		{
			flushNow(gen);
		}
	}

	private void flushNow(int gen)
	{
		Socket s;
		String frame;
		synchronized (this)
		{
			flushScheduled = false;
			if (gen != generation || state != State.CONNECTED || pending.isEmpty())
			{
				return;
			}
			frame = takeFrame(false);
			s = socket;
			lastFrameAt = clock.now();
		}
		if (s != null && frame != null)
		{
			s.send(frame);
		}
		// Anything that did not fit waits its turn.
		flushSoon();
	}

	/** The next frame's worth of waiting payloads, removed from the queue. Holds the lock. */
	private String takeFrame(boolean persist)
	{
		JsonArray d = new JsonArray();
		int size = 0;
		List<String> taken = new ArrayList<>();
		for (Map.Entry<String, String> e : pending.entrySet())
		{
			int cost = e.getValue().length() + 8;
			if (d.size() >= MAX_BATCH || d.size() > 0 && size + cost > MAX_FRAME_CHARS)
			{
				break;
			}
			d.add(e.getValue());
			size += cost;
			taken.add(e.getKey());
		}
		for (String k : taken)
		{
			String payload = pending.remove(k);
			if (k.startsWith("#"))
			{
				unslotted--;
			}
			else
			{
				lastSent.put(k, payload);
			}
		}
		return d.size() == 0 ? null : frame(d, persist);
	}

	private String frame(JsonArray d, boolean persist)
	{
		JsonObject o = new JsonObject();
		o.addProperty("t", "b");
		o.add("d", d);
		if (persist)
		{
			o.addProperty("p", 1);
		}
		return gson.toJson(o);
	}

	/** At logout: what is still waiting, plus the last live state sent, flagged to be kept. Holds the lock. */
	private String finalFrame()
	{
		for (String kind : LIVE)
		{
			String last = lastSent.get(kind);
			if (last != null && !pending.containsKey(kind))
			{
				pending.put(kind, last);
			}
		}
		return takeFrame(true);
	}

	/** The slot a payload replaces: its kind, and the part for a bank; null for an occurrence (events, claims). */
	String slot(String payload)
	{
		JsonObject o;
		try
		{
			o = gson.fromJson(payload, JsonObject.class);
		}
		catch (RuntimeException e)
		{
			return null;
		}
		JsonElement k = o == null ? null : o.get("k");
		String kind = k != null && k.isJsonPrimitive() ? k.getAsString() : "";
		if (!SLOTTED.contains(kind))
		{
			return null;
		}
		if ("bank".equals(kind))
		{
			JsonElement p = o.get("p");
			return p != null && p.isJsonPrimitive() ? "bank:" + p.getAsString() : null;
		}
		return kind;
	}

	private void dial(ServerKeys keys)
	{
		int gen;
		String who;
		synchronized (this)
		{
			state = State.CONNECTING;
			gen = generation;
			who = name;
		}
		notifyState();
		String url = BASE_URL + "/v1/groups/" + keys.groupId + "/live";
		Socket s = dialer.dial(url, keys.groupAuth, who, new Events()
		{
			@Override
			public void opened(Socket socket)
			{
				onOpened(gen, socket);
			}

			@Override
			public void text(Socket socket, String text)
			{
				onText(gen, text);
			}

			@Override
			public void closed(Socket socket, int code)
			{
				onClosed(gen, code, 0);
			}

			@Override
			public void failed(Socket socket, int status, long retryAfterMs)
			{
				onFailed(gen, status, retryAfterMs);
			}
		});
		synchronized (this)
		{
			if (gen == generation && socket == null)
			{
				socket = s;
			}
		}
	}

	private void onOpened(int gen, Socket s)
	{
		synchronized (this)
		{
			if (gen != generation)
			{
				s.close(1000, "stale");
				return;
			}
			socket = s;
			// Connected is reported on the server's "hi", which carries who is online.
		}
	}

	private void onText(int gen, String text)
	{
		JsonObject f;
		try
		{
			f = gson.fromJson(text, JsonObject.class);
		}
		catch (RuntimeException e)
		{
			return;
		}
		if (f == null || !f.has("t"))
		{
			return;
		}
		synchronized (this)
		{
			if (gen != generation)
			{
				return;
			}
		}
		switch (f.get("t").getAsString())
		{
			case "hi":
				hello(gen, names(f.get("n")));
				break;
			case "b":
				relayed(f.get("m"));
				break;
			case "j":
				member(gen, f.get("n"), true);
				break;
			case "l":
				member(gen, f.get("n"), false);
				break;
			default:
				// "e" (rate, bad): nothing to do; the next frame carries on.
				break;
		}
	}

	private void hello(int gen, List<String> names)
	{
		synchronized (this)
		{
			if (gen != generation)
			{
				return;
			}
			state = State.CONNECTED;
			backoffMs = BACKOFF_MIN_MS;
			online.clear();
			for (String n : names)
			{
				online.add(GroupMembers.key(n));
			}
		}
		notifyState();
		listener.connected(names);
	}

	private void member(int gen, JsonElement n, boolean joined)
	{
		if (n == null || !n.isJsonPrimitive())
		{
			return;
		}
		String who = n.getAsString();
		synchronized (this)
		{
			if (gen != generation)
			{
				return;
			}
			if (joined)
			{
				online.add(GroupMembers.key(who));
			}
			else
			{
				online.remove(GroupMembers.key(who));
			}
		}
		if (joined)
		{
			listener.joined(who);
		}
		else
		{
			listener.left(who);
		}
	}

	private void relayed(JsonElement m)
	{
		if (m == null || !m.isJsonArray())
		{
			return;
		}
		for (JsonElement e : m.getAsJsonArray())
		{
			if (!e.isJsonObject())
			{
				continue;
			}
			JsonObject o = e.getAsJsonObject();
			JsonElement from = o.get("f");
			JsonElement d = o.get("d");
			if (from != null && from.isJsonPrimitive() && d != null && d.isJsonPrimitive())
			{
				listener.payload(from.getAsString(), d.getAsString());
			}
		}
	}

	private static List<String> names(JsonElement n)
	{
		if (n == null || !n.isJsonArray())
		{
			return Collections.emptyList();
		}
		List<String> out = new ArrayList<>();
		for (JsonElement e : n.getAsJsonArray())
		{
			if (e.isJsonPrimitive())
			{
				out.add(e.getAsString());
			}
		}
		return out;
	}

	private void onClosed(int gen, int code, long retryAfterMs)
	{
		if (code == CLOSE_DELETED)
		{
			synchronized (this)
			{
				if (gen != generation)
				{
					return;
				}
				reset();
			}
			notifyState();
			listener.deleted();
			return;
		}
		if (code == CLOSE_REPLACED)
		{
			refuse(gen);
			return;
		}
		retry(gen, code == CLOSE_QUOTA ? untilUtcMidnight() : retryAfterMs);
	}

	private void onFailed(int gen, int status, long retryAfterMs)
	{
		if (status == 400 || status == 401 || status == 403)
		{
			// A wrong key, a bad name or a full group: trying again will not help until something changes.
			refuse(gen);
			return;
		}
		// 429 (quota), 503 (kill switch), 1027 pages, network errors: wait, then try again.
		retry(gen, retryAfterMs);
	}

	private void refuse(int gen)
	{
		synchronized (this)
		{
			if (gen != generation)
			{
				return;
			}
			generation++;
			state = State.REFUSED;
			socket = null;
			online.clear();
			pending.clear();
		}
		notifyState();
	}

	private void retry(int gen, long atLeastMs)
	{
		long wait;
		int next;
		synchronized (this)
		{
			if (gen != generation || state == State.OFF)
			{
				return;
			}
			generation++;
			next = generation;
			state = State.WAITING;
			socket = null;
			online.clear();
			pending.clear();
			lastSent.clear();
			wait = Math.max(backoffMs, atLeastMs);
			backoffMs = Math.min(BACKOFF_MAX_MS, backoffMs * 2);
		}
		notifyState();
		clock.schedule(() -> redial(next), wait);
	}

	private void redial(int gen)
	{
		ServerKeys keys;
		synchronized (this)
		{
			if (gen != generation || state != State.WAITING)
			{
				return;
			}
			keys = ServerKeys.of(key);
		}
		if (keys != null)
		{
			dial(keys);
		}
	}

	private long untilUtcMidnight()
	{
		long day = 24L * 60 * 60 * 1000;
		long now = clock.now();
		return day - Math.floorMod(now, day) + 1_000;
	}

	private void notifyState()
	{
		listener.stateChanged(state());
	}
}
