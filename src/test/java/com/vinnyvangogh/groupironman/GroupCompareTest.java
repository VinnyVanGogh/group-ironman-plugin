package com.vinnyvangogh.groupironman;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Compare: two players side by side, the leaderboard per stat, and group against group. */
public class GroupCompareTest
{
	private static GroupCompare.Figures fig(String name, int qp, Map<String, Integer> levels, Map<String, Double> pbs)
	{
		return new GroupCompare.Figures(name, levels, PersonalBests.of(pbs), qp);
	}

	private static Map<String, Integer> lv(Object... kv)
	{
		Map<String, Integer> m = new HashMap<>();
		for (int i = 0; i < kv.length; i += 2)
		{
			m.put((String) kv[i], (Integer) kv[i + 1]);
		}
		return m;
	}

	@Test
	public void playersSideBySideSkillBossAndQuests()
	{
		GroupCompare.Figures a = fig("GIM1", 100, lv("Attack", 70, "Magic", 50),
			Collections.singletonMap("Zulrah", 70.0));
		GroupCompare.Figures b = fig("GIM2", 120, lv("Attack", 70, "Magic", 60),
			Collections.singletonMap("Zulrah", 65.0));
		List<GroupCompare.Row> rows = GroupCompare.players(a, b);
		assertEquals(GroupCompare.Verdict.TIE, row(rows, "Attack").verdict);
		assertEquals(GroupCompare.Verdict.B, row(rows, "Magic").verdict);
		assertEquals("lower time wins", GroupCompare.Verdict.B, row(rows, "Zulrah").verdict);
		assertEquals(GroupCompare.Verdict.B, row(rows, "Quest points").verdict);
		assertEquals(GroupCompare.Verdict.B, row(rows, "Total level").verdict);
	}

	@Test
	public void missingFigureIsUnknownNotLoss()
	{
		GroupCompare.Figures a = fig("GIM1", 100, lv("Attack", 70), Collections.singletonMap("Zulrah", 70.0));
		GroupCompare.Figures b = fig("GIM2", -1, lv("Attack", 60), Collections.emptyMap());
		List<GroupCompare.Row> rows = GroupCompare.players(a, b);
		assertEquals(GroupCompare.Verdict.UNKNOWN, row(rows, "Zulrah").verdict);
		assertEquals(GroupCompare.Verdict.UNKNOWN, row(rows, "Quest points").verdict);
		assertEquals(GroupCompare.Verdict.A, row(rows, "Attack").verdict);
	}

	@Test
	public void groupAgainstGroupIsSkillBySkillGap()
	{
		Map<String, Long> mine = new HashMap<>();
		mine.put("Attack", 1000L);
		mine.put("Magic", 500L);
		Map<String, Long> next = new HashMap<>();
		next.put("Attack", 900L);
		next.put("Magic", 800L);
		next.put("Runecraft", 5L);
		List<GroupCompare.Gap> gaps = GroupCompare.groups(mine, next);
		assertEquals(100L, gap(gaps, "Attack").delta);
		assertEquals(-300L, gap(gaps, "Magic").delta);
		assertTrue("a skill only they have still lists", gap(gaps, "Runecraft").delta < 0);
	}

	private static GroupCompare.Row row(List<GroupCompare.Row> rows, String label)
	{
		return rows.stream().filter(r -> r.label.equals(label)).findFirst().get();
	}

	private static GroupCompare.Gap gap(List<GroupCompare.Gap> l, String label)
	{
		return l.stream().filter(r -> r.label.equals(label)).findFirst().get();
	}
}
