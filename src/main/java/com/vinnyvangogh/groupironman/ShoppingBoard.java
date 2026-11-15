package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

/**
 * What the Shopping tab shows for each live list: every item line with what
 * the group still has to get, who holds what, and where this player can get
 * it. Pure: the plugin captures the inputs, the panel draws the cards.
 */
final class ShoppingBoard
{
	/** One item line, with where the viewer can get it. */
	static final class Row
	{
		final ShopAllocation.Line line;
		final ShopSources.Options sources;

		Row(ShopAllocation.Line line, ShopSources.Options sources)
		{
			this.line = line;
			this.sources = sources;
		}
	}

	/** One list, worked out. */
	static final class Card
	{
		final ShoppingList list;
		/** The preset, or null for a custom list or a preset this build lacks. */
		final ShopPresets.Preset preset;
		final List<Row> rows;
		/** Members on the list whose quest progress this client has not heard: every quest counts for them. */
		final List<String> progressUnknown;
		/** Members on the list who share no item counts: their need is not judged. */
		final List<String> itemsUnknown;

		Card(ShoppingList list, ShopPresets.Preset preset, List<Row> rows, List<String> progressUnknown,
			List<String> itemsUnknown)
		{
			this.list = list;
			this.preset = preset;
			this.rows = Collections.unmodifiableList(rows);
			this.progressUnknown = Collections.unmodifiableList(progressUnknown);
			this.itemsUnknown = Collections.unmodifiableList(itemsUnknown);
		}

		int done()
		{
			int n = 0;
			for (Row r : rows)
			{
				n += r.line.done() ? 1 : 0;
			}
			return n;
		}

		/** A preset the data has nothing left for, or a custom list with no items: nothing to show but that. */
		boolean empty()
		{
			return rows.isEmpty();
		}
	}

	/** Everything a board needs; captured on the client side. */
	static final class Inputs
	{
		final QuestData data;
		final ShopPresets presets;
		final Function<String, QuestProgress> progress;
		final ShopAllocation.Holdings holdings;
		final IntPredicate tradeable;
		final IntFunction<String> names;
		final ShopSources.Viewer viewer;

		Inputs(QuestData data, ShopPresets presets, Function<String, QuestProgress> progress,
			ShopAllocation.Holdings holdings, IntPredicate tradeable, IntFunction<String> names,
			ShopSources.Viewer viewer)
		{
			this.data = data;
			this.presets = presets;
			this.progress = progress;
			this.holdings = holdings;
			this.tradeable = tradeable;
			this.names = names;
			this.viewer = viewer;
		}
	}

	private ShoppingBoard()
	{
	}

	static List<Card> of(List<ShoppingList> lists, Inputs in)
	{
		List<Card> out = new ArrayList<>();
		for (ShoppingList l : lists)
		{
			out.add(card(l, in));
		}
		return out;
	}

	static Card card(ShoppingList list, Inputs in)
	{
		ShopPresets.Preset preset = list.custom() ? null : in.presets.preset(list.preset);
		Map<String, Map<Integer, ShopNeeds.Need>> needs = new LinkedHashMap<>();
		List<String> progressUnknown = new ArrayList<>();
		List<String> itemsUnknown = new ArrayList<>();
		for (String member : list.members)
		{
			if (preset != null)
			{
				QuestProgress p = in.progress.apply(member);
				if (!p.known())
				{
					progressUnknown.add(member);
				}
				needs.put(member, ShopNeeds.forMember(in.data, preset.roots(list.picks), p));
			}
			else
			{
				needs.put(member, custom(list, in.names));
			}
			if (!in.holdings.known(member))
			{
				itemsUnknown.add(member);
			}
		}
		List<Row> rows = new ArrayList<>();
		for (ShopAllocation.Line line : ShopAllocation.of(needs, in.holdings, in.tradeable))
		{
			rows.add(new Row(line, ShopSources.forViewer(line.itemId, in.data.item(line.itemId), in.viewer, in.data,
				ItemDrops.bundled())));
		}
		return new Card(list, preset, rows, progressUnknown, itemsUnknown);
	}

	private static Map<Integer, ShopNeeds.Need> custom(ShoppingList list, IntFunction<String> names)
	{
		Map<Integer, ShopNeeds.Need> out = new LinkedHashMap<>();
		list.items.forEach((id, qty) -> out.put(id, new ShopNeeds.Need(id, names.apply(id), qty, true, false,
			Collections.emptySet())));
		return out;
	}

	/**
	 * Shops this player can be served at, each with the still-needed lines it sells, across every list. A covered
	 * line, or one only members without shared items need, never makes a shop a candidate.
	 */
	static Map<String, List<ShopAllocation.Line>> byShop(List<Card> cards)
	{
		Map<String, List<ShopAllocation.Line>> out = new LinkedHashMap<>();
		for (Card c : cards)
		{
			for (Row r : c.rows)
			{
				if (r.line.stillNeeded() <= 0)
				{
					continue;
				}
				for (ShopSources.Source s : r.sources.sources)
				{
					if (s.shop)
					{
						out.computeIfAbsent(s.name, k -> new ArrayList<>()).add(r.line);
					}
				}
			}
		}
		return out;
	}

	/** "your group needs 4 more Bucket, 3 more Rope and 2 other items"; empty for no lines. */
	static String describe(List<ShopAllocation.Line> lines)
	{
		if (lines == null || lines.isEmpty())
		{
			return "";
		}
		StringBuilder text = new StringBuilder("your group needs ");
		int named = Math.min(3, lines.size());
		for (int i = 0; i < named; i++)
		{
			ShopAllocation.Line line = lines.get(i);
			text.append(i == 0 ? "" : ", ").append(Amounts.GAME.of(line.stillNeeded())).append(" more ")
				.append(line.name);
		}
		int rest = lines.size() - named;
		if (rest > 0)
		{
			text.append(" and ").append(rest).append(" other item").append(rest == 1 ? "" : "s");
		}
		return text.toString();
	}
}
