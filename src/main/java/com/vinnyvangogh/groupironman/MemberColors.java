package com.vinnyvangogh.groupironman;

import java.awt.Color;

/**
 * One colour per member for the Leaderboard and Compare views (Board 2026-11-18): the same member is the same colour
 * on every name, bar, tile level and table cell. The palette is Okabe-Ito based, each at least 6:1 against the
 * panel's dark background, and is picked by the member's place in the group's order.
 */
final class MemberColors
{
	private static final Color[] PALETTE = {
		new Color(0xE6, 0x9F, 0x00), new Color(0x56, 0xB4, 0xE9), new Color(0x2F, 0xBF, 0x8F),
		new Color(0xF0, 0xE4, 0x42), new Color(0xDD, 0x8C, 0xC0)};

	private MemberColors()
	{
	}

	/** The colour for the member at {@code index}; a sixth member or beyond wraps round. */
	static Color of(int index)
	{
		return PALETTE[Math.floorMod(index, PALETTE.length)];
	}

	/** The same colour at about 22% opacity, to tint a table cell behind its text. */
	static Color soft(int index)
	{
		Color c = of(index);
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), 56);
	}
}
