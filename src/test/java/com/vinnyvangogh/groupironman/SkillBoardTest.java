package com.vinnyvangogh.groupironman;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Ranking, gaps, ties, filters and the stat list behind the Compare and Leaderboard tabs. */
public class SkillBoardTest
{
	private static GroupCompare.Figures fig(String name, int qp, Object... skillAndLevel)
	{
		Map<String, Integer> levels = new HashMap<>();
		for (int i = 0; i < skillAndLevel.length; i += 2)
		{
			levels.put((String) skillAndLevel[i], (Integer) skillAndLevel[i + 1]);
		}
		return new GroupCompare.Figures(name, levels, null, qp);
	}

	@Test
	public void gridIsThe24SkillsThenTotalQuestPointsAndCombat()
	{
		assertEquals(27, SkillBoard.STATS.size());
		assertEquals("Attack", SkillBoard.STATS.get(0));
		assertTrue(SkillBoard.STATS.contains("Sailing"));
		assertEquals("Total level", SkillBoard.STATS.get(24));
		assertEquals("Quest points", SkillBoard.STATS.get(25));
		assertEquals("Combat level", SkillBoard.STATS.get(26));
		assertEquals("Combat level", SkillBoard.COMPARE_ROWS.get(25));
		assertEquals("Quest points", SkillBoard.COMPARE_ROWS.get(26));
		assertFalse(SkillBoard.STATS.toString().toLowerCase().contains("worst"));
	}

	@Test
	public void combatLevelFollowsTheOsrsFormulaAndIsUnknownWithoutAllSevenSkills()
	{
		Map<String, Integer> l = new HashMap<>();
		l.put("Attack", 40);
		l.put("Strength", 37);
		l.put("Defence", 1);
		l.put("Hitpoints", 37);
		l.put("Prayer", 26);
		l.put("Ranged", 9);
		l.put("Magic", 44);
		// base 0.25*(1+37+13)=12.75, melee 25.025, magic 0.325*66=21.45
		assertEquals(37, SkillBoard.combat(l));
		l.remove("Magic");
		assertEquals(-1, SkillBoard.combat(l));
		assertNull(SkillBoard.value(fig("Member 1", 0), "Combat level"));
	}

	@Test
	public void leaderIsTheTopMemberOrNoneOnATieOrNoFigures()
	{
		assertEquals(1, SkillBoard.leader(Arrays.asList(5, 9, null)));
		assertEquals(-1, SkillBoard.leader(Arrays.asList(9, 9, 3)));
		assertEquals(-1, SkillBoard.leader(Arrays.asList(null, null)));
		assertTrue(SkillBoard.higher(3, 2));
		assertFalse(SkillBoard.higher(2, 2));
		assertFalse(SkillBoard.higher(null, 2));
	}

	@Test
	public void countsRankFromHiscoreScoresAndUnreadScoresAreUnknown()
	{
		GroupCompare.Figures a = new GroupCompare.Figures("A", null, null, -1, Collections.singletonMap("Zulrah", 40));
		GroupCompare.Figures b = new GroupCompare.Figures("B", null, null, -1, Collections.<String, Integer>emptyMap());
		GroupCompare.Figures c = fig("C", -1);
		assertEquals(40, (int) SkillBoard.value(a, "Zulrah"));
		assertEquals("read but unranked is zero", 0, (int) SkillBoard.value(b, "Zulrah"));
		assertNull("never read is unknown", SkillBoard.value(c, "Zulrah"));
		assertEquals(Collections.singletonList("Zulrah"),
			SkillBoard.activeCounts(Arrays.asList(a, b, c), StatCategory.BOSSING));
		assertTrue(SkillBoard.activeCounts(Arrays.asList(a, b, c), StatCategory.MINIGAMES).isEmpty());
	}

	@Test
	public void categoriesSplitTheHiscoreCounts()
	{
		assertTrue(StatCategory.BOSSING.names().contains("Zulrah"));
		assertTrue(StatCategory.ACTIVITIES.names().contains("Clue Scrolls (all)"));
		assertTrue(StatCategory.MINIGAMES.names().contains("Wintertodt"));
		assertFalse(StatCategory.BOSSING.names().contains("Wintertodt"));
		assertTrue(StatCategory.SKILLING.names().isEmpty());
	}

