package com.vinnyvangogh.groupironman;

import java.util.Map;

/**
 * A member's bank as it was when they last closed it: item counts and when.
 * Tab-free and slot-free; see {@link ItemCounts}.
 */
final class BankSnapshot
{
	private final long takenAt;
	private final Map<Integer, Integer> items;

	BankSnapshot(long takenAt, Map<Integer, Integer> items)
	{
		this.takenAt = takenAt;
		this.items = ItemCounts.sorted(items);
	}

	/** Epoch millis on the sender's clock when the bank was closed. */
	long takenAt()
	{
		return takenAt;
	}

	Map<Integer, Integer> items()
	{
		return items;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof BankSnapshot))
		{
			return false;
		}
		BankSnapshot b = (BankSnapshot) o;
		return takenAt == b.takenAt && items.equals(b.items);
	}

	@Override
	public int hashCode()
	{
		return Long.hashCode(takenAt) * 31 + items.hashCode();
	}
}
