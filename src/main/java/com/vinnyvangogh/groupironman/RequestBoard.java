package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * What the Shopping tab shows for each member's request: per item, who asked,
 * how many, how many the group already holds (banks, inventories, everything
 * "Who has it?" searches, and group storage: {@link ShopAllocation.Holdings},
 * the shopping list's own counting, canonical ids) and who said they will get
 * it. Pure: the plugin captures the inputs, the panel draws the cards.
 *
 * <p>Also the request's Discord events, so the words are tested here.
 */
final class RequestBoard
{
	static final long KIND_MADE = 1;
	static final long KIND_CLAIMED = 2;
	static final long KIND_DONE = 3;
	/** Items named in a "made a request" post; the rest are a count. */
	private static final int NAMED = 3;

	/** One item of a request. */
	static final class Row
	{
		final int itemId;
		final String name;
		final long wanted;
		final long have;
		/** Closed for good by the owner, or sealed when the group held it. */
		final boolean closed;
		final List<String> claimedBy;

		Row(int itemId, String name, long wanted, long have, boolean closed, List<String> claimedBy)
		{
			this.itemId = itemId;
			this.name = name;
			this.wanted = wanted;
			this.have = have;
			this.closed = closed;
			this.claimedBy = Collections.unmodifiableList(claimedBy);
		}

		/** Closes by itself once the group holds the amount. */
		boolean done()
		{
			return closed || have >= wanted;
		}
	}

	/** One request, worked out. */
	static final class Card
	{
		final RequestList request;
		final List<Row> rows;

		Card(RequestList request, List<Row> rows)
		{
			this.request = request;
			this.rows = Collections.unmodifiableList(rows);
		}

		int done()
		{
			int n = 0;
			for (Row r : rows)
			{
				n += r.done() ? 1 : 0;
			}
			return n;
		}

		/** The whole list is done when every item is, or the owner closed it. */
		boolean allDone()
		{
			return request.closed || done() == rows.size();
		}
	}

	private RequestBoard()
	{
	}

	static Card card(RequestList r, List<RequestClaim> claims, ShopAllocation.Holdings holdings,
		IntFunction<String> names)
	{
		List<Row> rows = new ArrayList<>();
		for (Map.Entry<Integer, Integer> e : r.items.entrySet())
		{
			int id = e.getKey();
			List<String> by = new ArrayList<>();
			for (RequestClaim c : claims)
			{
				if (c.claimed && c.itemId == id && !by.contains(c.member))
				{
					by.add(c.member);
				}
			}
			rows.add(new Row(id, names.apply(id), e.getValue(), holdings.total(id), r.closed
				|| r.closedItems.contains(id), by));
		}
		return new Card(r, rows);
	}

	/** Items the group now holds enough of that are not yet sealed: what the owner's client closes. */
	static Set<Integer> reached(Card c)
	{
		Set<Integer> out = new LinkedHashSet<>();
		for (Row r : c.rows)
		{
			if (!r.closed && r.have >= r.wanted)
			{
				out.add(r.itemId);
			}
		}
		return out;
	}

	// ---- Discord ----

	/** The owner made a request: "made a request, 'Fire cape': 500 Shark, 20 Prayer potion(4)". */
	static GroupEvent made(RequestList r, IntFunction<String> names, long now)
	{
		List<String> parts = new ArrayList<>();
		for (Map.Entry<Integer, Integer> e : r.items.entrySet())
		{
			if (parts.size() < NAMED)
			{
				parts.add(Amounts.GAME.of(e.getValue()) + " " + names.apply(e.getKey()));
			}
		}
		int rest = r.items.size() - parts.size();
		String items = String.join(", ", parts) + (rest > 0 ? " and " + rest + " more" : "");
		return GroupEvent.of(GroupEvent.Type.REQUEST, now, r.owner).text(r.name).detail(items).number(KIND_MADE)
			.build();
	}

	/** A member claimed an item: "will get Shark for Alice's 'Fire cape'". */
	static GroupEvent claimed(RequestList r, int itemId, String itemName, String member, long now)
	{
		return GroupEvent.of(GroupEvent.Type.REQUEST, now, member).text(r.name).detail(itemName + " for " + r.owner)
			.number(KIND_CLAIMED).items(Collections.singletonMap(itemId, 1)).build();
	}

	/** The owner's request is done: "'Fire cape' is done: the group has it all". */
	static GroupEvent done(RequestList r, long now)
	{
		return GroupEvent.of(GroupEvent.Type.REQUEST, now, r.owner).text(r.name).number(KIND_DONE).build();
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
