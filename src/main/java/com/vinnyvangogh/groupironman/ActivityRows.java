package com.vinnyvangogh.groupironman;

import java.util.*;
import java.util.List;

/**
 * What the Activities tab shows (docs/plans/activities.md §4.1), as plain rows so the Swing code only lays them out:
 * a card per member in an activity now ("LIVE NOW"), then recent completions and deaths from the Log ("RECENT
 * RUNS"), both behind the All / Bossing / Minigames filter.
 */
final class ActivityRows
{
	enum Filter
	{
		ALL("All"), BOSSING("Bossing"), MINIGAMES("Minigames");

		final String label;

		Filter(String label)
		{
			this.label = label;
		}

		boolean keeps(boolean bossing)
		{
			return this == ALL || (this == BOSSING) == bossing;
		}
	}

	/** How many recent runs are listed. */
	static final int MAX_RECENT = 25;
	private static final Set<GroupEvent.Type> RUNS = EnumSet.of(GroupEvent.Type.ACTIVITY_COMPLETE,
		GroupEvent.Type.ACTIVITY_DEATH);

	/** One member's live card. */
	static final class Card
	{
		final String member;
		final boolean you;
		/** "Alex" or "Alex (you)". */
		final String title;
		/** "The Inferno - wave 42/69", "solo - 38m", then glory and modifiers when there are any. */
		final List<String> lines;

		/** The run's wave-by-wave times with each wave's personal best, then the total; empty before one is done. */
		final List<String> splits;

		Card(String member, boolean you, List<String> lines, List<String> splits)
		{
			this.member = member;
			this.you = you;
			this.title = you ? member + " (you)" : member;
			this.lines = Collections.unmodifiableList(lines);
			this.splits = Collections.unmodifiableList(splits);
		}

		Card(String member, boolean you, List<String> lines)
		{
			this(member, you, lines, new ArrayList<>());
		}
	}

	private ActivityRows()
	{
	}

	/** Live cards, you first, then by name. */
	static List<Card> live(Map<String, ActivityState> activities, Filter filter, String self, long now)
	{
		List<Card> out = new ArrayList<>();
		for (Map.Entry<String, ActivityState> e : activities.entrySet())
		{
			ActivityState s = e.getValue();
			if (s.active() && filter.keeps(s.bossing()))
			{
				out.add(card(e.getKey(), e.getKey().equalsIgnoreCase(self), s, now));
			}
		}
		out.sort((a, b) -> a.you != b.you ? (a.you ? -1 : 1) : a.member.compareToIgnoreCase(b.member));
		return out;
	}

	private static Card card(String member, boolean you, ActivityState s, long now)
	{
		List<String> lines = new ArrayList<>();
		lines.add(s.phase.isEmpty() ? s.name : s.name + " - " + s.phase);
		String time = ActivityState.duration(s.elapsed(now));
		lines.add(s.mode.isEmpty() ? time : s.mode + " - " + time);
		if (!s.rotation.isEmpty())
		{
			lines.add(s.rotation);
		}
		if (s.waveMs > 0)
		{
			lines.add("This wave: " + WaveSplits.clock(s.waveElapsed(now)));
		}
		if (!s.stats.isEmpty())
		{
			lines.add(s.stats);
		}
		if (!s.mods.isEmpty())
		{
			lines.add("Modifiers: " + s.mods);
		}
		return new Card(member, you, lines, WaveSplits.rows(s.splits, s.bests));
	}

	/** "Done: Alex completed The Inferno, kill count 3 (1:58:12) - 9m ago", newest first. */
	static List<String> recent(List<GroupEvent> feed, Filter filter, ItemLookup lookup, long now)
	{
		List<GroupEvent> runs = new ArrayList<>();
		for (GroupEvent e : feed)
		{
			if (RUNS.contains(e.type) && filter.keeps(bossing(e.text)))
			{
				runs.add(e);
			}
		}
		runs.sort((a, b) -> Long.compare(b.at, a.at));
		List<String> out = new ArrayList<>();
		for (GroupEvent e : runs.subList(0, Math.min(MAX_RECENT, runs.size())))
		{
			String mark = e.type == GroupEvent.Type.ACTIVITY_DEATH ? "Died: " : "Done: ";
			out.add(mark + EventText.sentence(e, lookup) + " - " + BoostText.ago(now, e.at));
		}
		return out;
	}

	/** Every activity this build tracks is bossing; a name it does not know counts as a minigame. */
	static boolean bossing(String activity)
	{
		for (WaveTracker.Arena a : WaveTracker.Arena.values())
		{
			if (a.title.equalsIgnoreCase(activity))
			{
				return true;
			}
		}
		return false;
	}
}
