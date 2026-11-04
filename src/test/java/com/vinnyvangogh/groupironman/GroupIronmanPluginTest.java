package com.vinnyvangogh.groupironman;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Launches a developer-mode RuneLite client with this plugin side-loaded.
 * Run it with {@code ./gradlew run}.
 *
 * <p>This is a launcher, not a test. A clean JVM start proves the plugin
 * loads and nothing else.
 */
public class GroupIronmanPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GroupIronmanPlugin.class);
		RuneLite.main(args);
	}
}
