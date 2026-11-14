package com.vinnyvangogh.scenarios;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

/**
 * Developer client only: a "Scenarios" sidebar that posts a scenario's events into the client's real EventBus,
 * on the client thread, as the game would. Lives in src/test, so it is never in the Plugin Hub jar.
 *
 * <p>Started by the plugin's {@code *PluginTest} launcher, which calls {@link #use} first:
 * <pre>
 *   ScenariosPlugin.use(MyEventKinds.kinds());
 *   ExternalPluginManager.loadBuiltin(MyPlugin.class, ScenariosPlugin.class);
 * </pre>
 * No game input is sent and nothing reaches Jagex: an event is only a Java object handed to subscribers.
 */
@PluginDescriptor(
	name = "Scenarios (dev)",
	description = "Posts made-up game events from src/test/resources/scenarios into the developer client",
	developerPlugin = true
)
public class ScenariosPlugin extends Plugin
{
	/** Set by the launcher before RuneLite starts; read once when the plugin starts. */
	private static volatile EventKinds kinds = EventKinds.core();

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private EventBus eventBus;

	@Inject
	private ClientThread clientThread;

	private NavigationButton button;

	/** The plugin's kinds (core plus its own), for the panel to read scenarios with. */
	public static void use(EventKinds pluginKinds)
	{
		kinds = pluginKinds;
	}

	@Override
	protected void startUp()
	{
		ScenarioPanel panel = new ScenarioPanel(kinds, event -> clientThread.invokeLater(() -> eventBus.post(event)));
		button = NavigationButton.builder()
			.tooltip("Scenarios (dev)")
			.icon(icon())
			.priority(99)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(button);
		SwingUtilities.invokeLater(panel::reload);
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(button);
		button = null;
	}

	/** A drawn "S" so the template needs no image resource. */
	private static BufferedImage icon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setColor(new Color(0x2E7D32));
		g.fillRoundRect(0, 0, 16, 16, 5, 5);
		g.setColor(Color.WHITE);
		g.drawString("S", 4, 13);
		g.dispose();
		return image;
	}
}
