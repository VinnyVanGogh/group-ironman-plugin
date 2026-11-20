package com.vinnyvangogh.groupironman;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class MemberStatusTest
{
	private static final Gson GSON = new Gson();

	private static MemberStats stats(MemberStatus status)
	{
		Map<String, MemberStats.Level> skills = new LinkedHashMap<>();
		skills.put("Prayer", new MemberStats.Level(70, 43));
		return new MemberStats(skills, 100, 100, 302, 12850, 0, 0, 0, status);
	}

	private static MemberStatus roundTrip(MemberStatus status)
	{
		return GroupCodec.decode(GSON, GroupCodec.encodeStats(GSON, stats(status))).stats.status();
	}

	@Test
	public void gameValuesMapToState()
	{
		assertFalse(MemberStatus.fromGame(0, 0, 0).prayerOn());
		assertTrue(MemberStatus.fromGame(4, 0, 0).prayerOn());
		assertEquals(20, MemberStatus.fromGame(0, 20, 0).poisonHits());
		assertTrue(MemberStatus.fromGame(0, MemberStatus.VENOM_THRESHOLD + 3, 0).venomed());
		assertTrue(MemberStatus.fromGame(0, 0, 5).diseased());
	}

	@Test
	public void antipoisonImmunityIsNotPoison()
	{
		MemberStatus s = MemberStatus.fromGame(0, -40, 0);
		assertFalse(s.poisoned());
		assertTrue(s.chips().isEmpty());
	}

	@Test
	public void poisonTimeIsHitsTimesEighteenSeconds()
	{
		MemberStatus s = MemberStatus.fromGame(0, 10, 0);
		assertEquals(180, s.poisonSeconds());
		assertEquals(Collections.singletonList("Poisoned ~3m"), s.chips());
	}

	@Test
	public void venomAndDiseaseSayTheGameGivesNoTime()
	{
		MemberStatus s = MemberStatus.of(false, 0, true, true);
		assertEquals(List.of("Venomed", "Diseased"), s.chips());
		for (String line : s.lines())
		{
			assertTrue(line, line.contains("the game does not say"));
		}
	}

	@Test
	public void statusSurvivesTheWire()
	{
		MemberStatus s = MemberStatus.of(true, 12, false, true);
		assertEquals(s, roundTrip(s));
		assertEquals(MemberStatus.NONE, roundTrip(MemberStatus.NONE));
	}

	@Test
	public void olderSenderIsUnknownNotClear()
	{
		assertEquals(MemberStatus.UNKNOWN, roundTrip(MemberStatus.UNKNOWN));
		assertFalse(roundTrip(MemberStatus.UNKNOWN).known());
	}

	@Test
	public void absurdPoisonIsClamped()
	{
		String json = GroupCodec.encodeStats(GSON, stats(MemberStatus.NONE)).replace("\"ps\":0", "\"ps\":999999999");
		assertEquals(MemberStatus.MAX_POISON_HITS, GroupCodec.decode(GSON, json).stats.status().poisonHits());
	}

	@Test
	public void prayerBarShowsOnOffOrNothing()
	{
		assertEquals(1, prayerBar(MemberStatus.of(true, 0, false, false)).on);
		assertEquals(0, prayerBar(MemberStatus.NONE).on);
		assertEquals(-1, prayerBar(MemberStatus.UNKNOWN).on);
		assertTrue(prayerBar(MemberStatus.of(true, 0, false, false)).tooltip().contains("prayer ON"));
	}

	@Test
	public void petSurvivesTheWireAndNoPetStaysEmpty()
	{
		MemberStatus s = MemberStatus.NONE.withPet("Callisto cub");
		assertEquals("Callisto cub", roundTrip(s).pet());
		assertEquals("", roundTrip(MemberStatus.NONE).pet());
		assertFalse(GroupCodec.encodeStats(GSON, stats(MemberStatus.NONE)).contains("\"pt\""));
	}

	@Test
	public void petNameIsTidied()
	{
		assertEquals("", MemberStatus.NONE.withPet(null).pet());
		assertEquals("Hellpuppy", MemberStatus.NONE.withPet("<col=ff0000>Hellpuppy</col> ").pet());
		assertEquals(MemberStatus.MAX_PET_NAME, MemberStatus.NONE.withPet("x".repeat(500)).pet().length());
		assertFalse(MemberStatus.NONE.equals(MemberStatus.NONE.withPet("Rocky")));
	}

	private static PanelModel.Bar prayerBar(MemberStatus status)
	{
		for (PanelModel.Bar b : PanelModel.bars(stats(status)))
		{
			if (b.label.equals("Prayer"))
			{
				return b;
			}
		}
		throw new AssertionError("no prayer bar");
	}
}
