package com.vinnyvangogh.groupironman;

import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Board 2026-11-14: the board was posted three times in one afternoon of restarts and hops, the old ones left.
 * One board per webhook, ever: a restart edits the saved board, a startup fallback never posts, the same webhook
 * in any URL form is one board, and any new board deletes the ones before it.
 */
public class StatusBoardRestartTest
{
	private static final String ONLINE = "https://discord.com/api/webhooks/123456/secretTOKEN_value";
	private static final String MAIN = "https://discord.com/api/webhooks/777/mainTOKEN";
	private static final long GAP = StatusBoard.MIN_GAP_MS;
	private static final long DAY = 20_000;

	/** This client's saved boards, as the file keeps them. */
	private String saved = "";

	private StatusBoard client(String url)
	{
		StatusBoard board = new StatusBoard();
		board.target(url, () -> saved);
		return board;
	}

	private void save(StatusBoard board)
	{
		String line = board.takeSaved();
		if (line != null)
		{
			saved = StatusBoard.merge(saved, line);
		}
	}

	/** A client posts the board on {@code url} as {@code id} on {@code day} and saves it. */
	private StatusBoard posted(String url, String id, long day, long at)
	{
		StatusBoard board = client(url);
		board.show("a", at);
		StatusBoard.Send send = board.next(at, false, day, true);
		assertTrue(send.create());
		board.done(send, 200, id, -1, at);
		save(board);
		return board;
	}

	@Test
	public void aRestartWithTheSameWebhookEditsTheSavedBoard()
	{
		posted(ONLINE, "111", DAY, NOW);
		for (int restart = 1; restart <= 3; restart++)
		{
			StatusBoard board = client(ONLINE);
			board.show("b" + restart, NOW + restart * GAP);
			StatusBoard.Send send = board.next(NOW + restart * GAP, false, DAY, true);
			assertFalse("restart " + restart + ": no new board", send.create());
			assertEquals("111", send.messageId);
			board.done(send, 200, null, -1, NOW + restart * GAP);
			save(board);
		}
	}

	@Test
	public void theSameWebhookInAnyUrlFormIsOneBoard()
	{
		posted(ONLINE, "111", DAY, NOW);
		String[] forms = {ONLINE + "/", ONLINE + "?thread_id=42", ONLINE + "/?thread_id=42&wait=true",
			"https://discordapp.com/api/webhooks/123456/secretTOKEN_value",
			"https://ptb.discord.com/api/webhooks/123456/secretTOKEN_value"};
		for (String url : forms)
		{
			assertEquals(url, "w123456", StatusBoard.hookOf(url));
			StatusBoard board = client(url);
			board.show("b", NOW);
			assertEquals(url, "111", board.next(NOW, false, DAY, true).messageId);
		}
		assertEquals("w777", StatusBoard.hookOf(MAIN));
		assertNull(StatusBoard.hookOf("https://example.com/hook"));
		assertNull(StatusBoard.hookOf(null));
	}

	@Test
	public void aUrlFormChangeMidSessionKeepsTheBoard()
	{
		StatusBoard board = posted(ONLINE, "111", DAY, NOW);
		board.target(ONLINE + "?thread_id=42", () -> "");
		board.show("b", NOW + GAP);
		StatusBoard.Send edit = board.next(NOW + GAP, false, DAY, true);
		assertEquals("111", edit.messageId);
		assertEquals("and goes to the new form", ONLINE + "?thread_id=42", edit.url);
	}

	@Test
	public void theSavedLineHoldsTheWebhookIdNeverTheToken()
	{
		posted(ONLINE, "111", DAY, NOW);
		assertEquals("w123456\t111\t" + DAY, saved);
		assertFalse(saved.contains("secretTOKEN"));
	}

	@Test
	public void aMainFallbackThenOnlineAtStartupPostsNoExtraBoard()
	{
		posted(ONLINE, "111", DAY, NOW);
		// Restart: the first ticks see Main (the leader's role or shared webhooks not in yet), then Online.
		StatusBoard board = client(null);
		board.settle(StatusBoard.settled(0, NOW, false));
		board.target(MAIN, () -> saved);
		board.show("a", NOW);
		assertNull("not settled: nothing on Main", board.next(NOW, false, DAY, true));
		board.target(ONLINE, () -> saved);
		assertNull("not settled: nothing at all", board.next(NOW + GAP, false, DAY, true));
		long login = NOW + GAP;
		board.settle(StatusBoard.settled(login, login + StatusBoard.SETTLE_MS - 1, true));
		assertNull("still settling", board.next(login + StatusBoard.SETTLE_MS - 1, false, DAY, true));
		board.settle(StatusBoard.settled(login, login + StatusBoard.SETTLE_MS, true));
		StatusBoard.Send send = board.next(login + StatusBoard.SETTLE_MS, false, DAY, true);
		assertFalse("settled on Online: the saved board is edited", send.create());
		assertEquals("111", send.messageId);
	}

