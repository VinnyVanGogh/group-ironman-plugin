package com.vinnyvangogh.groupironman;

import java.util.Locale;
import java.util.function.*;
import java.util.regex.*;

/**
 * This player's run through a wave activity: the TzHaar Fight Cave (63 waves), the Inferno (69) and the Fortis
 * Colosseum (12), as docs/plans/activities.md §2.4 A1/A2 specify. Pure: the engine feeds it the template region
 * (unwrapped with {@code WorldPoint.fromLocalInstance}), game messages, the player's own death and logouts, and it
 * keeps an {@link ActivityState} and emits the four activity events.
 *
 * <p>Only coarse status is read: the wave the game prints, time spent, completion and defeat. No attack, prayer,
 * spawn or hazard is ever tracked (§1.3).
 *
 * <p>Messages, from the base client and Hub plugins (tags removed with {@link EventText#plain}):
 * {@code Wave: N} starts each wave in all three (RuneLite's former FightCavePlugin {@code WAVE_PATTERN}; the Hub
 * fortis-colosseum {@code ColosseumStateTracker} reads the same line), {@code Wave N completed!} ends a Colosseum
 * wave and "Sol Heredit jumps down from his seat" starts wave 12 (fortis-colosseum), and the kill count line
 * ({@link EventText#killCount}) completes a run. "You have been defeated!" is the design doc's defeat line and is
 * not yet confirmed in game; the player's own death covers a defeat without it.
 */
final class WaveTracker
{
	enum Arena
	{
		FIGHT_CAVES("fight_caves", "Fight Caves", 9551, 63, "TzTok-Jad", 63, 31, 37, 50, 62, 63),
		INFERNO("inferno", "The Inferno", 9043, 69, "TzKal-Zuk", 68, 30, 50, 60, 66, 67, 68, 69),
		COLOSSEUM("colosseum", "Fortis Colosseum", 7216, 12, "Sol Heredit", 12, 4, 8, 11, 12);

		final String id;
		final String title;
		/** The template region ({@code regions.txt}), never the instance's own. */
		final int region;
		final int max;
		/** The kill count line's name that completes the run. */
		final String boss;
		/** From this wave on, every wave is a boss wave and always a milestone: Jad 63, Triple Jads 68, Sol 12. */
		final int bossFrom;

		/** The waves that always post a milestone (Board, 2026-11-18). */
		final int[] milestones;

		Arena(String id, String title, int region, int max, String boss, int bossFrom, int... milestones)
		{
			this.milestones = milestones;
			this.id = id;
			this.title = title;
			this.region = region;
			this.max = max;
			this.boss = boss;
			this.bossFrom = bossFrom;
		}

		static Arena at(int region)
		{
			for (Arena a : values())
			{
				if (a.region == region)
				{
					return a;
				}
			}
			return null;
		}

		static Arena byId(String id)
		{
			for (Arena a : values())
			{
				if (a.id.equals(id))
				{
					return a;
				}
			}
			return null;
		}

		boolean bossWave(int wave)
		{
			return wave >= bossFrom;
		}

		boolean milestone(int wave)
		{
			for (int m : milestones)
			{
				if (m == wave)
				{
					return true;
				}
			}
			return false;
		}
	}

	static final Pattern WAVE = Pattern.compile("^Wave: (\\d+)$");
	static final Pattern WAVE_DONE = Pattern.compile("^Wave (\\d+) completed!");
	static final String SOL_WAVE = "Sol Heredit jumps down from his seat";
	static final Pattern DEFEATED = Pattern.compile("^You have been defeated!?$");
	/** The run's time on a completion: "Duration: 1:02:33" (tags removed), never a Colosseum wave's own time. */
	static final Pattern DURATION = Pattern.compile("(?i)duration: ?([0-9:.]+)");
	/** A death this soon after a defeat line is the same death. */
	static final long SAME_DEATH_MS = 3_000;
	/** The live board moves only every this many waves (and on boss waves), so its message is not edited each wave. */
	static final int BOARD_STEP = 5;

	private final Consumer<GroupEvent> emit;
	private final Supplier<String> mode;

	private Arena arena;
	private int wave;
	private int completed;
	private long startedAt;
	/** Time in the run before {@link #resumedAt}. */
	private long activeMs;
	/** When the run's clock last started; 0 while paused (logged out) or restored and not yet back in. */
	private long resumedAt;
	private String runMode = "";
	private String mods = "";
	private long glory;
	private String duration;
	private long pendingKc;
	private long defeatedAt;
	private int region = -1;
	private int world;
	private int every;
	private Predicate<Arena> enabled = a -> true;
	private boolean changed;

