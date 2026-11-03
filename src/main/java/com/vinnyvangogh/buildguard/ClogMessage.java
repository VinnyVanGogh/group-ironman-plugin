package com.vinnyvangogh.buildguard;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the game message "New item added to your collection log: X".
 *
 * <p>The game only sends it with the in-game setting <i>Collection log - New
 * addition notification</i> turned on; SETUP.md says so. The plugin checks the
 * message type is a game message before calling this, so a player typing the
 * same words in chat is not read.
 */
final class ClogMessage
{
	private static final Pattern NEW_SLOT = Pattern.compile("^New item added to your collection log: (.+)$");
	private static final Pattern TAG = Pattern.compile("<[^>]*>");

	private ClogMessage()
	{
	}

	/** The item name, or null when this is not that message. */
	static String parse(String message)
	{
		if (message == null)
		{
			return null;
		}
		Matcher m = NEW_SLOT.matcher(TAG.matcher(message).replaceAll("").trim());
		if (!m.matches())
		{
			return null;
		}
		String item = m.group(1).trim();
		return item.isEmpty() ? null : item;
	}
}
