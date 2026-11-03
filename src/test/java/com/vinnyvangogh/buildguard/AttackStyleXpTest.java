package com.vinnyvangogh.buildguard;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Which skills the selected attack style trains, and whether that ends a
 * member's build.
 *
 * <p>The style names are the strings the game's weapon-style structs carry
 * (param {@code ATTACK_STYLE_NAME}), so the tests feed exactly those.
 */
public class AttackStyleXpTest
{
	/** A scimitar's four styles, as the game lists them. */
	private static final List<String> SCIMITAR = Arrays.asList("Accurate", "Aggressive", "Controlled", "Defensive");

	/** A staff: three melee styles, an empty slot, then the two casting slots. */
	private static final List<String> STAFF = Arrays.asList("Accurate", "Aggressive", "Defensive", "Other", "Casting", "Defensive");

	private static final List<BuildRule> ONE_DEF = Collections.singletonList(
		BuildRule.local("Defence", BuildRule.HARD_CAP, 1, ""));

	private static Map<Skill, Integer> levels(int def)
	{
		Map<Skill, Integer> out = new EnumMap<>(Skill.class);
		out.put(Skill.DEFENCE, def);
		return out;
	}

	@Test
	public void controlledTrainsDefenceAndEndsAOneDefencePure()
	{
		Set<Skill> trained = AttackStyleXp.skillsTrained(AttackStyleXp.resolveStyles(SCIMITAR), 2, 0);
		assertEquals(EnumSet.of(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE), trained);

		List<BuildRule> risk = AttackStyleXp.rulesAtRisk(trained, ONE_DEF, levels(1));
		assertEquals(1, risk.size());
		assertEquals("Defence", risk.get(0).skillName());
	}

	@Test
	public void defensiveStyleIsAtRiskForAOneDefencePure()
	{
		Set<Skill> trained = AttackStyleXp.skillsTrained(AttackStyleXp.resolveStyles(SCIMITAR), 3, 0);
		assertEquals(EnumSet.of(Skill.DEFENCE), trained);
		assertEquals(1, AttackStyleXp.rulesAtRisk(trained, ONE_DEF, levels(1)).size());
	}

	@Test
	public void accurateAndAggressiveAreSafeForAOneDefencePure()
	{
		List<String> styles = AttackStyleXp.resolveStyles(SCIMITAR);
		for (int com : new int[]{0, 1})
		{
			Set<Skill> trained = AttackStyleXp.skillsTrained(styles, com, 0);
			assertTrue("style " + com + " trained " + trained,
				AttackStyleXp.rulesAtRisk(trained, ONE_DEF, levels(1)).isEmpty());
		}
	}

	/**
	 * The defensive-casting toggle shares slot 4 with plain casting; the game
	 * says which by a separate varbit, and the fifth listed style is renamed.
	 */
	@Test
	public void defensiveCastingTrainsDefenceButPlainCastingDoesNot()
	{
		List<String> styles = AttackStyleXp.resolveStyles(STAFF);

		Set<Skill> casting = AttackStyleXp.skillsTrained(styles, 4, 0);
		assertEquals(EnumSet.of(Skill.MAGIC), casting);
		assertTrue(AttackStyleXp.rulesAtRisk(casting, ONE_DEF, levels(1)).isEmpty());

		Set<Skill> defCasting = AttackStyleXp.skillsTrained(styles, 4, 1);
		assertEquals(EnumSet.of(Skill.MAGIC, Skill.DEFENCE), defCasting);
		assertEquals(1, AttackStyleXp.rulesAtRisk(defCasting, ONE_DEF, levels(1)).size());
	}

	@Test
	public void longrangeTrainsDefence()
	{
		// The struct names the ranged styles by what they train, not by the
		// button label: Accurate and Rapid are both "Ranging".
		List<String> bow = AttackStyleXp.resolveStyles(Arrays.asList("Ranging", "Ranging", "Longrange"));
		assertEquals(EnumSet.of(Skill.RANGED, Skill.DEFENCE), AttackStyleXp.skillsTrained(bow, 2, 0));
		assertEquals(EnumSet.of(Skill.RANGED), AttackStyleXp.skillsTrained(bow, 1, 0));
	}

	/** An index past the weapon's list, or a style name the game adds later, trains nothing we know of. */
	@Test
	public void unknownStyleOrIndexIsNoSkillsRatherThanACrash()
	{
		assertTrue(AttackStyleXp.skillsTrained(AttackStyleXp.resolveStyles(SCIMITAR), 9, 0).isEmpty());
		assertTrue(AttackStyleXp.skillsTrained(AttackStyleXp.resolveStyles(Arrays.asList("Brand new")), 0, 0).isEmpty());
		assertTrue(AttackStyleXp.skillsTrained(Collections.emptyList(), 0, 0).isEmpty());
	}

	/** A 60-Attack cap is a target to train toward until the level is reached. */
	@Test
	public void hardCapIsOnlyAtRiskOnceTheCapIsReached()
	{
		List<BuildRule> sixtyAtt = Collections.singletonList(BuildRule.local("Attack", BuildRule.HARD_CAP, 60, ""));
		Set<Skill> accurate = EnumSet.of(Skill.ATTACK);

		Map<Skill, Integer> lv = new EnumMap<>(Skill.class);
		lv.put(Skill.ATTACK, 40);
		assertTrue(AttackStyleXp.rulesAtRisk(accurate, sixtyAtt, lv).isEmpty());

		lv.put(Skill.ATTACK, 60);
		assertEquals(1, AttackStyleXp.rulesAtRisk(accurate, sixtyAtt, lv).size());
	}

	/** Not knowing the level must not read as safe: a wrong "safe" costs XP that never comes back. */
	@Test
	public void unknownLevelIsTreatedAsAtRisk()
	{
		assertEquals(1, AttackStyleXp.rulesAtRisk(EnumSet.of(Skill.DEFENCE), ONE_DEF, Collections.emptyMap()).size());
	}

	@Test
	public void noXpIsAlwaysAtRiskAndSoftRulesNeverAre()
	{
		List<BuildRule> rules = Arrays.asList(
			BuildRule.local("Prayer", BuildRule.NO_XP, 0, ""),
			BuildRule.local("Strength", BuildRule.SOFT_CAP, 0, ""),
			BuildRule.local("Attack", BuildRule.GOAL, 99, ""));
		Map<Skill, Integer> lv = new EnumMap<>(Skill.class);
		lv.put(Skill.PRAYER, 1);
		lv.put(Skill.STRENGTH, 99);
		lv.put(Skill.ATTACK, 99);

		List<BuildRule> risk = AttackStyleXp.rulesAtRisk(
			EnumSet.of(Skill.PRAYER, Skill.STRENGTH, Skill.ATTACK), rules, lv);
		assertEquals(1, risk.size());
		assertEquals("Prayer", risk.get(0).skillName());
	}
}
