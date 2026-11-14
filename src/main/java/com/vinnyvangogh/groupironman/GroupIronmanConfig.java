package com.vinnyvangogh.groupironman;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

/**
 * Settings for GIM Utilities.
 *
 * <p>The group name and every {@code keyName} below are frozen once a version
 * is live on the Plugin Hub: renaming one silently resets that setting for
 * every user who has it. Choose them as if they were permanent, because they
 * are. Sections, positions, display names and descriptions are free to change.
 *
 * <p>The group is this plugin's own: not Build Guard's {@code build-guard}, so
 * the two never read or write each other's settings, and not
 * {@code groupironman}, which the Hub plugin "Group Iron Man" (Spedwards)
 * already uses. Every switch defaults to off: a fresh install sends nothing
 * and listens to nothing.
 *
 * <p>The panel has exactly eight sections, in this order: Group, Discord:
 * connection, Discord: what to post, Discord: how to post, Discord: daily
 * digest, In-game, Thresholds, Advanced. {@code ConfigLayoutTest} holds the
 * layout to that.
 */
@ConfigGroup(GroupIronmanConfig.GROUP)
public interface GroupIronmanConfig extends Config
{
	/** Frozen. See the class javadoc. */
	String GROUP = "gimutilities";

	/** Frozen. */
	String KEY_GROUP_KEY = "groupKey";
	String KEY_OPT_OUT_ALL = "eventOptOutAll";
	String KEY_OPT_OUT_TYPES = "eventOptOutTypes";
	String KEY_MUTE_TYPES = "overlayMuteTypes";

	/** Frozen. Written by the plugin once per version (UpdateNotes). */
	String KEY_LAST_SEEN_VERSION = "lastSeenVersion";

	/**
	 * The {@code @Units} on every gp number box: no suffix at all. RuneLite's number box is a fixed 6 columns
	 * wide whatever the label, and its plain formatter writes thousands commas, so 1,000,000 showed as
	 * "1,000,00". A {@code @Units} formatter writes {@code value + units} with no commas instead, so 1000000
	 * fits. Display only: the saved value and its key are untouched. ConfigFitTest holds every row to fit.
	 */
	String GP_PLAIN = "";

	// ---- The eight sections ----
	// Every section but Group starts closed, so a first install shows all eight names at a glance (Board 2026-11-11).

	@ConfigSection(name = "Group", description = "Join your group and choose what you share with them. Nothing is"
		+ " shared until you turn it on, and you only see members' data for what you share yourself.",
		position = 0)
	String groupSection = "sectionGroup";

	@ConfigSection(name = "Discord: connection", description = "Where your Discord posts go: the webhooks, and the"
		+ " name each post shows. Each member's client posts only that member's own events, so nothing is posted"
		+ " twice.", position = 1, closedByDefault = true)
	String discordConnectionSection = "sectionDiscordConnection";

	@ConfigSection(name = "Discord: what to post", description = "One switch per event. Each is off until you tick"
		+ " it.", position = 2, closedByDefault = true)
	String discordEventsSection = "sectionDiscordWhat";

	@ConfigSection(name = "Discord: how to post", description = "How posts look and when they go: online status,"
		+ " combining level-ups, screenshots, mentions and quiet hours.", position = 3, closedByDefault = true)
	String discordHowSection = "sectionDiscordHow";

	@ConfigSection(name = "Discord: daily digest", description = "Tick a category to add a once-a-day summary of it,"
		+ " sent while you are logged in. Instant posts still go out as set above; the digest never delays"
		+ " them.", position = 4, closedByDefault = true)
	String digestSection = "sectionDigest";

	@ConfigSection(name = "In-game", description = "Event banners, the group box, world map markers and the ::find"
		+ " command, and which events show as a banner in game. Members' events need 'Share group events' (or"
		+ " drops) on, here and on their side.", position = 5, closedByDefault = true)
	String inGameSection = "sectionInGame";

	@ConfigSection(name = "Thresholds", description = "What is big enough to count as an event, for both Discord"
		+ " and in game: values in gp, levels, kill counts and days away.", position = 6, closedByDefault = true)
	String thresholdsSection = "sectionThresholds";

	@ConfigSection(name = "Advanced", description = "Rarely needed: the colours in the ::find answer.",
		position = 7, closedByDefault = true)
	String advancedSection = "sectionAdvanced";

	// ---- 1. Group ----

	@ConfigItem(
		keyName = "groupSync",
		name = "Sync with my group",
		description = "Connect to your group's private party when you press 'Join group sync' in the panel. Off: solo,"
			+ " nothing is sent and the panel shows only what this client saw. Does nothing until there is a group key.",
		section = groupSection,
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
		section = groupSection,
		position = 1
	)
	default String groupKey()
	{
		return "";
	}

	@ConfigItem(
		keyName = "groupShareBank",
		name = "Share bank",
		description = "When you close your bank, send its item counts to your group, and show each member's last"
			+ " bank with when it was seen and its value. Kept on disk so it survives a restart.",
		section = groupSection,
		position = 2
	)
	default boolean groupShareBank()
	{
		return false;
	}

