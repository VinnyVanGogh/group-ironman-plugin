package com.vinnyvangogh.groupironman;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

/**
 * Settings for GIM Utilities.
 *
 * <p>The group name and every {@code keyName} below are frozen once a version
 * is live on the Plugin Hub: renaming one silently resets that setting for
 * every user who has it. Choose them as if they were permanent, because they
 * are.
 *
 * <p>The group is this plugin's own: not Build Guard's {@code build-guard}, so
 * the two never read or write each other's settings, and not
 * {@code groupironman}, which the Hub plugin "Group Iron Man" (Spedwards)
 * already uses. Every switch defaults to off: a fresh install sends nothing
 * and listens to nothing.
 */
@ConfigGroup(GroupIronmanConfig.GROUP)
public interface GroupIronmanConfig extends Config
{
	/** Frozen. See the class javadoc. */
	String GROUP = "gimutilities";

	@ConfigSection(
		name = "Group sync",
		description = "How GIM Utilities connects your group, without the Party plugin. Press 'Join group sync' in"
			+ " the panel once per session.",
		position = 0
	)
	String syncSection = "sync";

	@ConfigSection(
		name = "Group banks, storage and stats",
		description = "Group Ironman sharing over RuneLite Party. Each is off until you turn it on, and each only"
			+ " shows you other members' data while you share your own.",
		position = 1
	)
	String groupSection = "group";

	/** Frozen. */
	String KEY_GROUP_KEY = "groupKey";

	@ConfigItem(
		keyName = "groupSync",
		name = "Sync with my group",
		description = "Connect to your group's private party when you press 'Join group sync' in the panel. Off: solo,"
			+ " nothing is sent and the panel shows only what this client saw. Does nothing until there is a group key.",
		section = syncSection,
		position = 0
	)
	default boolean groupSync()
	{
		return true;
	}

	@ConfigItem(
		keyName = KEY_GROUP_KEY,
		name = "Group key",
		description = "Your group's secret key, 32 letters and digits. Create one in the panel, or paste the one a"
			+ " member shared. Party data reaches everyone with this key, so only share it in your group chat.",
		secret = true,
		section = syncSection,
		position = 1
	)
	default String groupKey()
	{
		return "";
	}

	@ConfigItem(
		keyName = "groupShareBank",
		name = "Share bank",
		description = "When you close your bank, send its item counts to your party, and show each member's last"
			+ " bank with when it was seen and its value. Kept on disk so it survives a restart.",
		section = groupSection,
		position = 0
	)
	default boolean groupShareBank()
	{
		return false;
	}

	@ConfigItem(
		keyName = "groupShareStorage",
		name = "Share group storage changes",
		description = "When you close group storage, tell your party what you took and deposited, and keep a log"
			+ " of the last 200 changes. Only members running GIM Utilities with this on are logged.",
		section = groupSection,
		position = 1
	)
	default boolean groupShareStorage()
	{
		return false;
	}

	@ConfigItem(
		keyName = "groupShareStats",
		name = "Share stats",
		description = "Send your real and boosted levels, HP, prayer, run energy, special attack, world and"
			+ " location, and show every member's, with online / last seen",
		section = groupSection,
		position = 2
	)
	default boolean groupShareStats()
	{
		return false;
	}

	@ConfigItem(
		keyName = "groupShareCarried",
		name = "Share inventory and equipment",
		description = "Send the item counts of your inventory and worn items, for the 'Who has it?' search",
		section = groupSection,
		position = 3
	)
	default boolean groupShareCarried()
	{
		return false;
	}

	@ConfigItem(
		keyName = "groupLowStock",
		name = "Storage minimums",
		description = "One per line or comma, 'Shark: 100'. A desktop notification when a group storage close"
			+ " leaves an item below its minimum. Empty means no alerts.",
		section = groupSection,
		position = 4
	)
	default String groupLowStock()
	{
		return "";
	}

