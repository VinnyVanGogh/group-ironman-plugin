package com.vinnyvangogh.buildguard;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The lamp advisor port against the original.
 *
 * <p>questpath's {@code -export-lamp-vectors} runs its Go advisor over a set
 * of hand-built cases — one per rule — and writes a verdict for every skill on
 * every one. This runs the Java port over the same inputs and demands the same
 * answers.
 *
 * <p>The same job {@link PlannerVectorsTest} does, and the stakes are higher
 * rather than lower. A quest verdict that disagrees can be noticed and
 * corrected; a lamp verdict that disagrees is noticed after the experience has
 * already been spent somewhere it cannot be taken back from.
 *
 * <p>They cannot be regenerated from this repository: the generator is
 * questpath's Go {@code -export-lamp-vectors}, so the round trip is to export
 * there and copy both files here unchanged.
 *
 * <pre>
 * # in the questpath repo
 * questpath -export-lamps        src/main/resources/com/vinnyvangogh/questpath/lamps.json
 * questpath -export-lamp-vectors src/test/resources/com/vinnyvangogh/questpath/lamp-vectors.json
 * # then copy both here, unchanged, to
 * #   src/main/resources/com/vinnyvangogh/buildguard/lamps.json
 * #   src/test/resources/com/vinnyvangogh/buildguard/lamp-vectors.json
 * </pre>
 *
 * <p><b>A failing vector is a porting bug, not a vector to edit.</b> If this
 * file and the Go advisor disagree, one of them is wrong about an irreversible
 * choice a player is about to make, and editing the expectation to match the
 * port hides exactly the thing the vectors exist to surface.
 */
public class LampVectorsTest
{
	private static final Gson GSON = new Gson();

	/**
	 * The skills the advisor is asked about, in questpath's own hiscores
	 * order — which is the order the Go side emits its expectations in, so
	 * the two lists line up index for index.
	 */
	private static final List<String> SKILLS = List.of(
		"attack", "defence", "strength", "hitpoints", "ranged", "prayer", "magic",
		"cooking", "woodcutting", "fletching", "fishing", "firemaking", "crafting",
		"smithing", "mining", "herblore", "agility", "thieving", "slayer", "farming",
		"runecraft", "hunter", "construction", "sailing");

	private Vectors vectors;

	static class Vectors
	{
		@SerializedName("version")
		int version;
		@SerializedName("cases")
		List<Case> cases;
	}

	static class Case
	{
		@SerializedName("name")
		String name;
		@SerializedName("account")
		Account account;
		@SerializedName("lamp")
		Lamp lamp;
		@SerializedName("inputs")
		InputSpec inputs;
		@SerializedName("expected")
		List<Expectation> expected;
	}

	static class Account
	{
		@SerializedName("skills")
		List<SkillState> skills;
		@SerializedName("rules")
		List<BuildRule> rules;
	}

	static class SkillState
	{
		@SerializedName("skill")
		String skill;
		@SerializedName("level")
		int level;
		@SerializedName("xp")
		long xp;
	}

	static class InputSpec
	{
		@SerializedName("route_needs")
		Map<String, Integer> routeNeeds;
		@SerializedName("free_ceiling")
		int freeCeiling;
		@SerializedName("has_ceiling")
		boolean hasCeiling;
		@SerializedName("rates")
		Map<String, Double> rates;
	}

	static class Expectation
	{
		@SerializedName("skill")
		String skill;
		@SerializedName("state")
		String state;
		@SerializedName("block")
		String block;
		@SerializedName("risk")
		String risk;
		@SerializedName("rank")
		int rank;
		@SerializedName("xp")
		double xp;
		@SerializedName("xp_known")
		boolean xpKnown;
	}

