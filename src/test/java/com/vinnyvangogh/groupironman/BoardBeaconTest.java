package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The "this client keeps the live board" message, and when it stops other clients posting online/offline. */
public class BoardBeaconTest
{
	private static final Gson GSON = new Gson();

	@Test
	public void roundTrips()
	{
		String json = BoardBeacon.encode(GSON, 1_000L);
		assertTrue(BoardBeacon.isBeacon(json));
		assertTrue(BoardBeacon.valid(GSON, json));
	}

	@Test
	public void otherKindsAndJunkAreNotBeacons()
	{
		assertFalse(BoardBeacon.isBeacon(null));
		assertFalse(BoardBeacon.isBeacon("{\"k\":\"activity\"}"));
		assertFalse(BoardBeacon.valid(GSON, null));
		assertFalse(BoardBeacon.valid(GSON, "not json \"board\""));
		assertFalse(BoardBeacon.valid(GSON, "{\"v\":1,\"k\":\"boardx\"}"));
		assertFalse(BoardBeacon.valid(GSON, "{\"v\":0,\"k\":\"board\"}"));
		assertFalse(BoardBeacon.valid(GSON, "{\"v\":1,\"k\":\"board\",\"pad\":\"" + new String(new char[400]).replace('\0', 'x')
			+ "\"}"));
	}

	@Test
	public void nothingHeardMeansNoBoard()
	{
		assertFalse(new BoardBeacon().fresh(System.currentTimeMillis()));
	}

	@Test
	public void freshUntilStaleThenPostsResume()
	{
		BoardBeacon b = new BoardBeacon();
		b.heard(10_000L);
		assertTrue(b.fresh(10_000L));
		assertTrue(b.fresh(10_000L + BoardBeacon.STALE_MS - 1));
		assertFalse(b.fresh(10_000L + BoardBeacon.STALE_MS));
	}

	@Test
	public void clearForgets()
	{
		BoardBeacon b = new BoardBeacon();
		b.heard(10_000L);
		b.clear();
		assertFalse(b.fresh(10_001L));
	}
}