	@ConfigItem(
		keyName = "groupShareExtras",
		name = "Share rune pouch, quiver, seed vault",
		description = "Send what is in your rune pouch and Dizana's quiver, and your seed vault as of the last time"
			+ " you opened it, and show every member's",
		section = groupSection,
		position = 5
	)
	default boolean groupShareExtras()
	{
		return false;
	}

	@ConfigItem(
		keyName = "groupShareDrops",
		name = "Share drops and collection log",
		description = "Send your NPC drops and new collection log slots, and keep every member's in a 'Since you"
			+ " last played' list. Collection log slots need the game's new-addition chat message turned on.",
		section = groupSection,
		position = 6
	)
	default boolean groupShareDrops()
	{
		return false;
	}

	@ConfigItem(
		keyName = "groupDropMinValue",
		name = "Drops worth at least (gp)",
		description = "Only items whose stack is worth at least this much are shared from a drop. 0 shares every item.",
		section = groupSection,
		position = 7
	)
	default int groupDropMinValue()
	{
		return 10_000;
	}

	@ConfigItem(
		keyName = "groupWorldMap",
		name = "Show members on world map",
		description = "Mark online members on the world map. Needs 'Share stats' on, here and on their side.",
		section = groupSection,
		position = 8
	)
	default boolean groupWorldMap()
	{
		return false;
	}

	@ConfigItem(
		keyName = "groupFindCommand",
		name = "::find chat command",
		description = "Type '::find dragon hunter wand' in the chatbox to see who has it and who last took it"
			+ " from group storage. The answer is shown only to you; nothing is sent to chat.",
		section = groupSection,
		position = 9
	)
	default boolean groupFindCommand()
	{
		return false;
	}

	@ConfigItem(
		keyName = "groupShareEvents",
		name = "Share group events",
		description = "Send your pets, deaths, levels, quests, logins and Barrows chests to your party, so members"
			+ " see them on their overlays. Drops and collection log slots go with 'Share drops and collection log'.",
		section = groupSection,
		position = 10
	)
	default boolean groupShareEvents()
	{
		return false;
	}

	@ConfigSection(
		name = "::find colours",
		description = "Colours in the ::find answer, so you can tell your bank, members' banks, group storage and"
			+ " what people carry apart at a glance.",
		position = 2,
		closedByDefault = true
	)
	String findSection = "findColours";

	@ConfigItem(keyName = "findQueryColour", name = "Your search", description = "The words you searched for.",
		section = findSection, position = 0)
	default Color findQueryColour()
	{
		return new Color(0xD0, 0x5A, 0x00);
	}

	@ConfigItem(keyName = "findColourItemNames", name = "Colour item names",
		description = "Also colour each item's name. Off keeps the answer calmer: only your search and the places"
			+ " are coloured.", section = findSection, position = 1)
	default boolean findColourItemNames()
	{
		return false;
	}

	@ConfigItem(keyName = "findItemColour", name = "Item name", description = "Each matching item's name, when"
		+ " Colour item names is on.", section = findSection, position = 6)
	default Color findItemColour()
	{
		return new Color(0x1E, 0x5A, 0xA8);
	}

	@ConfigItem(keyName = "findYourBankColour", name = "Your bank", description = "What is in your own bank.",
		section = findSection, position = 2)
	default Color findYourBankColour()
	{
		return new Color(0x1E, 0x8C, 0x1E);
	}

	@ConfigItem(keyName = "findMemberBankColour", name = "Members' banks",
		description = "What is in another member's bank.", section = findSection, position = 3)
	default Color findMemberBankColour()
	{
		return new Color(0x8A, 0x2B, 0xE2);
	}

	@ConfigItem(keyName = "findStorageColour", name = "Group storage",
		description = "What is in group storage, and who took or deposited it.", section = findSection, position = 4)
	default Color findStorageColour()
	{
		return new Color(0xB8, 0x86, 0x0B);
	}

	@ConfigItem(keyName = "findCarriedColour", name = "Carried and worn",
		description = "What someone has in their inventory, equipped, or in a pouch, quiver or seed vault.",
		section = findSection, position = 5)
	default Color findCarriedColour()
	{
		return new Color(0x00, 0x8B, 0x8B);
	}

