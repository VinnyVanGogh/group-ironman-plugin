package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Farming and bird house timers: decoded from recorded variable values, fire once, silent when switched off. */
public class FarmRunsTest
{
	/** A whole number of 20, 40 and 640 minute growth cycles since the epoch. */
	private static final long T = 1_800_000_000L;
	private static final int FALADOR = 12083;
	private static final int FALADOR_X = 3050;
	private static final int FALADOR_Y = 3300;
	private static final int CATHERBY = 11062;
	private static final int LUMBRIDGE_TREE = 12594;
	private static final int FOSSIL_ISLAND = 14651;
	private static final int FARMING_GUILD_HESPORI = 5021;
	private static final String ME = "Alex";

	private final List<GroupEvent> out = new ArrayList<>();
	private final Map<Integer, Integer> varbits = new HashMap<>();
	private final Map<Integer, Integer> varps = new HashMap<>();
	private final IntUnaryOperator readBit = id -> varbits.getOrDefault(id, 0);
	private final IntUnaryOperator readVarp = id -> varps.getOrDefault(id, 0);
	private EnumSet<GroupEvent.Type> on;
	private FarmRuns farm;

	@Before
	public void setUp()
	{
		on = EnumSet.allOf(GroupEvent.Type.class);
		farm = new FarmRuns(out::add, t -> on.contains(t));
	}

	private void atFalador(int value, long now)
	{
		varbits.put(VarbitID.FARMING_TRANSMIT_D, value);
		farm.observe(FALADOR, FALADOR_X, FALADOR_Y, 0, readBit, readVarp, now);
	}

	private void birds(int a, int b, int c, int d, long now)
	{
		varps.put(VarPlayerID.BIRDHOUSE_TRANSMIT_A, a);
		varps.put(VarPlayerID.BIRDHOUSE_TRANSMIT_B, b);
		varps.put(VarPlayerID.BIRDHOUSE_TRANSMIT_C, c);
		varps.put(VarPlayerID.BIRDHOUSE_TRANSMIT_D, d);
		farm.observe(FOSSIL_ISLAND, 3700, 3800, 0, readBit, readVarp, now);
	}

	private GroupEvent only()
	{
		assertEquals(1, out.size());
		return out.get(0);
	}

	// ---- Decoding, values from Time Tracking's PatchImplementation ----

	@Test
	public void herbsGrowForFourValuesThenAreReady()
	{
		assertEquals(0, FarmRuns.crop(FarmRuns.Kind.HERB, 4).stage);
		assertEquals(3, FarmRuns.crop(FarmRuns.Kind.HERB, 7).stage);
		assertEquals("torstol", 0, FarmRuns.crop(FarmRuns.Kind.HERB, 103).stage);
		assertEquals("huasca", 2, FarmRuns.crop(FarmRuns.Kind.HERB, 62).stage);
		assertEquals("goutweed", 1, FarmRuns.crop(FarmRuns.Kind.HERB, 193).stage);
	}

	@Test
	public void nothingGrowingIsNotACrop()
	{
		assertNull("weeds", FarmRuns.crop(FarmRuns.Kind.HERB, 3));
		assertNull("ready to pick", FarmRuns.crop(FarmRuns.Kind.HERB, 8));
		assertNull("between huasca and kwuarm", FarmRuns.crop(FarmRuns.Kind.HERB, 67));
		assertNull("diseased", FarmRuns.crop(FarmRuns.Kind.HERB, 128));
		assertNull("dead", FarmRuns.crop(FarmRuns.Kind.HERB, 170));
	}

