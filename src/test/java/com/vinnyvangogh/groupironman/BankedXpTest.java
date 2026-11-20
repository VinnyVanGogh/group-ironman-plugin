package com.vinnyvangogh.groupironman;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Banked experience: the bundled table, the level maths and the lines Compare shows. */
public class BankedXpTest
{
	private static Map<Integer, Integer> items(int... idCount)
	{
		Map<Integer, Integer> m = new HashMap<>();
		for (int i = 0; i < idCount.length; i += 2)
		{
			m.put(idCount[i], idCount[i + 1]);
		}
		return m;
	}

	@Test
	public void theLevelTableMatchesTheGame()
	{
		assertEquals(0, BankedXp.xpAt(1));
		assertEquals(83, BankedXp.xpAt(2));
		assertEquals(1154, BankedXp.xpAt(10));
		assertEquals(13363, BankedXp.xpAt(30));
		assertEquals(13034431, BankedXp.xpAt(99));
	}

	@Test
	public void levelAtRoundsDownAndCapsAt99()
	{
		assertEquals(1, BankedXp.levelAt(82, 1));
		assertEquals(2, BankedXp.levelAt(83, 1));
		assertEquals(99, BankedXp.levelAt(200_000_000, 1));
		assertEquals("never below where the player already is", 40, BankedXp.levelAt(0, 40));
	}

	@Test
	public void theBundledTableIdsAreTheItemsTheyNameAndEachHasOneSkill()
	{
		BankedXp t = BankedXp.bundled();
		assertEquals(72, t.of(ItemID.DRAGON_BONES).xp, 0);
		assertEquals("Prayer", t.of(ItemID.DRAGON_BONES).skill);
		assertEquals("Prayer", t.of(ItemID.DRAGON_BONES_SUPERIOR).skill);
		assertEquals("Prayer", t.of(ItemID.ABYSSAL_ASHES).skill);
		assertEquals("Prayer", t.of(ItemID.ZOGRE_ANCESTRAL_BONES_OURG).skill);
		assertEquals(202.5, t.of(ItemID.YEW_LOGS).xp, 0);
		assertEquals("Firemaking", t.of(ItemID.REDWOOD_LOGS).skill);
		assertEquals("Cooking", t.of(ItemID.RAW_SHARK).skill);
		assertEquals(230, t.of(ItemID.RAW_ANGLERFISH).xp, 0);
		assertEquals("Crafting", t.of(ItemID.UNCUT_DRAGONSTONE).skill);
		assertEquals("Smithing", t.of(ItemID.RUNITE_BAR).skill);
		assertEquals(75, t.of(ItemID.RUNITE_BAR).xp, 0);
		assertTrue(t.size() > 60);
	}

	@Test
	public void quantitiesMultiplyAndUnknownItemsAddNothing()
	{
		Map<String, Double> xp = BankedXp.bundled().xp(items(ItemID.DRAGON_BONES, 10, ItemID.YEW_LOGS, 2, 999999, 5,
			ItemID.BONES, 0));
		assertEquals(720, xp.get("Prayer"), 0);
		assertEquals(405, xp.get("Firemaking"), 0);
		assertEquals(2, xp.size());
	}

	@Test
	public void combineAddsHoldingsAndToleratesNull()
	{
		Map<Integer, Integer> c = BankedXp.combine(items(1, 2, 3, 4), null, items(1, 5));
		assertEquals(7, (int) c.get(1));
		assertEquals(4, (int) c.get(3));
		assertTrue(BankedXp.combine().isEmpty());
	}

	@Test
	public void saturatingAddKeepsHugeStacksFromWrapping()
	{
		Map<Integer, Integer> c = BankedXp.combine(items(1, Integer.MAX_VALUE), items(1, 10));
		assertEquals(Integer.MAX_VALUE, (int) c.get(1));
	}

