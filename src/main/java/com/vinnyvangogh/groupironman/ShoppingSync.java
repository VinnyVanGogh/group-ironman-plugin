package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
		if (lists.size() < MAX_LISTS)
		{
			return true;
		}
		ShoppingList oldest = null;
		for (ShoppingList l : lists.values())
		{
			if (l.removed && (oldest == null || l.at < oldest.at))
			{
				oldest = l;
			}
		}
		if (oldest == null)
		{
			return false;
		}
		lists.remove(oldest.id);
		return true;
	}

	private void record(ShoppingList before, ShoppingList after)
	{
		String text = describe(before, after);
		if (text.isEmpty())
		{
			return;
		}
		history.addFirst(new Change(after.at, text));
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
			for (String payload : strings(in.get("lists")))
			{
				ShoppingCodec.Message m = ShoppingCodec.decode(gson, payload);
				if (m != null && m.list != null && !(m.list.removed && now - m.list.at > TOMBSTONE_MS))
				{
					ShoppingList current = lists.get(m.list.id);
					if (m.list.newerThan(current) && (current != null || lists.size() < MAX_LISTS))
					{
						lists.put(m.list.id, m.list);
					}
				}
			}
			readHistory(in.get("hist"));
			readProgress(in.get("qp"));
		}
		onChange.run();
	}

	private void readHistory(JsonElement e)
	{
		if (e == null || !e.isJsonArray() || !history.isEmpty())
		{
			return;
		}
		for (JsonElement line : e.getAsJsonArray())
		{
			if (history.size() >= MAX_HISTORY || !line.isJsonArray() || line.getAsJsonArray().size() < 2)
			{
				continue;
			}
			JsonArray a = line.getAsJsonArray();
			Long at = ItemCounts.longValue(a.get(0));
			if (at != null && a.get(1).isJsonPrimitive())
			{
				history.addLast(new Change(at, ShoppingList.cap(a.get(1).getAsString(), 200)));
			}
		}
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

	private static List<String> strings(JsonElement e)
	{
		if (e == null || !e.isJsonArray())
		{
			return Collections.emptyList();
		}
		List<String> out = new ArrayList<>();
		for (JsonElement s : e.getAsJsonArray())
		{
			if (s.isJsonPrimitive() && out.size() < MAX_LISTS)
			{
				out.add(s.getAsString());
			}
		}
		return out;
	}
}