	/** {@code emit} takes the activity events; {@code mode} is "solo" or the team, read when a run starts. */
	WaveTracker(Consumer<GroupEvent> emit, Supplier<String> mode)
	{
		this.emit = emit;
		this.mode = mode;
	}

	/** "Post every N waves"; 0 posts boss waves only. */
	void every(int n)
	{
		every = Math.max(0, n);
	}

	/** Each tick: where the player stands (template region, -1 while loading) and their world. */
	void region(int regionId, int world, long now, String me)
	{
		this.world = world;
		if (regionId < 0)
		{
			return;
		}
		region = regionId;
		if (arena == null)
		{
			return;
		}
		if (regionId == arena.region)
		{
			if (resumedAt == 0)
			{
				resumedAt = now;
				changed = true;
			}
			return;
		}
		leave(now, me);
	}

	/** Out of the arena alive: a Colosseum claim after a cleared wave, otherwise a quiet exit. */
	private void leave(long now, String me)
	{
		if (arena == Arena.COLOSSEUM && completed > 0 && resumedAt > 0)
		{
			post(event(GroupEvent.Type.ACTIVITY_COMPLETE, now, me).number(0)
				.detail("claimed loot after wave " + completed).build());
		}
		clear();
	}

	/** A game message: waves, a Colosseum wave cleared, the run's duration, a kill count, a defeat. */
	void chat(String message, long now, String me)
	{
		String text = EventText.plain(message);
		Matcher m = WAVE.matcher(text);
		if (m.matches())
		{
			wave(Integer.parseInt(m.group(1)), now, me);
			return;
		}
		if (text.contains(SOL_WAVE))
		{
			wave(Arena.COLOSSEUM.max, now, me);
			return;
		}
		Matcher done = WAVE_DONE.matcher(text);
		if (done.find())
		{
			completed = Integer.parseInt(done.group(1));
			return;
		}
		if (DEFEATED.matcher(text).matches())
		{
			defeat(now, me);
			return;
		}
		other(text);
	}

	/** A kill count that completes the run, or the run's duration line. */
	private void other(String text)
	{
		if (arena == null)
		{
			return;
		}
		EventText.Count kc = EventText.killCount(text);
		if (kc != null && kc.name.equalsIgnoreCase(arena.boss))
		{
			pendingKc = Math.max(1, kc.count);
			return;
		}
		Matcher d = DURATION.matcher(text);
		if (d.find())
		{
			duration = d.group(1);
		}
	}

	private void wave(int n, long now, String me)
	{
		Arena here = Arena.at(region);
		if (here == null || n < 1 || n > here.max)
		{
			return;
		}
		if (arena != here || n < wave)
		{
			start(here, n, now, me);
		}
		resumedAt = resumedAt == 0 ? now : resumedAt;
		// A wave printed again after a relog is not a second milestone.
		boolean fresh = n != wave;
		wave = n;
		changed = true;
		if (fresh && milestone(here, n))
		{
			post(event(GroupEvent.Type.ACTIVITY_WAVE, now, me).number(n).detail(phase()).build());
		}
	}

	/** The arena's own milestone waves, plus every {@link #every} waves if set; never wave 1, which is the start. */
	private boolean milestone(Arena a, int n)
	{
		return n > 1 && (a.milestone(n) || every > 0 && n % every == 0);
	}

	/** Which arenas post events at all; the run itself is still tracked and shown. Default: all. */
	void enabled(Predicate<Arena> on)
	{
		enabled = on == null ? a -> true : on;
	}

	private void post(GroupEvent e)
	{
		if (arena != null && enabled.test(arena))
		{
			emit.accept(e);
		}
	}

	private void start(Arena a, int n, long now, String me)
	{
		clear();
		arena = a;
		startedAt = now;
		resumedAt = now;
		String m = mode.get();
		runMode = m == null ? "" : m;
		if (n == 1)
		{
			post(event(GroupEvent.Type.ACTIVITY_START, now, me).number(1).detail(runMode).build());
		}
	}

	/**
	 * The player's own death. True when it was inside a run (now, or a defeat line just before it): the
	 * activity death covers it, so the plain death post can be skipped.
	 */
	boolean died(long now, String me)
	{
		if (arena != null)
		{
			boolean posts = enabled.test(arena);
			defeat(now, me);
			return posts;
		}
		return defeatedAt > 0 && now - defeatedAt <= SAME_DEATH_MS;
	}

	private void defeat(long now, String me)
	{
		if (arena == null)
		{
			return;
		}
		post(event(GroupEvent.Type.ACTIVITY_DEATH, now, me).number(wave)
			.detail(join(phase(), ActivityState.duration(elapsed(now)), mods)).world(world).location(arena.title)
			.build());
		defeatedAt = now;
		clear();
	}

