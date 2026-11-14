package com.vinnyvangogh.groupironman;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Online and offline posts for members without the plugin, as the game's Group channel sees them (Board
 * 2026-11-11, 'Online status' Posts).
 *
 * <ul>
 * <li>Each such member gets the same rules as a member's own login: the offline grace, a relog inside it posting
 * nothing, at most one pair per {@link PresenceDebounce#PAIR_MS}, a hop never a logout. It is a
 * {@link PresenceDebounce} per member.</li>
 * <li>The first channel after a {@link #reset} (this player's login) is the baseline: who is already on is not a
 * login. Losing the channel (this player's hop or logout) is not everyone logging out.</li>
 * <li>Members whose clients run the plugin post their own and are skipped.</li>
 * <li>Every plugin user sees the same channel; only the one that {@link #leads} sends the posts to Discord.</li>
 * </ul>
 *
 * Not thread-safe: the engine holds its presence lock.
 */
final class ChannelPosts
{
	private final ZoneId zone;
	private final Map<String, PresenceDebounce> members = new HashMap<>();
	private boolean seeded;

	ChannelPosts(ZoneId zone)
	{
		this.zone = zone;
	}

	/** The game's presence now: the posts for members without the plugin ({@code plugin} false), in order. */
	List<GroupEvent> update(GamePresence game, Predicate<String> plugin, long now, int graceMinutes)
	{
		if (!game.known())
		{
			return Collections.emptyList();
		}
		List<GroupEvent> out = new ArrayList<>();
		for (String name : game.names())
		{
			if (plugin.test(name))
			{
				continue;
			}
			PresenceDebounce member = members.computeIfAbsent(GroupMembers.key(name), k -> new PresenceDebounce(zone));
			if (game.state(name) == GamePresence.State.ONLINE)
			{
				List<GroupEvent> posts = member.login(now, game.world(name), name, graceMinutes);
				if (seeded)
				{
					out.addAll(posts);
				}
			}
			else
			{
				member.logout(now);
			}
		}
		seeded = true;
		return out;
	}

	/** Timer: the offlines whose grace has passed. */
	List<GroupEvent> poll(long now, int graceMinutes)
	{
		List<GroupEvent> out = new ArrayList<>();
		for (PresenceDebounce member : members.values())
		{
			GroupEvent offline = member.poll(now, graceMinutes);
			if (offline != null)
			{
				out.add(offline);
			}
		}
		return out;
	}

	/** This player logged out: forget everyone, and take the next channel as a new baseline. */
	void reset()
	{
		members.clear();
		seeded = false;
	}

	/**
	 * {@link #leads(String, Collection)} for a client that may not be in the party: one that is not syncing is
	 * not heard by the others, who may post without it, so it posts only when it hears no one either.
	 */
	static boolean leads(String self, boolean syncing, Collection<String> othersOnline)
	{
		return syncing ? leads(self, othersOnline) : othersOnline.isEmpty() && !GroupMembers.key(self).isEmpty();
	}

	/**
	 * True when {@code self} is the plugin user that posts for the rest: the first by name of itself and
	 * {@code othersOnline}, the plugin users online. Every client works it out from the same names, so one posts.
	 */
	static boolean leads(String self, Collection<String> othersOnline)
	{
		String me = GroupMembers.key(self);
		if (me.isEmpty())
		{
			return false;
		}
		for (String other : othersOnline)
		{
			String key = GroupMembers.key(other);
			if (!key.isEmpty() && key.compareTo(me) < 0)
			{
				return false;
			}
		}
		return true;
	}
}
