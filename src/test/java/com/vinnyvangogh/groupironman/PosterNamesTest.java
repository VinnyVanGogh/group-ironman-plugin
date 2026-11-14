package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static com.vinnyvangogh.groupironman.EventFixtures.items;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The rules a Custom poster name must meet, and how a name lands in a post. Which name is used: WebhooksTest. */
public class PosterNamesTest
{
	private final Gson gson = new Gson();
	private final FakeItems items = new FakeItems();

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
	public void noNameSendsNoUsernameKey()
	{
		JsonObject o = post(null);
		assertFalse("Discord shows the webhook's own name", o.has("username"));
		assertFalse("and its own avatar", o.has("avatar_url"));
		assertEquals("the footer still says where it came from", "GIM Utilities",
			o.getAsJsonArray("embeds").get(0).getAsJsonObject().getAsJsonObject("footer").get("text").getAsString());
		assertEquals("Party Pete", post("Party Pete").get("username").getAsString());
	}

	@Test
	public void aNameOver80CharactersIsTrimmed()
	{
		String longName = "  " + repeat('a', 100) + "  ";
		assertEquals(repeat('a', 80), PosterNames.clean(longName));
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
		assertFalse("blank is not refused: it means the default name", PosterNames.refused(""));
	}

	@Test
	public void namesWithMentionsOrMarkdownAreRefused()
	{
		for (String bad : new String[]{"Loot @ Home", "Bank#1", "Time: now", "a ```code``` bot", "everyone",
			"Here", " EVERYONE "})
		{
			assertTrue(bad, PosterNames.refused(PosterNames.clean(bad)));
		}
		assertFalse("only exactly everyone or here", PosterNames.refused("everyone's bot"));
		assertFalse(PosterNames.refused("Over here"));
		assertFalse("one or two backticks are fine", PosterNames.refused("a `b` c"));
		assertFalse("an apostrophe is fine", PosterNames.refused("Death's Office"));

		PosterNames warnings = new PosterNames();
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("group storage", "Bank#1");
		List<String> lines = warnings.check(fields);
		assertEquals(Collections.singletonList(PosterNames.message("group storage")), lines);
		assertFalse("never the name itself", lines.get(0).contains("Bank#1"));
		assertTrue("once", warnings.check(fields).isEmpty());
	}

	@Test
	public void aRefusedNameGetsOneChatWarningNamingTheField()
	{
		PosterNames warnings = new PosterNames();
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("main", "Clyde");
		fields.put("deaths & danger", "Death Bot");
		List<String> lines = warnings.check(fields);
		assertEquals(1, lines.size());
		assertEquals(PosterNames.message("main"), lines.get(0));
		assertTrue(lines.get(0).contains("'discord'"));
		assertTrue(lines.get(0).contains("'clyde'"));
		assertTrue(lines.get(0).contains("default name"));
		assertFalse("never the name itself", lines.get(0).contains("Clyde"));
		assertTrue("once", warnings.check(fields).isEmpty());
		fields.put("main", "Main Bot");
		assertTrue(warnings.check(fields).isEmpty());
		fields.put("main", "Discord");
		assertEquals("broken again, warned again", 1, warnings.check(fields).size());
		fields.put("main", "");
		assertTrue("off Custom (blank), forgotten", warnings.check(fields).isEmpty());
		fields.put("main", "Discord");
		assertEquals("back on Custom, warned again", 1, warnings.check(fields).size());
	}

	@Test
	public void theDigestUsesThePosterName()
	{
		List<String> lines = Collections.singletonList("Alex: took 1 x Abyssal whip");
		JsonObject own = parse(DiscordWebhook.digest(gson, GroupEvent.Category.STORAGE, lines, null));
		assertFalse(own.has("username"));
		JsonObject named = parse(DiscordWebhook.digest(gson, GroupEvent.Category.STORAGE, lines, "Varrock Herald"));
		assertEquals("Varrock Herald", named.get("username").getAsString());
	}

	@Test
	public void theWebhooksOwnNameIsTheDefaultChoice()
	{
		GroupIronmanConfig defaults = new GroupIronmanConfig()
		{
		};
		assertEquals(DiscordPosterName.WEBHOOK, defaults.discordPosterName());
		assertEquals("Webhook's own", DiscordPosterName.WEBHOOK.toString());
		assertEquals("saved under the constant's name, not the label", "WEBHOOK", DiscordPosterName.WEBHOOK.name());
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
