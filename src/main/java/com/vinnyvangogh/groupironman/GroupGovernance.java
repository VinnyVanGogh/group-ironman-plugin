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
	private GroupWebhooks webhooks;
	private GroupWebhooks pendingWebhooks;
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
		// A webhooks document for another group (or none) is dropped; one waiting on these roles is taken.
		if (!fromLeader(webhooks))
		{
			webhooks = null;
		}
		applyPendingWebhooks();
	}

	synchronized GroupRoles roles()
	{
		return roles;
	}

	synchronized GroupSettings settings()
	{
		return settings;
	}

	/**
	 * The leader's latest webhooks document for this group, shared or not: null when none is held, or when the
	 * one held is not signed by the current leader with the leader's key (a new leader, a forgotten one).
	 */
	synchronized GroupWebhooks webhooks()
	{
		return fromLeader(webhooks) ? webhooks : null;
	}

	/** The leader's shared webhooks, or null when the leader shares none. */
	synchronized GroupWebhooks sharedWebhooks()
	{
		GroupWebhooks w = webhooks();
		return w != null && w.shared ? w : null;
	}

	private boolean fromLeader(GroupWebhooks w)
	{
		return w != null && roles != null && w.group.equals(group) && roles.isLeader(w.by)
			&& roles.leaderKey.equals(w.byKey);
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

	/**
	 * The leader's "Share webhooks with the group": on publishes {@code own}'s seven webhooks, off publishes a
	 * version with none, which clears members' copies. False when this client is not the leader (an editor may
	 * not); the held document unchanged when nothing changed.
	 */
	synchronized boolean shareWebhooks(String self, boolean share, Webhooks own, long now)
	{
		if (myRole(self) != Role.LEADER)
		{
			return false;
		}
		GroupWebhooks held = webhooks();
		// Never shared and still not: nothing for members to clear, so nothing is made or sent.
		boolean unchanged = held == null ? !share : held.same(share, own);
		if (!unchanged)
		{
			webhooks = GroupWebhooks.next(held, group, share, own, self, me, now);
		}
		return true;
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
		return roles == null ? checkClaim(r) : checkUpdate(r);
	}

	/** No leader pinned yet: accepted only with the key the claimant announced themselves. */
	private Verdict checkClaim(GroupRoles r)
	{
		String claimant = keys.get(GroupMembers.key(r.leader));
		if (claimant == null)
		{
			return Verdict.PENDING;
		}
		return claimant.equals(r.leaderKey) ? Verdict.ACCEPTED : Verdict.REFUSED;
	}

	/** A leader is pinned: same leader and key, and newer. */
	private Verdict checkUpdate(GroupRoles r)
	{
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

	/**
	 * The leader's webhooks, from the party or from this client's cache: accepted only when signed by the
	 * leader the roles pin, with the leader's key, for this group, and newer than what is held. An editor's or
	 * a member's is refused.
	 */
	synchronized Verdict receiveWebhooks(GroupWebhooks incoming)
	{
		if (incoming == null || !incoming.verify())
		{
			return Verdict.REFUSED;
		}
		if (roles == null)
		{
			// Also before the group is known: the cache is read before the game says which group this is.
			pendingWebhooks = incoming.newerThan(pendingWebhooks) ? incoming : pendingWebhooks;
			return Verdict.PENDING;
		}
		if (!fromLeader(incoming))
		{
			return Verdict.REFUSED;
		}
		if (fromLeader(webhooks) && !incoming.newerThan(webhooks))
		{
			return Verdict.STALE;
		}
		webhooks = incoming;
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
		applyPendingWebhooks();
	}

	private void applyPendingWebhooks()
	{
		if (pendingWebhooks != null && roles != null)
		{
			GroupWebhooks w = pendingWebhooks;
			pendingWebhooks = null;
			receiveWebhooks(w);
		}
	}
}
