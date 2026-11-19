package com.vinnyvangogh.groupironman;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntFunction;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LampBookTest
{
	private static final long NOW = EventFixtures.NOW;
	private static final int LAMP = 2528;
	private static final int TOME = 33333;
	private static final int ROPE = 954;
	private static final IntFunction<String> NAMES = id -> id == LAMP ? "Antique lamp"
		: id == TOME ? "Tome of experience" : "Rope";

	private final LampBook book = new LampBook();

	private static Map<Integer, Integer> inv(int... idAndCount)
	{
		Map<Integer, Integer> m = new TreeMap<>();
		for (int i = 0; i < idAndCount.length; i += 2)
		{
			m.put(idAndCount[i], idAndCount[i + 1]);
		}
		return m;
	}

	@Test
	public void namesOfLampsAndBooks()
	{
		assertTrue(LampBook.isLampOrBook("Antique lamp"));
		assertTrue(LampBook.isLampOrBook("<col=ff9040>Lamp</col>"));
		assertTrue(LampBook.isLampOrBook("Book of knowledge"));
		assertTrue(LampBook.isLampOrBook("Book of arcane knowledge"));
		assertTrue(LampBook.isLampOrBook("Tome of experience"));
		assertTrue(LampBook.isLampOrBook("Tome of experience (Smithing)"));
		assertFalse(LampBook.isLampOrBook("Oil lamp"));
		assertFalse(LampBook.isLampOrBook("Lamp post"));
		assertFalse(LampBook.isLampOrBook("Rope"));
		assertFalse(LampBook.isLampOrBook(null));
	}

	@Test
	public void firstInventoryIsOnlyTheBaseline()
	{
		assertTrue(book.inventory(inv(LAMP, 1, ROPE, 3), NAMES, NOW, "Alex", false).isEmpty());
		assertTrue("nothing changed", book.inventory(inv(LAMP, 1, ROPE, 3), NAMES, NOW, "Alex", false).isEmpty());
	}

	@Test
	public void aLampGainedIsReceived()
	{
		book.inventory(inv(ROPE, 3), NAMES, NOW, "Alex", false);
		List<GroupEvent> out = book.inventory(inv(ROPE, 3, LAMP, 1), NAMES, NOW + 1_000, "Alex", false);
		assertEquals(1, out.size());
		GroupEvent e = out.get(0);
		assertEquals(GroupEvent.Type.LAMP_BOOK, e.type);
		assertEquals("Antique lamp", e.text);
		assertEquals(Collections.singletonMap(LAMP, 1), e.items);
		assertEquals(1, e.number);
		assertEquals("Alex", e.member);
		assertTrue(e.isValid());
	}

	@Test
	public void onlyTheGainCounts()
	{
		book.inventory(inv(LAMP, 2), NAMES, NOW, "Alex", false);
		List<GroupEvent> out = book.inventory(inv(LAMP, 5, TOME, 1), NAMES, NOW + 1_000, "Alex", false);
		assertEquals(2, out.size());
		assertEquals(3, out.get(0).number);
		assertEquals("Tome of experience", out.get(1).text);
		assertTrue("using one is a loss, not a receipt", book.inventory(inv(LAMP, 4, TOME, 1), NAMES, NOW + 2_000,
			"Alex", false).isEmpty());
	}

	@Test
	public void movedFromTheBankIsNotReceived()
	{
		book.inventory(inv(ROPE, 1), NAMES, NOW, "Alex", false);
		assertTrue(book.inventory(inv(ROPE, 1, LAMP, 1), NAMES, NOW + 1_000, "Alex", true).isEmpty());
		assertTrue("and it stays moved, not announced later", book.inventory(inv(ROPE, 1, LAMP, 1), NAMES,
			NOW + 2_000, "Alex", false).isEmpty());
	}

	@Test
	public void resetStartsTheBaselineOver()
	{
		book.inventory(inv(ROPE, 1), NAMES, NOW, "Alex", false);
		book.reset();
		assertTrue("the next account's lamps are what it already had", book.inventory(inv(LAMP, 1), NAMES, NOW,
			"Sam", false).isEmpty());
	}

	@Test
	public void useGivesTheSkillAndTheXp()
	{
		book.stat("Smithing", 1_000, NOW - 60_000, "Alex");
		book.clicked("Rub", "<col=ff9040>Antique lamp</col>", NOW);
		GroupEvent e = book.stat("Smithing", 11_000, NOW + 3_000, "Alex");
		assertNotNull(e);
		assertEquals(GroupEvent.Type.LAMP_USED, e.type);
		assertEquals("Smithing", e.text);
		assertEquals(10_000, e.number);
		assertEquals("Antique lamp", e.detail);
		assertNull("one window, one event", book.stat("Smithing", 12_000, NOW + 4_000, "Alex"));
	}

	@Test
	public void booksAreReadNotRubbed()
	{
		book.stat("Prayer", 100, NOW - 60_000, "Alex");
		book.clicked("Read", "Book of knowledge", NOW);
		GroupEvent e = book.stat("Prayer", 1_100, NOW + 2_000, "Alex");
		assertNotNull(e);
		assertEquals("Book of knowledge", e.detail);
	}

	@Test
	public void noClickNoUse()
	{
		book.stat("Smithing", 1_000, NOW - 60_000, "Alex");
		assertNull(book.stat("Smithing", 2_000, NOW, "Alex"));
		book.clicked("Drop", "Antique lamp", NOW);
		assertNull("dropping one is not using it", book.stat("Smithing", 3_000, NOW + 1_000, "Alex"));
		book.clicked("Rub", "Oil lamp", NOW);
		assertNull("another lamp is not an XP lamp", book.stat("Smithing", 4_000, NOW + 1_000, "Alex"));
	}

	@Test
	public void aLateGainIsNotTheLamps()
	{
		book.stat("Mining", 1_000, NOW - 60_000, "Alex");
		book.clicked("Rub", "Antique lamp", NOW);
		assertNull(book.stat("Mining", 1_200, NOW + LampBook.USE_WINDOW_MS + 1, "Alex"));
		assertNull("the window is gone for good", book.stat("Mining", 1_400, NOW + LampBook.USE_WINDOW_MS + 2, "Alex"));
	}

	@Test
	public void aLoginsFirstValueIsOnlyABaseline()
	{
		book.clicked("Rub", "Antique lamp", NOW);
		assertNull(book.stat("Cooking", 50_000, NOW + 1_000, "Alex"));
		assertNotNull("but the next gain, in the window, is", book.stat("Cooking", 60_000, NOW + 2_000, "Alex"));
	}

	@Test
	public void wordsAndBoxLines()
	{
		GroupEvent got = GroupEvent.of(GroupEvent.Type.LAMP_BOOK, NOW, "Alex").text("Antique lamp")
			.items(Collections.singletonMap(LAMP, 1)).number(1).build();
		assertEquals("received Antique lamp", got.describe(new FakeItems()));
		GroupEvent used = GroupEvent.of(GroupEvent.Type.LAMP_USED, NOW, "Alex").text("Smithing").number(10_000)
			.detail("Antique lamp").build();
		assertTrue(used.describe(new FakeItems()).startsWith("used Antique lamp on Smithing, +"));
		BoxText.Line line = BoxText.format(got, new FakeItems(), BoxSize.values()[0], true, BoxTimes.SHORT, NOW);
		assertEquals(LAMP, line.icon.itemId);
		BoxText.Line useLine = BoxText.format(used, new FakeItems(), BoxSize.values()[0], true, BoxTimes.SHORT, NOW);
		assertEquals("Smithing", useLine.icon.skill);
	}

	@Test
	public void routesToItsOwnWebhooks()
	{
		assertEquals(Webhook.DROPS, Webhook.of(GroupEvent.Type.LAMP_BOOK));
		assertEquals(Webhook.PROGRESS, Webhook.of(GroupEvent.Type.LAMP_USED));
		assertEquals("lb", GroupEvent.Type.LAMP_BOOK.code);
		assertEquals(GroupEvent.Type.LAMP_USED, GroupEvent.Type.of("lu"));
	}
}
