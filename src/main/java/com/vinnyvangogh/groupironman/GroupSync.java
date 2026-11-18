package com.vinnyvangogh.groupironman;

import java.util.Objects;

/**
 * Joining and leaving the group's RuneLite party, without the Party panel.
 *
 * <p>RuneLite's {@code PartyService} is a core client service, so this works
 * with the Party plugin turned off (and its overlays then stay hidden). The
 * passphrase comes from {@link GroupKey}; the player never types it.
 *
 * <p>Every join is a click. Joining a party on login or on a timer is a
 * rejected Plugin Hub feature ("Auto-rejoin for parties: would put undue
 * strain on the party servers"; Party Smarty, plugin-hub #19833, was closed
 * for it), so nothing here runs on its own except leaving.
 *
 * <p>Rules: a party the player joined themselves (raids, a friend) is never
 * replaced without them pressing "Switch"; on shutdown only a party this
 * plugin joined is left.
 */
final class GroupSync
{
	/** The slice of {@code PartyService} this needs, so the rules can be tested without a client. */
	interface Party
	{
		/** The passphrase of the party this client is in, or null when in none. */
		String passphrase();

		void changeParty(String passphrase);
	}

	enum State
	{
		/** "Sync with my group" is off: local data only. */
		OFF,
		/** No group key yet. */
		NO_KEY,
		/** Not logged in on a Group Ironman, so the group is not known. */
		NO_GROUP,
		/** Can join; waiting for the click. */
		READY,
		/** In this plugin's party for an older key: the key was changed or a new one arrived. */
		STALE,
		/** In a party the player joined themselves. */
		OTHER_PARTY,
		CONNECTED
	}

	private final Party party;
	/** The passphrase this plugin last joined, so shutdown leaves only that. */
	private String joined;

	GroupSync(Party party)
	{
		this.party = party;
	}

	/** The passphrase this group should use now, or null when it cannot be made yet. */
	static String target(boolean syncOn, String group, String key)
	{
		if (!syncOn || group == null || group.trim().isEmpty())
		{
			return null;
		}
		return GroupKey.passphrase(group, key);
	}

	State state(boolean syncOn, String group, String key)
	{
		if (!syncOn)
		{
			return State.OFF;
		}
		if (!GroupKey.isValid(key))
		{
			return State.NO_KEY;
		}
		String target = target(true, group, key);
		if (target == null)
		{
			return State.NO_GROUP;
		}
		String current = party.passphrase();
		if (current == null)
		{
			return State.READY;
		}
		if (current.equals(target))
		{
			return State.CONNECTED;
		}
		return ours(current) ? State.STALE : State.OTHER_PARTY;
	}

	/**
	 * The "Join group sync" click. Joins when not in a party or in this
	 * plugin's party for an old key; never leaves a party the player joined
	 * themselves (that is {@link #switchParty}). True when it joined.
	 */
	boolean join(boolean syncOn, String group, String key)
	{
		State s = state(syncOn, group, key);
		if (s != State.READY && s != State.STALE)
		{
			return false;
		}
		return change(target(syncOn, group, key));
	}

	/** The "Switch to group sync" click, shown only while in another party. */
	boolean switchParty(boolean syncOn, String group, String key)
	{
		if (state(syncOn, group, key) != State.OTHER_PARTY)
		{
			return false;
		}
		return change(target(syncOn, group, key));
	}

	/** Leaves the party only when it is the one this plugin joined. True when it left. */
	boolean leaveOwn()
	{
		String current = party.passphrase();
		if (joined == null || !joined.equals(current))
		{
			joined = null;
			return false;
		}
		joined = null;
		party.changeParty(null);
		return true;
	}

	/** Whether the party this client is in is one this plugin joined. */
	boolean inOwnParty()
	{
		return joined != null && joined.equals(party.passphrase());
	}

	private boolean change(String passphrase)
	{
		if (passphrase == null || Objects.equals(passphrase, party.passphrase()))
		{
			return false;
		}
		party.changeParty(passphrase);
		joined = passphrase;
		return true;
	}

	/** A GIM Utilities passphrase, for any key. */
	private static boolean ours(String passphrase)
	{
		return passphrase.startsWith("gimu:");
	}
}