	@Test
	public void settledNeedsALoginTheWaitAndTheSavedBoards()
	{
		assertFalse("no login yet", StatusBoard.settled(0, NOW, true));
		assertFalse("saved boards not read", StatusBoard.settled(NOW, NOW + StatusBoard.SETTLE_MS, false));
		assertFalse(StatusBoard.settled(NOW, NOW + StatusBoard.SETTLE_MS - 1, true));
		assertTrue(StatusBoard.settled(NOW, NOW + StatusBoard.SETTLE_MS, true));
	}

	@Test
	public void aHopDoesNotRepost()
	{
		StatusBoard board = posted(ONLINE, "111", DAY, NOW);
		// The engine re-targets every tick; a hop changes only the world on the lines.
		board.target(ONLINE, () -> saved);
		board.show("a (world 302)", NOW + GAP);
		StatusBoard.Send send = board.next(NOW + GAP, false, DAY, true);
		assertFalse(send.create());
		assertEquals("111", send.messageId);
		board.done(send, 200, null, -1, NOW + GAP);
		assertNull("nothing new to save", board.takeSaved());
	}

	@Test
	public void aForcedNewPostDeletesThePreviousBoards()
	{
		// Saved by an earlier client: board 333, with 111 and 222 still to delete.
		saved = "w123456\t333\t" + DAY + "\t111,222";
		StatusBoard board = client(ONLINE);
		board.show("a", NOW);
		StatusBoard.Send first = board.next(NOW, false, DAY, true);
		assertTrue(first.delete);
		assertEquals("111", first.messageId);
		board.done(first, 204, null, -1, NOW);
		save(board);
		assertEquals("w123456\t333\t" + DAY + "\t222", saved);
		StatusBoard.Send second = board.next(NOW, false, DAY, true);
		assertEquals("222", second.messageId);
		board.done(second, 404, null, -1, NOW);
		save(board);
		assertEquals("already gone: forgotten too", "w123456\t333\t" + DAY, saved);

		// A new day's board: posted first, then 333 deleted once 444 is saved.
		StatusBoard.Send post = board.next(NOW + GAP, false, DAY + 1, true);
		assertTrue(post.create());
		board.done(post, 200, "444", -1, NOW + GAP);
		assertNull("no delete before the save", board.next(NOW + GAP, false, DAY + 1, true));
		save(board);
		assertEquals("w123456\t444\t" + (DAY + 1) + "\t333", saved);
		StatusBoard.Send delete = board.next(NOW + GAP, false, DAY + 1, true);
		assertTrue(delete.delete);
		assertEquals("333", delete.messageId);
	}

	@Test
	public void aNewBoardForAnyReasonQueuesTheOneBeforeIt()
	{
		saved = "w123456\t111";
		StatusBoard board = client(ONLINE);
		board.show("a", NOW);
		// A board saved without its day is brought down on the first send: a post that replaces 111.
		StatusBoard.Send post = board.next(NOW, false, DAY, true);
		assertTrue(post.create());
		board.done(post, 200, "222", -1, NOW);
		save(board);
		assertEquals("w123456\t222\t" + DAY + "\t111", saved);
	}

	@Test
	public void onlyTheLastFewOldBoardsAreKept()
	{
		StatusBoard board = client(ONLINE);
		for (int i = 1; i <= StatusBoard.MAX_OLD + 3; i++)
		{
			// Never saved in between, so no delete goes out and every post queues the one before it.
			board.show("a" + i, NOW + i * GAP);
			StatusBoard.Send post = board.next(NOW + i * GAP, false, DAY + i, true);
			assertTrue(post.create());
			board.done(post, 200, Integer.toString(100 + i), -1, NOW + i * GAP);
		}
		save(board);
		String olds = saved.split("\t")[3];
		assertEquals(saved, StatusBoard.MAX_OLD, olds.split(",").length);
		assertTrue("the newest old ones", olds.endsWith(Integer.toString(100 + StatusBoard.MAX_OLD + 2)));
	}

	@Test
	public void eachWebhookKeepsItsOwnBoard()
	{
		posted(ONLINE, "111", DAY, NOW);
		posted(MAIN, "555", DAY, NOW);
		assertEquals("w123456\t111\t" + DAY + "\nw777\t555\t" + DAY, saved);
		StatusBoard back = client(ONLINE);
		back.show("b", NOW);
		assertEquals("111", back.next(NOW, false, DAY, true).messageId);
		assertEquals("the tag alone forgets one", "w777\t555\t" + DAY, StatusBoard.merge(saved, "w123456"));
	}

	@Test
	public void aLineSavedUnderTheOldUrlHashIsMovedToTheWebhookId()
	{
		saved = Integer.toHexString(ONLINE.hashCode()) + "\t111\t" + DAY;
		StatusBoard board = client(ONLINE);
		board.show("a", NOW);
		assertEquals("its board is kept", "111", board.next(NOW, false, DAY, true).messageId);
		save(board);
		assertEquals("and saved again by id, the old line gone", "w123456\t111\t" + DAY, saved);
	}

	@Test
	public void aMalformedSavedLineIsIgnored()
	{
		for (String bad : new String[]{"w123456\tabc", "w123456", "w123456\t1\t2\t3\t4", "w123456\t1\t2\tx,1"})
		{
			saved = bad;
			StatusBoard board = client(ONLINE);
			board.show("a", NOW);
			assertTrue(bad, board.next(NOW, false, DAY, true).create());
		}
	}
}
