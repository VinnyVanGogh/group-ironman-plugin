package com.vinnyvangogh.groupironman;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The game's chat lines for the medium event kinds. Pure, tags stripped first.
 *
 * <p><b>Unverified wording.</b> These lines were written from memory of the
 * base client's screenshot, chat commands and slayer plugins and of Dink: the
 * sources were not readable when this was built (BRIEF.md, part C). A line the
 * game words differently fails silent, so each needs one in-game check.
 */
final class EventChat
{
	private static final Pattern CLUE = Pattern.compile("^You have completed ([0-9,]+) (\\w+) Treasure Trails?\\.?$");
	private static final Pattern COMBAT_TASK = Pattern.compile(
		"^Congratulations, you've completed an? (\\w+) combat task: (.+?)(?: \\(\\d+ points?\\))?\\.?$");
	private static final Pattern NEW_BEST = Pattern.compile(
		"(?i)(?:duration:|subdued in|completion time:) ([0-9:.]*[0-9])\\.? \\(new personal best\\)");
	private static final Pattern SLAYER_DONE = Pattern.compile("^You've completed (?:at least )?([0-9,]+) (?:Wilderness )?"
		+ "tasks?\\b.*");
	private static final Pattern SLAYER_NEW = Pattern.compile(
		"^You have received a new Slayer assignment from .+?: (.+) \\(([0-9,]+)\\)$");
	private static final Pattern SPECIAL = Pattern.compile("^(.+?) found something special: (.+)$");
	private static final Pattern DASH_LOOT = Pattern.compile("^(.+?) - (.+)$");
	static final String SPECIAL_LOOT_HEADER = "Special loot:";

	private EventChat()
	{
	}

	/** {@code group(1)} and {@code group(2)} of the pattern on the tag-free line; null when it does not match. */
	private static String[] two(Pattern p, String message)
	{
		Matcher m = p.matcher(EventText.plain(message));
		return m.matches() ? new String[]{m.group(1).trim(), m.group(2).trim()} : null;
	}

	/** Clue tier and count: "You have completed 42 medium Treasure Trails." */
	static EventText.Count clue(String message)
	{
		String[] p = two(CLUE, message);
		return p == null ? null : new EventText.Count(p[1], number(p[0]));
	}

	/** {tier, task name}, or null. */
	static String[] combatTask(String message)
	{
		return two(COMBAT_TASK, message);
	}

	/** The time of a new personal best, or null. */
	static String personalBest(String message)
	{
		Matcher m = NEW_BEST.matcher(EventText.plain(message));
		return m.find() ? m.group(1) : null;
	}

	/** Tasks completed so far, or -1 when this is not the task-done line. */
	static long slayerDone(String message)
	{
		Matcher m = SLAYER_DONE.matcher(EventText.plain(message));
		return m.matches() ? number(m.group(1)) : -1;
	}

	/** New task as a monster name and amount, or null. */
	static EventText.Count slayerNew(String message)
	{
		String[] p = two(SLAYER_NEW, message);
		return p == null ? null : new EventText.Count(p[0], number(p[1]));
	}

	/** {player, item} from "Alex found something special: X". */
	static String[] raidSpecial(String message)
	{
		return two(SPECIAL, message);
	}

	/** {player, item} from Chambers' "Alex - X", which only means loot straight after its header line. */
	static String[] raidDash(String message)
	{
		return two(DASH_LOOT, message);
	}

	static boolean isSpecialLootHeader(String message)
	{
		return SPECIAL_LOOT_HEADER.equals(EventText.plain(message));
	}

	private static long number(String digits)
	{
		try
		{
			return Long.parseLong(digits.replace(",", ""));
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}
}
