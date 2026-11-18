package com.vinnyvangogh.groupironman;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Who leads the group and who may edit its settings, signed by the leader.
 *
 * <p>The leader is whoever claimed the group first (by creating its key, or
 * with "Claim leadership" on a group that has none yet, Board 2026-11-10).
 * Every client pins the leader's public key the first time it accepts this
 * document, and from then on accepts only newer versions signed by that same
 * key: a member cannot sign themselves in.
 *
 * <p>Immutable. Each editor is held with the public key the leader saw in
 * their announce, so an editor's settings are checked against that key.
 */
final class GroupRoles
{
	/** Plenty for a group of five; caps what a document can make a client hold. */
	static final int MAX_EDITORS = 16;

	/** {@link GroupMembers#key} of the group name. */
	final String group;
	final long version;
	final long at;
	final String leader;
	final String leaderKey;
	/** Display name to public key, sorted so the signed text is stable. */
	final Map<String, String> editors;
	final String signature;

	GroupRoles(String group, long version, long at, String leader, String leaderKey, Map<String, String> editors,
		String signature)
	{
		this.group = group;
		this.version = version;
		this.at = at;
		this.leader = leader;
		this.leaderKey = leaderKey;
		this.editors = Collections.unmodifiableMap(new TreeMap<>(editors));
		this.signature = signature;
	}

	/** Version 1, with {@code leader} as leader and no editors. */
	static GroupRoles claim(String group, String leader, GroupSigner.Identity id, long now)
	{
		return signed(GroupMembers.key(group), 1, now, GroupMembers.display(leader), id, new TreeMap<>());
	}

	/** The next version with {@code name} an editor (or not), signed by the leader's {@code id}. */
	GroupRoles withEditor(String name, String key, boolean editor, GroupSigner.Identity id, long now)
	{
		Map<String, String> next = new TreeMap<>(editors);
		next.keySet().removeIf(n -> GroupMembers.key(n).equals(GroupMembers.key(name)));
		if (editor)
		{
			next.put(GroupMembers.display(name), key);
		}
		return signed(group, version + 1, Math.max(now, at), leader, id, next);
	}

	private static GroupRoles signed(String group, long version, long at, String leader, GroupSigner.Identity id,
		Map<String, String> editors)
	{
		GroupRoles unsigned = new GroupRoles(group, version, at, leader, id.publicKey(), editors, "");
		return new GroupRoles(group, version, at, leader, id.publicKey(), editors, id.sign(unsigned.canonical()));
	}

	String canonical()
	{
		StringBuilder eds = new StringBuilder();
		for (Map.Entry<String, String> e : editors.entrySet())
		{
			eds.append(GroupSigner.canonical(e.getKey(), e.getValue()));
		}
		return GroupSigner.canonical("gimu-roles", 1, group, version, at, leader, leaderKey, eds);
	}

	/** Signed by the key it names as the leader's, and well formed. */
	boolean verify()
	{
		return version >= 1 && !leader.isEmpty() && editors.size() <= MAX_EDITORS
			&& GroupSigner.verify(leaderKey, canonical(), signature);
	}

	boolean isLeader(String name)
	{
		return GroupMembers.key(leader).equals(GroupMembers.key(name));
	}

	/** The key {@code name} was made editor with, or null when not an editor. */
	String editorKey(String name)
	{
		String k = GroupMembers.key(name);
		for (Map.Entry<String, String> e : editors.entrySet())
		{
			if (GroupMembers.key(e.getKey()).equals(k))
			{
				return e.getValue();
			}
		}
		return null;
	}

	/** Whether {@code name}, signing with {@code key}, may change the group's settings. */
	boolean mayEdit(String name, String key)
	{
		if (key == null)
		{
			return false;
		}
		return isLeader(name) ? key.equals(leaderKey) : key.equals(editorKey(name));
	}
}
