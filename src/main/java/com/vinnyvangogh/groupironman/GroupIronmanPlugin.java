package com.vinnyvangogh.groupironman;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.party.PartyDataService;
import net.runelite.client.party.PartyDataType;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

/**
 * GIM Utilities.
 *
 * <p>Opt-in group sharing over RuneLite Party: member banks, the group storage
 * log, live stats with boosts and where each member is, inventories and
 * equipment, rune pouch, quiver and seed vault, drops and collection log
 * slots, "who has it?" and {@code ::find}, storage minimums and world map
 * markers. The features live in {@link GroupService}, which this plugin
 * registers on the event bus; this class owns the side panel and its button.
 *
 * <p>Party traffic goes only through RuneLite's own {@code PartyService}. The
 * plugin's only network call of its own is the optional Discord webhook the
 * player sets ({@link GroupEventEngine}); it runs no server. It joins the
 * group's party by itself once there is a group key, so the Party plugin can
 * stay off. The key travels outside the game: "Copy group key" puts it on the
 * computer's clipboard for the player to send (e.g. in Discord) and "Paste
 * group key" saves one; the plugin never types into the game chatbox.
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
	static final String NAME = "GIM Utilities";

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private WSClient wsClient;

	@Inject
	private EventBus eventBus;

	@Inject
	private GroupIronmanConfig config;

	@Inject
	private GroupService group;

	@Inject
	private GroupEventEngine events;

	@Inject
	private MemberMapMarkers markers;

	@Inject
	private PartyDataService partyDataService;

	@Inject
	private ItemManager itemManager;

	@Inject
	private SkillIconManager skillIconManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private SpriteManager spriteManager;

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
		// Member names only travel when some plugin asks for them (PartyDataService sends
		// CHARACTER_NAME only while subscribed). Without the Party plugin, that is us.
		partyDataService.subscribe(this, PartyDataType.CHARACTER_NAME);
		eventBus.register(group);
		panel = new GroupIronmanPanel(itemManager, skillIconManager, spriteManager, new GroupIronmanPanel.Actions()
		{
			@Override
			public void join()
			{
				clientThread.invokeLater(group::joinGroup);
			}

			@Override
			public void switchParty()
			{
				clientThread.invokeLater(group::switchToGroup);
			}

			@Override
			public void leave()
			{
				clientThread.invokeLater(group::leaveGroup);
			}

			@Override
			public void newKey()
			{
				group.newKey();
			}

			@Override
			public String groupKey()
			{
				return group.groupKey();
			}

			@Override
			public boolean pasteKey(String text)
			{
				// Pasting is the player's own click, so it joins once, right away.
				boolean saved = group.pasteKey(text);
				if (saved)
				{
					clientThread.invokeLater(group::joinGroup);
				}
				return saved;
			}

			@Override
			public boolean claim()
			{
				return group.claimLeadership();
			}

			@Override
			public boolean setEditor(String name, boolean editor)
			{
				return group.setEditor(name, editor);
			}

			@Override
			public boolean save(Map<String, Long> values)
			{
				return group.saveGroupSettings(values);
			}

			@Override
			public void forgetLeader()
			{
				group.forgetLeader();
			}

			@Override
			public void optOut(GroupEvent.Type type, boolean overlay, boolean out)
			{
				group.optOut(type, overlay, out);
			}

			@Override
			public void optOutAll(boolean out)
			{
				group.optOutAll(out);
			}
		});
		navButton = NavigationButton.builder()
			.tooltip(NAME)
			.icon(icon())
			.priority(9)
			.panel(panel)
			.build();
		group.startUp(this::redraw);
		events.startUp(group);
		eventBus.register(events);
		updateNavButton();
	}

	@Override
	protected void shutDown()
	{
		wsClient.unregisterMessage(VvgGroupIronmanMessage.class);
		eventBus.unregister(events);
		events.shutDown();
		eventBus.unregister(group);
		group.shutDown();
		partyDataService.unsubscribe(this, PartyDataType.values());
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
		events.reloadConfig();
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
		GroupIronmanPanel.View view = new GroupIronmanPanel.View(group.syncState(), group.roster(), group.members(),
			group.lookup, group.switches(), group.regions(), group.since(), group.self(), settingsView());
		SwingUtilities.invokeLater(() -> p.render(view));
		updateMarkers();
	}

	/** The Settings tab's view, or null before group settings have started. */
	private GroupSettingsTab.View settingsView()
	{
		GroupGovernance g = group.governance();
		if (g == null)
		{
			return null;
		}
		String self = group.self();
		GroupSettingsSchema.Resolved values = group.groupValues();
		Map<String, Long> current = new TreeMap<>();
		for (GroupSettingsSchema.Setting s : GroupSettingsSchema.ALL)
		{
			current.put(s.key, values.value(s.key));
		}
		List<String> members = GroupSettingsTab.gridMembers(group.members().names(), self);
		Set<String> heard = new HashSet<>();
		for (String m : members)
		{
			if (g.knownKey(m) != null)
			{
				heard.add(m);
			}
		}
		return new GroupSettingsTab.View(g.myRole(self), g.roles(), g.settings(), g.unclaimed(), group.syncing(),
			members, self, current, MemberPrefs.of(config), g.prefs(), heard);
	}

	/** Markers only add points to the world map; they never open it (that stays the player's click). */
	private void updateMarkers()
	{
		if (config.groupWorldMap() && config.groupShareStats() && group.syncing())
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
