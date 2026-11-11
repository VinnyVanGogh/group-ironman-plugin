package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * What the panel's Settings tab shows, built without Swing so it is tested:
 * the header lines, the history in words, and the read-only opt-out grid the
 * leader and editors see (Board 2026-11-10: only a member changes their own
 * opt-outs).
 */
final class SettingsModel
{
	enum Cell
	{
		/** Posts this kind. */
		IN,
		/** Opted out, of this kind or of everything. */
		OUT,
		/** No announce heard from this member yet. */
		UNKNOWN
	}

	static final class GridRow
	{
		final GroupEvent.Type type;
		final String label;
		/** One per {@link Grid#members}, in order. */
		final List<Cell> cells;

		GridRow(GroupEvent.Type type, List<Cell> cells)
		{
			this.type = type;
			this.label = EventText.title(type);
			this.cells = Collections.unmodifiableList(cells);
		}
	}

	static final class Grid
	{
		final List<String> members;
		final List<GridRow> rows;

		Grid(List<String> members, List<GridRow> rows)
		{
			this.members = Collections.unmodifiableList(members);
			this.rows = Collections.unmodifiableList(rows);
		}
	}

	private SettingsModel()
	{
	}

	/** Event kind by member: who posts what. {@code prefs} is by display name, as announced. */
	static Grid grid(List<String> members, Map<String, MemberPrefs> prefs)
	{
		List<MemberPrefs> byMember = new ArrayList<>();
		for (String m : members)
		{
			byMember.add(find(prefs, m));
		}
		List<GridRow> rows = new ArrayList<>();
		for (GroupEvent.Type t : GroupEvent.Type.values())
		{
			List<Cell> cells = new ArrayList<>();
			for (MemberPrefs p : byMember)
			{
				cells.add(p == null ? Cell.UNKNOWN : p.posts(t) ? Cell.IN : Cell.OUT);
			}
			rows.add(new GridRow(t, cells));
		}
		return new Grid(new ArrayList<>(members), rows);
	}

	private static MemberPrefs find(Map<String, MemberPrefs> prefs, String name)
	{
		String key = GroupMembers.key(name);
		for (Map.Entry<String, MemberPrefs> e : prefs.entrySet())
		{
			if (GroupMembers.key(e.getKey()).equals(key))
			{
				return e.getValue();
			}
		}
		return null;
	}

	/** Who leads, this player's role, and the settings' version and last save. */
	static List<String> header(GroupGovernance.Role mine, GroupRoles roles, GroupSettings settings, long now)
	{
		List<String> out = new ArrayList<>();
		if (roles == null)
		{
			out.add("No group leader yet. Until one saves group settings, each member uses their own.");
			return out;
		}
		out.add("Leader: " + roles.leader + (roles.editors.isEmpty() ? ""
			: ". Editors: " + String.join(", ", roles.editors.keySet())));
		out.add("You are " + (mine == GroupGovernance.Role.LEADER ? "the leader"
			: mine == GroupGovernance.Role.EDITOR ? "an editor" : "a member: view only") + ".");
		if (settings == null)
		{
			out.add("No group settings saved yet: each member uses their own.");
		}
		else
		{
			out.add("Version " + settings.version + ", saved by " + settings.by + " " + BoostText.ago(now, settings.at)
				+ ".");
		}
		return out;
	}

	/** The history, newest first, in words. */
	static List<String> history(GroupSettings settings, long now)
	{
		List<String> out = new ArrayList<>();
		if (settings == null)
		{
			return out;
		}
		for (GroupSettings.Change c : settings.history)
		{
			out.add(c.by + " " + change(c) + " (" + BoostText.ago(now, c.at) + ")");
		}
		return out;
	}

	static String change(GroupSettings.Change c)
	{
		if (GroupSettings.MANY.equals(c.key))
		{
			return "set " + c.to + " settings";
		}
		if (c.key.startsWith("resigned:"))
		{
			return "re-signed the settings " + c.key.substring("resigned:".length()) + " saved";
		}
		GroupSettingsSchema.Setting s = GroupSettingsSchema.get(c.key);
		if (s == null)
		{
			return "changed " + c.key;
		}
		String where = s.section.equals(GroupSettingsSchema.THRESHOLDS) || s.section.equals(GroupSettingsSchema.TIMING)
			? "" : " (" + s.section + ")";
		if (s.flag)
		{
			return "turned " + (c.to != 0 ? "on" : "off") + " " + s.label + where;
		}
		return "changed " + s.label + " from " + c.from + " to " + c.to;
	}
}
