package com.vinnyvangogh.buildguard;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The rule this type exists for: a number the client has not actually supplied
 * must never reach the panel as if it had.
 */
public class SkillXpTest
{
	private static Map<Skill, Integer> map(Skill skill, int value)
	{
		Map<Skill, Integer> m = new EnumMap<>(Skill.class);
		m.put(skill, value);
		return m;
	}

	@Test
	public void unknownKnowsNothing()
	{
		assertFalse(SkillXp.unknown().known(Skill.STRENGTH));
		assertEquals(-1, SkillXp.unknown().get(Skill.STRENGTH));
		assertEquals(-1, SkillXp.unknown().toLevel(Skill.STRENGTH, 99));
	}

	@Test
	public void acceptsExperienceThatMatchesTheReportedLevel()
	{
		SkillXp xp = SkillXp.of(map(Skill.STRENGTH, 83), map(Skill.STRENGTH, 2_800_000));
		assertTrue(xp.known(Skill.STRENGTH));
		assertEquals(2_800_000, xp.get(Skill.STRENGTH));
	}

	/**
	 * The case this guards: LOGGED_IN has fired but the stats packet has not
	 * arrived, so getSkillExperience answers 0 for a skill the client already
	 * reports as level 83. Zero is a real value for an untrained skill, which
	 * is exactly why it cannot be trusted on its own.
	 */
	@Test
	public void rejectsExperienceBelowTheThresholdForItsLevel()
	{
		SkillXp notLoaded = SkillXp.of(map(Skill.STRENGTH, 83), map(Skill.STRENGTH, 0));
		assertFalse("zero xp at level 83 is impossible, so it is not loaded",
			notLoaded.known(Skill.STRENGTH));

		SkillXp shortOfThreshold = SkillXp.of(map(Skill.ATTACK, 2), map(Skill.ATTACK, 40));
		assertFalse("40 xp cannot be level 2; level 2 starts at 83",
			shortOfThreshold.known(Skill.ATTACK));
	}

	@Test
	public void acceptsAGenuinelyUntrainedSkill()
	{
		SkillXp xp = SkillXp.of(map(Skill.RANGED, 1), map(Skill.RANGED, 0));
		assertTrue("level 1 with 0 xp is a real, loaded state", xp.known(Skill.RANGED));
		assertEquals(0, xp.get(Skill.RANGED));
	}

	@Test
	public void acceptsExperienceExactlyOnTheThreshold()
	{
		SkillXp xp = SkillXp.of(map(Skill.ATTACK, 2), map(Skill.ATTACK, 83));
		assertTrue(xp.known(Skill.ATTACK));
	}

	/**
	 * The cost is measured from where the account actually is, not from its
	 * level's threshold. This is the whole point: 90% of the way through a
	 * level should read as nearly free, not as a full level's grind.
	 */
	@Test
	public void toLevelMeasuresFromRealExperience()
	{
		// Level 50 is 101,333 and level 51 is 111,945.
		SkillXp atThreshold = SkillXp.of(map(Skill.MAGIC, 50), map(Skill.MAGIC, 101_333));
		assertEquals(111_945 - 101_333, atThreshold.toLevel(Skill.MAGIC, 51));

		SkillXp nearlyThere = SkillXp.of(map(Skill.MAGIC, 50), map(Skill.MAGIC, 111_000));
		assertEquals(945, nearlyThere.toLevel(Skill.MAGIC, 51));
	}

	@Test
	public void toLevelIsZeroForALevelAlreadyReached()
	{
		SkillXp xp = SkillXp.of(map(Skill.STRENGTH, 83), map(Skill.STRENGTH, 2_800_000));
		assertEquals("already past it costs nothing, which is not the same as unknown",
			0, xp.toLevel(Skill.STRENGTH, 50));
	}

	@Test
	public void skillsMissingFromTheReadingStayUnknown()
	{
		SkillXp xp = SkillXp.of(map(Skill.STRENGTH, 83), map(Skill.STRENGTH, 2_800_000));
		assertFalse(xp.known(Skill.MAGIC));
		assertEquals(-1, xp.toLevel(Skill.MAGIC, 99));
	}

	/**
	 * The signature moves when the experience does and holds still when it
	 * does not.
	 *
	 * The Combat tab skips rebuilding itself on an unchanged signature, so a
	 * constant one would freeze the panel and a churning one would undo the
	 * saving entirely -- roughly thirty Swing components thrown away and
	 * remade every game tick.
	 */
	@Test
	public void theSignatureTracksTheExperienceItSummarises()
	{
		java.util.Map<Skill, Integer> levels = new java.util.EnumMap<>(Skill.class);
		levels.put(Skill.ATTACK, 50);
		java.util.Map<Skill, Integer> xp = new java.util.EnumMap<>(Skill.class);
		xp.put(Skill.ATTACK, 101_333);

		SkillXp first = SkillXp.of(levels, xp);
		SkillXp same = SkillXp.of(levels, xp);
		assertEquals(first.signature(), same.signature());

		java.util.Map<Skill, Integer> moved = new java.util.EnumMap<>(xp);
		moved.put(Skill.ATTACK, 120_000);
		assertNotEquals(first.signature(), SkillXp.of(levels, moved).signature());
	}
}
