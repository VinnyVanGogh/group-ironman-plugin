package com.vinnyvangogh.groupironman;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.SkillingContainer.COAL_BAG;
import static com.vinnyvangogh.groupironman.SkillingContainer.FISH_BARREL;
import static com.vinnyvangogh.groupironman.SkillingContainer.LOG_BASKET;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Log basket, fish barrel and coal bag counts from the game's own lines (copied
 * from Combat Vitals' ContainerContents), and the item names kept for Find.
 * Unknown stays unknown: no line, no number.
 */
public class ContainerContentsTest
{
	private static ContainerContents openBasket()
	{
		ContainerContents c = new ContainerContents();
		c.carried(EnumSet.of(LOG_BASKET), Collections.emptyMap());
		return c;
	}

	private static Map<String, Integer> named(Object... nameCount)
	{
		Map<String, Integer> out = new TreeMap<>();
		for (int i = 0; i < nameCount.length; i += 2)
		{
			out.put((String) nameCount[i], (Integer) nameCount[i + 1]);
		}
		return out;
	}

	// ---- Unknown stays unknown ----

	@Test
	public void unknownUntilTheGameSaysAndNoGuessFromGathering()
	{
		ContainerContents c = openBasket();
		assertEquals(-1, c.contents(LOG_BASKET));
		// Gathering into a basket of unknown contents is still unknown, not "1".
		assertFalse(c.message("You get some yew logs.", 10));
		assertEquals(-1, c.contents(LOG_BASKET));
		assertNull(c.named(LOG_BASKET));
	}

	@Test
	public void unrelatedAndNullLinesChangeNothing()
	{
		ContainerContents c = openBasket();
		assertFalse(c.message(null));
		assertFalse(c.message("Welcome to Old School RuneScape."));
		assertFalse(c.message("The basket is full of good intentions"));
		assertEquals(-1, c.contents(LOG_BASKET));
	}

	// ---- Message variants ----

	@Test
	public void checkDialogSumsEveryStackAndKeepsTheNames()
	{
		ContainerContents c = openBasket();
		assertTrue(c.message("The basket contains: 14 x Yew logs"));
		assertEquals(14, c.contents(LOG_BASKET));
		assertEquals(named("Yew logs", 14), c.named(LOG_BASKET));

		// Dialog lines joined with spaces, or a comma list: both read the same.
		assertTrue(c.message("The basket contains: 5 x Logs 3 x Oak logs"));
		assertEquals(8, c.contents(LOG_BASKET));
		assertEquals(named("Logs", 5, "Oak logs", 3), c.named(LOG_BASKET));
		assertTrue(c.message("The basket contains: 5 x Logs, 3 x Oak logs"));
		assertEquals(named("Logs", 5, "Oak logs", 3), c.named(LOG_BASKET));
	}

	@Test
	public void everyEmptyWordingIsZero()
	{
		String[] lines = {"You empty your basket.", "You empty your basket into the bank.", "Your basket is empty.",
			"The basket is empty."};
		for (String line : lines)
		{
			ContainerContents c = openBasket();
			c.message("The basket contains: 19 x Logs");
			assertTrue(line, c.message(line));
			assertEquals(line, 0, c.contents(LOG_BASKET));
			assertEquals(line, Collections.emptyMap(), c.named(LOG_BASKET));
		}
	}

	@Test
	public void gatheredIntoAnOpenBasketCountsOneAndNamesIt()
	{
		ContainerContents c = openBasket();
		c.message("The basket contains: 18 x Yew logs");
		assertTrue(c.message("You get some yew logs.", 20));
		assertEquals(19, c.contents(LOG_BASKET));
		assertEquals(named("Yew logs", 19), c.named(LOG_BASKET));
		assertTrue(c.message("You get some logs.", 21));
		assertEquals(named("Logs", 1, "Yew logs", 19), c.named(LOG_BASKET));
	}

	@Test
	public void aClosedBasketTakesNothing()
	{
		ContainerContents c = new ContainerContents();
		c.carried(EnumSet.noneOf(SkillingContainer.class), Collections.emptyMap());
		c.message("The basket contains: 3 x Logs");
		assertFalse(c.message("You get some logs.", 5));
		assertEquals(3, c.contents(LOG_BASKET));
	}

