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
	public void statsAreTotalThe24SkillsAndQuestPoints()
	{
		assertEquals(26, SkillBoard.STATS.size());
		assertEquals("Total level", SkillBoard.STATS.get(0));
		assertTrue(SkillBoard.STATS.contains("Sailing"));
		assertEquals("Quest points", SkillBoard.STATS.get(25));
		assertFalse(SkillBoard.STATS.toString().toLowerCase().contains("worst"));
	}

	@Test
	public void stepWrapsAtBothEnds()
	{
		assertEquals("Attack", SkillBoard.step("Total level", 1));
		assertEquals("Quest points", SkillBoard.step("Total level", -1));
		assertEquals("Total level", SkillBoard.step("Quest points", 1));
		assertEquals("Total level", SkillBoard.step("nonsense", 1));
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
		assertEquals(90, SkillBoard.scale("Total level", total));
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

	@Test
	public void compareListsTheBiggestGapFirstAndFiltersBehindAheadAll()
	{
		GroupCompare.Figures me = fig("Member 1", 100, "Magic", 70, "Attack", 90, "Mining", 50, "Slayer", 60);
		GroupCompare.Figures them = fig("Member 2", 120, "Magic", 85, "Attack", 80, "Mining", 50, "Hunter", 20);
		List<SkillBoard.Gap> behind = SkillBoard.compare(me, them, SkillBoard.Filter.BEHIND);
		assertEquals("Quest points", behind.get(0).stat);
		assertEquals(-20, behind.get(0).delta);
		assertEquals("Magic", behind.get(1).stat);
		assertEquals(2, behind.size());
		List<SkillBoard.Gap> ahead = SkillBoard.compare(me, them, SkillBoard.Filter.AHEAD);
		assertEquals("total level is summed from the shared skills", "Total level", ahead.get(0).stat);
		assertEquals(35, ahead.get(0).delta);
		assertEquals("Attack", ahead.get(1).stat);
		assertEquals(10, ahead.get(1).delta);
		List<SkillBoard.Gap> all = SkillBoard.compare(me, them, SkillBoard.Filter.ALL);
		assertTrue("a tie is listed under All", all.stream().anyMatch(g -> g.stat.equals("Mining") && g.delta == 0));
		assertEquals("ties come last", "Mining", all.get(all.size() - 1).stat);
		assertFalse("Slayer and Hunter were shared by one side only", all.stream()
			.anyMatch(g -> g.stat.equals("Slayer") || g.stat.equals("Hunter")));
		assertFalse("a tie is neither behind nor ahead", behind.stream().anyMatch(g -> g.delta == 0)
			|| ahead.stream().anyMatch(g -> g.delta == 0));
	}

	@Test
	public void equalGapsKeepDropdownOrder()
	{
		GroupCompare.Figures me = fig("Member 1", -1, "Strength", 50, "Attack", 50);
		GroupCompare.Figures them = fig("Member 2", -1, "Strength", 60, "Attack", 60);
		List<SkillBoard.Gap> g = SkillBoard.compare(me, them, SkillBoard.Filter.BEHIND);
		assertNotNull(g);
		int attack = -1;
		int strength = -1;
		for (int i = 0; i < g.size(); i++)
		{
			attack = g.get(i).stat.equals("Attack") ? i : attack;
			strength = g.get(i).stat.equals("Strength") ? i : strength;
		}
		assertTrue(attack < strength);
	}
}
