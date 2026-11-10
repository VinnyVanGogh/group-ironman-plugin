package com.vinnyvangogh.groupironman;

import java.awt.Color;
import net.runelite.client.util.ColorUtil;

/**
 * How {@code ::find} answers read in the chatbox (Board 2026-11-04): the search
 * term and each place coloured by kind, and "your bank" rather than your own
 * name. The game's chat font has no bold, so emphasis is colour. Colours come
 * from the plugin settings; {@link #PLAIN} adds no tags, for tests and logs.
 */
final class FindStyle
{
	/** No colour tags. */
	static final FindStyle PLAIN = new FindStyle("", null, null, null, null, null, null);

	/** This player's name, so their own places read "your bank". */
	final String self;
	final Color query;
	final Color item;
	final Color yourBank;
	final Color memberBank;
	final Color storage;
	final Color carried;

	FindStyle(String self, Color query, Color item, Color yourBank, Color memberBank, Color storage, Color carried)
	{
		this.self = self == null ? "" : self;
		this.query = query;
		this.item = item;
		this.yourBank = yourBank;
		this.memberBank = memberBank;
		this.storage = storage;
		this.carried = carried;
	}

	static FindStyle of(GroupIronmanConfig config, String self)
	{
		// Item names in the chat's own colour unless asked (Board 2026-11-04: "a little too colourful").
		Color item = config.findColourItemNames() ? config.findItemColour() : null;
		return new FindStyle(self, config.findQueryColour(), item, config.findYourBankColour(),
			config.findMemberBankColour(), config.findStorageColour(), config.findCarriedColour());
	}

	boolean isYou(String member)
	{
		return !self.isEmpty() && self.equalsIgnoreCase(member);
	}

	/** {@code text} in {@code colour}, or as is when there is none. */
	static String paint(String text, Color colour)
	{
		return colour == null ? text : ColorUtil.wrapWithColorTag(text, colour);
	}

	/**
	 * Where a hit is, worded from the reader's side: "15 in your bank", "5 in Alex's
	 * bank", "140 in group storage", "1 equipped by Sam", "1 equipped by you".
	 */
	String place(GroupSearch.Hit h)
	{
		return place(h, Amounts.GAME);
	}

	/** {@link #place(GroupSearch.Hit)} with the amount in {@code amounts}: "450K in group storage". */
	String place(GroupSearch.Hit h, Amounts amounts)
	{
		String amount = amounts.of(h.quantity);
		if ("Group".equals(h.member))
		{
			return paint(amount + " in group " + h.where, storage);
		}
		boolean you = isYou(h.member);
		if ("equipped".equals(h.where))
		{
			return paint(amount + " equipped by " + (you ? "you" : h.member), carried);
		}
		String owner = you ? "your" : h.member + "'s";
		Color colour = "bank".equals(h.where) ? (you ? yourBank : memberBank) : carried;
		return paint(amount + " in " + owner + " " + h.where, colour);
	}
}
