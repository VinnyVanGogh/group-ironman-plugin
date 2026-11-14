package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static com.vinnyvangogh.groupironman.FakeItems.*;

/**
 * The group storage log as the Board tested it: open, look, deposit, Save.
 * Only what the game saved is logged (Board 2026-11-14: 1,000 logs deposited
 * and walked away from without Save were posted, though the game threw them away).
 */
public class StorageSessionTest
{
	private static final FakeItems ITEMS = new FakeItems();
	private static final long NOW = 1_000_000_000L;
	/** Logs: the Board's 1,000 that were never saved. Unknown to FakeItems, which is fine for counts. */
	private static final int LOGS = 1511;

	private static Map<Integer, Integer> items(int... pairs)
	{
		Map<Integer, Integer> out = new TreeMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			out.put(pairs[i], pairs[i + 1]);
		}
		return out;
	}

	/** What GroupService does with each change the session hands back: the roster and the would-be posts. */
	private static final class Sink
	{
		final GroupRoster roster = new GroupRoster();
		final List<StorageChange> posted = new ArrayList<>();

		void commit(StorageChange change)
		{
			if (change == null)
			{
				return;
			}
			roster.putStorage(change);
			if (!EventDetect.storage(change, ITEMS).isEmpty())
			{
				posted.add(change);
			}
		}

		List<PanelModel.LogRow> log(long now)
		{
			return PanelModel.storage(roster, ITEMS, "me", null, now).log;
		}

		Map<Integer, Integer> snapshot()
		{
			return roster.storageItems();
		}
	}

	// --- Failure cases first -------------------------------------------------

	@Test
	public void depositThenCloseWithoutSavePostsNothingAndKeepsTheSnapshot()
	{
		Sink sink = new Sink();
		StorageSession s = new StorageSession();
		s.open();
		sink.commit(s.contents(NOW, "Me", items(SHARK, 40)));
		sink.commit(s.contents(NOW, "Me", items(SHARK, 40, LOGS, 1_000)));
		s.close(NOW + 1_000);

		assertTrue("nothing posted", sink.posted.isEmpty());
		assertTrue("nothing logged", sink.log(NOW + 1_000).isEmpty());
		assertEquals("snapshot is what the game kept", items(SHARK, 40), sink.snapshot());
	}

	@Test
	public void depositSaveDepositMoreThenCloseWithoutSavePostsOnlyTheSavedPart()
	{
		Sink sink = new Sink();
		StorageSession s = new StorageSession();
		s.open();
		sink.commit(s.contents(NOW, "Me", items(SHARK, 40)));
		sink.commit(s.contents(NOW, "Me", items(SHARK, 40, TROUT, 5)));
		sink.commit(s.save(NOW + 1, "Me"));
		sink.commit(s.contents(NOW + 2, "Me", items(SHARK, 40, TROUT, 5, COAL, 9)));
		s.close(NOW + 3);

		assertEquals(1, sink.posted.size());
		assertEquals(items(TROUT, 5), sink.posted.get(0).deposited());
		assertEquals(items(SHARK, 40, TROUT, 5), sink.snapshot());
	}

	@Test
	public void containerThatDisagreesOnTheNextOpenCorrectsTheSnapshotWithoutAPost()
	{
		Sink sink = new Sink();
		StorageSession s = new StorageSession();
		s.open();
		sink.commit(s.contents(NOW, "Me", items(SHARK, 40)));
		sink.commit(s.contents(NOW, "Me", items(SHARK, 40, TROUT, 5)));
		sink.commit(s.save(NOW + 1, "Me"));
		s.close(NOW + 2);
		assertEquals(1, sink.posted.size());

		// The save did not stick: the server's next contents are the old ones.
		s.open();
		StorageChange correction = s.contents(NOW + 60_000, "Me", items(SHARK, 40));
		assertNotNull(correction);
		assertTrue("a correction is a snapshot, never a change", correction.isEmpty());
		sink.commit(correction);
		assertEquals("no second post", 1, sink.posted.size());
		assertEquals(items(SHARK, 40), sink.snapshot());
	}

	@Test
	public void nextOpenMatchingWhatWasCommittedRecordsNothing()
	{
		StorageSession s = new StorageSession();
		s.open();
		s.contents(NOW, "Me", items(SHARK, 40));
		s.contents(NOW, "Me", items(SHARK, 40, LOGS, 1_000));
		s.close(NOW + 1);
		s.open();
		assertNull("discarded deposit, contents as committed", s.contents(NOW + 5_000, "Me", items(SHARK, 40)));
	}

	@Test
	public void aSaveLongAfterTheCloseIsNotThisVisits()
	{
		StorageSession s = new StorageSession();
		s.open();
		s.contents(NOW, "Me", items(SHARK, 40));
		s.contents(NOW, "Me", items(SHARK, 40, LOGS, 1_000));
		s.close(NOW);
		assertNull(s.save(NOW + StorageSession.LATE_SAVE_MS + 1, "Me"));
	}

	@Test
	public void nothingIsLoggedBeforeTheGameSendsTheContentsOrWhileClosed()
	{
		StorageSession s = new StorageSession();
		assertNull("never opened: ignored", s.contents(NOW, "Me", items(SHARK, 1)));
		assertNull("closed: ignored", s.save(NOW, "Me"));
		s.open();
		assertNull("open, nothing arrived yet", s.save(NOW, "Me"));
	}

	// --- Saves that do happen -------------------------------------------------

	@Test
	public void depositSaveCloseLogsExactlyOneLine()
	{
		Sink sink = new Sink();
		StorageSession s = new StorageSession();
		s.open();
		sink.commit(s.contents(NOW, "Me", items(SHARK, 40)));
		sink.commit(s.contents(NOW, "Me", items(SHARK, 40, TROUT, 5)));
		sink.commit(s.save(NOW, "Me"));
		// The "Saving..." the same Save shows: nothing new moved.
		sink.commit(s.save(NOW + 600, "Me"));
		s.close(NOW + 1_000);

		assertEquals(1, sink.posted.size());
		List<PanelModel.LogRow> log = sink.log(NOW + 1_000);
		assertEquals(1, log.size());
		assertEquals("You deposited 5 Trout", log.get(0).text);
	}

	@Test
	public void aClosePathTheGameSavesOnPostsOnce()
	{
		Sink sink = new Sink();
		StorageSession s = new StorageSession();
		s.open();
		sink.commit(s.contents(NOW, "Me", items(SHARK, 40)));
		sink.commit(s.contents(NOW, "Me", items(SHARK, 40, TROUT, 5)));
		s.close(NOW + 600);
		// "Saving..." shows just after the window closed.
		sink.commit(s.save(NOW + 1_200, "Me"));
		sink.commit(s.save(NOW + 1_800, "Me"));

		assertEquals(1, sink.posted.size());
		assertEquals(items(TROUT, 5), sink.posted.get(0).deposited());
		assertEquals(items(SHARK, 40, TROUT, 5), sink.snapshot());

		// And the next open agrees, so there is nothing to correct.
		s.open();
		assertNull(s.contents(NOW + 60_000, "Me", items(SHARK, 40, TROUT, 5)));
	}

	@Test
	public void openAndCloseWithNoChangeShowsTheSnapshotAndNoChangeLine()
	{
		Sink sink = new Sink();
		StorageSession s = new StorageSession();
		s.open();
		StorageChange snapshot = s.contents(NOW, "Me", items(SHARK, 40, COAL, 10));
		assertNotNull("the first contents of a session are recorded", snapshot);
		assertTrue(snapshot.isEmpty());
		sink.commit(snapshot);
		s.close(NOW);

		PanelModel.StorageView view = PanelModel.storage(sink.roster, ITEMS, "Me", null, NOW);
		assertEquals("Last seen just now, by you", view.snapshot);
		assertEquals(2, view.items.size());
		assertEquals("most valuable stack first", SHARK, view.items.get(0).id);
		assertTrue(view.log.isEmpty());
	}

	@Test
	public void eachSaveLogsWhatMovedSinceTheLastOne()
	{
		StorageSession s = new StorageSession();
		s.open();
		s.contents(NOW, "Me", items(SHARK, 40));
		s.contents(NOW, "Me", items(SHARK, 30));
		assertEquals(items(SHARK, 10), s.save(NOW, "Me").took());
		s.contents(NOW, "Me", items(SHARK, 30, COAL, 2));
		StorageChange second = s.save(NOW + 1, "Me");
		assertTrue(second.took().isEmpty());
		assertEquals(items(COAL, 2), second.deposited());
	}
}
