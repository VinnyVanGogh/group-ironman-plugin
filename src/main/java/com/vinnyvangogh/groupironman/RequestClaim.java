package com.vinnyvangogh.groupironman;

/**
 * One member saying "I'll get it" for one item of a request, or taking it
 * back. Each member writes only their own claims, so the newest record for
 * a (request, item, member) wins ({@link #newerThan}) and two members
 * claiming the same item at once both keep their claim.
 */
final class RequestClaim
{
	final String requestId;
	final int itemId;
	final String member;
	final boolean claimed;
	final long at;

	RequestClaim(String requestId, int itemId, String member, boolean claimed, long at)
	{
		this.requestId = requestId;
		this.itemId = itemId;
		this.member = ShoppingList.cap(member, ShoppingList.MAX_TEXT);
		this.claimed = claimed;
		this.at = at;
	}

	/** One record per request, item and member, however the member's name is spelt. */
	String key()
	{
		return requestId + ":" + itemId + ":" + GroupMembers.key(member);
	}

	/** Later wins; at the same moment, taking a claim back wins, so every client lands on the same answer. */
	boolean newerThan(RequestClaim other)
	{
		if (other == null)
		{
			return true;
		}
		return at != other.at ? at > other.at : !claimed && other.claimed;
	}
}
