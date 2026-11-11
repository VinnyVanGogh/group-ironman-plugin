package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The live online board: posted once, edited after, posted again when deleted, rate-limited, never leaking the URL. */
public class StatusBoardTest
{
	private static final String URL = "https://discord.com/api/webhooks/123456/secretTOKEN_value";
	private static final String OTHER = "https://discord.com/api/webhooks/999/anotherTOKEN";
	private static final Gson GSON = new Gson();
	private static final long GAP = StatusBoard.MIN_GAP_MS;

	private final StatusBoard board = new StatusBoard();
	private String saved;

	private void target(String url)
	{
		board.target(url, () -> saved);
	}

	/** Posts the board with {@code lines} at {@code at} and answers it with message id 111. */
	private void posted(String lines, long at)
	{
		target(URL);
		board.show(lines, at);
		StatusBoard.Send send = board.next(at, false);
		assertTrue("the first send posts", send.create());
		board.done(send, 200, "111", -1, at);
		saved = board.takeSaved();
	}

	@Test
	public void theBoardIsPostedOnceThenEdited()
	{
		posted("a", NOW);
		assertTrue(saved.endsWith("\t111"));
		assertFalse("only a hash of the webhook is saved", saved.contains("secretTOKEN") || saved.contains("discord"));
		assertNull("nothing changed", board.next(NOW + GAP, false));
		board.show("b", NOW + GAP);
		StatusBoard.Send edit = board.next(NOW + GAP, false);
		assertFalse(edit.create());
		assertEquals("111", edit.messageId);
		assertEquals("b", edit.lines);
		board.done(edit, 200, null, -1, NOW + GAP);
		assertNull("an edit keeps the id", board.takeSaved());
	}

	@Test
	public void aRestartEditsTheSavedBoardAndANewWebhookPostsItsOwn()
	{
		posted("a", NOW);
		StatusBoard restarted = new StatusBoard();
		restarted.target(URL, () -> saved);
		restarted.show("a", NOW);
		assertEquals("111", restarted.next(NOW, false).messageId);

		StatusBoard moved = new StatusBoard();
		moved.target(OTHER, () -> saved);
		moved.show("a", NOW);
		assertTrue("the saved id belongs to the other webhook", moved.next(NOW, false).create());
	}

	@Test
	public void aDeletedBoardIsPostedAgain()
	{
		posted("a", NOW);
		board.show("b", NOW + GAP);
		StatusBoard.Send edit = board.next(NOW + GAP, false);
		board.done(edit, 404, null, -1, NOW + GAP);
		assertEquals("the id is forgotten", "", board.takeSaved());
		assertNull("still at most one request per gap", board.next(NOW + GAP + 1, false));
		StatusBoard.Send again = board.next(NOW + 2 * GAP, false);
		assertTrue(again.create());
		assertEquals("b", again.lines);
		board.done(again, 200, "222", -1, NOW + 2 * GAP);
		assertTrue(board.takeSaved().endsWith("\t222"));
	}

	@Test
	public void atMostOneRequestPerGapAndOnlyTheNewestLines()
	{
		posted("a", NOW);
		board.show("b", NOW + 1_000);
		assertNull(board.next(NOW + 1_000, false));
		board.show("c", NOW + 5_000);
		assertNull(board.next(NOW + GAP - 1, false));
		StatusBoard.Send edit = board.next(NOW + GAP, false);
		assertEquals("c", edit.lines);
		assertEquals("the board says when it last changed", NOW + 5_000, edit.changedAt);
		assertNull("one in flight at a time", board.next(NOW + 3 * GAP, false));
	}

	@Test
	public void quietHoursAndRetryAfterHoldTheEdit()
	{
		posted("a", NOW);
		board.show("b", NOW + GAP);
		assertNull("quiet hours", board.next(NOW + GAP, true));
		StatusBoard.Send edit = board.next(NOW + 2 * GAP, false);
		board.done(edit, 429, null, 60_000, NOW + 2 * GAP);
		assertNull(board.next(NOW + 3 * GAP, false));
		assertEquals("b", board.next(NOW + 2 * GAP + 60_000, false).lines);
	}

