package com.vinnyvangogh.groupironman;

/**
 * Which combat style a piece of gear is for, worked out from RuneLite's item stats rather than a list of
 * names: offensive bonuses decide first, and armour with none is judged by which defence leads. Bit flags,
 * so the plugin can keep one int per item.
 */
final class ItemStyles
{
	static final int MELEE = 1;
	static final int RANGED = 2;
	static final int MAGIC = 4;
	/** Any item with combat stats, whatever its style: an amulet is combat gear but no style. */
	static final int COMBAT = 8;

	private ItemStyles()
	{
	}

	/**
	 * @param slot equipment slot, or -1 for an item that cannot be worn
	 * @param aMelee best of the stab, slash and crush attack bonuses
	 * @param dMelee best of the stab, slash and crush defence bonuses
	 * @return the {@link #MELEE}, {@link #RANGED}, {@link #MAGIC} and {@link #COMBAT} bits; 0 for a non-combat item
	 */
	static int classify(int slot, int aMelee, int aRange, int aMagic, int str, int rstr, float mdmg, int dMelee,
		int dRange, int dMagic, int prayer)
	{
		if (slot < 0)
		{
			return 0;
		}
		int style = 0;
		if (aMelee > 0 || str > 0)
		{
			style |= MELEE;
		}
		if (aRange > 0 || rstr > 0)
		{
			style |= RANGED;
		}
		if (aMagic > 0 || mdmg > 0)
		{
			style |= MAGIC;
		}
		if (style == 0)
		{
			style = byDefence(dMelee, dRange, dMagic);
		}
		boolean stats = style != 0 || aMelee != 0 || aRange != 0 || aMagic != 0 || dMelee != 0 || dRange != 0
			|| dMagic != 0 || prayer != 0;
		return stats ? style | COMBAT : 0;
	}

	/** Armour has no attack bonus: it belongs to the style it defends best against, ties to the weaker claim. */
	private static int byDefence(int dMelee, int dRange, int dMagic)
	{
		if (dMagic > 0 && dMagic >= dMelee && dMagic >= dRange)
		{
			return MAGIC;
		}
		if (dRange > 0 && dRange > dMelee)
		{
			return RANGED;
		}
		return dMelee > 0 ? MELEE : 0;
	}
}
