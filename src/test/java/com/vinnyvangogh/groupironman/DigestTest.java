package com.vinnyvangogh.groupironman;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The daily digest's queue and the digest and recap schedule. */
public class DigestTest
{
	private static final ZoneId UTC = ZoneOffset.UTC;

	private static long at(int day, int hour)
	{
		// 2026-11-02 is a Monday.
		return LocalDateTime.of(2026, 11, day, hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
	}

	@Test
	public void theDailyDigestIsDueOnceAfterItsHour()
	{
		assertFalse("before the hour", Digest.dailyDue(at(3, 21), at(4, 19), 20, UTC));
		assertTrue(Digest.dailyDue(at(3, 21), at(4, 20), 20, UTC));
		assertFalse("already sent today", Digest.dailyDue(at(4, 20), at(4, 23), 20, UTC));
		assertTrue("a missed day still sends", Digest.dailyDue(at(1, 21), at(4, 22), 20, UTC));
	}

	@Test
	public void aFirstRunOnlySetsTheBaseline()
	{
		assertFalse(Digest.dailyDue(0, at(4, 22), 20, UTC));
		assertFalse(Digest.weeklyDue(0, at(9, 22), 20, UTC));
	}

	@Test
	public void theRecapIsDueMondaysAfterTheHour()
	{
		assertFalse("Sunday", Digest.weeklyDue(at(2, 21), at(8, 23), 20, UTC));
		assertFalse("Monday, too early", Digest.weeklyDue(at(2, 21), at(9, 19), 20, UTC));
		assertTrue(Digest.weeklyDue(at(2, 21), at(9, 20), 20, UTC));
		assertFalse("sent this week", Digest.weeklyDue(at(9, 20), at(10, 22), 20, UTC));
		assertTrue("logged in late in the week still sends", Digest.weeklyDue(at(2, 21), at(12, 9), 20, UTC));
	}

	@Test
	public void linesAreDrainedOncePerCategory()
	{
		Digest d = new Digest();
		d.add(GroupEvent.Category.DROPS, "Alex: one");
		d.add(GroupEvent.Category.DROPS, "Alex: two");
		d.add(GroupEvent.Category.DEATHS, "Alex: died");
		List<String> drops = d.drain(GroupEvent.Category.DROPS);
		assertEquals(2, drops.size());
		assertEquals("Alex: one", drops.get(0));
		assertTrue(d.drain(GroupEvent.Category.DROPS).isEmpty());
		assertEquals(1, d.drain(GroupEvent.Category.DEATHS).size());
	}

	@Test
	public void aFullDayKeepsTheNewestLines()
	{
		Digest d = new Digest();
		for (int i = 0; i < Digest.MAX_LINES + 5; i++)
		{
			d.add(GroupEvent.Category.DROPS, "line " + i);
		}
		List<String> lines = d.drain(GroupEvent.Category.DROPS);
		assertEquals(Digest.MAX_LINES, lines.size());
		assertEquals("line 5", lines.get(0));
	}

	@Test
	public void savedLinesSurviveARestart()
	{
		Digest d = new Digest();
		d.add(GroupEvent.Category.DROPS, "Alex: one");
		d.add(GroupEvent.Category.GROUP, "Alex: back");
		Digest back = Digest.parse(d.save());
		assertEquals("Alex: one", back.drain(GroupEvent.Category.DROPS).get(0));
		assertEquals("Alex: back", back.drain(GroupEvent.Category.GROUP).get(0));
		assertTrue(Digest.parse("nonsense\nNOPE\tline").drain(GroupEvent.Category.DROPS).isEmpty());
		assertTrue(Digest.parse(null).drain(GroupEvent.Category.DROPS).isEmpty());
	}

	@Test
	public void theRecapTalliesTheWeekAndStartsOver()
	{
		Digest.Week w = Digest.Week.parse("");
		w.xp(1_000_000);
		w.xp(1_250_000);
		w.drop();
		w.drop();
		w.kill();
		w.storage(true);
		w.storage(false);
		w.storage(false);
		Digest.Week saved = Digest.Week.parse(w.save());
		GroupEvent e = saved.recap(1L, "Alex");
		assertEquals(GroupEvent.Type.WEEKLY_RECAP, e.type);
		assertEquals(250_000, e.number);
		assertEquals("2 drops, 1 kills, 1 deposits, 2 withdrawals", e.text);
		saved.startOver();
		assertEquals(0, saved.recap(1L, "Alex").number);
	}
}