	@Test
	public void aBrokenWebhookIsNotRetriedUntilTheLinesChange()
	{
		target(URL);
		board.show("a", NOW);
		board.done(board.next(NOW, false), 401, null, -1, NOW);
		assertNull(board.next(NOW + 10 * GAP, false));
		board.show("b", NOW + 10 * GAP);
		assertTrue(board.next(NOW + 10 * GAP, false).create());
	}

	@Test
	public void noWebhookNoRequest()
	{
		target(null);
		board.show("a", NOW);
		assertNull(board.next(NOW, false));
	}

	@Test
	public void thePayloadNeverCarriesTheWebhook()
	{
		StatusBoard.Send create = new StatusBoard.Send(URL, null, "line", NOW);
		StatusBoard.Send edit = new StatusBoard.Send(URL, "111", "line", NOW);
		for (StatusBoard.Send send : Arrays.asList(create, edit))
		{
			for (DiscordStyle style : DiscordStyle.values())
			{
				String json = StatusBoard.payload(GSON, send, style, "Iron Pals");
				assertFalse(json, json.contains("secretTOKEN") || json.contains("123456") || json.contains("webhooks"));
				JsonObject o = GSON.fromJson(json, JsonObject.class);
				assertEquals("mentions nobody", 0, o.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").size());
				assertEquals("an edit takes no username", send.create(), o.has("username"));
				assertFalse("the webhook's own name sends none",
					StatusBoard.payload(GSON, send, style, null).contains("username"));
				String text = style == DiscordStyle.PLAIN ? o.get("content").getAsString()
					: o.getAsJsonArray("embeds").get(0).getAsJsonObject().get("description").getAsString();
				assertEquals("line\nUpdated <t:" + NOW / 1000 + ":R>", text.substring(text.indexOf("line")));
			}
		}
	}

	@Test
	public void eachMemberOnlineOfflineOrUnknown()
	{
		GroupMembers roster = GroupMembers.of("Iron Pals", Arrays.asList("Sam", "Alex", "Kim", "Jo"));
		Map<String, Long> heard = new HashMap<>();
		heard.put("Sam", NOW - 10_000);
		heard.put("Jo", NOW - Presence.ONLINE_MS - 60_000);
		Map<String, MemberStats> stats = new HashMap<>();
		stats.put("Sam", new MemberStats(Collections.emptyMap(), 100, 100, 302, 0));
		stats.put("Jo", new MemberStats(Collections.emptyMap(), 100, 100, 330, 0));
		StatusBoard.Self me = new StatusBoard.Self("Alex", false, 301, NOW - 120_000);
		String lines = StatusBoard.lines(roster, heard, stats, me, NOW);
		assertEquals(String.join("\n",
			StatusBoard.OFFLINE + " **Alex** offline (last seen <t:" + (NOW - 120_000) / 1000 + ":R>)",
			StatusBoard.OFFLINE + " **Jo** offline (last seen <t:" + (NOW - Presence.ONLINE_MS - 60_000) / 1000 + ":R>)",
			StatusBoard.UNKNOWN + " **Kim** unknown",
			StatusBoard.ONLINE + " **Sam** online (world 302)"), lines);

		StatusBoard.Self hopped = new StatusBoard.Self("Alex", true, 444, 0);
		assertTrue(StatusBoard.lines(roster, heard, stats, hopped, NOW)
			.startsWith(StatusBoard.ONLINE + " **Alex** online (world 444)"));
	}

	@Test
	public void withoutTheRosterOnlyMembersHeardFromAreListed()
	{
		Map<String, Long> heard = Collections.singletonMap("Sam", NOW);
		StatusBoard.Self me = new StatusBoard.Self("Alex", true, 301, 0);
		assertEquals(StatusBoard.ONLINE + " **Alex** online (world 301)\n" + StatusBoard.ONLINE + " **Sam** online",
			StatusBoard.lines(GroupMembers.UNKNOWN, heard, Collections.emptyMap(), me, NOW));
		assertEquals("", StatusBoard.lines(GroupMembers.UNKNOWN, Collections.emptyMap(), Collections.emptyMap(), null,
			NOW));
	}

	/** The board through the real sender, with a fake transport: the requests it makes, and their answers. */
	private final List<DiscordQueue.Post> requests = new ArrayList<>();
	private final List<DiscordSender.Done> answers = new ArrayList<>();
	private long now = NOW;
	private final DiscordSender sender = new DiscordSender((post, done) ->
	{
		requests.add(post);
		answers.add(done);
	}, () -> now, () -> 12);

	private void sendNext()
	{
		StatusBoard.Send send = board.next(now, false);
		board.send(sender, send, StatusBoard.payload(GSON, send, DiscordStyle.EMBED, null), () -> now);
		sender.pump();
	}

	@Test
	public void theBoardGoesThroughTheSendersQueuePostThenEdit()
	{
		String thread = URL + "?thread_id=42";
		board.target(thread, () -> null);
		board.show("a", now);
		sendNext();
		DiscordQueue.Post post = requests.get(0);
		assertNull("a new board is a post", post.edit);
		assertEquals("waits for the id, keeps the thread", URL + "?thread_id=42&wait=true", DiscordSender.target(post));
		answers.remove(0).done(200, null, "111");
		assertTrue(board.takeSaved().endsWith("\t111"));

		now += GAP;
		board.show("b", now);
		sendNext();
		DiscordQueue.Post edit = requests.get(1);
		assertEquals("then edits it in place", "111", edit.edit);
		assertEquals(URL + "/messages/111?thread_id=42", DiscordSender.target(edit));
		answers.remove(0).done(404, null);
		assertEquals("a deleted board is forgotten", "", board.takeSaved());
		now += GAP;
		sendNext();
		assertNull("and posted again", requests.get(2).edit);
	}

	@Test
	public void theBoardSharesThePerMinuteLimit()
	{
		for (int i = 0; i < DiscordQueue.PER_MINUTE; i++)
		{
			sender.post(URL, "post " + i, null);
			sender.pump();
			answers.remove(0).done(204, null);
		}
		target(URL);
		board.show("a", now);
		sendNext();
		assertEquals("the minute's posts used the limit", DiscordQueue.PER_MINUTE, requests.size());
		now += 60_000;
		sender.pump();
		assertEquals("the board goes when the minute is up", DiscordQueue.PER_MINUTE + 1, requests.size());
	}

	@Test
	public void aBoardRequestAFullQueueDropsIsSentAgain()
	{
		target(URL);
		board.show("a", now);
		StatusBoard.Send send = board.next(now, false);
		board.send(sender, send, "{}", () -> now);
		for (int i = 0; i < DiscordQueue.MAX_QUEUED; i++)
		{
			sender.post(URL, "post " + i, null);
		}
		assertNull("not before the gap", board.next(now + 1, false));
		assertTrue("dropped, so tried again", board.next(now + GAP, false).create());
	}

	@Test
	public void anUpgradeKeepsWhatItsOldSwitchDid()
	{
		assertEquals(OnlineStatus.POSTS, OnlineStatus.upgrading(true, true));
		assertEquals(OnlineStatus.OFF, OnlineStatus.upgrading(false, true));
		assertNull("a new install takes the default", OnlineStatus.upgrading(false, false));
		assertEquals("a new install posts; a member opts into the board (Board 2026-11-10)", OnlineStatus.POSTS,
			new GroupIronmanConfig()
			{
			}.discordOnlineStatus());
		assertFalse("the board is not a post", EventSettings.from(new GroupIronmanConfig()
		{
		}).discord.contains(GroupEvent.Type.ONLINE));
	}
}