	@Before
	public void load() throws Exception
	{
		try (InputStream stream = LampVectorsTest.class.getResourceAsStream("lamp-vectors.json"))
		{
			assertNotNull("lamp-vectors.json is missing; regenerate it with -export-lamp-vectors", stream);
			vectors = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Vectors.class);
		}
		assertNotNull(vectors);
		assertNotNull("the vector file carries no cases", vectors.cases);
		assertFalse("the vector file carries no cases", vectors.cases.isEmpty());
	}

	/**
	 * The file is the whole file.
	 *
	 * <p>Every other assertion here loops over whatever {@code cases} happens to
	 * contain, so a truncated or half-copied vector file passes them all while
	 * checking a fraction of the advisor. {@code PlannerVectorsTest} is pinned
	 * against the graph's own quest count and needs no equivalent; this one has
	 * nothing to cross-check against, so the count is written down.
	 *
	 * <p>Nine cases, one per rule the advisor applies, each classifying all
	 * twenty-four skills: 216 verdicts.
	 */
	@Test
	public void theVectorFileIsCompleteAndNotTruncated()
	{
		assertEquals("lamp-vectors.json should carry 9 cases; re-copy it from questpath",
			9, vectors.cases.size());
		for (Case testCase : vectors.cases)
		{
			assertEquals(testCase.name + ": expects a verdict for every skill",
				SKILLS.size(), testCase.expected.size());
		}
	}

	/**
	 * Every case, every skill, every field the vectors pin.
	 *
	 * <p>The reason prose is deliberately not pinned, for the same reason the
	 * planner vectors pin a status and not a reason chain: the wording is
	 * written for a web page, and pinning it would make every rewording a test
	 * failure in another repository. The state is the answer.
	 */
	@Test
	public void everyCaseAgreesWithTheGoAdvisor()
	{
		for (Case testCase : vectors.cases)
		{
			List<LampVerdict> got = LampAdvisor.advise(
				testCase.lamp, accountFor(testCase), SKILLS, inputsFor(testCase));

			assertEquals(testCase.name + ": verdict count",
				testCase.expected.size(), got.size());

			for (int i = 0; i < got.size(); i++)
			{
				Expectation want = testCase.expected.get(i);
				LampVerdict actual = got.get(i);
				String where = testCase.name + " / " + want.skill;

				assertEquals(where + ": skill order", want.skill, actual.skill());
				assertEquals(where + ": state", want.state, actual.state().name());
				assertEquals(where + ": block reason",
					want.block == null ? "NONE" : want.block, actual.block().name());
				assertEquals(where + ": risk reason",
					want.risk == null ? "NONE" : want.risk, actual.risk().name());
				assertEquals(where + ": rank", want.rank, actual.rank());
				assertEquals(where + ": xp known", want.xpKnown, actual.xpKnown());
				if (want.xpKnown)
				{
					assertEquals(where + ": xp", want.xp, actual.xp(), 0.0001);
				}
			}
		}
	}

	/**
	 * The rule that costs the most to break, asserted on its own rather than
	 * only as part of the sweep above.
	 *
	 * <p>An unmodelled lamp must still grey every NO_XP skill — that rule does
	 * not depend on the amount — and must never rank anything. A ranking
	 * produced from a number nobody has is a guess wearing the clothes of
	 * advice, and the player cannot take the choice back.
	 */
	@Test
	public void anUnmodelledLampBlocksNoXpAndRanksNothing()
	{
		int checked = 0;
		for (Case testCase : vectors.cases)
		{
			if (testCase.lamp.modelled())
			{
				continue;
			}
			checked++;
			List<LampVerdict> got = LampAdvisor.advise(
				testCase.lamp, accountFor(testCase), SKILLS, inputsFor(testCase));

			int greyedForNoXp = 0;
			for (LampVerdict v : got)
			{
				assertEquals(testCase.name + ": " + v.skill() + " ranked from an unpriced lamp",
					0, v.rank());
				assertFalse(testCase.name + ": claims to know an unmodelled lamp's xp", v.xpKnown());
				if (v.block() == LampVerdict.Block.NO_XP)
				{
					greyedForNoXp++;
				}
			}
			assertTrue(testCase.name + ": a NO_XP skill must still be greyed", greyedForNoXp > 0);
		}
		assertTrue("no case exercises an unmodelled lamp", checked > 0);
	}

	/**
	 * Ranks must be a dense 1..n with no gaps and no repeats, and must land
	 * only on OK skills.
	 *
	 * <p>The overlay draws "the best" and "the next two" by rank, so a gap in
	 * the sequence silently drops a suggestion off the interface — a failure
	 * that looks exactly like the advisor having no opinion.
	 */
	@Test
	public void ranksAreDenseAndOnlyOnEligibleSkills()
	{
		for (Case testCase : vectors.cases)
		{
			List<LampVerdict> got = LampAdvisor.advise(
				testCase.lamp, accountFor(testCase), SKILLS, inputsFor(testCase));

			Set<Integer> seen = new HashSet<>();
			for (LampVerdict v : got)
			{
				if (v.rank() == 0)
				{
					continue;
				}
				assertEquals(testCase.name + ": " + v.skill() + " is ranked but not OK",
					LampVerdict.State.OK, v.state());
				assertTrue(testCase.name + ": rank " + v.rank() + " used twice", seen.add(v.rank()));
			}
			for (int rank = 1; rank <= seen.size(); rank++)
			{
				assertTrue(testCase.name + ": rank " + rank + " missing from a dense sequence",
					seen.contains(rank));
			}
		}
	}

	/**
	 * The bundled catalogue has to parse and has to carry the lamps the
	 * vectors name by item id, or the advisor is correct about lamps the
	 * plugin can never actually identify.
	 */
	@Test
	public void theBundledCatalogueCarriesTheLampsItClaimsTo() throws Exception
	{
		LampCatalogue catalogue = LampCatalogue.load(GSON);
		assertEquals(LampCatalogue.SUPPORTED_VERSION, catalogue.version());
		assertFalse(catalogue.lamps().isEmpty());

		// The genie lamp and the Book of Knowledge, whose multipliers are the
		// pair a port can silently swap.
		Lamp genie = catalogue.forItem(2528, "Lamp");
		assertTrue("the genie lamp is missing from the bundle", genie.modelled());
		assertEquals("genie lamp multiplier", 10, genie.xpPerLevel());
		assertEquals("the wiki's own worked example: level 21 gives 210", 210d, genie.xpAt(21), 0.0001);

		Lamp book = catalogue.forItem(11640, "Book of Knowledge");
		assertTrue("the book of knowledge is missing from the bundle", book.modelled());
		assertEquals("book multiplier", 15, book.xpPerLevel());
		assertEquals("50% more than the lamp at the same level", 315d, book.xpAt(21), 0.0001);
	}

	/**
	 * An item the catalogue has never heard of comes back as an unknown lamp,
	 * never as null and never as a zero-experience one.
	 */
	@Test
	public void anUncataloguedItemIsUnknownRatherThanEmpty() throws Exception
	{
		LampCatalogue catalogue = LampCatalogue.load(GSON);
		Lamp lamp = catalogue.forItem(Integer.MAX_VALUE, "something new");

		assertNotNull(lamp);
		assertFalse(lamp.modelled());
		assertEquals("amount unknown", lamp.describeAmount());
		assertEquals("an unknown lamp must not claim to be worth zero", -1d, lamp.xpAt(50), 0.0001);
	}

	private PlayerState accountFor(Case testCase)
	{
		Map<String, Integer> levels = new HashMap<>();
		Map<String, Long> experience = new HashMap<>();

		// Every skill is seeded first, so a skill the case does not name is
		// defaulted the same way the Go side defaults it rather than being
		// absent here and present there.
		for (String skill : SKILLS)
		{
			levels.put(skill, "hitpoints".equals(skill) ? 10 : 1);
			experience.put(skill, 0L);
		}
		if (testCase.account.skills != null)
		{
			for (SkillState skill : testCase.account.skills)
			{
				levels.put(skill.skill, skill.level);
				experience.put(skill.skill, skill.xp);
			}
		}

		List<BuildRule> rules = testCase.account.rules == null
			? Collections.emptyList() : testCase.account.rules;
		return new PlayerState(levels, experience, new HashSet<>(), rules);
	}

	private LampAdvisor.Inputs inputsFor(Case testCase)
	{
		LampAdvisor.Inputs inputs = new LampAdvisor.Inputs();
		if (testCase.inputs == null)
		{
			return inputs;
		}
		if (testCase.inputs.routeNeeds != null)
		{
			inputs.routeNeeds(new HashMap<>(testCase.inputs.routeNeeds));
		}
		if (testCase.inputs.rates != null)
		{
			inputs.rates(new HashMap<>(testCase.inputs.rates));
		}
		// Only when the vectors say a ceiling was supplied. Applying a zero
		// ceiling because the field defaulted would flag every Ranged level
		// in every other case.
		if (testCase.inputs.hasCeiling)
		{
			inputs.freeCeiling(testCase.inputs.freeCeiling);
		}
		return inputs;
	}
}
