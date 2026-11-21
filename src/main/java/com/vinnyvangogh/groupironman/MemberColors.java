package com.vinnyvangogh.groupironman;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

	/**
	 * Every member's colour by {@link GroupMembers#key}, from the same order Compare uses: the map markers, minimap
	 * dots and overhead names read this, so a member is one colour everywhere.
	 */
	static Map<String, Color> byKey(List<String> order)
	{
		Map<String, Color> out = new HashMap<>();
		for (int i = 0; i < order.size(); i++)
		{
			out.putIfAbsent(GroupMembers.key(order.get(i)), of(i));
		}
		return out;
	}

	/** The same colour at about 22% opacity, to tint a table cell behind its text. */
	static Color soft(int index)
	{
		Color c = of(index);
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), 56);
	}
}
