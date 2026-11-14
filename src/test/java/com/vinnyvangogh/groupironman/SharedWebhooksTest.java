package com.vinnyvangogh.groupironman;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * "Share webhooks with the group" (Board 2026-11-13): the leader's webhooks reach members inside a signed group
 * document; nothing else may share them, a member's own webhook wins, and a member never keeps them in config.
 */
public class SharedWebhooksTest
{
	private static final String GROUP = "GroupShip";
	private static final String TOKEN = "LEADERSECRETTOKEN";
	private static final String MAIN = "https://discord.com/api/webhooks/111/" + TOKEN + "main";
	private static final String PROGRESS = "https://discord.com/api/webhooks/222/" + TOKEN + "progress";
	private static final String ONLINE = "https://discord.com/api/webhooks/333/" + TOKEN + "online";
	private static final String OWN_DROPS = "https://discord.com/api/webhooks/444/membersowndrops";
	private static final String OWN_ONLINE = "https://discord.com/api/webhooks/555/membersownonline";
	private final Gson gson = new Gson();

	/** One client: its config, its group cache file, what it sent, and its sync. */
	private final class Client
	{
		final String name;
		final Map<String, String> config = new HashMap<>();
		final List<String> outbox = new ArrayList<>();
		String cache;
		GovernanceSync sync;

		Client(String name)
		{
			this.name = name;
			start();
		}

		/** Starting the plugin: the config is read at once, the cache file a moment later. */
		void start()
		{
			sync = new GovernanceSync(gson, new GovernanceSync.Store()
			{
				@Override
				public String get(String key)
				{
					return config.get(key);
				}

				@Override
				public void set(String key, String value)
				{
					if (value == null)
					{
						config.remove(key);
					}
					else
					{
						config.put(key, value);
					}
				}
			}, outbox::add, () ->
			{
			}, json -> cache = json);
			sync.group(GROUP);
			if (cache != null)
			{
				sync.restoreWebhooks(cache);
			}
		}

		void tick(Client... others)
		{
			sync.flush(MemberPrefs.NONE);
			for (String payload : outbox)
			{
				assertTrue(GovernanceSync.isGovernance(payload));
				for (Client o : others)
				{
					o.sync.receive(name, payload);
				}
			}
			outbox.clear();
		}

		GroupWebhooks shared()
		{
			return sync.governance().sharedWebhooks();
		}
	}

	private static Webhooks leaderHooks()
	{
		Map<Webhook, Webhooks.Setting> s = new EnumMap<>(Webhook.class);
		s.put(Webhook.MAIN, new Webhooks.Setting(MAIN, DiscordPosterName.GIM_UTILITIES, ""));
		s.put(Webhook.PROGRESS, new Webhooks.Setting(PROGRESS, DiscordPosterName.CUSTOM, "Leader's Levels"));
		s.put(Webhook.ONLINE, new Webhooks.Setting(ONLINE, DiscordPosterName.WEBHOOK, ""));
		return new Webhooks(s);
	}

	private static Webhooks none()
	{
		return new Webhooks(new EnumMap<>(Webhook.class));
	}

	/** Alex leads, Sam is an editor, Kim is a member; each has heard the others. */
	private Client[] group()
	{
		Client alex = new Client("Alex");
		Client sam = new Client("Sam");
		Client kim = new Client("Kim");
		alex.tick(sam, kim);
		sam.tick(alex, kim);
		kim.tick(alex, sam);
		assertTrue(alex.sync.claim("Alex", NOW));
		assertTrue(alex.sync.setEditor("Alex", "Sam", true, NOW + 1));
		alex.tick(sam, kim);
		assertEquals(GroupGovernance.Role.EDITOR, sam.sync.governance().myRole("Sam"));
		return new Client[]{alex, sam, kim};
	}

	private static boolean anyWebhooksDocument(List<String> payloads)
	{
		for (String p : payloads)
		{
			if (p.contains("\"k\":\"" + GovernanceCodec.WEBHOOKS + "\""))
			{
				return true;
			}
		}
		return false;
	}

	@Test
	public void onlyTheLeaderMayTurnSharingOn()
	{
		Client[] c = group();
		Client sam = c[1];
		Client kim = c[2];
		assertFalse("an editor may not", sam.sync.shareWebhooks("Sam", true, leaderHooks(), NOW + 2));
		assertFalse("a member may not", kim.sync.shareWebhooks("Kim", true, leaderHooks(), NOW + 2));
		sam.sync.flush(MemberPrefs.NONE);
		kim.sync.flush(MemberPrefs.NONE);
		assertFalse(anyWebhooksDocument(sam.outbox));
		assertFalse(anyWebhooksDocument(kim.outbox));
		assertNull(sam.sync.governance().webhooks());
		assertNull(kim.sync.governance().webhooks());
	}

