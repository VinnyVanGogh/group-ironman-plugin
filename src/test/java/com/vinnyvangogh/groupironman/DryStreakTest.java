package com.vinnyvangogh.groupironman;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DryStreakTest
{
	private static final int RATE = DryStreak.RATES.get("zulrah").oneIn;

	private static final long AFTER_HOLD = NOW + DryStreak.HOLD_MS + 1;
	private static final Set<String> UNIQUE = Collections.singleton("Tanzanite fang");

	private static void killTo(DryStreak d, int kills)
	{
		for (int i = 0; i < kills; i++)
		{
			d.kill("Zulrah", NOW, "Alex");
		}
	}

	@Test
	public void callsOutOnceAtEachMultipleOfTheRate()
	{
		DryStreak d = DryStreak.parse("");
		killTo(d, RATE - 1);
		assertTrue(d.due(AFTER_HOLD).isEmpty());
		d.kill("Zulrah", NOW, "Alex");
		List<GroupEvent> out = d.due(AFTER_HOLD);
		assertEquals(1, out.size());
		GroupEvent e = out.get(0);
		assertEquals(GroupEvent.Type.DRY_STREAK, e.type);
		assertEquals("Zulrah", e.text);
		assertEquals(RATE, e.number);
		assertTrue("fires once", d.due(AFTER_HOLD).isEmpty());
		d.kill("Zulrah", NOW, "Alex");
		assertTrue("not again on the next kill", d.due(AFTER_HOLD).isEmpty());
		killTo(d, RATE - 1);
		assertEquals(2L * RATE, d.due(AFTER_HOLD).get(0).number);
	}

	@Test
	public void theCallOutWaitsForTheKillsLoot()
	{
		DryStreak d = DryStreak.parse("");
		killTo(d, RATE);
		assertTrue(d.due(NOW).isEmpty());
		assertTrue(d.due(NOW + DryStreak.HOLD_MS).isEmpty());
		assertEquals(1, d.due(AFTER_HOLD).size());
	}

	@Test
	public void aUniqueOnTheCallOutKillCancelsIt_lineFirst()
	{
		DryStreak d = DryStreak.parse("");
		killTo(d, RATE);
		d.drop("Zulrah", UNIQUE, NOW + 1000);
		assertTrue(d.due(AFTER_HOLD).isEmpty());
		assertEquals(0, d.kills("Zulrah"));
	}

	@Test
	public void aUniqueOnTheCallOutKillCancelsIt_lootFirst()
	{
		DryStreak d = DryStreak.parse("");
		long earlierKill = NOW - DryStreak.HOLD_MS - 1;
		for (int i = 0; i < RATE - 1; i++)
		{
			d.kill("Zulrah", earlierKill, "Alex");
		}
		d.drop("Zulrah", UNIQUE, NOW);
		d.kill("Zulrah", NOW + 1000, "Alex");
		assertTrue(d.due(AFTER_HOLD + 1000).isEmpty());
		assertEquals("the line belongs to the kill that gave the unique", 0, d.kills("Zulrah"));
	}

	@Test
	public void theNextKillAfterAUniqueCountsOne()
	{
		DryStreak d = DryStreak.parse("");
		d.drop("Zulrah", UNIQUE, NOW);
		d.kill("Zulrah", AFTER_HOLD, "Alex");
		assertEquals("a unique whose line never came does not eat a later kill", 1, d.kills("Zulrah"));

		DryStreak lineFirst = DryStreak.parse("");
		killTo(lineFirst, 3);
		lineFirst.drop("Zulrah", UNIQUE, NOW + 1000);
		lineFirst.kill("Zulrah", NOW + 2000, "Alex");
		assertEquals("line then loot leaves nothing behind", 1, lineFirst.kills("Zulrah"));
	}

	@Test
	public void otherDropsDoNotReset()
	{
		DryStreak d = DryStreak.parse("");
		killTo(d, RATE);
		d.drop("Zulrah", Collections.singleton("Zulrah's scales"), NOW + 1000);
		assertEquals(RATE, d.kills("Zulrah"));
		assertEquals("nor cancel the call-out", 1, d.due(AFTER_HOLD).size());
	}

	@Test
	public void bossesWithoutARateAreNotTracked()
	{
		DryStreak d = DryStreak.parse("");
		d.kill("Goblin", NOW, "Alex");
		assertTrue(d.due(AFTER_HOLD).isEmpty());
		assertEquals(0, d.kills("Goblin"));
	}

	@Test
	public void savedStreaksSurviveAndBadTextIsIgnored()
	{
		DryStreak d = DryStreak.parse("");
		killTo(d, 7);
		assertEquals(7, DryStreak.parse(d.save()).kills("Zulrah"));
		assertEquals(0, DryStreak.parse("zulrah=x;;=;").kills("Zulrah"));
		assertEquals(0, DryStreak.parse(null).kills("Zulrah"));
	}
}
