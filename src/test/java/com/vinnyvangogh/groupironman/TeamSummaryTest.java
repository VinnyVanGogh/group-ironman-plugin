package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/** Solo vs a team vs a mass, naming group members only (docs/plans/activities.md §2.2, §7 item 2). */
public class TeamSummaryTest
{
	private static final String ME = "Ember Fox";
	private static final List<String> GROUP = Arrays.asList(ME, "Moss Kettle", "Pebble Wren");

	@Test
	public void aloneIsSolo()
	{
		assertEquals("solo", TeamSummary.of(ME, GROUP, Collections.singletonList(ME)).mode());
		assertEquals("", TeamSummary.of(ME, GROUP, Collections.singletonList(ME)).with());
	}

	@Test
	public void aGroupMateIsADuoByName()
	{
		assertEquals("duo with Moss Kettle", TeamSummary.of(ME, GROUP, Arrays.asList(ME, "Moss Kettle")).mode());
	}

	@Test
	public void strangersAreCountedNeverNamed()
	{
		TeamSummary t = TeamSummary.of(ME, GROUP, Arrays.asList(ME, "Moss Kettle", "Random Guy", "Other Person"));
		assertEquals("4-man (with Moss Kettle +2 others)", t.mode());
		assertFalse(t.mode().contains("Random"));
	}

	@Test
	public void forbiddenSpaceAndCaseMatchTheRoster()
	{
		// The game's names carry a non-breaking space; the roster may not.
		TeamSummary t = TeamSummary.of("ember fox", GROUP, Arrays.asList(ME, "Moss Kettle"));
		assertEquals("duo with Moss Kettle", t.mode());
	}

	@Test
	public void fortyRandomsAreAMassWithNoStrangerNamed()
	{
		List<String> present = new ArrayList<>(Arrays.asList(ME, "Pebble Wren"));
		for (int i = 0; i < 40; i++)
		{
			present.add("Stranger " + i);
		}
		TeamSummary t = TeamSummary.of(ME, GROUP, present);
		assertEquals("mass (42 players, with Pebble Wren)", t.mode());
		assertEquals("with Pebble Wren +40 others", t.with());
		for (int i = 0; i < 40; i++)
		{
			assertFalse(t.mode().contains("Stranger"));
			assertFalse(t.with().contains("Stranger"));
		}
	}

	@Test
	public void oneOtherIsSingular()
	{
		assertEquals("with +1 other", TeamSummary.of(ME, GROUP, Arrays.asList(ME, "Someone")).with());
	}
}
