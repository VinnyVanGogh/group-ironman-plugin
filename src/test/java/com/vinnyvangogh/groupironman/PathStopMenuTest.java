package com.vinnyvangogh.groupironman;

import net.runelite.api.MenuAction;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The Path to card's right-click "Stop path" (Board 2026-11-20): before it, right-clicking the card did nothing. */
public class PathStopMenuTest
{
	private static PathStopOverlay card()
	{
		return new PathStopOverlay(new GroupIronmanConfig()
		{
		}, () -> null);
	}

	@Test
	public void theCardOffersStopPathAsAPluginOnlyEntry()
	{
		PathStopOverlay card = card();
		assertEquals(1, card.getMenuEntries().size());
		OverlayMenuEntry e = card.getMenuEntries().get(0);
		assertEquals("Stop path", e.getOption());
		assertEquals("Path to", e.getTarget());
		assertEquals("sends nothing to the game", MenuAction.RUNELITE_OVERLAY, e.getMenuAction());
	}

	@Test
	public void onlyThisCardsStopPathStops()
	{
		PathStopOverlay card = card();
		OverlayMenuEntry stop = card.getMenuEntries().get(0);
		assertTrue(card.stops(new OverlayMenuClicked(stop, card)));
		assertFalse("another card's click", card.stops(new OverlayMenuClicked(stop, card())));
		assertFalse("another entry", card.stops(new OverlayMenuClicked(
			new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, "Configure", "Path to"), card)));
	}
}
