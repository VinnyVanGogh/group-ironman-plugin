package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the on-screen "Path to" card says: the stop being walked to, what to
 * get there and for whom, and the stop after it on a "Path to next" tour.
 * Pure: the plugin builds it once per redraw from the same cards the Shopping
 * tab draws, and the overlay only reads it, so the card never disagrees with
 * the tab.
 */
final class PathStopPlan
{
	/** One thing to get at the stop. */
	static final class Entry
	{
		final String item;
		final long amount;
		/** "2 gp each" for a shop that prices it; empty otherwise. */
		final String price;
		/** Members it is for ("Alice, Bob"); empty when nobody is named. */
		final String forWho;

		Entry(String item, long amount, String price, String forWho)
		{
			this.item = item;
			this.amount = amount;
			this.price = price;
			this.forWho = forWho;
		}
	}

	final String stop;
	final boolean bank;
	final List<Entry> entries;
	/** The stop after this one on a "Path to next" tour; empty for none. */
	final String then;

	PathStopPlan(String stop, boolean bank, List<Entry> entries, String then)
	{
		this.stop = stop;
		this.bank = bank;
		this.entries = Collections.unmodifiableList(entries);
		this.then = then;
	}

	/** "Buy" at a shop, "Withdraw" at a bank: group storage is where the bank stops come from. */
	String verb()
	{
		return bank ? "Withdraw" : "Buy";
	}

	/** The plan for the path in force, or null when there is none or its target is gone. */
	static PathStopPlan of(ItemPaths.Target target, ItemPaths.Stop stop, List<ShoppingBoard.Card> lists,
		List<RequestBoard.Card> requests, ItemPaths.Planner planner)
	{
		if (target == null || stop == null)
		{
			return null;
		}
		if (target.kind == ItemPaths.Target.Kind.REQUEST_ITEM)
		{
			return new PathStopPlan(stop.where(), stop.bank, requestEntries(target, stop, requests), "");
		}
		for (ShoppingBoard.Card c : lists)
		{
			if (c.list.id.equals(target.id))
			{
				return new PathStopPlan(stop.where(), stop.bank, listEntries(target, stop, c, planner),
					target.kind == ItemPaths.Target.Kind.LIST_NEXT ? following(planner.next(c), stop) : "");
			}
		}
		return null;
	}

	/** The names of the list items {@code stop} supplies: what the stop is for. */
	static List<String> supplies(ShoppingBoard.Card card, ItemPaths.Stop stop, ItemPaths.Planner planner)
	{
		List<String> out = new ArrayList<>();
		for (ShoppingBoard.Row r : card.rows)
		{
			if (ItemPaths.named(planner.shopping(r), stop.name))
			{
				out.add(r.line.name);
			}
		}
		return out;
	}

	private static List<Entry> listEntries(ItemPaths.Target target, ItemPaths.Stop stop, ShoppingBoard.Card card,
		ItemPaths.Planner planner)
	{
		List<Entry> out = new ArrayList<>();
		for (ShoppingBoard.Row r : card.rows)
		{
			// "Path to" one line is about that line; a list's "next" is about everything the stop serves.
			boolean wanted = target.kind == ItemPaths.Target.Kind.LIST_NEXT || r.line.itemId == target.itemId;
			if (wanted && ItemPaths.named(planner.shopping(r), stop.name))
			{
				out.add(entry(r, stop));
			}
		}
		return out;
	}

	private static Entry entry(ShoppingBoard.Row r, ItemPaths.Stop stop)
	{
		List<String> who = new ArrayList<>();
		long amount = 0;
		for (ShopAllocation.Share s : r.line.shares)
		{
			long part = stop.bank ? s.fromStorage : s.shortBy;
			if (part > 0)
			{
				who.add(s.member);
				amount += part;
			}
		}
		if (stop.bank)
		{
			amount = r.line.storageUsed;
		}
		else if (amount == 0)
		{
			amount = r.line.stillNeeded();
		}
		return new Entry(r.line.name, amount, stop.bank ? "" : price(r.sources, stop.name), String.join(", ", who));
	}

	private static String price(ShopSources.Options sources, String shop)
	{
		for (ShopSources.Source s : sources.sources)
		{
			if (s.shop && s.name.equals(shop) && s.price >= 0)
			{
				return Amounts.GAME.of(s.price) + " gp each";
			}
		}
		return "";
	}

	private static List<Entry> requestEntries(ItemPaths.Target target, ItemPaths.Stop stop,
		List<RequestBoard.Card> requests)
	{
		List<Entry> out = new ArrayList<>();
		for (RequestBoard.Card c : requests)
		{
			if (!c.request.id.equals(target.id))
			{
				continue;
			}
			for (RequestBoard.Row r : c.rows)
			{
				if (r.itemId == target.itemId && !r.done())
				{
					out.add(new Entry(r.name, Math.max(0, r.wanted - r.have), "", String.join(", ", r.claimedBy)));
				}
			}
		}
		return out;
	}

	private static String following(List<ItemPaths.Stop> tour, ItemPaths.Stop now)
	{
		for (int i = 0; i + 1 < tour.size(); i++)
		{
			if (tour.get(i).name.equals(now.name))
			{
				return tour.get(i + 1).where();
			}
		}
		return "";
	}
}
