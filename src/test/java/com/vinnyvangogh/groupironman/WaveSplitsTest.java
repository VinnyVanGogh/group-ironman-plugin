package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Per-wave splits, personal bests and the rotation as the tracker, the codec and the card rows carry them. */
public class WaveSplitsTest
{
	private static final String ME = "Ember Fox";
	private static final int FIGHT_CAVES = 9551;
	private static final int INFERNO = 9043;
	private static final int COLOSSEUM = 7216;

	private final List<GroupEvent> out = new ArrayList<>();
	private WaveTracker t;
	private long now = 1_000_000;

	@Before
	public void setUp()
	{
		t = new WaveTracker(out::add, () -> "solo");
	}

	private void wave(int n)
	{
		t.chat("<col=ef1020>Wave: " + n + "</col>", now, ME);
	}

	private static List<GroupEvent> of(List<GroupEvent> all, GroupEvent.Type type)
	{
		List<GroupEvent> kept = new ArrayList<>();
		all.stream().filter(e -> e.type == type).forEach(kept::add);
		return kept;
	}

	@Test
	public void splitsComeFromTheTimeBetweenWaveLines()
	{
		t.region(FIGHT_CAVES, 302, now, ME);
		wave(1);
		now += 42_000;
		wave(2);
		now += 38_000;
		wave(3);
		now += 10_000;
		ActivityState s = t.state(now);
		assertEquals("42,38", s.splits);
		assertEquals(10_000, s.waveMs);
		assertEquals(10_000, s.waveElapsed(now));
		assertEquals(Arrays.asList("Wave 1: 0:42", "Wave 2: 0:38", "Total: 1:20"), WaveSplits.rows(s.splits, ""));
	}

	@Test
	public void theTimeWhileLoggedOutIsNotInAWave()
	{
		t.region(FIGHT_CAVES, 302, now, ME);
		wave(1);
		now += 20_000;
		t.pause(now);
		now += 600_000;
		t.region(FIGHT_CAVES, 302, now, ME);
		now += 10_000;
		wave(2);
		assertEquals("30", t.state(now).splits);
	}

	@Test
	public void aWaveReprintedAfterARelogIsNotASecondSplit()
	{
		t.region(FIGHT_CAVES, 302, now, ME);
		wave(1);
		now += 30_000;
		wave(1);
		assertEquals("", t.state(now).splits);
		assertEquals(30_000, t.state(now).waveMs);
	}

	@Test
	public void personalBestKeepsTheFastestTimePerWaveAcrossRuns()
	{
		t.region(FIGHT_CAVES, 302, now, ME);
		wave(1);
		now += 50_000;
		wave(2);
		now += 40_000;
		wave(3);
		assertTrue(t.takeBestsChanged());
		assertEquals("50,40", t.state(now).bests);
		// A second run, faster on wave 1 and slower on wave 2.
		t.region(9808, 302, now, ME);
		t.region(FIGHT_CAVES, 302, now, ME);
		wave(1);
		now += 45_000;
		wave(2);
		now += 70_000;
		wave(3);
		assertEquals("45,40", t.state(now).bests);
		assertEquals(Arrays.asList("Wave 1: 0:45 (best 0:45)", "Wave 2: 1:10 (best 0:40)", "Total: 1:55"),
			WaveSplits.rows(t.state(now).splits, t.state(now).bests));
	}

	@Test
	public void bestsSurviveASaveAndRestore()
	{
		t.region(INFERNO, 302, now, ME);
		wave(1);
		now += 90_000;
		wave(2);
		String saved = t.saveBests();
		assertEquals("inferno:90", saved);
		WaveTracker fresh = new WaveTracker(out::add, () -> "solo");
		fresh.restoreBests(saved);
		fresh.region(INFERNO, 302, now, ME);
		fresh.chat("Wave: 1", now, ME);
		assertEquals("90", fresh.state(now).bests);
		fresh.restoreBests("garbage|inferno:x,y|:5");
		assertEquals("", fresh.saveBests());
	}

	@Test
	public void aRunResumesWithItsSplitsAndRotation()
	{
		t.region(FIGHT_CAVES, 302, now, ME);
		wave(1);
		t.spawn("C", now);
		now += 30_000;
		wave(2);
		t.spawn("NW", now);
		t.spawn("SW", now);
		now += 25_000;
		wave(3);
		now += 5_000;
		// A logout pauses the run's clock before it is saved.
		t.pause(now);
		String saved = t.save();
		WaveTracker back = new WaveTracker(out::add, () -> "solo");
		back.restore(saved);
		back.region(FIGHT_CAVES, 302, now, ME);
		ActivityState s = back.state(now);
		assertEquals("30,25", s.splits);
		assertEquals("Rotation 1", s.rotation);
		assertEquals(3, s.wave);
		assertEquals(5_000, s.waveMs);
	}

	@Test
	public void anOlderSavedRunStillRestores()
	{
		t.restore("fight_caves,12,5,60000,0");
		assertTrue(t.active());
		assertEquals(12, t.state(now).wave);
	}

	@Test
	public void fightCavesRotationFromSpawnsBeforeOrAfterTheWaveLine()
	{
		t.region(FIGHT_CAVES, 302, now, ME);
		// Wave 1's monster shows up a moment before the line.
		t.spawn("NW", now - 500);
		wave(1);
		assertEquals("unknown", t.state(now).rotation);
		now += 20_000;
		wave(2);
		t.spawn("C", now + 600);
		t.spawn("SE", now + 700);
		assertEquals("Rotation 5", t.state(now).rotation);
	}

