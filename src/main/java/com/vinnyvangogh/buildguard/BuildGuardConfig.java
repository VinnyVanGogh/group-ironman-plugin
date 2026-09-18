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

}
