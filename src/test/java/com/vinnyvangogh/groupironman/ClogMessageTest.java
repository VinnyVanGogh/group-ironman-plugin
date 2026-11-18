package com.vinnyvangogh.groupironman;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Reading the game's "New item added to your collection log" chat line. */
public class ClogMessageTest
{
	@Test
	public void readsTheItemNameOutOfTheColouredMessage()
	{
		assertEquals("Draconic visage",
			ClogMessage.parse("New item added to your collection log: <col=ef1020>Draconic visage</col>"));
	}

	@Test
	public void readsAPlainMessage()
	{
		assertEquals("Abyssal whip", ClogMessage.parse("New item added to your collection log: Abyssal whip"));
	}

	@Test
	public void ignoresOtherMessages()
	{
		assertNull(ClogMessage.parse(null));
		assertNull(ClogMessage.parse(""));
		assertNull(ClogMessage.parse("Your Vorkath kill count is: 5."));
		assertNull(ClogMessage.parse("New item added to your collection log: "));
		assertNull(ClogMessage.parse("Alex: New item added to your collection log: Fake"));
	}
}
