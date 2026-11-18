package com.vinnyvangogh.groupironman;

import java.util.List;
import java.util.Map;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static com.vinnyvangogh.groupironman.EventFixtures.items;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The detectors and the game's lines, field by field. */
public class EventDetectTest
{
	private final FakeItems items = new FakeItems();

	@Test
	public void aStorageVisitIsItsDepositWithdrawalSummaryAndLast()
	{
		StorageChange change = new StorageChange(NOW, "Alex", items(FakeItems.WHIP, 1, FakeItems.SHARK, 5),
			items(FakeItems.COAL, 100), items(FakeItems.SHARK, 10, FakeItems.COAL, 100));
		List<GroupEvent> out = EventDetect.storage(change, items);
		assertEquals(4, out.size());

		GroupEvent deposit = out.get(0);
		assertEquals(GroupEvent.Type.STORAGE_DEPOSIT, deposit.type);
		assertEquals(items(FakeItems.COAL, 100), deposit.items);
		assertEquals(15_000, deposit.number);
		assertEquals("Alex", deposit.member);

		GroupEvent withdrawal = out.get(1);
		assertEquals(GroupEvent.Type.STORAGE_WITHDRAWAL, withdrawal.type);
		assertEquals(1_504_000, withdrawal.number);

		GroupEvent summary = out.get(2);
		assertEquals(GroupEvent.Type.STORAGE_SUMMARY, summary.type);
		assertEquals("group storage: took 5 x Shark, 1 x Abyssal whip; deposited 100 x Coal",
			EventText.describe(summary, items));

		GroupEvent last = out.get(3);
		assertEquals(GroupEvent.Type.STORAGE_LAST, last.type);
		assertEquals("only the whip ran out", items(FakeItems.WHIP, 1), last.items);
	}

	@Test
	public void anEmptyVisitIsNoEvent()
	{
		assertTrue(EventDetect.storage(new StorageChange(NOW, "Alex", items(), items(), items(FakeItems.SHARK, 1)),
			items).isEmpty());
		assertTrue(EventDetect.storage(null, items).isEmpty());
	}

	@Test
	public void aMinimumNamesTheItemAndBothCounts()
	{
		GroupEvent e = EventDetect.minimum(NOW, "Alex", new LowStock.Alert("Shark", 40, 100));
		assertEquals(GroupEvent.Type.STORAGE_MINIMUM, e.type);
		assertEquals("group storage is low on Shark: 40 left, minimum 100", EventText.describe(e, items));
	}

	@Test
	public void uniquesAreUntradeablesLessTheIgnored()
	{
		Map<Integer, Integer> drop = items(FakeItems.WHIP, 1, FakeItems.SHARK, 2, FakeItems.COAL, 1);
		// Whip tradeable; shark and coal stand in for untradeables, coal ignored by name.
		Map<Integer, Integer> out = EventDetect.uniques(drop, id -> id == FakeItems.WHIP, items, "coal, Clue scroll");
		assertEquals(items(FakeItems.SHARK, 2), out);
		assertTrue(EventDetect.uniques(drop, id -> true, items, "").isEmpty());
	}

	@Test
	public void theDefaultIgnoreListLeavesRealUniquesIn()
	{
		assertTrue(EventDetect.DEFAULT_UNTRADEABLE_IGNORE.contains("Clue scroll"));
		assertFalse(EventDetect.DEFAULT_UNTRADEABLE_IGNORE.toLowerCase().contains("visage"));
	}

	@Test
	public void petMessagesAreRead()
	{
		assertTrue(EventText.isPet("You have a funny feeling like you're being followed."));
		assertTrue(EventText.isPet("<col=ef1020>You feel something weird sneaking into your backpack.</col>"));
		assertTrue(EventText.isPet("You have a funny feeling like you would have been followed..."));
		assertFalse(EventText.isPet("You feel a funny feeling."));
		assertFalse(EventText.isPet(null));
	}

	@Test
	public void killAndChestCountsAreRead()
	{
		EventText.Count kc = EventText.killCount("Your Vorkath kill count is: <col=ff0000>1,204</col>.");
		assertEquals("Vorkath", kc.name);
		assertEquals(1204, kc.count);
		EventText.Count chest = EventText.killCount("Your Barrows chest count is: <col=ff0000>41</col>.");
		assertEquals("Barrows", chest.name);
		assertEquals(41, chest.count);
		assertNull(EventText.killCount("Your reward is: 1 x Shark."));
	}

	@Test
	public void questNamesComeOffTheScrollTitle()
	{
		assertEquals("Cook's Assistant", EventText.questName("You have completed Cook's Assistant!"));
		assertEquals("Restless Ghost", EventText.questName("You have completed the Restless Ghost Quest!"));
		assertEquals("Some Odd Title", EventText.questName("Some Odd Title"));
	}

	@Test
	public void aDeathSaysWhereByWhomWhichWorldAndALifeLost()
	{
		GroupEvent e = GroupEvent.of(GroupEvent.Type.DEATH, NOW, "Alex").text("Vorkath").location("Vorkath")
			.world(302).number(GroupEvent.LIFE_LOST).build();
		assertEquals("died at Vorkath, killed by Vorkath (world 302). The group lost a life.",
			EventText.describe(e, items));
		assertTrue(e.isBig());
		assertFalse(GroupEvent.of(GroupEvent.Type.DEATH, NOW, "Alex").build().isBig());
	}

	@Test
	public void barrowsCountsSetPiecesAcrossChests()
	{
		BarrowsProgress p = BarrowsProgress.parse("");
		ItemLookup names = new ItemLookup()
		{
			@Override
			public String name(int id)
			{
				return id == 1 ? "Dharok's helm" : id == 2 ? "Dharok's greataxe" : id == 3 ? "Karil's coif" : "Bolt rack";
			}

			@Override
			public long price(int id)
			{
				return 0;
			}
		};
		assertEquals("Dharok's 1/4", p.add(items(1, 1, 4, 100), names));
		// Saved and read back, as between sessions; the helm again does not count twice.
		BarrowsProgress later = BarrowsProgress.parse(p.save());
		assertEquals("Dharok's 2/4, Karil's 1/4", later.add(items(1, 1, 2, 1, 3, 1), names));
		assertEquals("no piece, no progress line", "", later.add(items(4, 100), names));
	}
}
