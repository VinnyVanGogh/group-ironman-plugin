package com.vinnyvangogh.groupironman;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.party.PartyDataService;
import net.runelite.client.party.PartyDataType;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayMenuEntry;

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
	// The Plugin Hub name; getPluginDirectory() keeps the cache in .runelite/plugin-data/gim-utilities.
	internalName = "gim-utilities",
	// RuneLite moves the pre-Filepath cache folder (.runelite/gim-utilities) there on first use.
	legacyDataDirectory = GroupService.CACHE_DIR,
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

	@Inject
	private Client client;

	@Inject
	private ConfigManager configManager;

	/**
	 * The Shopping tab's open lists and items ({@link ShopFolds}), in the logged-in character's RuneLite profile:
	 * not a setting, never shown or sent. Frozen once published.
	 */
	static final String SHOPPING_OPEN_KEY = "shoppingOpen";

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private PluginManager pluginManager;

	/** Bank tiles for "Path to" an item in group storage; read once. */
	private final List<ItemPaths.Stop> banks = ItemPaths.loadBanks();
	/** The path handed to Shortest Path; null when not running. */
	private volatile PathSession paths;
	private GroupIronmanPanel panel;
	private NavigationButton navButton;
	private SidebarButton sidebar;

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
		// Shortest Path is reached only through RuneLite's own PluginMessage: no reflection, no dependency.
		paths = new PathSession(new ShortestPathBridge(pluginManager::getPlugins,
			p -> p instanceof Plugin && pluginManager.isPluginEnabled((Plugin) p),
			ShortestPathBridge.clientThreadEventPoster(clientThread::invoke, eventBus::post)));
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
			public void openSettings()
			{
				openOwnSettings();
			}

			@Override
			public void turnOnSharing()
			{
				group.turnOnSharing();
				// The player's own click, as Paste is: joins once, right away, now that sync is on.
				clientThread.invokeLater(group::joinGroup);
			}

			@Override
			public void postToDiscord()
			{
				group.postToDiscord();
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
			public GovernanceSync.SaveResult save(Map<String, Long> values)
			{
				return group.saveGroupSettings(values);
			}

			@Override
			public boolean shareWebhooks(boolean share)
			{
				return group.shareWebhooks(share);
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

			@Override
			public boolean addList(String name, String preset, List<String> picks, List<String> members,
				Map<Integer, Integer> items)
			{
				return group.addShoppingList(name, preset, picks, members, items);
			}

			@Override
			public boolean renameList(String id, String name)
			{
				return group.renameShoppingList(id, name);
			}

			@Override
			public boolean removeList(String id)
			{
				return group.removeShoppingList(id);
			}

			@Override
			public void searchItems(String query, Consumer<List<ShoppingTab.Hit>> done)
			{
				group.searchItems(query, done);
			}

			@Override
			public boolean addRequest(String name, Map<Integer, Integer> items)
			{
				return group.addRequest(name, items);
			}

			@Override
			public boolean renameRequest(String id, String name)
			{
				return group.renameRequest(id, name);
			}

			@Override
			public boolean removeRequest(String id)
			{
				return group.removeRequest(id);
			}

			@Override
			public boolean closeRequest(String id)
			{
				return group.closeRequest(id);
			}

			@Override
			public boolean closeRequestItem(String id, int itemId)
			{
				return group.closeRequestItem(id, itemId);
			}

			@Override
			public boolean claimRequest(String id, int itemId, boolean claimed)
			{
				return group.claimRequest(id, itemId, claimed);
			}

			@Override
			public String openLists()
			{
				return configManager.getRSProfileConfiguration(GroupIronmanConfig.GROUP, SHOPPING_OPEN_KEY);
			}

			@Override
			public void saveOpenLists(String keys)
			{
				if (keys == null)
				{
					configManager.unsetRSProfileConfiguration(GroupIronmanConfig.GROUP, SHOPPING_OPEN_KEY);
				}
				else
				{
					configManager.setRSProfileConfiguration(GroupIronmanConfig.GROUP, SHOPPING_OPEN_KEY, keys);
				}
			}

			@Override
			public String pathTo(ItemPaths.Target why, ItemPaths.Stop to)
			{
				PathSession s = paths;
				String said = s == null ? "" : s.go(why, to, group.here() != null);
				redraw();
				return said;
			}

			@Override
			public String clearPath()
			{
				PathSession s = paths;
				String said = s == null ? "" : s.stop();
				redraw();
				return said;
			}
		});
		navButton = NavigationButton.builder()
			.tooltip(NAME)
			.icon(icon())
			.priority(9)
			.panel(panel)
			.build();
		final NavigationButton button = navButton;
		sidebar = new SidebarButton(() -> clientToolbar.addNavigation(button),
			() -> clientToolbar.removeNavigation(button));
		group.startUp(this::redraw, this::getPluginDirectory);
		events.startUp(group);
		eventBus.register(events);
		sidebar.startUp();
		redraw();

		// Installed or turned on mid-session: no LOGGED_IN is coming, so say it now.
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invoke(this::postUpdateNotes);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			postUpdateNotes();
		}
	}

	/**
	 * Posts the welcome or "what's new" lines once per version (UpdateNotes), and
	 * stores the version as seen even with the setting off, so turning it on later
	 * does not bring back notes for an update long past. Client thread.
	 */
	private void postUpdateNotes()
	{
		final String lastSeen = config.lastSeenVersion();
		if (!UpdateNotes.needsRecord(lastSeen))
		{
			return;
		}
		configManager.setConfiguration(GroupIronmanConfig.GROUP, GroupIronmanConfig.KEY_LAST_SEEN_VERSION,
			UpdateNotes.VERSION);
		final List<String> lines = UpdateNotes.toShow(lastSeen, config.showUpdateNotes());
		for (int i = 0; i < lines.size(); i++)
		{
			final String message = new ChatMessageBuilder()
				.append(i == 0 ? ChatColorType.HIGHLIGHT : ChatColorType.NORMAL)
				.append(lines.get(i))
				.build();
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage(message)
				.build());
		}
	}

	@Override
	protected void shutDown()
	{
		// Turning the plugin off takes its path with it.
		PathSession s = paths;
		paths = null;
		if (s != null)
		{
			s.stop();
		}
		wsClient.unregisterMessage(VvgGroupIronmanMessage.class);
		eventBus.unregister(events);
		events.shutDown();
		eventBus.unregister(group);
		group.shutDown();
		partyDataService.unsubscribe(this, PartyDataType.values());
		markers.clear();
		sidebar.shutDown();
		sidebar = null;
		panel = null;
		navButton = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		// The plugin's own once-per-version bookkeeping changes nothing the features read.
		if (!GroupIronmanConfig.GROUP.equals(event.getGroup())
			|| GroupIronmanConfig.KEY_LAST_SEEN_VERSION.equals(event.getKey()))
		{
			return;
		}
		group.reloadConfig();
		events.reloadConfig();
		// The sidebar button stays whatever the switches say; the panel shows the change.
		redraw();
	}

	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		group.onPartyChanged();
		redraw();
	}

	/**
	 * Opens this plugin's page in RuneLite's settings. Posts the event an overlay's
	 * "Configure" menu entry posts; RuneLite's ConfigPlugin handles it
	 * ({@code onOverlayMenuClicked}) by opening the settings panel on the overlay's
	 * plugin. The overlay is a stand-in that is never added or drawn.
	 */
	private void openOwnSettings()
	{
		Overlay self = new Overlay(this)
		{
			@Override
			public Dimension render(Graphics2D graphics)
			{
				return null;
			}
		};
		eventBus.post(new OverlayMenuClicked(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, "", NAME),
			self));
	}

	private void redraw()
	{
		GroupIronmanPanel p = panel;
		if (p == null)
		{
			return;
		}
		GroupIronmanPanel.View view = new GroupIronmanPanel.View(group.syncState(), group.roster(), group.members(),
			group.lookup, group.switches(), group.regions(), group.since(), group.self(),
			group.presence(), settingsView(), group.anyOn(), group.sharingSettings(), shoppingView());
		SwingUtilities.invokeLater(() -> p.render(view));
		updateMarkers();
	}

	/** The Shopping tab's view, or null before the lists have started. */
	private ShoppingTab.View shoppingView()
	{
		ShoppingSync shop = group.shopping();
		if (shop == null)
		{
			return null;
		}
		List<ShoppingList> lists = shop.active();
		List<String> roster = new ArrayList<>(group.members().names());
		if (roster.isEmpty() && !group.self().isEmpty())
		{
			roster.add(group.self());
		}
		ShoppingBoard.Inputs in = group.shoppingInputs();
		List<ShoppingBoard.Card> cards = ShoppingBoard.of(lists, in);
		List<RequestBoard.Card> requests = group.requestCards();
		ItemPaths.Planner planner = ItemPaths.Planner.of(in, banks, group.here());
		// The path goes when its item is done, its list removed or its request closed.
		PathSession s = paths;
		if (s != null)
		{
			s.recheck(t -> ItemPaths.forTarget(t, cards, requests, planner));
		}
		return new ShoppingTab.View(cards, group.presets().presets(), roster, shop.history(),
			!group.self().isEmpty(), group.syncing(), requests, group.self(), planner,
			s == null ? "" : s.active());
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
		return GroupSettingsTab.View.of(g, self, group.members().names(), current, MemberPrefs.of(config),
			group.syncing());
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
