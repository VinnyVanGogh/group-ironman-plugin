package com.vinnyvangogh.buildguard;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Whether a shortfall can actually be drunk away, over the real bundle.
 *
 * <p>The bug these pin: the quest graph's {@code boostable} flag is a bare yes
 * from the wiki. It permits a boost and says nothing about size, so every
 * shortfall on a boostable requirement was reported as covered -- a
 * nine-level Cooking one included, by a boost that exists at no level in the
 * game.
 */
public class BoostsTest
{
	private static final Gson GSON = new Gson();

	private static QuestGraph graph() throws Exception
	{
		return QuestGraph.load(GSON);
	}

	private static Boosts boosts() throws Exception
	{
		return new Boosts(graph().boosts());
	}

	/** The bundle has to actually carry them, or every check below is vacuous. */
	@Test
	public void theBundleCarriesTheBoostTable() throws Exception
	{
		assertFalse("graph version 5 carries boosts", boosts().isEmpty());
		assertTrue("every skill's table is in there", graph().boosts().size() > 200);
	}

	/**
	 * The reported case. No Cooking boost reaches nine levels at any level,
	 * so the old "a boost covers this" was impossible rather than optimistic.
	 */
	@Test
	public void noCookingBoostReachesNineLevels() throws Exception
	{
		Boosts boosts = boosts();
		for (int level : new int[]{1, 22, 30, 50, 70, 99})
		{
			assertFalse("a nine-level Cooking gap is not coverable at level " + level,
				boosts.covers("Cooking", level, 9));
		}
	}

	/** And the figures it should quote instead. */
	@Test
	public void cookingBoostsAreSmallAndNamed() throws Exception
	{
		Boosts boosts = boosts();

		Boosts.Best atThirty = boosts.bestFor("Cooking", 30);
		assertEquals(3, atThirty.levels());
		assertFalse("the best reliable one is not a random stew", atThirty.random());
		assertFalse(atThirty.item().isEmpty());

		assertEquals("even at 99 it is six", 6, boosts.bestFor("Cooking", 99).levels());
	}

	/** A one-level gap is genuinely coverable, so the check is not just "no". */
	@Test
	public void aSmallGapIsStillCovered() throws Exception
	{
		assertTrue("one level of Cooking is well within a boost",
			boosts().covers("Cooking", 30, 1));
		assertTrue(boosts().covers("Cooking", 30, 3));
		assertFalse("but four is past what is available at that level",
			boosts().covers("Cooking", 30, 4));
	}

	/** Boosts that scale must be read at the level held, not at the target. */
	@Test
	public void scalingBoostsGrowWithTheLevelHeld() throws Exception
	{
		Boosts boosts = boosts();
		int low = boosts.bestFor("Attack", 1).levels();
		int high = boosts.bestFor("Attack", 99).levels();
		assertTrue("an Attack boost is worth more at 99 than at 1: " + low + " vs " + high,
			high > low);
	}

	/**
	 * A random stew promises nothing reliably, but is still worth mentioning.
	 * Merging the two would make a coin flip look like a plan.
	 */
	@Test
	public void randomBoostsAreSeparatedFromReliableOnes() throws Exception
	{
		Boosts boosts = boosts();
		Boosts.Best reliable = boosts.bestFor("Cooking", 30);
		Boosts.Best either = boosts.bestIncludingRandom("Cooking", 30);

		assertTrue("the stew reaches further than anything reliable",
			either.levels() >= reliable.levels());
		assertFalse("and the reliable answer never depends on a roll", reliable.random());
	}

	/** An unknown skill is not a licence to assume a boost exists. */
	@Test
	public void anUnknownSkillCoversNothing() throws Exception
	{
		assertEquals(0, boosts().bestFor("Sailingg", 50).levels());
		assertFalse(boosts().covers("Sailingg", 50, 1));
	}

	/**
	 * An empty table refuses rather than permits. Treating missing data as
	 * permission is precisely how the original bug worked.
	 */
	@Test
	public void anEmptyTableRefusesEverything()
	{
		Boosts none = new Boosts(null);
		assertTrue(none.isEmpty());
		assertFalse(none.covers("Cooking", 30, 1));
	}

	/**
	 * The classifier now applies the same test, so a quest fifty levels short
	 * on a boostable requirement is no longer called BOOSTABLE.
	 */
	@Test
	public void theClassifierNoLongerCallsAHugeGapBoostable() throws Exception
	{
		QuestGraph graph = graph();
		PlayerState fresh = new PlayerState(new HashMap<>(), new HashMap<>(),
			new HashSet<String>(), Collections.emptyList());
		QuestClassifier classifier = new QuestClassifier(graph, fresh);

		QuestGraph.Quest deep = null;
		for (QuestGraph.Quest quest : graph.quests())
		{
			if (quest.name().equalsIgnoreCase("While Guthix Sleeps"))
			{
				deep = quest;
			}
		}
		assertNotNull(deep);
		assertFalse("a fresh account is not a potion away from While Guthix Sleeps",
			classifier.classify(deep) == QuestStatus.BOOSTABLE);
	}
}
