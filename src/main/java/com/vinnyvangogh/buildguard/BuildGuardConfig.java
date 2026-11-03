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
}
