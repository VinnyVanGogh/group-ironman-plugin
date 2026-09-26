package com.vinnyvangogh.buildguard;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The same vectors {@code internal/osrs/combat_test.go} asserts in the
 * questpath Go app.
 *
 * They are duplicated here on purpose. This is the first piece of shared logic
 * to exist in two languages, and the only thing that will keep the two honest
 * is that both are pinned to the same numbers. When the planner itself is
 * ported, that pinning becomes generated golden vectors rather than
 * hand-copied ones — see docs/questpath-plugin-plan.md in the Go repo.
 */
public class CombatMathTest
{
	/** att 1 / str 82 / def 1 / hp 10 / rng 1 / mag 6 / pray 1 — combat 29. */
	private static final CombatStats LIVE = new CombatStats(1, 82, 1, 10, 1, 6, 1);

	@Test
	public void combatLevelMatchesTheSpecVector()
	{
		// base 0.25*(1+28+0) = 7.25, melee 0.325*85 = 27.625, ranged and magic
		// tie at 27.625, so combat = floor(34.875) = 34.
		assertEquals(34, CombatMath.combatLevel(new CombatStats(1, 84, 1, 28, 57, 57, 1)));
	}

	@Test
	public void combatLevelMatchesTheGoVectors()
	{
		assertEquals("live account", 29, CombatMath.combatLevel(LIVE));
		assertEquals("fresh account", 3, CombatMath.combatLevel(new CombatStats(1, 1, 1, 10, 1, 1, 1)));
		assertEquals("maxed main", 126, CombatMath.combatLevel(new CombatStats(99, 99, 99, 99, 99, 99, 99)));
		assertEquals("ranged term wins over melee", 50,
			CombatMath.combatLevel(new CombatStats(1, 1, 1, 10, 99, 1, 1)));
		assertEquals("magic term wins over melee", 50,
			CombatMath.combatLevel(new CombatStats(1, 1, 1, 10, 1, 99, 1)));
		assertEquals("odd prayer is floored, not rounded", 8,
			CombatMath.combatLevel(new CombatStats(1, 1, 1, 10, 1, 1, 43)));
	}

	@Test
	public void freeRangedCeilingMatchesTheGoVectors()
	{
		assertEquals("spec vector: 1 attack 84 strength", 57, CombatMath.freeRangedCeiling(1, 84));
		assertEquals("live account: 1 attack 82 strength", 55, CombatMath.freeRangedCeiling(1, 82));
		assertEquals("ceiling holds at strength 85", 57, CombatMath.freeRangedCeiling(1, 85));
		assertEquals("ceiling moves at strength 86", 58, CombatMath.freeRangedCeiling(1, 86));
		assertEquals("ceiling moves again at strength 88", 59, CombatMath.freeRangedCeiling(1, 88));
		assertEquals("fresh account", 1, CombatMath.freeRangedCeiling(1, 1));
		assertEquals("capped at 99 for a maxed melee", 99, CombatMath.freeRangedCeiling(99, 99));
	}

	/**
	 * The property the whole build rule rests on: training Ranged to the
	 * ceiling must not move the combat level.
	 *
	 * One level past the ceiling is asserted on the *unfloored* level, not the
	 * displayed one. Past the ceiling the ranged term overtakes the melee term
	 * and the exact level strictly rises — but combat is floored, and a single
	 * level past the ceiling adds only 0.325, which usually does not cross an
	 * integer boundary. An earlier version of this test asserted the displayed
	 * level rises and failed, correctly: "free" means the ceiling is the last
	 * level that costs nothing, not that the next one costs a level
	 * immediately.
	 */
	@Test
	public void freeRangedCeilingIsActuallyFree()
	{
		for (int strength = 1; strength <= CombatStats.MAX_LEVEL; strength++)
		{
			CombatStats base = new CombatStats(1, strength, 1, 10, 1, 1, 1);
			int ceiling = CombatMath.freeRangedCeiling(1, strength);

			assertEquals("strength " + strength + ": ranged at the ceiling must be free",
				CombatMath.combatLevel(base),
				CombatMath.combatLevel(base.with(Skill.RANGED, ceiling)));

			if (ceiling < CombatStats.MAX_LEVEL)
			{
				assertTrue("strength " + strength + ": past the ceiling the ranged term must start counting",
					CombatMath.combatLevelExact(base.with(Skill.RANGED, ceiling + 1))
						> CombatMath.combatLevelExact(base));
			}
		}
	}