	@Test
	public void aDocumentSignedByAnEditorOrAnotherKeyIsRefused()
	{
		Client[] c = group();
		Client alex = c[0];
		Client kim = c[2];
		GroupSigner.Identity stranger = GroupSigner.Identity.create();
		// Signed in Sam's (the editor's) name, and in the leader's name with a key that is not the leader's.
		GroupWebhooks byEditor = GroupWebhooks.next(null, GroupMembers.key(GROUP), true, leaderHooks(), "Sam",
			stranger, NOW + 5);
		GroupWebhooks forged = GroupWebhooks.next(null, GroupMembers.key(GROUP), true, leaderHooks(), "Alex",
			stranger, NOW + 5);
		assertEquals(GroupGovernance.Verdict.REFUSED, kim.sync.governance().receiveWebhooks(byEditor));
		assertEquals(GroupGovernance.Verdict.REFUSED, kim.sync.governance().receiveWebhooks(forged));
		assertNull(kim.shared());

		// A real one, then the same with a URL swapped: the signature no longer checks.
		assertTrue(alex.sync.shareWebhooks("Alex", true, leaderHooks(), NOW + 6));
		GroupWebhooks real = alex.sync.governance().webhooks();
		Map<Webhook, Webhooks.Setting> swapped = new EnumMap<>(real.settings);
		swapped.put(Webhook.MAIN, new Webhooks.Setting(OWN_DROPS, DiscordPosterName.WEBHOOK, ""));
		GroupWebhooks tampered = new GroupWebhooks(real.group, real.version + 1, real.at, real.by, real.byKey, true,
			swapped, real.signature);
		assertEquals(GroupGovernance.Verdict.REFUSED, kim.sync.governance().receiveWebhooks(tampered));
	}

	@Test
	public void aMemberGetsTheLeadersWebhooksButNeverInConfigOrLogs()
	{
		Client[] c = group();
		Client alex = c[0];
		Client kim = c[2];
		Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		root.addAppender(appender);
		try
		{
			assertTrue(alex.sync.shareWebhooks("Alex", true, leaderHooks(), NOW + 2));
			alex.tick(kim);
			// A copy with a broken signature is refused, which logs: the log must not carry its URLs either.
			GroupWebhooks real = alex.sync.governance().webhooks();
			kim.sync.receive("Alex", GovernanceCodec.encode(gson, new GroupWebhooks(real.group, real.version + 1,
				real.at, real.by, real.byKey, true, real.settings, "bad")));
			kim.start();
		}
		finally
		{
			root.detachAppender(appender);
		}

		GroupWebhooks got = kim.shared();
		assertNotNull("read back from the group cache after a restart", got);
		assertEquals("Alex", got.by);
		assertEquals(PROGRESS, got.settings.get(Webhook.PROGRESS).url);
		for (Map.Entry<String, String> e : kim.config.entrySet())
		{
			assertFalse(e.getKey(), e.getValue().contains(TOKEN));
		}
		assertTrue("kept in the group cache instead", kim.cache.contains(TOKEN));
		for (ILoggingEvent e : appender.list)
		{
			assertFalse(e.getFormattedMessage(), e.getFormattedMessage().contains(TOKEN));
		}
		assertFalse(got.toString().contains(TOKEN));
		assertFalse(SettingsModel.webhookLines(GroupGovernance.Role.MEMBER, got.by).toString().contains(TOKEN));
		assertEquals("Using the group's webhooks (set by Alex).",
			SettingsModel.webhookLines(GroupGovernance.Role.MEMBER, got.by).get(0));
	}

	@Test
	public void aMembersOwnWebhookWinsForItsCategory()
	{
		Client[] c = group();
		Client alex = c[0];
		Client kim = c[2];
		alex.sync.shareWebhooks("Alex", true, leaderHooks(), NOW + 2);
		alex.tick(kim);

		Map<Webhook, Webhooks.Setting> mine = new EnumMap<>(Webhook.class);
		mine.put(Webhook.DROPS, new Webhooks.Setting(OWN_DROPS, DiscordPosterName.WEBHOOK, ""));
		Webhooks merged = new Webhooks(mine).withGroup(kim.shared());

		assertEquals("own wins", OWN_DROPS, merged.url(Webhook.DROPS));
		assertFalse(merged.fromGroup(Webhook.DROPS));
		assertEquals("the group's where the member left it empty", PROGRESS, merged.url(Webhook.PROGRESS));
		assertTrue(merged.fromGroup(Webhook.PROGRESS));
		assertEquals("with the leader's poster name", "Leader's Levels", merged.name(Webhook.PROGRESS));
		assertEquals("an empty category lands on the group's Main", MAIN, merged.url(Webhook.DEATHS));
		assertEquals("the group's choice for Main", Webhook.MAIN.defaultName, merged.name(Webhook.MAIN));
		assertNull("the group's choice for Deaths: Webhook's own", merged.name(Webhook.DEATHS));
		assertNull("not shared: nothing to fill in", none().withGroup(null).url(Webhook.MAIN));
	}

