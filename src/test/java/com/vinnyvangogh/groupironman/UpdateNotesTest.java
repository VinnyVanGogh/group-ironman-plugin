package com.vinnyvangogh.groupironman;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The welcome and "what's new" chat lines: once per version, never with the
 * setting off, the welcome for any fresh install (however the plugin was turned
 * on), and nothing for an update whose notes are empty.
 */
public class UpdateNotesTest
{
	private static final String NEVER_SEEN = "";

	@Test
	public void versionIsThePluginPropertiesVersion() throws Exception
	{
		final Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(Paths.get("runelite-plugin.properties")))
		{
			properties.load(reader);
		}
		assertEquals(properties.getProperty("version"), UpdateNotes.VERSION);
	}

	@Test
	public void aFreshInstallGetsTheWelcomeLines()
	{
		// No stored version is a fresh install, whether it was turned on mid-session or before logging in.
		assertEquals(UpdateNotes.WELCOME, UpdateNotes.toShow(NEVER_SEEN, true));
		assertEquals(UpdateNotes.WELCOME, UpdateNotes.toShow(null, true));
		assertEquals(4, UpdateNotes.WELCOME.size());
		assertTrue(UpdateNotes.WELCOME.get(0).startsWith(GroupIronmanPlugin.NAME + " is on."));
	}

	@Test
	public void aFreshInstallShowsTheWelcomeOnceAcrossLoginsAndHops()
	{
		final List<String> posted = new ArrayList<>();
		String stored = NEVER_SEEN;
		for (int login = 0; login < 4; login++)
		{
			posted.addAll(UpdateNotes.toShow(stored, true));
			if (UpdateNotes.needsRecord(stored))
			{
				stored = UpdateNotes.VERSION;
			}
		}

		assertEquals(UpdateNotes.WELCOME, posted);
	}

	@Test
	public void theSeenVersionShowsNothing()
	{
		assertTrue(UpdateNotes.toShow(UpdateNotes.VERSION, true).isEmpty());
		assertFalse(UpdateNotes.needsRecord(UpdateNotes.VERSION));
	}

	@Test
	public void theSettingOffShowsNothingButStillRecordsTheVersion()
	{
		// Turned off now, back on later: the old lines must not appear then.
		assertTrue(UpdateNotes.toShow(NEVER_SEEN, false).isEmpty());
		assertTrue(UpdateNotes.toShow("0.9.0", false).isEmpty());
		assertTrue(UpdateNotes.needsRecord(NEVER_SEEN));
		assertTrue(UpdateNotes.needsRecord("0.9.0"));
	}

	@Test
	public void anUpdateShowsTheTitleThenTheNotes()
	{
		final List<String> shown = UpdateNotes.toShow("0.9.0", true);
		assertEquals(UpdateNotes.TITLE, shown.get(0));
		assertEquals(UpdateNotes.NOTES, shown.subList(1, shown.size()));
		assertEquals(shown, UpdateNotes.replay());
		assertTrue(UpdateNotes.needsRecord("0.9.0"));
	}

	@Test
	public void aDevBuildShowsTheNotesAgainWhenTheCommitChanges()
	{
		final String first = UpdateNotes.VERSION + "+abc1234";
		final String second = UpdateNotes.VERSION + "+def5678";
		assertFalse(UpdateNotes.needsRecord(first, first));
		assertTrue(UpdateNotes.needsRecord(first, second));
		assertEquals(UpdateNotes.replay(), UpdateNotes.toShow(first, true, second));
		assertTrue(UpdateNotes.toShow(first, false, second).isEmpty());
		// A fresh dev install still gets the welcome, not the notes.
		assertEquals(UpdateNotes.WELCOME, UpdateNotes.toShow("", true, second));
		assertTrue(UpdateNotes.current().startsWith(UpdateNotes.VERSION));
	}

	@Test
	public void everyNoteFitsOneChatLine()
	{
		for (String note : UpdateNotes.NOTES)
		{
			assertTrue(note, note.length() <= 110);
		}
	}

	@Test
	public void theTitleNamesThePluginAndVersion()
	{
		assertEquals("GIM Utilities updated to " + UpdateNotes.VERSION + ", what's new:", UpdateNotes.TITLE);
	}

	@Test
	public void theWelcomeNamesTheRealButtonsAndSettings()
	{
		final String welcome = String.join(" ", UpdateNotes.WELCOME);
		for (SyncHeader.Button b : new SyncHeader.Button[]{SyncHeader.Button.CREATE_KEY, SyncHeader.Button.JOIN})
		{
			assertTrue(b.label + " missing", welcome.contains(b.label));
		}
		assertTrue(welcome.contains("Post to Discord"));
		assertTrue(welcome.contains("Discord: connection"));
	}
}