	@Test
	public void levelsToNextCombatMatchesTheGoVectors()
	{
		Map<Skill, Integer> want = new HashMap<>();
		want.put(Skill.STRENGTH, 1);
		want.put(Skill.ATTACK, 1);
		want.put(Skill.HITPOINTS, 2);
		want.put(Skill.DEFENCE, 2);
		want.put(Skill.PRAYER, 3);
		want.put(Skill.MAGIC, 50);
		want.put(Skill.RANGED, 55);

		assertEquals(want, new HashMap<>(CombatMath.levelsToNextCombat(LIVE)));
	}

	/**
	 * Every reported delta must be exact: one level short does nothing, and
	 * the reported level raises combat by exactly one.
	 */
	@Test
	public void levelsToNextCombatActuallyReachesTheNextLevel()
	{
		int current = CombatMath.combatLevel(LIVE);

		for (Map.Entry<Skill, Integer> entry : CombatMath.levelsToNextCombat(LIVE).entrySet())
		{
			Skill skill = entry.getKey();
			int delta = entry.getValue();
			int level = LIVE.get(skill);

			assertEquals(skill + ": one level short must not raise combat",
				current, CombatMath.combatLevel(LIVE.with(skill, level + delta - 1)));
			assertEquals(skill + ": the reported level must raise combat by exactly one",
				current + 1, CombatMath.combatLevel(LIVE.with(skill, level + delta)));
		}
	}

	/**
	 * A skill that cannot raise combat at or below 99 is absent, not reported
	 * as some large number. A maxed account has no next combat level at all.
	 */
	@Test
	public void levelsToNextCombatOmitsUnreachable()
	{
		CombatStats maxed = new CombatStats(99, 99, 99, 99, 99, 99, 99);
		assertTrue("nothing raises combat past 126", CombatMath.levelsToNextCombat(maxed).isEmpty());
		assertEquals(-1, CombatMath.nextCombatLevelAt(maxed, Skill.STRENGTH));
	}

	@Test
	public void nextCombatLevelAtReportsTheLevelNotTheDelta()
	{
		// Strength 82, one level away from combat 30, so the answer is 83.
		assertEquals(83, CombatMath.nextCombatLevelAt(LIVE, Skill.STRENGTH));
	}

	/**
	 * The ceiling moves in steps as Strength grows, and the step must be the
	 * *next* one — the first Strength level that moves it at all, not some
	 * later one.
	 */
	@Test
	public void nextCeilingStepFindsTheFirstStrengthThatMovesIt()
	{
		// At 1 attack / 82 strength the ceiling is 55. 83 takes it to 56.
		CombatMath.CeilingStep step = CombatMath.nextCeilingStep(1, 82);
		assertEquals(83, step.atStrength);
		assertEquals(56, step.ceiling);
		assertEquals(1, step.gained);

		// From 83 the ceiling is 56 and 84 takes it to 57. The Go suite asserts
		// 85 -> 57 and 86 -> 58, and an earlier version of this test read that
		// as "it holds until 86". It does not: 84 already moves it, and 85
		// merely holds what 84 gained. Extrapolating between two vectors is
		// how you get a confidently wrong number.
		CombatMath.CeilingStep fromEightyThree = CombatMath.nextCeilingStep(1, 83);
		assertEquals(84, fromEightyThree.atStrength);
		assertEquals(57, fromEightyThree.ceiling);
		assertEquals(1, fromEightyThree.gained);
	}

	/**
	 * Whatever the step reports must agree with freeRangedCeiling itself, at
	 * the reported level and one below it. Two functions disagreeing about the
	 * same ceiling is exactly the drift this suite exists to catch.
	 */
	@Test
	public void nextCeilingStepAgreesWithTheCeilingFunction()
	{
		for (int strength = 1; strength < CombatStats.MAX_LEVEL; strength++)
		{
			CombatMath.CeilingStep step = CombatMath.nextCeilingStep(1, strength);
			if (step == null)
			{
				continue;
			}
			assertEquals("strength " + strength + ": the step's ceiling must be the real one",
				step.ceiling, CombatMath.freeRangedCeiling(1, step.atStrength));
			assertEquals("strength " + strength + ": one level earlier must not have moved yet",
				CombatMath.freeRangedCeiling(1, strength),
				CombatMath.freeRangedCeiling(1, step.atStrength - 1));
		}
	}

	@Test
	public void nextCeilingStepIsNullWhenNothingMovesIt()
	{
		// A maxed melee already puts the ceiling at 99.
		assertEquals(99, CombatMath.freeRangedCeiling(99, 99));
		assertEquals(null, CombatMath.nextCeilingStep(99, 99));
	}

	@Test
	public void combatProgressStaysWithinTheCurrentLevel()
	{
		double progress = CombatMath.combatProgress(LIVE);
		assertTrue("progress is not negative", progress >= 0.0);
		assertTrue("progress never reaches the next level", progress < 1.0);
		assertFalse("a real account is not exactly on a boundary here", progress == 0.0);
	}
}
