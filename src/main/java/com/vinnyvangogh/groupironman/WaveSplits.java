package com.vinnyvangogh.groupironman;

import java.util.*;

/**
 * Per-wave times, the way they travel and read. A run's splits are one string, whole seconds by wave: "42,38,0,51"
 * is wave 1 in 42s, wave 2 in 38s, wave 3 not timed, wave 4 in 51s. The personal bests are the same shape. Whole
 * seconds keep a 69-wave Inferno under the activity message's size limit.
 */
final class WaveSplits
{
	/** Most waves read; the Inferno has 69. */
	static final int MAX_WAVES = 70;

	private WaveSplits()
	{
	}

	/** Splits in milliseconds (index 0 is wave 1; 0 is untimed) as "42,38,0,51", trailing untimed waves cut. */
	static String encode(long[] ms)
	{
		int last = -1;
		for (int i = 0; i < ms.length && i < MAX_WAVES; i++)
		{
			if (ms[i] > 0)
			{
				last = i;
			}
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i <= last; i++)
		{
			out.append(i == 0 ? "" : ",").append(ms[i] > 0 ? Math.max(1, Math.round(ms[i] / 1000.0)) : 0);
		}
		return out.toString();
	}

	/** Milliseconds by wave from {@link #encode}; anything unreadable is untimed, and extra waves are dropped. */
	static long[] decode(String text)
	{
		if (text == null || text.isEmpty())
		{
			return new long[0];
		}
		String[] parts = text.split(",", -1);
		long[] out = new long[Math.min(parts.length, MAX_WAVES)];
		for (int i = 0; i < out.length; i++)
		{
			try
			{
				long s = Long.parseLong(parts[i].trim());
				out[i] = s > 0 && s < 86_400 ? s * 1000 : 0;
			}
			catch (NumberFormatException e)
			{
				out[i] = 0;
			}
		}
		return out;
	}

	/** "0:42", "12:05", "1:02:33": a time as a stopwatch shows it. */
	static String clock(long ms)
	{
		long s = Math.max(0, Math.round(ms / 1000.0));
		long h = s / 3600;
		long m = s % 3600 / 60;
		return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s % 60)
			: String.format(Locale.ROOT, "%d:%02d", m, s % 60);
	}

	/** The sum of the timed waves. */
	static long total(long[] ms)
	{
		long sum = 0;
		for (long v : ms)
		{
			sum += v;
		}
		return sum;
	}

	/** Wave-by-wave lines: "Wave 12: 0:42 (best 0:38)", and a "Total" last. Untimed waves are left out. */
	static List<String> rows(String splits, String bests)
	{
		long[] s = decode(splits);
		long[] b = decode(bests);
		List<String> out = new ArrayList<>();
		for (int i = 0; i < s.length; i++)
		{
			if (s[i] > 0)
			{
				String best = i < b.length && b[i] > 0 ? " (best " + clock(b[i]) + ")" : "";
				out.add("Wave " + (i + 1) + ": " + clock(s[i]) + best);
			}
		}
		if (!out.isEmpty())
		{
			out.add("Total: " + clock(total(s)));
		}
		return out;
	}

	/** "new best waves 31 0:20, 63 0:41": the waves of a finished run that set a personal best, at most {@code limit}. */
	static String newBests(long[] ms, long[] before, int limit)
	{
		List<String> out = new ArrayList<>();
		for (int i = 0; i < ms.length && out.size() < limit; i++)
		{
			if (ms[i] > 0 && (i >= before.length || before[i] == 0 || ms[i] < before[i]))
			{
				out.add((i + 1) + " " + clock(ms[i]));
			}
		}
		return out.isEmpty() ? "" : "new best waves " + String.join(", ", out);
	}
}
