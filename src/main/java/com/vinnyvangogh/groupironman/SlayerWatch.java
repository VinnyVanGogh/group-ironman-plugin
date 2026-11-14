package com.vinnyvangogh.groupironman;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Slayer task posts (Board live test 2026-11-10): "New Slayer task: 19 x Icefiends (Turael)" and
 * "Slayer task done: Icefiends (streak 1, 2 points)". Pure; client thread.
 *
 * <ul>
 * <li>A new task is the master's dialogue, a partner's assignment, or the gem naming a task other than the one
 * this session already knows. The gem only repeats a task, so the first gem check after a login is a baseline,
 * and the same task is never posted twice.</li>
 * <li>A task is done on the streak line that sends you back to a Slayer master, named from the "You have
 * completed your task!" line before it. That line alone posts after {@link #DONE_WAIT_TICKS} ticks.</li>
 * </ul>
 *
 * <p>A new task's {@link GroupEvent#number} is its amount, at least 1; a finished task's is 0. {@link
 * GroupEvent#detail} is who gave it, or the streak.
 */
final class SlayerWatch
{
	/** Ticks the "You have completed your task!" line waits for its streak line. */
	static final int DONE_WAIT_TICKS = 2;

	private final Consumer<GroupEvent> out;
	/** The held task, lower case: null until this session has seen one, empty for none. */
	private String task;
	private String taskName = "";
	/** A finished task still waiting for its streak line. */
	private String killed;
	private int killedWait;
	private String lastDialog = "";

	SlayerWatch(Consumer<GroupEvent> out)
	{
		this.out = out;
	}

	void reset()
	{
		task = null;
		taskName = "";
		killed = null;
		killedWait = 0;
		lastDialog = "";
	}

	/** One game chat line. */
	void chat(String message, long now, String me)
	{
		String name = EventChat.slayerKilled(message);
		if (name != null)
		{
			killed = name;
			killedWait = DONE_WAIT_TICKS;
			return;
		}
		long[] done = EventChat.slayerDone(message);
		if (done != null)
		{
			done(streak(done[0], done[1]), now, me);
			return;
		}
		if (EventChat.slayerCleared(message))
		{
			held("");
			return;
		}
		assigned(EventChat.slayerChat(message), now, me);
	}

	/** Each tick: the NPC dialogue's text and speaker, null text when none is open. */
	void dialog(String text, String npc, long now, String me)
	{
		String line = text == null ? "" : text;
		if (line.equals(lastDialog))
		{
			return;
		}
		lastDialog = line;
		if (!line.isEmpty())
		{
			assigned(EventChat.slayerDialog(line, npc), now, me);
		}
	}

	/** Each tick: a finished task whose streak line never came posts on its own. */
	void tick(long now, String me)
	{
		if (killed != null && --killedWait <= 0)
		{
			done("", now, me);
		}
	}

	private void assigned(EventChat.SlayerTask t, long now, String me)
	{
		if (t == null)
		{
			return;
		}
		String was = task;
		boolean changed = was != null && !was.equals(key(t.name));
		held(t.name);
		if ((t.fresh || changed) && t.amount > 0)
		{
			out.accept(GroupEvent.of(GroupEvent.Type.SLAYER, now, me).text(t.name).number(t.amount).detail(t.from)
				.build());
		}
	}

	private void done(String detail, long now, String me)
	{
		String name = killed != null ? killed : taskName;
		killed = null;
		held("");
		out.accept(GroupEvent.of(GroupEvent.Type.SLAYER, now, me).text(name.isEmpty() ? "task" : name)
			.detail(detail).build());
	}

	private void held(String name)
	{
		task = key(name);
		taskName = name;
	}

	private static String key(String name)
	{
		return name.trim().toLowerCase(Locale.ROOT);
	}

	/** "streak 1, 2 points"; just the streak when the line gives no points. */
	static String streak(long streak, long points)
	{
		String s = "streak " + streak;
		return points < 0 ? s : s + ", " + points + (points == 1 ? " point" : " points");
	}
}
