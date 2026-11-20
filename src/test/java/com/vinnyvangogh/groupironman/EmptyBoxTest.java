package com.vinnyvangogh.groupironman;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The empty Group box line: window by hour, rotation, and the four setting modes. */
public class EmptyBoxTest
{
	private static final long NOW = 1_700_000_000_000L;
	private static final long HOUR = 3_600_000L;

	private static ZonedDateTime at(int day, int hour)
	{
		// 2026-11-16 is a Monday.
		return ZonedDateTime.of(2026, 11, 16 + day, hour, 0, 0, 0, ZoneOffset.UTC);
	}

	/** A step that never hits the rare line. */
	private static final long STEP = 1;

	@Test
	public void windowsByHour()
	{
		assertEquals(3, EmptyBox.window(2));
		assertEquals(0, EmptyBox.window(5));
		assertEquals(0, EmptyBox.window(11));
		assertEquals(1, EmptyBox.window(12));
		assertEquals(2, EmptyBox.window(17));
		assertEquals(3, EmptyBox.window(22));
		assertEquals(3, EmptyBox.window(23));
	}

	@Test
	public void funLinesFollowTheClock()
	{
		assertTrue(EmptyBox.fun(8, false).contains("Early bird gets the logs"));
		assertTrue(EmptyBox.fun(2, false).contains("The group is asleep. Your bank isn't"));
		assertEquals(EmptyBox.fun(8, false).size() + 1, EmptyBox.fun(8, true).size());
	}

	@Test
	public void classicIsAlwaysTheOldLine()
	{
		assertEquals("Nobody else online", EmptyBox.pick(EmptyBoxMode.CLASSIC, at(0, 8), NOW,
			Collections.emptyMap(), "x", 0));
	}

	@Test
	public void usefulNeverTellsAJoke()
	{
		Map<String, LastSeen> seen = new TreeMap<>();
		seen.put("Alex", new LastSeen(NOW - 3 * HOUR, 0, true));
		for (long step = 0; step < 20; step++)
		{
			String line = EmptyBox.pick(EmptyBoxMode.USEFUL, at(0, 8), NOW, seen, null, step);
			assertEquals("Alex last on 3h ago", line);
		}
	}

	@Test
	public void usefulWithNothingKnownFallsBackToClassic()
	{
		assertEquals(EmptyBox.CLASSIC, EmptyBox.pick(EmptyBoxMode.USEFUL, at(0, 8), NOW,
			Collections.emptyMap(), null, STEP));
	}

	@Test
	public void funOnlyUsesTheTimeWindow()
	{
		String line = EmptyBox.pick(EmptyBoxMode.FUN, at(1, 3), NOW, Collections.emptyMap(), "Alex: Level 40", STEP);
		assertTrue(EmptyBox.fun(3, false).contains(line));
	}

	@Test
	public void bothRotatesThroughUsefulAndFun()
	{
		Map<String, LastSeen> seen = new TreeMap<>();
		seen.put("Alex", new LastSeen(NOW - HOUR, 0, true));
		java.util.Set<String> lines = new java.util.HashSet<>();
		for (long step = 1; step <= 6; step++)
		{
			lines.add(EmptyBox.pick(EmptyBoxMode.BOTH, at(0, 8), NOW, seen, "Alex: Level 40", step));
		}
		assertTrue(lines.contains("Alex last on 1h ago"));
		assertTrue(lines.contains("Latest: Alex: Level 40"));
		assertTrue(lines.contains("Early bird gets the logs"));
	}

	@Test
	public void newestSightingFirstAndUnknownSkipped()
	{
		Map<String, LastSeen> seen = new TreeMap<>();
		seen.put("Old", new LastSeen(NOW - 5 * HOUR, 0, true));
		seen.put("New", new LastSeen(NOW - HOUR, 0, true));
		seen.put("Never", new LastSeen(0, 0, false));
		assertEquals(2, EmptyBox.lastOn(seen, NOW).size());
		assertEquals("New last on 1h ago", EmptyBox.lastOn(seen, NOW).get(0));
	}

	@Test
	public void sameStepSameLineAndStepChangesIt()
	{
		long step = NOW / EmptyBox.ROTATE_MS;
		String a = EmptyBox.pick(EmptyBoxMode.FUN, at(0, 8), NOW, Collections.emptyMap(), null, step);
		assertEquals(a, EmptyBox.pick(EmptyBoxMode.FUN, at(0, 8), NOW + 1000, Collections.emptyMap(), null, step));
		String b = EmptyBox.pick(EmptyBoxMode.FUN, at(0, 8), NOW, Collections.emptyMap(), null, step + 1);
		assertTrue(!a.equals(b) || a.equals(EmptyBox.EGG));
	}

	@Test
	public void theRareLineShowsAboutOneInFiftyAndNeverWhenUseful()
	{
		int eggs = 0;
		for (long step = 0; step < 5000; step++)
		{
			if (EmptyBox.EGG.equals(EmptyBox.pick(EmptyBoxMode.FUN, at(0, 8), NOW, Collections.emptyMap(), null, step)))
			{
				eggs++;
			}
			assertTrue(!EmptyBox.EGG.equals(EmptyBox.pick(EmptyBoxMode.USEFUL, at(0, 8), NOW,
				Collections.emptyMap(), "e", step)));
		}
		assertEquals(5000 / EmptyBox.EGG_ONE_IN, eggs);
	}
}