	@ConfigItem(
		keyName = "groupShareStorage",
		name = "Share group storage changes",
		description = "When you close group storage, tell your group what you took and deposited, and keep a log"
			+ " of the last 200 changes. Only members running GIM Utilities with this on are logged.",
		section = groupSection,
		position = 3
	)
	default boolean groupShareStorage()
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
		keyName = "groupShareStats",
		name = "Share stats",
		description = "Send your real and boosted levels, HP, prayer, run energy, special attack, world and"
			+ " location, and show every member's, with online / last seen.",
		section = groupSection,
		position = 5
	)
	default boolean groupShareStats()
	{
		return false;
	}

	@ConfigItem(
		keyName = "groupShareCarried",
		name = "Share inventory and equipment",
		description = "Send the item counts of your inventory and worn items, for the 'Who has it?' search.",
		section = groupSection,
		position = 6
	)
	default boolean groupShareCarried()
	{
		return false;
	}

	@ConfigItem(
		keyName = "groupShareExtras",
		name = "Share pouch, quiver, vault",
		description = "Send what is in your rune pouch and Dizana's quiver, and your seed vault as of the last time"
			+ " you opened it, and show every member's.",
		section = groupSection,
		position = 7
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
		position = 8
	)
	default boolean groupShareDrops()
	{
		return false;
	}

	@ConfigItem(
		keyName = "groupShareEvents",
		name = "Share group events",
		description = "Send your pets, deaths, levels, quests, logins and Barrows chests to your group, so members"
			+ " see them as banners. Drops and collection log slots go with 'Share drops and collection log'.",
		section = groupSection,
		position = 9
	)
	default boolean groupShareEvents()
	{
		return false;
	}

	// ---- 2. Discord: connection ----

	@ConfigItem(keyName = "discordEnabled", name = "Post to Discord", description = "Post the events ticked under"
		+ " 'Discord: what to post' to the webhook below.", warning = DiscordWebhook.WARNING,
		section = discordConnectionSection, position = 0)
	default boolean discordEnabled()
	{
		return false;
	}

	@ConfigItem(keyName = "discordWebhookUrl", name = "Discord webhook URL", description = "From Discord: channel"
		+ " settings, Integrations, Webhooks. Must start https://discord.com/api/webhooks/. Every post goes here"
		+ " unless its category has its own webhook below.", secret = true, section = discordConnectionSection,
		position = 1)
	default String discordWebhookUrl()
	{
		return "";
	}

	@ConfigItem(keyName = "discordDropsUrl", name = "Drops webhook (optional)", description = "Drops, pets,"
		+ " collection log and uniques go here instead, when set.", secret = true, section = discordConnectionSection,
		position = 2)
	default String discordDropsUrl()
	{
		return "";
	}

	@ConfigItem(keyName = "discordDeathsUrl", name = "Deaths webhook (optional)", description = "Deaths go here"
		+ " instead, when set.", secret = true, section = discordConnectionSection, position = 3)
	default String discordDeathsUrl()
	{
		return "";
	}

	@ConfigItem(keyName = "discordStorageUrl", name = "Storage webhook (optional)", description = "Group storage"
		+ " posts go here instead, when set.", secret = true, section = discordConnectionSection, position = 4)
	default String discordStorageUrl()
	{
		return "";
	}

	@ConfigItem(keyName = "discordProgressUrl", name = "Progress webhook (optional)", description = "Levels, XP"
		+ " and quests go here instead, when set.", secret = true, section = discordConnectionSection, position = 5)
	default String discordProgressUrl()
	{
		return "";
	}

	@ConfigItem(keyName = "discordGroupUrl", name = "Group webhook (optional)", description = "Online, offline, the"
		+ " live board and 'back after a break' go here instead, when set.", secret = true,
		section = discordConnectionSection, position = 6)
	default String discordGroupUrl()
	{
		return "";
	}

	@ConfigItem(keyName = "discordActivitiesUrl", name = "Activities webhook (optional)", description = "Barrows"
		+ " chests go here instead, when set.", secret = true, section = discordConnectionSection, position = 7)
	default String discordActivitiesUrl()
	{
		return "";
	}

	@ConfigItem(keyName = "discordPosterName", name = "Poster name", description = "Whose name posts show. Webhook's"
		+ " own: the name and avatar set on each webhook in Discord. GIM Utilities: that name. Custom: the names"
		+ " below.",
		section = discordConnectionSection, position = 8)
	default DiscordPosterName discordPosterName()
	{
		return DiscordPosterName.WEBHOOK;
	}

	@ConfigItem(keyName = "discordNameMain", name = "Custom name: main", description = "With Poster name on Custom,"
		+ " posts to the main webhook use this name, unless their category has its own below. Blank uses the"
		+ " webhook's own. At most 80 characters, without 'discord', 'clyde', @, #, : or ```, and not 'everyone'"
		+ " or 'here'.", section = discordConnectionSection, position = 9)
	default String discordNameMain()
	{
		return "";
	}

	@ConfigItem(keyName = "discordNameDrops", name = "Custom name: Drops", description = "With Poster name on"
		+ " Custom, drop posts use this name. Blank uses the webhook's own.", section = discordConnectionSection,
		position = 10)
	default String discordNameDrops()
	{
		return "";
	}

	@ConfigItem(keyName = "discordNameDeaths", name = "Custom name: Deaths", description = "With Poster name on"
		+ " Custom, death posts use this name. Blank uses the webhook's own.", section = discordConnectionSection,
		position = 11)
	default String discordNameDeaths()
	{
		return "";
	}

	@ConfigItem(keyName = "discordNameStorage", name = "Custom name: Storage", description = "With Poster name on"
		+ " Custom, group storage posts use this name. Blank uses the webhook's own.",
		section = discordConnectionSection, position = 12)
	default String discordNameStorage()
	{
		return "";
	}

	@ConfigItem(keyName = "discordNameProgress", name = "Custom name: Progress", description = "With Poster name on"
		+ " Custom, level, XP and quest posts use this name. Blank uses the webhook's own.",
		section = discordConnectionSection, position = 13)
	default String discordNameProgress()
	{
		return "";
	}

	@ConfigItem(keyName = "discordNameGroup", name = "Custom name: Group", description = "With Poster name on"
		+ " Custom, online, offline, live board and 'back after a break' posts use this name. Blank uses the"
		+ " webhook's own.", section = discordConnectionSection, position = 14)
	default String discordNameGroup()
	{
		return "";
	}

	@ConfigItem(keyName = "discordNameActivities", name = "Custom name: Activities", description = "With Poster name"
		+ " on Custom, Barrows posts use this name. Blank uses the webhook's own.", section = discordConnectionSection,
		position = 15)
	default String discordNameActivities()
	{
		return "";
	}

	@ConfigItem(keyName = KEY_OPT_OUT_ALL, name = "Don't post my events", description = "Yours alone, whatever"
		+ " your group's settings say: none of your events go to Discord. Per-kind choices are in the panel's"
		+ " Settings tab. Your group's leader and editors can see this choice.", section = discordConnectionSection,
		position = 16)
	default boolean eventOptOutAll()
	{
		return false;
	}

	// ---- 3. Discord: what to post. Drops, deaths, storage, progress, group, activities. ----

	@ConfigItem(keyName = "discordValuableDrop", name = "Valuable drop", description = "A drop worth at least the"
		+ " 'Valuable drop' value under Thresholds, with the monster and kill count.", section = discordEventsSection,
		position = 0)
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
		+ " item from a drop, other than those ignored under Thresholds.", section = discordEventsSection,
		position = 3)
	default boolean discordUntradeableUnique()
	{
		return false;
	}

	@ConfigItem(keyName = "discordRaidUnique", name = "Raid unique", description = "Your purple in Chambers, Theatre"
		+ " or Tombs, with kill count. Team members outside your group are only counted.",
		section = discordEventsSection, position = 4)
	default boolean discordRaidUnique()
	{
		return false;
	}

	@ConfigItem(keyName = "discordClue", name = "Clue reward", description = "A clue reward worth at least the"
		+ " 'Clue reward' value under Thresholds, with clue counts by tier.", section = discordEventsSection,
		position = 5)
	default boolean discordClue()
	{
		return false;
	}

	@ConfigItem(keyName = "discordDryStreak", name = "Dry streak", description = "Kills past a boss's unique rate."
		+ " Vorkath and Zulrah for now.", section = discordEventsSection, position = 6)
	default boolean discordDryStreak()
	{
		return false;
	}

	@ConfigItem(keyName = "discordDeath", name = "Death", description = "Where, the killer if known, the world, and"
		+ " a Hardcore group life lost.", section = discordEventsSection, position = 7)
	default boolean discordDeath()
	{
		return false;
	}

	@ConfigItem(keyName = "discordDeathValue", name = "Value lost on death", description = "What a death cost, and"
		+ " where the gravestone is.", section = discordEventsSection, position = 8)
	default boolean discordDeathValue()
	{
		return false;
	}

	@ConfigItem(keyName = "discordGravestone", name = "Gravestone about to expire", description = "Before the"
		+ " gravestone's items go to Death's Office.", section = discordEventsSection, position = 9)
	default boolean discordGravestone()
	{
		return false;
	}

	@ConfigItem(keyName = "discordHardcoreLife", name = "Hardcore life lost", description = "The group lost a life,"
		+ " and how many are left.", section = discordEventsSection, position = 10)
	default boolean discordHardcoreLife()
	{
		return false;
	}

	@ConfigItem(keyName = "discordWilderness", name = "Wilderness and skull", description = "You entered the"
		+ " Wilderness or got skulled.", section = discordEventsSection, position = 11)
	default boolean discordWilderness()
	{
		return false;
	}

	@ConfigItem(keyName = "discordStorageDeposit", name = "Big storage deposit", description = "A deposit worth at"
		+ " least the 'Big storage' value under Thresholds.", section = discordEventsSection, position = 12)
	default boolean discordStorageDeposit()
	{
		return false;
	}

	@ConfigItem(keyName = "discordStorageWithdrawal", name = "Big storage withdrawal", description = "A withdrawal"
		+ " worth at least the 'Big storage' value under Thresholds.", section = discordEventsSection,
		position = 13)
	default boolean discordStorageWithdrawal()
	{
		return false;
	}

	@ConfigItem(keyName = "discordStorageSummary", name = "Storage visit summary", description = "Everything you"
		+ " took and deposited in one group storage visit.", section = discordEventsSection, position = 14)
	default boolean discordStorageSummary()
	{
		return false;
	}

	@ConfigItem(keyName = "discordStorageMinimum", name = "Storage minimum hit", description = "Your visit left an"
		+ " item below its 'Storage minimums' value.", section = discordEventsSection, position = 15)
	default boolean discordStorageMinimum()
	{
		return false;
	}

	@ConfigItem(keyName = "discordStorageLast", name = "Took the last one", description = "You took the last of an"
		+ " item from group storage.", section = discordEventsSection, position = 16)
	default boolean discordStorageLast()
	{
		return false;
	}

	@ConfigItem(keyName = "discordLevelUp", name = "Level up", description = "A skill level up, from the 'Level ups"
		+ " from level' value under Thresholds.", section = discordEventsSection, position = 17)
	default boolean discordLevelUp()
	{
		return false;
	}

	@ConfigItem(keyName = "discordMaxed", name = "99s and max total", description = "A skill reaches 99, or every"
		+ " skill is 99.", section = discordEventsSection, position = 18)
	default boolean discordMaxed()
	{
		return false;
	}

	@ConfigItem(keyName = "discordXpMilestone", name = "XP milestones", description = "1M, 10M and 200M XP in a"
		+ " skill.", section = discordEventsSection, position = 19)
	default boolean discordXpMilestone()
	{
		return false;
	}

	@ConfigItem(keyName = "discordCombatLevel", name = "Combat level up", description = "Your combat level went up.",
		section = discordEventsSection, position = 20)
	default boolean discordCombatLevel()
	{
		return false;
	}

	@ConfigItem(keyName = "discordTotalLevel", name = "Total level milestones", description = "Every 100 total"
		+ " levels.", section = discordEventsSection, position = 21)
	default boolean discordTotalLevel()
	{
		return false;
	}

	@ConfigItem(keyName = "discordQuest", name = "Quest complete", description = "The quest and your quest points.",
		section = discordEventsSection, position = 22)
	default boolean discordQuest()
	{
		return false;
	}

	@ConfigItem(keyName = "discordQuestPoints", name = "Quest point milestones", description = "Every 25 quest"
		+ " points, and every quest done.", section = discordEventsSection, position = 23)
	default boolean discordQuestPoints()
	{
		return false;
	}

	@ConfigItem(keyName = "discordDiary", name = "Achievement diary tier", description = "A diary tier completed.",
		section = discordEventsSection, position = 24)
	default boolean discordDiary()
	{
		return false;
	}

	@ConfigItem(keyName = "discordCombatTask", name = "Combat achievement", description = "A combat task and its tier.",
		section = discordEventsSection, position = 25)
	default boolean discordCombatTask()
	{
		return false;
	}

	@ConfigItem(keyName = "discordKcMilestone", name = "Kill count milestone", description = "Every 'KC"
		+ " milestone every' kills of a boss (under Thresholds).", section = discordEventsSection, position = 26)
	default boolean discordKcMilestone()
	{
		return false;
	}

	@ConfigItem(keyName = "discordPersonalBest", name = "New personal best", description = "A new boss or raid best"
		+ " time.", section = discordEventsSection, position = 27)
	default boolean discordPersonalBest()
	{
		return false;
	}

	@ConfigItem(keyName = "discordSlayer", name = "Slayer task", description = "A task done, and a new task.",
		section = discordEventsSection, position = 28)
	default boolean discordSlayer()
	{
		return false;
	}

	@ConfigItem(keyName = "discordFarming", name = "Farming runs ready", description = "Your herb or tree patches have"
		+ " finished growing, going by when you last saw them.", section = discordEventsSection, position = 29)
	default boolean discordFarming()
	{
		return false;
	}

	@ConfigItem(keyName = "discordBirdhouse", name = "Bird houses, Hespori", description = "Your bird houses or Hespori"
		+ " have finished, going by when you last saw them.", section = discordEventsSection, position = 30)
	default boolean discordBirdhouse()
	{
		return false;
	}

	@ConfigItem(keyName = "discordBack", name = "Back after a break", description = "You logged in after the 'Back"
		+ " after (days)' value under Thresholds.", section = discordEventsSection, position = 31)
	default boolean discordBack()
	{
		return false;
	}

	@ConfigItem(keyName = "discordWeeklyRecap", name = "Weekly recap", description = "Each Monday, your week: XP,"
		+ " drops, kills and storage visits.", section = discordEventsSection, position = 32)
	default boolean discordWeeklyRecap()
	{
		return false;
	}

	@ConfigItem(keyName = "discordBarrows", name = "Barrows chest", description = "The chest's loot, chest count and"
		+ " set progress.", section = discordEventsSection, position = 33)
	default boolean discordBarrows()
	{
		return false;
	}

	// ---- 4. Discord: how to post ----

	@ConfigItem(keyName = "discordOnlineStatus", name = "Online status", description = "Posts: a post for each"
		+ " login and logout, with the world and how long you played (a hop, or a relog within the offline grace, is"
		+ " not posted). Live board: one message in the Group channel listing each member online or offline,"
		+ " edited as members log in, out or hop. Members without this plugin are read from the game's Group"
		+ " channel and marked '(no plugin)'; their logins are posted by one plugin user only. Only one member"
		+ " sharing a Group webhook should turn it on, or the channel gets one board each. Off: neither.",
		section = discordHowSection, position = 0)
	default OnlineStatus discordOnlineStatus()
	{
		return OnlineStatus.POSTS;
	}

	@Range(max = 10)
	@Units(Units.MINUTES)
	@ConfigItem(keyName = "discordOfflineGrace", name = "Offline grace", description = "Minutes a logout waits"
		+ " before it is posted. Logging back in within it posts nothing. At most one online and offline per 10"
		+ " minutes.", section = discordHowSection, position = 1)
	default int discordOfflineGrace()
	{
		return 2;
	}

	@ConfigItem(keyName = "discordCombineLevels", name = "Combine level-ups", description = "Minutes to"
		+ " combine level-ups for. More levels in the same skill within this many minutes of its first post edit"
		+ " that post ('Prayer 23 → 25 (+2)') instead of posting again. 99s, total and combat levels always post."
		+ " 0 turns it off.",
		section = discordHowSection, position = 2)
	@Range(max = 240)
	@Units(Units.MINUTES)
	default int discordCombineLevels()
	{
		return 30;
	}

	@ConfigItem(keyName = "discordStyle", name = "Message style", description = "Rich embeds with item icons and a"
		+ " colour per category, or plain text.", section = discordHowSection, position = 3)
	default DiscordStyle discordStyle()
	{
		return DiscordStyle.EMBED;
	}

	@ConfigItem(keyName = "discordScreenshot", name = "Attach a screenshot", description = "Attach a screenshot of"
		+ " the game to each post.", section = discordHowSection, position = 4)
	default boolean discordScreenshot()
	{
		return false;
	}

	@ConfigItem(keyName = "discordGravestoneScreenshot", name = "Grave picture", description = "The screenshot"
		+ " on 'Gravestone about to expire': where you died (kept in memory only; none after a client restart),"
		+ " where you stand when it posts, or none. Needs 'Attach a screenshot'.", section = discordHowSection,
		position = 5)
	default GraveScreenshot discordGravestoneScreenshot()
	{
		return GraveScreenshot.DEATH;
	}

	@ConfigItem(keyName = "discordMention", name = "Mention for big events", description = "Put in front of pets,"
		+ " 99s, maxing and a Hardcore group life lost, e.g. <@&role id> or <@user id>. Empty mentions nobody.",
		section = discordHowSection, position = 6)
	default String discordMention()
	{
		return "";
	}

	@ConfigItem(keyName = "discordQuietStart", name = "Quiet hours from", description = "Hour (0-23) posts start"
		+ " waiting; they are sent when quiet hours end. Same as 'until' means no quiet hours.",
		section = discordHowSection, position = 7)
	@Range(max = 23)
	default int discordQuietStart()
	{
		return 0;
	}

	@ConfigItem(keyName = "discordQuietEnd", name = "Quiet hours until", description = "Hour (0-23) quiet hours"
		+ " end.", section = discordHowSection, position = 8)
	@Range(max = 23)
	default int discordQuietEnd()
	{
		return 0;
	}

	@ConfigItem(keyName = "discordEasterEggs", name = "Easter eggs", description = "Add one short funny line under"
		+ " some posts: a 0 gp death, a pet, a famous drop, a big storage visit. The facts stay as they are.",
		section = discordHowSection, position = 9)
	default boolean discordEasterEggs()
	{
		return false;
	}

	@ConfigItem(keyName = "discordLegendAt", name = "Legend at (gp)", description = "With Easter eggs on, a group"
		+ " storage deposit worth at least this much gp gets a line. 0 turns it off.", section = discordHowSection,
		position = 10)
	@Units(GP_PLAIN)
	default int discordLegendAt()
	{
		return 1_000_000;
	}

	@ConfigItem(keyName = "discordFreeloaderAt", name = "Freeloader at (gp)", description = "With Easter eggs on, a"
		+ " group storage withdrawal worth at least this much gp gets a line. 0 turns it off.",
		section = discordHowSection, position = 11)
	@Units(GP_PLAIN)
	default int discordFreeloaderAt()
	{
		return 1_000_000;
	}

	/** Replaced by {@link #discordOnlineStatus}; still read once, so an older install keeps its choice. */
	@ConfigItem(keyName = "discordOnline", name = "Online and offline", description = "Replaced by 'Online"
		+ " status'.", hidden = true, section = discordHowSection, position = 12)
	default boolean discordOnline()
	{
		return false;
	}

	// ---- 5. Discord: daily digest ----

	@ConfigItem(keyName = "discordDigestHour", name = "Digest hour", description = "Hour (0-23) the daily digest and"
		+ " the Monday recap are sent, once you are logged in.", section = digestSection, position = 0)
	@Range(max = 23)
	default int discordDigestHour()
	{
		return 20;
	}

	@ConfigItem(keyName = "digestDrops", name = "Include in daily digest: Drops", description = "Adds a daily"
		+ " summary of every drop event since the last digest, ticked for instant posts or not. Never delays"
		+ " instant posts.", section = digestSection, position = 1)
	default boolean digestDrops()
	{
		return false;
	}

	@ConfigItem(keyName = "digestDeaths", name = "Include in daily digest: Deaths", description = "Adds a daily"
		+ " summary of every death event since the last digest, ticked for instant posts or not. Never delays"
		+ " instant posts.", section = digestSection, position = 2)
	default boolean digestDeaths()
	{
		return false;
	}

	@ConfigItem(keyName = "digestStorage", name = "Include in daily digest: Storage", description = "Adds a daily"
		+ " summary of every storage event since the last digest, ticked for instant posts or not. Never delays"
		+ " instant posts.", section = digestSection, position = 3)
	default boolean digestStorage()
	{
		return false;
	}

	@ConfigItem(keyName = "digestProgress", name = "Include in daily digest: Progress", description = "Adds a"
		+ " daily summary of every progress event since the last digest, ticked for instant posts or not. Never"
		+ " delays instant posts.", section = digestSection, position = 4)
	default boolean digestProgress()
	{
		return false;
	}

	@ConfigItem(keyName = "digestGroup", name = "Include in daily digest: Group", description = "Adds a daily"
		+ " summary of every group event since the last digest, ticked for instant posts or not. Never delays"
		+ " instant posts.", section = digestSection, position = 5)
	default boolean digestGroup()
	{
		return false;
	}

	@ConfigItem(keyName = "digestActivities", name = "Include in daily digest: Activities", description = "Adds a"
		+ " daily summary of every activity event since the last digest, ticked for instant posts or not. Never"
		+ " delays instant posts.", section = digestSection, position = 6)
	default boolean digestActivities()
	{
		return false;
	}

	// ---- 6. In-game: banners, group box, map, ::find, then which events show as a banner ----

	@ConfigItem(keyName = "overlayDurationSeconds", name = "Banner time (seconds)", description = "How long each"
		+ " event banner stays on screen.", section = inGameSection, position = 0)
	@Range(min = 2, max = 60)
	default int overlayDurationSeconds()
	{
		return 8;
	}

	@ConfigItem(keyName = "overlayPosition", name = "Banner position", description = "Where banners appear. You can"
		+ " still drag them with Alt held.", section = inGameSection, position = 1)
	default ToastPosition overlayPosition()
	{
		return ToastPosition.TOP_CENTER;
	}

	@ConfigItem(keyName = "overlayMaxStacked", name = "Most banners at once", description = "Older banners make way"
		+ " for newer ones.", section = inGameSection, position = 2)
	@Range(min = 1, max = 10)
	default int overlayMaxStacked()
	{
		return 3;
	}

	@ConfigItem(keyName = "overlayGroupBox", name = "Group box", description = "A box on screen with who is online,"
		+ " their world, and the latest events.", section = inGameSection, position = 3)
	default boolean overlayGroupBox()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayGroupBoxEvents", name = "Group box events", description = "How many of the"
		+ " latest events the group box lists.", section = inGameSection, position = 4)
	@Range(min = 1, max = 20)
	default int overlayGroupBoxEvents()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "groupWorldMap",
		name = "Show members on world map",
		description = "Mark online members on the world map. Needs 'Share stats' on, here and on their side.",
		section = inGameSection,
		position = 5
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
		section = inGameSection,
		position = 6
	)
	default boolean groupFindCommand()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayValuableDrop", name = "Banner: Valuable drop", description = "A drop worth at least"
		+ " the 'Valuable drop' value under Thresholds.", section = inGameSection, position = 7)
	default boolean overlayValuableDrop()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayPet", name = "Banner: Pet", description = "A pet.", section = inGameSection,
		position = 8)
	default boolean overlayPet()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayCollectionLog", name = "Banner: New collection log slot", description = "A new"
		+ " collection log slot.", section = inGameSection, position = 9)
	default boolean overlayCollectionLog()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayUntradeableUnique", name = "Banner: Untradeable unique", description = "An"
		+ " untradeable unique from a drop.", section = inGameSection, position = 10)
	default boolean overlayUntradeableUnique()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayRaidUnique", name = "Banner: Raid unique", description = "A raid purple.",
		section = inGameSection, position = 11)
	default boolean overlayRaidUnique()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayClue", name = "Banner: Clue reward", description = "A clue reward worth at least"
		+ " the 'Clue reward' value under Thresholds.", section = inGameSection, position = 12)
	default boolean overlayClue()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayDryStreak", name = "Banner: Dry streak", description = "Kills past a boss's"
		+ " unique rate.", section = inGameSection, position = 13)
	default boolean overlayDryStreak()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayDeath", name = "Banner: Death", description = "A member died.",
		section = inGameSection, position = 14)
	default boolean overlayDeath()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayDeathValue", name = "Banner: Value lost on death", description = "What a death"
		+ " cost.", section = inGameSection, position = 15)
	default boolean overlayDeathValue()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayGravestone", name = "Banner: Gravestone expiring", description = "A"
		+ " gravestone is about to expire.", section = inGameSection, position = 16)
	default boolean overlayGravestone()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayHardcoreLife", name = "Banner: Hardcore life lost", description = "The group lost"
		+ " a life.", section = inGameSection, position = 17)
	default boolean overlayHardcoreLife()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayWilderness", name = "Banner: Wilderness and skull", description = "A member entered"
		+ " the Wilderness or got skulled.", section = inGameSection, position = 18)
	default boolean overlayWilderness()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayStorageDeposit", name = "Banner: Big storage deposit", description = "A big group"
		+ " storage deposit.", section = inGameSection, position = 19)
	default boolean overlayStorageDeposit()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayStorageWithdrawal", name = "Banner: Big storage withdrawal", description = "A big"
		+ " group storage withdrawal.", section = inGameSection, position = 20)
	default boolean overlayStorageWithdrawal()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayStorageSummary", name = "Banner: Storage visit summary", description = "Each group"
		+ " storage visit.", section = inGameSection, position = 21)
	default boolean overlayStorageSummary()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayStorageMinimum", name = "Banner: Storage minimum hit", description = "A storage"
		+ " minimum was hit.", section = inGameSection, position = 22)
	default boolean overlayStorageMinimum()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayStorageLast", name = "Banner: Took the last one", description = "Someone took the"
		+ " last of an item.", section = inGameSection, position = 23)
	default boolean overlayStorageLast()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayLevelUp", name = "Banner: Level up", description = "A level up.",
		section = inGameSection, position = 24)
	default boolean overlayLevelUp()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayMaxed", name = "Banner: 99s and max total", description = "A 99 or max total.",
		section = inGameSection, position = 25)
	default boolean overlayMaxed()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayXpMilestone", name = "Banner: XP milestones", description = "1M, 10M and 200M XP.",
		section = inGameSection, position = 26)
	default boolean overlayXpMilestone()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayCombatLevel", name = "Banner: Combat level up", description = "A combat level up.",
		section = inGameSection, position = 27)
	default boolean overlayCombatLevel()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayTotalLevel", name = "Banner: Total level milestones", description = "Every 100"
		+ " total levels.", section = inGameSection, position = 28)
	default boolean overlayTotalLevel()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayQuest", name = "Banner: Quest complete", description = "A quest completed.",
		section = inGameSection, position = 29)
	default boolean overlayQuest()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayQuestPoints", name = "Banner: Quest point milestones", description = "Every 25"
		+ " quest points, and every quest done.", section = inGameSection, position = 30)
	default boolean overlayQuestPoints()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayDiary", name = "Banner: Achievement diary tier", description = "A diary tier"
		+ " completed.", section = inGameSection, position = 31)
	default boolean overlayDiary()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayCombatTask", name = "Banner: Combat achievement", description = "A combat task.",
		section = inGameSection, position = 32)
	default boolean overlayCombatTask()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayKcMilestone", name = "Banner: Kill count milestone", description = "A boss kill"
		+ " count milestone.", section = inGameSection, position = 33)
	default boolean overlayKcMilestone()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayPersonalBest", name = "Banner: New personal best", description = "A new best"
		+ " time.", section = inGameSection, position = 34)
	default boolean overlayPersonalBest()
	{
		return false;
	}

	@ConfigItem(keyName = "overlaySlayer", name = "Banner: Slayer task", description = "A task done, and a new task.",
		section = inGameSection, position = 35)
	default boolean overlaySlayer()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayFarming", name = "Banner: Farming runs ready", description = "Your herb or tree"
		+ " patches have finished growing, going by when you last saw them.", section = inGameSection, position = 36)
	default boolean overlayFarming()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayBirdhouse", name = "Banner: Bird houses, Hespori", description = "Your bird houses"
		+ " or Hespori have finished, going by when you last saw them.", section = inGameSection, position = 37)
	default boolean overlayBirdhouse()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayOnline", name = "Banner: Online", description = "A member logged in, with the"
		+ " world.", section = inGameSection, position = 38)
	default boolean overlayOnline()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayBack", name = "Banner: Back after a break", description = "A member is back after"
		+ " the 'Back after (days)' value under Thresholds.", section = inGameSection, position = 39)
	default boolean overlayBack()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayWeeklyRecap", name = "Banner: Weekly recap", description = "A member's week.",
		section = inGameSection, position = 40)
	default boolean overlayWeeklyRecap()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayBarrows", name = "Banner: Barrows chest", description = "A Barrows chest.",
		section = inGameSection, position = 41)
	default boolean overlayBarrows()
	{
		return false;
	}

	// ---- 7. Thresholds: what counts. Both Discord and the banners read these. ----

	@ConfigItem(keyName = "eventDropMinValue", name = "Valuable drop (gp)", description = "Valuable drop at least"
		+ " (gp): a drop counts as valuable when an item stack in it is worth at least this much.",
		section = thresholdsSection, position = 0)
	@Units(GP_PLAIN)
	default int eventDropMinValue()
	{
		return 1_000_000;
	}

	@ConfigItem(keyName = "eventStorageMinValue", name = "Big storage (gp)", description = "Big storage move"
		+ " at least (gp): a group storage deposit or withdrawal counts as big when it is worth at least this much.",
		section = thresholdsSection, position = 1)
	@Units(GP_PLAIN)
	default int eventStorageMinValue()
	{
		return 1_000_000;
	}

	@ConfigItem(keyName = "eventClueMinValue", name = "Clue reward (gp)", description = "Clue reward at least (gp): a"
		+ " clue reward counts when it is worth at least this much.", section = thresholdsSection, position = 2)
	@Units(GP_PLAIN)
	default int eventClueMinValue()
	{
		return 1_000_000;
	}

	@ConfigItem(
		keyName = "groupDropMinValue",
		name = "Shared drops (gp)",
		description = "Share drops worth at least (gp): with 'Share drops and collection log' on, only items whose"
			+ " stack is worth at least this much are sent to your group's panels. 0 shares every item.",
		section = thresholdsSection,
		position = 3
	)
	@Units(GP_PLAIN)
	default int groupDropMinValue()
	{
		return 10_000;
	}

	@ConfigItem(keyName = "eventLevelMin", name = "Level ups from level", description = "Skill level ups below"
		+ " this level are not posted to Discord or shown as a banner.",
		section = thresholdsSection, position = 4)
	@Range(min = 1, max = 99)
	default int eventLevelMin()
	{
		return 1;
	}

	@ConfigItem(keyName = "eventLevelEvery", name = "Post every N levels", description = "From 'Level ups from"
		+ " level', only every Nth level counts (e.g. 5: 60, 65, 70). 1 is every level. 99s, max total, total level"
		+ " milestones and combat levels always count. Your group's settings replace this once its leader saves"
		+ " them in the panel's Settings tab.", section = thresholdsSection, position = 5)
	@Range(min = 1, max = 98)
	default int eventLevelEvery()
	{
		return 1;
	}

	@ConfigItem(keyName = "eventKcEvery", name = "KC milestone every", description = "Kill count milestone every:"
		+ " a boss kill count milestone every this many kills. 0 turns them off.", section = thresholdsSection,
		position = 6)
	default int eventKcEvery()
	{
		return 100;
	}

	@ConfigItem(keyName = "eventBackAfterDays", name = "Back after (days)", description = "Logging in after at"
		+ " least this many days away counts as 'Back after a break'.", section = thresholdsSection, position = 7)
	@Range(min = 1, max = 365)
	default int eventBackAfterDays()
	{
		return 7;
	}

	@ConfigItem(keyName = "eventGravestoneWarnMinutes", name = "Gravestone warning", description = "Minutes left: how"
		+ " long before a gravestone's 15 minutes run out to warn.", section = thresholdsSection, position = 8)
	@Range(min = 1, max = 14)
	@Units(Units.MINUTES)
	default int eventGravestoneWarnMinutes()
	{
		return 5;
	}

	@ConfigItem(keyName = "eventUntradeableIgnore", name = "Untradeables to ignore", description = "Untradeable"
		+ " drops whose name starts with one of these (comma separated) are not uniques.",
		section = thresholdsSection, position = 9)
	default String eventUntradeableIgnore()
	{
		return EventDetect.DEFAULT_UNTRADEABLE_IGNORE;
	}

	// ---- 8. Advanced: ::find colours ----

	@ConfigItem(keyName = "findQueryColour", name = "Search colour", description = "::find colour: the words you"
		+ " searched for in the ::find answer.", section = advancedSection, position = 0)
	default Color findQueryColour()
	{
		return new Color(0xD0, 0x5A, 0x00);
	}

	@ConfigItem(keyName = "findColourItemNames", name = "Colour item names",
		description = "Also colour each item's name in the ::find answer. Off keeps the answer calmer: only your"
			+ " search and the places are coloured.", section = advancedSection, position = 1)
	default boolean findColourItemNames()
	{
		return false;
	}

	@ConfigItem(keyName = "findYourBankColour", name = "Your bank colour", description = "::find colour: what is in"
		+ " your own bank.", section = advancedSection, position = 2)
	default Color findYourBankColour()
	{
		return new Color(0x1E, 0x8C, 0x1E);
	}

	@ConfigItem(keyName = "findMemberBankColour", name = "Member bank colour",
		description = "::find colour: what is in another member's bank.", section = advancedSection, position = 3)
	default Color findMemberBankColour()
	{
		return new Color(0x8A, 0x2B, 0xE2);
	}

	@ConfigItem(keyName = "findStorageColour", name = "Storage colour",
		description = "::find colour: what is in group storage, and who took or deposited it.",
		section = advancedSection, position = 4)
	default Color findStorageColour()
	{
		return new Color(0xB8, 0x86, 0x0B);
	}

	@ConfigItem(keyName = "findCarriedColour", name = "Carried colour",
		description = "::find colour: what someone has in their inventory, equipped, or in a pouch, quiver or"
			+ " seed vault.",
		section = advancedSection, position = 5)
	default Color findCarriedColour()
	{
		return new Color(0x00, 0x8B, 0x8B);
	}

	@ConfigItem(keyName = "findItemColour", name = "Item name colour", description = "::find colour: each matching"
		+ " item's name, when 'Colour item names' is on.", section = advancedSection, position = 6)
	default Color findItemColour()
	{
		return new Color(0x1E, 0x5A, 0xA8);
	}

	@ConfigItem(keyName = "showUpdateNotes", name = "Show update notes", description = "Say hello in the game chat"
		+ " on a fresh install, and what is new once after each update, on your next login.",
		section = advancedSection, position = 7)
	default boolean showUpdateNotes()
	{
		return true;
	}

	/** The last version whose update notes were handled; written by the plugin, never shown. */
	@ConfigItem(keyName = KEY_LAST_SEEN_VERSION, name = "Last seen version", description = "The last version whose"
		+ " update notes were handled. Written by the plugin.", hidden = true, section = advancedSection,
		position = 8)
	default String lastSeenVersion()
	{
		return "";
	}

	@ConfigItem(keyName = KEY_OPT_OUT_TYPES, name = "Kinds not to post", description = "Set from the panel's"
		+ " Settings tab.", hidden = true, section = advancedSection, position = 9)
	default String eventOptOutTypes()
	{
		return "";
	}

	@ConfigItem(keyName = KEY_MUTE_TYPES, name = "Kinds not to show", description = "Set from the panel's"
		+ " Settings tab.", hidden = true, section = advancedSection, position = 10)
	default String overlayMuteTypes()
	{
		return "";
	}
}