	@Test
	public void treesGrowUntilTheirLastStage()
	{
		assertEquals(0, FarmRuns.crop(FarmRuns.Kind.TREE, 8).stage);
		assertEquals("oak has 5 stages", 5, FarmRuns.crop(FarmRuns.Kind.TREE, 8).stages);
		assertEquals("oak's last growing value", 3, FarmRuns.crop(FarmRuns.Kind.TREE, 11).stage);
		assertNull("oak fully grown", FarmRuns.crop(FarmRuns.Kind.TREE, 12));
		assertEquals("magic has 13 stages", 13, FarmRuns.crop(FarmRuns.Kind.TREE, 59).stages);
		assertEquals(11, FarmRuns.crop(FarmRuns.Kind.TREE, 59).stage);
		assertNull("magic fully grown", FarmRuns.crop(FarmRuns.Kind.TREE, 60));
		assertNull("diseased oak", FarmRuns.crop(FarmRuns.Kind.TREE, 73));
	}

	@Test
	public void hesporiGrowsThroughThreeValues()
	{
		assertEquals(0, FarmRuns.crop(FarmRuns.Kind.HESPORI, 4).stage);
		assertEquals(2, FarmRuns.crop(FarmRuns.Kind.HESPORI, 6).stage);
		assertNull("harvestable", FarmRuns.crop(FarmRuns.Kind.HESPORI, 7));
		assertNull("weeds", FarmRuns.crop(FarmRuns.Kind.HESPORI, 2));
	}

	@Test
	public void aBirdHouseIsOnlyTimedOnceSeeded()
	{
		assertNotNull("regular, seeded", FarmRuns.crop(FarmRuns.Kind.BIRD, 3));
		assertNotNull("redwood, seeded", FarmRuns.crop(FarmRuns.Kind.BIRD, 27));
		assertNull("empty", FarmRuns.crop(FarmRuns.Kind.BIRD, 0));
		assertNull("built, no seed", FarmRuns.crop(FarmRuns.Kind.BIRD, 1));
		assertNull("not a bird house", FarmRuns.crop(FarmRuns.Kind.BIRD, 30));
	}

	// ---- Timing, as Time Tracking's getTickTime with no offset ----

	@Test
	public void aHerbIsReadyAfterItsRemainingGrowthCycles()
	{
		assertEquals("stage 0, four cycles", T + 4 * 1200, FarmRuns.crop(FarmRuns.Kind.HERB, 4).readyAt(T));
		assertEquals("stage 3, one cycle", T + 1200, FarmRuns.crop(FarmRuns.Kind.HERB, 7).readyAt(T));
		assertEquals("mid-cycle sighting counts from that cycle", T + 4 * 1200,
			FarmRuns.crop(FarmRuns.Kind.HERB, 4).readyAt(T + 100));
	}

	@Test
	public void treesAndHesporiUseTheirOwnCycles()
	{
		assertEquals("oak", T + 4 * 2400, FarmRuns.crop(FarmRuns.Kind.TREE, 8).readyAt(T));
		assertEquals("hespori", T + 3 * 38400, FarmRuns.crop(FarmRuns.Kind.HESPORI, 4).readyAt(T));
	}

	@Test
	public void aBirdHouseTakesFiftyMinutesFromTheSighting()
	{
		assertEquals(T + 3000, FarmRuns.crop(FarmRuns.Kind.BIRD, 3).readyAt(T));
	}

	// ---- Firing ----

	@Test
	public void aPatchFiresOnceWhenDue()
	{
		atFalador(7, T);
		farm.due(T + 1199, ME);
		assertTrue("not due yet", out.isEmpty());

		farm.due(T + 1200, ME);
		GroupEvent e = only();
		assertEquals(GroupEvent.Type.FARMING, e.type);
		assertEquals(ME, e.member);
		assertEquals("Herbs", e.text);
		assertEquals("Falador", e.detail);

		farm.due(T + 9000, ME);
		assertEquals("fires once", 1, out.size());
		atFalador(7, T + 9000);
		farm.due(T + 20000, ME);
		assertEquals("seeing the same value again does not refire", 1, out.size());
	}

