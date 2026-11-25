package com.vinnyvangogh.groupironman;

/**
 * The words of a Shopping request event: made, claimed or done. The requests themselves are Group Shopping's
 * ({@link GroupShopping}); it hands GIM this player's request events, and GIM shows and shares them like any other.
 */
final class RequestBoard
{
	static final long KIND_MADE = 1;
	static final long KIND_CLAIMED = 2;
	static final long KIND_DONE = 3;

	private RequestBoard()
	{
	}

	/** {@link EventText}'s line for a request event. */
	static String describe(GroupEvent e)
	{
		if (e.number == KIND_CLAIMED)
		{
			int cut = e.detail.lastIndexOf(" for ");
			return cut < 0 ? "will get " + e.detail + " ('" + e.text + "')"
				: "will get " + e.detail.substring(0, cut) + " for " + e.detail.substring(cut + 5) + "'s '" + e.text
				+ "'";
		}
		if (e.number == KIND_DONE)
		{
			return "'" + e.text + "' is done: the group has it all";
		}
		return "made a request, '" + e.text + "'" + (e.detail.isEmpty() ? "" : ": " + e.detail);
	}
}
