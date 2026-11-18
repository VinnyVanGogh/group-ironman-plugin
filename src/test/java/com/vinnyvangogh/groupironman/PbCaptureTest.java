package com.vinnyvangogh.groupironman;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import org.junit.Test;

public class PbCaptureTest
{
	@Test
	public void newBestAfterKillCountLineIsRecordedForThatBoss()
	{
		PbCapture c = new PbCapture();
		assertFalse(c.chat("Your Vorkath kill count is: <col=ff0000>200</col>."));
		assertTrue(c.chat("Fight duration: <col=ff0000>1:23</col> (new personal best)"));
		assertEquals(83.0, c.bests().get("Vorkath"), 0.0001);
	}

	@Test
	public void keepsTheBestPersonalBestLineAndIgnoresSlowerKills()
	{
		PbCapture c = new PbCapture();
		c.chat("Your Zulrah kill count is: 5.");
		c.chat("Fight duration: 1:40. Personal best: 1:23");
		assertEquals(83.0, c.bests().get("Zulrah"), 0.0001);
		c.chat("Your Zulrah kill count is: 6.");
		assertFalse("a slower kill is no change", c.chat("Fight duration: 2:00. Personal best: 1:23"));
		assertEquals(83.0, c.bests().get("Zulrah"), 0.0001);
	}

	@Test
	public void hourTimesAndTenths()
	{
		assertEquals(3723.0, PbCapture.seconds("1:02:03"), 0.0001);
		assertEquals(65.4, PbCapture.seconds("1:05.4"), 0.0001);
		assertNull(PbCapture.seconds("soon"));
		assertNull(PbCapture.seconds("1:2:3:4"));
	}

	@Test
	public void aTimeWithNoKnownBossIsDropped()
	{
		PbCapture c = new PbCapture();
		assertFalse(c.chat("Fight duration: 1:23 (new personal best)"));
		assertTrue(c.bests().isEmpty());
	}

	@Test
	public void theBossIsForgottenAfterOneTime()
	{
		PbCapture c = new PbCapture();
		c.chat("Your Vorkath kill count is: 1.");
		c.chat("Fight duration: 1:23 (new personal best)");
		assertFalse("same boss is not reused for an unrelated line",
			c.chat("Fight duration: 1:00 (new personal best)"));
	}

	@Test
	public void startsFromSavedBestsAndNeverGoesSlower()
	{
		PbCapture c = new PbCapture();
		c.load(Collections.singletonMap("Vorkath", 70.0));
		c.chat("Your Vorkath kill count is: 9.");
		assertFalse(c.chat("Fight duration: 1:23 (new personal best)"));
		assertEquals(70.0, c.bests().get("Vorkath"), 0.0001);
	}
}