	@Test
	public void timeIsInSecondsAndAKindsPatchesPostTogether()
	{
		atFalador(7, T);
		varbits.put(VarbitID.FARMING_TRANSMIT_D, 7);
		farm.observe(CATHERBY, 2800, 3460, 0, readBit, readVarp, T);
		farm.due(T + 1200, ME);

		GroupEvent e = only();
		assertEquals(2, e.number);
		assertTrue(e.detail, e.detail.contains("Falador"));
		assertTrue(e.detail, e.detail.contains("Catherby"));
	}

	@Test
	public void aNewCropRearmsThePatch()
	{
		atFalador(7, T);
		farm.due(T + 1200, ME);
		assertEquals(1, out.size());

		atFalador(4, T + 1300);
		farm.due(T + 2000, ME);
		assertEquals("new crop not ready yet", 1, out.size());
		farm.due(T + 1200 + 4 * 1200, ME);
		assertEquals("new crop fires", 2, out.size());
	}

	@Test
	public void aPatchPickedBeforeItsTimeIsDropped()
	{
		atFalador(7, T);
		atFalador(3, T + 10);
		farm.due(T + 9000, ME);
		assertTrue(out.isEmpty());
	}

	@Test
	public void arrivingAtAlreadyReadyPatchesIsSilent()
	{
		atFalador(8, T);
		farm.due(T + 90000, ME);
		assertTrue(out.isEmpty());
	}

	// ---- Where variables are read ----

	@Test
	public void varsAreIgnoredOutsideThePatchsRegion()
	{
		varbits.put(VarbitID.FARMING_TRANSMIT_D, 7);
		farm.observe(1234, 3000, 3000, 0, readBit, readVarp, T);
		farm.due(T + 9000, ME);
		assertTrue(out.isEmpty());
	}

	@Test
	public void faladorHerbsNeedThePatchsSideOfTheRegion()
	{
		varbits.put(VarbitID.FARMING_TRANSMIT_D, 7);
		farm.observe(FALADOR, FALADOR_X, 3271, 0, readBit, readVarp, T);
		farm.due(T + 9000, ME);
		assertTrue("Port Sarim's side", out.isEmpty());
	}

	@Test
	public void catherbysUpstairsIsSkipped()
	{
		varbits.put(VarbitID.FARMING_TRANSMIT_D, 7);
		farm.observe(CATHERBY, 2830, 3400, 0, readBit, readVarp, T);
		farm.due(T + 9000, ME);
		assertTrue(out.isEmpty());
	}

	@Test
	public void treesAreWatchedFromTheirOwnVariable()
	{
		varbits.put(VarbitID.FARMING_TRANSMIT_A, 8);
		farm.observe(LUMBRIDGE_TREE, 3200, 3220, 0, readBit, readVarp, T);
		farm.due(T + 4 * 2400, ME);
		GroupEvent e = only();
		assertEquals(GroupEvent.Type.FARMING, e.type);
		assertEquals("Trees", e.text);
		assertEquals("Lumbridge", e.detail);
	}

	@Test
	public void hesporiPostsWithTheBirdHouseKind()
	{
		varbits.put(VarbitID.FARMING_TRANSMIT_J, 6);
		farm.observe(FARMING_GUILD_HESPORI, 1250, 3740, 0, readBit, readVarp, T);
		farm.due(T + 38400, ME);
		GroupEvent e = only();
		assertEquals(GroupEvent.Type.BIRDHOUSE, e.type);
		assertEquals("Hespori", e.text);
	}

	// ---- Bird houses ----

	@Test
	public void birdHousesPostTogetherWhenTheLastIsDone()
	{
		birds(3, 3, 3, 3, T);
		varps.put(VarPlayerID.BIRDHOUSE_TRANSMIT_D, 6);
		farm.observe(FOSSIL_ISLAND, 3700, 3800, 0, readBit, readVarp, T + 60);

		farm.due(T + 3000, ME);
		assertTrue("the last is still going", out.isEmpty());

		farm.due(T + 3060, ME);
		GroupEvent e = only();
		assertEquals(GroupEvent.Type.BIRDHOUSE, e.type);
		assertEquals("Bird houses", e.text);

		farm.due(T + 9000, ME);
		assertEquals("fires once", 1, out.size());
	}