	@Test
	public void anUnnamedExtraLogKeepsTheCountButForgetsTheNames()
	{
		ContainerContents c = openBasket();
		c.message("The basket contains: 4 x Yew logs");
		assertTrue(c.message("The nature offerings enabled you to chop an extra log.", 7));
		assertEquals(5, c.contents(LOG_BASKET));
		assertNull("which log is not said, so Find says nothing", c.named(LOG_BASKET));
	}

	@Test
	public void anItemLandingInTheInventoryWhileOpenMeansFull()
	{
		ContainerContents c = openBasket();
		c.message("The basket contains: 20 x Logs");
		c.message("You get some logs.", 30);
		// The inventory grew on the same tick with no Fill or Empty: it missed the basket.
		assertTrue(c.inventoryChanged(10, 11, 30));
		assertEquals(28, c.contents(LOG_BASKET));
	}

	@Test
	public void fillAndEmptyMoveWhatTheInventoryShows()
	{
		ContainerContents c = openBasket();
		c.message("The basket contains: 10 x Logs");
		c.clicked(LOG_BASKET, "Fill", 40);
		assertTrue(c.inventoryChanged(12, 4, 41));
		assertEquals(18, c.contents(LOG_BASKET));
		assertNull("filled from the inventory: which logs is not said", c.named(LOG_BASKET));

		c.clicked(LOG_BASKET, "Empty", 50);
		assertTrue(c.inventoryChanged(4, 20, 50));
		assertEquals(2, c.contents(LOG_BASKET));

		c.clicked(LOG_BASKET, "Empty to bank", 60);
		assertEquals(0, c.contents(LOG_BASKET));
	}

	@Test
	public void coalBagWordingIncludingOne()
	{
		ContainerContents c = new ContainerContents();
		assertTrue(c.message("The coal bag contains 27 pieces of coal."));
		assertEquals(27, c.contents(COAL_BAG));
		assertEquals(named("Coal", 27), c.named(COAL_BAG));
		assertTrue(c.message("The coal bag still contains one piece of coal."));
		assertEquals(1, c.contents(COAL_BAG));
		assertTrue(c.message("The coal bag is now empty."));
		assertEquals(0, c.contents(COAL_BAG));
	}

	@Test
	public void barrelCheckFullAndCaughtFish()
	{
		ContainerContents c = new ContainerContents();
		c.carried(EnumSet.of(FISH_BARREL), Collections.emptyMap());
		assertTrue(c.message("The barrel contains: 3 x Raw trout, 2 x Raw salmon"));
		assertEquals(5, c.contents(FISH_BARREL));
		assertTrue(c.fishCaught(70));
		assertEquals(6, c.contents(FISH_BARREL));
		assertTrue(c.message("The barrel is full. It may be emptied at a bank."));
		assertEquals(28, c.contents(FISH_BARREL));
	}

	@Test
	public void restoredCountsNeverOverrideThisSession()
	{
		ContainerContents c = openBasket();
		c.message("The basket contains: 9 x Logs");
		c.restore(LOG_BASKET, 3);
		assertEquals(9, c.contents(LOG_BASKET));
		c.restore(FISH_BARREL, 12);
		assertEquals(12, c.contents(FISH_BARREL));
		assertNull("a restored count has no names", c.named(FISH_BARREL));
		c.clear();
		assertEquals(-1, c.contents(LOG_BASKET));
	}

	// ---- Which items are containers ----

	@Test
	public void containerNamesOpenOrClosed()
	{
		assertEquals(LOG_BASKET, SkillingContainer.fromItemName("Log basket"));
		assertEquals(LOG_BASKET, SkillingContainer.fromItemName("Open forestry basket"));
		assertEquals(FISH_BARREL, SkillingContainer.fromItemName("Fish sack barrel"));
		assertEquals(COAL_BAG, SkillingContainer.fromItemName("Coal bag"));
		assertNull(SkillingContainer.fromItemName("Logs"));
		assertNull(SkillingContainer.fromItemName(null));
		assertTrue(SkillingContainer.isOpen("Open log basket"));
		assertFalse(SkillingContainer.isOpen("Log basket"));
		assertEquals("Yew logs", ContainerContents.logName("yew "));
		assertEquals("Logs", ContainerContents.logName(null));
	}
}
