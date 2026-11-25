package com.vinnyvangogh.groupironman;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Seven webhooks: Main and six categories (Board 2026-11-11). Discord Relay owns them and posts; GIM keeps where
 * each event goes and the URL fallback, for the leader's sharing and the live board. Poster names, warnings and
 * the webhook settings' layout moved to Discord Relay with their tests.
 */
public class WebhooksTest
{
	private static final String MAIN = "https://discord.com/api/webhooks/1/main-token";
	private static final String DROPS = "https://discord.com/api/webhooks/2/drops-token";
	private static final String DIGEST = "https://discord.com/api/webhooks/3/digest-token";

	private static Webhooks.Setting url(String url)
	{
		return new Webhooks.Setting(url, DiscordPosterName.WEBHOOK, "");
	}

	private static Map<Webhook, Webhooks.Setting> mainOnly()
	{
		Map<Webhook, Webhooks.Setting> out = new EnumMap<>(Webhook.class);
		out.put(Webhook.MAIN, url(MAIN));
		return out;
	}

	// ---- Adversarial first: what must never post, or post to the wrong place ----

	@Test
	public void noMainAndNoCategoryMeansNoPost()
	{
		for (Webhook w : Webhook.values())
		{
			assertNull(w + " has nowhere to post", Webhooks.NONE.url(w));
		}
		assertFalse(Webhooks.NONE.any());
	}

	@Test
	public void anInvalidCategoryUrlFallsBackToMainNeverPostsToTheBadOne()
	{
		Map<Webhook, Webhooks.Setting> s = mainOnly();
		s.put(Webhook.DEATHS, url("https://discord.com/channels/1/2"));
		assertEquals(MAIN, new Webhooks(s).url(Webhook.DEATHS));
	}

	@Test
	public void anInvalidMainWithNoCategoryPostsNothing()
	{
		Map<Webhook, Webhooks.Setting> s = new EnumMap<>(Webhook.class);
		s.put(Webhook.MAIN, url("not a link"));
		s.put(Webhook.DROPS, url(DROPS));
		Webhooks w = new Webhooks(s);
		assertNull(w.url(Webhook.PROGRESS));
		assertEquals("a category with its own still posts", DROPS, w.url(Webhook.DROPS));
		assertTrue(w.any());
	}

	// ---- Routing ----

	@Test
	public void anEmptyCategoryFallsBackToMain()
	{
		Webhooks w = new Webhooks(mainOnly());
		for (Webhook hook : Webhook.values())
		{
			assertEquals(hook + " falls back to Main", MAIN, w.url(hook));
		}
	}

	@Test
	public void aSetCategoryPostsToItsOwnUrl()
	{
		Map<Webhook, Webhooks.Setting> s = mainOnly();
		s.put(Webhook.DROPS, url("  \"" + DROPS + "\" "));
		s.put(Webhook.DIGEST, url(DIGEST));
		Webhooks w = new Webhooks(s);
		assertEquals("cleaned of quotes", DROPS, w.url(Webhook.DROPS));
		assertEquals("the daily digest follows the Digest webhook", DIGEST, w.url(Webhook.DIGEST));
		assertEquals(MAIN, w.url(Webhook.PROGRESS));
	}

	@Test
	public void aNameIsCutToDiscordsLimitNeverThroughASurrogatePair()
	{
		StringBuilder longName = new StringBuilder();
		for (int i = 0; i < 100; i++)
		{
			longName.append('a');
		}
		assertEquals(80, Webhooks.cleanName(longName.toString()).length());
		assertEquals("", Webhooks.cleanName(null));
		String emojiAt80 = new String(new char[79]).replace('\0', 'a') + "😀";
		assertEquals(79, Webhooks.cleanName(emojiAt80).length());
	}

