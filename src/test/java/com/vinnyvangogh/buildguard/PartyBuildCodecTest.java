package com.vinnyvangogh.buildguard;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The payload one member's client sends the rest of the party.
 *
 * <p>Every peer may be on a different build of this plugin, so the decoder is
 * held to what an older or newer sender actually puts on the wire, not only to
 * what this build writes.
 */
public class PartyBuildCodecTest
{
	private static final Gson GSON = new Gson();

	private static PartyBuild sample()
	{
		List<BuildRule> rules = Arrays.asList(
			BuildRule.local("Defence", BuildRule.HARD_CAP, 1, ""),
			BuildRule.local("Prayer", BuildRule.NO_XP, 0, ""));
		Map<String, Integer> levels = new LinkedHashMap<>();
		levels.put("Defence", 1);
		levels.put("Prayer", 1);
		return new PartyBuild(rules, levels, Arrays.asList("Rune scimitar", "Leather body"),
			Collections.singletonList("Defence"), "Controlled attack style");
	}

	@Test
	public void ruleSurvivesTheRoundTripOverTheParty()
	{
		PartyBuild back = PartyBuildCodec.decode(GSON, PartyBuildCodec.encode(GSON, sample()));

		assertNotNull(back);
		assertEquals(2, back.rules().size());
		BuildRule def = back.rules().get(0);
		assertEquals("Defence", def.skillName());
		assertEquals(BuildRule.HARD_CAP, def.type());
		assertEquals(1, def.value());
		assertTrue("a decoded cap must still block", def.blocking());
		assertEquals(Integer.valueOf(1), back.levels().get("Defence"));
		assertEquals(Arrays.asList("Rune scimitar", "Leather body"), back.gear());
		assertEquals(Collections.singletonList("Defence"), back.atRisk());
		assertEquals("Controlled attack style", back.riskReason());
	}

	@Test
	public void encodedPayloadCarriesTheProtocolVersion()
	{
		assertTrue(PartyBuildCodec.encode(GSON, sample()).contains("\"v\":" + PartyBuildCodec.PROTOCOL));
	}

	/** A newer peer adds fields; this build must still read the ones it knows. */
	@Test
	public void newerPeerWithExtraFieldsStillDecodes()
	{
		String fromTheFuture = "{\"v\":7,\"rules\":[{\"skill\":\"Defence\",\"type\":\"HARD_CAP\",\"value\":1,\"colour\":\"red\"}],"
			+ "\"levels\":{\"Defence\":1},\"pets\":[\"Bloodhound\"],\"gear\":[\"Rune scimitar\"]}";
		PartyBuild back = PartyBuildCodec.decode(GSON, fromTheFuture);

		assertNotNull(back);
		assertEquals(1, back.rules().size());
		assertEquals("Defence", back.rules().get(0).skillName());
		assertEquals(Collections.singletonList("Rune scimitar"), back.gear());
		assertTrue(back.atRisk().isEmpty());
	}

	/** A rule type this build does not know is dropped, exactly as local storage drops it. */
	@Test
	public void unknownRuleTypeFromAPeerIsDropped()
	{
		String json = "{\"v\":2,\"rules\":[{\"skill\":\"Defence\",\"type\":\"HARD_CAP\",\"value\":1},"
			+ "{\"skill\":\"Magic\",\"type\":\"BANNED_FOREVER\"}]}";
		PartyBuild back = PartyBuildCodec.decode(GSON, json);
		assertNotNull(back);
		assertEquals(1, back.rules().size());
	}

	@Test
	public void olderPeerWithMissingOptionalFieldsDecodesToEmpties()
	{
		PartyBuild back = PartyBuildCodec.decode(GSON, "{\"v\":1}");
		assertNotNull(back);
		assertTrue(back.rules().isEmpty());
		assertTrue(back.levels().isEmpty());
		assertTrue(back.gear().isEmpty());
		assertTrue(back.atRisk().isEmpty());
		assertEquals("", back.riskReason());
	}

	@Test
	public void payloadWithoutAVersionOrUnparseableIsRefused()
	{
		assertNull(PartyBuildCodec.decode(GSON, null));
		assertNull(PartyBuildCodec.decode(GSON, ""));
		assertNull(PartyBuildCodec.decode(GSON, "{not json"));
		assertNull(PartyBuildCodec.decode(GSON, "\"a string\""));
		assertNull(PartyBuildCodec.decode(GSON, "{\"rules\":[]}"));
		assertNull(PartyBuildCodec.decode(GSON, "{\"v\":0}"));
		assertNull(PartyBuildCodec.decode(GSON, "{\"v\":\"one\"}"));
	}

	/** A peer is not trusted to keep its lists small; the panel must not be floodable. */
	@Test
	public void oversizedListsAndNamesAreTrimmed()
	{
		List<String> gear = new ArrayList<>();
		for (int i = 0; i < 500; i++)
		{
			gear.add("Item " + i);
		}
		StringBuilder longName = new StringBuilder();
		for (int i = 0; i < 300; i++)
		{
			longName.append('x');
		}
		gear.set(0, longName.toString());
		PartyBuild big = new PartyBuild(Collections.emptyList(), Collections.emptyMap(), gear,
			Collections.emptyList(), longName.toString());

		PartyBuild back = PartyBuildCodec.decode(GSON, PartyBuildCodec.encode(GSON, big));
		assertNotNull(back);
		assertEquals(PartyBuildCodec.MAX_GEAR, back.gear().size());
		assertEquals(PartyBuildCodec.MAX_NAME, back.gear().get(0).length());
		assertEquals(PartyBuildCodec.MAX_NAME, back.riskReason().length());
	}

	@Test
	public void levelsOutsideTheGameRangeAreDropped()
	{
		PartyBuild back = PartyBuildCodec.decode(GSON,
			"{\"v\":1,\"levels\":{\"Defence\":1,\"Attack\":-4,\"Strength\":5000,\"Magic\":99}}");
		assertNotNull(back);
		assertEquals(2, back.levels().size());
		assertEquals(Integer.valueOf(99), back.levels().get("Magic"));
	}
}
