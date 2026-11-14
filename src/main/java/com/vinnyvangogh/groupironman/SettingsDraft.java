package com.vinnyvangogh.groupironman;

import java.util.Map;
import java.util.TreeMap;

/**
 * The Settings tab's unsaved edits, and when they give way to what the group
 * holds. Pure, so the rules are tested without Swing.
 *
 * <p>The draft is replaced only when this player's role or the group's roles
 * change, or when it holds no edits. Anything else the tab shows (who is
 * online, who has been heard, each member's opt-outs) never touches it: the
 * leader's Save once sent the old values because another member's announce
 * redrew the tab (Board 2026-11-13). A new settings version arriving under
 * unsaved edits keeps them and says so, so Save is a choice, not a surprise.
 */
final class SettingsDraft
{
	private Map<String, Long> values = new TreeMap<>();
	/** What the draft started from: edited means it differs from this. */
	private Map<String, Long> base = new TreeMap<>();
	private String roleKey;
	private String settingsKey = "";
	private String notice;

	/** The draft as the inputs show it. */
	Map<String, Long> values()
	{
		return values;
	}

	void put(String key, long value)
	{
		values.put(key, value);
	}

	/** True when the player changed something not yet saved. */
	boolean edited()
	{
		return !values.equals(base);
	}

	/** Why the draft no longer matches the group's, or null. */
	String notice()
	{
		return notice;
	}

	/**
	 * Every redraw: what this client holds now. {@code current} is each shared
	 * setting's value now, the group's or this member's own while it has none.
	 */
	void update(GroupGovernance.Role role, GroupRoles roles, GroupSettings settings, Map<String, Long> current)
	{
		String nowRoles = role + "|" + (roles == null ? "" : roles.signature);
		String nowSettings = settings == null ? "" : settings.signature;
		if (!nowRoles.equals(roleKey) || !edited() || values.equals(current))
		{
			adopt(current);
		}
		else if (settings != null && !nowSettings.equals(settingsKey))
		{
			notice = "Your group's settings changed (version " + settings.version + " by " + settings.by + ")."
				+ " Save will replace them, or Discard to see theirs.";
		}
		roleKey = nowRoles;
		settingsKey = nowSettings;
	}

	/** "Discard changes". */
	void discard(Map<String, Long> current)
	{
		adopt(current);
	}

	/** Save went through: the draft is now what the group holds. */
	void saved()
	{
		base = new TreeMap<>(values);
		notice = null;
	}

	private void adopt(Map<String, Long> current)
	{
		values = new TreeMap<>(current);
		base = new TreeMap<>(current);
		notice = null;
	}
}
