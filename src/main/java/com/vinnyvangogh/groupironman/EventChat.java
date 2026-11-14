package com.vinnyvangogh.groupironman;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The game's chat lines for the medium event kinds. Pure, tags stripped first.
 *
 * <p><b>Unverified wording.</b> These lines were written from memory of the
 * base client's screenshot, chat commands and slayer plugins and of Dink: the
 * sources were not readable when this was built (BRIEF.md, part C). A line the
 * game words differently fails silent, so each needs one in-game check. The
 * Slayer lines are the exception: they are cited from RuneLite's source below.
 */
final class EventChat
{
	private static final Pattern CLUE = Pattern.compile("^You have completed ([0-9,]+) (\\w+) Treasure Trails?\\.?$");
	private static final Pattern COMBAT_TASK = Pattern.compile(
		"^Congratulations, you've completed an? (\\w+) combat task: (.+?)(?: \\(\\d+ points?\\))?\\.?$");
	private static final Pattern NEW_BEST = Pattern.compile(
		"(?i)(?:duration:|subdued in|completion time:) ([0-9:.]*[0-9])\\.? \\(new personal best\\)");
	/*
	 * Slayer lines. RuneLite's SlayerPlugin read these from chat and dialogue until it moved to varps
	 * (runelite/runelite 1f6ae893, 2023-02-24, "slayer: use vars for task tracking"); the patterns are its
	 * constants from the parent revision, SlayerPlugin.java:107-121, with the test lines in SlayerPluginTest.java
	 * :85-126. The completion line it only trusted when it named a Slayer master (:472), which the gem's
	 * "You've completed 0 tasks in a row and currently have a total of 0 points." does not.
	 * "You have completed your task! ..." is Dink's SlayerNotifier.SLAYER_TASK_REGEX (pajlads/DinkPlugin 485054b).
	 */
	/** CHAT_COMPLETE_MESSAGE, with Dink's "Mortimer" streak. Group 1 the streak, 2 the points this task gave. */
	private static final Pattern SLAYER_DONE = Pattern.compile("You've completed (?:at least )?([\\d,]+)"
		+ " (?:Wilderness |Mortimer )?tasks?(?: and received ([\\d,]+) points?, giving you a total of [\\d,]+"
		+ "| and reached the maximum amount of Slayer points \\([\\d,]+\\))?");
	private static final Pattern SLAYER_KILLED = Pattern.compile(
		"^You have completed your task! You killed ([\\d,]+) ([^.]+)\\..*");
	/** CHAT_GEM_PROGRESS_MESSAGE; the partner's name is a group here. */
	private static final Pattern SLAYER_GEM = Pattern.compile("^(?:You're assigned to kill|You have received a new"
		+ " Slayer assignment from (?<from>.*):) (?:[Tt]he )?(?<name>.+?)(?: (?:in|on|south of) (?:the )?[^;]+?)?"
		+ "(?:; only | \\()(?<amount>\\d+)(?: more to go\\.|\\))$");
	/** CHAT_GEM_COMPLETE_MESSAGE, CHAT_CANCEL_MESSAGE and CHAT_CANCEL_MESSAGE_JAD_ZUK: no task any more. */
	private static final Pattern SLAYER_CLEARED = Pattern.compile("^(?:You need something new to hunt\\."
		+ "|Your task has been cancelled\\.|You no longer have a slayer task(?: as you left the (?:fight cave|Inferno))?\\.)$");
	/** NPC_ASSIGN_MESSAGE: the master's (or NPC Contact's) dialogue. */
	private static final Pattern NPC_ASSIGN = Pattern.compile(".*(?:Your new task is to kill|You are to bring balance"
		+ " to)\\s*(?<amount>\\d+) (?<name>.+?)(?: (?:in|on|south of) (?:the )?.+)?\\.");
	/** NPC_ASSIGN_BOSS_MESSAGE. */
	private static final Pattern NPC_ASSIGN_BOSS = Pattern.compile("^(?:Excellent\\. )?You're now assigned to"
		+ " (?:kill|bring balance to) (?:the )?(?<name>.*) (?<amount>\\d+) times.*Your reward point tally is (.*)\\.$");
	/** NPC_ASSIGN_FIRST_MESSAGE: Turael's (or Spria's, Aya's) first task. */
	private static final Pattern NPC_ASSIGN_FIRST = Pattern.compile("^We'll start you off (?:hunting|bringing balance"
		+ " to) (?<name>.*), you'll need to kill (?<amount>\\d*) of them\\.$");
	/** NPC_CURRENT_MESSAGE: the master, or the gem's "Check", repeating the task already held. */
	private static final Pattern NPC_CURRENT = Pattern.compile("^You're (?:still(?: meant to be)?|currently assigned"
		+ " to) (?:hunting|bringing balance to|kill|bring balance to|slaying) (?<name>.+?)(?: (?:in|on|south of)"
		+ " (?:the )?.+)?(?:, with|; (?:you have|only)) (?<amount>\\d+)(?: more)? to go\\..*");
	private static final Pattern BREAK = Pattern.compile("(?i)<br\\s*/?>");
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

