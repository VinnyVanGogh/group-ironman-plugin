package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

	/** {@code skills}: how many skills there are, OVERALL not counted. */
	ProgressTracker(int skills)
	{
		this.skills = skills;
	}

	void reset()
	{
		levels.clear();
		xp.clear();
		total = -1;
		combat = -1;
		qp = -1;
	}

	/** A skill's real level and XP now. */
	List<GroupEvent> skill(String skill, int level, long experience, long at, String member)
	{
		List<GroupEvent> out = new ArrayList<>();
		Integer before = levels.put(skill, level);
		Long xpBefore = xp.put(skill, experience);
		if (before != null && level > before)
		{
			out.add(GroupEvent.of(GroupEvent.Type.LEVEL_UP, at, member).text(skill).number(level).build());
			if (level >= MAX_LEVEL && before < MAX_LEVEL)
			{
				out.add(GroupEvent.of(GroupEvent.Type.MAXED, at, member).text(skill).number(MAX_LEVEL).build());
			}
		}
		if (xpBefore != null)
		{
			for (long milestone : XP_MILESTONES)
			{
				if (xpBefore < milestone && experience >= milestone)
				{
					out.add(GroupEvent.of(GroupEvent.Type.XP_MILESTONE, at, member).text(skill).number(milestone)
						.build());
				}
			}
		}
		totals(at, member, out);
		return out;
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
		return before > 0 && level > before
			? GroupEvent.of(GroupEvent.Type.COMBAT_LEVEL, at, member).number(level).build() : null;
	}

	/** Quest points now; a milestone event when they crossed a multiple of {@link #QP_STEP}. */
	GroupEvent questPoints(int points, long at, String member)
	{
		int before = qp;
		qp = points;
		return before >= 0 && points / QP_STEP > before / QP_STEP
			? GroupEvent.of(GroupEvent.Type.QUEST_POINTS, at, member).number(points / QP_STEP * QP_STEP).build()
			: null;
	}
}
