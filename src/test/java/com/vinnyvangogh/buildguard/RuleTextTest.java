package com.vinnyvangogh.buildguard;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The plain-text rule box in the config panel: one "Skill: limit" per line or comma. */
public class RuleTextTest
{
	@Test
	public void typicalPureParses()
	{
		List<BuildRule> rules = RuleText.parse("Defence: 1\nPrayer: none, Attack: 60\nStrength: goal 99\nRanged: soft");
		assertEquals(5, rules.size());

		assertEquals("Defence", rules.get(0).skillName());
		assertEquals(BuildRule.HARD_CAP, rules.get(0).type());
		assertEquals(1, rules.get(0).value());

		assertEquals(BuildRule.NO_XP, rules.get(1).type());
		assertEquals(BuildRule.HARD_CAP, rules.get(2).type());
		assertEquals(60, rules.get(2).value());
		assertEquals(BuildRule.GOAL, rules.get(3).type());
		assertEquals(99, rules.get(3).value());
		assertEquals(BuildRule.SOFT_CAP, rules.get(4).type());
	}

	@Test
	public void skillNamesAreCaseInsensitiveAndCanonicalised()
	{
		List<BuildRule> rules = RuleText.parse("defence: 1, PRAYER: no xp");
		assertEquals("Defence", rules.get(0).skillName());
		assertEquals("Prayer", rules.get(1).skillName());
		assertEquals(BuildRule.NO_XP, rules.get(1).type());
	}

	@Test
	public void unknownSkillsAndBadLimitsAreReportedNotSilentlyKept()
	{
		// US spelling, a word that is not a limit, a missing colon, and a cap
		// of 0 (which is not a level; "none" is how to say no XP).
		String text = "Defense: 1\nAttack: lots\nPrayer 1\nDefence: 0";
		assertTrue(RuleText.parse(text).isEmpty());
		assertEquals(4, RuleText.problems(text).size());
	}

	@Test
	public void laterLineOnTheSameSkillWins()
	{
		List<BuildRule> rules = RuleText.parse("Defence: 10\nDefence: 1");
		assertEquals(1, rules.size());
		assertEquals(1, rules.get(0).value());
	}

	@Test
	public void emptyTextIsNoRules()
	{
		assertTrue(RuleText.parse(null).isEmpty());
		assertTrue(RuleText.parse("  \n , ").isEmpty());
		assertTrue(RuleText.problems("  \n , ").isEmpty());
	}
}
