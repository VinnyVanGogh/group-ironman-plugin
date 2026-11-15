package com.vinnyvangogh.groupironman;

import com.vinnyvangogh.testkit.ui.RuneLiteLook;
import com.vinnyvangogh.testkit.ui.SwingDriver;
import java.awt.image.BufferedImage;
import java.io.File;
import org.junit.Before;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.GimPanelFixture.MIN;
import static com.vinnyvangogh.groupironman.GimPanelFixture.MOSS;
import static com.vinnyvangogh.groupironman.GimPanelFixture.SELF;
import static org.junit.Assert.assertTrue;

/**
 * The side panel with a member mid-Inferno (docs/plans/activities.md §4.1, §4.2), headless: the Group tab's card
 * line and the Activities tab's live card and recent runs. Writes both shots to build/screenshots.
 */
public class ActivityPanelTest
{
	@Before
	public void lookLikeRuneLite()
	{
		SwingDriver.onEdt(RuneLiteLook::install);
	}

	/** Moss is on Inferno wave 42 for 50 minutes; Ember died in the Colosseum earlier and is in the Fight Caves now. */
	static GimPanelFixture.Scene midInferno()
	{
		GimPanelFixture.Scene s = GimPanelFixture.groupScene();
		s.roster.putActivity(MOSS, new ActivityState.Builder("inferno", s.now - MIN).name("The Inferno").mode("solo")
			.phase("wave 42/69").board("in The Inferno, wave 40").wave(42).max(69).startedAt(s.now - 51 * MIN)
			.elapsedMs(49 * MIN).build());
		s.roster.putActivity(SELF, new ActivityState.Builder("fight_caves", s.now).name("Fight Caves").mode("solo")
			.phase("wave 37/63").board("in Fight Caves, wave 35").wave(37).max(63).startedAt(s.now - 20 * MIN)
			.elapsedMs(20 * MIN).build());
		s.roster.feed().add(GroupEvent.of(GroupEvent.Type.ACTIVITY_DEATH, s.now - 41 * MIN, SELF)
			.text("Fortis Colosseum").number(11).detail("wave 11/12, 24m, Doom II").build());
		s.roster.feed().add(GroupEvent.of(GroupEvent.Type.ACTIVITY_COMPLETE, s.now - 2 * 60 * MIN, MOSS)
			.text("Fight Caves").number(3).detail("48:12").build());
		return s;
	}

	@Test
	public void groupTabCardShowsTheWave()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = new GimPanelFixture().show(midInferno());
			f.ui.assertShows("The Inferno - wave 42/69 - solo");
			f.ui.assertShows("Fight Caves - wave 37/63 - solo");
			shot(f, "activity-group-card");
		});
	}

	@Test
	public void activitiesTabShowsLiveCardsAndRecentRuns()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = new GimPanelFixture().show(midInferno()).tab("Activities");
			f.ui.assertShows("Live now");
			f.ui.assertShows(MOSS);
			f.ui.assertShows("The Inferno - wave 42/69");
			f.ui.assertShows("solo - 50m");
			f.ui.assertShows(SELF + " (you)");
			f.ui.assertShows("Recent runs");
			shot(f, "activity-tab-inferno");
		});
	}

	@Test
	public void minigamesFilterHidesTheBossRuns()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = new GimPanelFixture().show(midInferno()).tab("Activities");
			f.ui.click("Minigames");
			f.ui.layout(GimPanelFixture.PANEL_WIDTH);
			f.ui.assertShows("No group members in a boss or minigame right now.");
			f.ui.assertHides("The Inferno - wave 42/69");
		});
	}

	@Test
	public void nobodyInAnActivitySaysSo()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = new GimPanelFixture().show(GimPanelFixture.groupScene()).tab("Activities");
			f.ui.assertShows("No group members in a boss or minigame right now.");
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
