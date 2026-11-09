package com.vinnyvangogh.groupironman;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The words for each {@link GroupEvent.Type}, and the game's chat and widget
 * lines that start one. Pure, so every line can be tested without a client.
 *
 * <p>Message texts follow the base client's screenshot plugin
 * ({@code ScreenshotPlugin.java} at runelite master: {@code PET_MESSAGES}
 * ~:111-113, {@code BOSSKILL_MESSAGE_PATTERN} ~:102, the Barrows chest count
 * line ~:398, the quest scroll title ~:286-291).
 */
final class EventText
{
	private static final String[] PET_MESSAGES = {
		"You have a funny feeling like you're being followed",
		"You feel something weird sneaking into your backpack",
		"You have a funny feeling like you would have been followed",
	};
	private static final Pattern TAG = Pattern.compile("<[^>]*>");
	/** "Your Vorkath kill count is: 120." and "Your Barrows chest count is: 41." */
	private static final Pattern KILL_COUNT = Pattern.compile(
		"^Your (.+?) (?:kill|chest|success|completion|completed) count is: ?([0-9,]+)\\.?$");
	/** "Your completed Chambers of Xeric count is: 12." The word order is unverified: see {@link EventChat}. */
	private static final Pattern RAID_COUNT = Pattern.compile("^Your completed (.+?) count is: ?([0-9,]+)\\.?$");
	/** "You have completed Cook's Assistant!" and "You have completed the Restless Ghost Quest!" */
	private static final Pattern QUEST = Pattern.compile(
		"^You (?:have|'ve) (?:completed|been|rebuilt|achieved|helped|defeated|restored|freed|found|learned|solved)"
			+ "(?: the)? '?(.+?)'?(?: [Qq]uest)?[!.]?$");

	private static final Map<GroupEvent.Type, BiFunction<GroupEvent, ItemLookup, String>> LINES =
		new EnumMap<>(GroupEvent.Type.class);
	private static final Map<GroupEvent.Type, String> TITLES = new EnumMap<>(GroupEvent.Type.class);

