package com.vinnyvangogh.groupironman;

import com.google.gson.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * The group's shared shopping lists, and each member's quest progress, kept in
 * step over the party. No server: every client holds every list, the newest
 * revision of each wins ({@link ShoppingList#newerThan}), and a member who
 * joins is sent everything (their {@code UserSync}).
 *
 * <p><b>When it sends.</b> Never on a timer of its own: a change marks the
 * list, and {@link #flush} sends it on the next game tick while connected to
 * the group's party. This player's quest progress goes only while some list is
 * for them, so a fresh install that nobody has made a list for sends nothing.
 *
 * <p>Kept in the group cache file (the {@code shop} entry), never the config.
 * Thread-safe: party messages, the client thread and the panel all call in.
 */
final class ShoppingSync
{
	/** Lists kept, removed ones included; a full book refuses new lists rather than forgetting live ones. */
	static final int MAX_LISTS = 40;
	static final int MAX_HISTORY = 20;
	/** A removed list is forgotten after this long: every member has long since heard. */
	static final long TOMBSTONE_MS = 30L * 24 * 60 * 60 * 1000;

	/** One line of "who did what". */
	static final class Change
	{
		final long at;
		final String text;

		Change(long at, String text)
		{
			this.at = at;
			this.text = text;
		}
	}

	private final Gson gson;
	private final Consumer<String> send;
	private final Runnable onChange;
	private final Map<String, ShoppingList> lists = new LinkedHashMap<>();
	private final Deque<Change> history = new ArrayDeque<>();
	/** By {@link GroupMembers#key}. */
	private final Map<String, QuestProgress> progress = new TreeMap<>();
	private final Set<String> dirty = new LinkedHashSet<>();
	/** Members' requests and claims: the same messages, tick and cache entry as the lists. */
	private final RequestBook requests = new RequestBook();
	private boolean progressDirty;

	ShoppingSync(Gson gson, Consumer<String> send, Runnable onChange)
	{
		this.gson = gson;
		this.send = send;
		this.onChange = onChange;
	}

	/** A cheap look before decoding. */
	static boolean isShopping(String payload)
	{
		return ShoppingCodec.isShopping(payload);
	}

	// ---- This player's actions ----

	/** False when the book is full. */
	boolean add(ShoppingList list)
	{
		boolean ok;
		synchronized (this)
		{
			ok = accept(list, true);
		}
		if (ok)
		{
			onChange.run();
		}
		return ok;
	}

	/** Custom lists only: a preset's name is the preset's. False when there is no such live list. */
	boolean rename(String id, String name, String self, long now)
	{
		String clean = ShoppingList.cap(name, ShoppingList.MAX_NAME);
		return change(id, l -> l.custom() && !clean.isEmpty() && !clean.equals(l.name)
			? l.renamed(clean, self, now) : null);
	}

	boolean remove(String id, String self, long now)
	{
		return change(id, l -> l.removedBy(self, now));
	}

	private boolean change(String id, java.util.function.Function<ShoppingList, ShoppingList> edit)
	{
		boolean ok = false;
		synchronized (this)
		{
			ShoppingList current = lists.get(id);
			ShoppingList next = current == null || current.removed ? null : edit.apply(current);
			if (next != null)
			{
				ok = accept(next, true);
			}
		}
		if (ok)
		{
			onChange.run();
		}
		return ok;
	}

	// ---- This player's requests ----

	/** A new request of this player's own; false with no items or a full book. */
	boolean addRequest(RequestList r)
	{
		if (r.items.isEmpty() || r.removed)
		{
			return false;
		}
		boolean ok;
		synchronized (this)
		{
			ok = takeRequest(r, true);
		}
		return changed(ok);
	}

	boolean renameRequest(String id, String name, String self, long now)
	{
		String clean = ShoppingList.cap(name, ShoppingList.MAX_NAME);
		return editRequest(id, self, r -> !clean.isEmpty() && !clean.equals(r.name) ? r.renamed(clean, now)
			: null) != null;
	}

	boolean removeRequest(String id, String self, long now)
	{
		return editRequest(id, self, r -> r.removedBy(now)) != null;
	}

	/** The owner closes the whole list, done or not. */
	boolean closeRequest(String id, String self, long now)
	{
		return editRequest(id, self, r -> r.closed ? null : r.closedBy(now)) != null;
	}

	/**
	 * The owner closes these items (by hand, or because the group holds them); the list closes with its last.
	 * Returns the new revision, or null when nothing changed or this player is not the owner.
	 */
	RequestList closeItems(String id, Set<Integer> items, String self, long now)
	{
		return editRequest(id, self, r ->
		{
			Set<Integer> fresh = new LinkedHashSet<>(items);
			fresh.retainAll(r.items.keySet());
			fresh.removeAll(r.closedItems);
			return r.closed || fresh.isEmpty() ? null : r.withClosedItems(fresh, now);
		});
	}

	/** "I'll get it", or taking it back. Anyone may, on an open request's open item. */
	boolean claim(String id, int itemId, String self, boolean claimed, long now)
	{
		boolean ok;
		synchronized (this)
		{
			RequestList r = requests.get(id);
			ok = r != null && r.open() && r.items.containsKey(itemId) && !self.isEmpty()
				&& !(claimed && r.closedItems.contains(itemId))
				&& requests.accept(new RequestClaim(id, itemId, self, claimed, now), true);
		}
		return changed(ok);
	}

	/** The owner's edit of a live request; null when it is not theirs, not live, or the edit changes nothing. */
	private RequestList editRequest(String id, String self, java.util.function.Function<RequestList, RequestList> edit)
	{
		RequestList next;
		synchronized (this)
		{
			RequestList current = requests.get(id);
			next = current == null || current.removed || !current.ownedBy(self) ? null : edit.apply(current);
			if (next != null && !takeRequest(next, true))
			{
				next = null;
			}
		}
		changed(next != null);
		return next;
	}

	/** Holds the lock. */
	private boolean takeRequest(RequestList r, boolean own)
	{
		String text = requests.accept(r, own);
		if (text == null)
		{
			return false;
		}
		note(r.at, text);
		return true;
	}

	private boolean changed(boolean ok)
	{
		if (ok)
		{
			onChange.run();
		}
		return ok;
	}

	/** This player's quest progress as the game shows it; sent when it changed and some list is for them. */
	void ownProgress(String self, QuestProgress now)
	{
		synchronized (this)
		{
			if (self.isEmpty() || now.equals(progress.get(GroupMembers.key(self))))
			{
				return;
			}
			progress.put(GroupMembers.key(self), now);
			progressDirty = true;
		}
		onChange.run();
	}

	// ---- The party ----

	/** A shopping message from {@code member}, a roster member. False when it is not one. */
	boolean receive(String member, String payload)
	{
		ShoppingCodec.Message m = ShoppingCodec.decode(gson, payload);
		if (m == null)
		{
			return false;
		}
		synchronized (this)
		{
			if (m.list != null)
			{
				accept(m.list, false);
			}
			else if (m.request != null)
			{
				takeRequest(m.request, false);
			}
			else if (m.claim != null)
			{
				requests.accept(m.claim, false);
			}
			else
			{
				progress.put(GroupMembers.key(member), m.quests);
			}
		}
		onChange.run();
		return true;
	}

	/** Someone joined: they may have missed all of it, removals included. */
	synchronized void resendAll()
	{
		dirty.addAll(lists.keySet());
		requests.resendAll();
		progressDirty = true;
	}

	/** Game tick, connected: every list that changed, then this player's progress when a list is for them. */
	void flush(String self)
	{
		List<String> out = new ArrayList<>();
		synchronized (this)
		{
			for (String id : dirty)
			{
				ShoppingList l = lists.get(id);
				if (l != null)
				{
					out.add(ShoppingCodec.encode(gson, l));
				}
			}
			dirty.clear();
			requests.drain(gson, out);
			QuestProgress mine = progress.get(GroupMembers.key(self));
			if (progressDirty && mine != null && isFor(self))
			{
				out.add(ShoppingCodec.encode(gson, mine));
				progressDirty = false;
			}
		}
		out.forEach(send);
	}

	/** True when some live list names {@code member}. */
	synchronized boolean isFor(String member)
	{
		for (ShoppingList l : lists.values())
		{
			if (!l.removed && containsIgnoreCase(l.members, member))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean containsIgnoreCase(List<String> names, String name)
	{
		for (String n : names)
		{
			if (GroupMembers.key(n).equals(GroupMembers.key(name)))
			{
				return true;
			}
		}
		return false;
	}

	/** Keeps {@code list} when it is newer than what is held; {@code own} marks it to send. Holds the lock. */
	private boolean accept(ShoppingList list, boolean own)
	{
		ShoppingList current = lists.get(list.id);
		if (!list.newerThan(current))
		{
			return false;
		}
		if (current == null && !roomFor())
		{
			return false;
		}
		lists.put(list.id, list);
		record(current, list);
		if (own)
		{
			dirty.add(list.id);
		}
		return true;
	}

	/** Room for one more list, forgetting the oldest removed one if that makes room. */
	private boolean roomFor()
	{
		return makeRoom(lists, MAX_LISTS, l -> l.removed, l -> l.at);
	}

	/**
	 * True when {@code book} has room for one more entry under {@code max}, forgetting the oldest {@code spare}
	 * one if that makes room. Lists and requests both use it.
	 */
	static <T> boolean makeRoom(Map<String, T> book, int max, java.util.function.Predicate<T> spare,
		java.util.function.ToLongFunction<T> at)
	{
		if (book.size() < max)
		{
			return true;
		}
		String oldest = null;
		long oldestAt = Long.MAX_VALUE;
		for (Map.Entry<String, T> e : book.entrySet())
		{
			long when = at.applyAsLong(e.getValue());
			if (spare.test(e.getValue()) && (oldest == null || when < oldestAt))
			{
				oldest = e.getKey();
				oldestAt = when;
			}
		}
		if (oldest == null)
		{
			return false;
		}
		book.remove(oldest);
		return true;
	}

	private void record(ShoppingList before, ShoppingList after)
	{
		note(after.at, describe(before, after));
	}

	/** One line of history, newest first; an empty line is not kept. Holds the lock. */
	private void note(long at, String text)
	{
		if (text.isEmpty())
		{
			return;
		}
		history.addFirst(new Change(at, text));
		while (history.size() > MAX_HISTORY)
		{
			history.removeLast();
		}
	}

	/** "Bob renamed 'Ropes' to 'Ropes and buckets'"; empty when there is nothing to say. */
	static String describe(ShoppingList before, ShoppingList after)
	{
		String who = after.by.isEmpty() ? "Someone" : after.by;
		if (after.removed)
		{
			return before != null && before.removed ? "" : who + " removed '" + after.name + "'";
		}
		if (before == null || before.removed)
		{
			return who + " added '" + after.name + "' for " + String.join(", ", after.members);
		}
		if (!before.name.equals(after.name))
		{
			return who + " renamed '" + before.name + "' to '" + after.name + "'";
		}
		return who + " changed '" + after.name + "'";
	}

	// ---- What the tab draws ----

	/** Live lists, oldest first. */
	synchronized List<ShoppingList> active()
	{
		List<ShoppingList> out = new ArrayList<>();
		for (ShoppingList l : lists.values())
		{
			if (!l.removed)
			{
				out.add(l);
			}
		}
		out.sort((a, b) -> a.created != b.created ? Long.compare(a.created, b.created) : a.id.compareTo(b.id));
		return out;
	}

	synchronized ShoppingList list(String id)
	{
		return lists.get(id);
	}

	synchronized List<Change> history()
	{
		return new ArrayList<>(history);
	}

	/** Members' requests not removed, oldest first, closed ones included. */
	synchronized List<RequestList> requests()
	{
		return requests.live();
	}

	/** Any held revision, removed included; null when none. */
	synchronized RequestList request(String id)
	{
		return requests.get(id);
	}

	/** Standing claims on one request. */
	synchronized List<RequestClaim> claims(String id)
	{
		return requests.claims(id);
	}

	/** {@link QuestProgress#UNKNOWN} for a member whose client never said. */
	synchronized QuestProgress progress(String member)
	{
		QuestProgress p = progress.get(GroupMembers.key(member));
		return p == null ? QuestProgress.UNKNOWN : p;
	}

	// ---- The group cache ----

	/** The cache file's {@code shop} entry: every list (as its party message), the history and progress. */
	synchronized JsonObject toCache()
	{
		JsonObject out = new JsonObject();
		JsonArray ls = new JsonArray();
		lists.values().forEach(l -> ls.add(ShoppingCodec.encode(gson, l)));
		out.add("lists", ls);
		out.add("reqs", requests.requestsToCache(gson));
		out.add("claims", requests.claimsToCache(gson));
		JsonArray hist = new JsonArray();
		for (Change c : history)
		{
			JsonArray line = new JsonArray();
			line.add(c.at);
			line.add(c.text);
			hist.add(line);
		}
		out.add("hist", hist);
		JsonObject qp = new JsonObject();
		progress.forEach((member, p) -> qp.addProperty(member, ShoppingCodec.encode(gson, p)));
		out.add("qp", qp);
		return out;
	}

	/** Reads what {@link #toCache} wrote; anything unreadable is skipped. Removed lists past their time are dropped. */
	void fromCache(JsonElement e, long now)
	{
		if (e == null || !e.isJsonObject())
		{
			return;
		}
		JsonObject in = e.getAsJsonObject();
		synchronized (this)
		{
			for (String payload : strings(in.get("lists"), MAX_LISTS))
			{
				readList(payload, now);
			}
			// Requests before claims: a claim is kept only on a request held.
			for (String payload : strings(in.get("reqs"), RequestBook.MAX_REQUESTS))
			{
				requests.fromCache(ShoppingCodec.decode(gson, payload), now, TOMBSTONE_MS);
			}
			for (String payload : strings(in.get("claims"), RequestBook.MAX_CLAIMS))
			{
				requests.fromCache(ShoppingCodec.decode(gson, payload), now, TOMBSTONE_MS);
			}
			readHistory(in.get("hist"));
			readProgress(in.get("qp"));
		}
		onChange.run();
	}

	/** One cached list, unless it is a removal past its time or older than what is held. Holds the lock. */
	private void readList(String payload, long now)
	{
		ShoppingCodec.Message m = ShoppingCodec.decode(gson, payload);
		ShoppingList l = m == null ? null : m.list;
		if (l == null || l.removed && now - l.at > TOMBSTONE_MS)
		{
			return;
		}
		ShoppingList current = lists.get(l.id);
		if (l.newerThan(current) && (current != null || lists.size() < MAX_LISTS))
		{
			lists.put(l.id, l);
		}
	}

	private void readHistory(JsonElement e)
	{
		if (e == null || !e.isJsonArray() || !history.isEmpty())
		{
			return;
		}
		for (JsonElement line : e.getAsJsonArray())
		{
			Change c = history.size() < MAX_HISTORY ? change(line) : null;
			if (c != null)
			{
				history.addLast(c);
			}
		}
	}

	/** {@code [at, text]}, or null when it is not one. */
	private static Change change(JsonElement line)
	{
		JsonArray a = line.isJsonArray() ? line.getAsJsonArray() : null;
		if (a == null || a.size() < 2 || !a.get(1).isJsonPrimitive())
		{
			return null;
		}
		Long at = ItemCounts.longValue(a.get(0));
		return at == null ? null : new Change(at, ShoppingList.cap(a.get(1).getAsString(), 200));
	}

	private void readProgress(JsonElement e)
	{
		if (e == null || !e.isJsonObject())
		{
			return;
		}
		for (Map.Entry<String, JsonElement> m : e.getAsJsonObject().entrySet())
		{
			ShoppingCodec.Message p = m.getValue().isJsonPrimitive()
				? ShoppingCodec.decode(gson, m.getValue().getAsString()) : null;
			if (p != null && p.quests != null && progress.size() < GroupCache.MAX_MEMBERS)
			{
				progress.putIfAbsent(GroupMembers.key(m.getKey()), p.quests);
			}
		}
	}

	private static List<String> strings(JsonElement e, int max)
	{
		if (e == null || !e.isJsonArray())
		{
			return Collections.emptyList();
		}
		List<String> out = new ArrayList<>();
		for (JsonElement s : e.getAsJsonArray())
		{
			if (s.isJsonPrimitive() && out.size() < max)
			{
				out.add(s.getAsString());
			}
		}
		return out;
	}
}
