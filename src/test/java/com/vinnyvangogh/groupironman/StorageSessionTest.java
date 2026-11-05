package com.vinnyvangogh.groupironman;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static com.vinnyvangogh.groupironman.FakeItems.*;

/** The group storage log as the Board tested it: open, look, deposit, Save. */
public class StorageSessionTest
{
	private static final FakeItems ITEMS = new FakeItems();
	private static final long NOW = 1_000_000_000L;

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
	public void openAndCloseWithNoChangeShowsTheSnapshotAndNoChangeLine()
	{
		GroupRoster roster = new GroupRoster();
		StorageSession s = new StorageSession();
		s.open();
		s.contents(items(SHARK, 40, COAL, 10));
		StorageChange change = s.close(NOW, "Me");
		assertNotNull(change);
		assertTrue(change.isEmpty());
		roster.putStorage(change);

		PanelModel.StorageView view = PanelModel.storage(roster, ITEMS, "Me", null, NOW);
		assertEquals("Last seen just now, by you", view.snapshot);
		assertEquals(2, view.items.size());
		assertEquals("most valuable stack first", SHARK, view.items.get(0).id);
		assertTrue(view.log.isEmpty());
	}

	@Test
	public void depositFiveTroutAndSaveLogsOneLine()
	{
		GroupRoster roster = new GroupRoster();
		StorageSession s = new StorageSession();
		s.open();
		s.contents(items(SHARK, 40));
		s.contents(items(SHARK, 40, TROUT, 5));
		roster.putStorage(s.save(NOW, "Me"));
		// The close after a Save has nothing new to log.
		StorageChange close = s.close(NOW + 1_000, "Me");
		assertTrue(close.isEmpty());
		roster.putStorage(close);

		List<PanelModel.LogRow> log = PanelModel.storage(roster, ITEMS, "me", null, NOW + 1_000).log;
		assertEquals(1, log.size());
		assertEquals("You deposited 5 Trout", log.get(0).text);
	}

	@Test
	public void eachSaveLogsWhatMovedSinceTheLastOne()
	{
		StorageSession s = new StorageSession();
		s.open();
		s.contents(items(SHARK, 40));
		s.contents(items(SHARK, 30));
		assertEquals(items(SHARK, 10), s.save(NOW, "Me").took());
		s.contents(items(SHARK, 30, COAL, 2));
		StorageChange second = s.save(NOW + 1, "Me");
		assertTrue(second.took().isEmpty());
		assertEquals(items(COAL, 2), second.deposited());
	}

	@Test
	public void nothingIsLoggedBeforeTheGameSendsTheContentsOrWhileClosed()
	{
		StorageSession s = new StorageSession();
		s.contents(items(SHARK, 1));
		assertNull("closed: ignored", s.save(NOW, "Me"));
		s.open();
		assertNull("open, nothing arrived yet", s.close(NOW, "Me"));
	}
}
