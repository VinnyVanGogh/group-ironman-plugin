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
		JOIN("Join group sync", "Connect to your group's private party. Once per session; RuneLite does not allow"
			+ " plugins to rejoin parties on their own."),
		SWITCH("Switch to group sync", "Leave the party you are in and connect to your group's"),
		LEAVE("Leave", "Disconnect from your group's party"),
		CREATE_KEY("Create group key", "Make a new random key for your group; then copy and send the share line"),
		NEW_KEY("New key", "Replace the key, e.g. if it leaked. Share the new one; members must rejoin"),
		COPY_KEY("Copy group key", "Copies the key to your computer's clipboard to send to your group, e.g. in"
			+ " Discord. Anyone with it can join your group's sync, so share it only with your group"),
		PASTE_KEY("Paste group key", "Saves the key a member sent you, from your computer's clipboard. The plugin"
			+ " then joins your group's sync by itself");

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
				return new SyncHeader("No group key yet", "One member creates a key and sends it to the group, e.g."
					+ " in Discord; everyone else copies it and presses Paste.", false, Button.CREATE_KEY,
					Button.PASTE_KEY);
			case NO_GROUP:
				return new SyncHeader("Waiting for your group", "Log in on your Group Ironman. The group roster"
					+ " comes from the game.", false, Button.COPY_KEY);
			case READY:
				return new SyncHeader("Not connected", "Press Join to sync with " + group + ".", false, Button.JOIN,
					Button.COPY_KEY);
			case STALE:
				return new SyncHeader("Group key changed", "Press Join to reconnect with the new key.", false,
					Button.JOIN, Button.COPY_KEY);
			case OTHER_PARTY:
				return new SyncHeader("In another party; GIM sync paused", "Nothing is sent to that party.", false,
					Button.SWITCH);
			case CONNECTED:
				// Counts you too (Board 2026-11-04: "zero members online, even though I'm online").
				return new SyncHeader("Connected, " + (othersOnline + 1) + " online"
					+ (othersOnline == 0 ? " (you)" : ""), "Synced with " + group + ". Only roster members are"
					+ " shown.", true,
					Button.COPY_KEY, Button.NEW_KEY, Button.LEAVE);
			default:
				throw new IllegalArgumentException(String.valueOf(state));
		}
	}
}
