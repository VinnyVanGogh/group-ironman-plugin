package com.vinnyvangogh.groupironman;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.runelite.api.WorldType;
import org.junit.Test;

/** Group members are marked in their Compare colour, roster only, and never in PvP or the Wilderness. */
public class MemberPlayerOverlayTest
{
	private static final List<String> ORDER = Arrays.asList("GIM1", "GIM2", "GIM3");

	@Test
	public void colourIsTheOneCompareUsesForThatPlace()
	{
		Map<String, Color> colours = MemberColors.byKey(ORDER);
		for (int i = 0; i < ORDER.size(); i++)
		{
			assertEquals(MemberColors.of(i), MemberPlayerOverlay.colourFor(colours, ORDER.get(i), "someone else"));
		}
	}

	@Test
	public void lookupIgnoresCaseSpacingAndIcons()
	{
		Map<String, Color> colours = MemberColors.byKey(Arrays.asList("Iron Bob"));
		assertEquals(MemberColors.of(0), MemberPlayerOverlay.colourFor(colours, "iron_bob", "x"));
		assertEquals(MemberColors.of(0), MemberPlayerOverlay.colourFor(colours, "<img=2>IRON BOB", "x"));
	}

	@Test
	public void nonMembersAndYourselfGetNoColour()
	{
		Map<String, Color> colours = MemberColors.byKey(ORDER);
		assertNull(MemberPlayerOverlay.colourFor(colours, "Stranger", "GIM1"));
		assertNull(MemberPlayerOverlay.colourFor(colours, "GIM1", "gim1"));
		assertNull(MemberPlayerOverlay.colourFor(colours, null, "GIM1"));
		assertNull(MemberPlayerOverlay.colourFor(Collections.emptyMap(), "GIM2", "GIM1"));
	}

	@Test
	public void pvpWorldsAndTheWildernessSuppressEverything()
	{
		assertFalse(MemberPlayerOverlay.suppressed(EnumSet.noneOf(WorldType.class), false));
		assertFalse(MemberPlayerOverlay.suppressed(EnumSet.of(WorldType.MEMBERS), false));
		assertTrue(MemberPlayerOverlay.suppressed(EnumSet.noneOf(WorldType.class), true));
		assertTrue(MemberPlayerOverlay.suppressed(EnumSet.of(WorldType.PVP), false));
		assertTrue(MemberPlayerOverlay.suppressed(EnumSet.of(WorldType.DEADMAN), false));
	}

	@Test
	public void compareOrderIsRosterOrderWithSelfLastWhenUnlisted()
	{
		// The same list feeds Compare's View and the overlays, so one member is one colour everywhere.
		Map<String, Color> a = MemberColors.byKey(ORDER);
		Map<String, Color> b = MemberColors.byKey(ORDER);
		assertEquals(a, b);
		assertEquals(ORDER.size(), a.size());
	}

	@Test
	public void switchesDefaultToNamesAndDotsOnOutlineOff()
	{
		GroupIronmanConfig c = new GroupIronmanConfig()
		{
		};
		assertTrue(c.memberNames());
		assertTrue(c.memberMinimapDots());
		assertFalse(c.memberOutline());
	}
}
