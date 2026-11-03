package com.vinnyvangogh.buildguard;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

/**
 * Settings for Build Guard.
 *
 * <p>The group name and every {@code keyName} below are frozen once a version
 * is live on the Plugin Hub: renaming one silently resets that setting for
 * every user who has it. Choose them as if they were permanent, because they
 * are.
 */
@ConfigGroup(BuildGuardConfig.GROUP)
public interface BuildGuardConfig extends Config
{
	/** Frozen. See the class javadoc. */
	String GROUP = "build-guard";

	@ConfigSection(
		name = "Display",
		description = "What is shown and where",
		position = 0
	)
	String displaySection = "display";

	@ConfigSection(
		name = "Group (RuneLite Party)",
		description = "Share your build with your RuneLite Party, e.g. a Group Ironman team. Everything here is off until you turn it on.",
		position = 1
	)
	String partySection = "party";

	@ConfigItem(
		keyName = "enabled",
		name = "Enabled",
		description = "Turns the readout on and off without disabling the plugin",
		section = displaySection,
		position = 0
	)
	default boolean enabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "buildRules",
		name = "Build rules",
		description = "One per line or comma: 'Defence: 1' caps Defence at 1, 'Prayer: none' means no XP,"
			+ " 'Strength: goal 99' and 'Ranged: soft' only inform",
		section = displaySection,
		position = 1
	)
	default String buildRules()
	{
		return "";
	}

	@ConfigItem(
		keyName = "warnAttackStyle",
		name = "Warn on attack style",
		description = "Chat warning when your selected attack style trains a skill your rules cap",
		section = displaySection,
		position = 2
	)
	default boolean warnAttackStyle()
	{
		return true;
	}

	@ConfigItem(
		keyName = "partyShareBuild",
		name = "Share build with party",
		description = "Send your build rules and the levels of those skills to your RuneLite Party,"
			+ " and show the party's builds in a side panel",
		section = partySection,
		position = 0
	)
	default boolean partyShareBuild()
	{
		return false;
	}

	@ConfigItem(
		keyName = "partyShareRisk",
		name = "Tell party when at risk",
		description = "Also show your party a line when your attack style is about to give XP your rules cap",
		section = partySection,
		position = 1
	)
	default boolean partyShareRisk()
	{
		return false;
	}

	@ConfigItem(
		keyName = "partyShareGear",
		name = "Share gear list",
		description = "Also send the names of the items you are wearing and carrying, for the panel's 'who has what' list",
		section = partySection,
		position = 2
	)
	default boolean partyShareGear()
	{
		return false;
	}

	@ConfigSection(
		name = "Group banks, storage and stats",
		description = "Group Ironman extras over RuneLite Party. Each is off until you turn it on, and each only"
			+ " shows you other members' data while you share your own.",
		position = 2
	)
	String groupSection = "group";

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
			+ " of the last 200 changes. Only members running Build Guard with this on are logged.",
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
}
