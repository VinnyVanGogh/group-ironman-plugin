package com.vinnyvangogh.buildguard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Which skill to point a lamp at, and which to never point it at.
 *
 * <p>A port of questpath's {@code internal/planner/lampchoice.go}, held to it
 * by {@link LampVectorsTest} and the shared {@code lamp-vectors.json} the Go
 * side generates. That test is not a nicety. Two implementations of one rule
 * set drift, both render confidently, and here the disagreement lands on a
 * choice that cannot be taken back — the interface asks once and the
 * experience is spent.
 *
 * <p>The classification order is load-bearing and changing it changes
 * answers:
 *
 * <ol>
 * <li><b>NO_XP first</b>, before the lamp's own requirements and before the
 *     amount is consulted at all, because it is true regardless of both.
 *     Reporting a NO_XP skill as merely "below this lamp's minimum" would
 *     describe a permanent wall as a temporary one.
 * <li><b>The lamp's own restrictions</b> — the skills it does not allow, and
 *     its level floor.
 * <li><b>Unknown</b>, for a lamp with no modelled amount against a skill that
 *     carries a cap. The cap is real and the amount is not known, so whether
 *     one crosses the other is genuinely unanswerable, and saying so is the
 *     honest answer where a guess would cost the most.
 * <li><b>The caps</b>, then <b>the risks</b>.
 * </ol>
 *
 * <p>Pure: an account, a lamp and some optional context in, a list of
 * verdicts out. No client, no Swing, no I/O.
 */
class LampAdvisor
{
	/**
	 * Everything beyond the account that sharpens the advice, all optional.
	 *
	 * <p>Optional is the point, and in Build Guard two of the three are
	 * <b>always</b> absent: {@link Inputs#routeNeeds} and {@link Inputs#rates}
	 * came from questpath's saved route and its configured experience rates, and
	 * this plugin has neither. They are kept because the generated vectors
	 * exercise them — a case supplies both, and an advisor that ignored them
	 * would rank differently from the Go original while still looking correct.
	 * Dropping a parameter the vectors set is how a port comes to agree by
	 * accident.
	 *
	 * <p>Advice computed without them is still worth giving; it is simply a
	 * weaker claim, and {@link Inputs} records which parts were present so the
	 * display can say so rather than implying it had everything.
	 */
	static class Inputs
	{
		private Map<String, Integer> routeNeeds = Collections.emptyMap();
		private int freeCeiling;
		private boolean hasCeiling;
		private Map<String, Double> rates = Collections.emptyMap();

		Inputs routeNeeds(Map<String, Integer> needs)
		{
			this.routeNeeds = needs == null ? Collections.emptyMap() : needs;
			return this;
		}

		/**
		 * The highest Ranged or Magic level that does not raise the combat
		 * level, from {@link CombatMath#freeRangedCeiling(int, int)}.
		 *
		 * <p>Passed in rather than computed here so this class stays pure and
		 * so both languages feed the advisor from their own combat code — code
		 * already pinned to the same vectors, which is what stops the two
		 * drifting.
		 */
		Inputs freeCeiling(int ceiling)
		{
			this.freeCeiling = ceiling;
			this.hasCeiling = true;
			return this;
		}

		Inputs rates(Map<String, Double> rates)
		{
			this.rates = rates == null ? Collections.emptyMap() : rates;
			return this;
		}

		boolean hasCeiling()
		{
			return hasCeiling;
		}
	}

	private LampAdvisor()
	{
	}

	/**
	 * Classifies every skill on the lamp's interface and ranks the ones worth
	 * choosing.
	 *
	 * @param skills the skills to classify, in the order the caller will draw
	 *               them — the returned list keeps that order, and the rank
	 *               carries the ordering, so re-sorting the result would make
	 *               the two disagree.
	 */
	static List<LampVerdict> advise(Lamp lamp, PlayerState account, List<String> skills, Inputs inputs)
	{
		Inputs in = inputs == null ? new Inputs() : inputs;
		List<LampVerdict> out = new ArrayList<>(skills.size());
		for (String skill : skills)
		{
			out.add(classify(lamp, skill, account, in));
		}
		rank(out, account, in);
		return out;
	}

