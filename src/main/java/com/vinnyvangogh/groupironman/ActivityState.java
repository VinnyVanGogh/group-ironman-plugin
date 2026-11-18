package com.vinnyvangogh.groupironman;

import java.util.concurrent.TimeUnit;

/**
 * What one member is doing right now in a tracked activity (docs/plans/activities.md §5.1): "Fight Caves, wave
 * 37/63, solo". Display-ready: every string is formatted by the sender, so a receiver on an older build still shows
 * activities it has no detector for. Immutable; {@link #NONE} is "not in an activity".
 *
 * <p>Only coarse where-and-how-far status: the activity, the wave, how long, player-chosen modifiers. Never a boss
 * mechanic, attack, prayer or hazard (§1.3).
 */
final class ActivityState
{
	/** Bossing, for the Activities tab's filter. Fight Caves, Inferno and Colosseum are all this. */
	static final String BOSS = "boss";
	static final String MINIGAME = "minigame";
	/** Not in an activity: what a clear frame decodes to. */
	static final ActivityState NONE = new Builder("", 0).build();

	/** Slug, e.g. {@code fight_caves}; empty when not in an activity. */
	final String id;
	/** {@link #BOSS} or {@link #MINIGAME}. */
	final String cat;
	/** "Fight Caves". */
	final String name;
	/** "solo", or "duo with Sam" from {@link TeamSummary}. */
	final String mode;
	/** "wave 37/63". */
	final String phase;
	/** The live board's words, coarse so the board is not edited every wave: "in Fight Caves, wave 35". */
	final String board;
	final int wave;
	final int max;
	/** When the run started, the sender's clock. */
	final long startedAt;
	/** Time spent in the run as of {@link #at} (pauses while logged out). */
	final long elapsedMs;
	/** "14,200 glory"; empty when none. */
	final String stats;
	/** "Mantimayhem II, Myopia I"; empty when none. */
	final String mods;
	/** Named group members and a count of anyone else; empty when solo. */
	final String team;
	/** When this state was true, the sender's clock. */
	final long at;
	private final String overlay;

	private ActivityState(Builder b)
	{
		id = b.id;
		cat = b.cat;
		name = b.name;
		mode = b.mode;
		phase = b.phase;
		board = b.board;
		wave = b.wave;
		max = b.max;
		startedAt = b.startedAt;
		elapsedMs = Math.max(0, b.elapsedMs);
		stats = b.stats;
		mods = b.mods;
		team = b.team;
		at = b.at;
		overlay = "  " + (phase.isEmpty() ? name : name + ": " + phase);
	}

	boolean active()
	{
		return !id.isEmpty();
	}

	/** Bossing (true) or a minigame, for the Activities tab's filter. */
	boolean bossing()
	{
		return !MINIGAME.equals(cat);
	}

	/** Time in the run at {@code now}: the elapsed time sent, plus the time since it was sent. */
	long elapsed(long now)
	{
		return elapsedMs + Math.max(0, now - at);
	}

	/** The Group tab card's line: "Fight Caves - wave 37/63 - solo". */
	String cardLine()
	{
		StringBuilder out = new StringBuilder(name);
		if (!phase.isEmpty())
		{
			out.append(" - ").append(phase);
		}
		if (!mode.isEmpty())
		{
			out.append(" - ").append(mode);
		}
		return out.toString();
	}

	/** The in-game group box's sub-line: "Fight Caves: wave 37/63". Made once here, never per frame. */
	String overlayLine()
	{
		return overlay;
	}

	/** "38m" or "1h 4m" or "42s": how long a run has gone on. */
	static String duration(long ms)
	{
		long s = TimeUnit.MILLISECONDS.toSeconds(Math.max(0, ms));
		if (s < 60)
		{
			return s + "s";
		}
		long m = s / 60;
		return m < 60 ? m + "m" : m / 60 + "h " + m % 60 + "m";
	}

	Builder toBuilder()
	{
		return new Builder(id, at).cat(cat).name(name).mode(mode).phase(phase).board(board).wave(wave).max(max)
			.startedAt(startedAt).elapsedMs(elapsedMs).stats(stats).mods(mods).team(team);
	}

	static final class Builder
	{
		private final String id;
		private final long at;
		private String cat = BOSS;
		private String name = "";
		private String mode = "";
		private String phase = "";
		private String board = "";
		private int wave;
		private int max;
		private long startedAt;
		private long elapsedMs;
		private String stats = "";
		private String mods = "";
		private String team = "";

		Builder(String id, long at)
		{
			this.id = id == null ? "" : id;
			this.at = at;
		}

		Builder cat(String v)
		{
			cat = MINIGAME.equals(v) ? MINIGAME : BOSS;
			return this;
		}

		Builder name(String v)
		{
			name = orEmpty(v);
			return this;
		}

		Builder mode(String v)
		{
			mode = orEmpty(v);
			return this;
		}

		Builder phase(String v)
		{
			phase = orEmpty(v);
			return this;
		}

		Builder board(String v)
		{
			board = orEmpty(v);
			return this;
		}

		Builder wave(int v)
		{
			wave = v;
			return this;
		}

		Builder max(int v)
		{
			max = v;
			return this;
		}

		Builder startedAt(long v)
		{
			startedAt = v;
			return this;
		}

		Builder elapsedMs(long v)
		{
			elapsedMs = v;
			return this;
		}

		Builder stats(String v)
		{
			stats = orEmpty(v);
			return this;
		}

		Builder mods(String v)
		{
			mods = orEmpty(v);
			return this;
		}

		Builder team(String v)
		{
			team = orEmpty(v);
			return this;
		}

		private static String orEmpty(String v)
		{
			return v == null ? "" : v;
		}

		ActivityState build()
		{
			return new ActivityState(this);
		}
	}
}