	// ---- Group events: what counts. Both outputs read these. ----

	@ConfigSection(name = "Group events", description = "When a drop, storage visit or level is big enough to"
		+ " count as an event, for both the Discord webhook and the overlays.", position = 3, closedByDefault = true)
	String eventsSection = "events";

	@ConfigItem(keyName = "eventDropMinValue", name = "Valuable drop at least (gp)", description = "A drop counts"
		+ " when an item stack in it is worth at least this much.", section = eventsSection, position = 0)
	default int eventDropMinValue()
	{
		return 1_000_000;
	}

	@ConfigItem(keyName = "eventStorageMinValue", name = "Big storage move at least (gp)", description = "A group"
		+ " storage deposit or withdrawal counts when it is worth at least this much.", section = eventsSection,
		position = 1)
	default int eventStorageMinValue()
	{
		return 1_000_000;
	}

	@ConfigItem(keyName = "eventLevelMin", name = "Level ups from level", description = "Level ups below this"
		+ " level are not events.", section = eventsSection, position = 2)
	@Range(min = 1, max = 99)
	default int eventLevelMin()
	{
		return 1;
	}

	@ConfigItem(keyName = "eventBackAfterDays", name = "Back after (days)", description = "Logging in after at"
		+ " least this many days away is an event.", section = eventsSection, position = 3)
	@Range(min = 1, max = 365)
	default int eventBackAfterDays()
	{
		return 7;
	}

	@ConfigItem(keyName = "eventUntradeableIgnore", name = "Untradeables to ignore", description = "Untradeable"
		+ " drops whose name starts with one of these (comma separated) are not uniques.", section = eventsSection,
		position = 4)
	default String eventUntradeableIgnore()
	{
		return EventDetect.DEFAULT_UNTRADEABLE_IGNORE;
	}

	// ---- Discord webhook ----

	@ConfigSection(name = "Discord webhook", description = "Post your own events to a Discord channel. Each member's"
		+ " client posts only that member's events, so nothing is posted twice.", position = 4,
		closedByDefault = true)
	String discordSection = "discord";

	@ConfigItem(keyName = "discordEnabled", name = "Post to Discord", description = "Post the events ticked under"
		+ " 'Discord: which events' to the webhook below.", warning = DiscordWebhook.WARNING,
		section = discordSection, position = 0)
	default boolean discordEnabled()
	{
		return false;
	}

	@ConfigItem(keyName = "discordWebhookUrl", name = "Discord webhook URL", description = "From Discord: channel"
		+ " settings, Integrations, Webhooks. Must start https://discord.com/api/webhooks/.", secret = true,
		section = discordSection, position = 1)
	default String discordWebhookUrl()
	{
		return "";
	}

	@ConfigItem(keyName = "discordDropsUrl", name = "Drops webhook (optional)", description = "Drops, pets,"
		+ " collection log and uniques go here instead, when set.", secret = true, section = discordSection,
		position = 2)
	default String discordDropsUrl()
	{
		return "";
	}

	@ConfigItem(keyName = "discordDeathsUrl", name = "Deaths webhook (optional)", description = "Deaths go here"
		+ " instead, when set.", secret = true, section = discordSection, position = 3)
	default String discordDeathsUrl()
	{
		return "";
	}

	@ConfigItem(keyName = "discordStorageUrl", name = "Storage webhook (optional)", description = "Group storage"
		+ " events go here instead, when set.", secret = true, section = discordSection, position = 4)
	default String discordStorageUrl()
	{
		return "";
	}

	@ConfigItem(keyName = "discordProgressUrl", name = "Progress webhook (optional)", description = "Levels, XP"
		+ " and quests go here instead, when set.", secret = true, section = discordSection, position = 5)
	default String discordProgressUrl()
	{
		return "";
	}

	@ConfigItem(keyName = "discordGroupUrl", name = "Group webhook (optional)", description = "Online, offline and"
		+ " 'back after' go here instead, when set.", secret = true, section = discordSection, position = 6)
	default String discordGroupUrl()
	{
		return "";
	}

