package com.vinnyvangogh.groupironman;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The text the Compare tab builds, and the settings that narrow "who benefits most". */
public class CompareTabTest
{
	private static GroupCompare.Figures fig(String name, int qp, Map<String, Integer> levels, Map<String, Double> pbs)
	{
		return new GroupCompare.Figures(name, levels, PersonalBests.of(pbs), qp);
	}

	@Test
	public void playerLinesNameTheLeaderAndShowUnknownAsAQuestionMark()
	{
		Map<String, Integer> a = new HashMap<>();
		a.put("Magic", 50);
		Map<String, Integer> b = new HashMap<>();
		b.put("Magic", 60);
		b.put("Attack", 70);
		List<String> lines = CompareTab.playerLines(GroupCompare.players(
			fig("GIM1", -1, a, Collections.singletonMap("Zulrah", 70.0)),
			fig("GIM2", 100, b, Collections.singletonMap("Zulrah", 65.0))), "GIM1", "GIM2");
		assertTrue(lines.contains("Magic 50 / 60  GIM2"));
		assertTrue("an unshared level is unknown, not a loss", lines.contains("Attack ? / 70"));
		assertTrue(lines.contains("Zulrah 1:10.0 / 1:05.0  GIM2"));
		assertTrue(lines.contains("Quest points ? / 100"));
	}

	@Test
	public void leaderboardLinesCanBeSortedByBestHolder()
	{
		List<GroupCompare.Leader> leaders = GroupCompare.leaderboard(Arrays.asList(
			fig("Zed", 10, Collections.singletonMap("Magic", 90), null),
			fig("Amy", 20, Collections.singletonMap("Magic", 50), null)));
		List<String> byName = CompareTab.leaderboardLines(leaders, "Name");
		assertTrue(byName.contains("Magic: best Zed, worst Amy"));
		List<String> byBest = CompareTab.leaderboardLines(leaders, "Best");
		assertEquals(leaders.size(), byBest.size());
	}

	@Test
	public void groupLinesSayWhoLeadsAndByHowMuch()
	{
		Map<String, Long> mine = new HashMap<>();
		mine.put("Magic", 2_000_000L);
		mine.put("Attack", 100L);
		Map<String, Long> theirs = new HashMap<>();
		theirs.put("Magic", 1_000_000L);
		theirs.put("Attack", 300L);
		List<String> lines = CompareTab.groupLines(mine, theirs);
		assertTrue(lines.contains("Magic: you lead by 1,000,000"));
		assertTrue(lines.contains("Attack: behind by 200"));
	}

	@Test
	public void aGroupThatCouldNotBeReadIsSaidSo()
	{
		assertTrue(CompareTab.groupLines(null, Collections.<String, Long>emptyMap()).get(0).contains("your group"));
		assertTrue(CompareTab.groupLines(Collections.<String, Long>emptyMap(), null).get(0).contains("that group"));
	}

	@Test
	public void namesAreTrimmedDeduplicatedAndCappedAtAGroup()
	{
		assertEquals(Arrays.asList("A", "B"), CompareTab.parseNames(" A, B ,a,, "));
		assertEquals(CompareTab.MAX_OTHER_NAMES, CompareTab.parseNames("1,2,3,4,5,6,7").size());
		assertTrue(CompareTab.parseNames(null).isEmpty());
	}

	@Test
	public void benefitSettingsParseBlankAsAllAndIgnoreUnknownNames()
	{
		ItemBenefit.Settings all = ItemBenefit.Settings.parse("", "  ");
		assertEquals(ItemBenefit.ALL_STATS, all.stats());
		assertEquals(ItemBenefit.ALL_CATEGORIES, all.categories());
		ItemBenefit.Settings some = ItemBenefit.Settings.parse("Defence, bogus", "weapon,shield");
		assertEquals(Collections.singleton("defence"), some.stats());
		assertEquals(Arrays.asList("weapon", "shield"), Arrays.asList(some.categories().toArray()));
		assertEquals("only unknown names fall back to all", ItemBenefit.ALL_STATS,
			ItemBenefit.Settings.parse("bogus", "").stats());
	}

	@Test
	public void benefitLinesExplainEachMembersPosition()
	{
		List<ItemBenefit.Result> ranked = Arrays.asList(
			new ItemBenefit.Result("A", true, 0, 12, false),
			new ItemBenefit.Result("B", false, 1, 0, false),
			new ItemBenefit.Result("C", true, 0, 0, true));
		List<String> lines = CompareTab.benefitLines(new BenefitFinder.Outcome("Whip", ranked, "", true));
		assertEquals("Whip, best first:", lines.get(0));
		assertEquals("A: can use it now, +12 over current gear", lines.get(1));
		assertEquals("B: 1 level short", lines.get(2));
		assertEquals("C: already has an equal or better one", lines.get(3));
		assertTrue(lines.get(4).contains("requirements"));
	}
}
