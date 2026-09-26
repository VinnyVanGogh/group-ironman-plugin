package com.vinnyvangogh.buildguard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How many levels a temporary boost is actually worth to this account, right
 * now.
 *
 * <p>The quest graph has always carried a {@code boostable} flag per
 * requirement, straight from the wiki's {@code {{boostable|yes}}} marker. That
 * flag says a boost is <em>permitted</em>. It never said how large one can be,
 * so a fifty-level Thieving shortfall was reported as covered exactly as
 * confidently as a one-level Cooking one -- and the Cooking case was reported
 * as a nine-level boost, which no item in the game provides at any level.
 *
 * <p>The bundle now carries the magnitudes, so the question has an answer.
 * Given a skill and the level held, this returns the best boost available and
 * how much it gives, and the caller can compare that against the shortfall
 * instead of assuming.
 *
 * <p>Random boosts are excluded from the answer and reported separately. A
 * spicy stew is the only thing that reaches some levels, but it drains as
 * readily as it boosts, and a quest plan built on a coin flip is not a plan.
 */
class Boosts
{
	/** What one boost is worth at a given level, and what to drink. */
	static class Best
	{
		private final int levels;
		private final String item;
		private final boolean random;

		Best(int levels, String item, boolean random)
		{
			this.levels = levels;
			this.item = item;
			this.random = random;
		}

		int levels()
		{
			return levels;
		}

		String item()
		{
			return item;
		}

		/** Whether this one can drain instead of boosting. */
		boolean random()
		{
			return random;
		}
	}

	/** Nothing available: no boost known for the skill at all. */
	static final Best NONE = new Best(0, "", false);

	private final Map<String, List<QuestGraph.Boost>> bySkill = new HashMap<>();

	Boosts(List<QuestGraph.Boost> boosts)
	{
		if (boosts == null)
		{
			return;
		}
		for (QuestGraph.Boost boost : boosts)
		{
			bySkill.computeIfAbsent(boost.skill().toLowerCase(), key -> new ArrayList<>())
				.add(boost);
		}
	}

	/** Whether the bundle carried any boost data at all. */
	boolean isEmpty()
	{
		return bySkill.isEmpty();
	}

	/**
	 * How many levels this boost gives an account at the level it has now.
	 *
	 * <p>Clamped to the wiki's stated ceiling, because the formulae are linear
	 * and the game's caps are not: an Attack potion is
	 * {@code 3 + floor(level * 0.1)}, which unclamped reads 13 at level 99 and
	 * the wiki says 12.
	 *
	 * <p>A random boost returns zero. It is not that it gives nothing -- it is
	 * that it reliably gives nothing, which is the only figure a plan can be
	 * built on.
	 */
	static int valueAt(QuestGraph.Boost boost, int level)
	{
		if (boost.random())
		{
			return 0;
		}
		int value = boost.base();
		if (boost.perLevel() > 0)
		{
			value = boost.base() + (int) Math.floor(level * boost.perLevel());
		}
		if (boost.max() > 0 && value > boost.max())
		{
			value = boost.max();
		}
		return Math.max(0, value);
	}

	/**
	 * The best reliable boost for this skill at this level.
	 *
	 * Returns {@link #NONE} when the skill has no boosts, or when everything
	 * it has is random -- both of which mean the same thing to a caller
	 * deciding whether a shortfall is coverable.
	 */
	Best bestFor(String skill, int level)
	{
		List<QuestGraph.Boost> boosts = bySkill.get(skill == null ? "" : skill.toLowerCase());
		if (boosts == null)
		{
			return NONE;
		}
		Best best = NONE;
		for (QuestGraph.Boost boost : boosts)
		{
			int value = valueAt(boost, level);
			if (value > best.levels())
			{
				best = new Best(value, boost.item(), false);
			}
		}
		return best;
	}

	/**
	 * The best boost including the ones that might drain instead.
	 *
	 * Kept apart from {@link #bestFor} so a caller can decide whether a quest
	 * is reachable using only what is reliable, and still be able to mention
	 * that a stew exists and might get you there. Merging the two would make
	 * a coin flip look like a plan.
	 */
	Best bestIncludingRandom(String skill, int level)
	{
		List<QuestGraph.Boost> boosts = bySkill.get(skill == null ? "" : skill.toLowerCase());
		if (boosts == null)
		{
			return NONE;
		}
		Best best = bestFor(skill, level);
		for (QuestGraph.Boost boost : boosts)
		{
			if (!boost.random())
			{
				continue;
			}
			// A random boost's ceiling is what it might give, which is the
			// only interesting thing about it.
			int ceiling = boost.max() > 0 ? boost.max() : boost.base();
			if (ceiling > best.levels())
			{
				best = new Best(ceiling, boost.item(), true);
			}
		}
		return best;
	}

	/**
	 * Whether a shortfall in this skill can actually be boosted away.
	 *
	 * <p>The whole point of the class. {@code shortfall} is how many levels
	 * are missing; this says whether anything drinkable covers it at the level
	 * currently held. A skill with no boost data returns false rather than
	 * true: the bundle is expected to carry boosts, and treating missing data
	 * as permission is how the original bug worked.
	 */
	boolean covers(String skill, int level, int shortfall)
	{
		return shortfall > 0 && bestFor(skill, level).levels() >= shortfall;
	}
}