	@Test
	public void ranksHighestFirstAndSaysWhatItTakesToPassTheMemberAbove()
	{
		List<SkillBoard.Entry> r = SkillBoard.rank(Arrays.asList(
			fig("Member 1", 0, "Magic", 70), fig("Member 2", 0, "Magic", 90), fig("Member 3", 0, "Magic", 85)),
			"Magic");
		assertEquals(Arrays.asList("Member 2", "Member 3", "Member 1"), Arrays.asList(r.get(0).name, r.get(1).name,
			r.get(2).name));
		assertEquals(1, r.get(0).rank);
		assertEquals(5, r.get(0).lead);
		assertEquals("leading by 5", SkillBoard.hint(r.get(0)));
		assertEquals("5 to pass Member 2", SkillBoard.hint(r.get(1)));
		assertEquals("15 to pass Member 3", SkillBoard.hint(r.get(2)));
		assertEquals(3, SkillBoard.entry(r, "Member 1").rank);
	}

	@Test
	public void tiesShareARankAndTheNextRankSkips()
	{
		List<SkillBoard.Entry> r = SkillBoard.rank(Arrays.asList(
			fig("Member 1", 0, "Magic", 80), fig("Member 2", 0, "Magic", 80), fig("Member 3", 0, "Magic", 60)),
			"Magic");
		assertEquals(1, SkillBoard.entry(r, "Member 1").rank);
		assertEquals(1, SkillBoard.entry(r, "Member 2").rank);
		assertEquals(3, SkillBoard.entry(r, "Member 3").rank);
		assertEquals("tied with Member 2", SkillBoard.hint(SkillBoard.entry(r, "Member 1")));
		// Passing a tied pair means passing the first of them.
		assertEquals("20 to pass Member 1", SkillBoard.hint(SkillBoard.entry(r, "Member 3")));
	}

	@Test
	public void unsharedFiguresAreUnrankedAndNeverCountedOrPassed()
	{
		List<SkillBoard.Entry> r = SkillBoard.rank(Arrays.asList(
			fig("Member 1", 0), fig("Member 2", 0, "Magic", 50)), "Magic");
		assertEquals(1, SkillBoard.rankedCount(r));
		SkillBoard.Entry none = SkillBoard.entry(r, "Member 1");
		assertEquals(0, none.rank);
		assertNull(none.value);
		assertEquals("not shared", SkillBoard.hint(none));
		assertEquals("", SkillBoard.hint(SkillBoard.entry(r, "Member 2")));
		assertNull(SkillBoard.entry(r, "nobody"));
	}

	@Test
	public void totalLevelSumsSharedSkillsAndQuestPointsComeFromTheMember()
	{
		List<SkillBoard.Entry> total = SkillBoard.rank(Arrays.asList(
			fig("Member 1", 10, "Magic", 50, "Attack", 40), fig("Member 2", 30, "Magic", 60)), "Total level");
		assertEquals("Member 1", total.get(0).name);
		assertEquals(90, (int) total.get(0).value);
		assertEquals("all 24 skills at 99", 2376, SkillBoard.scale("Total level", total));
		assertEquals(126, SkillBoard.scale("Combat level", total));
		assertEquals(99, SkillBoard.scale("Magic", total));
		List<SkillBoard.Entry> qp = SkillBoard.rank(Arrays.asList(
			fig("Member 1", 10), fig("Member 2", 30), fig("Member 3", -1)), "Quest points");
		assertEquals("Member 2", qp.get(0).name);
		assertEquals(20, SkillBoard.entry(qp, "Member 1").toPass);
		assertEquals(0, SkillBoard.entry(qp, "Member 3").rank);
	}

	@Test
	public void rankingAnEmptyGroupIsEmpty()
	{
		assertTrue(SkillBoard.rank(Collections.<GroupCompare.Figures>emptyList(), "Magic").isEmpty());
	}

}
