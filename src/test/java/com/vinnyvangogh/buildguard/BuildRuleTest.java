package com.vinnyvangogh.buildguard;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The build rule model's wire contract, and the distinction the UI draws from
 * it.
 *
 * NO_XP and HARD_CAP end a build if crossed; SOFT_CAP and GOAL only inform.
 * Rendering them the same would turn "approaching a target" into "about to
 * ruin your account", and the second warning stops meaning anything once it
 * fires for the first.
 *
 * <p>Rules are parsed here as a bare JSON array, which is the only shape Build
 * Guard ever sees one in: it is what {@link BuildRuleCodec} writes to storage
 * and reads back, and it is what {@code account.rules} carries in both
 * generated vector files. questpath wrapped the same array in a push response
 * envelope, and that envelope is deliberately not ported -- there is no push.
 */
public class BuildRuleTest
{
	/**
	 * Constructed rather than injected, which is allowed in a test and nowhere
	 * else. There is no Guice injector here, and every production entry point
	 * takes an injected Gson as a parameter. See {@code AGENTS.md}.
	 */
	private static final Gson GSON = new Gson();

	/**
	 * An array rather than a {@code TypeToken<List<BuildRule>>}, matching {@link
	 * BuildRuleCodec} — see the note there for why the token's {@code
	 * java.lang.reflect.Type} import is avoided throughout this repo.
	 */
	private static List<BuildRule> parse(String jsonArray)
	{
		return Arrays.asList(GSON.fromJson(jsonArray, BuildRule[].class));
	}

	@Test
	public void parsesTheRuleFieldsTheEngineReads()
	{
		List<BuildRule> rules = parse("["
			+ "{\"skill\":\"defence\",\"type\":\"NO_XP\",\"blocking\":true,\"note\":\"1 def\"},"
			+ "{\"skill\":\"ranged\",\"type\":\"HARD_CAP\",\"value\":56,\"blocking\":true}]");

		assertEquals(2, rules.size());

		assertEquals("defence", rules.get(0).skillName());
		assertEquals(Skill.DEFENCE, rules.get(0).skill());
		assertTrue(rules.get(0).blocking());
		assertFalse("NO_XP carries no level", rules.get(0).hasValue());
		assertEquals("1 def", rules.get(0).note());

		assertEquals(56, rules.get(1).value());
		assertTrue(rules.get(1).hasValue());
	}

	/**
	 * The rule the port cannot afford to lose: blocking-ness is derived from the
	 * <em>type</em>, not read from the field.
	 *
	 * <p>Neither generated vector file carries a {@code blocking} key at all, so
	 * trusting the field alone makes every rule in them non-blocking. Measured
	 * against this repo's bundle on 2026-09-17: 99 of 218 verdicts then disagree
	 * with the Go planner, 13 of them coming back flatly ELIGIBLE for quests
	 * that end a pure. See {@link BuildRule#blocking()} for the full breakdown.
	 *
	 * <p>{@code PlannerVectorsTest} is what catches that, but only as 99 named
	 * quests. This is the same failure pinned as one sentence: rules with no
	 * flag, asserted blocking anyway.
	 */
	@Test
	public void aRuleWithNoBlockingFlagStillBlocksOnItsType()
	{
		List<BuildRule> rules = parse("["
			+ "{\"skill\":\"attack\",\"type\":\"NO_XP\"},"
			+ "{\"skill\":\"hitpoints\",\"type\":\"HARD_CAP\",\"value\":28},"
			+ "{\"skill\":\"prayer\",\"type\":\"SOFT_CAP\"},"
			+ "{\"skill\":\"cooking\",\"type\":\"GOAL\",\"value\":60}]");

		assertTrue("NO_XP ends a build", rules.get(0).blocking());
		assertTrue("HARD_CAP ends a build", rules.get(1).blocking());
		assertFalse("SOFT_CAP only informs", rules.get(2).blocking());
		assertFalse("GOAL only informs", rules.get(3).blocking());
	}

	/**
	 * A stored rule may name a skill this client build does not know -- one
	 * saved against a newer game. The rule is kept and shown under the name it
	 * carries rather than dropped: it might be the one ending the build.
	 */
	@Test
	public void anUnknownSkillResolvesToNullWithoutLosingTheRule()
	{
		BuildRule rule = parse("[{\"skill\":\"basketweaving\",\"type\":\"NO_XP\",\"blocking\":true}]").get(0);

		assertNull(rule.skill());
		assertEquals("basketweaving", rule.skillName());
		assertEquals("no xp at all", rule.describe());
	}

	/** A rule with no note or value reads as empty and zero, never null. */
	@Test
	public void absentFieldsReadAsEmptyRatherThanNull()
	{
		BuildRule rule = parse("[{\"skill\":\"defence\",\"type\":\"NO_XP\"}]").get(0);

		assertEquals("", rule.note());
		assertEquals(0, rule.value());
		assertFalse(rule.hasValue());
	}

	@Test
	public void describeSaysWhatTheLimitActuallyIs()
	{
		assertEquals("no xp at all", ruleOf("NO_XP", 0).describe());
		assertEquals("capped at 56", ruleOf("HARD_CAP", 56).describe());
		assertEquals("warns, never blocks", ruleOf("SOFT_CAP", 0).describe());
		assertEquals("goal 50", ruleOf("GOAL", 50).describe());
	}

	/**
	 * A type this build does not know is rendered plainly rather than hidden. A
	 * new rule kind must not make a limit vanish from the UI.
	 */
	@Test
	public void anUnknownRuleTypeIsShownNotSwallowed()
	{
		assertEquals("SOMETHING_NEW", ruleOf("SOMETHING_NEW", 0).describe());
	}

	/**
	 * Ordered by what ends a build first. A pure scanning this list wants the
	 * thing that would ruin the account at the top, not alphabetical order.
	 */
	@Test
	public void orderedPutsWhatEndsABuildFirst()
	{
		List<BuildRule> ordered = BuildRule.ordered(Arrays.asList(
			named("thieving", "GOAL", 50),
			named("prayer", "SOFT_CAP", 0),
			named("ranged", "HARD_CAP", 56),
			named("attack", "NO_XP", 0),
			named("defence", "NO_XP", 0)));

		assertEquals("attack", ordered.get(0).skillName());
		assertEquals("defence", ordered.get(1).skillName());
		assertEquals("ranged", ordered.get(2).skillName());
		assertEquals("prayer", ordered.get(3).skillName());
		assertEquals("thieving", ordered.get(4).skillName());
	}

	private static BuildRule ruleOf(String type, int value)
	{
		return named("defence", type, value);
	}

	private static BuildRule named(String skill, String type, int value)
	{
		String json = "[{\"skill\":\"" + skill + "\",\"type\":\"" + type + "\""
			+ (value > 0 ? ",\"value\":" + value : "") + "}]";
		return parse(json).get(0);
	}
}
