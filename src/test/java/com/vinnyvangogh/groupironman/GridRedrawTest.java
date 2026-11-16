package com.vinnyvangogh.groupironman;

import com.vinnyvangogh.testkit.ui.RuneLiteLook;
import com.vinnyvangogh.testkit.ui.SwingDriver;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.JLabel;
import net.runelite.client.game.SpriteManager;
import org.junit.Before;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.GimPanelFixture.HOUR;
import static com.vinnyvangogh.groupironman.GimPanelFixture.MOSS;
import static com.vinnyvangogh.groupironman.GimPanelFixture.items;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The Banks and Storage tabs under a stream of syncs (Board, 2026-11-15: the
 * Banks tab flickered and ate game clicks at Revenants). A sync that changes
 * nothing draws nothing; one changed item repaints one cell; a hidden tab does
 * no work; a burst of syncs draws once.
 */
public class GridRedrawTest
{
	/** A scheduler that holds tasks until the test runs them, recording each delay. */
	private static final class Held implements RedrawCoalescer.Scheduler
	{
		final Deque<Runnable> tasks = new ArrayDeque<>();
		final List<Long> delays = new ArrayList<>();

		@Override
		public void schedule(Runnable task, long delayMs)
		{
			tasks.add(task);
			delays.add(delayMs);
		}

		void runAll()
		{
			while (!tasks.isEmpty())
			{
				tasks.poll().run();
			}
		}
	}

	@Before
	public void lookLikeRuneLite()
	{
		SwingDriver.onEdt(RuneLiteLook::install);
	}

	// ---- RedrawCoalescer ----

	@Test
	public void aBurstOfTwentyRequestsRunsOnce()
	{
		Held held = new Held();
		AtomicLong clock = new AtomicLong(10_000);
		AtomicInteger runs = new AtomicInteger();
		RedrawCoalescer c = new RedrawCoalescer(1000, clock::get, held, runs::incrementAndGet);
		for (int i = 0; i < 20; i++)
		{
			c.request();
		}
		assertEquals(1, held.tasks.size());
		assertEquals(Long.valueOf(0), held.delays.get(0));
		held.runAll();
		assertEquals(1, runs.get());

		// The next request within the gap waits out the rest of it.
		clock.addAndGet(300);
		c.request();
		c.request();
		assertEquals(1, held.tasks.size());
		assertEquals(Long.valueOf(700), held.delays.get(1));
		held.runAll();
		assertEquals(2, runs.get());
	}

	// ---- ItemGridView ----

	private static List<PanelModel.ItemCell> cells(int n)
	{
		List<PanelModel.ItemCell> out = new ArrayList<>();
		for (int i = 1; i <= n; i++)
		{
			out.add(new PanelModel.ItemCell(i, "item " + i, i, i * 10L));
		}
		return out;
	}

	@Test
	public void gridUpdateTouchesOnlyChangedCellsAndKeepsTheLabels()
	{
		SwingDriver.onEdt(() ->
		{
			AtomicInteger painted = new AtomicInteger();
			ItemGridView grid = new ItemGridView((cell, item) -> painted.incrementAndGet(), 5, 200);
			assertEquals(30, grid.update(cells(30)));
			List<JLabel> before = new ArrayList<>(grid.cells());

			assertEquals("unchanged data repaints nothing", 0, grid.update(cells(30)));

			List<PanelModel.ItemCell> next = cells(30);
			next.set(7, new PanelModel.ItemCell(8, "item 8", 9, 90));
			assertEquals("one changed item repaints one cell", 1, grid.update(next));
			assertEquals(31, painted.get());
			for (int i = 0; i < before.size(); i++)
			{
				assertSame("cells are kept, not rebuilt", before.get(i), grid.cells().get(i));
			}

			assertEquals("dropping two items removes two cells", 2, grid.update(cells(28)));
			assertEquals(28, grid.cells().size());
		});
	}

	// ---- The panel ----

	private static GimPanelFixture.Scene oneMoreLobster(GimPanelFixture.Scene s)
	{
		s.roster.putBank(MOSS, new BankSnapshot(s.now - HOUR, items(FakeItems.WHIP, 1, FakeItems.LOBSTER, 81)));
		return s;
	}

	@Test
	public void anUnchangedSyncDrawsNothingAndOneChangedItemRepaintsOneCell()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture.Scene s = GimPanelFixture.bankScene();
			GimPanelFixture f = new GimPanelFixture().show(s).tab("Banks");
			int draws = f.panel.gridDraws;
			assertEquals(1, f.panel.bankRebuilds());

			clearInvocations(f.items);
			f.panel.render(s.view());
			assertEquals("same data: nothing handed to the Swing thread", draws, f.panel.gridDraws);
			verify(f.items, never()).getImage(anyInt(), anyInt(), anyBoolean());

