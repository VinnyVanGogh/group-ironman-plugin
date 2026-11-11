package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static com.vinnyvangogh.groupironman.EventFixtures.items;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PosterNamesTest
{
	private static final String MAIN = DiscordWebhookTest.URL;
	private static final String STORAGE = "https://discord.com/api/webhooks/2/storage-token";
	private final Gson gson = new Gson();
	private final FakeItems items = new FakeItems();

	private static Map<GroupEvent.Category, String> urls()
	{
		Map<GroupEvent.Category, String> out = new EnumMap<>(GroupEvent.Category.class);
		out.put(GroupEvent.Category.STORAGE, STORAGE);
		return out;
	}

	private static Map<GroupEvent.Category, String> names(GroupEvent.Category category, String name)
	{
		Map<GroupEvent.Category, String> out = new EnumMap<>(GroupEvent.Category.class);
		out.put(category, name);
		return out;
	}

	private String name(DiscordPosterName mode, GroupEvent.Category category, Map<GroupEvent.Category, String> names,
		String main)
	{
		return PosterNames.name(mode, category, urls(), MAIN, names, main);
	}

	private JsonObject post(String username)
	{
		GroupEvent drop = GroupEvent.of(GroupEvent.Type.DROP, NOW, "Alex").text("Vorkath")
			.items(items(FakeItems.WHIP, 1)).number(120).build();
		ItemLookup lookup = Amounts.BOTH.words(items);
		return parse(DiscordWebhook.payload(gson, DiscordMerge.single(drop, lookup), lookup, DiscordStyle.EMBED, "",
			false, null, username));
	}

	private static JsonObject parse(String json)
	{
		return new JsonParser().parse(json).getAsJsonObject();
	}

	@Test
	public void theWebhooksOwnNameSendsNoUsernameKey()
	{
		assertNull(name(DiscordPosterName.WEBHOOK, GroupEvent.Category.STORAGE,
			names(GroupEvent.Category.STORAGE, "Gringotts Bot"), "Main Bot"));
		JsonObject o = post(null);
		assertFalse("Discord shows the webhook's own name", o.has("username"));
		assertFalse("and its own avatar", o.has("avatar_url"));
		assertEquals("the footer still says where it came from", "GIM Utilities",
			o.getAsJsonArray("embeds").get(0).getAsJsonObject().getAsJsonObject("footer").get("text").getAsString());
	}

	@Test
	public void gimUtilitiesIsTodaysName()
	{
		assertEquals("GIM Utilities", name(DiscordPosterName.GIM_UTILITIES, GroupEvent.Category.DROPS,
			Collections.emptyMap(), ""));
		assertEquals("GIM Utilities", post("GIM Utilities").get("username").getAsString());
	}

	@Test
	public void customWithABlankFieldSendsNoUsername()
	{
		assertNull(name(DiscordPosterName.CUSTOM, GroupEvent.Category.STORAGE,
			names(GroupEvent.Category.STORAGE, "   "), "Main Bot"));
		assertNull("unset", name(DiscordPosterName.CUSTOM, GroupEvent.Category.DEATHS, Collections.emptyMap(), ""));
		assertNull(name(DiscordPosterName.CUSTOM, GroupEvent.Category.DEATHS, Collections.emptyMap(), null));
	}

	@Test
	public void customWithANameSendsThatNamePerCategory()
	{
		Map<GroupEvent.Category, String> set = new EnumMap<>(GroupEvent.Category.class);
		set.put(GroupEvent.Category.STORAGE, "  Gringotts Bot ");
		set.put(GroupEvent.Category.DROPS, "Loot Goblin");
		assertEquals("Gringotts Bot", name(DiscordPosterName.CUSTOM, GroupEvent.Category.STORAGE, set, "Main Bot"));
		assertEquals("a category's own name, even on the main webhook", "Loot Goblin",
			name(DiscordPosterName.CUSTOM, GroupEvent.Category.DROPS, set, "Main Bot"));
		assertEquals("no category name, on the main webhook: the main name", "Main Bot",
			name(DiscordPosterName.CUSTOM, GroupEvent.Category.DEATHS, set, "Main Bot"));
		assertEquals("Gringotts Bot", post("Gringotts Bot").get("username").getAsString());
	}

	@Test
	public void aBlankCategoryNameOnItsOwnWebhookIsTheWebhooksName()
	{
		assertNull("the storage webhook's own name, not the main custom name",
			name(DiscordPosterName.CUSTOM, GroupEvent.Category.STORAGE, Collections.emptyMap(), "Main Bot"));
	}

	@Test
	public void aNameOver80CharactersIsTrimmed()
	{
		String longName = "  " + repeat('a', 100) + "  ";
		assertEquals(repeat('a', 80), PosterNames.clean(longName));
		assertEquals(repeat('b', 80), name(DiscordPosterName.CUSTOM, GroupEvent.Category.DEATHS,
			Collections.emptyMap(), repeat('b', 81)));
		assertEquals("never half a surrogate pair", 79, PosterNames.clean(repeat('c', 79) + "😀").length());
		assertEquals("cut, then trimmed", repeat('d', 70), PosterNames.clean(repeat('d', 70) + repeat(' ', 20) + "x"));
	}

	@Test
	public void namesDiscordRejectsAreRefused()
	{
		assertTrue(PosterNames.refused("Discord Bot"));
		assertTrue(PosterNames.refused("my CLYDE"));
		assertTrue(PosterNames.refused("xdIsCoRdx"));
		assertFalse(PosterNames.refused("Gringotts Bot"));
		assertNull("refused names are not sent", name(DiscordPosterName.CUSTOM, GroupEvent.Category.DEATHS,
			names(GroupEvent.Category.DEATHS, "Discord Deaths"), "Main Bot"));
		assertNull(name(DiscordPosterName.CUSTOM, GroupEvent.Category.DEATHS, Collections.emptyMap(), "Clyde"));
	}

	@Test
	public void aRefusedNameGetsOneChatWarningNamingTheField()
	{
		PosterNames warnings = new PosterNames();
		Map<String, String> fields = new java.util.LinkedHashMap<>();
		fields.put("main", "Clyde");
		fields.put("deaths", "Death Bot");
		List<String> lines = warnings.check(DiscordPosterName.CUSTOM, fields);
		assertEquals(1, lines.size());
		assertEquals(PosterNames.message("main"), lines.get(0));
		assertTrue(lines.get(0).contains("'discord' or 'clyde'"));
		assertFalse("never the name itself", lines.get(0).contains("Clyde"));
		assertTrue("once", warnings.check(DiscordPosterName.CUSTOM, fields).isEmpty());
		fields.put("main", "Main Bot");
		assertTrue(warnings.check(DiscordPosterName.CUSTOM, fields).isEmpty());
		fields.put("main", "Discord");
		assertEquals("broken again, warned again", 1, warnings.check(DiscordPosterName.CUSTOM, fields).size());
		assertTrue("names are unused unless Custom is picked",
			new PosterNames().check(DiscordPosterName.WEBHOOK, fields).isEmpty());
	}

	@Test
	public void theDigestUsesThePosterName()
	{
		List<String> lines = Collections.singletonList("Alex: took 1 x Abyssal whip");
		JsonObject own = parse(DiscordWebhook.digest(gson, GroupEvent.Category.STORAGE, lines, null));
		assertFalse(own.has("username"));
		JsonObject custom = parse(DiscordWebhook.digest(gson, GroupEvent.Category.STORAGE, lines, "Gringotts Bot"));
		assertEquals("Gringotts Bot", custom.get("username").getAsString());
	}

	@Test
	public void theWebhooksOwnNameIsTheDefault()
	{
		GroupIronmanConfig defaults = new GroupIronmanConfig()
		{
		};
		assertEquals(DiscordPosterName.WEBHOOK, defaults.discordPosterName());
		assertEquals("", defaults.discordNameMain());
		assertEquals("Use the webhook's own name", DiscordPosterName.WEBHOOK.toString());
	}

	private static String repeat(char c, int n)
	{
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < n; i++)
		{
			s.append(c);
		}
		return s.toString();
	}
}
