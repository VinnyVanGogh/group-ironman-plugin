package com.vinnyvangogh.groupironman;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * The in-game group roster, the allow-list for party data.
 *
 * <p>Read from the game's own Group Ironman clan:
 * {@code client.getClanSettings(ClanID.GROUP_IRONMAN).getMembers()}, falling
 * back to {@code client.getClanChannel(ClanID.GROUP_IRONMAN)} when settings
 * are not loaded. No hiscores or Wise Old Man calls: the roster the game shows
 * is authoritative and needs no network.
 *
 * <p>A party member whose name is not on it is ignored and listed as "Not in
 * your group", so a stranger who somehow has the passphrase sees nothing they
 * did not already have and gets nothing shown.
 */
final class GroupMembers
{
	private final String group;
	private final Set<String> names;
	private final Set<String> display;

	private GroupMembers(String group, Set<String> names, Set<String> display)
	{
		this.group = group;
		this.names = names;
		this.display = display;
	}

	static final GroupMembers UNKNOWN = new GroupMembers("", Collections.emptySet(), Collections.emptySet());

	/** {@code group} is the group's name; {@code members} as the game lists them. */
	static GroupMembers of(String group, Collection<String> members)
	{
		Set<String> names = new LinkedHashSet<>();
		Set<String> display = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		if (members != null)
		{
			for (String m : members)
			{
				String key = key(m);
				if (!key.isEmpty())
				{
					names.add(key);
					display.add(clean(m));
				}
			}
		}
		return new GroupMembers(group == null ? "" : clean(group), Collections.unmodifiableSet(names),
			Collections.unmodifiableSet(display));
	}

	/** The group's name, "" when not known. */
	String group()
	{
		return group;
	}

	boolean known()
	{
		return !names.isEmpty();
	}

	/** What to do with party data from a member called {@code name}. */
	enum Verdict
	{
		ACCEPT,
		/** Not on a known roster: drop it and list them as "Not in your group". */
		STRANGER,
		/** The roster has not arrived yet: drop it quietly; members resend once it has. */
		WAIT
	}

	Verdict check(String name)
	{
		if (!known())
		{
			return Verdict.WAIT;
		}
		return allows(name) ? Verdict.ACCEPT : Verdict.STRANGER;
	}

	/** True when {@code name} is on the roster. An unknown roster allows nobody. */
	boolean allows(String name)
	{
		return names.contains(key(name));
	}

	/** The roster's names, sorted, as the game spells them. */
	Set<String> names()
	{
		return display;
	}

	/** Jagex names compare case-insensitively, and a space may arrive as a no-break space, '_' or '-'. */
	static String key(String name)
	{
		return clean(name).replace('_', ' ').replace('-', ' ').toLowerCase(Locale.ROOT);
	}

	/** A name as the game spells it, without chat icon tags. */
	static String display(String name)
	{
		return clean(name);
	}

	private static String clean(String name)
	{
		if (name == null)
		{
			return "";
		}
		return name.replaceAll("<[^>]*>", "").replace(' ', ' ').trim();
	}
}
