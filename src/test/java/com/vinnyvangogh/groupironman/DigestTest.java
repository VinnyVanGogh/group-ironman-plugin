package com.vinnyvangogh.groupironman;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The weekly recap's schedule and tally. The daily digest's queue and schedule moved to Discord Relay. */
public class DigestTest
{
	private static final ZoneId UTC = ZoneOffset.UTC;

	private static long at(int day, int hour)
	{
		// 2026-11-02 is a Monday.
		return LocalDateTime.of(2026, 11, day, hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
	}

	@Test
	public void theRecapIsDueMondaysAfterTheHour()
	{
		assertFalse("Sunday", Digest.weeklyDue(at(2, 21), at(8, 23), 20, UTC));
		assertFalse("Monday, too early", Digest.weeklyDue(at(2, 21), at(9, 19), 20, UTC));
		assertTrue(Digest.weeklyDue(at(2, 21), at(9, 20), 20, UTC));
		assertFalse("sent this week", Digest.weeklyDue(at(9, 20), at(10, 22), 20, UTC));
		assertTrue("logged in late in the week still sends", Digest.weeklyDue(at(2, 21), at(12, 9), 20, UTC));
		assertFalse("a first run only sets the baseline", Digest.weeklyDue(0, at(9, 22), 20, UTC));
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
