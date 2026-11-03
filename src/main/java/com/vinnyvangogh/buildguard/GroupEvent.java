package com.vinnyvangogh.buildguard;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * One line of the "since you last played" log: a member's NPC drop, or a new
 * collection log slot.
 *
 * <p>A drop names the NPC and carries item ids; a collection log slot only has
 * the item name, because that is all the game's chat line gives.
 *
 * <p>{@link #at} is the sender's clock and is never adjusted on receipt: the
 * same event is resent whenever someone joins the party, and the time is what
 * lets {@link DropLog} recognise it.
 */
final class GroupEvent
{
	enum Type
	{
		DROP, CLOG
	}

	/** Longest NPC or item name kept. The longest real ones are under 40. */
	static final int MAX_TEXT = 64;
	/** Most distinct items kept from one drop. */
	static final int MAX_DROP_ITEMS = 40;

	final Type type;
	final long at;
	final String member;
	/** The NPC for a drop, the item name for a collection log slot. */
	final String text;
	/** Item id to quantity for a drop; empty for a collection log slot. */
	final Map<Integer, Integer> items;

	private GroupEvent(Type type, long at, String member, String text, Map<Integer, Integer> items)
	{
		this.type = type;
		this.at = at;
		this.member = member == null ? "" : member.trim();
		String t = text == null ? "" : text.trim();
		this.text = t.length() > MAX_TEXT ? t.substring(0, MAX_TEXT) : t;
		Map<Integer, Integer> kept = new TreeMap<>();
		for (Map.Entry<Integer, Integer> e : items.entrySet())
		{
			if (kept.size() >= MAX_DROP_ITEMS)
			{
				break;
			}
			kept.put(e.getKey(), e.getValue());
		}
		this.items = Collections.unmodifiableMap(kept);
	}

	static GroupEvent drop(long at, String member, String npc, Map<Integer, Integer> items)
	{
		return new GroupEvent(Type.DROP, at, member, npc, items);
	}

	static GroupEvent clog(long at, String member, String item)
	{
		return new GroupEvent(Type.CLOG, at, member, item, Collections.emptyMap());
	}

	GroupEvent withMember(String name)
	{
		return new GroupEvent(type, at, name, text, items);
	}

	/** Worth logging: has a name, and a drop has at least one item. */
	boolean isValid()
	{
		return !text.isEmpty() && (type == Type.CLOG || !items.isEmpty());
	}

	/** The same event seen twice, e.g. once live and once in a resend. */
	boolean sameAs(GroupEvent o)
	{
		return type == o.type && at == o.at && member.equalsIgnoreCase(o.member)
			&& text.toLowerCase(Locale.ROOT).equals(o.text.toLowerCase(Locale.ROOT)) && items.equals(o.items);
	}

	/** "Vorkath dropped 2 x Dragon bones" or "new collection log slot, Draconic visage". */
	String describe(ItemLookup lookup)
	{
		if (type == Type.CLOG)
		{
			return "new collection log slot, " + text;
		}
		return text + " dropped " + StorageLog.describe(items, lookup);
	}
}
