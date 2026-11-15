package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Fight Caves, Inferno and Colosseum runs (docs/plans/activities.md §7 item 3): waves from the game's own lines,
 * entering and leaving the arena, solo vs a team, defeat at wave N, completion, a logout mid-run.
 */
public class WaveTrackerTest
{
	private static final String ME = "Ember Fox";
	private static final int FIGHT_CAVES = 9551;
	private static final int INFERNO = 9043;
	private static final int COLOSSEUM = 7216;
	private static final int COLOSSEUM_LOBBY = 7316;
	private static final int TZHAAR_CITY = 9808;
	private static final long MIN = 60_000;

	private final List<GroupEvent> out = new ArrayList<>();
	private String team = "solo";
	private WaveTracker t;
	private long now = 1_000_000;

	@Before
	public void setUp()
	{
		t = new WaveTracker(out::add, () -> team);
	}

	private void at(int region)
	{
		t.region(region, 302, now, ME);
	}

	private void chat(String line)
	{
		t.chat(line, now, ME);
	}

	/** The game colours the wave line; the tracker reads it without tags. */
	private void wave(int n)
	{
		chat("<col=ef1020>Wave: " + n + "</col>");
	}

	private List<GroupEvent> of(GroupEvent.Type type)
	{
		List<GroupEvent> kept = new ArrayList<>();
		out.stream().filter(e -> e.type == type).forEach(kept::add);
		return kept;
	}

	@Test
	public void fightCavesStartsOnWaveOneAndShowsTheCardLine()
	{
		at(FIGHT_CAVES);
		wave(1);
		assertEquals(1, of(GroupEvent.Type.ACTIVITY_START).size());
		assertEquals("solo", of(GroupEvent.Type.ACTIVITY_START).get(0).detail);
		now += 20 * MIN;
		wave(37);
		ActivityState s = t.state(now);
		assertTrue(s.active());
		assertEquals("fight_caves", s.id);
		assertEquals("Fight Caves - wave 37/63 - solo", s.cardLine());
		assertEquals(37, s.wave);
		assertEquals(63, s.max);
		assertEquals("in Fight Caves, wave 35", s.board);
	}

	@Test
	public void waveLinesOutsideAnArenaAreIgnored()
	{
		at(TZHAAR_CITY);
		wave(1);
		assertFalse(t.active());
		assertTrue(out.isEmpty());
	}

	@Test
	public void fightCavesOneToSixtyThreeClearPostsMilestonesAndCompletion()
	{
		t.every(10);
		at(FIGHT_CAVES);
		for (int w = 1; w <= 63; w++)
		{
			wave(w);
			now += 30_000;
		}
		List<Long> waves = new ArrayList<>();
		of(GroupEvent.Type.ACTIVITY_WAVE).forEach(e -> waves.add(e.number));
		assertEquals(java.util.Arrays.asList(10L, 20L, 30L, 40L, 50L, 60L, 63L), waves);
		chat("Your TzTok-Jad kill count is: <col=ff0000>4</col>.");
		chat("Duration: <col=ff0000>31:40</col> (new personal best)");
		t.tick(now, ME);
		List<GroupEvent> done = of(GroupEvent.Type.ACTIVITY_COMPLETE);
		assertEquals(1, done.size());
		assertEquals(4, done.get(0).number);
		assertEquals("Fight Caves", done.get(0).text);
		assertTrue(done.get(0).detail, done.get(0).detail.startsWith("31:40"));
		assertFalse("the run ends with the kill", t.active());
		assertTrue(of(GroupEvent.Type.ACTIVITY_DEATH).isEmpty());
	}

	@Test
	public void infernoOneToSixtyNineMarksTripleJadsAndZuk()
	{
		t.every(10);
		at(INFERNO);
		for (int w = 1; w <= 69; w++)
		{
			wave(w);
		}
		List<Long> waves = new ArrayList<>();
		of(GroupEvent.Type.ACTIVITY_WAVE).forEach(e -> waves.add(e.number));
		assertEquals(java.util.Arrays.asList(10L, 20L, 30L, 40L, 50L, 60L, 68L, 69L), waves);
		assertEquals("in The Inferno, wave 69", t.state(now).board);
		chat("Your TzKal-Zuk kill count is: 1.");
		t.tick(now, ME);
		assertEquals(1, of(GroupEvent.Type.ACTIVITY_COMPLETE).size());
		assertEquals(1, of(GroupEvent.Type.ACTIVITY_COMPLETE).get(0).number);
	}

	@Test
	public void waveEveryZeroPostsBossWavesOnly()
	{
		t.every(0);
		at(FIGHT_CAVES);
		for (int w = 1; w <= 63; w++)
		{
			wave(w);
		}
		assertEquals(1, of(GroupEvent.Type.ACTIVITY_WAVE).size());
		assertEquals(63, of(GroupEvent.Type.ACTIVITY_WAVE).get(0).number);
	}

	@Test
	public void deathAtWaveFiftyEightPostsOnceEvenWithTheDefeatLineAfter()
	{
		at(INFERNO);
		for (int w = 1; w <= 58; w++)
		{
			wave(w);
		}
		now += 72 * MIN;
		assertTrue("inside a run: the activity death covers the plain one", t.died(now, ME));
		now += 600;
		chat("You have been defeated!");
		List<GroupEvent> deaths = of(GroupEvent.Type.ACTIVITY_DEATH);
		assertEquals(1, deaths.size());
		assertEquals(58, deaths.get(0).number);
		assertTrue(deaths.get(0).detail, deaths.get(0).detail.startsWith("wave 58/69"));
		assertEquals(302, deaths.get(0).world);
		assertFalse(t.active());
	}

