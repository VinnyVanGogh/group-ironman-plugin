package com.vinnyvangogh.groupironman;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The panel header for each sync state: a title, what to do next, and the
 * buttons that do it. Pure, so every state's wording is tested.
 */
final class SyncHeader
{
	enum Button
	{
		JOIN("Join group sync", "Connect to your group's private party. Once per session."),
		SWITCH("Switch to group sync", "Leave the party you are in and connect to your group's"),
		LEAVE("Leave", "Disconnect from your group's party"),
		CREATE_KEY("Create group key", "Make a new random key for your group; then copy and send the share line"),
		NEW_KEY("New key", "Replace the key, e.g. if it leaked. Share the new one; members must rejoin"),
		COPY_SHARE_LINE("Copy share line", "Copies '[GIMU] <key>' to the clipboard. Paste it in group chat (/g) and"
			+ " press Enter yourself; members' GIM Utilities pick it up and hide it");

		final String label;
		final String tooltip;

		Button(String label, String tooltip)
		{
			this.label = label;
			this.tooltip = tooltip;
		}
	}

	final String title;
	/** One line under the title, or null. */
	final String detail;
	/** Green when synced. */
	final boolean ok;
	final List<Button> buttons;

	private SyncHeader(String title, String detail, boolean ok, Button... buttons)
	{
		this.title = title;
		this.detail = detail;
		this.ok = ok;
		this.buttons = Collections.unmodifiableList(Arrays.asList(buttons));
	}

	/** {@code othersOnline}: members other than this player heard from in the last few minutes. */
	static SyncHeader of(GroupSync.State state, int othersOnline, GroupMembers members)
	{
		String group = members.group().isEmpty() ? "your group" : members.group();
		switch (state)
		{
			case OFF:
				return new SyncHeader("Sync off", "Solo: only what this client saw. Turn on 'Sync with my group' in"
					+ " settings to share.", false);
			case NO_KEY:
				return new SyncHeader("No group key yet", "One member creates a key and sends the share line in"
					+ " group chat; everyone else's plugin picks it up.", false, Button.CREATE_KEY);
			case NO_GROUP:
				return new SyncHeader("Waiting for your group", "Log in on your Group Ironman. The group roster"
					+ " comes from the game.", false, Button.COPY_SHARE_LINE);
			case READY:
				return new SyncHeader("Not connected", "Press Join to sync with " + group + ".", false, Button.JOIN,
					Button.COPY_SHARE_LINE);
			case STALE:
				return new SyncHeader("Group key changed", "Press Join to reconnect with the new key.", false,
					Button.JOIN, Button.COPY_SHARE_LINE);
			case OTHER_PARTY:
				return new SyncHeader("In another party; GIM sync paused", "Nothing is sent to that party.", false,
					Button.SWITCH);
			case CONNECTED:
				return new SyncHeader("Connected, " + othersOnline + (othersOnline == 1 ? " member" : " members")
					+ " online", "Synced with " + group + ". Only roster members are shown.", true,
					Button.COPY_SHARE_LINE, Button.NEW_KEY, Button.LEAVE);
			default:
				throw new IllegalArgumentException(String.valueOf(state));
		}
	}
}
