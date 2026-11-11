package com.vinnyvangogh.groupironman;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Roles and the group's settings on one client: what it holds, what it will
 * accept from the party, and what its own player may sign. Pure and
 * synchronized; {@link GroupService} feeds it messages and persists what it
 * holds, and the panel calls its actions.
 *
 * <p>The checks, all made by every receiving client:
 * <ul>
 * <li>Roles are accepted only when signed by the pinned leader key (the
 * first accepted claim's), and only when newer. A first claim is accepted only
 * once the claimant's own announce has shown the same key, so nobody can
 * claim in another member's name.</li>
 * <li>Settings are accepted only when signed by the leader or a current
 * editor, with the key the roles hold for them, and only when
 * {@link GroupSettings#newerThan} what is held.</li>
 * </ul>
 * Member names are the ones {@link GroupService#sender} already checked
 * against the in-game roster, the trust every other group feature uses.
 */
final class GroupGovernance
{
	enum Role
	{
		LEADER, EDITOR, MEMBER
	}

	enum Verdict
	{
		ACCEPTED,
		/** Already held, or older than what is held. */
		STALE,
		/** Bad signature, wrong group, wrong leader key, or a signer who may not edit. */
		REFUSED,
		/** Held until what it needs arrives: the claimant's key, or the roles. */
		PENDING
	}

	private final GroupSigner.Identity me;
	private String group = "";
	private GroupRoles roles;
	private GroupSettings settings;
	private GroupRoles pendingRoles;
	private GroupSettings pendingSettings;
	/** Name key to the public key in that member's latest announce. */
	private final Map<String, String> keys = new LinkedHashMap<>();
	/** Display name to the opt-outs in that member's latest announce. */
	private final Map<String, MemberPrefs> prefs = new LinkedHashMap<>();

	GroupGovernance(GroupSigner.Identity me)
	{
		this.me = me;
	}

	String publicKey()
	{
		return me.publicKey();
	}

	/** The in-game group this client is on; roles for any other group are not used. */
	synchronized void group(String name)
	{
		group = GroupMembers.key(name == null ? "" : name);
	}

	/** What was saved last session; anything not for the current group is dropped. */
	synchronized void load(GroupRoles savedRoles, GroupSettings savedSettings)
	{
		roles = savedRoles != null && savedRoles.verify() && savedRoles.group.equals(group) ? savedRoles : null;
		settings = roles != null && savedSettings != null && savedSettings.verify() ? savedSettings : null;
	}

	synchronized GroupRoles roles()
	{
		return roles;
	}

	synchronized GroupSettings settings()
	{
		return settings;
	}

	synchronized Map<String, MemberPrefs> prefs()
	{
		return Collections.unmodifiableMap(new LinkedHashMap<>(prefs));
	}

	/** The key {@code name} last announced, or null. */
	synchronized String knownKey(String name)
	{
		return keys.get(GroupMembers.key(name));
	}

	synchronized boolean unclaimed()
	{
		return roles == null && !group.isEmpty();
	}

	/** {@code name}'s role as the roles say, whatever key they sign with. */
	synchronized Role role(String name)
	{
		if (roles == null)
		{
			return Role.MEMBER;
		}
		if (roles.isLeader(name))
		{
			return Role.LEADER;
		}
		return roles.editorKey(name) != null ? Role.EDITOR : Role.MEMBER;
	}

	/** This client's role: as {@link #role}, and only when it holds the key the roles name. */
	synchronized Role myRole(String self)
	{
		if (roles == null || !roles.mayEdit(self, me.publicKey()))
		{
			return Role.MEMBER;
		}
		return roles.isLeader(self) ? Role.LEADER : Role.EDITOR;
	}

	// ---- This player's own actions ----

	/** "Create group key" or "Claim leadership": null when the group already has a leader here. */
	synchronized GroupRoles claim(String self, long now)
	{
		if (!unclaimed())
		{
			return null;
		}
		roles = GroupRoles.claim(group, self, me, now);
		keys.put(GroupMembers.key(self), me.publicKey());
		applyPending();
		return roles;
	}

	/**
	 * The leader grants or revokes {@code name}'s editor role. Null when this
	 * client is not the leader, or (granting) {@code name}'s key is not known.
	 * Revoking an editor whose save is the current settings re-signs them as
	 * the leader, so a member who joins later accepts the same document.
	 */
	synchronized GroupRoles setEditor(String self, String name, boolean editor, long now)
	{
		if (myRole(self) != Role.LEADER || roles.isLeader(name))
		{
			return null;
		}
		String key = keys.get(GroupMembers.key(name));
		if (editor && key == null)
		{
			return null;
		}
		roles = roles.withEditor(name, key, editor, me, now);
		if (settings != null && !roles.mayEdit(settings.by, settings.byKey))
		{
			settings = settings.resign(self, me, now);
		}
		return roles;
	}

	/**
	 * The leader or an editor saves the group's settings. Null when this client
	 * may not; the held settings unchanged when nothing changed.
	 */
	synchronized GroupSettings save(String self, Map<String, Long> values, long now)
	{
		if (myRole(self) == Role.MEMBER)
		{
			return null;
		}
		if (settings != null && settings.sameValues(values))
		{
			return settings;
		}
		settings = GroupSettings.next(settings, values, self, me, now);
		return settings;
	}

	/** "Forget group leader": this client only, so a group whose leader lost their key can start over. */
	synchronized void forgetLeader()
	{
		roles = null;
		pendingRoles = null;
	}

	// ---- From the party ----

	synchronized void receiveMember(String name, String key, MemberPrefs memberPrefs)
	{
		if (key != null && !key.isEmpty() && key.length() <= GroupSigner.MAX_KEY_CHARS)
		{
			keys.put(GroupMembers.key(name), key);
		}
		if (memberPrefs != null)
		{
			prefs.put(GroupMembers.display(name), memberPrefs);
		}
		applyPending();
	}

	synchronized Verdict receiveRoles(GroupRoles incoming)
	{
		Verdict v = checkRoles(incoming);
		if (v == Verdict.PENDING)
		{
			pendingRoles = incoming;
		}
		if (v == Verdict.ACCEPTED)
		{
			roles = incoming;
			applyPending();
		}
		return v;
	}

	private Verdict checkRoles(GroupRoles r)
	{
		if (r == null || !r.verify() || !r.group.equals(group))
		{
			return Verdict.REFUSED;
		}
		if (roles == null)
		{
			String claimant = keys.get(GroupMembers.key(r.leader));
			if (claimant == null)
			{
				return Verdict.PENDING;
			}
			return claimant.equals(r.leaderKey) ? Verdict.ACCEPTED : Verdict.REFUSED;
		}
		if (!r.leaderKey.equals(roles.leaderKey) || !r.isLeader(roles.leader))
		{
			return Verdict.REFUSED;
		}
		return r.version > roles.version ? Verdict.ACCEPTED : Verdict.STALE;
	}

	synchronized Verdict receiveSettings(GroupSettings incoming)
	{
		if (incoming == null || !incoming.verify())
		{
			return Verdict.REFUSED;
		}
		if (roles == null)
		{
			pendingSettings = incoming.newerThan(pendingSettings) ? incoming : pendingSettings;
			return Verdict.PENDING;
		}
		if (!roles.mayEdit(incoming.by, incoming.byKey))
		{
			return Verdict.REFUSED;
		}
		if (!incoming.newerThan(settings))
		{
			return Verdict.STALE;
		}
		settings = incoming;
		return Verdict.ACCEPTED;
	}

	/** Whatever was waiting on a key or on the roles, tried again. */
	private void applyPending()
	{
		if (pendingRoles != null && roles == null)
		{
			GroupRoles r = pendingRoles;
			Verdict v = checkRoles(r);
			if (v != Verdict.PENDING)
			{
				pendingRoles = null;
			}
			if (v == Verdict.ACCEPTED)
			{
				roles = r;
			}
		}
		if (pendingSettings != null && roles != null)
		{
			GroupSettings s = pendingSettings;
			pendingSettings = null;
			receiveSettings(s);
		}
	}
}
