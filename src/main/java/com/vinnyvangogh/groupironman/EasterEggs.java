package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The in-game side of the Easter eggs: the 67 and 69 level tags on banners and the Group box. The Discord lines
 * (every other egg) are Discord Relay's; GIM sends it the group's egg settings ({@code settings} message,
 * docs/contracts/discord-relay-v1.md). The phrasing is read from {@code easter-eggs.txt} and picked by
 * {@link #pick}, a fixed hash of a seed taken from the event, so every client (and Discord Relay, which uses the
 * same seed and file) picks the same line.
 */
final class EasterEggs
{
	/** The group's Easter egg settings. A threshold of 0 turns that egg off. */
	static final class Settings
	{
		final boolean on;
		final long legendAt;
		final long freeloaderAt;

		Settings(boolean on, long legendAt, long freeloaderAt)
		{
			this.on = on;
			this.legendAt = legendAt;
			this.freeloaderAt = freeloaderAt;
		}

		/** The group's Easter egg settings when it has them, else this member's own. */
		static Settings of(GroupSettingsSchema.Resolved values)
		{
			return new Settings(values.on("discordEasterEggs"), values.value("discordLegendAt"),
				values.value("discordFreeloaderAt"));
		}
	}

	/** Event types whose number is a level a {@link #LEVEL_NUMBERS} egg reads: one skill's, or the combat level. */
	private static final Set<GroupEvent.Type> LEVELS = Collections.unmodifiableSet(EnumSet.of(
		GroupEvent.Type.LEVEL_UP, GroupEvent.Type.COMBAT_LEVEL));
	/** Levels tagged in game, with their phrasings from the egg file. */
	private static final Map<Long, String[]> LEVEL_NUMBERS;

	static
	{
		Map<String, List<String>> file = new HashMap<>();
		for (String line : RegionNames.resource("easter-eggs.txt").split("\n"))
		{
			int eq = line.indexOf('=');
			if (eq > 0 && !line.startsWith("#"))
			{
				file.computeIfAbsent(line.substring(0, eq).trim(), k -> new ArrayList<>()).add(line.substring(eq + 1).trim());
			}
		}
		Map<Long, String[]> levels = new HashMap<>();
		levels.put(67L, file.getOrDefault("LEVEL_67", Collections.emptyList()).toArray(new String[0]));
		levels.put(69L, file.getOrDefault("LEVEL_69", Collections.emptyList()).toArray(new String[0]));
		LEVEL_NUMBERS = Collections.unmodifiableMap(levels);
	}

	private EasterEggs()
	{
	}

	/**
	 * The tag for this event's line in the group box and banners, or null: only the level-number eggs (67, 69) show
	 * in game. The same phrasing as the Discord post, picked from the same seed, with any emoji cut off: the game
	 * font has none.
	 */
	static String overlayTag(GroupEvent e, Settings settings)
	{
		String[] lines = settings.on && LEVELS.contains(e.type) ? LEVEL_NUMBERS.get(e.number) : null;
		if (lines == null || lines.length == 0)
		{
			return null;
		}
		String line = lines[pick(seed(e), lines.length)];
		int[] plain = line.codePoints().filter(c -> !Character.isSupplementaryCodePoint(c)).toArray();
		return new String(plain, 0, plain.length).trim();
	}

	/** An in-game line with this event's {@link #overlayTag} after it, or the line as it was. */
	static String tagged(String text, GroupEvent e, Settings settings)
	{
		String tag = overlayTag(e, settings);
		return tag == null ? text : text + " " + tag;
	}

	/** Which of {@code n} phrasings a seed picks: MurmurHash3's 64-bit finalizer, so nearby seeds spread out. */
	static int pick(long seed, int n)
	{
		long z = seed;
		z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
		z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
		z ^= z >>> 33;
		return (int) Math.floorMod(z, (long) n);
	}

	/** The same on every client that sees the event: the sender's time, the member and the kind. */
	static long seed(GroupEvent e)
	{
		return 31L * (31L * e.at + e.member.toLowerCase(Locale.ROOT).hashCode()) + e.type.code.hashCode();
	}
}