	@Test
	public void spawnsOutsideTheWindowOrOutsideTheCaveAreIgnored()
	{
		t.region(FIGHT_CAVES, 302, now, ME);
		wave(1);
		now += 60_000;
		t.spawn("C", now);
		assertEquals("unknown", t.state(now).rotation);
		WaveTracker elsewhere = new WaveTracker(out::add, () -> "solo");
		elsewhere.region(INFERNO, 302, now, ME);
		elsewhere.spawn("C", now);
		elsewhere.chat("Wave: 1", now, ME);
		assertEquals("no rotation", elsewhere.state(now).rotation);
	}

	@Test
	public void infernoSaysItHasNoRotationAndColosseumShowsItsModifiers()
	{
		t.region(INFERNO, 302, now, ME);
		wave(1);
		assertEquals("no rotation", t.state(now).rotation);
		WaveTracker c = new WaveTracker(out::add, () -> "solo");
		c.region(COLOSSEUM, 302, now, ME);
		c.chat("Wave: 1", now, ME);
		c.colosseum("Myopia I", 0);
		ActivityState s = c.state(now);
		assertEquals("", s.rotation);
		assertEquals("Myopia I", s.waveLine(now));
	}

	@Test
	public void colosseumSplitEndsWhenTheWaveIsCleared()
	{
		t.region(COLOSSEUM, 302, now, ME);
		wave(1);
		now += 70_000;
		t.chat("Wave 1 completed!", now, ME);
		now += 30_000;
		wave(2);
		assertEquals("70", t.state(now).splits);
	}

	@Test
	public void completionPostsTotalAndNewBestWaves()
	{
		t.region(FIGHT_CAVES, 302, now, ME);
		wave(1);
		t.spawn("C", now);
		now += 40_000;
		wave(2);
		now += 30_000;
		t.chat("Your TzTok-Jad kill count is: <col=ff0000>2</col>.", now, ME);
		t.chat("Duration: <col=ff0000>1:10</col>", now, ME);
		t.tick(now, ME);
		GroupEvent done = of(out, GroupEvent.Type.ACTIVITY_COMPLETE).get(0);
		assertTrue(done.detail, done.detail.startsWith("1:10"));
		assertTrue(done.detail, done.detail.contains("unknown"));
		assertTrue(done.detail, done.detail.contains("new best waves 1 0:40, 2 0:30"));
	}

	@Test
	public void deathPostsTheRotationAndTheWaveTime()
	{
		t.region(FIGHT_CAVES, 302, now, ME);
		wave(1);
		now += 12_000;
		assertTrue(t.died(now, ME));
		GroupEvent dead = of(out, GroupEvent.Type.ACTIVITY_DEATH).get(0);
		assertTrue(dead.detail, dead.detail.contains("wave time 0:12"));
		assertTrue(dead.detail, dead.detail.contains("unknown"));
	}

	@Test
	public void splitsAndRotationTravelAndAreBoundedOnTheWire()
	{
		Gson gson = new Gson();
		ActivityState s = new ActivityState.Builder("fight_caves", 5).name("Fight Caves").rotation("Rotation 7 or 8")
			.waveMs(12_000).splits("40,30").bests("38,31").wave(3).max(63).build();
		ActivityState back = ActivityCodec.decode(gson, ActivityCodec.encode(gson, s));
		assertNotNull(back);
		assertEquals("Rotation 7 or 8", back.rotation);
		assertEquals(12_000, back.waveMs);
		assertEquals("40,30", back.splits);
		assertEquals("38,31", back.bests);
		String hostile = "{\"v\":1,\"k\":\"activity\",\"at\":1,\"id\":\"inferno\",\"name\":\"x\",\"splits\":\"4<b>0,9\","
			+ "\"rotation\":\"<html>" + new String(new char[100]).replace('\0', 'r') + "\"}";
		ActivityState clean = ActivityCodec.decode(gson, hostile);
		assertNotNull(clean);
		assertEquals("40,9", clean.splits);
		assertEquals(ActivityCodec.MAX_ROTATION, clean.rotation.length());
		assertFalse(clean.rotation.contains("<"));
	}

	@Test
	public void cardRowsShowRotationWaveTimeAndSplits()
	{
		ActivityState s = new ActivityState.Builder("fight_caves", 1_000).name("Fight Caves").mode("solo")
			.phase("wave 3/63").wave(3).max(63).rotation("Rotation 2").waveMs(9_000).splits("40,30").bests("38,35")
			.build();
		java.util.Map<String, ActivityState> map = new java.util.HashMap<>();
		map.put(ME, s);
		ActivityRows.Card card = ActivityRows.live(map, ActivityRows.Filter.ALL, ME, 3_000).get(0);
		assertTrue(card.lines.toString(), card.lines.contains("Rotation 2"));
		assertTrue(card.lines.toString(), card.lines.contains("This wave: 0:11"));
		assertEquals(Arrays.asList("Wave 1: 0:40 (best 0:38)", "Wave 2: 0:30 (best 0:35)", "Total: 1:10"), card.splits);
		assertEquals("Rotation 2 - this wave 0:11", s.waveLine(3_000));
	}
}