	@Test
	public void varpsAreBirdHousesOnlyOnTheIslandsGround()
	{
		varps.put(VarPlayerID.BIRDHOUSE_TRANSMIT_A, 3);
		farm.observe(FOSSIL_ISLAND, 3700, 3800, 1, readBit, readVarp, T);
		farm.due(T + 9000, ME);
		assertTrue("upstairs", out.isEmpty());
	}

	@Test
	public void aLoginsZeroedVarpsDoNotClearTheHouses()
	{
		birds(3, 3, 3, 3, T);
		birds(0, 0, 0, 0, T + 10);
		farm.due(T + 3000, ME);
		assertEquals("kept: varps had not arrived yet", 1, out.size());
	}

	@Test
	public void twoHousesTakenDownStillCount()
	{
		birds(3, 3, 0, 0, T);
		birds(0, 0, 0, 0, T + 10);
		farm.due(T + 9000, ME);
		assertTrue("both really went", out.isEmpty());
	}

	// ---- Switches ----

	@Test
	public void aKindSwitchedOffIsNotWatched()
	{
		on.remove(GroupEvent.Type.FARMING);
		atFalador(7, T);
		on.add(GroupEvent.Type.FARMING);
		farm.due(T + 9000, ME);
		assertTrue(out.isEmpty());
	}

	@Test
	public void aKindSwitchedOffWhenDueIsSilentAndStaysSilent()
	{
		atFalador(7, T);
		on.remove(GroupEvent.Type.FARMING);
		farm.due(T + 1200, ME);
		assertTrue(out.isEmpty());

		on.add(GroupEvent.Type.FARMING);
		farm.due(T + 9000, ME);
		assertTrue("not posted late", out.isEmpty());
	}

	@Test
	public void birdHousesHaveTheirOwnSwitch()
	{
		on.remove(GroupEvent.Type.BIRDHOUSE);
		birds(3, 3, 3, 3, T);
		atFalador(7, T);
		farm.due(T + 9000, ME);
		assertEquals(GroupEvent.Type.FARMING, only().type);
	}

	// ---- Saving ----

	@Test
	public void timersSurviveARestart()
	{
		atFalador(7, T);
		assertTrue("something to save", farm.takeDirty());
		assertFalse("only once", farm.takeDirty());

		FarmRuns back = FarmRuns.parse(farm.save(), out::add, t -> true);
		back.due(T + 1200, ME);
		assertEquals("Falador", only().detail);
	}

	@Test
	public void aFiredTimerStaysFiredAcrossARestart()
	{
		atFalador(7, T);
		farm.due(T + 1200, ME);
		out.clear();

		FarmRuns back = FarmRuns.parse(farm.save(), out::add, t -> true);
		back.due(T + 9000, ME);
		assertTrue(out.isEmpty());
	}

	@Test
	public void aBadSaveIsIgnored()
	{
		FarmRuns none = FarmRuns.parse(null, out::add, t -> true);
		none.due(T, ME);
		assertTrue(out.isEmpty());

		String junk = "nonsense;999.1=4,1,1,0;12083.x=4,1,1,0;" + FALADOR + "." + VarbitID.FARMING_TRANSMIT_D + "=abc,1,1,0";
		FarmRuns bad = FarmRuns.parse(junk, out::add, t -> true);
		bad.due(T + 90000, ME);
		assertTrue(out.isEmpty());
		assertEquals("", bad.save());
	}

	@Test
	public void aLongListOfPlacesBecomesACount()
	{
		assertEquals("Falador, Catherby", FarmRuns.places(List.of("Falador", "Catherby")));
		List<String> many = new ArrayList<>();
		for (int i = 0; i < 12; i++)
		{
			many.add("Civitas illa Fortis");
		}
		assertEquals("12 patches", FarmRuns.places(many));
	}
}