	@Test
	public void theDefaultNamesAreRuneScapeFlavoured()
	{
		Map<Webhook, String> expected = new EnumMap<>(Webhook.class);
		expected.put(Webhook.MAIN, "Town Crier");
		expected.put(Webhook.ONLINE, "World Hopper");
		expected.put(Webhook.DROPS, "Party Pete");
		expected.put(Webhook.PROGRESS, "Skillcape Seller");
		expected.put(Webhook.DEATHS, "Death's Office");
		expected.put(Webhook.STORAGE, "Gielinor Banker");
		expected.put(Webhook.DIGEST, "Varrock Herald");
		for (Webhook w : Webhook.values())
		{
			assertEquals(expected.get(w), w.defaultName);
			assertTrue(w + " under Discord's 80", w.defaultName.length() <= Webhooks.MAX_NAME);
		}
	}

	// ---- Which events go where ----

	@Test
	public void eachEventTypeMapsToExactlyOneWebhook()
	{
		for (GroupEvent.Type t : GroupEvent.Type.values())
		{
			assertNotNull(t + " has a webhook", Webhook.of(t));
			assertFalse("the digest webhook carries the digest only, never an instant post", Webhook.of(t) == Webhook.DIGEST);
		}
	}

	@Test
	public void theCategoriesHoldWhatTheBoardListed()
	{
		expect(Webhook.ONLINE, GroupEvent.Type.ONLINE);
		expect(Webhook.DROPS, GroupEvent.Type.DROP, GroupEvent.Type.PET, GroupEvent.Type.CLOG, GroupEvent.Type.UNIQUE,
			GroupEvent.Type.BARROWS, GroupEvent.Type.RAID_UNIQUE, GroupEvent.Type.STORAGE_LAST);
		expect(Webhook.PROGRESS, GroupEvent.Type.LEVEL_UP, GroupEvent.Type.MAXED, GroupEvent.Type.XP_MILESTONE,
			GroupEvent.Type.COMBAT_LEVEL, GroupEvent.Type.TOTAL_LEVEL, GroupEvent.Type.QUEST,
			GroupEvent.Type.QUEST_POINTS, GroupEvent.Type.ACTIVITY_START, GroupEvent.Type.ACTIVITY_WAVE,
			GroupEvent.Type.ACTIVITY_COMPLETE);
		expect(Webhook.DEATHS, GroupEvent.Type.DEATH, GroupEvent.Type.DEATH_VALUE,
			GroupEvent.Type.GRAVESTONE_EXPIRING, GroupEvent.Type.HC_LIFE_LOST, GroupEvent.Type.ACTIVITY_DEATH);
		expect(Webhook.STORAGE, GroupEvent.Type.STORAGE_DEPOSIT, GroupEvent.Type.STORAGE_WITHDRAWAL,
			GroupEvent.Type.STORAGE_SUMMARY, GroupEvent.Type.STORAGE_MINIMUM);
		Set<GroupEvent.Type> listed = EnumSet.noneOf(GroupEvent.Type.class);
		for (GroupEvent.Type t : GroupEvent.Type.values())
		{
			if (Webhook.of(t) != Webhook.MAIN)
			{
				listed.add(t);
			}
		}
		assertEquals("exactly the Board's lists, nothing else outside Main", 27, listed.size());
		assertEquals("anything not covered goes to Main", Webhook.MAIN, Webhook.of(GroupEvent.Type.CLUE));
		assertEquals(Webhook.MAIN, Webhook.of(GroupEvent.Type.WEEKLY_RECAP));
	}

	private static void expect(Webhook webhook, GroupEvent.Type... types)
	{
		for (GroupEvent.Type t : types)
		{
			assertEquals(t.name(), webhook, Webhook.of(t));
		}
	}

	/** The webhooks, poster names and "Post to Discord" are Discord Relay's settings now, never GIM's. */
	@Test
	public void gimHasNoWebhookSettingsLeft()
	{
		for (Method m : ConfigLayoutTest.items())
		{
			String key = m.getAnnotation(ConfigItem.class).keyName();
			assertFalse(key, key.endsWith("Url") || key.startsWith("discordPoster") || key.startsWith("discordName")
				|| key.equals("discordEnabled") || key.equals("discordMention") || key.equals("discordScreenshot"));
		}
	}
}
