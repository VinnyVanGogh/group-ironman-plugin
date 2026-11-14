package com.vinnyvangogh.groupironman;

import com.vinnyvangogh.testkit.ui.RuneLiteLook;
import com.vinnyvangogh.testkit.ui.SwingDriver;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.GimPanelFixture.MOSS;
import static com.vinnyvangogh.groupironman.GimPanelFixture.SELF;
import static com.vinnyvangogh.groupironman.GimPanelFixture.TIN;
import static com.vinnyvangogh.groupironman.GimPanelFixture.WREN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Clicks through the real side panel: each tab, its buttons, filters and
 * folds, with what a scenario left behind. Only the panel's own Swing
 * components are touched; nothing reaches the game.
 *
 * <p>Every test runs headless except the two clipboard ones, which skip
 * (Assume) without a display: a headless JVM has no system clipboard.
 */
public class GimPanelUiTest
{
	@Before
	public void lookLikeRuneLite()
	{
		SwingDriver.onEdt(RuneLiteLook::install);
	}

	private static GimPanelFixture panel(GimPanelFixture.Scene scene)
	{
		return new GimPanelFixture().show(scene);
	}

	// ---- Header ----

	@Test
	public void connectedHeaderNamesTheGroupAndLeaveCallsTheAction()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = panel(GimPanelFixture.groupScene());
			f.ui.assertShows("Connected, 2 online");
			f.ui.assertShows("Synced with " + GimPanelFixture.GROUP);
			f.ui.click("Leave");
			assertEquals("leave", f.actions.calls.get(0));
		});
	}

	@Test
	public void readyHeaderOffersJoin()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture.Scene s = GimPanelFixture.groupScene();
			s.sync = GroupSync.State.READY;
			GimPanelFixture f = panel(s);
			f.ui.assertShows("Not connected");
			f.ui.click("Join group sync");
			assertTrue(f.actions.calls.contains("join"));
		});
	}

	@Test
	public void everythingOffLeadsWithTheNoticeAndItsButtonOpensSettings()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture.Scene s = GimPanelFixture.groupScene();
			s.anyOn = false;
			GimPanelFixture f = panel(s);
			f.ui.assertShows(SidebarButton.OFF_TEXT);
			f.ui.click(SidebarButton.OFF_BUTTON);
			assertEquals("openSettings", f.actions.calls.get(0));
		});
	}

	// ---- Share with your group card ----

	@Test
	public void shareCardTurnsSharingOnAndNotNowHidesIt()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture.Scene s = GimPanelFixture.groupScene();
			// Sync on, every Share switch off: the card opens by itself.
			s.sharing = new SharingPrompt.Settings(GimPanelFixture.KEY, true, false, false, false, true);
			GimPanelFixture f = panel(s);
			f.ui.assertShows("Share with your group?");
			f.ui.click("Turn on sharing");
			assertTrue(f.actions.calls.contains("turnOnSharing"));
			f.ui.click("Post my events to Discord");
			assertTrue(f.actions.calls.contains("postToDiscord"));
			f.ui.click("Not now");
			f.ui.assertHides("Share with your group?");
		});
	}

	@Test
	public void noShareCardWhileEverythingIsShared()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = panel(GimPanelFixture.groupScene());
			f.ui.assertHides("Share with your group?");
		});
	}

	// ---- Paste group key ----

	@Test
	public void pasteWithNoKeyOnTheClipboardSaysSo()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture.Scene s = GimPanelFixture.groupScene();
			s.sync = GroupSync.State.NO_KEY;
			GimPanelFixture f = panel(s);
			f.actions.pasteAccepts = false;
			f.ui.assertShows("No group key yet");
			f.ui.button("Create group key");
			f.ui.click("Paste group key");
			assertTrue(f.actions.calls.get(0).startsWith("pasteKey:"));
			f.ui.assertShows("Your clipboard has no group key");
		});
	}

	/** Needs a display: puts a made-up key on the system clipboard, then restores what was there. */
	@Test
	public void pasteReadsTheKeyFromTheClipboardAndOpensTheShareCard()
	{
		Assume.assumeFalse("no system clipboard without a display", GraphicsEnvironment.isHeadless());
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		Transferable before = clipboard.getContents(null);
		try
		{
			clipboard.setContents(new StringSelection(GimPanelFixture.KEY), null);
			SwingDriver.onEdt(() ->
			{
				GimPanelFixture.Scene s = GimPanelFixture.groupScene();
				s.sync = GroupSync.State.NO_KEY;
				// After the paste: sync on, some sharing on, not all of it.
				s.sharing = new SharingPrompt.Settings(GimPanelFixture.KEY, true, true, false, false, false);
				GimPanelFixture f = panel(s);
				f.ui.click("Paste group key");
				assertEquals("pasteKey:" + GimPanelFixture.KEY, f.actions.calls.get(0));
				f.ui.assertShows("Key saved. Joining your group's sync.");
				f.ui.assertShows("Share with your group?");
			});
		}
		finally
		{
			restore(clipboard, before);
		}
	}

	/** Needs a display: Copy group key writes the system clipboard. */
	@Test
	public void copyPutsTheKeyOnTheClipboard() throws Exception
	{
		Assume.assumeFalse("no system clipboard without a display", GraphicsEnvironment.isHeadless());
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		Transferable before = clipboard.getContents(null);
		try
		{
			SwingDriver.onEdt(() ->
			{
				GimPanelFixture f = panel(GimPanelFixture.groupScene());
				f.ui.click("Copy group key");
				f.ui.assertShows("Key copied.");
			});
			assertEquals(GimPanelFixture.KEY, clipboard.getData(DataFlavor.stringFlavor));
		}
		finally
		{
			restore(clipboard, before);
		}
	}

	private static void restore(Clipboard clipboard, Transferable before)
	{
		if (before != null)
		{
			clipboard.setContents(before, null);
		}
	}

	// ---- Group ----

	@Test
	public void groupTabHasACardPerMemberWithHiscoresForTheOneWithoutThePlugin()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = panel(GimPanelFixture.groupScene());
			f.ui.assertShows(SELF + " (you)");
			f.ui.assertShows(MOSS);
			f.ui.assertShows("From hiscores, 2h ago");
			f.ui.assertShows("Online - W416 (no plugin)");
		});
	}

	@Test
	public void aMemberCardFoldsFromItsHeader()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = panel(GimPanelFixture.groupScene());
			assertTrue(arrow(f.ui.label(MOSS)).open);
			f.ui.click(f.ui.label(MOSS));
			assertFalse(arrow(f.ui.label(MOSS)).open);
			f.ui.click(f.ui.label(MOSS));
			assertTrue(arrow(f.ui.label(MOSS)).open);
		});
	}

	private static GroupIronmanPanel.Arrow arrow(JLabel header)
	{
		return (GroupIronmanPanel.Arrow) header.getIcon();
	}

	// ---- Banks ----

	@Test
	public void banksCollapseAllThenExpandAll()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = panel(GimPanelFixture.bankScene()).tab("Banks");
			f.ui.assertShows("Your bank (" + SELF + ")");
			f.ui.assertShows(MOSS + "'s bank");
			f.ui.click("Collapse all");
			assertFalse(arrow(f.ui.label(MOSS + "'s bank")).open);
			assertFalse(arrow(f.ui.label("Your bank")).open);
			f.ui.click("Expand all");
			assertTrue(arrow(f.ui.label(MOSS + "'s bank")).open);
		});
	}

	@Test
	public void bankFilterFoldsBanksWithoutAMatch()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = panel(GimPanelFixture.bankScene()).tab("Banks");
			f.ui.type(f.ui.field("Filter banks"), "whip");
			assertTrue(arrow(f.ui.label(MOSS + "'s bank")).open);
			assertFalse(arrow(f.ui.label("Your bank")).open);
		});
	}

	@Test
	public void banksTabSaysWhenBankSharingIsOff()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture.Scene s = GimPanelFixture.bankScene();
			s.switches = new GroupOutbox.Switches(false, true, true, true, true, true);
			GimPanelFixture f = panel(s).tab("Banks");
			f.ui.assertShows("Bank sharing is off.");
		});
	}

	// ---- Storage ----

	@Test
	public void storageShowsTheSnapshotAndTheVisitsAndFilters()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = panel(GimPanelFixture.storageScene()).tab("Storage");
			f.ui.assertShows("Group storage");
			f.ui.assertShows("Changes");
			f.ui.assertShows(MOSS);
			f.ui.assertShows(WREN);
			f.ui.type(f.ui.field("Filter storage"), "zzzz");
			f.ui.assertShows("Empty, or no item matches the filter.");
		});
	}

	@Test
	public void storageWarnsWhenItsSwitchIsOff()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture.Scene s = GimPanelFixture.storageScene();
			s.switches = new GroupOutbox.Switches(true, false, true, true, true, true);
			GimPanelFixture f = panel(s).tab("Storage");
			f.ui.assertShows("'Share group storage changes' is off");
		});
	}

	// ---- Find ----

	@Test
	public void findAnswersWhoHasAnItem()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = panel(GimPanelFixture.storageScene()).tab("Find");
			f.ui.assertShows("Who has it?");
			f.ui.type(f.ui.field("Item name"), "shark");
			f.ui.assertHides("Who has it?");
			f.ui.assertShows("Shark");
			f.ui.type(f.ui.field("Item name"), "dragon claws");
			f.ui.assertShows("Nobody has it.");
		});
	}

	// ---- Log ----

	@Test
	public void logListsEveryonesEventsAndAChipNarrowsThem()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = panel(GimPanelFixture.logScene()).tab("Log");
			f.ui.assertShows("Herblore");
			f.ui.assertShows(TIN);
			f.ui.click("Deaths");
			f.ui.assertShows(TIN);
			f.ui.assertHides("Herblore");
			f.ui.click("Deaths");
			f.ui.assertShows("Herblore");
		});
	}

	@Test
	public void logSearchFindsByTextAndSaysWhenNothingMatches()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = panel(GimPanelFixture.logScene()).tab("Log");
			f.ui.type(f.ui.field("Search the log"), "slayer");
			f.ui.assertShows("Slayer");
			f.ui.assertHides("Herblore");
			f.ui.type(f.ui.field("Search the log"), "nothing like this");
			f.ui.assertShows("Nothing matches.");
		});
	}

	@Test
	public void emptyLogExplainsWhatWillAppear()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = panel(GimPanelFixture.groupScene()).tab("Log");
			f.ui.assertShows("Nothing logged yet.");
		});
	}

	// ---- Settings ----

	private static GimPanelFixture settings(String self, boolean syncing)
	{
		GimPanelFixture.Scene s = GimPanelFixture.groupScene();
		s.self = self;
		s.settings = new GimPanelFixture.Governance().led(SELF, MOSS, s.now).view(self, syncing);
		return panel(s).tab("Settings");
	}

	@Test
	public void leaderSeesEditorsAndSavesToTheGroup()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = settings(SELF, true);
			f.ui.assertShows("Editors");
			f.ui.assertShows("Edit group settings");
			f.ui.button("Remove editor");
			f.ui.click("Save to group");
			assertTrue(f.actions.calls.contains("save"));
			assertEquals(Long.valueOf(70), f.actions.saved.get("eventLevelMin"));
			f.ui.assertShows("Saved and sent to your group.");
		});
	}

	@Test
	public void aRefusedSaveSaysWhy()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = settings(SELF, true);
			f.actions.saveResult = GovernanceSync.SaveResult.NOT_EDITOR;
			f.ui.click("Save to group");
			f.ui.assertShows("Not saved: you are no longer leader or editor.");
		});
	}

	@Test
	public void leaderMakesAMemberAnEditor()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = settings(SELF, true);
			f.ui.click(f.ui.all(AbstractButton.class).stream()
				.filter(b -> "Make editor".equals(b.getText())).findFirst().orElseThrow(AssertionError::new));
			assertTrue(f.actions.calls.toString(), f.actions.calls.get(0).startsWith("setEditor:"));
			assertTrue(f.actions.calls.get(0).endsWith("=true"));
		});
	}

	@Test
	public void memberSeesTheGroupsSettingsReadOnly()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = settings(WREN, true);
			f.ui.assertShows("Your group's settings");
			f.ui.assertShows("You are a member: view only.");
			f.ui.assertHides("Make editor");
			f.ui.assertHides("Remove editor");
			f.ui.assertHides("Save to group");
			f.ui.assertHides("Who posts what");
			// "What counts" starts open; its numbers are the group's, greyed out.
			f.ui.assertShows("- " + GroupSettingsSchema.THRESHOLDS);
			f.ui.assertShows("Level ups from level");
			assertFalse(f.ui.all(JSpinner.class).isEmpty());
			for (JSpinner s : f.ui.all(JSpinner.class))
			{
				assertFalse("a member cannot edit", s.isEnabled());
			}
		});
	}

	@Test
	public void editorCanEditButHasNoEditorList()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = settings(MOSS, true);
			f.ui.assertShows("Edit group settings");
			f.ui.assertHides("Make editor");
			f.ui.button("Save to group");
		});
	}

	@Test
	public void whoPostsWhatShowsEachMembersChoice()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = settings(SELF, true);
			f.ui.click("+ Who posts what");
			f.ui.assertShows("- Who posts what");
			f.ui.assertShows("Each member's own choice");
			assertTrue("Tin does not post deaths", f.ui.texts().contains("no"));
		});
	}

	@Test
	public void myEventsOptOutCallsTheAction()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = settings(WREN, true);
			JCheckBox all = (JCheckBox) f.ui.button("Don't post my events");
			f.ui.click(all);
			assertEquals("optOutAll=true", f.actions.calls.get(0));
		});
	}

	@Test
	public void settingsSayToJoinWhenNotSyncing()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = settings(SELF, false);
			f.ui.assertShows("Join group sync to send or receive group settings.");
		});
	}
}
