package com.vinnyvangogh.groupironman;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The in-game Easter egg tag (67 and 69 on banners and the Group box). Every Discord egg line, and the egg file's
 * wording rules, moved to Discord Relay with its tests.
 */
public class EasterEggsTest
{
	private static final EasterEggs.Settings ON = new EasterEggs.Settings(true, 1_000_000, 1_000_000);
	private static final EasterEggs.Settings OFF = new EasterEggs.Settings(false, 1_000_000, 1_000_000);

	private static GroupEvent level(GroupEvent.Type type, long level)
	{
		return GroupEvent.of(type, 1_000L, "Alex").text("Attack").number(level).build();
	}

	@Test
	public void sixtySevenAndSixtyNineAreTaggedInGame()
	{
		for (long n : new long[]{67, 69})
		{
			String tag = EasterEggs.overlayTag(level(GroupEvent.Type.LEVEL_UP, n), ON);
			assertNotNull("level " + n, tag);
			assertFalse(tag.isEmpty());
			assertTrue("no emoji in the game font", tag.codePoints().noneMatch(Character::isSupplementaryCodePoint));
			assertNotNull("combat " + n, EasterEggs.overlayTag(level(GroupEvent.Type.COMBAT_LEVEL, n), ON));
		}
	}

	@Test
	public void otherLevelsAndOtherKindsAreNotTagged()
	{
		assertNull(EasterEggs.overlayTag(level(GroupEvent.Type.LEVEL_UP, 68), ON));
		assertNull(EasterEggs.overlayTag(level(GroupEvent.Type.TOTAL_LEVEL, 67), ON));
		assertNull("Easter eggs off", EasterEggs.overlayTag(level(GroupEvent.Type.LEVEL_UP, 67), OFF));
		assertEquals("line", EasterEggs.tagged("line", level(GroupEvent.Type.LEVEL_UP, 68), ON));
	}

	@Test
	public void theTagIsStableForTheSameEvent()
	{
		GroupEvent e = level(GroupEvent.Type.LEVEL_UP, 69);
		assertEquals(EasterEggs.overlayTag(e, ON), EasterEggs.overlayTag(e, ON));
		assertEquals("line " + EasterEggs.overlayTag(e, ON), EasterEggs.tagged("line", e, ON));
		assertEquals(EasterEggs.pick(42L, 5), EasterEggs.pick(42L, 5));
	}
}
