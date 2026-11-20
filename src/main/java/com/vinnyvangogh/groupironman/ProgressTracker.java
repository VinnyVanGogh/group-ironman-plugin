package com.vinnyvangogh.groupironman;

import java.util.*;
import java.util.List;

/**
 * Levels, XP, total, combat level and quest points, turned into progress
 * events by comparing each new value with the last one this session.
 *
 * <p>The game sends every skill when the player logs in. The first value of
 * each is only remembered, never an event, so a login does not announce
 * every level the player already had; {@link #reset} on logout starts over
 * (another account may log in next). The total is only judged once every
 * skill has a value, for the same reason.
 */
final class ProgressTracker
{
	static final long[] XP_MILESTONES = {1_000_000L, 10_000_000L, 200_000_000L};
	static final int TOTAL_STEP = 100;
	static final int QP_STEP = 25;
	static final int MAX_LEVEL = 99;

	private final int skills;
	private final Map<String, Integer> levels = new HashMap<>();
	private final Map<String, Long> xp = new HashMap<>();
	private int total = -1;
	private int combat = -1;
	private int qp = -1;
	/** False while a login settles ({@link LoginGate}): every value only records. A new tracker counts. */
	private boolean counting = true;

	/** {@code skills}: how many skills there are, OVERALL not counted. */
	ProgressTracker(int skills)
	{
		this.skills = skills;
	}

	/** Another account may log in next: forget every value, and count nothing until {@link #settle}. */
	void reset()
	{
		levels.clear();
		xp.clear();
		total = -1;
		combat = -1;
		qp = -1;
		counting = false;
	}

	/** A login is settling: values only record until {@link #settle}. */
	void quiet()
	{
		counting = false;
	}

	/** The login has settled: a value that goes up from here on is progress. */
	void settle()
	{
		counting = true;
	}

	/** A skill's real level and XP now. */
	List<GroupEvent> skill(String skill, int level, long experience, long at, String member)
	{
		List<GroupEvent> out = new ArrayList<>();
		Integer before = levels.put(skill, level);
		Long xpBefore = xp.put(skill, experience);
		levelUp(skill, level, before, at, member, out);
		milestones(skill, experience, xpBefore, at, member, out);
		// The total is kept while quiet too, so the first level after the login is judged against it.
		totals(at, member, out);
		if (!counting)
		{
			out.clear();
		}
		return out;
	}

	private static void levelUp(String skill, int level, Integer before, long at, String member, List<GroupEvent> out)
	{
		if (before == null || level <= before)
		{
			return;
		}
		out.add(GroupEvent.of(GroupEvent.Type.LEVEL_UP, at, member).text(skill).number(level).previous(before).build());
		if (level >= MAX_LEVEL && before < MAX_LEVEL)
		{
			out.add(GroupEvent.of(GroupEvent.Type.MAXED, at, member).text(skill).number(MAX_LEVEL).build());
		}
	}

	private static void milestones(String skill, long experience, Long xpBefore, long at, String member,
		List<GroupEvent> out)
	{
		if (xpBefore == null)
		{
			return;
		}
		for (long milestone : XP_MILESTONES)
		{
			if (xpBefore < milestone && experience >= milestone)
			{
				out.add(GroupEvent.of(GroupEvent.Type.XP_MILESTONE, at, member).text(skill).number(milestone).build());
			}
		}
	}

	private void totals(long at, String member, List<GroupEvent> out)
	{
		if (levels.size() < skills)
		{
			return;
		}
		int now = 0;
		for (int l : levels.values())
		{
			now += l;
		}
		int before = total;
		total = now;
		if (before < 0 || now <= before)
		{
			return;
		}
		if (now / TOTAL_STEP > before / TOTAL_STEP)
		{
			out.add(GroupEvent.of(GroupEvent.Type.TOTAL_LEVEL, at, member).number(now / TOTAL_STEP * TOTAL_STEP)
				.build());
		}
		int max = skills * MAX_LEVEL;
		if (now >= max && before < max)
		{
			out.add(GroupEvent.of(GroupEvent.Type.MAXED, at, member).text("total").number(now).build());
		}
	}

	/** The combat level now; null when it did not go up. */
	GroupEvent combat(int level, long at, String member)
	{
		int before = combat;
		combat = level;
		return counting && before > 0 && level > before
			? GroupEvent.of(GroupEvent.Type.COMBAT_LEVEL, at, member).number(level).build() : null;
	}

	/** Quest points now; a milestone event when they crossed a multiple of {@link #QP_STEP}. */
	GroupEvent questPoints(int points, long at, String member)
	{
		int before = qp;
		qp = points;
		return counting && before >= 0 && points / QP_STEP > before / QP_STEP
			? GroupEvent.of(GroupEvent.Type.QUEST_POINTS, at, member).number(points / QP_STEP * QP_STEP).build()
			: null;
	}
}
