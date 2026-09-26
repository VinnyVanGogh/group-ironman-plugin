package com.vinnyvangogh.buildguard;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
 * The port against the original.
 *
 * <p>questpath's {@code -export-planner-vectors} runs its Go planner over the
 * real account and writes a verdict for all 218 quests. This runs the Java
 * port over the same account and the same graph, and demands the same answers.
 *
 * <p>This is the only thing standing between "the two agree" and "the two
 * appear to agree". Both implementations answer confidently, so a disagreement
 * is invisible until the game and the plugin tell you different things about
 * the same quest.
 *
 * <p><b>A failing vector is a bug in this port, never a vector to edit.</b>
 * They cannot be regenerated from this repository in any case — the generator
 * is questpath's Go {@code -export-planner-vectors}, so the round trip is:
 *
 * <pre>
 * # in the questpath repo
 * questpath -export-quest-graph      src/main/resources/com/vinnyvangogh/questpath/quests.json
 * questpath -export-planner-vectors  src/test/resources/com/vinnyvangogh/questpath/planner-vectors.json
 * # then copy both here, unchanged, to
 * #   src/main/resources/com/vinnyvangogh/buildguard/quests.json
 * #   src/test/resources/com/vinnyvangogh/buildguard/planner-vectors.json
 * </pre>
 *
 * <p>Editing an expectation to match the port writes the bug down as the
 * expected result, which is precisely what these files exist to notice.
 */
public class PlannerVectorsTest
{
	private static final Gson GSON = new Gson();

	private QuestGraph graph;
	private Vectors vectors;

	static class Vectors
	{
		@SerializedName("version")
		int version;
		@SerializedName("account")
		Account account;
		@SerializedName("expected")
		List<Verdict> expected;
	}

	static class Account
	{
		@SerializedName("skills")
		List<SkillState> skills;
		@SerializedName("completed")
		List<Long> completed;
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

	static class Verdict
	{
		@SerializedName("quest_id")
		long questId;
		@SerializedName("slug")
		String slug;
		@SerializedName("status")
		String status;
	}

	@Before
	public void load() throws Exception
	{
		graph = QuestGraph.load(GSON);

		try (InputStream stream = PlannerVectorsTest.class.getResourceAsStream("planner-vectors.json"))
		{
			assertNotNull("planner-vectors.json is missing; regenerate it with -export-planner-vectors", stream);
			vectors = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Vectors.class);
		}
		assertNotNull(vectors);
	}

	private PlayerState accountUnderTest()
	{
		Map<String, Integer> levels = new HashMap<>();
		Map<String, Long> experience = new HashMap<>();
		for (SkillState skill : vectors.account.skills)
		{
			levels.put(skill.skill, skill.level);
			experience.put(skill.skill, skill.xp);
		}
		// The vectors carry questpath's own quest ids; the engine matches by
		// name, because the client's ids are the game's and comparing the two
		// spaces is the bug this indirection exists to prevent. Resolving
		// through the graph is what the plugin does with the client's names.
		Set<String> completed = new HashSet<>();
		if (vectors.account.completed != null)
		{
			for (Long id : vectors.account.completed)
			{
				QuestGraph.Quest quest = graph.get(id);
				assertNotNull("the vectors mark quest " + id + " complete but the graph lacks it", quest);
				completed.add(quest.name());
			}
		}
		List<BuildRule> rules = vectors.account.rules == null ? Collections.emptyList() : vectors.account.rules;
		return new PlayerState(levels, experience, completed, rules);
	}

	/**
	 * Quests where this port is deliberately ahead of the Go planner.
	 *
	 * <p><b>Empty, and that is the point.</b> It briefly held ten quests --
	 * Lost City, Sea Slug, Tears of Guthix and others -- during the window
	 * where the port checked that a boost was large enough to close a
	 * shortfall and the Go planner still checked only that the wiki permitted
	 * one. Go now stores the boost table and applies the same test, so both
	 * agree again and every quest is back under the plain assertion.
	 *
	 * <p>Kept rather than deleted because the situation recurs: the port and
	 * the planner are two implementations of one thing, and a fix always
	 * lands in one of them first. An empty set with the rule written down
	 * beside it is a cheaper thing to reach for than the argument about
	 * whether to regenerate the vectors -- which is the wrong answer, since
	 * regenerating writes the bug down as the expected result and these
	 * vectors exist to be the thing that notices.
	 */
	private static final Set<String> KNOWN_AHEAD_OF_GO = Collections.emptySet();

