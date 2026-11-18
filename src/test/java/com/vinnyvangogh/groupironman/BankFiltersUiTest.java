package com.vinnyvangogh.groupironman;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import com.vinnyvangogh.testkit.ui.SwingDriver;
import static org.junit.Assert.assertTrue;

/** Click through the Banks and Storage pills, the bank preview and the Find quick-find, as a player would. */
public class BankFiltersUiTest
{
	private static GimPanelFixture.Scene scene()
	{
		GimPanelFixture.Scene s = GimPanelFixture.bankScene();
		// 30 more kinds of item beside the sharks and coal: well past the 20-stack preview.
		Map<Integer, Integer> big = new TreeMap<>(s.roster.banks().get(GimPanelFixture.SELF).items());
		for (int i = 0; i < 30; i++)
		{
			big.put(5000 + i, 1 + i);
		}
		big.put(FakeItems.WHIP, 1);
		big.put(FakeItems.AIR_RUNE, 900);
		s.roster.putBank(GimPanelFixture.SELF, new BankSnapshot(s.now - 4 * 60_000, big));
		s.roster.putStorage(new StorageChange(s.now - 60_000, GimPanelFixture.MOSS, new TreeMap<>(),
			GimPanelFixture.items(FakeItems.AIR_RUNE, 400, FakeItems.WHIP, 1, FakeItems.SHARK, 20),
			GimPanelFixture.items(FakeItems.AIR_RUNE, 400, FakeItems.WHIP, 1, FakeItems.SHARK, 20)));
		return s;
	}

	@Test
	public void theBankDrawsTopStacksAndSaysHowManyAreLeftOut()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = new GimPanelFixture().show(scene()).tab("Banks");
			f.ui.assertShows("most valuable shown");
			f.ui.assertShows("more: search, pick a category or turn on Show all items.");
			shot(f, "banks-preview-top-20");

			f.ui.click("Show all items");
			f.ui.layout(GimPanelFixture.PANEL_WIDTH);
			f.ui.assertHides("most valuable shown");
		});
	}

	@Test
	public void aCategoryPillNarrowsTheBank()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = new GimPanelFixture().show(scene()).tab("Banks");
			f.ui.click("Runes");
			f.ui.layout(GimPanelFixture.PANEL_WIDTH);
			f.ui.assertHides("most valuable shown");
			shot(f, "banks-runes-pill");
			f.ui.click("Magic");
			f.ui.layout(GimPanelFixture.PANEL_WIDTH);
			f.ui.assertHides("most valuable shown");
		});
	}

	@Test
	public void storageHasThePillsToo()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = new GimPanelFixture().show(scene()).tab("Storage");
			f.ui.click("Melee");
			f.ui.layout(GimPanelFixture.PANEL_WIDTH);
			shot(f, "storage-melee-pill");
			f.ui.assertShows("Group storage");
		});
	}

	@Test
	public void findButtonsAndDropdownsSearchWithoutTyping()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = new GimPanelFixture().show(scene()).tab("Find");
			f.ui.assertShows("Runes anywhere");
			f.ui.assertShows("Who has it?");

			f.ui.click("Runes anywhere");
			f.ui.layout(GimPanelFixture.PANEL_WIDTH);
			f.ui.assertShows("Air rune - 900");
			f.ui.assertShows("Air rune - 400 in group storage");
			f.ui.assertHides("Abyssal whip");
			shot(f, "find-quick-runes");

			f.ui.click("Food");
			f.ui.layout(GimPanelFixture.PANEL_WIDTH);
			f.ui.assertShows("Shark");
			f.ui.assertHides("Air rune");
		});
	}

	private static void shot(GimPanelFixture f, String name)
	{
		BufferedImage img = f.ui.render(GimPanelFixture.PANEL_WIDTH, 0, ReadmeShots.SCALE);
		File file = new File(ReadmeShots.outputDir(), name + ".png");
		SwingDriver.writePng(img, file);
		assertTrue(file.getPath(), file.length() > 0);
	}
}
