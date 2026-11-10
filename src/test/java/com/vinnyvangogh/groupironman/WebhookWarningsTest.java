package com.vinnyvangogh.groupironman;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** A set but broken webhook field is told to the player once, and the URL never is. */
public class WebhookWarningsTest
{
	private static final String GOOD = "https://discord.com/api/webhooks/123/SeCrEtToKeN";
	private static final String BAD = "https://discord.com/channels/123/SeCrEtToKeN";

	@Test
	public void aQuotedUrlIsAcceptedAfterStripping()
	{
		assertTrue(DiscordWebhook.valid("\"" + GOOD + "\""));
		assertTrue(DiscordWebhook.valid("  '" + GOOD + "'  "));
		assertTrue(DiscordWebhook.valid("“" + GOOD + "”"));
		assertTrue(DiscordWebhook.valid(" \"" + GOOD + "\"\n"));
		assertEquals(GOOD, DiscordWebhook.clean("\"" + GOOD + "\""));
		assertEquals("the route posts to the stripped URL", GOOD,
			DiscordWebhook.route(GroupEvent.Category.DROPS, new LinkedHashMap<>(), "'" + GOOD + "'"));
		assertFalse("a quote inside is still wrong", DiscordWebhook.valid(GOOD + "\"x"));
	}

	@Test
	public void anInvalidUrlWarnsOnceAndNeverShowsTheUrl()
	{
		WebhookWarnings warnings = new WebhookWarnings();
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("main", GOOD);
		fields.put("drops", BAD);
		fields.put("deaths", "");
		fields.put("storage", null);

		List<String> first = warnings.check(fields);
		assertEquals(1, first.size());
		assertEquals("GIM Utilities: the drops Discord webhook isn't a valid Discord webhook link", first.get(0));
		assertFalse(first.get(0).contains("discord.com"));
		assertFalse(first.get(0).contains("SeCrEtToKeN"));

		assertTrue("once: a later login or config change is quiet", warnings.check(fields).isEmpty());

		fields.put("drops", GOOD);
		assertTrue(warnings.check(fields).isEmpty());
		fields.put("drops", BAD);
		assertEquals("broken again after a fix warns again", 1, warnings.check(fields).size());
	}

	@Test
	public void aDifferentBadValueWarnsAgain()
	{
		WebhookWarnings warnings = new WebhookWarnings();
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("main", BAD);
		assertEquals(1, warnings.check(fields).size());
		fields.put("main", "not a link");
		assertEquals(1, warnings.check(fields).size());
	}

	@Test
	public void quotedGoodUrlsAndBlankFieldsNeverWarn()
	{
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("main", "\"" + GOOD + "\"");
		fields.put("drops", "   ");
		fields.put("deaths", "\"\"");
		assertTrue(new WebhookWarnings().check(fields).isEmpty());
	}
}
