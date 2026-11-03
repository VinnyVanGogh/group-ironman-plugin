package com.vinnyvangogh.buildguard;

import java.util.Map;

/**
 * One visit to group storage by one member: what they took, what they
 * deposited, and what was left in storage when they closed it.
 *
 * <p>{@code member} is filled in by the receiver from the party's own member
 * list, never read from the payload, so a peer cannot attribute a change to
 * somebody else.
 */
final class StorageChange
{
	private final long at;
	private final String member;
	private final Map<Integer, Integer> took;
	private final Map<Integer, Integer> deposited;
	private final Map<Integer, Integer> after;

	StorageChange(long at, String member, Map<Integer, Integer> took, Map<Integer, Integer> deposited,
		Map<Integer, Integer> after)
	{
		this.at = at;
		this.member = member == null ? "" : member;
		this.took = ItemCounts.sorted(took);
		this.deposited = ItemCounts.sorted(deposited);
		this.after = ItemCounts.sorted(after);
	}

	long at()
	{
		return at;
	}

	String member()
	{
		return member;
	}

	Map<Integer, Integer> took()
	{
		return took;
	}

	Map<Integer, Integer> deposited()
	{
		return deposited;
	}

	/** Storage contents after the visit. */
	Map<Integer, Integer> after()
	{
		return after;
	}

	boolean isEmpty()
	{
		return took.isEmpty() && deposited.isEmpty();
	}

	StorageChange withMember(String name)
	{
		return new StorageChange(at, name, took, deposited, after);
	}
}
