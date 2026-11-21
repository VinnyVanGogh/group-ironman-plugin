package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The live board while the leader is offline: one online member's client keeps it, and edits the same message. */
public class BoardHandoffTest
{
	private static final Gson GSON = new Gson();
	private static final String URL = "https://discord.com/api/webhooks/123456/secretTOKEN_value";
	private static final long LONG_AGO = NOW - Presence.ONLINE_MS - 1;

	private static StatusBoard.Self self(String name, boolean online)
	{
		return new StatusBoard.Self(name, online, 301, 0);
	}

	private static Map<String, Long> heard(Object... pairs)
	{
		Map<String, Long> m = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			m.put((String) pairs[i], (Long) pairs[i + 1]);
		}
		return m;
	}

	@Test
	public void offlineLeaderLeavesTheBoardToTheFirstOnlineMember()
	{
		Map<String, Long> h = heard("Lead", LONG_AGO, "Amy", NOW, "Bob", NOW);
		assertTrue(StatusBoard.standsIn(self("Amy", true), "Lead", h, GamePresence.UNKNOWN, NOW));
		assertFalse("Bob sorts after Amy", StatusBoard.standsIn(self("Bob", true), "Lead", h, GamePresence.UNKNOWN, NOW));
	}

	@Test
	public void anOnlineLeaderKeepsItAndNobodyStandsIn()
	{
		Map<String, Long> h = heard("Lead", NOW, "Amy", NOW);
		assertFalse(StatusBoard.standsIn(self("Amy", true), "Lead", h, GamePresence.UNKNOWN, NOW));
	}

	@Test
	public void theLeaderNeverStandsInForThemselvesAndAnOfflineMemberNeverDoes()
	{
		Map<String, Long> h = heard("Lead", NOW, "Amy", NOW);
		assertFalse(StatusBoard.standsIn(self("Lead", true), "Lead", h, GamePresence.UNKNOWN, NOW));
		assertFalse(StatusBoard.standsIn(self("Amy", false), "Lead", heard("Lead", LONG_AGO), GamePresence.UNKNOWN, NOW));
		assertFalse("no leader known", StatusBoard.standsIn(self("Amy", true), "", heard(), GamePresence.UNKNOWN, NOW));
		assertFalse(StatusBoard.standsIn(null, "Lead", heard(), GamePresence.UNKNOWN, NOW));
	}

	@Test
	public void aMemberHeardLongAgoDoesNotBlockTheNextOne()
	{
		Map<String, Long> h = heard("Lead", LONG_AGO, "Amy", LONG_AGO, "Bob", NOW);
		assertTrue(StatusBoard.standsIn(self("Bob", true), "Lead", h, GamePresence.UNKNOWN, NOW));
	}

	@Test
	public void beaconNamesTheBoardAndOnlyTheSameWebhookMayTakeIt()
	{
		BoardBeacon b = new BoardBeacon();
		String json = BoardBeacon.encode(GSON, NOW, "w123456", "9876543210");
		assertTrue(BoardBeacon.valid(GSON, json));
		b.heard(GSON, json, NOW);
		assertEquals("9876543210", b.boardOn("w123456", NOW));
		assertNull("another webhook's board is not ours", b.boardOn("w999", NOW));
		assertNull("stale", b.boardOn("w123456", NOW + BoardBeacon.STALE_MS));
	}

	@Test
	public void aBeaconWithJunkIdsNamesNoBoard()
	{
		BoardBeacon b = new BoardBeacon();
		b.heard(GSON, "{\"v\":1,\"k\":\"board\",\"h\":\"w1\",\"m\":\"12ab\"}", NOW);
		assertNull(b.boardOn("w1", NOW));
		assertFalse("junk is never encoded", BoardBeacon.encode(GSON, NOW, "w1", "x").contains("\"m\""));
		assertFalse(BoardBeacon.encode(GSON, NOW).contains("\"m\""));
	}

	@Test
	public void adoptingEditsTheNamedBoardAndDeletesTheOldOne()
	{
		StatusBoard board = new StatusBoard();
		board.target(URL, () -> null);
		board.show("a", NOW);
		StatusBoard.Send post = board.next(NOW, false);
		board.done(post, 200, "111", -1, NOW);
		board.adopt("222", 5);
		assertEquals("222", board.boardId());
		board.show("b", NOW + 1);
		StatusBoard.Send edit = board.next(NOW + StatusBoard.MIN_GAP_MS, false);
		assertFalse("edits, never posts a second", edit == null || edit.create() || edit.delete);
		assertEquals("222", edit.messageId);
	}
}
