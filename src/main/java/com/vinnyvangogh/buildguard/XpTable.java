package com.vinnyvangogh.buildguard;

/**
 * The Old School RuneScape experience curve.
 *
 * A port of {@code internal/osrs/xp.go} in the questpath Go app, carrying the
 * same anchors. Pure arithmetic, no client dependency.
 *
 * <p>The client already knows your XP, so this exists for the other direction:
 * turning a level a plugin wants to talk about — the next combat level, a
 * quest requirement — back into the grind it actually costs. "Prayer +3" and
 * "Strength +1" read as comparable until you see that one is 6,000 XP and the
 * other is 42,000.
 */
public final class XpTable
{
	/** The highest level the curve is defined for. 100–126 are virtual. */
	public static final int MAX_VIRTUAL_LEVEL = 126;

	/**
	 * Cumulative experience to reach each level, indexed by level.
	 *
	 * <p>Index 0 is unused so the array can be indexed by level directly
	 * rather than by level - 1, which is the off-by-one this class exists to
	 * stop repeating.
	 */
	private static final long[] TABLE = new long[MAX_VIRTUAL_LEVEL + 1];

	static
	{
		// xp(L) = floor( ( sum_{n=1}^{L-1} floor( n + 300 * 2^(n/7) ) ) / 4 )
		//
		// The division by 4 applies to the completed sum, not to each term.
		// The two readings agree for the first few levels, which makes the
		// wrong one easy to ship; they diverge higher up, where the anchors in
		// XpTableTest catch it.
		double sum = 0;
		for (int n = 1; n < MAX_VIRTUAL_LEVEL; n++)
		{
			sum += Math.floor(n + 300 * Math.pow(2, n / 7.0));
			TABLE[n + 1] = (long) Math.floor(sum / 4);
		}
	}

	private XpTable()
	{
	}

	/**
	 * Total experience required to reach {@code level}.
	 *
	 * Levels at or below 1 return 0. Levels above the virtual maximum clamp
	 * rather than throwing, because callers routinely compute speculative
	 * targets.
	 */
	public static long xpFor(int level)
	{
		if (level <= CombatStats.MIN_LEVEL)
		{
			return 0;
		}
		return TABLE[Math.min(level, MAX_VIRTUAL_LEVEL)];
	}

	/**
	 * The level reached at {@code xp}.
	 *
	 * May exceed 99: virtual levels are meaningful when projecting forward.
	 * Callers needing an in-game level should clamp to 99 themselves.
	 */
	public static int levelFor(long xp)
	{
		if (xp <= 0)
		{
			return CombatStats.MIN_LEVEL;
		}
		// A linear scan over 126 entries, off any hot path. Binary search
		// would trade clarity for nothing measurable.
		for (int level = MAX_VIRTUAL_LEVEL; level > CombatStats.MIN_LEVEL; level--)
		{
			if (xp >= TABLE[level])
			{
				return level;
			}
		}
		return CombatStats.MIN_LEVEL;
	}

	/**
	 * Experience needed to go from level {@code a} to level {@code b}, or 0
	 * when b is at or below a — so callers can subtract without guarding
	 * against a negative gap.
	 */
	public static long xpBetween(int a, int b)
	{
		long gap = xpFor(b) - xpFor(a);
		return Math.max(gap, 0);
	}

	/**
	 * Renders an experience figure the way a player reads one: 42.2k, 1.99m.
	 * Below 10,000 it stays exact, because that is the range where the precise
	 * number is still something you can act on.
	 */
	public static String format(long xp)
	{
		if (xp < 10_000)
		{
			return String.format("%,d", xp);
		}
		if (xp < 1_000_000)
		{
			return String.format("%.1fk", xp / 1_000.0);
		}
		return String.format("%.2fm", xp / 1_000_000.0);
	}
}