	@Test
	public void turningSharingOffClearsMembersCopiesOnTheNextSync()
	{
		Client[] c = group();
		Client alex = c[0];
		Client kim = c[2];
		alex.sync.shareWebhooks("Alex", true, leaderHooks(), NOW + 2);
		alex.tick(kim);
		assertNotNull(kim.shared());

		assertTrue(alex.sync.shareWebhooks("Alex", false, leaderHooks(), NOW + 3));
		alex.tick(kim);
		assertNull(kim.shared());
		assertNull(none().withGroup(kim.sync.governance().webhooks()).url(Webhook.MAIN));
		assertFalse("the cached copy is the off version, without URLs", kim.cache.contains(TOKEN));
		kim.start();
		assertNull("and stays off after a restart", kim.shared());
	}

	@Test
	public void theLeadersEditWhileSharingIsANewerVersionAndNoEditSendsNothing()
	{
		Client[] c = group();
		Client alex = c[0];
		Client kim = c[2];
		alex.sync.shareWebhooks("Alex", true, leaderHooks(), NOW + 2);
		alex.tick(kim);
		GroupWebhooks first = alex.sync.governance().webhooks();

		assertTrue(alex.sync.shareWebhooks("Alex", true, leaderHooks(), NOW + 3));
		assertSame("unchanged: the same document", first, alex.sync.governance().webhooks());
		alex.sync.flush(MemberPrefs.NONE);
		assertFalse(anyWebhooksDocument(alex.outbox));
		alex.outbox.clear();

		Map<Webhook, Webhooks.Setting> edited = new EnumMap<>(leaderHooks().settings());
		edited.put(Webhook.MAIN, new Webhooks.Setting(OWN_DROPS, DiscordPosterName.WEBHOOK, ""));
		alex.sync.shareWebhooks("Alex", true, new Webhooks(edited), NOW + 4);
		alex.tick(kim);
		assertTrue(kim.shared().version > first.version);
		assertEquals(OWN_DROPS, kim.shared().settings.get(Webhook.MAIN).url);
	}

	@Test
	public void theLiveBoardOnTheSharedWebhookIsPostedByTheLeaderOnly()
	{
		Client[] c = group();
		Client alex = c[0];
		alex.sync.shareWebhooks("Alex", true, leaderHooks(), NOW + 2);
		alex.tick(c[1], c[2]);

		int keepers = 0;
		for (Client client : c)
		{
			boolean leader = client.sync.governance().myRole(client.name) == GroupGovernance.Role.LEADER;
			Webhooks own = leader ? leaderHooks() : none();
			Webhooks hooks = own.withGroup(client.shared());
			assertEquals(ONLINE, hooks.url(Webhook.ONLINE));
			if (StatusBoard.keeps(OnlineStatus.LIVE_BOARD, hooks, leader))
			{
				keepers++;
			}
		}
		assertEquals("one board for the group", 1, keepers);

		// A member's own pasted Online status webhook is theirs, as before sharing existed.
		Map<Webhook, Webhooks.Setting> mine = new EnumMap<>(Webhook.class);
		mine.put(Webhook.ONLINE, new Webhooks.Setting(OWN_ONLINE, DiscordPosterName.WEBHOOK, ""));
		assertTrue(StatusBoard.keeps(OnlineStatus.LIVE_BOARD, new Webhooks(mine).withGroup(c[2].shared()), false));
		assertFalse(StatusBoard.keeps(OnlineStatus.POSTS, leaderHooks(), true));
	}

	@Test
	public void aNewLeaderDropsTheOldLeadersWebhooks()
	{
		Client[] c = group();
		Client alex = c[0];
		Client kim = c[2];
		alex.sync.shareWebhooks("Alex", true, leaderHooks(), NOW + 2);
		alex.tick(kim);
		assertNotNull(kim.shared());
		kim.sync.forgetLeader();
		assertNull("no leader, no shared webhooks", kim.shared());
	}

	@Test
	public void theFullestDocumentFitsOneMessage()
	{
		Map<Webhook, Webhooks.Setting> s = new EnumMap<>(Webhook.class);
		String longName = new String(new char[PosterNames.MAX]).replace('\0', 'n');
		for (Webhook w : Webhook.values())
		{
			String url = "https://canary.discordapp.com/api/webhooks/123456789012345678901/"
				+ new String(new char[68]).replace('\0', 'x');
			s.put(w, new Webhooks.Setting(url, DiscordPosterName.CUSTOM, longName));
		}
		GroupWebhooks doc = GroupWebhooks.next(null, "a twelve char", true, new Webhooks(s), "A twelve char",
			GroupSigner.Identity.create(), NOW);
		String payload = GovernanceCodec.encode(gson, doc);
		assertTrue(payload.length() + " chars", payload.length() < GroupCodec.MAX_PAYLOAD_CHARS);
		GroupWebhooks back = GovernanceCodec.decode(gson, payload).webhooks;
		assertTrue("survives the round trip with its signature", back.verify());
		assertEquals(7, back.settings.size());
	}
}
