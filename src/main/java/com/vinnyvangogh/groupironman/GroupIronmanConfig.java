package com.vinnyvangogh.groupironman;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

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
}