	/**
	 * The whole point. Every quest, the same verdict.
	 *
	 * Failures are collected rather than thrown on the first one: a port that
	 * is wrong is usually wrong about a whole class of quest, and seeing three
	 * of them names the class where seeing one names a quest.
	 */
	@Test
	public void everyVerdictMatchesTheGoPlanner()
	{
		QuestClassifier classifier = new QuestClassifier(graph, accountUnderTest());

		List<String> mismatches = new ArrayList<>();
		for (Verdict expected : vectors.expected)
		{
			QuestStatus actual = classifier.classify(expected.questId);
			String actualName = actual == null ? "NOT_IN_GRAPH" : actual.name();
			if (!expected.status.equals(actualName) && !KNOWN_AHEAD_OF_GO.contains(expected.slug))
			{
				mismatches.add(expected.slug + ": expected " + expected.status + ", got " + actualName);
			}
		}

		assertEquals(mismatches.size() + " of " + vectors.expected.size()
			+ " verdicts disagree with the Go planner:\n  " + String.join("\n  ", mismatches),
			Collections.emptyList(), mismatches);
	}

	/**
	 * The exemptions above have to stay real.
	 *
	 * A slug that stops diverging is a slug someone fixed, and leaving it
	 * listed would quietly excuse a future regression on that quest. This
	 * fails when the list is stale, which is what makes it safe to have one.
	 */
	@Test
	public void everyKnownDivergenceStillDiverges()
	{
		QuestClassifier classifier = new QuestClassifier(graph, accountUnderTest());

		List<String> agreeing = new ArrayList<>();
		for (Verdict expected : vectors.expected)
		{
			if (!KNOWN_AHEAD_OF_GO.contains(expected.slug))
			{
				continue;
			}
			QuestStatus actual = classifier.classify(expected.questId);
			String actualName = actual == null ? "NOT_IN_GRAPH" : actual.name();
			if (expected.status.equals(actualName))
			{
				agreeing.add(expected.slug);
			}
		}

		assertEquals("these no longer diverge and must come off KNOWN_AHEAD_OF_GO: "
			+ String.join(", ", agreeing), Collections.emptyList(), agreeing);
	}

	/**
	 * The vectors must actually exercise the engine. A set that was all
	 * ELIGIBLE would pass a classifier that returned ELIGIBLE unconditionally,
	 * and the suite would be worthless while looking green.
	 */
	@Test
	public void theVectorsCoverEveryStatusTheEngineCanProduce()
	{
		Set<String> seen = new HashSet<>();
		for (Verdict verdict : vectors.expected)
		{
			seen.add(verdict.status);
		}

		for (String required : new String[]{
			"COMPLETE", "ELIGIBLE", "BOOSTABLE", "QUEST_BLOCKED",
			"SKILL_BLOCKED", "BUILD_BLOCKED", "UNREACHABLE"})
		{
			assertTrue("the vectors never exercise " + required
				+ "; regenerate them from an account that does", seen.contains(required));
		}
	}

	@Test
	public void theBundledGraphCoversEveryQuestTheVectorsJudge()
	{
		for (Verdict verdict : vectors.expected)
		{
			assertNotNull("the bundled graph is missing " + verdict.slug
				+ "; the two files were exported from different databases",
				graph.get(verdict.questId));
		}
		assertEquals("the graph and the vectors describe different quest sets",
			graph.quests().size(), vectors.expected.size());
	}

	/**
	 * The account the vectors were generated against must actually have rules.
	 * Without them nothing can be BUILD_BLOCKED, and the build guard -- the
	 * reason this whole engine exists -- would be untested while every other
	 * assertion still passed.
	 */
	@Test
	public void theAccountUnderTestActuallyHasBuildRules()
	{
		assertFalse("regenerate the vectors from an account with build rules",
			accountUnderTest().hasRules() == false);
	}
}