	@ConfigItem(keyName = "discordActivitiesUrl", name = "Activities webhook (optional)", description = "Barrows"
		+ " chests go here instead, when set.", secret = true, section = discordSection, position = 7)
	default String discordActivitiesUrl()
	{
		return "";
	}

	@ConfigItem(keyName = "discordStyle", name = "Message style", description = "Rich embeds with item icons and a"
		+ " colour per category, or plain text.", section = discordSection, position = 8)
	default DiscordStyle discordStyle()
	{
		return DiscordStyle.EMBED;
	}

	@ConfigItem(keyName = "discordScreenshot", name = "Attach a screenshot", description = "Attach a screenshot of"
		+ " the game to each post.", section = discordSection, position = 9)
	default boolean discordScreenshot()
	{
		return false;
	}

	@ConfigItem(keyName = "discordMention", name = "Mention for big events", description = "Put in front of pets,"
		+ " 99s, maxing and a Hardcore group life lost, e.g. <@&role id> or <@user id>. Empty mentions nobody.",
		section = discordSection, position = 10)
	default String discordMention()
	{
		return "";
	}

	@ConfigItem(keyName = "discordQuietStart", name = "Quiet hours from", description = "Hour (0-23) posts start"
		+ " waiting; they are sent when quiet hours end. Same as 'until' means no quiet hours.",
		section = discordSection, position = 11)
	@Range(max = 23)
	default int discordQuietStart()
	{
		return 0;
	}

	@ConfigItem(keyName = "discordQuietEnd", name = "Quiet hours until", description = "Hour (0-23) quiet hours"
		+ " end.", section = discordSection, position = 12)
	@Range(max = 23)
	default int discordQuietEnd()
	{
		return 0;
	}

	@ConfigSection(name = "Discord: which events", description = "Each event is its own switch, all off until you"
		+ " tick it.", position = 5, closedByDefault = true)
	String discordEventsSection = "discordEvents";

	@ConfigItem(keyName = "discordValuableDrop", name = "Valuable drop", description = "A drop worth at least the"
		+ " 'Valuable drop' value, with the monster and kill count.", section = discordEventsSection, position = 0)
	default boolean discordValuableDrop()
	{
		return false;
	}

	@ConfigItem(keyName = "discordPet", name = "Pet", description = "You got a pet.", section = discordEventsSection,
		position = 1)
	default boolean discordPet()
	{
		return false;
	}

	@ConfigItem(keyName = "discordCollectionLog", name = "New collection log slot", description = "Needs the game's"
		+ " new-addition chat message on.", section = discordEventsSection, position = 2)
	default boolean discordCollectionLog()
	{
		return false;
	}

	@ConfigItem(keyName = "discordUntradeableUnique", name = "Untradeable unique", description = "An untradeable"
		+ " item from a drop, other than those ignored under Group events.", section = discordEventsSection,
		position = 3)
	default boolean discordUntradeableUnique()
	{
		return false;
	}

	@ConfigItem(keyName = "discordDeath", name = "Death", description = "Where, the killer if known, the world, and"
		+ " a Hardcore group life lost.", section = discordEventsSection, position = 4)
	default boolean discordDeath()
	{
		return false;
	}

	@ConfigItem(keyName = "discordStorageDeposit", name = "Big storage deposit", description = "A deposit worth at"
		+ " least the 'Big storage move' value.", section = discordEventsSection, position = 5)
	default boolean discordStorageDeposit()
	{
		return false;
	}

	@ConfigItem(keyName = "discordStorageWithdrawal", name = "Big storage withdrawal", description = "A withdrawal"
		+ " worth at least the 'Big storage move' value.", section = discordEventsSection, position = 6)
	default boolean discordStorageWithdrawal()
	{
		return false;
	}

	@ConfigItem(keyName = "discordStorageSummary", name = "Storage visit summary", description = "Everything you"
		+ " took and deposited in one group storage visit.", section = discordEventsSection, position = 7)
	default boolean discordStorageSummary()
	{
		return false;
	}

