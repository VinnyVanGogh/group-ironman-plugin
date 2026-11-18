package com.vinnyvangogh.groupironman;

import java.util.Collection;
import java.util.Set;

/**
 * Notices the moment every member of the group is online at once. Fires on the change from "someone is missing" to
 * "all here", never on the first look (a client that starts with everyone already online says nothing), and again only
 * after someone has been missing in between. A group of one is never "everyone".
 */
final class EveryoneOnline
{
	static final int MIN_MEMBERS = 2;

	private boolean known;
	private boolean all;

	/** Returns the member count when this look is the change to everyone online, else 0. */
	int update(Collection<String> members, Set<String> online)
	{
		boolean now = members.size() >= MIN_MEMBERS && online.containsAll(members);
		boolean fire = known && now && !all;
		known = true;
		all = now;
		return fire ? members.size() : 0;
	}

	/** Forget what was seen: a new login or group starts the baseline over. */
	void reset()
	{
		known = false;
		all = false;
	}
}
