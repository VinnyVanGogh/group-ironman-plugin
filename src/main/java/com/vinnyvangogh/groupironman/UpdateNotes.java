package com.vinnyvangogh.groupironman;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * The game-chat lines posted once per version: a welcome on a fresh install,
 * and a "what's new" list on the first login after an update.
 *
 * <p>Every published version stores itself as seen, so nothing stored always
 * means a fresh install, however the plugin was turned on: installed mid-session
 * or switched on before logging in (Board 2026-11-11). Combat Vitals needed a
 * logged-in check only because its 1.0.0 never stored a version.
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
	static final List<String> NOTES = Collections.unmodifiableList(Arrays.asList(
		"Compare tab: members side by side, with banked experience; Leaderboard skill grid and personal bests.",
		"Fight Caves, Inferno and Colosseum entry posts are on; set milestone waves and per-activity switches.",
		"Group box shows short event lines with icons; new Group box size, icons and times settings.",
		"Shopping: Path to / Path to next routes you to the next stop with the Shortest Path plugin.",
		"Member login and logout banners and Log lines show at once. Type ::whatsnew to see these again."
	));

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

	/** The dev build's commit, stamped into build-info.properties by build.gradle; "" on a Hub build. */
	private static final String BUILD = readBuild();

	private UpdateNotes()
	{
	}

	/**
	 * What to post on this login, first line first; empty when nothing.
	 *
	 * @param lastSeen the stored last-seen version, "" when none
	 * @param enabled  the "Show update notes" setting
	 */
	static List<String> toShow(String lastSeen, boolean enabled)
	{
		return toShow(lastSeen, enabled, VERSION);
	}

	/** As above, for a build whose seen-marker is {@code current} (see {@link #current()}). */
	static List<String> toShow(String lastSeen, boolean enabled, String current)
	{
		if (!enabled || !needsRecord(lastSeen, current))
		{
			return Collections.emptyList();
		}
		if (isNullOrEmpty(lastSeen))
		{
			return WELCOME;
		}
		if (NOTES.isEmpty())
		{
			return Collections.emptyList();
		}
		return replay();
	}

	/** The notes again on request ({@code ::whatsnew}); says so when there are none. */
	static List<String> replay()
	{
		if (NOTES.isEmpty())
		{
			return Collections.singletonList(GroupIronmanPlugin.NAME + " " + VERSION + " has no update notes.");
		}
		final List<String> lines = new ArrayList<>(NOTES.size() + 1);
		lines.add(TITLE);
		lines.addAll(NOTES);
		return lines;
	}

	/**
	 * The marker stored as seen: the version, plus the commit on a dev build, so
	 * the notes show again whenever the dev build changes.
	 */
	static String current()
	{
		return BUILD.isEmpty() ? VERSION : VERSION + "+" + BUILD;
	}

	/** Whether this version still has to be stored as seen; true with the setting off too. */
	static boolean needsRecord(String lastSeen)
	{
		return needsRecord(lastSeen, VERSION);
	}

	static boolean needsRecord(String lastSeen, String current)
	{
		return !current.equals(lastSeen);
	}

	private static String readBuild()
	{
		try (InputStream in = UpdateNotes.class.getResourceAsStream("/com/vinnyvangogh/groupironman/build-info.properties"))
		{
			if (in == null)
			{
				return "";
			}
			final Properties p = new Properties();
			p.load(in);
			return p.getProperty("commit", "").trim();
		}
		catch (Exception e)
		{
			return "";
		}
	}

	private static boolean isNullOrEmpty(String s)
	{
		return s == null || s.isEmpty();
	}
}
