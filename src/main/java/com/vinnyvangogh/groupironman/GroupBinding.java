package com.vinnyvangogh.groupironman;

import java.util.*;

/**
 * Whether the account logged in now is a member of the group this client is set up for (Board 2026-11-15).
 *
 * <p>The roster ({@link GroupMembers}) is read from the logged-in account's own Group Ironman clan, so on its own
 * it cannot tell an account that is in another group, or in none, from a member: every account is on its own
 * roster. What ties the client to one group is the group's name, saved the first time a member's roster is
 * read ({@link #bound}), and cleared when the group key changes. An account whose roster names another group,
 * or that has no group roster at all, is not a member: nothing is posted, logged or shared for it.
 *
 * <p>The first binding, on a client set up before this existed, is taken from what the client already holds
 * for its group, so the first login after an update being a non-member's cannot bind the wrong group: the
 * signed roles' group when saved, else a roster that has one of the members this client has heard from.
 */
final class GroupBinding
{
	enum Verdict
	{
		/** On the bound group's roster: everything runs. */
		MEMBER,
		/** Another group's roster, or not on it: nothing runs for this account. */
		NOT_MEMBER,
		/** No roster yet (loading, or an account with no group): nothing runs until it is known. */
		WAIT
	}

	static final class Result
	{
		final Verdict verdict;
		/** {@link GroupMembers#key} of the group to save as bound now, or null when nothing changes. */
		final String bind;

		Result(Verdict verdict, String bind)
		{
			this.verdict = verdict;
			this.bind = bind;
		}

		boolean member()
		{
			return verdict == Verdict.MEMBER;
		}
	}

	private GroupBinding()
	{
	}

	/**
	 * @param bound       the saved group ({@link GroupMembers#key}), "" or null when none is saved yet
	 * @param roster      the logged-in account's group roster, as the game shows it
	 * @param self        the logged-in account's name
	 * @param rolesGroup  the group the saved signed roles are for, "" or null when none are saved
	 * @param heard       the names of members this client holds data for (the group cache)
	 */
	static Result check(String bound, GroupMembers roster, String self, String rolesGroup, Collection<String> heard)
	{
		String me = GroupMembers.key(self);
		String group = GroupMembers.key(roster.group());
		if (!roster.known() || me.isEmpty() || group.isEmpty())
		{
			return new Result(Verdict.WAIT, null);
		}
		if (!roster.allows(self))
		{
			return new Result(Verdict.NOT_MEMBER, null);
		}
		String saved = bound == null ? "" : GroupMembers.key(bound);
		if (!saved.isEmpty())
		{
			return new Result(group.equals(saved) ? Verdict.MEMBER : Verdict.NOT_MEMBER, null);
		}
		String roles = rolesGroup == null ? "" : GroupMembers.key(rolesGroup);
		if (!roles.isEmpty())
		{
			return new Result(group.equals(roles) ? Verdict.MEMBER : Verdict.NOT_MEMBER, roles);
		}
		Set<String> others = new HashSet<>();
		if (heard != null)
		{
			heard.forEach(n -> others.add(GroupMembers.key(n)));
		}
		others.remove(me);
		others.remove("");
		if (others.isEmpty() || others.stream().anyMatch(roster::allows))
		{
			return new Result(Verdict.MEMBER, group);
		}
		// Holds other members' data, none of them on this roster: another group's account. A member binds it.
		return new Result(Verdict.NOT_MEMBER, null);
	}
}
