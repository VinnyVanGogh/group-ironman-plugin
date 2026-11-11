package com.vinnyvangogh.groupironman;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static com.vinnyvangogh.groupironman.FakeItems.*;

/** The "since you last played" log: members' drops and new collection log slots. */
public class DropLogTest
{
	private static final long HOUR = TimeUnit.HOURS.toMillis(1);
	private static final long NOW = 1_762_000_000_000L;

	private static Map<Integer, Integer> items(int... pairs)
	{
		Map<Integer, Integer> out = new TreeMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			out.put(pairs[i], pairs[i + 1]);
		}
		return out;
	}

	@Test
	public void entriesAreNewestFirst()
	{
		DropLog log = new DropLog();
		log.add(GroupEvent.drop(NOW - 3 * HOUR, "Alex", "Vorkath", items(WHIP, 1)));
		log.add(GroupEvent.clog(NOW - HOUR, "Sam", "Draconic visage"));
		log.add(GroupEvent.drop(NOW - 2 * HOUR, "Sam", "Shark", items(SHARK, 3)));
		List<GroupEvent> entries = log.entries();
		assertEquals(3, entries.size());
		assertEquals(NOW - HOUR, entries.get(0).at);
		assertEquals(NOW - 3 * HOUR, entries.get(2).at);
	}

	@Test
	public void aResentEventIsNotLoggedTwice()
	{
		DropLog log = new DropLog();
		assertTrue(log.add(GroupEvent.drop(NOW, "Alex", "Vorkath", items(WHIP, 1))));
		// The same drop arriving again when a member rejoins and everyone resends.
		assertFalse(log.add(GroupEvent.drop(NOW, "alex", "Vorkath", items(WHIP, 1))));
		assertTrue(log.add(GroupEvent.clog(NOW, "Alex", "Abyssal whip")));
		assertFalse(log.add(GroupEvent.clog(NOW, "Alex", "abyssal whip")));
		assertEquals(2, log.entries().size());
	}

	@Test
	public void twoDifferentDropsInTheSameMillisecondAreBothKept()
	{
		DropLog log = new DropLog();
		assertTrue(log.add(GroupEvent.drop(NOW, "Alex", "Goblin", items(SHARK, 1))));
		assertTrue(log.add(GroupEvent.drop(NOW, "Alex", "Goblin", items(COAL, 1))));
		assertEquals(2, log.entries().size());
	}

	@Test
	public void emptyOrNamelessEventsAreDropped()
	{
		DropLog log = new DropLog();
		assertFalse(log.add(GroupEvent.drop(NOW, "Alex", "Vorkath", items())));
		assertFalse(log.add(GroupEvent.clog(NOW, "Alex", "   ")));
		assertFalse(log.add(GroupEvent.clog(NOW, "", "Abyssal whip")));
		assertTrue(log.entries().isEmpty());
	}

	@Test
	public void theLogIsCapped()
	{
		DropLog log = new DropLog();
		for (int i = 0; i < DropLog.MAX_ENTRIES + 25; i++)
		{
			log.add(GroupEvent.clog(NOW + i, "Alex", "Item " + i));
		}
		List<GroupEvent> entries = log.entries();
		assertEquals(DropLog.MAX_ENTRIES, entries.size());
		assertEquals("the oldest go first", NOW + DropLog.MAX_ENTRIES + 24, entries.get(0).at);
	}

	@Test
	public void recentByIsOnlyThatMembersEventsInsideTheResendWindow()
	{
		DropLog log = new DropLog();
		log.add(GroupEvent.drop(NOW - DropLog.RESEND_WINDOW_MS - 1, "Me", "Old", items(SHARK, 1)));
		log.add(GroupEvent.drop(NOW - HOUR, "Me", "Vorkath", items(WHIP, 1)));
		log.add(GroupEvent.drop(NOW - HOUR, "Alex", "Zulrah", items(WHIP, 1)));
		List<GroupEvent> mine = log.recentBy("Me", NOW);
		assertEquals(1, mine.size());
		assertEquals("Vorkath", mine.get(0).text);
	}

	@Test
	public void recentByIsCapped()
	{
		DropLog log = new DropLog();
		for (int i = 0; i < DropLog.MAX_RESEND + 10; i++)
		{
			log.add(GroupEvent.clog(NOW - i, "Me", "Item " + i));
		}
		assertEquals(DropLog.MAX_RESEND, log.recentBy("Me", NOW).size());
	}

	@Test
	public void longTextIsCutAndDropsAreCappedAtCreation()
	{
		StringBuilder longName = new StringBuilder();
		Map<Integer, Integer> many = new TreeMap<>();
		for (int i = 1; i <= 100; i++)
		{
			longName.append('x');
			many.put(i, 1);
		}
		GroupEvent e = GroupEvent.drop(NOW, "Alex", longName.toString(), many);
		assertEquals(GroupEvent.MAX_TEXT, e.text.length());
		assertEquals(GroupEvent.MAX_DROP_ITEMS, e.items.size());
	}
}