	@ConfigItem(keyName = "discordStorageMinimum", name = "Storage minimum hit", description = "Your visit left an"
		+ " item below its 'Storage minimums' value.", section = discordEventsSection, position = 8)
	default boolean discordStorageMinimum()
	{
		return false;
	}

	@ConfigItem(keyName = "discordStorageLast", name = "Took the last one", description = "You took the last of an"
		+ " item from group storage.", section = discordEventsSection, position = 9)
	default boolean discordStorageLast()
	{
		return false;
	}

	@ConfigItem(keyName = "discordLevelUp", name = "Level up", description = "From the 'Level ups from level'"
		+ " value.", section = discordEventsSection, position = 10)
	default boolean discordLevelUp()
	{
		return false;
	}

	@ConfigItem(keyName = "discordMaxed", name = "99s and max total", description = "A skill reaches 99, or every"
		+ " skill is 99.", section = discordEventsSection, position = 11)
	default boolean discordMaxed()
	{
		return false;
	}

	@ConfigItem(keyName = "discordXpMilestone", name = "XP milestones", description = "1M, 10M and 200M XP in a"
		+ " skill.", section = discordEventsSection, position = 12)
	default boolean discordXpMilestone()
	{
		return false;
	}

	@ConfigItem(keyName = "discordCombatLevel", name = "Combat level up", description = "Your combat level went up.",
		section = discordEventsSection, position = 13)
	default boolean discordCombatLevel()
	{
		return false;
	}

	@ConfigItem(keyName = "discordTotalLevel", name = "Total level milestones", description = "Every 100 total"
		+ " levels.", section = discordEventsSection, position = 14)
	default boolean discordTotalLevel()
	{
		return false;
	}

	@ConfigItem(keyName = "discordQuest", name = "Quest complete", description = "The quest and your quest points.",
		section = discordEventsSection, position = 15)
	default boolean discordQuest()
	{
		return false;
	}

	@ConfigItem(keyName = "discordQuestPoints", name = "Quest point milestones", description = "Every 25 quest"
		+ " points, and every quest done.", section = discordEventsSection, position = 16)
	default boolean discordQuestPoints()
	{
		return false;
	}

	@ConfigItem(keyName = "discordOnline", name = "Online and offline", description = "Login and logout with the"
		+ " world. A logout followed by a login within 10 minutes (a hop) is not posted.",
		section = discordEventsSection, position = 17)
	default boolean discordOnline()
	{
		return false;
	}

	@ConfigItem(keyName = "discordBack", name = "Back after a break", description = "Logging in after the 'Back"
		+ " after' days.", section = discordEventsSection, position = 18)
	default boolean discordBack()
	{
		return false;
	}

	@ConfigItem(keyName = "discordBarrows", name = "Barrows chest", description = "The chest's loot, chest count and"
		+ " set progress.", section = discordEventsSection, position = 19)
	default boolean discordBarrows()
	{
		return false;
	}

	// ---- Overlays ----

	@ConfigSection(name = "Overlays", description = "A banner for each event, yours and your group's, and a group"
		+ " box with who is online and the latest events.", position = 6, closedByDefault = true)
	String overlaySection = "overlays";

	@ConfigItem(keyName = "overlayDurationSeconds", name = "Banner time (seconds)", description = "How long each"
		+ " event banner stays.", section = overlaySection, position = 0)
	@Range(min = 2, max = 60)
	default int overlayDurationSeconds()
	{
		return 8;
	}

	@ConfigItem(keyName = "overlayPosition", name = "Banner position", description = "Where banners appear. You can"
		+ " still drag them with Alt held.", section = overlaySection, position = 1)
	default ToastPosition overlayPosition()
	{
		return ToastPosition.TOP_CENTER;
	}

	@ConfigItem(keyName = "overlayMaxStacked", name = "Most banners at once", description = "Older banners make way"
		+ " for newer ones.", section = overlaySection, position = 2)
	@Range(min = 1, max = 10)
	default int overlayMaxStacked()
	{
		return 3;
	}

