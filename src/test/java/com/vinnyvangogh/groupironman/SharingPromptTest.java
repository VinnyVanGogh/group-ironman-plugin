package com.vinnyvangogh.groupironman;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The "Turn on sharing" card: one click after pasting the key, nothing on without it (Board 2026-11-13). */
public class SharingPromptTest
{
	private static final String KEY = GroupKey.generate();
	private static final String OTHER_KEY = GroupKey.generate();

	/** Sync and every Share switch off, no webhook. */
	private static SharingPrompt.Settings allOff(String key)
	{
		return new SharingPrompt.Settings(key, false, false, false, false, false);
	}

	/** Sync on, every Share switch off. */
	private static SharingPrompt.Settings syncOnly(String key, boolean webhook)
	{
		return new SharingPrompt.Settings(key, true, false, false, false, webhook);
	}

	private static SharingPrompt.Settings allSharing(String key, boolean webhook, boolean discordOn)
	{
		return new SharingPrompt.Settings(key, true, true, true, discordOn, webhook);
	}

	@Test
	public void shownAfterAKeyPasteWithTheSwitchesOff()
	{
		SharingPrompt p = new SharingPrompt();
		assertNull("sync off, nothing pasted: no card", p.card(allOff(KEY)));
		p.pasted(KEY);
		SharingPrompt.Card card = p.card(allOff(KEY));
		assertNotNull(card);
		assertTrue(card.sharing);
		assertFalse("no webhook, no Discord button", card.discord);
	}

	@Test
	public void shownWhileSyncIsOnAndEveryShareSwitchIsOff()
	{
		SharingPrompt p = new SharingPrompt();
		assertNotNull(p.card(syncOnly(KEY, false)));
	}

	@Test
	public void neverShownWithoutAKey()
	{
		SharingPrompt p = new SharingPrompt();
		p.pasted("");
		assertNull(p.card(syncOnly("", false)));
	}

	@Test
	public void hiddenAfterTurnOnSharingAndStaysHidden()
	{
		SharingPrompt p = new SharingPrompt();
		p.pasted(KEY);
		assertNotNull(p.card(allOff(KEY)));
		assertNull(p.card(allSharing(KEY, false, false)));
		// The player later unticks one switch: the card does not come back to nag.
		assertNull(p.card(new SharingPrompt.Settings(KEY, true, true, false, false, false)));
	}

	@Test
	public void notNowHidesItUntilTheKeyChanges()
	{
		SharingPrompt p = new SharingPrompt();
		p.pasted(KEY);
		p.notNow(KEY);
		assertNull(p.card(allOff(KEY)));
		assertNull("not even while sync is on with every switch off", p.card(syncOnly(KEY, false)));
		p.pasted(OTHER_KEY);
		assertNotNull("a new key brings it back", p.card(allOff(OTHER_KEY)));
	}

	@Test
	public void theDiscordButtonShowsOnlyWithAWebhook()
	{
		SharingPrompt p = new SharingPrompt();
		assertFalse(p.card(syncOnly(KEY, false)).discord);
		assertTrue(p.card(syncOnly(KEY, true)).discord);
		assertFalse("already posting", p.card(new SharingPrompt.Settings(KEY, true, false, false, true, true)).discord);
	}

	@Test
	public void afterTurnOnSharingTheDiscordButtonStaysUntilUsed()
	{
		SharingPrompt p = new SharingPrompt();
		p.pasted(KEY);
		SharingPrompt.Card card = p.card(allSharing(KEY, true, false));
		assertNotNull(card);
		assertFalse(card.sharing);
		assertTrue(card.discord);
		assertNull(p.card(allSharing(KEY, true, true)));
	}

	@Test
	public void turnOnSharingSetsExactlySyncAndEveryShareSwitch()
	{
		Map<String, Object> set = new LinkedHashMap<>();
		SharingPrompt.turnOnSharing(set::put);
		Map<String, Object> expected = new LinkedHashMap<>();
		for (String key : Arrays.asList("groupSync", "groupShareBank", "groupShareStorage", "groupShareStats",
			"groupShareCarried", "groupShareExtras", "groupShareDrops", "groupShareEvents"))
		{
			expected.put(key, true);
		}
		assertEquals(expected, set);
	}

	@Test
	public void postToDiscordSetsOnlyPostToDiscord()
	{
		Map<String, Object> set = new LinkedHashMap<>();
		SharingPrompt.postToDiscord(set::put);
		assertEquals(java.util.Collections.singletonMap("discordEnabled", (Object) true), set);
	}

	@Test
	public void everyKeyItSetsIsAnExistingBooleanSetting()
	{
		Set<String> booleans = new HashSet<>();
		for (Method m : GroupIronmanConfig.class.getMethods())
		{
			if (m.isAnnotationPresent(ConfigItem.class) && m.getReturnType() == boolean.class)
			{
				booleans.add(m.getAnnotation(ConfigItem.class).keyName());
			}
		}
		for (String key : SharingPrompt.SHARING_KEYS)
		{
			assertTrue(key, booleans.contains(key));
		}
		assertTrue(booleans.contains(SharingPrompt.DISCORD_KEY));
	}

	@Test
	public void settingsReadFromConfig()
	{
		GroupIronmanConfig fresh = new GroupIronmanConfig()
		{
			@Override
			public String groupKey()
			{
				return KEY;
			}
		};
		SharingPrompt.Settings s = SharingPrompt.Settings.of(fresh);
		assertEquals(KEY, s.key);
		assertTrue("Sync with my group defaults on", s.sync);
		assertFalse(s.anyShare);
		assertFalse(s.allSharing);
		assertFalse(s.webhook);
		assertNotNull("a fresh install with a key sees the card", new SharingPrompt().card(s));

		GroupIronmanConfig everything = new GroupIronmanConfig()
		{
			@Override
			public String groupKey()
			{
				return KEY;
			}

			@Override
			public boolean groupShareBank()
			{
				return true;
			}

			@Override
			public boolean groupShareStorage()
			{
				return true;
			}

			@Override
			public boolean groupShareStats()
			{
				return true;
			}

			@Override
			public boolean groupShareCarried()
			{
				return true;
			}

			@Override
			public boolean groupShareExtras()
			{
				return true;
			}

			@Override
			public boolean groupShareDrops()
			{
				return true;
			}

			@Override
			public boolean groupShareEvents()
			{
				return true;
			}

			@Override
			public String discordWebhookUrl()
			{
				return "https://discord.com/api/webhooks/123456789012345678/abcdefghijklmnopqrstuvwxyz";
			}
		};
		SharingPrompt.Settings all = SharingPrompt.Settings.of(everything);
		assertTrue(all.anyShare);
		assertTrue(all.allSharing);
		assertTrue(all.webhook);
	}
}
