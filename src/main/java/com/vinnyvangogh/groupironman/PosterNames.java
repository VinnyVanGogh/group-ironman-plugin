package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The rules a Custom poster name must meet ({@link Webhooks#name} decides which name a post uses).
 *
 * <p>Discord's Execute Webhook takes a username of 1 to 80 characters and answers 400 to the names in
 * {@link #refused}, so a longer name is cut to 80 and a refused one is never sent: the post goes out under that
 * webhook's default name and the player gets one chat line naming the field, never the name. Pure, so the rules
 * and the once-only warning are tested.
 */
final class PosterNames
{
	/** Discord's limit on a webhook username. */
	static final int MAX = 80;
	private static final String[] REFUSED = {"discord", "clyde", "@", "#", ":", "```"};
	private static final List<String> EXACT = Arrays.asList("everyone", "here");

	/** Field label to the refused name already warned about. In memory only, never shown or logged. */
	private final Map<String, String> warned = new HashMap<>();

	/** Trimmed and cut to {@link #MAX} characters, never through a surrogate pair. Empty for null. */
	static String clean(String name)
	{
		String s = name == null ? "" : name.trim();
		if (s.length() > MAX)
		{
			int end = Character.isHighSurrogate(s.charAt(MAX - 1)) ? MAX - 1 : MAX;
			s = s.substring(0, end).trim();
		}
		return s;
	}

	/**
	 * Discord answers 400 to a webhook username containing "discord", "clyde", '@', '#', ':' or "```", or one
	 * that is exactly "everyone" or "here" (any case, checked after {@link #clean}).
	 */
	static boolean refused(String name)
	{
		String lower = name.toLowerCase(Locale.ROOT);
		if (EXACT.contains(lower))
		{
			return true;
		}
		for (String part : REFUSED)
		{
			if (lower.contains(part))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The chat lines to show now: one per Custom box (label to name, {@link Webhooks#customNames}) whose name
	 * Discord would refuse, once per name. A box that is cleared, fixed or no longer on Custom is forgotten, so
	 * breaking it again warns again.
	 */
	synchronized List<String> check(Map<String, String> fields)
	{
		List<String> out = new ArrayList<>();
		for (Map.Entry<String, String> field : fields.entrySet())
		{
			String value = clean(field.getValue());
			if (!refused(value))
			{
				warned.remove(field.getKey());
			}
			else if (!value.equals(warned.put(field.getKey(), value)))
			{
				out.add(message(field.getKey()));
			}
		}
		return out;
	}

	static String message(String label)
	{
		return GroupIronmanPlugin.NAME + ": the " + label + " Discord custom name can't contain 'discord', 'clyde',"
			+ " @, #, : or ```, or be 'everyone' or 'here'; posts use its default name instead";
	}
}