	static
	{
		// Kept word for word from 1.0.0's log line; the value goes in the embed's own field.
		line(GroupEvent.Type.DROP, "Valuable drop", (e, l) -> e.text + " dropped " + StorageLog.describe(e.items, l)
			+ kc(e));
		line(GroupEvent.Type.CLOG, "New collection log slot", (e, l) -> "new collection log slot, " + e.text);
		line(GroupEvent.Type.PET, "Pet", (e, l) -> "a pet!" + (e.text.isEmpty() ? "" : " (" + e.text + ")"));
		line(GroupEvent.Type.UNIQUE, "Untradeable unique", (e, l) -> "untradeable from " + e.text + ": "
			+ StorageLog.describe(e.items, l));
		line(GroupEvent.Type.DEATH, "Died", EventText::death);
		line(GroupEvent.Type.STORAGE_DEPOSIT, "Big storage deposit", (e, l) -> "deposited "
			+ items(e, l) + " to group storage");
		line(GroupEvent.Type.STORAGE_WITHDRAWAL, "Big storage withdrawal", (e, l) -> "took "
			+ items(e, l) + " from group storage");
		line(GroupEvent.Type.STORAGE_SUMMARY, "Group storage visit", EventText::summary);
		line(GroupEvent.Type.STORAGE_MINIMUM, "Storage minimum hit", (e, l) -> "group storage is low on " + e.text
			+ ": " + e.number + " left" + (e.detail.isEmpty() ? "" : ", " + e.detail));
		line(GroupEvent.Type.STORAGE_LAST, "Took the last one", (e, l) -> "took the last of "
			+ StorageLog.describe(e.items, l) + " from group storage");
		line(GroupEvent.Type.LEVEL_UP, "Level up", (e, l) -> e.text + " level " + e.number);
		line(GroupEvent.Type.MAXED, "99!", (e, l) -> "total".equalsIgnoreCase(e.text)
			? "maxed, total level " + e.number : "99 " + e.text + "!");
		line(GroupEvent.Type.XP_MILESTONE, "XP milestone", (e, l) -> BoostText.gp(e.number) + " " + e.text + " XP");
		line(GroupEvent.Type.COMBAT_LEVEL, "Combat level", (e, l) -> "combat level " + e.number);
		line(GroupEvent.Type.TOTAL_LEVEL, "Total level", (e, l) -> "total level " + e.number);
		line(GroupEvent.Type.QUEST, "Quest complete", (e, l) -> "completed " + e.text
			+ (e.number > 0 ? " (" + e.number + " QP)" : ""));
		line(GroupEvent.Type.QUEST_POINTS, "Quest points", (e, l) -> e.text.isEmpty()
			? e.number + " quest points" : e.text + ", " + e.number + " quest points");
		line(GroupEvent.Type.ONLINE, "Online", (e, l) -> "is " + e.text
			+ (e.world > 0 ? " on world " + e.world : ""));
		line(GroupEvent.Type.BACK, "Back", (e, l) -> "is back after " + e.number + (e.number == 1 ? " day" : " days"));
		line(GroupEvent.Type.BARROWS, "Barrows chest", (e, l) -> "Barrows chest" + (e.number > 0 ? " " + e.number : "")
			+ ": " + items(e, l) + (e.detail.isEmpty() ? "" : " (" + e.detail + ")"));
		line(GroupEvent.Type.RAID_UNIQUE, "Raid unique", (e, l) -> "purple! " + e.text
			+ (e.number > 0 ? ", raid count " + e.number : "") + (e.detail.isEmpty() ? "" : " (" + e.detail + ")"));
		line(GroupEvent.Type.CLUE, "Clue reward", (e, l) -> e.text + " clue reward: " + items(e, l)
			+ (e.detail.isEmpty() ? "" : " (" + e.detail + ")"));
		line(GroupEvent.Type.DRY_STREAK, "Dry streak", (e, l) -> e.number + " " + e.text + " kills without a unique"
			+ (e.detail.isEmpty() ? "" : " (" + e.detail + ")"));
		line(GroupEvent.Type.DEATH_VALUE, "Lost on death", EventText::deathValue);
		line(GroupEvent.Type.GRAVESTONE_EXPIRING, "Gravestone expiring", (e, l) -> "gravestone"
			+ (e.location.isEmpty() ? "" : " at " + e.location) + (e.world > 0 ? " on world " + e.world : "")
			+ " has about " + e.number + " min left");
		line(GroupEvent.Type.HC_LIFE_LOST, "Group life lost", (e, l) -> "lost a Hardcore group life, " + e.number
			+ (e.number == 1 ? " life" : " lives") + " left");
		line(GroupEvent.Type.WILDERNESS, "Wilderness", (e, l) -> e.text);
		line(GroupEvent.Type.DIARY, "Achievement diary", (e, l) -> "completed the " + e.text + " diary");
		line(GroupEvent.Type.COMBAT_TASK, "Combat achievement", (e, l) -> "combat task: " + e.text
			+ (e.detail.isEmpty() ? "" : " (" + e.detail + ")"));
		line(GroupEvent.Type.KC_MILESTONE, "Kill count milestone", (e, l) -> e.number + " " + e.text + " kills");
		line(GroupEvent.Type.PERSONAL_BEST, "New personal best", (e, l) -> "new personal best: " + e.text);
		line(GroupEvent.Type.WEEKLY_RECAP, "Weekly recap", (e, l) -> "this week: +" + BoostText.gp(e.number) + " XP, "
			+ e.text);
		line(GroupEvent.Type.SLAYER, "Slayer", (e, l) -> "slayer " + e.text + (e.number > 0 ? " (" + e.number + ")" : ""));
	}

	private static String deathValue(GroupEvent e, ItemLookup lookup)
	{
		return "lost about " + BoostText.gp(e.number) + " gp on death" + (e.location.isEmpty() ? "" : " at " + e.location)
			+ (e.detail.isEmpty() ? "" : " (" + e.detail + ")");
	}

