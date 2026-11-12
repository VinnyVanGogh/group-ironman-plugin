package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The game-chat lines posted once per version: a welcome on a fresh install,
 * and a "what's new" list on the first login after an update.
 *
 * <p>Nothing stored means a version before this one, or a brand-new install.
 * The Plugin Hub updates plugins while the client starts, so an update starts
 * the plugin on the login screen; an install from the Plugin Hub panel starts
 * it in a running, usually logged-in client. Nothing stored and started logged
 * in is therefore read as a fresh install and gets the welcome lines instead.
 * (The same rule as Combat Vitals.)
 */
final class UpdateNotes
{
	/** Must match {@code version} in runelite-plugin.properties (UpdateNotesTest). */
	static final String VERSION = "1.0.0";

	static final String TITLE = GroupIronmanPlugin.NAME + " updated to " + VERSION + ", what's new:";

	/**
	 * What is new in {@link #VERSION}, one chat message each. Empty for the first
	 * release: an update to a version with no notes posts nothing.
	 */
	static final List<String> NOTES = Collections.emptyList();

	/** The fresh-install welcome, one chat message each (wording approved by the Board 2026-11-11). */
	static final List<String> WELCOME = Collections.unmodifiableList(Arrays.asList(
		"GIM Utilities is on. Everything starts off; turn on what you want.",
		"1. Group: open the GIM Utilities panel, Create group key, send it to your group; everyone Pastes it and"
			+ " clicks Join group sync.",
		"2. Discord: Server Settings > Integrations > Webhooks > Copy URL, paste it in Configure GIM Utilities >"
			+ " Discord: connection, and tick Post to Discord.",
		"3. You get: shared banks and storage log, ::find, member cards, the Log, and Discord posts + banners for"
			+ " drops, deaths, levels & more."
	));

	private UpdateNotes()
	{
	}

	/**
	 * What to post on this login, first line first; empty when nothing.
	 *
	 * @param lastSeen        the stored last-seen version, "" when none
	 * @param enabled         the "Show update notes" setting
	 * @param startedLoggedIn whether the plugin started while already logged in
	 */
	static List<String> toShow(String lastSeen, boolean enabled, boolean startedLoggedIn)
	{
		if (!enabled || !needsRecord(lastSeen))
		{
			return Collections.emptyList();
		}
		if (isNullOrEmpty(lastSeen) && startedLoggedIn)
		{
			return WELCOME;
		}
		if (NOTES.isEmpty())
		{
			return Collections.emptyList();
		}
		final List<String> lines = new ArrayList<>(NOTES.size() + 1);
		lines.add(TITLE);
		lines.addAll(NOTES);
		return lines;
	}

	/** Whether this version still has to be stored as seen; true with the setting off too. */
	static boolean needsRecord(String lastSeen)
	{
		return !VERSION.equals(lastSeen);
	}

	private static boolean isNullOrEmpty(String s)
	{
		return s == null || s.isEmpty();
	}
}
