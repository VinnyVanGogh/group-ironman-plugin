package com.vinnyvangogh.groupironman;

import java.util.*;
import java.util.List;

/**
 * Who a player is doing something with (docs/plans/activities.md §2.2): this group's members by name, everyone else
 * only as a count, never named (§1.3, "Transmitting names of non-group players"). One place for it: the raid unique's
 * "with Sam +1 other" ({@link EventWatchers}) and the activities' solo / duo / N-man / mass label.
 */
final class TeamSummary
{
	/** From this many players a place is a mass, not a team. */
	static final int MASS_FROM = 6;

	/** Group members with the player, in the order they were seen. */
	final List<String> named;
	/** Everyone else with the player: a count only. */
	final int others;

	private TeamSummary(List<String> named, int others)
	{
		this.named = Collections.unmodifiableList(named);
		this.others = others;
	}

	/** {@code present} is everyone seen here, the player included; {@code group} this group's in-game roster. */
	static TeamSummary of(String me, Collection<String> group, Collection<String> present)
	{
		Set<String> members = new HashSet<>();
		for (String g : group)
		{
			members.add(GroupMembers.key(g));
		}
		String self = GroupMembers.key(me == null ? "" : me);
		List<String> named = new ArrayList<>();
		int others = 0;
		for (String player : present)
		{
			String key = GroupMembers.key(player);
			if (key.equals(self))
			{
				continue;
			}
			if (members.contains(key))
			{
				named.add(player);
			}
			else
			{
				others++;
			}
		}
		return new TeamSummary(named, others);
	}

	/** The player and everyone with them. */
	int size()
	{
		return 1 + named.size() + others;
	}

	/** "with Sam, Pat +2 others", or empty when alone. */
	String with()
	{
		String team = String.join(", ", named);
		if (others > 0)
		{
			team += (team.isEmpty() ? "" : " ") + "+" + others + (others == 1 ? " other" : " others");
		}
		return team.isEmpty() ? "" : "with " + team;
	}

	/** "solo", "duo with Sam", "4-man (with Sam +2 others)" or "mass (48 players, with Sam)". */
	String mode()
	{
		int n = size();
		if (n == 1)
		{
			return "solo";
		}
		if (n == 2)
		{
			return "duo " + with();
		}
		if (n < MASS_FROM)
		{
			return n + "-man (" + with() + ")";
		}
		return "mass (" + n + " players" + (named.isEmpty() ? "" : ", with " + String.join(", ", named)) + ")";
	}
}
