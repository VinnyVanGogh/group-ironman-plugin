package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.List;

/**
 * Which of the Fight Caves' 15 spawn rotations a run is on, read from where the first waves' monsters appear.
 * Every rotation is a shift of one 15-step ring of spawn points; the wiki numbers them 1 to 15
 * (https://oldschool.runescape.wiki/w/TzHaar_Fight_Cave/Rotations, ring and per-rotation spawns in
 * docs/research/activity-detection.md §3.1 C, cross-checked with the Hub plugin damencs/spawn-predictor).
 *
 * <p>Only the player's own run, only the spawn point of each wave-start monster in waves 1 to 4; no kill order,
 * safespot or prayer cue (the precedent is RuneLite's Fight Cave info and the Hub's Fight Cave Waves).
 * Rotations 7 and 8 look alike until wave 4, so a run on them reads "Rotation 7 or 8" until then. Anything that
 * matches no rotation, or too little seen to narrow it, is "unknown", never a guess.
 */
final class FightCaveRotation
{
	/** Spawn points: north-west, centre, south-east, south, south-west. */
	static final String NW = "NW";
	static final String C = "C";
	static final String SE = "SE";
	static final String S = "S";
	static final String SW = "SW";

	static final String UNKNOWN = "unknown";
	/** Waves whose spawns identify a rotation. */
	static final int LAST_WAVE = 4;

	private static final String[] RING = {SE, SW, C, NW, SW, SE, S, NW, C, SE, SW, S, NW, C, S};
	/** Ring index of each wiki rotation's wave 1: rotation 1 is index 0 here. */
	private static final int[] START = {2, 13, 8, 12, 7, 3, 11, 6, 14, 5, 0, 9, 1, 10, 4};
	/** Monsters at the start of waves 1 to 4: one, two, one, two (a level 22, two 22s, a 45, a 45 and a 22). */
	private static final int[] COUNT = {1, 2, 1, 2};
	/**
	 * Where in the ring each of those waves starts, from the research table's per-rotation spawns (wave 4 starts
	 * where wave 3's 45 did). Waves 1 to 3 match the wiki's example (rotation 1: centre, then NW and SW); wave 4 is
	 * from the table only, so a run that disagrees with it reads "unknown" rather than a wrong number.
	 */
	private static final int[] OFFSET = {0, 1, 2, 2};

	private FightCaveRotation()
	{
	}

	/** The spawn point for a template-region tile (x and y modulo 64), or null when it is none of the five. */
	static String point(int sceneX, int sceneY)
	{
		if (sceneX < 0 || sceneX > 63 || sceneY < 0 || sceneY > 63)
		{
			return null;
		}
		if (sceneX < 22 && sceneY >= 40)
		{
			return NW;
		}
		if (sceneX >= 22 && sceneX <= 42 && sceneY >= 24 && sceneY <= 40)
		{
			return C;
		}
		if (sceneX > 42 && sceneY >= 20)
		{
			return SE;
		}
		if (sceneX >= 22 && sceneX <= 42 && sceneY < 24)
		{
			return S;
		}
		if (sceneX < 22 && sceneY < 40)
		{
			return SW;
		}
		return null;
	}

	/** The spawn points rotation {@code rotation} (1 to 15) uses in {@code wave} (1 to 4), sorted. */
	static List<String> expected(int rotation, int wave)
	{
		List<String> out = new ArrayList<>();
		for (int k = 0; k < COUNT[wave - 1]; k++)
		{
			out.add(RING[(START[rotation - 1] + OFFSET[wave - 1] + k) % RING.length]);
		}
		out.sort(String::compareTo);
		return out;
	}

	/**
	 * The rotations that fit what was seen. {@code seen.get(i)} is wave i + 1's spawn points, as many waves as
	 * have been seen; a wave with the wrong number of spawns rules nothing in or out and is skipped.
	 */
	static List<Integer> candidates(List<List<String>> seen)
	{
		List<Integer> out = new ArrayList<>();
		for (int r = 1; r <= START.length; r++)
		{
			if (fits(r, seen))
			{
				out.add(r);
			}
		}
		return out;
	}

	private static boolean fits(int rotation, List<List<String>> seen)
	{
		for (int w = 0; w < seen.size() && w < LAST_WAVE; w++)
		{
			List<String> got = new ArrayList<>(seen.get(w));
			if (got.size() != COUNT[w])
			{
				continue;
			}
			got.sort(String::compareTo);
			if (!got.equals(expected(rotation, w + 1)))
			{
				return false;
			}
		}
		return true;
	}

	/** "Rotation 5", "Rotation 7 or 8", or {@link #UNKNOWN}: more than two candidates is too little to say. */
	static String label(List<Integer> candidates)
	{
		if (candidates.size() == 1)
		{
			return "Rotation " + candidates.get(0);
		}
		if (candidates.size() == 2)
		{
			return "Rotation " + candidates.get(0) + " or " + candidates.get(1);
		}
		return UNKNOWN;
	}

	/** The candidates as saved: "7.8", empty for none seen. */
	static String save(List<Integer> candidates)
	{
		StringBuilder out = new StringBuilder();
		for (int c : candidates)
		{
			out.append(out.length() == 0 ? "" : ".").append(c);
		}
		return out.toString();
	}

	/** What {@link #save} kept; unreadable or out-of-range numbers are dropped. */
	static List<Integer> restore(String saved)
	{
		List<Integer> out = new ArrayList<>();
		if (saved == null || saved.isEmpty())
		{
			return out;
		}
		for (String p : saved.split("\\."))
		{
			try
			{
				int n = Integer.parseInt(p);
				if (n >= 1 && n <= START.length && !out.contains(n))
				{
					out.add(n);
				}
			}
			catch (NumberFormatException e)
			{
				return new ArrayList<>();
			}
		}
		return out;
	}
}
