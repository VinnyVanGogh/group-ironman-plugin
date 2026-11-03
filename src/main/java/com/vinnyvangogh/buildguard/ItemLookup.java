package com.vinnyvangogh.buildguard;

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
}