	private static LampVerdict classify(Lamp lamp, String skill, PlayerState account, Inputs in)
	{
		int level = account.levelIn(skill);
		long currentXp = account.experienceIn(skill);
		double xp = lamp.xpAt(level);
		boolean xpKnown = lamp.modelled();
		int levelAfter = xpKnown ? XpTable.levelFor(currentXp + (long) Math.floor(xp)) : level;

		BuildRule rule = account.ruleFor(skill);

		// 1. NO_XP, before everything, because it does not depend on the lamp.
		if (rule != null && BuildRule.NO_XP.equals(rule.type()))
		{
			return verdict(skill, LampVerdict.State.BLOCKED, LampVerdict.Block.NO_XP,
				LampVerdict.Risk.NONE, xp, xpKnown, level, levelAfter, 0,
				"this build keeps it at NO_XP -- any lamp xp here breaks it");
		}

		// 2. The lamp's own restrictions.
		if (!lamp.allows(skill))
		{
			return verdict(skill, LampVerdict.State.BLOCKED, LampVerdict.Block.NOT_ALLOWED,
				LampVerdict.Risk.NONE, xp, xpKnown, level, levelAfter, 0,
				"this lamp cannot be spent here at all");
		}
		if (lamp.minLevel() > 0 && level < lamp.minLevel())
		{
			return verdict(skill, LampVerdict.State.BLOCKED, LampVerdict.Block.BELOW_MINIMUM,
				LampVerdict.Risk.NONE, xp, xpKnown, level, levelAfter, 0,
				"below this lamp's level " + lamp.minLevel() + " minimum (currently " + level + ")");
		}

		// 3. An unmodelled lamp against a capped skill is unanswerable. GOAL
		// is deliberately not a cap here: overshooting a goal costs nothing,
		// so an unknown amount against one is not a risk worth its own state.
		boolean capped = rule != null
			&& (BuildRule.HARD_CAP.equals(rule.type()) || BuildRule.SOFT_CAP.equals(rule.type()));
		if (!xpKnown && capped)
		{
			return verdict(skill, LampVerdict.State.UNKNOWN, LampVerdict.Block.NONE,
				LampVerdict.Risk.NONE, xp, false, level, levelAfter, 0,
				"this lamp's xp is not modelled, and this skill is capped");
		}

		// 4. The caps. Guarded rather than an early return, because the
		// ceiling check below has to run whether or not a rule exists -- the
		// account this ceiling exists for is a pure, which has no rule on
		// Ranged at all. That is exactly why nothing else warns about it.
		if (rule != null)
		{
			if (BuildRule.HARD_CAP.equals(rule.type()) && rule.hasValue())
			{
				if (level >= rule.value())
				{
					return verdict(skill, LampVerdict.State.BLOCKED, LampVerdict.Block.AT_CAP,
						LampVerdict.Risk.NONE, xp, xpKnown, level, levelAfter, 0,
						"already at its cap (level " + rule.value() + ") -- the xp would be wasted");
				}
				if (xpKnown && levelAfter > rule.value())
				{
					return verdict(skill, LampVerdict.State.BLOCKED, LampVerdict.Block.HARD_CAP,
						LampVerdict.Risk.NONE, xp, xpKnown, level, levelAfter, 0,
						"this lamp would push it past its cap of level " + rule.value());
				}
			}
			else if (BuildRule.SOFT_CAP.equals(rule.type()))
			{
				return verdict(skill, LampVerdict.State.RISKY, LampVerdict.Block.NONE,
					LampVerdict.Risk.SOFT_CAP, xp, xpKnown, level, levelAfter, 0,
					"you asked to be warned about any xp here");
			}
		}

		// 5. The free combat ceiling. After the caps, because a hard cap is a
		// rule the account stated and the ceiling is arithmetic it may not
		// have noticed; where both apply, the stated rule is the better
		// sentence.
		if (crossesCeiling(skill, levelAfter, xpKnown, in))
		{
			return verdict(skill, LampVerdict.State.RISKY, LampVerdict.Block.NONE,
				LampVerdict.Risk.FREE_CEILING, xp, xpKnown, level, levelAfter, 0,
				"this would carry it to " + levelAfter + ", past your free ceiling of "
					+ in.freeCeiling + ", and raise your combat level");
		}

		return verdict(skill, LampVerdict.State.OK, LampVerdict.Block.NONE,
			LampVerdict.Risk.NONE, xp, xpKnown, level, levelAfter, 0, "");
	}

	/**
	 * Whether landing this lamp carries Ranged or Magic past the free band.
	 *
	 * <p>Only those two, because only they have one: the ceiling is derived
	 * from the melee term, so Attack and Strength raise the combat level
	 * immediately and the other combat skills are not what
	 * {@link CombatMath#freeRangedCeiling(int, int)} describes.
	 */
	private static boolean crossesCeiling(String skill, int levelAfter, boolean xpKnown, Inputs in)
	{
		if (!in.hasCeiling || !xpKnown)
		{
			return false;
		}
		if (!"ranged".equalsIgnoreCase(skill) && !"magic".equalsIgnoreCase(skill))
		{
			return false;
		}
		return levelAfter > in.freeCeiling;
	}

	private static LampVerdict verdict(String skill, LampVerdict.State state, LampVerdict.Block block,
		LampVerdict.Risk risk, double xp, boolean xpKnown, int levelNow, int levelAfter,
		double gained, String reason)
	{
		return new LampVerdict(skill, state, block, risk, xpKnown ? xp : 0, xpKnown,
			levelNow, levelAfter, gained, reason);
	}

	// -----------------------------------------------------------------------
	// Ranking
	// -----------------------------------------------------------------------

