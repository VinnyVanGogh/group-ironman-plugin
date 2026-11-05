package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Personal and group watchlists (Board 2026-11-04: "Personal watchlists and
 * group watchlists ... If anyone else adds something to the group, it
 * suggests it for you rather than automatically adding it").
 *
 * <ul>
 * <li><b>Personal</b>: this player's own list. Never sent to the group.</li>
 * <li><b>Group</b>: the items this player has added or accepted. This is the
 * list that is shared; each member keeps their own.</li>
 * <li><b>Suggested</b>: items on another member's group list that are not on
 * this player's. Accept, Dismiss, or Accept all; nothing is added on its
 * own.</li>
 * </ul>
 *
 * <p>An entry keeps who first added it and when, through every accept, so a
 * member who dismissed an item is not asked again when a third member accepts
 * the same one. Only an add made later than the dismissal suggests it again.
 *
 * <p>Pure apart from Gson. Changed from the Swing thread (the panel's buttons)
 * and the party thread (members' lists), so every method is synchronized and
 * every list returned is a copy.
 */
final class Watchlist
{
	static final int VERSION = 1;
	/** Per list. Also bounds a member's list on the wire, see {@link GroupCodec#encodeWatch}. */
	static final int MAX_ITEMS = 100;
	/** Display names are at most 12 characters; the rest is headroom. */
	static final int MAX_NAME = 32;
	/** Dismissals remembered; the oldest are forgotten first. */
	static final int MAX_DISMISSED = 500;
	/** Members a suggestion remembers as listing it. */
	private static final int MAX_FROM = 50;

	/** One watched item: at least {@link #quantity}, first added by {@link #by} at {@link #at}. */
	static final class Entry
	{
		final int itemId;
		final int quantity;
		/** Who first added it; "" when not known. */
		final String by;
		final long at;

		Entry(int itemId, int quantity, String by, long at)
		{
			this.itemId = itemId;
			this.quantity = Math.max(1, quantity);
			this.by = name(by);
			this.at = at;
		}

		Entry withQuantity(int q)
		{
			return new Entry(itemId, q, by, at);
		}

		Entry withBy(String who)
		{
			return new Entry(itemId, quantity, who, at);
		}

		Entry withAt(long when)
		{
			return new Entry(itemId, quantity, by, when);
		}
	}

	/** One place a watched item is: "Alex bank 120". */
	static final class Holder
	{
		final String member;
		final String where;
		final long quantity;

		Holder(String member, String where, long quantity)
		{
			this.member = member;
			this.where = where;
			this.quantity = quantity;
		}
	}

	/** Event 22, "someone got a watchlist item": the entry and everywhere it now is. */
	static final class Got
	{
		final Entry entry;
		final List<Holder> holders;

		Got(Entry entry, List<Holder> holders)
		{
			this.entry = entry;
			this.holders = holders;
		}
	}

	private final Map<Integer, Entry> personal = new LinkedHashMap<>();
	private final Map<Integer, Entry> group = new LinkedHashMap<>();
	private final Map<Integer, Entry> suggested = new LinkedHashMap<>();
	/** Per suggestion, the members whose list has it; it goes when none do. */
	private final Map<Integer, Set<String>> from = new HashMap<>();
	/** Item to the newest add time dismissed (or removed): suggestions no newer stay away. */
	private final Map<Integer, Long> dismissed = new LinkedHashMap<>();
	/** Items whose "got it" has fired, so it fires once. */
	private final Set<Integer> got = new HashSet<>();

	// Personal.

	synchronized boolean addPersonal(int itemId, int quantity, long now, List<GroupSearch.Source> seen)
	{
		return add(personal, new Entry(itemId, quantity, "", now), seen);
	}

	synchronized boolean removePersonal(int itemId)
	{
		boolean removed = personal.remove(itemId) != null;
		forgetGotIfUnwatched(itemId);
		return removed;
	}

	synchronized List<Entry> personal()
	{
		return new ArrayList<>(personal.values());
	}

	// Group.

	/**
	 * Adds to this player's group list. An item already suggested is accepted
	 * instead, keeping its first adder and time, so it does not look new to
	 * the members who already decided on it.
	 */
	synchronized boolean addGroup(int itemId, int quantity, String self, long now, List<GroupSearch.Source> seen)
	{
		Entry s = suggested.get(itemId);
		Entry e = s != null ? s.withQuantity(quantity) : new Entry(itemId, quantity, self, now);
		boolean changed = add(group, e, seen);
		if (changed)
		{
			dropSuggestion(itemId);
		}
		return changed;
	}

	/** Removes from this player's group list; members who still list it do not suggest it back. */
	synchronized boolean removeGroup(int itemId)
	{
		Entry e = group.remove(itemId);
		if (e == null)
		{
			return false;
		}
		remember(itemId, e.at);
		forgetGotIfUnwatched(itemId);
		return true;
	}

	synchronized List<Entry> group()
	{
		return new ArrayList<>(group.values());
	}

	/** What this player sends to the group: their group list, never the personal one. */
	synchronized List<Entry> shared()
	{
		return group();
	}

	// Suggestions.

	/**
	 * A member's whole group list arrived. Items not on this player's group
	 * list become suggestions, unless dismissed and not added again since;
	 * this member's earlier suggestions they no longer list are dropped. True
	 * when the suggestions changed.
	 */
	synchronized boolean receive(String member, List<Entry> theirs, long now)
	{
		String sender = name(member);
		Set<Integer> listed = new HashSet<>();
		boolean changed = false;
		for (Entry raw : theirs)
		{
			if (!validItem(raw.itemId) || !listed.add(raw.itemId))
			{
				continue;
			}
			Entry e = raw.by.isEmpty() ? raw.withBy(sender) : raw;
			e = e.at > now ? e.withAt(now) : e;
			if (group.containsKey(e.itemId))
			{
				continue;
			}
			Long d = dismissed.get(e.itemId);
			if (d != null && e.at <= d)
			{
				continue;
			}
			Entry current = suggested.get(e.itemId);
			if (current == null)
			{
				if (suggested.size() >= MAX_ITEMS)
				{
					continue;
				}
				suggested.put(e.itemId, e);
				changed = true;
			}
			else if (e.at > current.at)
			{
				// Added again after the one we hold: the newer add is what the player decides on.
				suggested.put(e.itemId, e);
				changed = true;
			}
			Set<String> who = from.computeIfAbsent(e.itemId, k -> new LinkedHashSet<>());
			if (who.size() < MAX_FROM)
			{
				who.add(sender);
			}
		}
		for (Integer id : new ArrayList<>(suggested.keySet()))
		{
			Set<String> who = from.get(id);
			if (who != null && !listed.contains(id) && who.remove(sender) && who.isEmpty())
			{
				dropSuggestion(id);
				changed = true;
			}
		}
		return changed;
	}

	synchronized boolean accept(int itemId, List<GroupSearch.Source> seen)
	{
		Entry s = suggested.get(itemId);
		if (s == null)
		{
			return false;
		}
		if (group.size() < MAX_ITEMS)
		{
			add(group, s, seen);
		}
		dropSuggestion(itemId);
		return true;
	}

	synchronized boolean dismiss(int itemId)
	{
		Entry s = suggested.get(itemId);
		if (s == null)
		{
			return false;
		}
		remember(itemId, s.at);
		dropSuggestion(itemId);
		return true;
	}

	/** "Accept all": every suggestion onto the group list. How many were taken. */
	synchronized int acceptAll(List<GroupSearch.Source> seen)
	{
		int n = 0;
		for (Integer id : new ArrayList<>(suggested.keySet()))
		{
			if (accept(id, seen))
			{
				n++;
			}
		}
		return n;
	}

	synchronized List<Entry> suggested()
	{
		return new ArrayList<>(suggested.values());
	}

	synchronized boolean isEmpty()
	{
		return personal.isEmpty() && group.isEmpty() && suggested.isEmpty();
	}

	/** Every item id on any list, for name lookups. */
	synchronized Set<Integer> itemIds()
	{
		Set<Integer> out = new HashSet<>(personal.keySet());
		out.addAll(group.keySet());
		out.addAll(suggested.keySet());
		return out;
	}

	// Who has it, and the two events.

	/** Everywhere {@code itemId} is, in source order. */
	static List<Holder> holders(int itemId, List<GroupSearch.Source> seen)
	{
		List<Holder> out = new ArrayList<>();
		for (GroupSearch.Source s : seen)
		{
			Integer q = s.items.get(itemId);
			if (q != null && q > 0)
			{
				out.add(new Holder(s.member, s.where, q));
			}
		}
		return out;
	}

	static long total(int itemId, List<GroupSearch.Source> seen)
	{
		long n = 0;
		for (GroupSearch.Source s : seen)
		{
			Integer q = s.items.get(itemId);
			if (q != null && q > 0)
			{
				n += q;
			}
		}
		return n;
	}

	/**
	 * Event 19, "important item nobody in the group has": watched items (both
	 * lists, each once) in no member's bank, inventory, equipment, pouch or
	 * seed vault, nor in group storage. Empty while nothing has been seen at
	 * all: no data is not "nobody has it".
	 */
	synchronized List<Entry> missingFromGroup(List<GroupSearch.Source> seen)
	{
		List<Entry> out = new ArrayList<>();
		if (seen.isEmpty())
		{
			return out;
		}
		for (Entry e : watched().values())
		{
			if (total(e.itemId, seen) == 0)
			{
				out.add(e);
			}
		}
		return out;
	}

	/**
	 * Event 22, "someone got a watchlist item": watched items the group now
	 * holds enough of, each only the first time. An item on both lists needs
	 * the larger quantity. Marks them, so the next call does not return them.
	 */
	synchronized List<Got> checkGot(List<GroupSearch.Source> seen)
	{
		List<Got> out = new ArrayList<>();
		for (Entry e : watched().values())
		{
			if (!got.contains(e.itemId) && total(e.itemId, seen) >= required(e.itemId))
			{
				got.add(e.itemId);
				out.add(new Got(e, holders(e.itemId, seen)));
			}
		}
		return out;
	}

	synchronized boolean isGot(int itemId)
	{
		return got.contains(itemId);
	}

	/** Personal then group, each item once; the group entry wins for who added it. */
	private Map<Integer, Entry> watched()
	{
		Map<Integer, Entry> all = new LinkedHashMap<>(group);
		for (Entry e : personal.values())
		{
			all.putIfAbsent(e.itemId, e);
		}
		return all;
	}

	private int required(int itemId)
	{
		Entry p = personal.get(itemId);
		Entry g = group.get(itemId);
		return Math.max(p == null ? 1 : p.quantity, g == null ? 1 : g.quantity);
	}

	// The add-item search.

	/**
	 * Item ids whose name matches {@code query} the way {@code ::find} does
	 * (aliases included), shortest name first so "Shark" comes before "Shark
	 * lure", at most {@code max}.
	 */
	static List<Integer> search(String query, Map<Integer, String> names, int max)
	{
		List<Integer> out = new ArrayList<>();
		ItemQuery q = ItemQuery.parse(query);
		if (q == null)
		{
			return out;
		}
		List<Map.Entry<Integer, String>> hits = new ArrayList<>();
		for (Map.Entry<Integer, String> e : names.entrySet())
		{
			if (q.matches(e.getValue()))
			{
				hits.add(e);
			}
		}
		hits.sort((a, b) ->
		{
			int byLength = Integer.compare(a.getValue().length(), b.getValue().length());
			if (byLength != 0)
			{
				return byLength;
			}
			int byName = a.getValue().compareToIgnoreCase(b.getValue());
			return byName != 0 ? byName : Integer.compare(a.getKey(), b.getKey());
		});
		for (int i = 0; i < hits.size() && i < max; i++)
		{
			out.add(hits.get(i).getKey());
		}
		return out;
	}

	// Saved form: one JSON document in the hidden "watchlist" setting.

	synchronized String toJson(Gson gson)
	{
		JsonObject o = new JsonObject();
		o.addProperty("v", VERSION);
		o.add("p", entriesJson(personal.values()));
		o.add("g", entriesJson(group.values()));
		JsonArray s = new JsonArray();
		for (Entry e : suggested.values())
		{
			JsonArray a = entryJson(e);
			JsonArray who = new JsonArray();
			for (String m : from.getOrDefault(e.itemId, Collections.emptySet()))
			{
				who.add(m);
			}
			a.add(who);
			s.add(a);
		}
		o.add("s", s);
		JsonArray d = new JsonArray();
		for (Map.Entry<Integer, Long> e : dismissed.entrySet())
		{
			JsonArray pair = new JsonArray();
			pair.add(e.getKey());
			pair.add(e.getValue());
			d.add(pair);
		}
		o.add("d", d);
		JsonArray g = new JsonArray();
		for (Integer id : got)
		{
			g.add(id);
		}
		o.add("got", g);
		return gson.toJson(o);
	}

	/** Replaces every list with the saved ones. False, changing nothing, when the text is unreadable. */
	synchronized boolean load(Gson gson, String json)
	{
		JsonObject in;
		try
		{
			JsonElement root = json == null || json.isEmpty() ? null : gson.fromJson(json, JsonElement.class);
			if (root == null || !root.isJsonObject())
			{
				return false;
			}
			in = root.getAsJsonObject();
		}
		catch (RuntimeException e)
		{
			return false;
		}
		Long v = ItemCounts.longValue(in.get("v"));
		if (v == null || v < 1)
		{
			return false;
		}
		personal.clear();
		group.clear();
		suggested.clear();
		from.clear();
		dismissed.clear();
		got.clear();
		for (Entry e : entriesFrom(in.get("p"), MAX_ITEMS))
		{
			personal.putIfAbsent(e.itemId, e);
		}
		for (Entry e : entriesFrom(in.get("g"), MAX_ITEMS))
		{
			group.putIfAbsent(e.itemId, e);
		}
		JsonElement s = in.get("s");
		if (s != null && s.isJsonArray())
		{
			for (JsonElement item : s.getAsJsonArray())
			{
				Entry e = entryFrom(item);
				if (e == null || suggested.size() >= MAX_ITEMS || group.containsKey(e.itemId))
				{
					continue;
				}
				suggested.put(e.itemId, e);
				Set<String> who = new LinkedHashSet<>();
				JsonArray a = item.getAsJsonArray();
				if (a.size() > 4 && a.get(4).isJsonArray())
				{
					for (JsonElement m : a.get(4).getAsJsonArray())
					{
						if (m.isJsonPrimitive() && who.size() < MAX_FROM)
						{
							who.add(name(m.getAsString()));
						}
					}
				}
				from.put(e.itemId, who);
			}
		}
		JsonElement d = in.get("d");
		if (d != null && d.isJsonArray())
		{
			for (JsonElement item : d.getAsJsonArray())
			{
				if (!item.isJsonArray() || item.getAsJsonArray().size() < 2)
				{
					continue;
				}
				Long id = ItemCounts.longValue(item.getAsJsonArray().get(0));
				Long at = ItemCounts.longValue(item.getAsJsonArray().get(1));
				if (id != null && at != null && validItem(id))
				{
					remember(id.intValue(), at);
				}
			}
		}
		JsonElement g = in.get("got");
		if (g != null && g.isJsonArray())
		{
			for (JsonElement item : g.getAsJsonArray())
			{
				Long id = ItemCounts.longValue(item);
				if (id != null && validItem(id) && (personal.containsKey(id.intValue()) || group.containsKey(id.intValue())))
				{
					got.add(id.intValue());
				}
			}
		}
		return true;
	}

	// One entry as JSON, here and on the wire: [id, quantity, at, "by"].

	static JsonArray entryJson(Entry e)
	{
		JsonArray a = new JsonArray();
		a.add(e.itemId);
		a.add(e.quantity);
		a.add(e.at);
		a.add(e.by);
		return a;
	}

	/** Null when it is not an entry: a bad id or time. A bad quantity reads as 1, a bad adder as "". */
	static Entry entryFrom(JsonElement item)
	{
		if (item == null || !item.isJsonArray() || item.getAsJsonArray().size() < 3)
		{
			return null;
		}
		JsonArray a = item.getAsJsonArray();
		Long id = ItemCounts.longValue(a.get(0));
		Long q = ItemCounts.longValue(a.get(1));
		Long at = ItemCounts.longValue(a.get(2));
		if (id == null || !validItem(id) || at == null || at < 0)
		{
			return null;
		}
		int quantity = q == null || q < 1 ? 1 : (int) Math.min(Integer.MAX_VALUE, q);
		String by = "";
		if (a.size() > 3 && a.get(3).isJsonPrimitive() && a.get(3).getAsJsonPrimitive().isString())
		{
			by = a.get(3).getAsString();
		}
		return new Entry(id.intValue(), quantity, by, at);
	}

	static List<Entry> entriesFrom(JsonElement in, int max)
	{
		List<Entry> out = new ArrayList<>();
		if (in == null || !in.isJsonArray())
		{
			return out;
		}
		for (JsonElement item : in.getAsJsonArray())
		{
			if (out.size() >= max)
			{
				break;
			}
			Entry e = entryFrom(item);
			if (e != null)
			{
				out.add(e);
			}
		}
		return out;
	}

	private static JsonArray entriesJson(Iterable<Entry> entries)
	{
		JsonArray out = new JsonArray();
		for (Entry e : entries)
		{
			out.add(entryJson(e));
		}
		return out;
	}

	private boolean add(Map<Integer, Entry> list, Entry e, List<GroupSearch.Source> seen)
	{
		if (!validItem(e.itemId))
		{
			return false;
		}
		Entry old = list.get(e.itemId);
		if (old != null)
		{
			if (old.quantity == e.quantity)
			{
				return false;
			}
			list.put(e.itemId, old.withQuantity(e.quantity));
			return true;
		}
		if (list.size() >= MAX_ITEMS)
		{
			return false;
		}
		list.put(e.itemId, e);
		// Already held when added: that is not news, so "got it" does not fire for it.
		if (total(e.itemId, seen) >= required(e.itemId))
		{
			got.add(e.itemId);
		}
		return true;
	}

	private void dropSuggestion(int itemId)
	{
		suggested.remove(itemId);
		from.remove(itemId);
	}

	private void remember(int itemId, long at)
	{
		Long old = dismissed.remove(itemId);
		dismissed.put(itemId, old == null ? at : Math.max(old, at));
		while (dismissed.size() > MAX_DISMISSED)
		{
			dismissed.remove(dismissed.keySet().iterator().next());
		}
	}

	private void forgetGotIfUnwatched(int itemId)
	{
		if (!personal.containsKey(itemId) && !group.containsKey(itemId))
		{
			got.remove(itemId);
		}
	}

	private static boolean validItem(long id)
	{
		return id > 0 && id < ItemCounts.MAX_ITEM_ID;
	}

	private static String name(String s)
	{
		String t = s == null ? "" : s.trim();
		return t.length() > MAX_NAME ? t.substring(0, MAX_NAME) : t;
	}
}
