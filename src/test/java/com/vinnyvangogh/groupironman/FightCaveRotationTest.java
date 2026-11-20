package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The 15 Fight Caves rotations, from the research table (docs/research/activity-detection.md §3.1 C). */
public class FightCaveRotationTest
{
	/** Wave 1 to 4 spawn points per rotation 1 to 15, as the research table lists them. */
	private static final String[][][] TABLE = {
		{{"C"}, {"NW", "SW"}, {"SW"}, {"SW", "SE"}},
		{{"C"}, {"S", "SE"}, {"SE"}, {"SE", "SW"}},
		{{"C"}, {"SE", "SW"}, {"SW"}, {"SW", "S"}},
		{{"NW"}, {"C", "S"}, {"S"}, {"S", "SE"}},
		{{"NW"}, {"C", "SE"}, {"SE"}, {"SE", "SW"}},
		{{"NW"}, {"SW", "SE"}, {"SE"}, {"SE", "S"}},
		{{"S"}, {"NW", "C"}, {"C"}, {"C", "S"}},
		{{"S"}, {"NW", "C"}, {"C"}, {"C", "SE"}},
		{{"S"}, {"SE", "SW"}, {"SW"}, {"SW", "C"}},
		{{"SE"}, {"S", "NW"}, {"NW"}, {"NW", "C"}},
		{{"SE"}, {"SW", "C"}, {"C"}, {"C", "NW"}},
		{{"SE"}, {"SW", "S"}, {"S"}, {"S", "NW"}},
		{{"SW"}, {"C", "NW"}, {"NW"}, {"NW", "SW"}},
		{{"SW"}, {"S", "NW"}, {"NW"}, {"NW", "C"}},
		{{"SW"}, {"SE", "S"}, {"S"}, {"S", "NW"}},
	};

	private static List<List<String>> seen(int rotation, int waves)
	{
		List<List<String>> out = new ArrayList<>();
		for (int w = 0; w < waves; w++)
		{
			out.add(new ArrayList<>(Arrays.asList(TABLE[rotation - 1][w])));
		}
		return out;
	}

	@Test
	public void everyRotationIsIdentifiedByWaveTwoExceptSevenAndEight()
	{
		for (int r = 1; r <= 15; r++)
		{
			List<Integer> got = FightCaveRotation.candidates(seen(r, 2));
			if (r == 7 || r == 8)
			{
				assertEquals("rotation " + r, Arrays.asList(7, 8), got);
				assertEquals("Rotation 7 or 8", FightCaveRotation.label(got));
			}
			else
			{
				assertEquals("rotation " + r, Arrays.asList(r), got);
				assertEquals("Rotation " + r, FightCaveRotation.label(got));
			}
		}
	}

	@Test
	public void waveFourSettlesSevenAndEight()
	{
		assertEquals(Arrays.asList(7), FightCaveRotation.candidates(seen(7, 4)));
		assertEquals(Arrays.asList(8), FightCaveRotation.candidates(seen(8, 4)));
	}

	@Test
	public void theRingReproducesTheTable()
	{
		for (int r = 1; r <= 15; r++)
		{
			for (int w = 1; w <= 4; w++)
			{
				List<String> want = new ArrayList<>(Arrays.asList(TABLE[r - 1][w - 1]));
				want.sort(String::compareTo);
				assertEquals("rotation " + r + " wave " + w, want, FightCaveRotation.expected(r, w));
			}
		}
	}

	@Test
	public void nothingSeenOrOneWaveIsUnknown()
	{
		assertEquals("unknown", FightCaveRotation.label(FightCaveRotation.candidates(new ArrayList<>())));
		assertEquals("unknown", FightCaveRotation.label(FightCaveRotation.candidates(seen(1, 1))));
	}

	@Test
	public void spawnsThatMatchNoRotationAreUnknownNotGuessed()
	{
		List<List<String>> odd = new ArrayList<>();
		odd.add(new ArrayList<>(Arrays.asList("NW")));
		odd.add(new ArrayList<>(Arrays.asList("NW", "NW")));
		assertTrue(FightCaveRotation.candidates(odd).isEmpty());
		assertEquals("unknown", FightCaveRotation.label(FightCaveRotation.candidates(odd)));
	}

	@Test
	public void aWaveWithTheWrongNumberOfSpawnsIsSkipped()
	{
		List<List<String>> partial = seen(5, 2);
		partial.get(1).remove(1);
		assertEquals("Rotation 5 stays among the candidates", true, FightCaveRotation.candidates(partial).contains(5));
	}

	@Test
	public void spawnPointsClassifyTheTemplateTiles()
	{
		assertEquals("NW", FightCaveRotation.point(11, 50));
		assertEquals("C", FightCaveRotation.point(33, 32));
		assertEquals("SE", FightCaveRotation.point(48, 26));
		assertEquals("S", FightCaveRotation.point(32, 15));
		assertEquals("SW", FightCaveRotation.point(13, 15));
		assertNull(FightCaveRotation.point(-1, 5));
		assertNull(FightCaveRotation.point(64, 5));
	}

	@Test
	public void saveAndRestoreRoundTripAndRejectGarbage()
	{
		assertEquals("7.8", FightCaveRotation.save(Arrays.asList(7, 8)));
		assertEquals(Arrays.asList(7, 8), FightCaveRotation.restore("7.8"));
		assertTrue(FightCaveRotation.restore("x.8").isEmpty());
		assertTrue(FightCaveRotation.restore("99").isEmpty());
		assertTrue(FightCaveRotation.restore("").isEmpty());
	}
}
