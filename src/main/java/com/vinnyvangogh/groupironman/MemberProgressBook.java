package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * What the group has told this client about each member's boss bests and quests/diaries, plus this member's own,
 * sent only when it changes. Thread-safe: party messages arrive on RuneLite's thread, the panel reads on the EDT.
 */
final class MemberProgressBook
{
	/** Most members kept, so a hostile sender cannot grow the maps with made-up names. */
	static final int MAX_MEMBERS = 100;

	private final Gson gson;
	private final Consumer<String> send;
	private final Map<String, PersonalBests> bests = new HashMap<>();
	private final Map<String, AchievementState> achievements = new HashMap<>();
	private PersonalBests sentBests;
	private AchievementState sentAchievements;

	MemberProgressBook(Gson gson, Consumer<String> send)
	{
		this.gson = gson;
		this.send = send;
	}

	/**
	 * Takes one party payload from {@code from}.
	 *
	 * @return the bests that member just beat (empty if none, or if this was not a bests payload); null when the
	 *     payload was not readable progress at all
	 */
	synchronized List<PersonalBests.Beaten> receive(String from, String payload)
	{
		if (from == null || !MemberProgressCodec.isProgress(payload))
		{
			return null;
		}
		PersonalBests b = MemberProgressCodec.decodeBests(gson, payload);
		if (b != null && room(from, bests))
		{
			PersonalBests earlier = bests.put(from, b);
			// A first sight of a member is a sync, not a beaten best.
			return earlier == null ? new ArrayList<>() : b.beaten(earlier);
		}
		AchievementState a = MemberProgressCodec.decodeAchievements(gson, payload);
		if (a != null && room(from, achievements))
		{
			achievements.put(from, a);
			return new ArrayList<>();
		}
		return null;
	}

	private static boolean room(String name, Map<String, ?> map)
	{
		return map.containsKey(name) || map.size() < MAX_MEMBERS;
	}

	/** This member's own state: sends what differs from what was last sent. */
	synchronized void publish(PersonalBests mine, AchievementState ach)
	{
		if (mine != null && !mine.equals(sentBests))
		{
			sentBests = mine;
			send.accept(MemberProgressCodec.encode(gson, mine));
		}
		if (ach != null && !ach.equals(sentAchievements))
		{
			sentAchievements = ach;
			send.accept(MemberProgressCodec.encode(gson, ach));
		}
	}

	/** A new party member or a rejoin: send everything again. */
	synchronized void resendAll()
	{
		sentBests = null;
		sentAchievements = null;
	}

	synchronized PersonalBests bests(String member)
	{
		return bests.getOrDefault(member, PersonalBests.EMPTY);
	}

	synchronized AchievementState achievements(String member)
	{
		return achievements.get(member);
	}

	synchronized java.util.Set<String> achievementMembers()
	{
		return new java.util.TreeSet<>(achievements.keySet());
	}

	synchronized Map<String, PersonalBests> allBests()
	{
		return Collections.unmodifiableMap(new HashMap<>(bests));
	}

	synchronized void forget(String member)
	{
		bests.remove(member);
		achievements.remove(member);
	}

	synchronized void clear()
	{
		bests.clear();
		achievements.clear();
		resendAll();
	}
}