	@Test
	public void colosseumDoomDefeatWithoutActorDeathStillPostsWithModifiers()
	{
		at(COLOSSEUM);
		wave(1);
		for (int w = 2; w <= 11; w++)
		{
			chat("Wave " + (w - 1) + " completed! Wave duration: 1:02");
			wave(w);
		}
		t.colosseum("Doom II, Mantimayhem II", 24_800);
		chat("You have been defeated!");
		List<GroupEvent> deaths = of(GroupEvent.Type.ACTIVITY_DEATH);
		assertEquals(1, deaths.size());
		assertEquals(11, deaths.get(0).number);
		assertTrue(deaths.get(0).detail, deaths.get(0).detail.contains("Doom II, Mantimayhem II"));
		now += 1_000;
		assertTrue("the death a tick later is the same one", t.died(now, ME));
		assertEquals(1, of(GroupEvent.Type.ACTIVITY_DEATH).size());
	}

	@Test
	public void colosseumShowsGloryAndModifiers()
	{
		at(COLOSSEUM);
		wave(1);
		wave(9);
		t.colosseum("Mantimayhem II, Myopia I", 14_200);
		ActivityState s = t.state(now);
		assertEquals("14,200 glory", s.stats);
		assertEquals("Mantimayhem II, Myopia I", s.mods);
		assertEquals("wave 9/12", s.phase);
	}

	@Test
	public void colosseumEarlyClaimLeavesWithoutAFakeKill()
	{
		at(COLOSSEUM);
		wave(1);
		for (int w = 2; w <= 7; w++)
		{
			chat("Wave " + (w - 1) + " completed! Wave duration: 0:50");
			wave(w);
		}
		chat("Wave 7 completed! Wave duration: 0:58");
		at(COLOSSEUM_LOBBY);
		List<GroupEvent> done = of(GroupEvent.Type.ACTIVITY_COMPLETE);
		assertEquals(1, done.size());
		assertEquals("no Sol Heredit kill count", 0, done.get(0).number);
		assertEquals("claimed loot after wave 7", done.get(0).detail);
		assertFalse(t.active());
	}

	@Test
	public void solHereditLineIsWaveTwelve()
	{
		at(COLOSSEUM);
		wave(1);
		chat("Sol Heredit jumps down from his seat...");
		assertEquals(12, t.state(now).wave);
		assertEquals(1, of(GroupEvent.Type.ACTIVITY_WAVE).size());
	}

	@Test
	public void voluntaryCaveExitClearsQuietly()
	{
		at(FIGHT_CAVES);
		wave(1);
		wave(5);
		t.takeChanged();
		at(TZHAAR_CITY);
		assertFalse(t.active());
		assertTrue(t.takeChanged());
		assertTrue("no death, no completion", of(GroupEvent.Type.ACTIVITY_DEATH).isEmpty());
		assertTrue(of(GroupEvent.Type.ACTIVITY_COMPLETE).isEmpty());
		assertFalse("a death after leaving is a plain death", t.died(now + 10 * MIN, ME));
	}

	@Test
	public void loadingRegionDoesNotEndARun()
	{
		at(FIGHT_CAVES);
		wave(3);
		t.region(-1, 302, now, ME);
		assertTrue(t.active());
	}

	@Test
	public void logoutMidWaveFortyTwoResumesOnFortyThreeKeepingTime()
	{
		at(INFERNO);
		wave(1);
		now += 40 * MIN;
		wave(42);
		t.pause(now);
		String saved = t.save();
		assertTrue(saved, saved.startsWith("inferno,42,"));

		WaveTracker next = new WaveTracker(out::add, () -> team);
		next.restore(saved);
		now += 3 * 60 * MIN;
		assertEquals("paused while logged out", 40 * MIN, next.elapsed(now));
		next.region(INFERNO, 302, now, ME);
		now += 2 * MIN;
		next.chat("Wave: 43", now, ME);
		assertEquals(43, next.state(now).wave);
		assertEquals(42 * MIN, next.elapsed(now));
		assertEquals("no second start", 1, of(GroupEvent.Type.ACTIVITY_START).size());
	}

	@Test
	public void reprintedMilestoneWaveAfterRelogIsNotPostedTwice()
	{
		at(FIGHT_CAVES);
		wave(1);
		wave(40);
		wave(40);
		assertEquals(1, of(GroupEvent.Type.ACTIVITY_WAVE).size());
	}

	@Test
	public void unreadableSaveIsIgnored()
	{
		t.restore("inferno,x,1,2,3");
		assertFalse(t.active());
		t.restore("nonsense");
		assertFalse(t.active());
		t.restore(null);
		assertFalse(t.active());
	}

	@Test
	public void teamModeIsReadAtTheStart()
	{
		team = "duo with Moss Kettle";
		at(FIGHT_CAVES);
		wave(1);
		assertEquals("Fight Caves - wave 1/63 - duo with Moss Kettle", t.state(now).cardLine());
	}

	@Test
	public void killCountForAnotherBossDoesNotCompleteTheRun()
	{
		at(FIGHT_CAVES);
		wave(12);
		chat("Your Vorkath kill count is: 50.");
		t.tick(now, ME);
		assertTrue(t.active());
		assertTrue(of(GroupEvent.Type.ACTIVITY_COMPLETE).isEmpty());
	}
}
