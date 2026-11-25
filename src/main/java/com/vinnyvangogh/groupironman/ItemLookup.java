package com.vinnyvangogh.groupironman;

/**
 * Item names and prices for the group panel.
 *
 * <p>An interface so the panel's text can be tested without an
 * {@code ItemManager}; the plugin's implementation answers from a cache it
 * fills on the client thread, because item compositions may only be read
 * there.
 */
interface ItemLookup
{
	/** The item's name, or a placeholder such as "item 1234" when it is not known yet. */
	String name(int id);

	/** Price per item in gp, 0 when unknown. */
	long price(int id);

	/** The equipment slot (EquipmentInventorySlot index) the item is worn in, or -1. */
	default int slot(int id)
	{
		return -1;
	}

	/** The {@link ItemStyles} bits from the item's equipment stats; 0 when it is not combat gear or not read yet. */
	default int combatStyles(int id)
	{
		return 0;
	}

	/** The id of the item with this name (untradeables too), or a negative number while unknown. */
	default int itemId(String name)
	{
		return -1;
	}

	/** What High Alchemy pays for one of the item in gp, 0 when unknown or not alchable. */
	default long highAlch(int id)
	{
		return 0;
	}

	/**
	 * An item quantity or gp value as text: the game's "450K" here, the exact
	 * number or both through {@link Amounts#words}. Every line built from this
	 * lookup reads its amounts the same way.
	 */
	default String amount(long amount)
	{
		return Amounts.GAME.of(amount);
	}
}
