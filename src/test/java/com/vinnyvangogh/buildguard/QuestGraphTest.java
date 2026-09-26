package com.vinnyvangogh.buildguard;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.StringReader;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The wire contract for the part of the bundled graph this plugin reads, and
 * the version gate that keeps a shape it does not understand from being parsed
 * as if it did.
 *
 * <p>Narrower than questpath's own {@code QuestGraphTest} on purpose, and in
 * exactly one direction: {@link QuestGraph} here models quests, requirements,
 * rewards, prerequisites and boosts, and nothing else. The shops, the item
 * catalogue, the diary tasks and the quest start coordinates are all still in
 * the bundle and all still ignored, so there is nothing here to assert about
 * them. What is <em>not</em> narrower is {@link QuestGraph#parse}: a trimmed
 * model is a model that depends on fewer fields, never one that may be fed a
 * document of unknown shape.
 */
public class QuestGraphTest
{
	/**
	 * Constructed rather than injected, which is allowed here and nowhere else.
	 * There is no Guice injector in a unit test, and the production path never
	 * builds one — {@link QuestGraph#load(Gson)} takes the client's injected
	 * Gson as a parameter. See {@code AGENTS.md}.
	 */
	private static final Gson GSON = new Gson();

	// ------------------------------------------------------------------
	// The model, in the shape the classifier reads it.

	@Test
	public void parsesAQuestsRequirementsAndRewards()
	{
		QuestGraph graph = GSON.fromJson("{\"version\":12,\"quests\":[{\"id\":7,\"slug\":\"dragon-slayer\","
			+ "\"name\":\"Dragon Slayer I\","
			+ "\"requirements\":[{\"skill\":\"quest points\",\"level\":32,\"boostable\":false},"
			+ "{\"skill\":\"crafting\",\"level\":8,\"boostable\":true}],"
			+ "\"rewards\":[{\"skill\":\"strength\",\"xp\":18650},{\"skill\":\"defence\",\"xp\":18650}]}]}",
			QuestGraph.class);

		QuestGraph.Quest quest = graph.quests().get(0);
		assertEquals(7L, quest.id());
		assertEquals("dragon-slayer", quest.slug());
		assertEquals("Dragon Slayer I", quest.name());

		assertEquals(2, quest.requirements().size());
		assertEquals("crafting", quest.requirements().get(1).skill());
		assertEquals(8, quest.requirements().get(1).level());
		assertTrue(quest.requirements().get(1).boostable());
		assertFalse(quest.requirements().get(0).boostable());

		assertEquals(2, quest.rewards().size());
		assertEquals("defence", quest.rewards().get(1).skill());
		assertEquals(18650d, quest.rewards().get(1).xp(), 0.0001);
	}

	/**
	 * A quest with none of the optional lists parses to empty ones, never null.
	 *
	 * The classifier iterates all four unconditionally, so a null here is a
	 * crash on the first quest that happens to grant nothing.
	 */
	@Test
	public void aQuestWithNoListsParsesToEmptyOnesNotNull()
	{
		QuestGraph graph = GSON.fromJson(
			"{\"version\":12,\"quests\":[{\"id\":1,\"slug\":\"a\",\"name\":\"A\"}]}", QuestGraph.class);

		QuestGraph.Quest quest = graph.quests().get(0);
		assertTrue(quest.requirements().isEmpty());
		assertTrue(quest.rewards().isEmpty());
		assertTrue(quest.prerequisites().isEmpty());
		assertFalse(quest.requiresStartedOnly(2L));
	}

	/**
	 * The started-only distinction version 6 introduced, and the one the port
	 * must apply identically to the Go planner or the vectors diverge.
	 *
	 * <p>Curse of the Empty Lord asks that Desert Treasure I be <em>begun</em>.
	 * A graph that could not express that demanded completion of a quest that
	 * only has to be started, and blocked something the account can genuinely
	 * do.
	 */
	@Test
	public void aStartedOnlyPrerequisiteIsDistinguishedFromAFinishedOne()
	{
		QuestGraph graph = GSON.fromJson("{\"version\":12,\"quests\":[{\"id\":10,\"slug\":\"a\",\"name\":\"A\","
			+ "\"prerequisites\":[20,30],\"prerequisites_started_only\":[20]}]}", QuestGraph.class);

		QuestGraph.Quest quest = graph.quests().get(0);
		assertEquals(2, quest.prerequisites().size());
		assertTrue("20 need only be started", quest.requiresStartedOnly(20L));
		assertFalse("30 must be finished", quest.requiresStartedOnly(30L));
	}

	/**
	 * The boost table version 5 introduced: the magnitude the {@code boostable}
	 * flag never carried.
	 *
	 * Without it a fifty-level shortfall and a one-level shortfall are
	 * indistinguishable, and both came back BOOSTABLE.
	 */
	@Test
	public void parsesTheBoostTableIncludingWhetherABoostCanDrain()
	{
		QuestGraph graph = GSON.fromJson("{\"version\":12,\"quests\":[],\"boosts\":["
			+ "{\"skill\":\"attack\",\"item\":\"Attack potion\",\"base\":3,\"per_level\":0.1,\"max\":12},"
			+ "{\"skill\":\"cooking\",\"item\":\"Spicy stew\",\"base\":0,\"max\":6,\"random\":true}]}",
			QuestGraph.class);

		assertEquals(2, graph.boosts().size());
		QuestGraph.Boost potion = graph.boosts().get(0);
		assertEquals("attack", potion.skill());
		assertEquals("Attack potion", potion.item());
		assertEquals(3, potion.base());
		assertEquals(0.1, potion.perLevel(), 0.0001);
		assertEquals(12, potion.max());
		assertFalse(potion.random());

		assertTrue("a stew drains as readily as it boosts", graph.boosts().get(1).random());
	}

	/** A graph with no boosts key parses to an empty list, never null. */
	@Test
	public void aGraphWithNoBoostsKeyParsesToAnEmptyList()
	{
		QuestGraph graph = GSON.fromJson("{\"version\":12,\"quests\":[]}", QuestGraph.class);

		assertTrue(graph.boosts().isEmpty());
	}

	// ------------------------------------------------------------------
	// The two lookups. Ids are questpath's rowids; names are what the client
	// speaks. Both indexes are built lazily, so both are worth exercising.

	@Test
	public void resolvesAQuestByIdAndByExactName()
	{
		QuestGraph graph = GSON.fromJson("{\"version\":12,\"quests\":["
			+ "{\"id\":1,\"slug\":\"cooks-assistant\",\"name\":\"Cook's Assistant\"},"
			+ "{\"id\":2,\"slug\":\"sheep-shearer\",\"name\":\"Sheep Shearer\"}]}", QuestGraph.class);

		assertEquals("Sheep Shearer", graph.get(2L).name());
		assertEquals(1L, graph.byName("Cook's Assistant").id());
	}

	/**
	 * An id or a name the graph does not carry resolves to null, never to a
	 * fabricated quest.
	 *
	 * Null is what {@link QuestClassifier} reads as "this bundle has never heard
	 * of it", which is a stale bundle rather than an eligible quest. The two must
	 * not render the same.
	 */
	@Test
	public void anUnknownIdOrNameResolvesToNull()
	{
		QuestGraph graph = GSON.fromJson(
			"{\"version\":12,\"quests\":[{\"id\":1,\"slug\":\"a\",\"name\":\"A\"}]}", QuestGraph.class);

		assertNull(graph.get(999L));
		assertNull(graph.byName("Nobody's Quest"));
	}

	// ------------------------------------------------------------------
	// parse() -- the version gate. This is the load-bearing half of the file.

	/**
	 * Pins the bump. A graph shape change must raise the version, and this
	 * constant is what {@link QuestGraph#parse} refuses a mismatch against. The
	 * two bumps this plugin genuinely depends on are 5 (the boost table) and 6
	 * (prerequisites_started_only); the rest added fields it does not read.
	 */
	@Test
	public void theSupportedVersionIsThePinnedBundleShape()
	{
		assertEquals(12, QuestGraph.SUPPORTED_VERSION);
	}

	/** A well-formed, correctly-versioned document parses cleanly. */
	@Test
	public void parseAcceptsAWellFormedGraph() throws IOException
	{
		QuestGraph graph = QuestGraph.parse(GSON, new StringReader(
			"{\"version\":12,\"quests\":[{\"id\":1,\"slug\":\"a\",\"name\":\"A\"}]}"));

		assertEquals(1, graph.quests().size());
	}

	/**
	 * A graph newer than this build understands is refused, not parsed
	 * leniently. The message says which direction the mismatch is in, because
	 * that is the difference between "update the plugin" and "re-export the
	 * bundle".
	 */
	@Test
	public void parseRefusesAVersionNewerThanThisBuildUnderstands()
	{
		try
		{
			QuestGraph.parse(GSON, new StringReader(
				"{\"version\":99,\"quests\":[{\"id\":1,\"slug\":\"a\",\"name\":\"A\"}]}"));
			fail("expected an IOException for a too-new version");
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage().contains("99"));
			assertTrue(e.getMessage().toLowerCase().contains("update the plugin"));
		}
	}

	/**
	 * A graph older than this build expects is refused just the same.
	 *
	 * <p>This is the direction that matters, and it is the reason the gate is
	 * not merely a {@code >=} check. An older bundle is missing fields this
	 * class reads, and a missing field parses into an absence rather than an
	 * error: no boost table reads as "no shortfall is coverable", no
	 * started-only list reads as "every prerequisite must be finished". Both are
	 * confident, wrong answers about whether an account may spend experience.
	 */
	@Test
	public void parseRefusesAVersionOlderThanThisBuildExpects()
	{
		try
		{
			QuestGraph.parse(GSON, new StringReader(
				"{\"version\":8,\"quests\":[{\"id\":1,\"slug\":\"a\",\"name\":\"A\"}]}"));
			fail("expected an IOException for a too-old version");
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage().contains("8"));
			assertTrue(e.getMessage().toLowerCase().contains("older"));
		}
	}

	/** A document with no quests at all is refused, the same as a missing bundle. */
	@Test
	public void parseRefusesAGraphWithNoQuests()
	{
		try
		{
			QuestGraph.parse(GSON, new StringReader("{\"version\":12,\"quests\":[]}"));
			fail("expected an IOException for an empty graph");
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage().contains("no quests"));
		}
	}

	/** Malformed JSON is refused with a message, never a null pointer downstream. */
	@Test
	public void parseRefusesMalformedJson()
	{
		try
		{
			QuestGraph.parse(GSON, new StringReader("{not json"));
			fail("expected an IOException for malformed JSON");
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage().contains("did not parse"));
		}
	}

	// ------------------------------------------------------------------
	// The bundle itself.

	/**
	 * The bundled resource loads, passes the gate, and carries the quests and
	 * boosts the engine needs.
	 *
	 * <p>{@code PlannerVectorsTest} would fail if this did, but not with a
	 * message that names the cause: a missing or unreadable jar resource shows
	 * up there as 218 disagreeing verdicts. This says which it was.
	 */
	@Test
	public void theBundledGraphLoadsFromTheJar() throws IOException
	{
		QuestGraph graph = QuestGraph.load(GSON);

		assertEquals(QuestGraph.SUPPORTED_VERSION, graph.version());
		assertEquals("the bundle carries every quest questpath knows", 218, graph.quests().size());
		assertFalse("without boosts nothing is ever BOOSTABLE", graph.boosts().isEmpty());
	}
}
