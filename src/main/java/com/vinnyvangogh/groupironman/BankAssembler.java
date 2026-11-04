package com.vinnyvangogh.groupironman;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Puts a member's bank back together from its parts.
 *
 * <p>Parts can arrive out of order, twice (a resend on a party sync), or not
 * at all (the member logged out mid-send). A snapshot is only handed on once
 * every part of the same snapshot is in; a newer snapshot discards an
 * unfinished older one, and a part of an older snapshot is ignored.
 *
 * <p>Synchronized: party messages arrive on OkHttp's websocket thread, and
 * leaving a party clears it from the client thread.
 */
final class BankAssembler
{
	private static final class Pending
	{
		final long takenAt;
		final Map<Integer, Integer>[] parts;

		@SuppressWarnings("unchecked")
		Pending(long takenAt, int of)
		{
			this.takenAt = takenAt;
			this.parts = new Map[of];
		}

		boolean complete()
		{
			for (Map<Integer, Integer> p : parts)
			{
				if (p == null)
				{
					return false;
				}
			}
			return true;
		}
	}

	private final Map<Long, Pending> pending = new HashMap<>();

	/** The whole snapshot when this part completes it, else null. */
	synchronized BankSnapshot accept(long memberId, GroupCodec.BankPart part)
	{
		Pending p = pending.get(memberId);
		if (p != null && part.takenAt < p.takenAt)
		{
			return null;
		}
		if (p == null || part.takenAt > p.takenAt || p.parts.length != part.of)
		{
			p = new Pending(part.takenAt, part.of);
			pending.put(memberId, p);
		}
		p.parts[part.part] = part.items;
		if (!p.complete())
		{
			return null;
		}
		pending.remove(memberId);
		Map<Integer, Integer> all = new TreeMap<>();
		for (Map<Integer, Integer> items : p.parts)
		{
			for (Map.Entry<Integer, Integer> e : items.entrySet())
			{
				all.merge(e.getKey(), e.getValue(), ItemCounts::saturatingAdd);
			}
		}
		return new BankSnapshot(p.takenAt, all);
	}

	synchronized void forget(long memberId)
	{
		pending.remove(memberId);
	}

	synchronized void clear()
	{
		pending.clear();
	}
}
