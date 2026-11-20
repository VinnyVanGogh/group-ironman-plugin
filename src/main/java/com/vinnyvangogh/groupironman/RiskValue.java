package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What a member stands to lose on death: inventory and worn items ranked by Grand Exchange guide price, the
 * most valuable kept, the rest lost, and a looting bag's contents always lost (they are never part of the
 * kept-item ranking).
 *
 * <p>Kept counts: 3 unskulled, 0 skulled, one more with the Protect Item prayer. Stackables are ranked one
 * unit at a time, so a stack never counts as a single kept item; that errs towards showing less kept, so
 * more at risk (not verified in game, see BRIEF.md). Items with no known price rank last and count for 0.
 *
 * <p>Ranking always uses the guide price, as the game does; {@code alch} only changes the figure shown.
 */
final class RiskValue
{
	static final int KEEP_UNSKULLED = 3;
	static final int KEEP_SKULLED = 0;

	private static final class Unit
	{
		final int id;
		final long rank;
		final long shown;
		final int count;

		Unit(int id, long rank, long shown, int count)
		{
			this.id = id;
			this.rank = rank;
			this.shown = shown;
			this.count = count;
		}
	}

	/** One scenario: what is kept and what is lost, in item lines, and the lost value in gp. */
	static final class Outcome
	{
		final int kept;
		final long lostValue;
		final List<String> keptLines;
		final List<String> lostLines;

		Outcome(int kept, long lostValue, List<String> keptLines, List<String> lostLines)
		{
			this.kept = kept;
			this.lostValue = lostValue;
			this.keptLines = Collections.unmodifiableList(keptLines);
			this.lostLines = Collections.unmodifiableList(lostLines);
		}
	}

	private final List<Unit> ranked;
	private final long bagValue;
	private final List<String> bagLines;
	private final boolean bagKnown;
	private final ItemLookup items;

	private RiskValue(List<Unit> ranked, long bagValue, List<String> bagLines, boolean bagKnown, ItemLookup items)
	{
		this.ranked = ranked;
		this.bagValue = bagValue;
		this.bagLines = bagLines;
		this.bagKnown = bagKnown;
		this.items = items;
	}

	/**
	 * @param carried the member's inventory, worn items and, when known, looting bag
	 * @param alch show High Alchemy value instead of the guide price
	 */
	static RiskValue of(GroupCodec.Carried carried, ItemLookup items, boolean alch)
	{
		Map<Integer, Integer> all = new TreeMap<>();
		for (Map.Entry<Integer, Integer> e : carried.inventory.entrySet())
		{
			all.merge(e.getKey(), e.getValue(), Integer::sum);
		}
		for (Map.Entry<Integer, Integer> e : carried.worn.entrySet())
		{
			all.merge(e.getKey(), e.getValue(), Integer::sum);
		}
		List<Unit> units = new ArrayList<>();
		for (Map.Entry<Integer, Integer> e : all.entrySet())
		{
			if (e.getValue() > 0)
			{
				units.add(new Unit(e.getKey(), items.price(e.getKey()), shown(items, e.getKey(), alch), e.getValue()));
			}
		}
		units.sort(Comparator.<Unit>comparingLong(u -> -u.rank).thenComparingInt(u -> u.id));
		GroupCodec.Held bag = carried.contents.get(SkillingContainer.LOOTING_BAG);
		long bagValue = 0;
		List<String> bagLines = new ArrayList<>();
		if (bag != null)
		{
			for (Map.Entry<Integer, Integer> e : bag.items.entrySet())
			{
				long unit = shown(items, e.getKey(), alch);
				bagValue += unit * e.getValue();
				bagLines.add(Amounts.EXACT.of(e.getValue()) + " x " + items.name(e.getKey()));
			}
		}
		return new RiskValue(units, bagValue, bagLines, bag != null, items);
	}

	private static long shown(ItemLookup items, int id, boolean alch)
	{
		return alch ? items.highAlch(id) : items.price(id);
	}

	/** Whether the member's looting bag contents are known: when not, its contents are not in the figure. */
	boolean bagKnown()
	{
		return bagKnown;
	}

	long bagValue()
	{
		return bagValue;
	}

	/** Keeping the {@code keep} most valuable items: everything else, plus the looting bag's contents, is lost. */
	Outcome keeping(int keep)
	{
		int left = Math.max(0, keep);
		long lost = bagValue;
		List<String> keptLines = new ArrayList<>();
		List<String> lostLines = new ArrayList<>();
		for (Unit u : ranked)
		{
			int kept = Math.min(left, u.count);
			left -= kept;
			int gone = u.count - kept;
			String name = items.name(u.id);
			if (kept > 0)
			{
				keptLines.add(Amounts.EXACT.of(kept) + " x " + name);
			}
			if (gone > 0)
			{
				lost += u.shown * gone;
				lostLines.add(Amounts.EXACT.of(gone) + " x " + name);
			}
		}
		lostLines.addAll(bagLines);
		return new Outcome(keep, lost, keptLines, lostLines);
	}

	/** The risk of a member who is {@code skulled}, with or without Protect Item. */
	Outcome outcome(boolean skulled, boolean protect)
	{
		return keeping((skulled ? KEEP_SKULLED : KEEP_UNSKULLED) + (protect ? 1 : 0));
	}
}