	/**
	 * The streak line after a finished task: {streak, points this task gave, or -1 when the line gives none}.
	 * Null for any other line, the gem's "currently have a total of" line among them: only a line that sends
	 * you back to a Slayer master is one.
	 */
	static long[] slayerDone(String message)
	{
		String text = EventText.plain(message);
		if (!text.startsWith("You've completed") || !text.toLowerCase(Locale.ROOT).contains("slayer master"))
		{
			return null;
		}
		Matcher m = SLAYER_DONE.matcher(text);
		if (!m.find())
		{
			return null;
		}
		return new long[]{number(m.group(1)), m.group(2) == null ? -1 : number(m.group(2))};
	}

	/** "You have completed your task! You killed 19 Icefiends. ...": the monster, or null. */
	static String slayerKilled(String message)
	{
		String[] p = two(SLAYER_KILLED, message);
		return p == null ? null : capital(p[1]);
	}

	/** The gem's empty-task line, or a cancelled task. */
	static boolean slayerCleared(String message)
	{
		return SLAYER_CLEARED.matcher(EventText.plain(message)).matches();
	}

	/** The gem's progress line (not new), or a partner's assignment (new, from them); null for anything else. */
	static SlayerTask slayerChat(String message)
	{
		Matcher m = SLAYER_GEM.matcher(EventText.plain(message));
		if (!m.matches())
		{
			return null;
		}
		String from = m.group("from");
		return task(m.group("name"), m.group("amount"), from == null ? "" : from.trim(), from != null);
	}

	/**
	 * The NPC dialogue: a new assignment ({@code npc} gave it) or the task repeated (not new); null for any other
	 * line. {@code <br>} breaks are spaces, as RuneLite's {@code Text.sanitizeMultilineText} made them.
	 */
	static SlayerTask slayerDialog(String text, String npc)
	{
		String line = EventText.plain(BREAK.matcher(text == null ? "" : text).replaceAll(" ")).replaceAll("\\s+", " ");
		String from = EventText.plain(npc);
		for (Pattern p : new Pattern[]{NPC_ASSIGN, NPC_ASSIGN_BOSS, NPC_ASSIGN_FIRST})
		{
			Matcher m = p.matcher(line);
			if (m.find())
			{
				return task(m.group("name"), m.group("amount"), from, true);
			}
		}
		Matcher current = NPC_CURRENT.matcher(line);
		return current.find() ? task(current.group("name"), current.group("amount"), from, false) : null;
	}

	private static SlayerTask task(String name, String amount, String from, boolean fresh)
	{
		return new SlayerTask(capital(name.trim()), number(amount), from, fresh);
	}

	/** "icefiends" as the gem says it reads "Icefiends". */
	private static String capital(String name)
	{
		return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	/** A Slayer task as a line names it. */
	static final class SlayerTask
	{
		final String name;
		final long amount;
		/** The master or partner who gave it; empty when the line does not say. */
		final String from;
		/** The line hands out a task, rather than repeating the one held. */
		final boolean fresh;

		SlayerTask(String name, long amount, String from, boolean fresh)
		{
			this.name = name;
			this.amount = amount;
			this.from = from;
			this.fresh = fresh;
		}
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
