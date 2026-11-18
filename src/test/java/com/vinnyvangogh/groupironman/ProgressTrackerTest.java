package com.vinnyvangogh.groupironman;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ProgressTrackerTest
{
	private static final String[] SKILLS = {"Attack", "Strength", "Defence"};
	private ProgressTracker tracker;

	@Before
	public void login()
	{
		tracker = new ProgressTracker(SKILLS.length);
		for (String s : SKILLS)
		{
			assertTrue("the login burst is only remembered", tracker.skill(s, 90, 5_400_000, NOW, "Alex").isEmpty());
		}
	}

	@Test
	public void aLevelUpIsOneEventWithSkillAndLevel()
	{
		List<GroupEvent> out = tracker.skill("Attack", 91, 5_902_831, NOW, "Alex");
		assertEquals(1, out.size());
		GroupEvent e = out.get(0);
		assertEquals(GroupEvent.Type.LEVEL_UP, e.type);
		assertEquals("Attack", e.text);
		assertEquals(91, e.number);
		assertEquals("Alex", e.member);
		assertTrue("XP alone is not a level", tracker.skill("Attack", 91, 5_910_000, NOW, "Alex").isEmpty());
	}

	@Test
	public void ninetyNineIsAlsoMaxed()
	{
		List<GroupEvent> out = tracker.skill("Attack", 99, 13_034_431, NOW, "Alex");
		assertEquals(GroupEvent.Type.LEVEL_UP, out.get(0).type);
		assertEquals(GroupEvent.Type.MAXED, out.get(1).type);
		assertEquals("Attack", out.get(1).text);
		assertEquals("crossed 10M too", GroupEvent.Type.XP_MILESTONE, out.get(2).type);
		assertEquals(10_000_000L, out.get(2).number);
	}

	@Test
	public void xpMilestonesFireOnceEach()
	{
		ProgressTracker t = new ProgressTracker(1);
		t.skill("Mining", 99, 199_000_000, NOW, "Alex");
		List<GroupEvent> out = t.skill("Mining", 99, 200_000_000, NOW, "Alex");
		assertEquals(1, out.size());
		assertEquals(200_000_000L, out.get(0).number);
		assertTrue(t.skill("Mining", 99, 200_000_000, NOW, "Alex").isEmpty());
	}

	@Test
	public void totalLevelEveryHundredAndMaxTotal()
	{
		ProgressTracker t = new ProgressTracker(2);
		t.skill("Attack", 99, 13_034_431, NOW, "Alex");
		t.skill("Strength", 98, 11_805_606, NOW, "Alex");
		List<GroupEvent> out = t.skill("Strength", 99, 13_034_431, NOW, "Alex");
		assertTrue(out.stream().anyMatch(e -> e.type == GroupEvent.Type.MAXED && "total".equals(e.text)
			&& e.number == 198));
		assertTrue("197 to 198 crosses no hundred", out.stream().noneMatch(e -> e.type == GroupEvent.Type.TOTAL_LEVEL));

		ProgressTracker h = new ProgressTracker(2);
		h.skill("Attack", 50, 0, NOW, "Alex");
		h.skill("Strength", 49, 0, NOW, "Alex");
		GroupEvent total = h.skill("Strength", 50, 0, NOW, "Alex").stream()
			.filter(e -> e.type == GroupEvent.Type.TOTAL_LEVEL).findFirst().orElse(null);
		assertNotNull(total);
		assertEquals(100, total.number);
	}

	@Test
	public void combatAndQuestPoints()
	{
		assertNull("first value is the baseline", tracker.combat(100, NOW, "Alex"));
		assertNull(tracker.combat(100, NOW, "Alex"));
		assertEquals(101, tracker.combat(101, NOW, "Alex").number);

		assertNull(tracker.questPoints(49, NOW, "Alex"));
		assertNull(tracker.questPoints(49, NOW, "Alex"));
		GroupEvent qp = tracker.questPoints(51, NOW, "Alex");
		assertEquals(GroupEvent.Type.QUEST_POINTS, qp.type);
		assertEquals(50, qp.number);
	}

	@Test
	public void aResetForgetsTheBaseline()
	{
		tracker.reset();
		assertTrue("another account's levels are a baseline, not level ups",
			tracker.skill("Attack", 99, 13_034_431, NOW, "Sam").isEmpty());
	}
}
