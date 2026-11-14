package com.vinnyvangogh.groupironman;

import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class BoostTextTest
{
	@Test
	public void unboostedIsJustTheLevel()
	{
		assertEquals("90", BoostText.level(90, 90));
	}

	@Test
	public void boostedShowsThePlus()
	{
		assertEquals("90 (+8)", BoostText.level(90, 98));
	}

	@Test
	public void drainedShowsTheMinus()
	{
		assertEquals("70 (-3)", BoostText.level(70, 67));
	}

	@Test
	public void deadOrEmptyPrayerIsAFullDrain()
	{
		assertEquals("99 (-99)", BoostText.level(99, 0));
	}

	@Test
	public void agoRoundsDown()
	{
		long now = TimeUnit.DAYS.toMillis(30);
		assertEquals("just now", BoostText.ago(now, now - 59_000));
		assertEquals("5m ago", BoostText.ago(now, now - TimeUnit.MINUTES.toMillis(5) - 30_000));
		assertEquals("3h ago", BoostText.ago(now, now - TimeUnit.MINUTES.toMillis(200)));
		assertEquals("2d ago", BoostText.ago(now, now - TimeUnit.HOURS.toMillis(50)));
	}

	@Test
	public void aTimestampFromTheFutureReadsAsJustNow()
	{
		assertEquals("just now", BoostText.ago(1000, 50_000));
	}
}
