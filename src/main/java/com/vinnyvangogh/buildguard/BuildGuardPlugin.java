package com.vinnyvangogh.buildguard;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * Build Guard.
 *
 * <p>Nothing is wired up yet, deliberately. The four in-scope pieces exist as
 * stubs that throw — {@code rules}, {@code verdict}, {@code warning} and
 * {@code lamp} — and `docs/SOURCE-MAP.md` names the questpath-plugin file each
 * one ports from.
 *
 * <p>The engine this plugin is carved out of is a port of a Go planner held to
 * 218 generated verdicts. Until those vectors run green here, this plugin has
 * no opinion about any quest, and says so rather than guessing.
 */
@Slf4j
@PluginDescriptor(
	name = "Build Guard",
	description = "Warns before a quest, lamp or activity gives XP that breaks a capped account's build",
	tags = {"build", "pure", "zerker", "skiller", "quest", "lamp", "xp"}
)
public class BuildGuardPlugin extends Plugin
{
	@Provides
	BuildGuardConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BuildGuardConfig.class);
	}

	@Override
	protected void startUp()
	{
		// Everything registered here must be unregistered in shutDown().
	}

	@Override
	protected void shutDown()
	{
		// Mirror of startUp(), in reverse. Never block here: no
		// awaitTermination, no joins, no sleeps.
	}
}
