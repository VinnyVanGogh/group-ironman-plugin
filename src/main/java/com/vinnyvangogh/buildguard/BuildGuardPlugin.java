package com.vinnyvangogh.buildguard;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * Build Guard.
 *
 * <p>The shape to keep: state is rebuilt once per game tick and published as
 * an immutable snapshot. Anything that renders reads the latest snapshot and
 * computes nothing itself, because rendering runs every frame and the game
 * state it describes only changes on a tick.
 */
@Slf4j
@PluginDescriptor(
	name = "Build Guard",
	description = "Warns before a quest, lamp or activity gives XP that breaks a capped account's build",
	tags = {"build", "pure", "zerker", "skiller", "quest", "lamp", "xp"}
)
public class BuildGuardPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private BuildGuardConfig config;

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

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		// Rebuild the snapshot here. Cheap work only: this runs 100 times a
		// minute. Scanning the whole scene belongs in a spawn/despawn
		// listener that maintains its own collection, not here.
	}

}
