package com.vinnyvangogh.buildguard;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * What a member's client is willing to put on the party wire.
 *
 * <p>The group features are opt-in: a member who has not turned sharing on
 * sends nothing at all, not an empty payload, and gear and the risk line each
 * need their own switch.
 */
public class PartyOutboxTest
{
	private static final Gson GSON = new Gson();

	private static final List<BuildRule> RULES = Collections.singletonList(
		BuildRule.local("Defence", BuildRule.HARD_CAP, 1, ""));
	private static final Map<String, Integer> LEVELS = Collections.singletonMap("Defence", 1);
	private static final List<String> GEAR = Arrays.asList("Rune scimitar");
	private static final List<String> RISK = Collections.singletonList("Defence");

	@Test
	public void optedOutMemberSendsNothing()
	{
		PartyOutbox.Switches off = new PartyOutbox.Switches(false, true, true);
		assertNull(PartyOutbox.payload(GSON, off, RULES, LEVELS, GEAR, RISK, "Controlled attack style"));
	}

	@Test
	public void optedOutMemberSendsNothingEvenWithNoRules()
	{
		PartyOutbox.Switches off = new PartyOutbox.Switches(false, false, false);
		assertNull(PartyOutbox.payload(GSON, off, Collections.emptyList(), Collections.emptyMap(),
			Collections.emptyList(), Collections.emptyList(), ""));
	}

	@Test
	public void sharingBuildAloneOmitsGearAndRisk()
	{
		PartyOutbox.Switches buildOnly = new PartyOutbox.Switches(true, false, false);
		String payload = PartyOutbox.payload(GSON, buildOnly, RULES, LEVELS, GEAR, RISK, "Controlled attack style");
		assertNotNull(payload);

		PartyBuild sent = PartyBuildCodec.decode(GSON, payload);
		assertNotNull(sent);
		assertEquals(1, sent.rules().size());
		assertEquals(Integer.valueOf(1), sent.levels().get("Defence"));
		assertTrue("gear needs its own opt-in", sent.gear().isEmpty());
		assertTrue("the group risk line needs its own opt-in", sent.atRisk().isEmpty());
		assertEquals("", sent.riskReason());
		assertTrue("nothing about the gear may reach the wire", !payload.contains("Rune scimitar"));
	}

	@Test
	public void everythingOnSendsGearAndRisk()
	{
		PartyOutbox.Switches all = new PartyOutbox.Switches(true, true, true);
		PartyBuild sent = PartyBuildCodec.decode(GSON,
			PartyOutbox.payload(GSON, all, RULES, LEVELS, GEAR, RISK, "Controlled attack style"));
		assertNotNull(sent);
		assertEquals(GEAR, sent.gear());
		assertEquals(RISK, sent.atRisk());
		assertEquals("Controlled attack style", sent.riskReason());
	}

	/** Only levels of skills the member has a rule on are shared; the rest is not the group's business. */
	@Test
	public void onlyLevelsOfRuledSkillsAreSent()
	{
		Map<String, Integer> all = new java.util.HashMap<>();
		all.put("Defence", 1);
		all.put("Woodcutting", 77);
		PartyBuild sent = PartyBuildCodec.decode(GSON, PartyOutbox.payload(GSON,
			new PartyOutbox.Switches(true, false, false), RULES, all, GEAR, RISK, ""));
		assertNotNull(sent);
		assertEquals(Collections.singletonMap("Defence", 1), sent.levels());
	}
}