	/**
	 * Fills in the rank and tier of every OK verdict.
	 *
	 * <p>The rule, and the thing it exists to avoid: <b>rank by the levels the
	 * lamp actually buys, never by the largest gap remaining.</b> A lamp is a
	 * small fixed amount, so its worth depends entirely on where the skill
	 * already stands — 10,000 experience is roughly twenty-five levels at
	 * Herblore 1 and a rounding error at Mining 60. Sending it at the biggest
	 * number reliably sends it where it does the least good.
	 *
	 * <p>Three refinements on top, each of which the Go side documents at
	 * length:
	 *
	 * <ul>
	 * <li><b>Tiers.</b> A level the saved route still needs removes a gate,
	 *     which beats adding experience anywhere else.
	 * <li><b>Fractional levels.</b> Whole ones tie constantly — every skill a
	 *     lamp cannot move a full level in scores zero — and the fraction is
	 *     exactly what separates a skill the lamp nearly finishes from one it
	 *     barely touches.
	 * <li><b>A total tie-break order</b>, so the shared golden vectors are
	 *     reproducible here and not merely in Go. An unstable order is a test
	 *     that passes in one language and fails in the other for no reason a
	 *     reader could find.
	 * </ul>
	 */
	private static void rank(List<LampVerdict> verdicts, PlayerState account, Inputs in)
	{
		List<Candidate> candidates = new ArrayList<>();
		for (LampVerdict v : verdicts)
		{
			if (v.state() != LampVerdict.State.OK || !v.xpKnown())
			{
				continue;
			}
			Candidate c = new Candidate();
			c.verdict = v;
			c.tier = tierFor(v.skill(), account, in);
			c.target = targetFor(v.skill(), account, in, c.tier);
			c.gained = levelsGained(account.experienceIn(v.skill()), v.xp(), c.target);
			c.level = v.levelNow();
			Double rate = in.rates.get(Lamp.normalise(v.skill()));
			if (rate != null && rate > 0)
			{
				c.rate = rate;
				c.hasRate = true;
			}
			candidates.add(c);
		}

		candidates.sort((a, b) ->
		{
			int byTier = a.tier.compareTo(b.tier);
			if (byTier != 0)
			{
				return byTier;
			}
			int byGained = Double.compare(b.gained, a.gained);
			if (byGained != 0)
			{
				return byGained;
			}
			// Among skills a lamp moves equally it is worth most in the one
			// that would otherwise take longest. Only a sourced rate can
			// order this: a skill with no configured rate is not assumed slow
			// or fast, it sorts after every skill that has one.
			if (a.hasRate != b.hasRate)
			{
				return a.hasRate ? -1 : 1;
			}
			if (a.hasRate)
			{
				int byRate = Double.compare(a.rate, b.rate);
				if (byRate != 0)
				{
					return byRate;
				}
			}
			int byLevel = Integer.compare(a.level, b.level);
			if (byLevel != 0)
			{
				return byLevel;
			}
			return a.verdict.skill().compareTo(b.verdict.skill());
		});

		for (int i = 0; i < candidates.size(); i++)
		{
			Candidate c = candidates.get(i);
			c.verdict.rankAs(i + 1, c.tier);
			c.verdict.setLevelsGained(c.gained);
		}
	}

	private static LampVerdict.Tier tierFor(String skill, PlayerState account, Inputs in)
	{
		Integer need = in.routeNeeds.get(Lamp.normalise(skill));
		if (need != null && account.levelIn(skill) < need)
		{
			return LampVerdict.Tier.ROUTE;
		}
		BuildRule rule = account.ruleFor(skill);
		if (rule != null && BuildRule.GOAL.equals(rule.type()) && rule.hasValue()
			&& account.levelIn(skill) < rule.value())
		{
			return LampVerdict.Tier.GOAL;
		}
		return LampVerdict.Tier.OTHER;
	}

	private static int targetFor(String skill, PlayerState account, Inputs in, LampVerdict.Tier tier)
	{
		switch (tier)
		{
			case ROUTE:
				return in.routeNeeds.get(Lamp.normalise(skill));
			case GOAL:
				return account.ruleFor(skill).value();
			default:
				return 0;
		}
	}

	/**
	 * The fractional levels this lamp adds, clamped at the target.
	 *
	 * <p>Clamped because experience past what the account asked for is not a
	 * reason to prefer a skill.
	 */
	private static double levelsGained(long currentXp, double lampXp, int target)
	{
		double after = currentXp + lampXp;
		if (target > 0)
		{
			double capXp = XpTable.xpFor(target);
			if (after > capXp)
			{
				after = capXp;
			}
		}
		double before = currentXp;
		if (after <= before)
		{
			return 0;
		}
		return fractionalLevel(after) - fractionalLevel(before);
	}

	/**
	 * The level plus how far through it the experience is, so two skills a
	 * lamp cannot move a whole level in can still be told apart.
	 */
	private static double fractionalLevel(double xp)
	{
		int level = XpTable.levelFor((long) Math.floor(xp));
		if (level >= XpTable.MAX_VIRTUAL_LEVEL)
		{
			return level;
		}
		double base = XpTable.xpFor(level);
		double span = XpTable.xpFor(level + 1) - base;
		if (span <= 0)
		{
			return level;
		}
		return level + (xp - base) / span;
	}

	private static final class Candidate
	{
		private LampVerdict verdict;
		private LampVerdict.Tier tier;
		private int target;
		private double gained;
		private double rate;
		private boolean hasRate;
		private int level;
	}
}