	private EventText()
	{
	}

	private static void line(GroupEvent.Type type, String title, BiFunction<GroupEvent, ItemLookup, String> line)
	{
		TITLES.put(type, title);
		LINES.put(type, line);
	}

	/** What happened, without the member: "Vorkath dropped 1 x Draconic visage (1.2M)". */
	static String describe(GroupEvent e, ItemLookup lookup)
	{
		return LINES.get(e.type).apply(e, lookup);
	}

	/** "Alex: Vorkath dropped ..." */
	static String sentence(GroupEvent e, ItemLookup lookup)
	{
		return e.member + ": " + describe(e, lookup);
	}

	/** A short heading for an embed or a toast. */
	static String title(GroupEvent.Type type)
	{
		return TITLES.get(type);
	}

	/** Every type has words; a new type without them fails EventTextTest, not a player's client. */
	static boolean covers(GroupEvent.Type type)
	{
		return LINES.containsKey(type) && TITLES.containsKey(type);
	}

	/** "1 x Draconic visage (1.2M gp)": the items and their total value when any is known. */
	static String items(GroupEvent e, ItemLookup lookup)
	{
		long value = value(e.items, lookup);
		return StorageLog.describe(e.items, lookup) + (value > 0 ? " (" + BoostText.gp(value) + " gp)" : "");
	}

	/** Total value at the lookup's prices. */
	static long value(Map<Integer, Integer> items, ItemLookup lookup)
	{
		long total = 0;
		for (Map.Entry<Integer, Integer> e : items.entrySet())
		{
			total += lookup.price(e.getKey()) * e.getValue();
		}
		return total;
	}

	private static String kc(GroupEvent e)
	{
		return e.number > 0 ? ", kill count " + e.number : "";
	}

	private static String death(GroupEvent e, ItemLookup lookup)
	{
		StringBuilder out = new StringBuilder("died");
		if (!e.location.isEmpty())
		{
			out.append(" at ").append(e.location);
		}
		if (!e.text.isEmpty())
		{
			out.append(", killed by ").append(e.text);
		}
		if (e.world > 0)
		{
			out.append(" (world ").append(e.world).append(')');
		}
		if (e.number == GroupEvent.LIFE_LOST)
		{
			out.append(". The group lost a life.");
		}
		return out.toString();
	}

	private static String summary(GroupEvent e, ItemLookup lookup)
	{
		StringBuilder out = new StringBuilder("group storage:");
		if (!e.taken.isEmpty())
		{
			out.append(" took ").append(StorageLog.describe(e.taken, lookup));
		}
		if (!e.items.isEmpty())
		{
			out.append(e.taken.isEmpty() ? " deposited " : "; deposited ").append(StorageLog.describe(e.items, lookup));
		}
		return out.toString();
	}

	static String plain(String message)
	{
		return message == null ? "" : TAG.matcher(message).replaceAll("").trim();
	}

	/** A pet message, from a game message only. */
	static boolean isPet(String message)
	{
		String text = plain(message);
		for (String pet : PET_MESSAGES)
		{
			if (text.startsWith(pet))
			{
				return true;
			}
		}
		return false;
	}

	/** A kill or chest count line: the name ("Vorkath", "Barrows") and the count; null when not one. */
	static Count killCount(String message)
	{
		String text = plain(message);
		Matcher m = KILL_COUNT.matcher(text);
		if (!m.matches())
		{
			m = RAID_COUNT.matcher(text);
			if (!m.matches())
			{
				return null;
			}
		}
		try
		{
			return new Count(m.group(1).trim(), Long.parseLong(m.group(2).replace(",", "")));
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/** The quest name from the quest scroll's title, or the whole title when it reads some other way. */
	static String questName(String title)
	{
		String text = plain(title);
		Matcher m = QUEST.matcher(text);
		return m.matches() ? m.group(1).trim() : text;
	}

	static final class Count
	{
		final String name;
		final long count;

		Count(String name, long count)
		{
			this.name = name;
			this.count = count;
		}
	}
}