	@ConfigItem(keyName = "overlayGroupBox", name = "Group box", description = "A box with who is online, their"
		+ " world, and the latest events.", section = overlaySection, position = 3)
	default boolean overlayGroupBox()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayGroupBoxEvents", name = "Group box events", description = "How many of the"
		+ " latest events the group box lists.", section = overlaySection, position = 4)
	@Range(min = 1, max = 20)
	default int overlayGroupBoxEvents()
	{
		return 5;
	}

	@ConfigSection(name = "Overlays: which events", description = "Each event is its own switch, all off until you"
		+ " tick it. Members' events need 'Share group events' (or drops) on, here and on their side.",
		position = 7, closedByDefault = true)
	String overlayEventsSection = "overlayEvents";

	@ConfigItem(keyName = "overlayValuableDrop", name = "Valuable drop", description = "A drop worth at least the"
		+ " 'Valuable drop' value.", section = overlayEventsSection, position = 0)
	default boolean overlayValuableDrop()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayPet", name = "Pet", description = "A pet.", section = overlayEventsSection,
		position = 1)
	default boolean overlayPet()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayCollectionLog", name = "New collection log slot", description = "A new collection"
		+ " log slot.", section = overlayEventsSection, position = 2)
	default boolean overlayCollectionLog()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayUntradeableUnique", name = "Untradeable unique", description = "An untradeable"
		+ " unique from a drop.", section = overlayEventsSection, position = 3)
	default boolean overlayUntradeableUnique()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayDeath", name = "Death", description = "A member died.",
		section = overlayEventsSection, position = 4)
	default boolean overlayDeath()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayStorageDeposit", name = "Big storage deposit", description = "A big group storage"
		+ " deposit.", section = overlayEventsSection, position = 5)
	default boolean overlayStorageDeposit()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayStorageWithdrawal", name = "Big storage withdrawal", description = "A big group"
		+ " storage withdrawal.", section = overlayEventsSection, position = 6)
	default boolean overlayStorageWithdrawal()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayStorageSummary", name = "Storage visit summary", description = "Each group storage"
		+ " visit.", section = overlayEventsSection, position = 7)
	default boolean overlayStorageSummary()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayStorageMinimum", name = "Storage minimum hit", description = "A storage minimum"
		+ " was hit.", section = overlayEventsSection, position = 8)
	default boolean overlayStorageMinimum()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayStorageLast", name = "Took the last one", description = "Someone took the last of"
		+ " an item.", section = overlayEventsSection, position = 9)
	default boolean overlayStorageLast()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayLevelUp", name = "Level up", description = "A level up.",
		section = overlayEventsSection, position = 10)
	default boolean overlayLevelUp()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayMaxed", name = "99s and max total", description = "A 99 or max total.",
		section = overlayEventsSection, position = 11)
	default boolean overlayMaxed()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayXpMilestone", name = "XP milestones", description = "1M, 10M and 200M XP.",
		section = overlayEventsSection, position = 12)
	default boolean overlayXpMilestone()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayCombatLevel", name = "Combat level up", description = "A combat level up.",
		section = overlayEventsSection, position = 13)
	default boolean overlayCombatLevel()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayTotalLevel", name = "Total level milestones", description = "Every 100 total"
		+ " levels.", section = overlayEventsSection, position = 14)
	default boolean overlayTotalLevel()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayQuest", name = "Quest complete", description = "A quest completed.",
		section = overlayEventsSection, position = 15)
	default boolean overlayQuest()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayQuestPoints", name = "Quest point milestones", description = "Every 25 quest"
		+ " points, and every quest done.", section = overlayEventsSection, position = 16)
	default boolean overlayQuestPoints()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayOnline", name = "Online", description = "A member logged in, with the world.",
		section = overlayEventsSection, position = 17)
	default boolean overlayOnline()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayBack", name = "Back after a break", description = "A member is back after the"
		+ " 'Back after' days.", section = overlayEventsSection, position = 18)
	default boolean overlayBack()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayBarrows", name = "Barrows chest", description = "A Barrows chest.",
		section = overlayEventsSection, position = 19)
	default boolean overlayBarrows()
	{
		return false;
	}
}
