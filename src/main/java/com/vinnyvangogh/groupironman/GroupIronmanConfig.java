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
	 * Frozen. Written by the plugin, never shown: the group ({@link GroupMembers#key} of its name) this client's
	 * group key belongs to, so an account in another group is told apart from a member ({@link GroupBinding}).
	 */
	String KEY_BOUND_GROUP = "boundGroup";
	/** 'Combine level-ups': frozen key; its default moved 30 → 60 for new installs only (GroupEventEngine). */
	String KEY_COMBINE_LEVELS = "discordCombineLevels";

	/**
	 * The {@code @Units} on every gp number box: no suffix at all. RuneLite's number box is a fixed 6 columns
	 * wide whatever the label, and its plain formatter writes thousands commas, so 1,000,000 showed as
	 * "1,000,00". A {@code @Units} formatter writes {@code value + units} with no commas instead, so 1000000
	 * fits. Display only: the saved value and its key are untouched. ConfigFitTest holds every row to fit.
	 */
	String GP_PLAIN = "";

	// ---- The eight sections ----
	// Every section but Group starts closed, so a first install shows all eight names at a glance (Board 2026-11-11).

	@ConfigSection(name = "Group", description = "What you share. You see members' data only for what you share.",
		position = 0)
	String groupSection = "sectionGroup";

	@ConfigSection(name = "Discord: connection", description = "Webhooks and poster names. Each client posts only"
		+ " its own events.", position = 1, closedByDefault = true)
	String discordConnectionSection = "sectionDiscordConnection";

	@ConfigSection(name = "Discord: what to post", description = "One switch per event.", position = 2,
		closedByDefault = true)
	String discordEventsSection = "sectionDiscordWhat";

	@ConfigSection(name = "Discord: how to post", description = "Online status, grouping, screenshots, quiet hours.",
		position = 3, closedByDefault = true)
	String discordHowSection = "sectionDiscordHow";

	@ConfigSection(name = "Discord: daily digest", description = "Daily summaries. Never delays instant posts.",
		position = 4, closedByDefault = true)
	String digestSection = "sectionDigest";

	@ConfigSection(name = "In-game", description = "Banners, group box, map, ::find. Members must share events too.",
		position = 5, closedByDefault = true)
	String inGameSection = "sectionInGame";

	@ConfigSection(name = "Thresholds", description = "What counts as an event, for Discord and in game.",
		position = 6, closedByDefault = true)
	String thresholdsSection = "sectionThresholds";

	@ConfigSection(name = "Advanced", description = "The ::find colours, update notes.",
		position = 7, closedByDefault = true)
	String advancedSection = "sectionAdvanced";

	// ---- 1. Group ----

	@ConfigItem(
		keyName = "groupSync",
		name = "Sync with my group",
		description = "Connect to your group's party via 'Join group sync' in the panel. Needs a group key.",
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
		description = "Secret key from the panel or a member. Anyone with it sees your data: share only in group chat.",
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
		description = "Send your bank on close, and show each member's last bank and its value.",
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
		description = "Send what you took and deposited. Logs the last 200 changes by members with this on.",
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
		description = "'Shark: 100', one per line or comma. Notifies when storage drops below a minimum.",
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
		description = "Send your levels, HP, prayer, run, spec, world and location, and show members'.",
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
		description = "Send your inventory and worn items, for 'Who has it?'.",
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
		description = "Send your rune pouch, quiver and seed vault, and show members'.",
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
		description = "Send drops and new collection log slots (needs the game's new-addition chat message).",
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
		description = "Send pets, deaths, levels, quests, logins and Barrows chests, shown to members as banners.",
		section = groupSection,
		position = 9
	)
	default boolean groupShareEvents()
	{
		return false;
	}

	// ---- 2. Discord: connection ----

	@ConfigItem(keyName = "discordEnabled", name = "Post to Discord",
		description = "Post ticked events to the webhooks.", warning = DiscordWebhook.WARNING,
		section = discordConnectionSection, position = 0)
	default boolean discordEnabled()
	{
		return false;
	}

	// Seven webhooks, three rows each: the URL, the poster-name choice, the Custom box (Board 2026-11-11). Main
	// keeps the keys of the one webhook there used to be. A category left empty posts to Main's URL under its own
	// poster name. Webhooks.of is the only reader of these keys; Webhook says which events each one carries.

	@ConfigItem(keyName = "discordWebhookUrl", name = "Main webhook",
		description = "From Discord channel settings, Integrations. Gets every post with no webhook of its own.",
		secret = true, section = discordConnectionSection, position = 1)
	default String discordWebhookUrl()
	{
		return "";
	}

	@ConfigItem(keyName = "discordPosterName", name = "Main name",
		description = "The webhook's name, Town Crier, or Custom below.", section = discordConnectionSection, position = 2)
	default DiscordPosterName discordPosterName()
	{
		return DiscordPosterName.WEBHOOK;
	}

	@ConfigItem(keyName = "discordNameMain", name = "Main custom name",
		description = "Up to 80 characters; not 'discord', 'clyde', 'everyone', 'here', @, #, : or ```.",
		section = discordConnectionSection, position = 3)
	default String discordNameMain()
	{
		return "Town Crier";
	}

	@ConfigItem(keyName = "discordGroupUrl", name = "Online status webhook",
		description = "Empty uses Main. Changing it starts a new board.", secret = true,
		section = discordConnectionSection, position = 4)
	default String discordGroupUrl()
	{
		return "";
	}

	@ConfigItem(keyName = "discordPosterOnline", name = "Online name",
		description = "The webhook's name, World Hopper, or Custom below.", section = discordConnectionSection,
		position = 5)
	default DiscordPosterName discordPosterOnline()
	{
		return DiscordPosterName.WEBHOOK;
	}

	@ConfigItem(keyName = "discordNameGroup", name = "Online custom name", description = "Used with Custom.",
		section = discordConnectionSection, position = 6)
	default String discordNameGroup()
	{
		return "World Hopper";
	}

	@ConfigItem(keyName = "discordDropsUrl", name = "Drops & loot webhook",
		description = "Drops, pets, log slots, uniques, chests. Empty uses Main.", secret = true,
		section = discordConnectionSection, position = 7)
	default String discordDropsUrl()
	{
		return "";
	}

	@ConfigItem(keyName = "discordPosterDrops", name = "Drops name",
		description = "The webhook's name, Party Pete, or Custom below.", section = discordConnectionSection,
		position = 8)
	default DiscordPosterName discordPosterDrops()
	{
		return DiscordPosterName.WEBHOOK;
	}

	@ConfigItem(keyName = "discordNameDrops", name = "Drops custom name", description = "Used with Custom.",
		section = discordConnectionSection, position = 9)
	default String discordNameDrops()
	{
		return "Party Pete";
	}

	@ConfigItem(keyName = "discordProgressUrl", name = "Progress webhook", description = "Levels, XP, quests."
		+ " Empty uses Main.", secret = true, section = discordConnectionSection, position = 10)
	default String discordProgressUrl()
	{
		return "";
	}

	@ConfigItem(keyName = "discordPosterProgress", name = "Progress name", description = "The webhook's name,"
		+ " Skillcape Seller, or Custom below.", section = discordConnectionSection, position = 11)
	default DiscordPosterName discordPosterProgress()
	{
		return DiscordPosterName.WEBHOOK;
	}

	@ConfigItem(keyName = "discordNameProgress", name = "Progress custom name", description = "Used with Custom.",
		section = discordConnectionSection, position = 12)
	default String discordNameProgress()
	{
		return "Skillcape Seller";
	}

	@ConfigItem(keyName = "discordDeathsUrl", name = "Deaths & danger webhook", description = "Deaths, Hardcore"
		+ " lives, gravestones. Empty uses Main.", secret = true, section = discordConnectionSection, position = 13)
	default String discordDeathsUrl()
	{
		return "";
	}

	@ConfigItem(keyName = "discordPosterDeaths", name = "Deaths name", description = "The webhook's name, Death's"
		+ " Office, or Custom below.", section = discordConnectionSection, position = 14)
	default DiscordPosterName discordPosterDeaths()
	{
		return DiscordPosterName.WEBHOOK;
	}

	@ConfigItem(keyName = "discordNameDeaths", name = "Deaths custom name", description = "Used with Custom.",
		section = discordConnectionSection, position = 15)
	default String discordNameDeaths()
	{
		return "Death's Office";
	}

	@ConfigItem(keyName = "discordStorageUrl", name = "Group storage webhook", description = "Storage events."
		+ " Empty uses Main.", secret = true, section = discordConnectionSection, position = 16)
	default String discordStorageUrl()
	{
		return "";
	}

	@ConfigItem(keyName = "discordPosterStorage", name = "Storage name", description = "The webhook's name,"
		+ " Gielinor Banker, or Custom below.", section = discordConnectionSection, position = 17)
	default DiscordPosterName discordPosterStorage()
	{
		return DiscordPosterName.WEBHOOK;
	}

	@ConfigItem(keyName = "discordNameStorage", name = "Storage custom name", description = "Used with Custom.",
		section = discordConnectionSection, position = 18)
	default String discordNameStorage()
	{
		return "Gielinor Banker";
	}

	@ConfigItem(keyName = "discordDigestUrl", name = "Daily digest webhook", description = "Empty uses Main.",
		secret = true, section = discordConnectionSection, position = 19)
	default String discordDigestUrl()
	{
		return "";
	}

	@ConfigItem(keyName = "discordPosterDigest", name = "Digest name", description = "The webhook's name, Varrock"
		+ " Herald, or Custom below.", section = discordConnectionSection, position = 20)
	default DiscordPosterName discordPosterDigest()
	{
		return DiscordPosterName.WEBHOOK;
	}

	@ConfigItem(keyName = "discordNameDigest", name = "Digest custom name", description = "Used with Custom.",
		section = discordConnectionSection, position = 21)
	default String discordNameDigest()
	{
		return "Varrock Herald";
	}

	// Retired with the seven-webhook split: Activities is no longer a webhook (its events go to Main, Barrows to
	// Drops & loot). Kept hidden, never read, so the frozen keys are never reused for something else.

	@ConfigItem(keyName = "discordActivitiesUrl", name = "Activities webhook (retired)", description = "Retired.",
		secret = true, hidden = true, section = discordConnectionSection, position = 22)
	default String discordActivitiesUrl()
	{
		return "";
	}

	@ConfigItem(keyName = "discordNameActivities", name = "Activities name (retired)", description = "Retired.",
		hidden = true, section = discordConnectionSection, position = 23)
	default String discordNameActivities()
	{
		return "";
	}

	@ConfigItem(keyName = KEY_OPT_OUT_ALL, name = "Don't post my events", description = "None of your events go to"
		+ " Discord, whatever the group says. Per event: panel Settings tab. Leader and editors see this.",
		section = discordConnectionSection, position = 24)
	default boolean eventOptOutAll()
	{
		return false;
	}

	// ---- 3. Discord: what to post. Drops, deaths, storage, progress, group, activities. ----

	@ConfigItem(keyName = "discordValuableDrop", name = "Valuable drop",
		description = "A drop over the Thresholds value, with the monster and kill count.",
		section = discordEventsSection, position = 0)
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

	@ConfigItem(keyName = "discordCollectionLog", name = "New collection log slot",
		description = "Needs the game's new-addition chat message.", section = discordEventsSection, position = 2)
	default boolean discordCollectionLog()
	{
		return false;
	}

	@ConfigItem(keyName = "discordUntradeableUnique", name = "Untradeable unique",
		description = "An untradeable drop not ignored under Thresholds.", section = discordEventsSection, position = 3)
	default boolean discordUntradeableUnique()
	{
		return false;
	}

	@ConfigItem(keyName = "discordRaidUnique", name = "Raid unique", description = "Your raid purple, with kill count.",
		section = discordEventsSection, position = 4)
	default boolean discordRaidUnique()
	{
		return false;
	}

	@ConfigItem(keyName = "discordClue", name = "Clue reward", description = "A clue reward over the Thresholds value.",
		section = discordEventsSection, position = 5)
	default boolean discordClue()
	{
		return false;
	}

	@ConfigItem(keyName = "discordDryStreak", name = "Dry streak", description = "Kills past Vorkath's or Zulrah's"
		+ " unique rate.", section = discordEventsSection, position = 6)
	default boolean discordDryStreak()
	{
		return false;
	}

	@ConfigItem(keyName = "discordDeath", name = "Death", description = "Where, the killer and the world.",
		section = discordEventsSection, position = 7)
	default boolean discordDeath()
	{
		return false;
	}

	@ConfigItem(keyName = "discordDeathValue", name = "Value lost on death",
		description = "What a death cost, and where the gravestone is.", section = discordEventsSection, position = 8)
	default boolean discordDeathValue()
	{
		return false;
	}

	@ConfigItem(keyName = "discordGravestone", name = "Gravestone about to expire",
		description = "Before its items go to Death's Office.", section = discordEventsSection, position = 9)
	default boolean discordGravestone()
	{
		return false;
	}

	@ConfigItem(keyName = "discordHardcoreLife", name = "Hardcore life lost", description = "With lives left.",
		section = discordEventsSection, position = 10)
	default boolean discordHardcoreLife()
	{
		return false;
	}

	@ConfigItem(keyName = "discordWilderness", name = "Wilderness and skull",
		description = "You entered the Wilderness or got skulled.", section = discordEventsSection, position = 11)
	default boolean discordWilderness()
	{
		return false;
	}

	@ConfigItem(keyName = "discordStorageDeposit", name = "Big storage deposit",
		description = "Worth over the Thresholds value.", section = discordEventsSection, position = 12)
	default boolean discordStorageDeposit()
	{
		return false;
	}

	@ConfigItem(keyName = "discordStorageWithdrawal", name = "Big storage withdrawal",
		description = "Worth over the Thresholds value.", section = discordEventsSection, position = 13)
	default boolean discordStorageWithdrawal()
	{
		return false;
	}

	@ConfigItem(keyName = "discordStorageSummary", name = "Storage visit summary",
		description = "Everything moved in one visit.", section = discordEventsSection, position = 14)
	default boolean discordStorageSummary()
	{
		return false;
	}

	@ConfigItem(keyName = "discordStorageMinimum", name = "Storage minimum hit",
		description = "An item fell below its minimum.", section = discordEventsSection, position = 15)
	default boolean discordStorageMinimum()
	{
		return false;
	}

	@ConfigItem(keyName = "discordStorageLast", name = "Took the last one",
		description = "You took the last of an item.", section = discordEventsSection, position = 16)
	default boolean discordStorageLast()
	{
		return false;
	}

	@ConfigItem(keyName = "discordLevelUp", name = "Level up", description = "A skill level up, per Thresholds.",
		section = discordEventsSection, position = 17)
	default boolean discordLevelUp()
	{
		return false;
	}

	@ConfigItem(keyName = "discordMaxed", name = "99s and max total", description = "A 99, or every skill at 99.",
		section = discordEventsSection, position = 18)
	default boolean discordMaxed()
	{
		return false;
	}

	@ConfigItem(keyName = "discordXpMilestone", name = "XP milestones", description = "1M, 10M and 200M XP in a skill.",
		section = discordEventsSection, position = 19)
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

	@ConfigItem(keyName = "discordTotalLevel", name = "Total level milestones", description = "Every 100 total levels.",
		section = discordEventsSection, position = 21)
	default boolean discordTotalLevel()
	{
		return false;
	}

	@ConfigItem(keyName = "discordQuest", name = "Quest complete", description = "With your quest points.",
		section = discordEventsSection, position = 22)
	default boolean discordQuest()
	{
		return false;
	}

	@ConfigItem(keyName = "discordQuestPoints", name = "Quest point milestones",
		description = "Every 25 quest points, and every quest done.", section = discordEventsSection, position = 23)
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

	@ConfigItem(keyName = "discordCombatTask", name = "Combat achievement", description = "A task and its tier.",
		section = discordEventsSection, position = 25)
	default boolean discordCombatTask()
	{
		return false;
	}

	@ConfigItem(keyName = "discordKcMilestone", name = "Kill count milestone",
		description = "Boss kill counts, per Thresholds.", section = discordEventsSection, position = 26)
	default boolean discordKcMilestone()
	{
		return false;
	}

	@ConfigItem(keyName = "discordPersonalBest", name = "New personal best",
		description = "A new boss or raid best time.", section = discordEventsSection, position = 27)
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

	@ConfigItem(keyName = "discordFarming", name = "Farming runs ready", description = "Herb or tree patches grown.",
		section = discordEventsSection, position = 29)
	default boolean discordFarming()
	{
		return false;
	}

	@ConfigItem(keyName = "discordBirdhouse", name = "Bird houses, Hespori", description = "Bird houses or Hespori done.",
		section = discordEventsSection, position = 30)
	default boolean discordBirdhouse()
	{
		return false;
	}

	@ConfigItem(keyName = "discordBack", name = "Back after a break", description = "Logged in after days away.",
		section = discordEventsSection, position = 31)
	default boolean discordBack()
	{
		return false;
	}

	@ConfigItem(keyName = "discordWeeklyRecap", name = "Weekly recap",
		description = "Mondays: your week's XP, drops, kills and storage visits.", section = discordEventsSection,
		position = 32)
	default boolean discordWeeklyRecap()
	{
		return false;
	}

	@ConfigItem(keyName = "discordBarrows", name = "Barrows chest", description = "Loot, chest count, set progress.",
		section = discordEventsSection, position = 33)
	default boolean discordBarrows()
	{
		return false;
	}

	@ConfigItem(keyName = "discordRequest", name = "Shopping requests",
		description = "Requests in the Shopping tab: asked, offered, or done.", section = discordEventsSection,
		position = 34)
	default boolean discordRequest()
	{
		return false;
	}

	@ConfigItem(keyName = "discordActivityStart", name = "Activity started",
		description = "Fight Caves, Inferno or Colosseum.", section = discordEventsSection, position = 35)
	default boolean discordActivityStart()
	{
		return false;
	}

	@ConfigItem(keyName = "discordActivityWave", name = "Activity wave milestone",
		description = "A wave milestone or final boss wave.", section = discordEventsSection, position = 36)
	default boolean discordActivityWave()
	{
		return false;
	}

	@ConfigItem(keyName = "discordActivityComplete", name = "Activity completed",
		description = "Finished, or claimed Colosseum loot early.", section = discordEventsSection, position = 37)
	default boolean discordActivityComplete()
	{
		return false;
	}

	@ConfigItem(keyName = "discordActivityDeath", name = "Activity death",
		description = "Died, with the wave. Replaces the Death post.", section = discordEventsSection, position = 38)
	default boolean discordActivityDeath()
	{
		return false;
	}

	// ---- 4. Discord: how to post ----

	@ConfigItem(keyName = "discordOnlineStatus", name = "Online status", description = "Posts: one per login and"
		+ " logout. Live board: one edited message of who is online; only one member per webhook should use it.",
		section = discordHowSection, position = 0)
	default OnlineStatus discordOnlineStatus()
	{
		return OnlineStatus.POSTS;
	}

	@ConfigItem(keyName = "discordRepostBoard", name = "Repost board daily",
		description = "Live board: after midnight, repost it at the bottom and delete the old one.",
		section = discordHowSection, position = 13)
	default boolean discordRepostBoard()
	{
		return true;
	}

	@Range(max = 10)
	@Units(Units.MINUTES)
	@ConfigItem(keyName = "discordOfflineGrace", name = "Offline grace",
		description = "Wait before posting a logout. Relogging within it posts nothing.", section = discordHowSection,
		position = 1)
	default int discordOfflineGrace()
	{
		return 2;
	}

	@ConfigItem(keyName = KEY_COMBINE_LEVELS, name = "Combine level-ups",
		description = "Levels within this many minutes edit one message, a card per skill. 0 is off.",
		section = discordHowSection, position = 2)
	@Range(max = 240)
	@Units(Units.MINUTES)
	default int discordCombineLevels()
	{
		return 60;
	}

	@ConfigItem(keyName = "discordGroupPosts", name = "Group posts (minutes)",
		description = "Same-kind events within this many minutes edit one message, up to 10 cards. Storage shows net"
			+ " totals. 0 is off.", section = discordHowSection, position = 15)
	@Range(max = 240)
	@Units(Units.MINUTES)
	default int discordGroupPosts()
	{
		return 30;
	}

	@ConfigItem(keyName = "discordStyle", name = "Message style", description = "Embeds with icons, or plain text.",
		section = discordHowSection, position = 3)
	default DiscordStyle discordStyle()
	{
		return DiscordStyle.EMBED;
	}

	@ConfigItem(keyName = "discordScreenshot", name = "Attach a screenshot",
		description = "A game screenshot on each post.", section = discordHowSection, position = 4)
	default boolean discordScreenshot()
	{
		return false;
	}

	@ConfigItem(keyName = "discordGravestoneScreenshot", name = "Grave picture",
		description = "For gravestone posts: where you died, where you stand, or none. Needs 'Attach a screenshot'.",
		section = discordHowSection, position = 5)
	default GraveScreenshot discordGravestoneScreenshot()
	{
		return GraveScreenshot.DEATH;
	}

	@ConfigItem(keyName = "discordMention", name = "Mention for big events",
		description = "Pinged on pets, 99s, max and Hardcore lives, e.g. <@&role id>.", section = discordHowSection,
		position = 6)
	default String discordMention()
	{
		return "";
	}

	@ConfigItem(keyName = "discordQuietStart", name = "Quiet hours from",
		description = "Hour posts start waiting. Same as 'until' means no quiet hours.", section = discordHowSection,
		position = 7)
	@Range(max = 23)
	default int discordQuietStart()
	{
		return 0;
	}

	@ConfigItem(keyName = "discordQuietEnd", name = "Quiet hours until", description = "Hour held posts are sent.",
		section = discordHowSection, position = 8)
	@Range(max = 23)
	default int discordQuietEnd()
	{
		return 0;
	}

	@ConfigItem(keyName = "discordEasterEggs", name = "Easter eggs",
		description = "A funny line under some posts, like a 0 gp death or level 69.", section = discordHowSection,
		position = 9)
	default boolean discordEasterEggs()
	{
		return false;
	}

	@ConfigItem(keyName = "discordLegendAt", name = "Legend at (gp)",
		description = "Easter egg for a storage deposit this big. 0 is off.", section = discordHowSection,
		position = 10)
	@Units(GP_PLAIN)
	default int discordLegendAt()
	{
		return 1_000_000;
	}

	@ConfigItem(keyName = "discordFreeloaderAt", name = "Freeloader at (gp)",
		description = "Easter egg for a storage withdrawal this big. 0 is off.", section = discordHowSection,
		position = 11)
	@Units(GP_PLAIN)
	default int discordFreeloaderAt()
	{
		return 1_000_000;
	}

	@ConfigItem(keyName = "discordNoPluginEvents", name = "Members without the plugin",
		description = "Post the game's group broadcasts for members without the plugin. The leader's choice wins.",
		section = discordHowSection, position = 14)
	default boolean discordNoPluginEvents()
	{
		return true;
	}

	/** Replaced by {@link #discordOnlineStatus}; still read once, so an older install keeps its choice. */
	@ConfigItem(keyName = "discordOnline", name = "Online and offline", description = "Replaced.", hidden = true,
		section = discordHowSection, position = 12)
	default boolean discordOnline()
	{
		return false;
	}

	// ---- 5. Discord: daily digest ----

	@ConfigItem(keyName = "discordDigestHour", name = "Digest hour",
		description = "Hour the digest and Monday recap are sent.", section = digestSection, position = 0)
	@Range(max = 23)
	default int discordDigestHour()
	{
		return 20;
	}

	@ConfigItem(keyName = "digestDrops", name = "Include in daily digest: Drops", description = "Drop events.",
		section = digestSection, position = 1)
	default boolean digestDrops()
	{
		return false;
	}

	@ConfigItem(keyName = "digestDeaths", name = "Include in daily digest: Deaths", description = "Death events.",
		section = digestSection, position = 2)
	default boolean digestDeaths()
	{
		return false;
	}

	@ConfigItem(keyName = "digestStorage", name = "Include in daily digest: Storage", description = "Storage events.",
		section = digestSection, position = 3)
	default boolean digestStorage()
	{
		return false;
	}

	@ConfigItem(keyName = "digestProgress", name = "Include in daily digest: Progress",
		description = "Progress events.", section = digestSection, position = 4)
	default boolean digestProgress()
	{
		return false;
	}

	@ConfigItem(keyName = "digestGroup", name = "Include in daily digest: Group", description = "Group events.",
		section = digestSection, position = 5)
	default boolean digestGroup()
	{
		return false;
	}

	@ConfigItem(keyName = "digestActivities", name = "Include in daily digest: Activities",
		description = "Activity events.", section = digestSection, position = 6)
	default boolean digestActivities()
	{
		return false;
	}

	// ---- 6. In-game: banners, group box, map, ::find, then which events show as a banner ----

	@ConfigItem(keyName = "overlayDurationSeconds", name = "Banner time (seconds)", description = "How long a"
		+ " banner stays.", section = inGameSection, position = 0)
	@Range(min = 2, max = 60)
	default int overlayDurationSeconds()
	{
		return 8;
	}

	@ConfigItem(keyName = "overlayPosition", name = "Banner position", description = "Alt-drag to move"
		+ " them.", section = inGameSection, position = 1)
	default ToastPosition overlayPosition()
	{
		return ToastPosition.TOP_CENTER;
	}

	@ConfigItem(keyName = "overlayMaxStacked", name = "Most banners at once", description = "Oldest go"
		+ " first.", section = inGameSection, position = 2)
	@Range(min = 1, max = 10)
	default int overlayMaxStacked()
	{
		return 3;
	}

	@ConfigItem(keyName = "overlayGroupBox", name = "Group box",
		description = "On screen: who is online, their worlds, and the latest events.", section = inGameSection,
		position = 3)
	default boolean overlayGroupBox()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayGroupBoxEvents", name = "Group box events", description = "Events the box lists.",
		section = inGameSection, position = 4)
	@Range(min = 1, max = 20)
	default int overlayGroupBoxEvents()
	{
		return 5;
	}

	@ConfigItem(keyName = "overlayPathStop", name = "Path stop card",
		description = "On screen while you path from the Shopping tab: the stop, what to get there and for whom.",
		section = inGameSection, position = 48)
	default boolean overlayPathStop()
	{
		return false;
	}

	@ConfigItem(
		keyName = "groupWorldMap",
		name = "Show members on world map",
		description = "Needs 'Share stats' on both sides.",
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
		description = "'::find whip' shows who has it and who last took it. Only you see the answer.",
		section = inGameSection,
		position = 6
	)
	default boolean groupFindCommand()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayValuableDrop", name = "Banner: Valuable drop",
		description = "Worth over the Thresholds value.", section = inGameSection, position = 7)
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

	@ConfigItem(keyName = "overlayCollectionLog", name = "Banner: New collection log slot",
		description = "A new collection log slot.", section = inGameSection, position = 9)
	default boolean overlayCollectionLog()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayUntradeableUnique", name = "Banner: Untradeable unique",
		description = "An untradeable unique from a drop.", section = inGameSection, position = 10)
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

	@ConfigItem(keyName = "overlayClue", name = "Banner: Clue reward", description = "Worth over the Thresholds value.",
		section = inGameSection, position = 12)
	default boolean overlayClue()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayDryStreak", name = "Banner: Dry streak", description = "Kills past a boss's unique rate.",
		section = inGameSection, position = 13)
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

	@ConfigItem(keyName = "overlayDeathValue", name = "Banner: Value lost on death", description = "What a death cost.",
		section = inGameSection, position = 15)
	default boolean overlayDeathValue()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayGravestone", name = "Banner: Gravestone expiring",
		description = "A gravestone is about to expire.", section = inGameSection, position = 16)
	default boolean overlayGravestone()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayHardcoreLife", name = "Banner: Hardcore life lost",
		description = "The group lost a life.", section = inGameSection, position = 17)
	default boolean overlayHardcoreLife()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayWilderness", name = "Banner: Wilderness and skull",
		description = "A member entered the Wilderness or got skulled.", section = inGameSection, position = 18)
	default boolean overlayWilderness()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayStorageDeposit", name = "Banner: Big storage deposit",
		description = "A big group storage deposit.", section = inGameSection, position = 19)
	default boolean overlayStorageDeposit()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayStorageWithdrawal", name = "Banner: Big storage withdrawal",
		description = "A big group storage withdrawal.", section = inGameSection, position = 20)
	default boolean overlayStorageWithdrawal()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayStorageSummary", name = "Banner: Storage visit summary",
		description = "Each group storage visit.", section = inGameSection, position = 21)
	default boolean overlayStorageSummary()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayStorageMinimum", name = "Banner: Storage minimum hit",
		description = "A storage minimum was hit.", section = inGameSection, position = 22)
	default boolean overlayStorageMinimum()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayStorageLast", name = "Banner: Took the last one",
		description = "Someone took the last of an item.", section = inGameSection, position = 23)
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

	@ConfigItem(keyName = "overlayTotalLevel", name = "Banner: Total level milestones",
		description = "Every 100 total levels.", section = inGameSection, position = 28)
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

	@ConfigItem(keyName = "overlayQuestPoints", name = "Banner: Quest point milestones",
		description = "Every 25 quest points, and every quest done.", section = inGameSection, position = 30)
	default boolean overlayQuestPoints()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayDiary", name = "Banner: Achievement diary tier",
		description = "A diary tier completed.", section = inGameSection, position = 31)
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

	@ConfigItem(keyName = "overlayKcMilestone", name = "Banner: Kill count milestone",
		description = "A boss kill count milestone.", section = inGameSection, position = 33)
	default boolean overlayKcMilestone()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayPersonalBest", name = "Banner: New personal best", description = "A new best time.",
		section = inGameSection, position = 34)
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

	@ConfigItem(keyName = "overlayFarming", name = "Banner: Farming runs ready",
		description = "Herb or tree patches grown.", section = inGameSection, position = 36)
	default boolean overlayFarming()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayBirdhouse", name = "Banner: Bird houses, Hespori",
		description = "Bird houses or Hespori done.", section = inGameSection, position = 37)
	default boolean overlayBirdhouse()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayOnline", name = "Banner: Online", description = "A member logged in, with the world.",
		section = inGameSection, position = 38)
	default boolean overlayOnline()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayBack", name = "Banner: Back after a break",
		description = "A member is back after days away.", section = inGameSection, position = 39)
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

	@ConfigItem(keyName = "shoppingShopAlert", name = "Shopping: shop alert",
		description = "A chat line near a shop that sells something a Shopping list needs, once per visit.",
		section = inGameSection, position = 42)
	default boolean shoppingShopAlert()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayRequest", name = "Banner: Shopping requests",
		description = "A request made, offered, or done.", section = inGameSection, position = 43)
	default boolean overlayRequest()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayActivityStart", name = "Banner: Activity started",
		description = "Fight Caves, Inferno or Colosseum.", section = inGameSection, position = 44)
	default boolean overlayActivityStart()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayActivityWave", name = "Banner: Activity wave",
		description = "A wave milestone or final boss wave.", section = inGameSection, position = 45)
	default boolean overlayActivityWave()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayActivityComplete", name = "Banner: Activity completed",
		description = "Fight Caves, Inferno or Colosseum done.", section = inGameSection, position = 46)
	default boolean overlayActivityComplete()
	{
		return false;
	}

	@ConfigItem(keyName = "overlayActivityDeath", name = "Banner: Activity death",
		description = "A member died there, with the wave.", section = inGameSection, position = 47)
	default boolean overlayActivityDeath()
	{
		return false;
	}

	// ---- 7. Thresholds: what counts. Both Discord and the banners read these. ----

	@ConfigItem(keyName = "eventDropMinValue", name = "Valuable drop (gp)",
		description = "A drop counts when one item stack is worth at least this.", section = thresholdsSection,
		position = 0)
	@Units(GP_PLAIN)
	default int eventDropMinValue()
	{
		return 1_000_000;
	}

	@ConfigItem(keyName = "eventStorageMinValue", name = "Big storage (gp)",
		description = "A group storage deposit or withdrawal worth at least this is big.", section = thresholdsSection,
		position = 1)
	@Units(GP_PLAIN)
	default int eventStorageMinValue()
	{
		return 1_000_000;
	}

	@ConfigItem(keyName = "eventClueMinValue", name = "Clue reward (gp)",
		description = "A clue reward counts when worth at least this.", section = thresholdsSection, position = 2)
	@Units(GP_PLAIN)
	default int eventClueMinValue()
	{
		return 1_000_000;
	}

	@ConfigItem(
		keyName = "groupDropMinValue",
		name = "Shared drops (gp)",
		description = "Only stacks worth at least this are shared with your group. 0 shares all.",
		section = thresholdsSection,
		position = 3
	)
	@Units(GP_PLAIN)
	default int groupDropMinValue()
	{
		return 10_000;
	}

	@ConfigItem(keyName = "eventLevelMin", name = "Level ups from level",
		description = "Level ups below this are ignored.", section = thresholdsSection, position = 4)
	@Range(min = 1, max = 99)
	default int eventLevelMin()
	{
		return 1;
	}

	@ConfigItem(keyName = "eventLevelEvery", name = "Post every N levels",
		description = "E.g. 5: 60, 65, 70. 99s and milestones always count. Your leader's group settings override this.",
		section = thresholdsSection, position = 5)
	@Range(min = 1, max = 98)
	default int eventLevelEvery()
	{
		return 1;
	}

	@ConfigItem(keyName = "eventKcEvery", name = "KC milestone every", description = "Boss kills per milestone. 0 is off.",
		section = thresholdsSection, position = 6)
	default int eventKcEvery()
	{
		return 100;
	}

	@ConfigItem(keyName = "eventBackAfterDays", name = "Back after (days)",
		description = "Days away that count as 'Back after a break'.", section = thresholdsSection, position = 7)
	@Range(min = 1, max = 365)
	default int eventBackAfterDays()
	{
		return 7;
	}

	@ConfigItem(keyName = "eventGravestoneWarnMinutes", name = "Gravestone warning",
		description = "Warn with this many of the 15 minutes left.", section = thresholdsSection, position = 8)
	@Range(min = 1, max = 14)
	@Units(Units.MINUTES)
	default int eventGravestoneWarnMinutes()
	{
		return 5;
	}

	@ConfigItem(keyName = "eventUntradeableIgnore", name = "Untradeables to ignore",
		description = "Comma-separated name starts that are not uniques.", section = thresholdsSection, position = 9)
	default String eventUntradeableIgnore()
	{
		return EventDetect.DEFAULT_UNTRADEABLE_IGNORE;
	}

	@ConfigItem(keyName = "eventWaveEvery", name = "Post every N waves",
		description = "Waves per milestone; boss waves always count. 0 is boss waves only.", section = thresholdsSection,
		position = 10)
	@Range(min = 0, max = 100)
	default int eventWaveEvery()
	{
		return 10;
	}

	// ---- 8. Advanced: ::find colours ----

	@ConfigItem(keyName = "findQueryColour", name = "Search colour", description = "Your search words in ::find.",
		section = advancedSection, position = 0)
	default Color findQueryColour()
	{
		return new Color(0xD0, 0x5A, 0x00);
	}

	@ConfigItem(keyName = "findColourItemNames", name = "Colour item names",
		description = "Also colour item names in ::find.", section = advancedSection, position = 1)
	default boolean findColourItemNames()
	{
		return false;
	}

	@ConfigItem(keyName = "findYourBankColour", name = "Your bank colour", description = "Your bank.",
		section = advancedSection, position = 2)
	default Color findYourBankColour()
	{
		return new Color(0x1E, 0x8C, 0x1E);
	}

	@ConfigItem(keyName = "findMemberBankColour", name = "Member bank colour",
		description = "Other members' banks.", section = advancedSection, position = 3)
	default Color findMemberBankColour()
	{
		return new Color(0x8A, 0x2B, 0xE2);
	}

	@ConfigItem(keyName = "findStorageColour", name = "Storage colour",
		description = "Group storage, and who moved it.", section = advancedSection, position = 4)
	default Color findStorageColour()
	{
		return new Color(0xB8, 0x86, 0x0B);
	}

	@ConfigItem(keyName = "findCarriedColour", name = "Carried colour",
		description = "Inventory, worn, pouch, quiver or seed vault.", section = advancedSection, position = 5)
	default Color findCarriedColour()
	{
		return new Color(0x00, 0x8B, 0x8B);
	}

	@ConfigItem(keyName = "findItemColour", name = "Item name colour",
		description = "Item names, with 'Colour item names' on.", section = advancedSection, position = 6)
	default Color findItemColour()
	{
		return new Color(0x1E, 0x5A, 0xA8);
	}

	@ConfigItem(keyName = "showUpdateNotes", name = "Show update notes",
		description = "A chat hello on install, and what is new after each update.", section = advancedSection,
		position = 7)
	default boolean showUpdateNotes()
	{
		return true;
	}

	/** The last version whose update notes were handled; written by the plugin, never shown. */
	@ConfigItem(keyName = KEY_LAST_SEEN_VERSION, name = "Last seen version", description = "Set by the plugin.",
		hidden = true, section = advancedSection, position = 8)
	default String lastSeenVersion()
	{
		return "";
	}

	@ConfigItem(keyName = KEY_OPT_OUT_TYPES, name = "Kinds not to post", description = "Set in the Settings tab.",
		hidden = true, section = advancedSection, position = 9)
	default String eventOptOutTypes()
	{
		return "";
	}

	@ConfigItem(keyName = KEY_MUTE_TYPES, name = "Kinds not to show", description = "Set in the Settings tab.",
		hidden = true, section = advancedSection, position = 10)
	default String overlayMuteTypes()
	{
		return "";
	}
}
