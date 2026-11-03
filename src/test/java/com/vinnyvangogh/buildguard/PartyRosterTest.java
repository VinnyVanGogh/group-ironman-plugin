package com.vinnyvangogh.buildguard;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** What the party panel shows, built from the payloads members have sent. */
public class PartyRosterTest
{
	private static PartyBuild pure(List<String> gear, List<String> risk, String reason)
	{
		return new PartyBuild(
			Collections.singletonList(BuildRule.local("Defence", BuildRule.HARD_CAP, 1, "")),
			Collections.singletonMap("Defence", 1), gear, risk, reason);
	}

	@Test
	public void memberLineShowsEveryCapWithTheCurrentLevel()
	{
		PartyRoster roster = new PartyRoster();
		roster.put(1L, "Zezima", pure(Collections.emptyList(), Collections.emptyList(), ""));

		List<String> lines = roster.memberLines();
		assertEquals(1, lines.size());
		assertEquals("Zezima: Defence capped at 1 (now 1)", lines.get(0));
	}

	@Test
	public void memberWithNoRulesSaysSo()
	{
		PartyRoster roster = new PartyRoster();
		roster.put(1L, "Lynx Titan", new PartyBuild(Collections.emptyList(), Collections.emptyMap(),
			Collections.emptyList(), Collections.emptyList(), ""));
		assertEquals(Collections.singletonList("Lynx Titan: no build rules"), roster.memberLines());
	}

	@Test
	public void riskLineNamesTheMemberTheSkillAndWhy()
	{
		PartyRoster roster = new PartyRoster();
		roster.put(1L, "Zezima", pure(Collections.emptyList(), Collections.singletonList("Defence"), "Controlled attack style"));
		roster.put(2L, "Safe", pure(Collections.emptyList(), Collections.emptyList(), ""));

		assertEquals(Collections.singletonList("Zezima is at risk of Defence XP (Controlled attack style)"),
			roster.riskLines());
	}

	@Test
	public void gearIndexGroupsWhoHasWhat()
	{
		PartyRoster roster = new PartyRoster();
		roster.put(1L, "Bee", pure(Arrays.asList("Rune scimitar", "Amulet of strength"), Collections.emptyList(), ""));
		roster.put(2L, "Ant", pure(Collections.singletonList("Rune scimitar"), Collections.emptyList(), ""));

		Map<String, List<String>> index = roster.gearIndex();
		assertEquals(Arrays.asList("Amulet of strength", "Rune scimitar"), new java.util.ArrayList<>(index.keySet()));
		assertEquals(Arrays.asList("Ant", "Bee"), index.get("Rune scimitar"));
	}

	@Test
	public void memberWhoLeavesIsForgotten()
	{
		PartyRoster roster = new PartyRoster();
		roster.put(1L, "Zezima", pure(Collections.singletonList("Rune scimitar"), Collections.singletonList("Defence"), "x"));
		roster.remove(1L);
		assertTrue(roster.memberLines().isEmpty());
		assertTrue(roster.riskLines().isEmpty());
		assertTrue(roster.gearIndex().isEmpty());
	}

	/** A newer payload from the same member replaces the old one, never sits beside it. */
	@Test
	public void laterPayloadReplacesTheEarlierOne()
	{
		PartyRoster roster = new PartyRoster();
		roster.put(1L, "Zezima", pure(Collections.emptyList(), Collections.singletonList("Defence"), "x"));
		roster.put(1L, "Zezima", pure(Collections.emptyList(), Collections.emptyList(), ""));
		assertTrue(roster.riskLines().isEmpty());
		assertEquals(1, roster.memberLines().size());
	}
}
