package com.vinnyvangogh.groupironman;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
 * Seven webhooks: Main and six categories, each with its own poster name (Board 2026-11-11). An empty category
 * falls back to Main's URL but keeps its own poster name (Board pre-run answer).
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

	private static Webhooks.Setting named(DiscordPosterName mode, String name)
	{
		return new Webhooks.Setting(MAIN, mode, name);
	}

	private static Webhooks of(Map<Webhook, Webhooks.Setting> settings)
	{
		return new Webhooks(settings);
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
		Webhooks none = of(new EnumMap<>(Webhook.class));
		for (Webhook w : Webhook.values())
		{
			assertNull(w + " has nowhere to post", none.url(w));
		}
	}

	@Test
	public void anInvalidCategoryUrlFallsBackToMainNeverPostsToTheBadOne()
	{
		Map<Webhook, Webhooks.Setting> s = mainOnly();
		s.put(Webhook.DEATHS, url("https://discord.com/channels/1/2"));
		assertEquals(MAIN, of(s).url(Webhook.DEATHS));
	}

	@Test
	public void anInvalidMainWithNoCategoryPostsNothing()
	{
		Map<Webhook, Webhooks.Setting> s = new EnumMap<>(Webhook.class);
		s.put(Webhook.MAIN, url("not a link"));
		s.put(Webhook.DROPS, url(DROPS));
		Webhooks w = of(s);
		assertNull(w.url(Webhook.PROGRESS));
		assertEquals("a category with its own still posts", DROPS, w.url(Webhook.DROPS));
	}

	@Test
	public void aRefusedCustomNameIsNeverSentItFallsBackToTheDefaultName()
	{
		for (String bad : new String[]{"Discord Bot", "clyde", "a@b", "everyone"})
		{
			Map<Webhook, Webhooks.Setting> s = mainOnly();
			s.put(Webhook.DEATHS, named(DiscordPosterName.CUSTOM, bad));
			assertEquals(bad, "Death's Office", of(s).name(Webhook.DEATHS));
		}
	}

	// ---- Routing ----

	@Test
	public void anEmptyCategoryFallsBackToMain()
	{
		Webhooks w = of(mainOnly());
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
		Webhooks w = of(s);
		assertEquals("cleaned of quotes", DROPS, w.url(Webhook.DROPS));
		assertEquals("the daily digest follows the Digest webhook", DIGEST, w.url(Webhook.DIGEST));
		assertEquals(MAIN, w.url(Webhook.PROGRESS));
	}

	// ---- Poster names ----

	@Test
	public void theDefaultNamesAreRuneScapeFlavouredAndDiscordSafe()
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
			assertTrue(w + " under Discord's 80", w.defaultName.length() <= PosterNames.MAX);
			assertFalse(w + " is a name Discord accepts", PosterNames.refused(w.defaultName));
		}
	}

	@Test
	public void eachPosterNameChoice()
	{
		Map<Webhook, Webhooks.Setting> s = mainOnly();
		s.put(Webhook.DROPS, named(DiscordPosterName.WEBHOOK, "Loot Goblin"));
		s.put(Webhook.PROGRESS, named(DiscordPosterName.GIM_UTILITIES, "Loot Goblin"));
		s.put(Webhook.STORAGE, named(DiscordPosterName.CUSTOM, "  Gringotts Bot "));
		Webhooks w = of(s);
		assertNull("webhook's own: no username sent", w.name(Webhook.DROPS));
		assertEquals("default name", "Skillcape Seller", w.name(Webhook.PROGRESS));
		assertEquals("custom, trimmed", "Gringotts Bot", w.name(Webhook.STORAGE));
	}

	@Test
	public void anEmptyCustomNameFallsBackToTheDefaultName()
	{
		Map<Webhook, Webhooks.Setting> s = mainOnly();
		s.put(Webhook.STORAGE, named(DiscordPosterName.CUSTOM, "   "));
		s.put(Webhook.DIGEST, named(DiscordPosterName.CUSTOM, null));
		Webhooks w = of(s);
		assertEquals("Gielinor Banker", w.name(Webhook.STORAGE));
		assertEquals("Varrock Herald", w.name(Webhook.DIGEST));
	}

	@Test
	public void aCategoryOnTheMainUrlKeepsItsOwnPosterName()
	{
		Map<Webhook, Webhooks.Setting> s = new EnumMap<>(Webhook.class);
		s.put(Webhook.MAIN, new Webhooks.Setting(MAIN, DiscordPosterName.CUSTOM, "Our Clan"));
		s.put(Webhook.DROPS, new Webhooks.Setting("", DiscordPosterName.GIM_UTILITIES, ""));
		Webhooks w = of(s);
		assertEquals("posts to Main's URL", MAIN, w.url(Webhook.DROPS));
		assertEquals("under the category's own name", "Party Pete", w.name(Webhook.DROPS));
		assertNull("unset category: its own choice, Webhook's own, not Main's name", w.name(Webhook.DEATHS));
	}

	@Test
	public void aCustomNameOver80IsCut()
	{
		Map<Webhook, Webhooks.Setting> s = mainOnly();
		StringBuilder longName = new StringBuilder();
		for (int i = 0; i < 100; i++)
		{
			longName.append('a');
		}
		s.put(Webhook.MAIN, named(DiscordPosterName.CUSTOM, longName.toString()));
		assertEquals(80, of(s).name(Webhook.MAIN).length());
	}

	@Test
	public void customFieldsAreOnlyThoseOnCustomSoOnlyTheyWarn()
	{
		Map<Webhook, Webhooks.Setting> s = mainOnly();
		s.put(Webhook.DROPS, named(DiscordPosterName.CUSTOM, "Discord Drops"));
		s.put(Webhook.DEATHS, named(DiscordPosterName.WEBHOOK, "Clyde"));
		Map<String, String> fields = of(s).customNames();
		assertEquals("every webhook, by label", 7, fields.size());
		assertEquals("Discord Drops", fields.get("drops & loot"));
		assertEquals("not on Custom: unused, so blank", "", fields.get("deaths & danger"));
		List<String> lines = new PosterNames().check(fields);
		assertEquals(Arrays.asList(PosterNames.message("drops & loot")), lines);
		assertFalse("never the name itself", lines.get(0).contains("Discord Drops"));
	}

	@Test
	public void urlFieldsNameEveryWebhookForTheWarning()
	{
		Map<Webhook, Webhooks.Setting> s = mainOnly();
		s.put(Webhook.DIGEST, url("https://example.com/nope"));
		Map<String, String> fields = of(s).urlFields();
		assertEquals(Arrays.asList("main", "online status", "drops & loot", "progress", "deaths & danger",
			"group storage", "daily digest"), Arrays.asList(fields.keySet().toArray(new String[0])));
		assertEquals(Arrays.asList(WebhookWarnings.message("daily digest")), new WebhookWarnings().check(fields));
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
			GroupEvent.Type.QUEST_POINTS);
		expect(Webhook.DEATHS, GroupEvent.Type.DEATH, GroupEvent.Type.DEATH_VALUE,
			GroupEvent.Type.GRAVESTONE_EXPIRING, GroupEvent.Type.HC_LIFE_LOST);
		expect(Webhook.STORAGE, GroupEvent.Type.STORAGE_DEPOSIT, GroupEvent.Type.STORAGE_WITHDRAWAL,
			GroupEvent.Type.STORAGE_SUMMARY, GroupEvent.Type.STORAGE_MINIMUM);
		Set<GroupEvent.Type> listed = EnumSet.noneOf(GroupEvent.Type.class);
		for (Webhook w : Webhook.values())
		{
			if (w != Webhook.MAIN)
			{
				for (GroupEvent.Type t : GroupEvent.Type.values())
				{
					if (Webhook.of(t) == w)
					{
						listed.add(t);
					}
				}
			}
		}
		assertEquals("exactly the Board's lists, nothing else outside Main", 23,listed.size());
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

	// ---- Settings: keys and defaults ----

	@Test
	public void todaysWebhookAndPosterNameAreMainUnderTheSameKeys()
	{
		Map<String, String> keys = keysByMethod();
		assertEquals("discordWebhookUrl", keys.get("discordWebhookUrl"));
		assertEquals("discordPosterName", keys.get("discordPosterName"));
		assertEquals("discordNameMain", keys.get("discordNameMain"));
		GroupIronmanConfig defaults = new GroupIronmanConfig()
		{
		};
		assertEquals(DiscordPosterName.WEBHOOK, defaults.discordPosterName());
		assertEquals("the Custom box starts with the default name, ready to edit", "Town Crier",
			defaults.discordNameMain());
		assertEquals("saved under the constant, never the label", "GIM_UTILITIES",
			DiscordPosterName.GIM_UTILITIES.name());
		assertEquals("Default name", DiscordPosterName.GIM_UTILITIES.toString());
	}

	@Test
	public void eachCustomBoxStartsWithItsWebhooksDefaultName()
	{
		GroupIronmanConfig defaults = new GroupIronmanConfig()
		{
		};
		Webhooks w = Webhooks.of(defaults);
		Map<String, String> custom = new HashMap<>();
		Map<Webhook, Webhooks.Setting> all = w.settings();
		for (Webhook hook : Webhook.values())
		{
			assertEquals(hook + " custom box", hook.defaultName, all.get(hook).name);
			assertEquals(hook + " starts on Webhook's own", DiscordPosterName.WEBHOOK, all.get(hook).mode);
			assertEquals(hook + " has no URL", "", all.get(hook).url);
			custom.put(hook.label, all.get(hook).name);
		}
		assertEquals(7, custom.size());
	}

	@Test
	public void theSevenWebhooksSitInOrderInDiscordConnection()
	{
		String[] order = {"discordEnabled",
			"discordWebhookUrl", "discordPosterName", "discordNameMain",
			"discordGroupUrl", "discordPosterOnline", "discordNameGroup",
			"discordDropsUrl", "discordPosterDrops", "discordNameDrops",
			"discordProgressUrl", "discordPosterProgress", "discordNameProgress",
			"discordDeathsUrl", "discordPosterDeaths", "discordNameDeaths",
			"discordStorageUrl", "discordPosterStorage", "discordNameStorage",
			"discordDigestUrl", "discordPosterDigest", "discordNameDigest"};
		Map<String, ConfigItem> items = new HashMap<>();
		for (Method m : ConfigLayoutTest.items())
		{
			items.put(m.getName(), m.getAnnotation(ConfigItem.class));
		}
		Set<Integer> seen = new HashSet<>();
		for (int i = 0; i < order.length; i++)
		{
			ConfigItem item = items.get(order[i]);
			assertNotNull(order[i], item);
			assertEquals(order[i], GroupIronmanConfig.discordConnectionSection, item.section());
			assertEquals(order[i] + " position", i, item.position());
			assertFalse(order[i] + " shown", item.hidden());
			assertTrue(seen.add(item.position()));
			if (order[i].endsWith("Url"))
			{
				assertTrue(order[i] + " is a secret field", item.secret());
			}
		}
		assertTrue("the retired Activities webhook is kept, hidden", items.get("discordActivitiesUrl").hidden());
		assertTrue(items.get("discordNameActivities").hidden());
	}

	private static Map<String, String> keysByMethod()
	{
		Map<String, String> out = new HashMap<>();
		for (Method m : ConfigLayoutTest.items())
		{
			out.put(m.getName(), m.getAnnotation(ConfigItem.class).keyName());
		}
		return out;
	}
}
