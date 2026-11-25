package com.vinnyvangogh.groupironman;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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

	private static GroupEvent drop(String npc)
	{
		return GroupEvent.drop(1L, "Alex", npc, Collections.singletonMap(4151, 1));
	}

	@Test
	public void linesAreDrainedOncePerCategory()
	{
		Digest d = new Digest();
		d.add(drop("Kraken"), "one");
		d.add(drop("Kraken"), "two");
		d.add(GroupEvent.of(GroupEvent.Type.DEATH, 2L, "Alex").build(), "died");
		List<Digest.Entry> drops = d.drain(GroupEvent.Category.DROPS);
		assertEquals(2, drops.size());
		assertEquals("one", drops.get(0).line);
		assertEquals("Alex", drops.get(0).member);
		assertTrue(d.drain(GroupEvent.Category.DROPS).isEmpty());
		assertEquals(1, d.drain(GroupEvent.Category.DEATHS).size());
	}

	@Test
	public void aFullDayKeepsTheNewestLines()
	{
		Digest d = new Digest();
		for (int i = 0; i < Digest.MAX_LINES + 5; i++)
		{
			d.add(drop("Kraken"), "line " + i);
		}
		List<Digest.Entry> lines = d.drain(GroupEvent.Category.DROPS);
		assertEquals(Digest.MAX_LINES, lines.size());
		assertEquals("line 5", lines.get(0).line);
	}

	@Test
	public void savedEntriesSurviveARestartWithEverythingTheDigestSums()
	{
		Digest d = new Digest();
		Map<Integer, Integer> in = new TreeMap<>();
		in.put(995, 799_999);
		in.put(385, 20);
		d.add(GroupEvent.of(GroupEvent.Type.STORAGE_SUMMARY, 5L, "Alex").items(in)
			.taken(Collections.singletonMap(4151, 1)).build(), "visited group storage");
		d.add(GroupEvent.of(GroupEvent.Type.LEVEL_UP, 6L, "Alex").text("Magic").number(62).previous(61).build(),
			"Magic level 62");
		Digest.Entry storage = Digest.parse(d.save()).drain(GroupEvent.Category.STORAGE).get(0);
		assertEquals(GroupEvent.Type.STORAGE_SUMMARY, storage.type);
		assertEquals("Alex", storage.member);
		assertEquals(5L, storage.at);
		assertEquals(in, storage.items);
		assertEquals(Collections.singletonMap(4151, 1), storage.taken);
		assertEquals("visited group storage", storage.line);
		Digest.Entry level = Digest.parse(d.save()).drain(GroupEvent.Category.PROGRESS).get(0);
		assertEquals("Magic", level.text);
		assertEquals(62, level.number);
		assertEquals(61, level.previous);
	}

	@Test
	public void anOlderBuildsLinesStillRead()
	{
		Digest old = Digest.parse("DROPS\tAlex: one\nGROUP\tAlex: back\n");
		Digest.Entry one = old.drain(GroupEvent.Category.DROPS).get(0);
		assertEquals("Alex", one.member);
		assertEquals("one", one.line);
		assertEquals(null, one.type);
		// Saved again, an older line keeps the older shape.
		assertEquals("GROUP\tAlex: back\n", old.save());
		assertTrue(Digest.parse("nonsense\nNOPE\tline").drain(GroupEvent.Category.DROPS).isEmpty());
		assertTrue(Digest.parse(null).drain(GroupEvent.Category.DROPS).isEmpty());
		Digest unreadable = Digest.parse("DROPS\tsaid\tzz\t1\tAlex\tKraken\t0\t0\t\t\n");
		Digest.Entry kept = unreadable.drain(GroupEvent.Category.DROPS).get(0);
		assertEquals("Alex", kept.member);
		assertEquals("said", kept.line);
	}

	@Test
	public void theNextDigestIsTodaysHourOrTomorrows()
	{
		assertEquals(at(4, 20), Digest.nextDaily(at(4, 19), 20, UTC));
		assertEquals(at(5, 20), Digest.nextDaily(at(4, 20), 20, UTC));
		assertEquals(at(5, 20), Digest.nextDaily(at(4, 23), 20, UTC));
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