	/** Each tick: a completion, once its duration line had the chance to arrive on the same tick. */
	void tick(long now, String me)
	{
		if (arena == null || pendingKc == 0)
		{
			return;
		}
		String time = duration != null ? duration : ActivityState.duration(elapsed(now));
		post(event(GroupEvent.Type.ACTIVITY_COMPLETE, now, me).number(pendingKc)
			.detail(join(time, stats(), mods)).build());
		clear();
	}

	/** Logged out: the run's clock stops until the player is back in the arena. */
	void pause(long now)
	{
		if (arena != null && resumedAt > 0)
		{
			activeMs += now - resumedAt;
			resumedAt = 0;
			changed = true;
		}
	}

	/** The Colosseum's chosen modifiers ({@link ColosseumModifiers}) and glory, read while in a Colosseum run. */
	void colosseum(String chosen, long gloryNow)
	{
		if (arena != Arena.COLOSSEUM)
		{
			return;
		}
		String m = chosen == null ? "" : chosen;
		if (!m.equals(mods) || gloryNow != glory)
		{
			mods = m;
			glory = gloryNow;
			changed = true;
		}
	}

	private void clear()
	{
		changed |= arena != null;
		arena = null;
		wave = 0;
		completed = 0;
		activeMs = 0;
		resumedAt = 0;
		mods = "";
		glory = 0;
		duration = null;
		pendingKc = 0;
		runMode = "";
	}

	/** True once after anything the state shows changed. */
	boolean takeChanged()
	{
		boolean c = changed;
		changed = false;
		return c;
	}

	boolean active()
	{
		return arena != null;
	}

	long elapsed(long now)
	{
		return activeMs + (resumedAt > 0 ? Math.max(0, now - resumedAt) : 0);
	}

	/** The state to show and send. */
	ActivityState state(long now)
	{
		if (arena == null)
		{
			return new ActivityState.Builder("", now).build();
		}
		return new ActivityState.Builder(arena.id, now).cat(ActivityState.BOSS).name(arena.title).mode(runMode)
			.phase(wave > 0 ? phase() : "").board(board()).wave(wave).max(arena.max).startedAt(startedAt)
			.elapsedMs(elapsed(now)).stats(stats()).mods(mods).build();
	}

	private String phase()
	{
		return "wave " + wave + "/" + arena.max;
	}

	/** "in Fight Caves, wave 35": the wave rounded down to {@link #BOARD_STEP}, a boss wave exact. */
	private String board()
	{
		int shown = arena.bossWave(wave) ? wave : wave - wave % BOARD_STEP;
		return "in " + arena.title + (shown > 0 ? ", wave " + shown : "");
	}

	private String stats()
	{
		return glory > 0 ? String.format(Locale.ROOT, "%,d glory", glory) : "";
	}

	private GroupEvent.Builder event(GroupEvent.Type type, long now, String me)
	{
		return GroupEvent.of(type, now, me).text(arena.title);
	}

	private static String join(String... parts)
	{
		StringBuilder out = new StringBuilder();
		for (String p : parts)
		{
			if (p != null && !p.isEmpty())
			{
				out.append(out.length() == 0 ? "" : ", ").append(p);
			}
		}
		return out.toString();
	}

	/** The run, kept in the RuneLite profile so a logout mid-run resumes: "inferno,42,startedAt,activeMs,completed". */
	String save()
	{
		if (arena == null)
		{
			return "";
		}
		return arena.id + "," + wave + "," + startedAt + "," + activeMs + "," + completed;
	}

	/** A run {@link #save} kept; paused until the player is back in its arena. Anything unreadable is ignored. */
	void restore(String saved)
	{
		String[] p = saved == null ? new String[0] : saved.split(",");
		Arena a = p.length == 5 ? Arena.byId(p[0]) : null;
		if (a == null)
		{
			return;
		}
		try
		{
			int w = Integer.parseInt(p[1]);
			long started = Long.parseLong(p[2]);
			long active = Long.parseLong(p[3]);
			int done = Integer.parseInt(p[4]);
			clear();
			arena = a;
			wave = Math.max(0, Math.min(a.max, w));
			startedAt = started;
			activeMs = Math.max(0, active);
			completed = Math.max(0, done);
			runMode = mode.get() == null ? "" : mode.get();
		}
		catch (NumberFormatException e)
		{
			clear();
		}
	}

	/** A new account: nothing carries over. */
	void reset()
	{
		clear();
		defeatedAt = 0;
		region = -1;
	}
}
