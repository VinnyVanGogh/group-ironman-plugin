package com.vinnyvangogh.groupironman;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntUnaryOperator;

/**
 * Builds this player's {@link AchievementState} from values the client thread reads: quest points, the quests the
 * game reports, and the diary tier varbits. Kept free of client types so it can be tested.
 */
final class AchievementCapture
{
	private AchievementCapture()
	{
	}

	/**
	 * Diary tier varbit labels ("Ardougne easy") and a varbit reader to one tier mask per area. A label that does
	 * not end in a known tier word is skipped; an area with no tier done still appears, as 0.
	 */
	static Map<String, Integer> diaries(Map<Integer, String> labels, IntUnaryOperator varbit)
	{
		Map<String, Integer> out = new TreeMap<>();
		for (Map.Entry<Integer, String> e : labels.entrySet())
		{
			String label = e.getValue();
			int space = label == null ? -1 : label.lastIndexOf(' ');
			if (space <= 0)
			{
				continue;
			}
			AchievementState.Tier tier = tier(label.substring(space + 1));
			if (tier == null)
			{
				continue;
			}
			String area = label.substring(0, space);
			int mask = out.getOrDefault(area, 0);
			if (varbit.applyAsInt(e.getKey()) != 0)
			{
				mask |= 1 << tier.ordinal();
			}
			out.put(area, mask);
		}
		return out;
	}

	static AchievementState state(int questPoints, Collection<String> finished, Collection<String> started,
		Map<Integer, String> diaryLabels, IntUnaryOperator varbit)
	{
		return AchievementState.of(questPoints, finished, started, diaries(diaryLabels, varbit));
	}

	private static AchievementState.Tier tier(String word)
	{
		try
		{
			return AchievementState.Tier.valueOf(word.toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}
}
