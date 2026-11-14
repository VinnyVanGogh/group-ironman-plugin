package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.Map;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Two simulated clients, each with its own config store, joined by a fake party. */
public class GovernanceSyncTest
{
	private static final GroupIronmanConfig DEFAULTS = new GroupIronmanConfig()
	{
	};

	/** One client: its config, what it sent, and its sync ({@link GovernanceClient}). */
	private final class Client extends GovernanceClient
	{
		Client(String name)
		{
			super(name, new Gson());
		}
	}

	private static Map<String, Long> levelMin(long level)
	{
		Map<String, Long> v = GroupSettingsSchema.seed(DEFAULTS);
		v.put("eventLevelMin", level);
		return v;
	}

	@Test
	public void aLeadersChangeReachesTheSecondMember()
	{
		Client alex = new Client("Alex");
		Client sam = new Client("Sam");
		alex.tick(sam);
		sam.tick(alex);

		assertTrue(alex.sync.claim("Alex", NOW));
		assertTrue(alex.sync.save("Alex", levelMin(70), NOW + 1));
		assertFalse("Sam is not an editor", sam.sync.save("Sam", levelMin(5), NOW + 1));
		alex.tick(sam);

		GroupSettings got = sam.sync.governance().settings();
		assertNotNull(got);
		assertEquals(70, new GroupSettingsSchema.Resolved(DEFAULTS, got).value("eventLevelMin"));
		assertTrue("kept for Sam's next session", sam.config.containsKey(GovernanceSync.KEY_SETTINGS));
	}

	@Test
	public void anOfflineMemberPicksUpTheLatestWhenTheyJoin()
	{
		Client alex = new Client("Alex");
		Client sam = new Client("Sam");
		alex.tick(sam);
		sam.tick(alex);
		alex.sync.claim("Alex", NOW);
		alex.sync.save("Alex", levelMin(70), NOW + 1);
		alex.tick();
		alex.sync.save("Alex", levelMin(80), NOW + 2);
		alex.tick();
		assertNull("Sam was offline for both", sam.sync.governance().settings());

		// Sam joins: his UserSync makes Alex resend everything.
		alex.sync.resendAll();
		alex.tick(sam);
		assertEquals(2, sam.sync.governance().settings().version);
	}

	@Test
	public void rolesSettingsAndKeyPairSurviveARestart()
	{
		Client alex = new Client("Alex");
		alex.sync.claim("Alex", NOW);
		alex.sync.save("Alex", levelMin(70), NOW + 1);
		String key = alex.sync.governance().publicKey();
		alex.start();
		assertEquals(key, alex.sync.governance().publicKey());
		assertEquals(GroupGovernance.Role.LEADER, alex.sync.governance().myRole("Alex"));
		assertEquals(1, alex.sync.governance().settings().version);
	}

	@Test
	public void nothingSentCarriesAWebhookUnlessTheLeaderSharesNorEverThePrivateKey()
	{
		Client alex = new Client("Alex");
		alex.sync.claim("Alex", NOW);
		alex.sync.save("Alex", levelMin(70), NOW + 1);
		alex.sync.flush(MemberPrefs.NONE);
		String privateHalf = alex.config.get(GovernanceSync.KEY_IDENTITY).split("\\.")[1];
		for (String payload : alex.outbox)
		{
			assertFalse(payload.contains(privateHalf));
			assertFalse(payload.toLowerCase(java.util.Locale.ROOT).contains("webhook"));
		}
		assertEquals("announce, roles, settings", 3, alex.outbox.size());
		alex.outbox.clear();

		// Turning sharing off without ever having shared sends nothing either.
		assertTrue(alex.sync.shareWebhooks("Alex", false, Webhooks.of(DEFAULTS), NOW + 2));
		alex.sync.flush(MemberPrefs.NONE);
		assertTrue(alex.outbox.isEmpty());

		// The leader's "Share webhooks with the group": now, and only now, one document carries them.
		java.util.Map<Webhook, Webhooks.Setting> hooks = new java.util.EnumMap<>(Webhook.class);
		hooks.put(Webhook.MAIN, new Webhooks.Setting("https://discord.com/api/webhooks/1/SHAREDTOKEN",
			DiscordPosterName.WEBHOOK, ""));
		assertTrue(alex.sync.shareWebhooks("Alex", true, new Webhooks(hooks), NOW + 3));
		alex.sync.flush(MemberPrefs.NONE);
		assertEquals(1, alex.outbox.size());
		assertTrue(alex.outbox.get(0).contains("SHAREDTOKEN"));
		assertFalse(alex.outbox.get(0).contains(privateHalf));
		assertFalse("never in the leader's config either", alex.config.values().toString().contains("SHAREDTOKEN"));
	}

	@Test
	public void anUnchangedAnnounceIsNotResent()
	{
		Client alex = new Client("Alex");
		alex.sync.flush(MemberPrefs.NONE);
		alex.outbox.clear();
		alex.sync.prefs(MemberPrefs.NONE);
		alex.sync.flush(MemberPrefs.NONE);
		assertTrue(alex.outbox.isEmpty());
		MemberPrefs out = new MemberPrefs(true, java.util.EnumSet.noneOf(GroupEvent.Type.class),
			java.util.EnumSet.noneOf(GroupEvent.Type.class));
		alex.sync.prefs(out);
		alex.sync.flush(out);
		assertEquals(1, alex.outbox.size());
	}
}
