package com.vinnyvangogh.groupironman;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

/**
 * Group Ironman.
 *
 * <p>Opt-in group sharing over RuneLite Party: member banks, the group storage
 * log, live stats with boosts and where each member is, inventories and
 * equipment, rune pouch, quiver and seed vault, drops and collection log
 * slots, "who has it?" and {@code ::find}, storage minimums and world map
 * markers. The features live in {@link GroupService}, which this plugin
 * registers on the event bus; this class owns the side panel and its button.
 *
 * <p>Party traffic goes only through RuneLite's own {@link PartyService}; this
 * plugin makes no network calls of its own and runs no server.
 */
@Slf4j
@PluginDescriptor(
	name = GroupIronmanPlugin.NAME,
	description = "Share banks, group storage changes, stats and drops with your Group Ironman team over RuneLite Party",
	tags = {"group", "ironman", "gim", "party", "bank", "storage", "shared"}
)
public class GroupIronmanPlugin extends Plugin
{
	/** Shown in the plugin list and at the start of every chat line and notification this plugin writes. */
	static final String NAME = "Group Ironman";

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private PartyService partyService;

	@Inject
	private WSClient wsClient;

	@Inject
	private EventBus eventBus;

	@Inject
	private GroupIronmanConfig config;

	@Inject
	private GroupService group;

	@Inject
	private MemberMapMarkers markers;

	private GroupIronmanPanel panel;
	private NavigationButton navButton;

	@Provides
	GroupIronmanConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GroupIronmanConfig.class);
	}

	@Override
	protected void startUp()
	{
		wsClient.registerMessage(VvgGroupIronmanMessage.class);
		eventBus.register(group);
		panel = new GroupIronmanPanel();
		navButton = NavigationButton.builder()
			.tooltip(NAME)
			.icon(icon())
			.priority(9)
			.panel(panel)
			.build();
		group.startUp(this::redraw);
		updateNavButton();
	}

	@Override
	protected void shutDown()
	{
		wsClient.unregisterMessage(VvgGroupIronmanMessage.class);
		eventBus.unregister(group);
		group.shutDown();
		markers.clear();
		clientToolbar.removeNavigation(navButton);
		panel = null;
		navButton = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!GroupIronmanConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		group.reloadConfig();
		updateNavButton();
	}

	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		group.onPartyChanged();
		redraw();
	}

	/** The panel button shows only while at least one group feature is on. */
	private void updateNavButton()
	{
		if (group.anyOn())
		{
			clientToolbar.addNavigation(navButton);
		}
		else
		{
			clientToolbar.removeNavigation(navButton);
		}
		redraw();
	}

	private void redraw()
	{
		GroupIronmanPanel p = panel;
		if (p == null)
		{
			return;
		}
		GroupIronmanPanel.View view = new GroupIronmanPanel.View(partyService.isInParty(), group.roster(),
			group.lookup, group.switches(), group.regions(), group.since(), group.self());
		SwingUtilities.invokeLater(() -> p.render(view));
		updateMarkers();
	}

	private void updateMarkers()
	{
		if (config.groupWorldMap() && config.groupShareStats() && partyService.isInParty())
		{
			markers.update(group.roster().onlineStats(System.currentTimeMillis()), group.self());
		}
		else
		{
			markers.clear();
		}
	}

	/** A plain 16x16 three-figure badge, drawn rather than shipped: icon.png is still the scaffold's placeholder. */
	private static BufferedImage icon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setColor(new Color(0x2E, 0x6B, 0x3A));
		g.fillRoundRect(1, 1, 14, 14, 5, 5);
		g.setColor(new Color(0xE8, 0xE0, 0xC8));
		g.fillOval(2, 5, 4, 4);
		g.fillOval(6, 3, 4, 4);
		g.fillOval(10, 5, 4, 4);
		g.fillRect(2, 10, 12, 3);
		g.dispose();
		return image;
	}
}