			f.panel.render(oneMoreLobster(s).view());
			assertEquals(draws + 1, f.panel.gridDraws);
			assertEquals("an item change updates in place", 1, f.panel.bankRebuilds());
			verify(f.items, times(1)).getImage(anyInt(), anyInt(), anyBoolean());
		});
	}

	@Test
	public void aHiddenBanksTabDoesNoWork()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture.Scene s = GimPanelFixture.bankScene();
			GimPanelFixture f = new GimPanelFixture().show(s);
			for (int i = 0; i < 5; i++)
			{
				f.panel.render(oneMoreLobster(s).view());
			}
			assertEquals(0, f.panel.gridModels);
			assertEquals(0, f.panel.gridDraws);
			f.tab("Banks");
			assertEquals("built once when shown", 1, f.panel.gridDraws);
			f.ui.assertShows(MOSS + "'s bank");
		});
	}

	@Test
	public void aBurstOfTwentySyncsDrawsTheBanksTabOnce()
	{
		SwingDriver.onEdt(() ->
		{
			Held held = new Held();
			GimPanelFixture.Scene s = GimPanelFixture.bankScene();
			GimPanelFixture f = new GimPanelFixture(mock(SpriteManager.class), held, Runnable::run).show(s)
				.tab("Banks");
			held.runAll();
			int draws = f.panel.gridDraws;
			for (int i = 0; i < 20; i++)
			{
				s.roster.putBank(MOSS, new BankSnapshot(s.now - HOUR, items(FakeItems.WHIP, 1, FakeItems.LOBSTER,
					100 + i)));
				f.panel.render(s.view());
			}
			assertEquals("twenty syncs, one build waiting", 1, held.tasks.size());
			held.runAll();
			assertEquals(draws + 1, f.panel.gridDraws);
			f.ui.layout(GimPanelFixture.PANEL_WIDTH);
			f.ui.assertShows(MOSS + "'s bank");
		});
	}

	@Test
	public void storageUpdatesInPlaceAndSkipsUnchangedSyncs()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture.Scene s = GimPanelFixture.storageScene();
			GimPanelFixture f = new GimPanelFixture().show(s).tab("Storage");
			int draws = f.panel.gridDraws;
			f.panel.render(s.view());
			assertEquals(draws, f.panel.gridDraws);
			f.ui.assertShows("Group storage");
			f.ui.assertShows("Changes");
		});
	}

	/**
	 * Five members with full banks: what a sync costs the Swing thread before (the whole tab rebuilt, model
	 * included) and after (one changed item drawn in place, model built elsewhere). Printed for the PR.
	 */
	@Test
	public void fiveFullBanksMeasureTheSwingThreadCost()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture.Scene s = GimPanelFixture.bankScene();
			s.members = Arrays.asList(GimPanelFixture.SELF, MOSS, GimPanelFixture.WREN, GimPanelFixture.TIN, "Fifth Owl");
			for (String m : s.members)
			{
				s.roster.putBank(m, new BankSnapshot(s.now - HOUR, fullBank(0)));
			}
			Held held = new Held();
			Deque<Runnable> edt = new ArrayDeque<>();
			GimPanelFixture f = new GimPanelFixture(mock(SpriteManager.class), held, edt::add).show(s);

			// Before: the old path rebuilt every card and cell, model included, on the Swing thread.
			long fullStart = System.nanoTime();
			f.tab("Banks");
			held.runAll();
			edt.poll().run();
			long full = System.nanoTime() - fullStart;

			// Warm the in-place path once, then time one more changed stack.
			long inPlace = 0;
			for (int round = 1; round <= 3; round++)
			{
				s.roster.putBank(MOSS, new BankSnapshot(s.now - HOUR, fullBank(round)));
				f.panel.render(s.view());
				held.runAll();
				long start = System.nanoTime();
				edt.poll().run();
				inPlace = System.nanoTime() - start;
			}
			assertEquals(1, f.panel.bankRebuilds());
			System.out.printf("Banks tab, 5 full banks: full rebuild %.1f ms, in-place sync %.2f ms on the Swing"
				+ " thread%n", full / 1e6, inPlace / 1e6);
			assertTrue("in place costs less than a rebuild", inPlace < full);
		});
	}

	/** 800 kinds of item, the first one's stack grown by {@code bump}. */
	private static Map<Integer, Integer> fullBank(int bump)
	{
		Map<Integer, Integer> bank = new LinkedHashMap<>();
		for (int id = 1; id <= 800; id++)
		{
			bank.put(id, id == 1 ? 1000 + bump : id);
		}
		return bank;
	}
}
