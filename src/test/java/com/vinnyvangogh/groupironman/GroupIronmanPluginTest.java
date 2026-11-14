package com.vinnyvangogh.groupironman;

import com.vinnyvangogh.scenarios.ScenariosPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Launches a developer-mode RuneLite client with this plugin side-loaded.
 * Run it with {@code ./gradlew run}.
 *
 * <p>This is a launcher, not a test. A clean JVM start proves the plugin
 * loads and nothing else. The "Scenarios (dev)" sidebar it adds posts made-up
 * game events into this client; see TESTING.md.
 */
public class GroupIronmanPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ScenariosPlugin.use(GimEventKinds.kinds());
		ExternalPluginManager.loadBuiltin(GroupIronmanPlugin.class, ScenariosPlugin.class);
		RuneLite.main(args);
	}
}
