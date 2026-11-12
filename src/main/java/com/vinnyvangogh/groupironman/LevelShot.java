package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * When a level-up's screenshot is taken (Board 2026-11-11): not the moment the level registers, which shows the
 * chatbox before "Congratulations, you've just advanced your Smithing level. You are now level 4." lands, but once
 * that line for the level is in, or {@link #WAIT} after the level-up, whichever is first. A second level of the
 * skill while one waits joins it, so both posts get the newer frame, but never past {@link #MAX_WAIT} after the
 * first. Pure and clock-driven; client thread only.
 */
final class LevelShot
{
	/** How long after the level-up a shot waits for its chat line. */
	static final long WAIT = 2_000;
	/** The longest any shot waits, however many levels join it. */
	static final long MAX_WAIT = 3_000;

	/** "Congratulations, you've just advanced your Smithing level. You are now level 4." */
	private static final Pattern ADVANCED = Pattern.compile(
		"^Congratulations, you've just advanced (?:your|a|an) (.+?) level\\. You are now level (\\d+)\\.?$");

	/** One skill's shot to take and the sends waiting for its frame. */
	static final class Held
	{
		final String skill;
		final long first;
		long level;
		long due;
		final List<Consumer<byte[]>> sends = new ArrayList<>();

		private Held(String skill, long first)
		{
			this.skill = skill;
			this.first = first;
		}
	}

	/** Waiting shots, by skill. */
	private final Map<String, Held> held = new HashMap<>();
	/** The highest level each skill's chat line has shown, and when. */
	private final Map<String, long[]> seen = new HashMap<>();

	/**
	 * A level-up's post, ready but for its picture: {@code send} gets the frame once the chat shows the level (or
	 * the wait is over). {@code at} is when the level registered.
	 */
	void hold(String skill, long level, long at, Consumer<byte[]> send)
	{
		String key = key(skill);
		Held h = held.get(key);
		if (h == null)
		{
			h = new Held(key, at);
			held.put(key, h);
		}
		h.level = Math.max(h.level, level);
		h.due = Math.min(Math.max(h.due, at + WAIT), h.first + MAX_WAIT);
		h.sends.add(send);
	}

	/** A game message: true when it was a level-up line, now seen. */
	boolean chat(String message, long now)
	{
		Matcher m = ADVANCED.matcher(EventText.plain(message));
		if (!m.matches())
		{
			return false;
		}
		String key = key(m.group(1));
		long level = Long.parseLong(m.group(2));
		long[] was = seen.get(key);
		if (was == null || level >= was[0])
		{
			seen.put(key, new long[]{level, now});
		}
		return true;
	}

	/** The shots to take now, removed: their chat line is in, or their wait is over. */
	List<Held> due(long now)
	{
		List<Held> out = new ArrayList<>();
		for (Iterator<Held> it = held.values().iterator(); it.hasNext(); )
		{
			Held h = it.next();
			if (now >= h.due || shown(h))
			{
				out.add(h);
				it.remove();
			}
		}
		// A line older than any wait can no longer belong to a level still to come.
		seen.values().removeIf(s -> now - s[1] > MAX_WAIT);
		return out;
	}

	/** Every waiting shot, removed: no more ticks are coming (a hop or a logout). */
	List<Held> drain()
	{
		List<Held> out = new ArrayList<>(held.values());
		held.clear();
		seen.clear();
		return out;
	}

	/** Shutdown: nothing is sent. */
	void clear()
	{
		held.clear();
		seen.clear();
	}

	private boolean shown(Held h)
	{
		long[] s = seen.get(h.skill);
		return s != null && s[0] >= h.level;
	}

	/** Skill names compared as the chat spells them: the event's "Runecraft" is the chat's "Runecrafting". */
	private static String key(String skill)
	{
		String k = skill.toLowerCase(Locale.ROOT);
		return k.equals("runecrafting") ? "runecraft" : k;
	}
}
