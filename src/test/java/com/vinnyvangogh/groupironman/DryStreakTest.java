package com.vinnyvangogh.groupironman;

import java.util.Collections;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class DryStreakTest
{
	private static final int RATE = DryStreak.RATES.get("zulrah").oneIn;

	private static GroupEvent killTo(DryStreak d, int kills)
	{
		GroupEvent last = null;
		for (int i = 0; i < kills; i++)
		{
			last = d.kill("Zulrah", NOW, "Alex");
		}
		return last;
	}

	@Test
	public void callsOutOnceAtEachMultipleOfTheRate()
	{
		DryStreak d = DryStreak.parse("");
		assertNull(killTo(d, RATE - 1));
		GroupEvent e = d.kill("Zulrah", NOW, "Alex");
		assertNotNull(e);
		assertEquals(GroupEvent.Type.DRY_STREAK, e.type);
		assertEquals("Zulrah", e.text);
		assertEquals(RATE, e.number);
		assertNull("not again on the next kill", d.kill("Zulrah", NOW, "Alex"));
		assertEquals(2L * RATE, killTo(d, RATE - 1).number);
	}

	@Test
	public void theUniqueResetsTheStreak()
	{
		DryStreak d = DryStreak.parse("");
		killTo(d, RATE - 1);
		d.drop("Zulrah", Collections.singleton("Tanzanite fang"));
		assertNull(d.kill("Zulrah", NOW, "Alex"));
		assertEquals(1, d.kills("Zulrah"));
	}

	@Test
	public void otherDropsDoNotReset()
	{
		DryStreak d = DryStreak.parse("");
		killTo(d, 5);
		d.drop("Zulrah", Collections.singleton("Zulrah's scales"));
		assertEquals(5, d.kills("Zulrah"));
	}

	@Test
	public void bossesWithoutARateAreNotTracked()
	{
		DryStreak d = DryStreak.parse("");
		assertNull(d.kill("Goblin", NOW, "Alex"));
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
