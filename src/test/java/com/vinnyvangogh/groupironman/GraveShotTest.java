package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The gravestone warning's picture (Board 2026-11-10: it showed where the player stood at the warning, not where
 * they died). Wired the way GroupEventEngine wires it: the grave's frame is chosen when the warning is emitted,
 * then freed once the grave is no longer out.
 */
public class GraveShotTest
{
	private static final byte[] DEATH_FRAME = {1, 2, 3};
	private static final int WARN = 5;

	private final List<GroupEvent> out = new ArrayList<>();
	private final List<GraveShot.Choice> posted = new ArrayList<>();
	private GraveShot shot;
	private EventWatchers watchers;
	private GraveScreenshot mode = GraveScreenshot.DEATH;
	private boolean screenshots = true;

	@Before
	public void setUp()
	{
		shot = new GraveShot();
		watchers = new EventWatchers(e ->
		{
			out.add(e);
			if (e.type == GroupEvent.Type.GRAVESTONE_EXPIRING)
			{
				posted.add(shot.choose(mode, screenshots));
			}
		}, t -> true);
	}

	/** onActorDeath, then the death post's frame arriving from the encoder. */
	private void dieWithFrame(byte[] frame)
	{
		watchers.died(EventFixtures.items(FakeItems.WHIP, 1), "Lumbridge", 302);
		int grave = shot.died();
		assertTrue(shot.ask(grave));
		shot.keep(grave, frame);
	}

	/** The engine's watch(): a grave tick, then the frame goes once the grave is no longer out. */
	private void tick(int visible)
	{
		watchers.graveTick(visible, WARN, NOW, "Alex");
		if (!watchers.graveOut())
		{
			shot.free();
		}
	}

	private void runToWarning()
	{
		int warnAt = EventWatchers.GRAVE_TICKS - WARN * EventWatchers.TICKS_PER_MINUTE;
		for (int i = 0; i < warnAt; i++)
		{
			tick(1);
		}
		assertEquals(1, posted.size());
	}

	@Test
	public void theWarningUsesTheDeathFrame()
	{
		dieWithFrame(DEATH_FRAME);
		runToWarning();
		GraveShot.Choice c = posted.get(0);
		assertTrue(c.picture);
		assertArrayEquals(DEATH_FRAME, c.png);
	}

	@Test
	public void theFrameIsFreedOnceTheWarningIsPosted()
	{
		dieWithFrame(DEATH_FRAME);
		assertTrue(shot.holding());
		runToWarning();
		assertFalse(shot.holding());
	}

	@Test
	public void theFrameIsFreedWhenTheGraveIsLooted()
	{
		dieWithFrame(DEATH_FRAME);
		tick(1);
		assertTrue(shot.holding());
		tick(0);
		assertFalse(shot.holding());
		for (int i = 0; i < EventWatchers.GRAVE_TICKS; i++)
		{
			tick(0);
		}
		assertTrue("a looted grave never warns", posted.isEmpty());
	}

	@Test
	public void afterARestartTheWarningHasNoPicture()
	{
		// A restart is a new GraveShot with nothing in it; the grave itself was known (a fresh death).
		watchers.died(EventFixtures.items(FakeItems.WHIP, 1), "Lumbridge", 302);
		shot.died();
		runToWarning();
		assertFalse(posted.get(0).picture);
		assertNull(posted.get(0).png);
	}

	@Test
	public void currentViewTakesTheNextFrame()
	{
		mode = GraveScreenshot.CURRENT;
		dieWithFrame(DEATH_FRAME);
		runToWarning();
		assertSame(GraveShot.Choice.NEXT_FRAME, posted.get(0));
		assertNull(posted.get(0).png);
	}

	@Test
	public void noneAttachesNothing()
	{
		mode = GraveScreenshot.NONE;
		dieWithFrame(DEATH_FRAME);
		runToWarning();
		assertFalse(posted.get(0).picture);
	}

	@Test
	public void screenshotsOffAttachesNothingWhateverTheSetting()
	{
		screenshots = false;
		for (GraveScreenshot m : GraveScreenshot.values())
		{
			assertFalse(m.name(), shot.choose(m, false).picture);
		}
		dieWithFrame(DEATH_FRAME);
		runToWarning();
		assertFalse(posted.get(0).picture);
	}

	@Test
	public void aLateFrameFromAnOlderGraveIsDropped()
	{
		int first = shot.died();
		shot.ask(first);
		int second = shot.died();
		shot.keep(first, DEATH_FRAME);
		assertFalse("the old grave's frame is not kept for the new one", shot.holding());
		shot.keep(second, DEATH_FRAME);
		assertTrue(shot.holding());
	}

	@Test
	public void aFrameAfterTheGraveIsGoneIsDropped()
	{
		int grave = shot.died();
		shot.free();
		shot.keep(grave, DEATH_FRAME);
		assertFalse(shot.holding());
		assertFalse("no grave, nothing to ask for", shot.ask(0));
	}

	@Test
	public void theFrameIsAskedForOncePerGrave()
	{
		int grave = shot.died();
		assertTrue(shot.ask(grave));
		assertFalse("the death value's later moment does not replace the death's frame", shot.ask(grave));
		assertTrue(shot.ask(shot.died()));
	}

	@Test
	public void theWarningListsTheTopItemsTheDeathLeft()
	{
		FakeItems items = new FakeItems();
		watchers.died(EventFixtures.items(FakeItems.WHIP, 1, FakeItems.SHARK, 10, FakeItems.COINS, 13_280,
			FakeItems.LOBSTER, 3, FakeItems.TROUT, 1), "Lumbridge", 302);
		shot.died();
		for (int i = 0; i < EventWatchers.DEATH_SETTLE_TICKS; i++)
		{
			watchers.deathTick(EventFixtures.items(), items, NOW, "Alex");
		}
		runToWarning();
		GroupEvent warning = out.get(out.size() - 1);
		assertEquals(GroupEvent.Type.GRAVESTONE_EXPIRING, warning.type);
		assertEquals(5, warning.items.size());
		assertEquals("gravestone at Lumbridge on world 302 has about 5 min left; in it: 1 x Abyssal whip,"
			+ " 13.2K x Coins, 10 x Shark and 2 more (1.52M gp)", EventText.describe(warning, items));
	}

	@Test
	public void aWarningWithoutACountedDeathListsNothing()
	{
		watchers.died(EventFixtures.items(FakeItems.WHIP, 1), "Lumbridge", 302);
		runToWarning();
		assertEquals("gravestone at Lumbridge on world 302 has about 5 min left",
			EventText.describe(out.get(0), new FakeItems()));
	}
}
