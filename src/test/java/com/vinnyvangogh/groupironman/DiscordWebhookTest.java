package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static com.vinnyvangogh.groupironman.EventFixtures.items;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DiscordWebhookTest
{
	static final String URL = "https://discord.com/api/webhooks/123456789012345678/AbC-def_GHI123";
	private final Gson gson = new Gson();
	private final FakeItems items = new FakeItems();

	@Test
	public void onlyDiscordWebhookUrlsAreAccepted()
	{
		assertTrue(DiscordWebhook.valid(URL));
		assertTrue(DiscordWebhook.valid("https://discordapp.com/api/webhooks/1/x"));
		assertTrue(DiscordWebhook.valid("https://canary.discord.com/api/webhooks/1/x"));
		assertFalse(DiscordWebhook.valid(""));
		assertFalse(DiscordWebhook.valid(null));
		assertFalse("not https", DiscordWebhook.valid("http://discord.com/api/webhooks/1/x"));
		assertFalse("another host", DiscordWebhook.valid("https://discord.com.evil.example/api/webhooks/1/x"));
		assertFalse("not a webhook", DiscordWebhook.valid("https://discord.com/channels/1/2"));
		assertFalse("no token", DiscordWebhook.valid("https://discord.com/api/webhooks/1/"));
	}

	@Test
	public void anEmbedHasTitleColourIconValueAndTime()
	{
		GroupEvent drop = GroupEvent.of(GroupEvent.Type.DROP, NOW, "Alex").text("Vorkath")
			.items(items(FakeItems.WHIP, 1)).number(120).world(302).build();
		JsonObject o = parse(DiscordWebhook.payload(gson, drop, items, DiscordStyle.EMBED, "<@&1>", false));
		assertEquals("GIM Utilities", o.get("username").getAsString());
		assertFalse("a drop is not big: no mention", o.has("content"));
		assertEquals(0, o.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").size());
		JsonObject embed = o.getAsJsonArray("embeds").get(0).getAsJsonObject();
		assertEquals("Alex: Valuable drop", embed.get("title").getAsString());
		assertEquals("Vorkath dropped 1 x Abyssal whip, kill count 120", embed.get("description").getAsString());
		assertEquals(GroupEvent.Category.DROPS.colour, embed.get("color").getAsInt());
		assertEquals("https://static.runelite.net/cache/item/icon/4151.png",
			embed.getAsJsonObject("thumbnail").get("url").getAsString());
		assertEquals("2023-11-14T22:13:20Z", embed.get("timestamp").getAsString());
		assertEquals("Value", embed.getAsJsonArray("fields").get(0).getAsJsonObject().get("name").getAsString());
		assertEquals("1.5M (1,500,000) gp",
			embed.getAsJsonArray("fields").get(0).getAsJsonObject().get("value").getAsString());
		assertEquals("302", embed.getAsJsonArray("fields").get(1).getAsJsonObject().get("value").getAsString());
		assertFalse(embed.has("image"));
	}

	@Test
	public void coinsReadLikeTheGameWithTheExactAmountAndAPileIcon()
	{
		GroupEvent took = GroupEvent.of(GroupEvent.Type.STORAGE_WITHDRAWAL, NOW, "Alex")
			.items(items(FakeItems.COINS, 40_000)).build();
		JsonObject embed = parse(DiscordWebhook.payload(gson, took, items, DiscordStyle.EMBED, "", false))
			.getAsJsonArray("embeds").get(0).getAsJsonObject();
		// The exact total is in the Value field; the line's own total stays short so parentheses don't nest.
		assertEquals("took 40K (40,000) x Coins (40K gp) from group storage",
			embed.get("description").getAsString());
		assertEquals("40K (40,000) gp", embed.getAsJsonArray("fields").get(0).getAsJsonObject().get("value")
			.getAsString());
		assertEquals("the 10,000 coin pile, not one coin", "https://static.runelite.net/cache/item/icon/1004.png",
			embed.getAsJsonObject("thumbnail").get("url").getAsString());

		JsonObject plain = parse(DiscordWebhook.payload(gson, took, items, DiscordStyle.PLAIN, "", false));
		assertEquals("Alex: took 40K (40,000) x Coins (40K gp) from group storage",
			plain.get("content").getAsString());
	}

	@Test
	public void aBigEventMentionsAndMayPing()
	{
		GroupEvent pet = GroupEvent.of(GroupEvent.Type.PET, NOW, "Alex").build();
		JsonObject o = parse(DiscordWebhook.payload(gson, pet, items, DiscordStyle.EMBED, " <@&42> ", false));
		assertEquals("<@&42>", o.get("content").getAsString());
		assertEquals(2, o.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").size());
		JsonObject none = parse(DiscordWebhook.payload(gson, pet, items, DiscordStyle.EMBED, "", false));
		assertFalse(none.has("content"));
	}

	@Test
	public void plainTextIsOneLine()
	{
		GroupEvent pet = GroupEvent.of(GroupEvent.Type.PET, NOW, "Alex").build();
		JsonObject o = parse(DiscordWebhook.payload(gson, pet, items, DiscordStyle.PLAIN, "<@1>", false));
		assertEquals("<@1> Alex: a pet!", o.get("content").getAsString());
		assertFalse(o.has("embeds"));
	}

	@Test
	public void aScreenshotIsTheEmbedImage()
	{
		GroupEvent pet = GroupEvent.of(GroupEvent.Type.PET, NOW, "Alex").build();
		JsonObject embed = parse(DiscordWebhook.payload(gson, pet, items, DiscordStyle.EMBED, "", true))
			.getAsJsonArray("embeds").get(0).getAsJsonObject();
		assertEquals("attachment://screenshot.png", embed.getAsJsonObject("image").get("url").getAsString());
	}

	@Test
	public void theMasterSwitchCarriesTheThirdPartyWarning() throws Exception
	{
		ConfigItem item = GroupIronmanConfig.class.getMethod("discordEnabled").getAnnotation(ConfigItem.class);
		assertEquals(DiscordWebhook.WARNING, item.warning());
		for (String url : new String[]{"discordWebhookUrl", "discordDropsUrl", "discordDeathsUrl", "discordStorageUrl",
			"discordProgressUrl", "discordGroupUrl", "discordActivitiesUrl"})
		{
			assertTrue(url + " is a secret field", GroupIronmanConfig.class.getMethod(url)
				.getAnnotation(ConfigItem.class).secret());
		}
	}

	private static JsonObject parse(String json)
	{
		return new JsonParser().parse(json).getAsJsonObject();
	}
}
