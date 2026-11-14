package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.Map;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GroupSettingsSchemaTest
{
	private static final String SECRET = "https://discord.com/api/webhooks/123456/SECRETTOKEN";
	private static final GroupIronmanConfig DEFAULTS = new GroupIronmanConfig()
	{
	};

	/** Every switch on, every number 7, every text field the webhook URL. */
	private static GroupIronmanConfig everythingSet()
	{
		return (GroupIronmanConfig) Proxy.newProxyInstance(GroupIronmanConfig.class.getClassLoader(),
			new Class<?>[]{GroupIronmanConfig.class}, (p, m, args) ->
			{
				if (m.getReturnType() == boolean.class)
				{
					return true;
				}
				if (m.getReturnType() == int.class)
				{
					return 7;
				}
				return m.getReturnType() == String.class ? SECRET : null;
			});
	}

	/**
	 * The shared settings never carry a webhook, whoever saves them: a webhook travels only in the leader's
	 * separate "Share webhooks with the group" document ({@link GroupWebhooks}, SharedWebhooksTest).
	 */
	@Test
	public void aWebhookNeverTravelsInTheSettingsPayloadUnlessTheLeaderShares()
	{
		for (GroupSettingsSchema.Setting s : GroupSettingsSchema.ALL)
		{
			String key = s.key.toLowerCase(Locale.ROOT);
			assertFalse(s.key, key.contains("url") || key.contains("webhook") || key.contains("mention"));
		}
		GroupSigner.Identity leader = GroupSigner.Identity.create();
		GroupSettings doc = GroupSettings.next(null, GroupSettingsSchema.seed(everythingSet()), "Alex", leader, NOW);
		String payload = GovernanceCodec.encode(new Gson(), doc);
		assertFalse(payload, payload.contains("SECRETTOKEN"));
		assertFalse(payload, payload.contains("discord.com"));

		GroupWebhooks off = GroupWebhooks.next(null, "groupship", false, Webhooks.of(everythingSet()), "Alex", leader,
			NOW);
		assertFalse("sharing off carries none", GovernanceCodec.encode(new Gson(), off).contains("SECRETTOKEN"));
		GroupWebhooks on = GroupWebhooks.next(off, "groupship", true, Webhooks.of(everythingSet()), "Alex", leader,
			NOW + 1);
		assertTrue("the leader shares", GovernanceCodec.encode(new Gson(), on).contains("SECRETTOKEN"));
	}

	@Test
	public void theFirstLeaderSaveSeedsFromTheLeadersConfig()
	{
		Map<String, Long> seeded = GroupSettingsSchema.seed(everythingSet());
		assertEquals(GroupSettingsSchema.ALL.size(), seeded.size());
		assertEquals(7L, (long) seeded.get("eventLevelMin"));
		assertEquals(1L, (long) seeded.get(GroupSettingsSchema.discordKey(GroupEvent.Type.PET)));
		assertEquals(1L, (long) seeded.get(GroupSettingsSchema.overlayKey(GroupEvent.Type.DEATH)));
		assertEquals(1L, (long) seeded.get(GroupSettingsSchema.digestKey(GroupEvent.Category.DROPS)));

		Map<String, Long> defaults = GroupSettingsSchema.seed(DEFAULTS);
		assertEquals(0L, (long) defaults.get(GroupSettingsSchema.discordKey(GroupEvent.Type.PET)));
		assertEquals((long) DEFAULTS.eventDropMinValue(), (long) defaults.get("eventDropMinValue"));
	}

	@Test
	public void everyEventKindAndDigestCategoryIsAGroupSetting()
	{
		for (GroupEvent.Type t : GroupEvent.Type.values())
		{
			if (t != GroupSettingsSchema.OWN_DISCORD)
			{
				assertNotNull(GroupSettingsSchema.get(GroupSettingsSchema.discordKey(t)));
			}
			assertNotNull(GroupSettingsSchema.get(GroupSettingsSchema.overlayKey(t)));
		}
		for (GroupEvent.Category c : GroupEvent.Category.values())
		{
			assertNotNull(GroupSettingsSchema.get(GroupSettingsSchema.digestKey(c)));
		}
		for (String key : new String[]{"eventDropMinValue", "eventStorageMinValue", "eventLevelMin", "eventLevelEvery",
			"eventBackAfterDays", "eventClueMinValue", "eventKcEvery", "eventGravestoneWarnMinutes", "discordDigestHour",
			"discordQuietStart", "discordQuietEnd", "discordOfflineGrace", "discordEasterEggs", "discordLegendAt",
			"discordFreeloaderAt"})
		{
			assertNotNull(key, GroupSettingsSchema.get(key));
		}
	}

	@Test
	public void sanitizeClampsAndDropsUnknownKeys()
	{
		Map<String, Long> in = GroupSettingsSchema.seed(DEFAULTS);
		in.put("discordQuietStart", 99L);
		in.put("eventLevelEvery", 0L);
		in.put(GroupSettingsSchema.discordKey(GroupEvent.Type.PET), 5L);
		in.put("discordWebhookUrl", 1L);
		Map<String, Long> out = GroupSettingsSchema.sanitize(in);
		assertEquals(23L, (long) out.get("discordQuietStart"));
		assertEquals(1L, (long) out.get("eventLevelEvery"));
		assertEquals(1L, (long) out.get(GroupSettingsSchema.discordKey(GroupEvent.Type.PET)));
		assertFalse(out.containsKey("discordWebhookUrl"));
	}

	@Test
	public void withoutGroupSettingsEachMemberKeepsTheirOwnConfig()
	{
		GroupSettingsSchema.Resolved r = new GroupSettingsSchema.Resolved(everythingSet(), null);
		assertEquals(7, r.value("eventLevelMin"));
		assertTrue(r.on(GroupSettingsSchema.discordKey(GroupEvent.Type.PET)));
	}

	@Test
	public void groupSettingsOverrideTheMembersOwnConfig()
	{
		Map<String, Long> values = GroupSettingsSchema.seed(DEFAULTS);
		values.put("eventLevelMin", 70L);
		GroupSettings doc = GroupSettings.next(null, values, "Alex", GroupSigner.Identity.create(), NOW);
		GroupSettingsSchema.Resolved r = new GroupSettingsSchema.Resolved(everythingSet(), doc);
		assertEquals(70, r.value("eventLevelMin"));
		assertFalse("the group's switch, not this member's", r.on(GroupSettingsSchema.discordKey(GroupEvent.Type.PET)));
	}

	@Test
	public void theFullestDocumentFitsOneMessage()
	{
		GroupSigner.Identity id = GroupSigner.Identity.create();
		GroupSettings doc = null;
		for (int i = 0; i < 40; i++)
		{
			Map<String, Long> values = GroupSettingsSchema.seed(DEFAULTS);
			for (GroupSettingsSchema.Setting s : GroupSettingsSchema.ALL)
			{
				values.put(s.key, i % 2 == 0 ? s.max : s.min);
			}
			doc = GroupSettings.next(doc, values, "A twelve char", id, NOW + i);
		}
		String payload = GovernanceCodec.encode(new Gson(), doc);
		assertTrue(payload.length() + " chars", payload.length() < GroupCodec.MAX_PAYLOAD_CHARS);
		assertTrue(doc.history.size() <= GroupSettings.MAX_HISTORY);
	}
}