	@Test
	public void gainsReportLevelsFromTheStartOfTheCurrentLevel()
	{
		// Prayer 1 starts at 0 xp; 83 xp banked is exactly level 2, 82 is not.
		assertEquals(2, BankedXp.gains(Collections.singletonMap("Prayer", 83.0),
			Collections.singletonMap("Prayer", 1)).get(0).to);
		assertEquals(1, BankedXp.gains(Collections.singletonMap("Prayer", 82.0),
			Collections.singletonMap("Prayer", 1)).get(0).to);
	}

	@Test
	public void gainsNeverDropBelowTheCurrentLevelAndSkipUnknownSkills()
	{
		Map<String, Double> banked = new HashMap<>();
		banked.put("Prayer", 1.0);
		banked.put("Cooking", 5000.0);
		List<BankedXp.Gain> g = BankedXp.gains(banked, Collections.singletonMap("Prayer", 70));
		assertEquals(1, g.size());
		assertEquals(70, g.get(0).to);
		assertEquals(0, g.get(0).levels());
	}

	@Test
	public void level99StaysAt99()
	{
		BankedXp.Gain g = BankedXp.gains(Collections.singletonMap("Cooking", 1e9),
			Collections.singletonMap("Cooking", 99)).get(0);
		assertEquals(99, g.to);
	}

	@Test
	public void gainsAreOrderedByLevelsThenExperience()
	{
		Map<String, Double> banked = new HashMap<>();
		banked.put("Prayer", 100.0);
		banked.put("Cooking", 400.0);
		banked.put("Smithing", 90.0);
		Map<String, Integer> levels = new HashMap<>();
		levels.put("Prayer", 1);
		levels.put("Cooking", 1);
		levels.put("Smithing", 1);
		List<BankedXp.Gain> g = BankedXp.gains(banked, levels);
		assertEquals("Cooking", g.get(0).skill);
		assertEquals("Prayer", g.get(1).skill);
		assertEquals("Smithing", g.get(2).skill);
	}

	@Test
	public void describeShowsTheLevelsGained()
	{
		BankedXp.Gain g = new BankedXp.Gain("Prayer", 1234.4, 40, 43);
		assertEquals("Prayer 40 > 43 (+3), 1,234 xp", BankedXp.describe(g));
		assertEquals("Prayer 40, 5 xp", BankedXp.describe(new BankedXp.Gain("Prayer", 5, 40, 40)));
	}

	@Test
	public void parseSkipsCommentsAndMalformedLines()
	{
		BankedXp t = BankedXp.parse("# c\n1|A|Prayer|4.5\nbad\n2|B|Prayer|x\n3|C||5\n-4|D|Prayer|1\n5|E|Prayer|0\n");
		assertEquals(1, t.size());
		assertNull(t.of(2));
	}

	@Test
	public void sharedStorageIsCountedOnceInTheGroupTotalAndNotPerMember()
	{
		Map<String, Map<String, Double>> banked = new HashMap<>();
		banked.put("A", Collections.singletonMap("Prayer", 100.0));
		banked.put("B", Collections.singletonMap("Prayer", 50.0));
		Map<String, Double> storage = Collections.singletonMap("Prayer", 1000.0);
		Map<String, Map<String, Integer>> levels = new HashMap<>();
		levels.put("A", Collections.singletonMap("Prayer", 1));
		levels.put("B", Collections.singletonMap("Prayer", 1));

		List<String> with = BankedXp.lines(banked, storage, levels, true);
		assertTrue(with.contains("  Prayer: 1,150 xp"));
		assertTrue(with.contains("Group total (shared storage counted once):"));
		List<String> without = BankedXp.lines(banked, storage, levels, false);
		assertTrue(without.contains("  Prayer: 150 xp"));
		assertTrue("A's own line never includes storage", with.contains("  Prayer 1 > 2 (+1), 100 xp"));
	}

	@Test
	public void aMemberWithNothingBankedSaysSo()
	{
		List<String> out = BankedXp.lines(Collections.singletonMap("A", Collections.emptyMap()),
			Collections.emptyMap(), Collections.emptyMap(), true);
		assertTrue(out.get(1).contains("nothing banked"));
		assertEquals("  nothing yet", out.get(3));
	}
}
