package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.Friend;
import net.runelite.api.FriendContainer;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanID;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.api.events.ClanMemberJoined;
import net.runelite.api.events.ClanMemberLeft;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.game.ItemStack;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.util.Filepath;
import net.runelite.client.party.messages.UserSync;

/**
 * The Group Ironman extras: member banks, the group storage log, live stats
 * and where each member is, inventories and equipment, rune pouch, quiver and
 * seed vault, the drops and collection log list, online / last seen, "who has
 * it?" and storage minimums.
 *
 * <p>Registered on the event bus by {@link GroupIronmanPlugin} while the plugin
 * runs. Every send goes through {@link GroupOutbox}, so a member with a
 * feature off sends nothing of it; every receive is dropped unless this
 * member shares the same feature.
 *
 * <p>Data only ever comes from this player's own client: their bank and
 * group storage as the game shows them, their own stats. Nothing is read
 * from another player and nothing goes anywhere but RuneLite Party. The one
 * exception is read, not sent: roster members who share no stats are looked up
 * on the public OSRS hiscores with RuneLite's own {@link HiscoreClient}
 * ({@link HiscoreBook}), and the result stays on this client.
 */
@Slf4j
class GroupService
{
	/** Fewest ticks between two stats sends; stats change on every prayer drain tick. */
	static final int STATS_INTERVAL_TICKS = 3;
	/** Stats are resent at least this often (about a minute) so members can tell online from gone. */
	static final int STATS_HEARTBEAT_TICKS = 100;
	/** Walking this many tiles counts as a move worth sending; every step would be a message per tick. */
	static final int MOVE_TILES = 8;
	static final int CARRIED_INTERVAL_TICKS = 5;
	static final int EXTRAS_INTERVAL_TICKS = 5;
	/** How many drops and log slots the panel lists. */
	static final int MAX_EVENT_LINES = 50;
	/** How often the group roster is re-read from the game's clan data. */
	static final int ROSTER_INTERVAL_TICKS = 10;
	/** The chatFilterCheck script callback the base chat filter plugin uses to drop a line. */
	private static final String CHAT_FILTER_CHECK = "chatFilterCheck";

	private static final int[] POUCH_TYPES = {
		VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3,
		VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6
	};
	private static final int[] POUCH_AMOUNTS = {
		VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3,
		VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6
	};
	private static final Set<Integer> POUCH_VARBITS = new HashSet<>();

	static
	{
		for (int i = 0; i < POUCH_TYPES.length; i++)
		{
			POUCH_VARBITS.add(POUCH_TYPES[i]);
			POUCH_VARBITS.add(POUCH_AMOUNTS[i]);
		}
	}

	/**
	 * The folder under .runelite the cache used before the Filepath API. Now only the
	 * PluginDescriptor's legacyDataDirectory, which RuneLite migrates into the plugin's data
	 * directory. Never Build Guard's: both plugins may run side by side.
	 */
	static final String CACHE_DIR = "gim-utilities";
	private static final String CACHE_FILE = "group-cache.json";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private GroupIronmanConfig config;

	@Inject
	private Gson gson;

	@Inject
	private PartyService partyService;

	@Inject
	private ItemManager itemManager;

	@Inject
	private Notifier notifier;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private ConfigManager configManager;

	@Inject
	private HiscoreClient hiscoreClient;

	private final GroupRoster roster = new GroupRoster();
	private final BankAssembler assembler = new BankAssembler();
	private final Map<Integer, String> names = new ConcurrentHashMap<>();
	private final Map<Integer, Long> prices = new ConcurrentHashMap<>();
	/** Equipment slot per item, read on the client thread with the name (ItemManager stats need it). */
	private final Map<Integer, Integer> slots = new ConcurrentHashMap<>();
	private Runnable onChange = () -> { };
	private Callable<Filepath> dataDirectory = () ->
	{
		throw new IOException("plugin data directory not set");
	};
	private GroupSync sync;
	/** Roles and the group's shared settings; null when not running. */
	private volatile GovernanceSync governance;
	/** The leader's signed webhooks document as the cache file keeps it, or null. Never put in the config. */
	private volatile String cachedWebhooks;
	/** The group event engine, told about storage visits, minimums and members' events; null when not running. */
	private volatile GroupEventEngine events;

	private volatile LowStock lowStock = LowStock.parse("");
	private volatile GroupMembers members = GroupMembers.UNKNOWN;
	private volatile GamePresence presence = GamePresence.UNKNOWN;

	// Client thread only.
	private final StorageSession storage = new StorageSession();
	private int ticksSinceRoster = ROSTER_INTERVAL_TICKS;
	private BankSnapshot lastBank;
	private boolean statsDirty;
	private boolean carriedDirty;
	private int ticksSinceStats = STATS_INTERVAL_TICKS;
	private int ticksSinceCarried = CARRIED_INTERVAL_TICKS;
	private int lastRun = -1;
	private int lastSpec = -1;
	private int lastRegion = -1;
	private int lastWorld = -1;
	private int lastX;
	private int lastY;
	private int lastPlane;
	private String lastStatsSent;
	private String lastCarriedSent;
	private boolean extrasDirty;
	private int ticksSinceExtras = EXTRAS_INTERVAL_TICKS;
	private String lastExtrasSent;
	private Map<Integer, Integer> seedVault = new TreeMap<>();
	private long seedVaultAt = -1;
	private boolean session;
	/** This login has not asked the hiscores about the roster yet. */
	private boolean hiscoresOnLogin = true;

	private volatile RegionNames regions = RegionNames.parse("");
	/** Nothing is written to disk until the file has been read, or a save would replace it with less. */
	private volatile boolean cacheLoaded;
	/** When this player last played before this session; 0 when not known. */
	private volatile long since;
	private volatile String self = "";

	final ItemLookup lookup = new ItemLookup()
	{
		@Override
		public String name(int id)
		{
			String name = names.get(id);
			return name == null ? "item " + id : name;
		}

		@Override
		public long price(int id)
		{
			return prices.getOrDefault(id, 0L);
		}

		@Override
		public int slot(int id)
		{
			return slots.getOrDefault(id, -1);
		}
	};

	/**
	 * @param dataDirectory the plugin's own data directory (Plugin#getPluginDirectory). Called on
	 *                      the executor, since the first call may migrate the legacy folder.
	 */
	void startUp(Runnable onChange, Callable<Filepath> dataDirectory)
	{
		this.onChange = onChange;
		this.dataDirectory = dataDirectory;
		sync = new GroupSync(new GroupSync.Party()
		{
			@Override
			public String passphrase()
			{
				return partyService.isInParty() ? partyService.getPartyPassphrase() : null;
			}

			@Override
			public void changeParty(String passphrase)
			{
				partyService.changeParty(passphrase);
			}
		});
		governance = new GovernanceSync(gson, new GovernanceSync.Store()
		{
			@Override
			public String get(String key)
			{
				return configManager.getConfiguration(GroupIronmanConfig.GROUP, key);
			}

			@Override
			public void set(String key, String value)
			{
				if (value == null)
				{
					configManager.unsetConfiguration(GroupIronmanConfig.GROUP, key);
				}
				else
				{
					configManager.setConfiguration(GroupIronmanConfig.GROUP, key, value);
				}
			}
		}, payload -> partyService.send(new VvgGroupIronmanMessage(payload)), () -> this.onChange.run(), webhooks ->
		{
			// The leader's shared webhooks: kept in the group cache file, never the RuneLite config.
			cachedWebhooks = webhooks;
			saveCache();
		});
		cacheLoaded = false;
		since = 0;
		reloadConfig();
		executor.execute(() ->
		{
			regions = RegionNames.load();
			loadCache();
		});
	}

	void shutDown()
	{
		if (session)
		{
			roster.markPlayed(System.currentTimeMillis());
		}
		saveCache();
		// Only a party this plugin joined; a raid party the player joined stays.
		if (sync != null)
		{
			sync.leaveOwn();
		}
		members = GroupMembers.UNKNOWN;
		governance = null;
		cachedWebhooks = null;
		cacheLoaded = false;
		session = false;
		hiscoresOnLogin = true;
		seedVault = new TreeMap<>();
		seedVaultAt = -1;
		lastExtrasSent = null;
		roster.clearAll();
		assembler.clear();
		names.clear();
		prices.clear();
		storage.close(0, "");
		lastBank = null;
		lastStatsSent = null;
		lastCarriedSent = null;
		onChange = () -> { };
	}

	GroupRoster roster()
	{
		return roster;
	}

	void events(GroupEventEngine engine)
	{
		events = engine;
	}

	/** This player's name, as events are filed under it. */
	String localPlayer()
	{
		return localName();
	}

	/** Client thread. Names and prices for items an event is about to describe. */
	void resolveItems(Collection<Integer> ids)
	{
		resolveNow(ids);
	}

	/** Client thread. One of this player's group events (not a drop or log slot) for the party, when shared. */
	void shareGroupEvent(GroupEvent event, boolean share)
	{
		if (!syncing())
		{
			return;
		}
		for (String payload : GroupOutbox.groupEvents(gson, share, Collections.singletonList(event)))
		{
			partyService.send(new VvgGroupIronmanMessage(payload));
		}
	}

	GroupOutbox.Switches switches()
	{
		return new GroupOutbox.Switches(config.groupShareBank(), config.groupShareStorage(),
			config.groupShareStats(), config.groupShareCarried(), config.groupShareExtras(),
			config.groupShareDrops());
	}

	RegionNames regions()
	{
		return regions;
	}

	/** When this player last played before this session, 0 when not known (first run, or still loading). */
	long since()
	{
		return since;
	}

	/** This player's name, for leaving their own drops out of "since you last played". */
	String self()
	{
		return self;
	}

	/** The in-game group roster, {@link GroupMembers#UNKNOWN} until the game has sent it. */
	GroupMembers members()
	{
		return members;
	}

	GroupSync.State syncState()
	{
		return sync == null ? GroupSync.State.OFF : sync.state(config.groupSync(), members.group(), config.groupKey());
	}

	/** True only in this group's own party: nothing is sent to, or taken from, any other party. */
	boolean syncing()
	{
		return syncState() == GroupSync.State.CONNECTED;
	}

	/**
	 * The "Join group sync" button, and a successful "Paste group key". Joining is
	 * always the player's own click: "Auto-rejoin for parties" is a rejected
	 * RuneLite feature ("would put undue strain on the party servers",
	 * github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features).
	 */
	void joinGroup()
	{
		if (sync.join(config.groupSync(), members.group(), config.groupKey()))
		{
			onChange.run();
		}
	}

	/** The "Switch to group sync" button, shown only while in a party the player joined themselves. */
	void switchToGroup()
	{
		if (sync.switchParty(config.groupSync(), members.group(), config.groupKey()))
		{
			onChange.run();
		}
	}

	void leaveGroup()
	{
		if (sync.leaveOwn())
		{
			onChange.run();
		}
	}

	/**
	 * "Create group key" and "New key": a fresh random key, which the player
	 * then shares. The member who creates the key leads a group that has no
	 * leader yet (Board 2026-11-10); a group that has one keeps it.
	 */
	void newKey()
	{
		configManager.setConfiguration(GroupIronmanConfig.GROUP, GroupIronmanConfig.KEY_GROUP_KEY, GroupKey.generate());
		claimLeadership();
	}

	// ---- Group settings and roles (the panel's Settings tab) ----

	/** Roles and the group's settings on this client, or null when not running. */
	GroupGovernance governance()
	{
		GovernanceSync g = governance;
		return g == null ? null : g.governance();
	}

	/** The value of each shared setting now: the group's, or this member's own config while it has none. */
	GroupSettingsSchema.Resolved groupValues()
	{
		GroupGovernance g = governance();
		return new GroupSettingsSchema.Resolved(config, g == null ? null : g.settings());
	}

	/** "Claim leadership", shown only while this client knows of no leader. False when refused. */
	boolean claimLeadership()
	{
		GovernanceSync g = governance;
		return g != null && !self.isEmpty() && g.claim(self, System.currentTimeMillis());
	}

	/** The leader's "Make editor" and "Remove editor". False when refused. */
	boolean setEditor(String name, boolean editor)
	{
		GovernanceSync g = governance;
		return g != null && !self.isEmpty() && g.setEditor(self, name, editor, System.currentTimeMillis());
	}

	/** The leader's or an editor's "Save to group", and why it did not go out when it did not. */
	GovernanceSync.SaveResult saveGroupSettings(Map<String, Long> values)
	{
		GovernanceSync g = governance;
		if (g == null)
		{
			return GovernanceSync.SaveResult.NOT_STARTED;
		}
		if (self.isEmpty())
		{
			return GovernanceSync.SaveResult.NOT_LOGGED_IN;
		}
		GovernanceSync.SaveResult r = g.save(self, values, System.currentTimeMillis());
		return r == GovernanceSync.SaveResult.SAVED && !syncing() ? GovernanceSync.SaveResult.SAVED_OFFLINE : r;
	}

	/** The leader's "Share webhooks with the group". False when refused: this client is not the leader. */
	boolean shareWebhooks(boolean share)
	{
		GovernanceSync g = governance;
		return g != null && !self.isEmpty() && g.shareWebhooks(self, share, Webhooks.of(config),
			System.currentTimeMillis());
	}

	/** The leader's shared webhooks this client holds, or null when the leader shares none. */
	GroupWebhooks sharedWebhooks()
	{
		GroupGovernance g = governance();
		return g == null ? null : g.sharedWebhooks();
	}

	/**
	 * Where this client's posts go: its own webhooks, with the leader's shared ones filling every field it left
	 * empty ({@link Webhooks#withGroup}). Every sender asks this.
	 */
	Webhooks webhooks()
	{
		return Webhooks.of(config).withGroup(sharedWebhooks());
	}

	/** True when this client is the group's leader, holding the leader's key. */
	boolean isLeader()
	{
		GroupGovernance g = governance();
		return g != null && !self.isEmpty() && g.myRole(self) == GroupGovernance.Role.LEADER;
	}

	/** While the leader shares, an edit of the leader's own webhooks goes to the group as a new version. */
	private void republishWebhooks()
	{
		GovernanceSync g = governance;
		if (g != null && sharedWebhooks() != null && isLeader())
		{
			g.shareWebhooks(self, true, Webhooks.of(config), System.currentTimeMillis());
		}
	}

	/** "Forget leader": this client only. */
	void forgetLeader()
	{
		GovernanceSync g = governance;
		if (g != null)
		{
			g.forgetLeader();
		}
	}

	/** This member's own "don't post" (or overlay "don't show") for one kind. */
	void optOut(GroupEvent.Type type, boolean overlay, boolean out)
	{
		String key = overlay ? GroupIronmanConfig.KEY_MUTE_TYPES : GroupIronmanConfig.KEY_OPT_OUT_TYPES;
		Set<GroupEvent.Type> types = MemberPrefs.parse(overlay ? config.overlayMuteTypes() : config.eventOptOutTypes());
		if (out ? types.add(type) : types.remove(type))
		{
			configManager.setConfiguration(GroupIronmanConfig.GROUP, key, MemberPrefs.format(types));
		}
	}

	/** "Don't post my events". */
	void optOutAll(boolean out)
	{
		configManager.setConfiguration(GroupIronmanConfig.GROUP, GroupIronmanConfig.KEY_OPT_OUT_ALL, out);
	}

	/** What "Copy group key" puts on the clipboard, or null without a key. */
	String groupKey()
	{
		String key = GroupKey.normalize(config.groupKey());
		return key.isEmpty() ? null : key;
	}

	/**
	 * "Paste group key": saves the key a member sent (bare or as a share line).
	 * False, and nothing changes, when the text holds no key.
	 */
	boolean pasteKey(String text)
	{
		String key = GroupKey.fromPasted(text);
		if (key.isEmpty())
		{
			return false;
		}
		configManager.setConfiguration(GroupIronmanConfig.GROUP, GroupIronmanConfig.KEY_GROUP_KEY, key);
		return true;
	}

	/** The panel's "Turn on sharing" card: what it reads. */
	SharingPrompt.Settings sharingSettings()
	{
		return SharingPrompt.Settings.of(config);
	}

	/** "Turn on sharing": the player's own click, never on its own. */
	void turnOnSharing()
	{
		SharingPrompt.turnOnSharing((key, value) -> configManager.setConfiguration(GroupIronmanConfig.GROUP, key, value));
	}

	/** "Post my events to Discord": the player's own click. */
	void postToDiscord()
	{
		SharingPrompt.postToDiscord((key, value) -> configManager.setConfiguration(GroupIronmanConfig.GROUP, key, value));
	}

	void reloadConfig()
	{
		if (sync != null && !config.groupSync())
		{
			// Turning sync off is the player's own action: leave our party, never someone else's.
			sync.leaveOwn();
		}
		lowStock = LowStock.parse(config.groupLowStock());
		GovernanceSync g = governance;
		if (g != null)
		{
			g.prefs(MemberPrefs.of(config));
		}
		republishWebhooks();
		GroupOutbox.Switches s = switches();
		// Turning a live feature off drops what was collected for it.
		if (!s.stats)
		{
			roster.clearStats();
		}
		if (!s.carried)
		{
			roster.clearCarried();
		}
		if (!s.extras)
		{
			roster.clearExtras();
		}
		clientThread.invokeLater(() ->
		{
			lastStatsSent = null;
			lastCarriedSent = null;
			lastExtrasSent = null;
			statsDirty = true;
			carriedDirty = true;
			extrasDirty = true;
		});
		onChange.run();
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.SHARED_BANK)
		{
			// The "before" is the first contents the server sends after opening,
			// not whatever the container held from an earlier visit.
			storage.open();
		}
	}

	/** Group storage's Save button: what moved so far is kept by the game, so it is logged now. */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (storage.isOpen() && event.getParam1() == InterfaceID.SharedBank.SAVE_BUTTON)
		{
			commitStorage(storage.save(System.currentTimeMillis(), localName()));
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == InventoryID.INV_GROUP_TEMP)
		{
			storage.contents(counts(event.getItemContainer()));
		}
		else if (id == InventoryID.INV || id == InventoryID.WORN)
		{
			carriedDirty = true;
		}
		else if (id == InventoryID.SEED_VAULT)
		{
			// Only sent by the game while the vault is open: this is the vault "as last seen".
			seedVault = counts(event.getItemContainer());
			seedVaultAt = System.currentTimeMillis();
			extrasDirty = true;
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (POUCH_VARBITS.contains(event.getVarbitId())
			|| event.getVarpId() == VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO
			|| event.getVarpId() == VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT)
		{
			extrasDirty = true;
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN && session)
		{
			// Logged out: remember when, so the next login knows what is new since.
			session = false;
			roster.markPlayed(System.currentTimeMillis());
			saveCache();
		}
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			hiscoresOnLogin = true;
			refreshPresence();
		}
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		if (!switches().drops || event.getNpc() == null)
		{
			return;
		}
		long min = Math.max(0, config.groupDropMinValue());
		Map<Integer, Integer> all = new TreeMap<>();
		for (ItemStack stack : event.getItems())
		{
			ItemCounts.add(all, stack.getId(), stack.getQuantity(), itemManager::canonicalize);
		}
		Map<Integer, Integer> kept = new TreeMap<>();
		for (Map.Entry<Integer, Integer> e : all.entrySet())
		{
			if ((long) itemManager.getItemPrice(e.getKey()) * e.getValue() >= min)
			{
				kept.put(e.getKey(), e.getValue());
			}
		}
		recordEvent(GroupEvent.drop(System.currentTimeMillis(), localName(), event.getNpc().getName(), kept));
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		String key = GroupKey.keyFrom(event.getType(), event.getMessage());
		if (!key.isEmpty())
		{
			receiveKey(event.getName(), key);
			return;
		}
		if (event.getType() != ChatMessageType.GAMEMESSAGE || !switches().drops)
		{
			return;
		}
		String item = ClogMessage.parse(event.getMessage());
		if (item != null)
		{
			recordEvent(GroupEvent.clog(System.currentTimeMillis(), localName(), item));
		}
	}

	/**
	 * A "[GIMU] key" line in group chat, the only channel a key is read
	 * from: every member of a Group Ironman group is in it and nobody else
	 * is. The key is stored; joining still waits for the player's click.
	 */
	private void receiveKey(String sender, String key)
	{
		if (!config.groupSync())
		{
			return;
		}
		boolean mine = GroupMembers.key(sender).equals(GroupMembers.key(localName()));
		if (key.equals(GroupKey.normalize(config.groupKey())))
		{
			if (mine)
			{
				tell("Group key shared with your group.");
			}
			return;
		}
		configManager.setConfiguration(GroupIronmanConfig.GROUP, GroupIronmanConfig.KEY_GROUP_KEY, key);
		tell((mine ? "Group key saved" : "Group key received from " + GroupMembers.display(sender))
			+ ". Press 'Join group sync' in the GIM Utilities panel.");
	}

	/**
	 * Hides every share line, ours and other members', from this player's
	 * chatbox, the way the base chat filter plugin drops a line: the key is
	 * a secret and the panel says what happened.
	 */
	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		if (!CHAT_FILTER_CHECK.equals(event.getEventName()))
		{
			return;
		}
		int[] intStack = client.getIntStack();
		int intStackSize = client.getIntStackSize();
		Object[] objectStack = client.getObjectStack();
		int objectStackSize = client.getObjectStackSize();
		if (intStackSize < 3 || objectStackSize < 1)
		{
			return;
		}
		ChatMessageType type = ChatMessageType.of(intStack[intStackSize - 2]);
		Object message = objectStack[objectStackSize - 1];
		if (message instanceof String && GroupKey.hide(type, (String) message))
		{
			intStack[intStackSize - 3] = 0;
		}
	}

	private void tell(String text)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(GroupIronmanPlugin.NAME + ": " + text)
			.build());
	}

	/**
	 * {@code ::find dragon hunter wand}. RuneLite's ChatInputManager posts
	 * this instead of sending the text, so the query never reaches public
	 * chat, and the answer goes only to this player's chatbox.
	 */
	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		if (!FindCommand.NAME.equalsIgnoreCase(event.getCommand()) || !config.groupFindCommand())
		{
			return;
		}
		resolveNow(roster.knownItemIds());
		String query = String.join(" ", event.getArguments());
		for (String line : FindCommand.answer(query, roster.searchSources(), roster.log().entries(), lookup,
			System.currentTimeMillis(), FindStyle.of(config, self)))
		{
			tell(line);
		}
	}

	/**
	 * Any thread. One routed event, yours or a member's, into the Log tab's
	 * feed. Kept on disk with the rest of the cache; duplicates (a resend) are
	 * dropped by the feed.
	 */
	void logEvent(GroupEvent event)
	{
		if (!roster.feed().add(event))
		{
			return;
		}
		saveCache();
		onChange.run();
	}

	/** Client thread. Logs one of this player's own events and tells the party. */
	private void recordEvent(GroupEvent event)
	{
		if (!roster.events().add(event))
		{
			return;
		}
		resolveNow(event.items.keySet());
		if (syncing())
		{
			for (String payload : GroupOutbox.events(gson, switches(), Collections.singletonList(event)))
			{
				partyService.send(new VvgGroupIronmanMessage(payload));
			}
		}
		saveCache();
		onChange.run();
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.SHARED_BANK)
		{
			commitStorage(storage.close(System.currentTimeMillis(), localName()));
		}
		else if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			closeBank();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		statsDirty = true;
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		ticksSinceStats++;
		ticksSinceCarried++;
		ticksSinceExtras++;
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		long now = System.currentTimeMillis();
		if (!session)
		{
			session = true;
			extrasDirty = true;
			Player player = client.getLocalPlayer();
			if (player != null && player.getName() != null)
			{
				self = player.getName();
			}
			if (cacheLoaded)
			{
				// Before this tick's mark: the last time seen in the previous session.
				since = roster.lastPlayed();
			}
		}
		if (cacheLoaded)
		{
			roster.markPlayed(now);
		}
		recordSeen(now);
		refreshRoster();
		if (!syncing())
		{
			return;
		}
		flushGovernance();
		GroupOutbox.Switches s = switches();

		if (s.stats)
		{
			pollStats(s);
		}

		if (s.carried && carriedDirty && ticksSinceCarried >= CARRIED_INTERVAL_TICKS)
		{
			carriedDirty = false;
			sendCarried(s);
		}

		if (s.extras && extrasDirty && ticksSinceExtras >= EXTRAS_INTERVAL_TICKS)
		{
			extrasDirty = false;
			sendExtras(s);
		}
	}

	/**
	 * Client thread. Re-reads the group roster from the game every few
	 * ticks. The first time it is known while syncing, asks members to resend:
	 * anything they sent before was dropped for want of a roster to check.
	 */
	private void refreshRoster()
	{
		if (++ticksSinceRoster < ROSTER_INTERVAL_TICKS)
		{
			return;
		}
		ticksSinceRoster = 0;
		GroupMembers now = readRoster();
		boolean first = !members.known() && now.known();
		boolean changed = !now.names().equals(members.names()) || !now.group().equals(members.group());
		members = now;
		GovernanceSync g = governance;
		if (g != null && now.known())
		{
			g.group(now.group());
		}
		if (first && syncing())
		{
			partyService.send(new UserSync());
		}
		if (changed)
		{
			onChange.run();
		}
		refreshPresence();
		hiscoresAfterRoster();
	}

	/** Client thread. Once per login, every roster member, seen online or not; otherwise only failed lookups' retries. */
	private void hiscoresAfterRoster()
	{
		boolean login = hiscoresOnLogin && cacheLoaded && members.known();
		if (login)
		{
			hiscoresOnLogin = false;
		}
		lookUpHiscores(members.names(), login);
	}

	/**
	 * Client thread. Starts a hiscore lookup, on OkHttp's threads, for each of {@code names} that is not this
	 * player, shares no stats (shared data always wins) and is due ({@link HiscoreBook#begin}). {@code asked}:
	 * this player just logged in or these members just came online.
	 */
	private void lookUpHiscores(Collection<String> names, boolean asked)
	{
		if (!cacheLoaded || !anyOn() || names.isEmpty())
		{
			return;
		}
		Set<String> shared = new HashSet<>();
		roster.allStats().keySet().forEach(n -> shared.add(GroupMembers.key(n)));
		shared.add(GroupMembers.key(self));
		long now = System.currentTimeMillis();
		for (String name : names)
		{
			if (!shared.contains(GroupMembers.key(name)) && roster.hiscores().begin(name, now, asked))
			{
				hiscoreClient.lookupAsync(name, HiscoreEndpoint.NORMAL)
					.whenComplete((result, error) -> hiscoreDone(name, result, error));
			}
		}
	}

	/** OkHttp thread. A null result is a name not on the hiscores; it shows as unavailable, like an error. */
	private void hiscoreDone(String name, HiscoreResult result, Throwable error)
	{
		long now = System.currentTimeMillis();
		Map<String, Integer> levels = error == null ? HiscoreBook.levels(result) : Collections.emptyMap();
		if (levels.isEmpty())
		{
			log.debug("hiscore lookup for a group member failed", error);
			roster.hiscores().failed(name, now);
		}
		else
		{
			roster.hiscores().succeeded(name, levels, now);
			saveCache();
		}
		onChange.run();
	}

	/**
	 * Client thread: the Group channel changed, a member joined it or left it. RuneLite posts
	 * {@code ClanChannelChanged} (with {@code getClanId()}) when a channel loads or goes, and
	 * {@code ClanMemberJoined} / {@code ClanMemberLeft} (with {@code getClanChannel()}) as members log in and out.
	 */
	@Subscribe
	public void onClanChannelChanged(ClanChannelChanged event)
	{
		if (event.getClanId() == ClanID.GROUP_IRONMAN)
		{
			refreshPresence();
		}
	}

	@Subscribe
	public void onClanMemberJoined(ClanMemberJoined event)
	{
		groupChannelEvent(event.getClanChannel());
	}

	@Subscribe
	public void onClanMemberLeft(ClanMemberLeft event)
	{
		groupChannelEvent(event.getClanChannel());
	}

	private void groupChannelEvent(ClanChannel channel)
	{
		if (channel != null && channel == client.getClanChannel(ClanID.GROUP_IRONMAN))
		{
			refreshPresence();
		}
	}

	/**
	 * Client thread, each tick logged in: this player, and every member the game shows online (the Group channel,
	 * plugin or not), were seen now on their world. The live board's "last seen"; saved with the group cache.
	 */
	private void recordSeen(long now)
	{
		if (!self.isEmpty())
		{
			roster.seen(self, new LastSeen(now, client.getWorld(), true));
		}
		presence.online().forEach((name, world) -> roster.seen(name, new LastSeen(now, world, false)));
	}

	/** Who in the group is online and where, as the game shows it; {@link GamePresence#UNKNOWN} logged out. */
	GamePresence presence()
	{
		return presence;
	}

	/** Client thread. Re-reads {@link #presence} and, when it changed, tells the panel and the event engine. */
	private void refreshPresence()
	{
		GamePresence now = client.getGameState() == GameState.LOGGED_IN
			? GamePresence.read(members, readChannel(), readFriends()) : GamePresence.UNKNOWN;
		if (now.equals(presence))
		{
			return;
		}
		GamePresence before = presence;
		presence = now;
		lookUpHiscores(GamePresence.cameOnline(members.names(), before, now), true);
		GroupEventEngine e = events;
		if (e != null)
		{
			e.gamePresence(now);
		}
		onChange.run();
	}

	/** Client thread. The Group channel's members and worlds, null before it loads. */
	private Map<String, Integer> readChannel()
	{
		ClanChannel channel = client.getClanChannel(ClanID.GROUP_IRONMAN);
		if (channel == null || channel.getMembers() == null)
		{
			return null;
		}
		Map<String, Integer> out = new HashMap<>();
		for (ClanChannelMember m : channel.getMembers())
		{
			out.put(m.getName(), m.getWorld());
		}
		return out;
	}

	/** Client thread. The Friends list's names and worlds (0 offline), null before it loads. */
	private Map<String, Integer> readFriends()
	{
		FriendContainer friends = client.getFriendContainer();
		if (friends == null || friends.getMembers() == null)
		{
			return null;
		}
		Map<String, Integer> out = new HashMap<>();
		for (Friend f : friends.getMembers())
		{
			if (f != null && f.getName() != null)
			{
				out.put(f.getName(), f.getWorld());
			}
		}
		return out;
	}

	/** Client thread. {@code ClanSettings} lists every member; the channel only those online, so it is the fallback. */
	private GroupMembers readRoster()
	{
		ClanSettings settings = client.getClanSettings(ClanID.GROUP_IRONMAN);
		if (settings != null && settings.getMembers() != null && !settings.getMembers().isEmpty())
		{
			List<String> names = new ArrayList<>();
			for (ClanMember m : settings.getMembers())
			{
				names.add(m.getName());
			}
			return GroupMembers.of(settings.getName(), names);
		}
		Map<String, Integer> online = readChannel();
		if (online != null)
		{
			return GroupMembers.of(client.getClanChannel(ClanID.GROUP_IRONMAN).getName(), online.keySet());
		}
		return GroupMembers.UNKNOWN;
	}

	/** Client thread. Run energy, special attack and location fire no StatChanged; poll the cheap ints. */
	private void pollStats(GroupOutbox.Switches s)
	{
		int run = client.getEnergy() / 100;
		int spec = client.getVarpValue(VarPlayerID.SA_ENERGY) / 10;
		int world = client.getWorld();
		WorldPoint at = position();
		int region = at == null ? 0 : at.getRegionID();
		int x = at == null ? 0 : at.getX();
		int y = at == null ? 0 : at.getY();
		int plane = at == null ? 0 : at.getPlane();
		if (run != lastRun || spec != lastSpec || world != lastWorld || region != lastRegion
			|| plane != lastPlane || Math.abs(x - lastX) + Math.abs(y - lastY) >= MOVE_TILES)
		{
			lastRun = run;
			lastSpec = spec;
			lastWorld = world;
			lastRegion = region;
			lastX = x;
			lastY = y;
			lastPlane = plane;
			statsDirty = true;
		}
		if (ticksSinceStats >= STATS_HEARTBEAT_TICKS)
		{
			// Nothing changed for a minute: send the same again so members still see us online.
			lastStatsSent = null;
			statsDirty = true;
		}
		if (statsDirty && ticksSinceStats >= STATS_INTERVAL_TICKS)
		{
			statsDirty = false;
			sendStats(s);
		}
	}

	/** Someone joined and asked for a resend. */
	@Subscribe
	public void onUserSync(UserSync event)
	{
		clientThread.invokeLater(() ->
		{
			lastStatsSent = null;
			lastCarriedSent = null;
			lastExtrasSent = null;
			statsDirty = true;
			carriedDirty = true;
			extrasDirty = true;
			GovernanceSync g = governance;
			if (g != null)
			{
				// Sent on the next tick: roles first, then the settings they authorise.
				g.resendAll();
			}
			if (!syncing())
			{
				return;
			}
			if (lastBank != null)
			{
				for (String payload : GroupOutbox.bank(gson, switches(), lastBank))
				{
					partyService.send(new VvgGroupIronmanMessage(payload));
				}
			}
			// The joiner may have been offline for these: this is how "since you last played" works with no server.
			List<GroupEvent> mine = roster.events().recentBy(localName(), System.currentTimeMillis());
			for (String payload : GroupOutbox.events(gson, switches(), mine))
			{
				partyService.send(new VvgGroupIronmanMessage(payload));
			}
		});
	}

	/** A member left: their stats and items stay as "last seen" for their offline card. */
	@Subscribe
	public void onUserPart(UserPart event)
	{
		assembler.forget(event.getMemberId());
		onChange.run();
	}

	void onPartyChanged()
	{
		GovernanceSync g = governance;
		if (g != null)
		{
			g.resendAll();
		}
		assembler.clear();
		roster.clearStrangers();
		lastStatsSent = null;
		lastCarriedSent = null;
		lastExtrasSent = null;
		statsDirty = true;
		carriedDirty = true;
		extrasDirty = true;
		onChange.run();
	}

	/**
	 * Who sent {@code message}, or null when it must be dropped: our own
	 * echo, a party that is not this group's, a member with no name yet, or a
	 * name that is not on the in-game group roster.
	 */
	private String sender(VvgGroupIronmanMessage message)
	{
		PartyMember local = partyService.getLocalMember();
		if (!syncing() || local != null && local.getMemberId() == message.getMemberId())
		{
			return null;
		}
		PartyMember member = partyService.getMemberById(message.getMemberId());
		String name = member == null ? null : member.getDisplayName();
		if (name == null || name.trim().isEmpty() || !member.isLoggedIn())
		{
			// Banks and the log are kept by name across restarts; without a name there is nothing to file it under.
			log.debug("ignored group payload from unnamed member {}", message.getMemberId());
			return null;
		}
		GroupMembers.Verdict verdict = members.check(name);
		if (verdict == GroupMembers.Verdict.STRANGER)
		{
			roster.stranger(name);
			onChange.run();
		}
		return verdict == GroupMembers.Verdict.ACCEPT ? name : null;
	}

	@Subscribe
	public void onVvgGroupIronmanMessage(VvgGroupIronmanMessage message)
	{
		String name = sender(message);
		if (name == null)
		{
			return;
		}
		GroupCodec.Decoded decoded = GroupCodec.decode(gson, message.getPayload());
		if (decoded == null)
		{
			notData(name, message);
			return;
		}
		GroupOutbox.Switches s = switches();
		long now = System.currentTimeMillis();

		switch (decoded.kind)
		{
			case BANK:
				if (!s.bank)
				{
					return;
				}
				BankSnapshot bank = assembler.accept(message.getMemberId(), decoded.bank);
				if (bank != null && roster.putBank(name, new BankSnapshot(Math.min(bank.takenAt(), now), bank.items())))
				{
					resolve(bank.items().keySet());
					saveCache();
				}
				break;
			case CARRIED:
				if (!s.carried)
				{
					return;
				}
				roster.putCarried(name, decoded.carried);
				resolve(decoded.carried.inventory.keySet());
				resolve(decoded.carried.worn.keySet());
				break;
			case STORAGE:
				if (!s.storage)
				{
					return;
				}
				StorageChange change = decoded.storage.withMember(name);
				change = new StorageChange(Math.min(change.at(), now), name, change.took(), change.deposited(),
					change.after());
				if (roster.putStorage(change))
				{
					resolve(change.after().keySet());
					resolve(change.took().keySet());
					resolve(change.deposited().keySet());
					checkLowStock(change.after());
					memberStorage(change);
					saveCache();
				}
				break;
			case STATS:
				if (!s.stats)
				{
					return;
				}
				roster.putStats(name, decoded.stats);
				break;
			case EXTRAS:
				if (!s.extras)
				{
					return;
				}
				MemberExtras before = roster.extras().get(name);
				roster.putExtras(name, decoded.extras);
				resolve(decoded.extras.runePouch.keySet());
				resolve(decoded.extras.quiver.keySet());
				resolve(decoded.extras.seedVault.keySet());
				if (before == null || before.seedVaultAt != roster.extras().get(name).seedVaultAt)
				{
					// The pouch changes every cast; only a new seed vault is worth a disk write.
					saveCache();
				}
				break;
			case EVENTS:
				if (!receiveEvents(name, decoded.events, s))
				{
					return;
				}
				break;
			default:
				return;
		}
		// Anything accepted from a member means their client is running and in the party.
		roster.heard(name, now);
		onChange.run();
	}

	/** A payload {@link GroupCodec} does not read: roles, group settings or an announce, or one from a newer build. */
	private void notData(String name, VvgGroupIronmanMessage message)
	{
		GovernanceSync g = governance;
		String payload = message.getPayload();
		if (g != null && GovernanceSync.isGovernance(payload) && g.receive(name, payload))
		{
			roster.heard(name, System.currentTimeMillis());
			return;
		}
		log.debug("ignored unreadable group payload from {}", message.getMemberId());
	}

	/** Game tick, connected: roles, settings and this member's announce, whichever changed. */
	private void flushGovernance()
	{
		GovernanceSync g = governance;
		if (g != null)
		{
			g.flush(MemberPrefs.of(config));
		}
	}

	/**
	 * A member's events. Drops and log slots go in the log under the drops
	 * switch, as in 1.0.0; the rest only under "Share group events". Each new
	 * one goes on to the engine for the overlays. False when this client
	 * takes none of them, so the member is not marked heard.
	 */
	private boolean receiveEvents(String name, List<GroupEvent> received, GroupOutbox.Switches s)
	{
		boolean share = config.groupShareEvents();
		if (!s.drops && !share)
		{
			return false;
		}
		boolean added = false;
		GroupEventEngine engine = events;
		for (GroupEvent e : received)
		{
			GroupEvent mine = e.withMember(name);
			boolean logged = e.type.logged();
			if (logged ? !s.drops || !roster.events().add(mine) : !share)
			{
				continue;
			}
			resolve(e.items.keySet());
			added |= logged;
			if (engine != null)
			{
				engine.member(mine);
			}
		}
		if (added)
		{
			saveCache();
		}
		return true;
	}

	private void memberStorage(StorageChange change)
	{
		GroupEventEngine engine = events;
		if (engine != null)
		{
			// Names resolve on the client thread; the value of a big move needs them.
			clientThread.invokeLater(() -> engine.storage(change, false));
		}
	}

	/**
	 * Client thread. A Save or the close (the close button saves too): the
	 * contents become the storage snapshot even when nothing moved, a change
	 * is logged locally whether or not sync is on, and only a change is sent.
	 */
	private void commitStorage(StorageChange change)
	{
		if (change == null)
		{
			return;
		}
		resolveNow(change.after().keySet());
		resolveNow(change.took().keySet());
		resolveNow(change.deposited().keySet());

		// Minimums are checked on every own save, shared or not: the alert is for this player.
		checkLowStockNow(change.after(), true);
		GroupEventEngine engine = events;
		if (engine != null)
		{
			engine.storage(change, true);
		}

		// Kept locally whether shared or not; with sharing off it is marked so and nothing is sent.
		String payload = GroupOutbox.ownStorage(gson, switches(), change, roster);
		if (payload != null && syncing())
		{
			partyService.send(new VvgGroupIronmanMessage(payload));
		}
		saveCache();
		onChange.run();
	}

	/** Client thread. */
	private void closeBank()
	{
		GroupOutbox.Switches s = switches();
		if (!s.bank)
		{
			return;
		}
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (bank == null)
		{
			return;
		}
		Map<Integer, Integer> items = counts(bank);
		BankSnapshot snapshot = new BankSnapshot(System.currentTimeMillis(), items);
		if (lastBank != null && lastBank.items().equals(snapshot.items()))
		{
			// Nothing moved since the last close; do not resend the whole bank.
			return;
		}
		lastBank = snapshot;
		resolveNow(items.keySet());
		roster.putBank(localName(), snapshot);
		if (syncing())
		{
			for (String payload : GroupOutbox.bank(gson, s, snapshot))
			{
				partyService.send(new VvgGroupIronmanMessage(payload));
			}
		}
		saveCache();
		onChange.run();
	}

	/** Client thread. */
	private void sendStats(GroupOutbox.Switches s)
	{
		Map<String, MemberStats.Level> skills = new LinkedHashMap<>();
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			skills.put(skill.getName(), new MemberStats.Level(client.getRealSkillLevel(skill),
				client.getBoostedSkillLevel(skill)));
		}
		MemberStats stats = new MemberStats(skills, lastRun, lastSpec, lastWorld, lastRegion, lastX, lastY, lastPlane);
		String payload = GroupOutbox.stats(gson, s, stats);
		if (payload == null || Objects.equals(payload, lastStatsSent))
		{
			return;
		}
		partyService.send(new VvgGroupIronmanMessage(payload));
		lastStatsSent = payload;
		ticksSinceStats = 0;
		roster.putStats(localName(), stats);
		roster.heard(localName(), System.currentTimeMillis());
		onChange.run();
	}

	/** Client thread. */
	private void sendExtras(GroupOutbox.Switches s)
	{
		MemberExtras extras = new MemberExtras(runePouch(), quiver(), seedVault, seedVaultAt);
		String payload = GroupOutbox.extras(gson, s, extras);
		if (payload == null || Objects.equals(payload, lastExtrasSent))
		{
			return;
		}
		partyService.send(new VvgGroupIronmanMessage(payload));
		lastExtrasSent = payload;
		ticksSinceExtras = 0;
		resolveNow(extras.runePouch.keySet());
		resolveNow(extras.quiver.keySet());
		resolveNow(extras.seedVault.keySet());
		roster.putExtras(localName(), extras);
		onChange.run();
	}

	/** Client thread. Rune type varbits index the RUNEPOUCH_RUNE enum, the way the base rune pouch plugin reads them. */
	private Map<Integer, Integer> runePouch()
	{
		Map<Integer, Integer> out = new TreeMap<>();
		EnumComposition runes = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		if (runes == null)
		{
			return out;
		}
		for (int i = 0; i < POUCH_TYPES.length; i++)
		{
			int type = client.getVarbitValue(POUCH_TYPES[i]);
			int amount = client.getVarbitValue(POUCH_AMOUNTS[i]);
			if (type > 0 && amount > 0)
			{
				ItemCounts.add(out, runes.getIntValue(type), amount, itemManager::canonicalize);
			}
		}
		return out;
	}

	/** Client thread. Dizana's quiver's ammo: an item id and count in two varps, -1 or 0 when empty. */
	private Map<Integer, Integer> quiver()
	{
		Map<Integer, Integer> out = new TreeMap<>();
		ItemCounts.add(out, client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO),
			client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT), itemManager::canonicalize);
		return out;
	}

	/** Client thread. */
	private void sendCarried(GroupOutbox.Switches s)
	{
		Map<Integer, Integer> inventory = counts(client.getItemContainer(InventoryID.INV));
		Map<Integer, Integer> worn = counts(client.getItemContainer(InventoryID.WORN));
		String payload = GroupOutbox.carried(gson, s, inventory, worn);
		if (payload == null || Objects.equals(payload, lastCarriedSent))
		{
			return;
		}
		partyService.send(new VvgGroupIronmanMessage(payload));
		lastCarriedSent = payload;
		ticksSinceCarried = 0;
		resolveNow(inventory.keySet());
		resolveNow(worn.keySet());
		roster.putCarried(localName(), new GroupCodec.Carried(inventory, worn));
		onChange.run();
	}

	/** Client thread. Counts by canonical id; bank fillers and placeholders are not items. */
	private Map<Integer, Integer> counts(ItemContainer container)
	{
		Map<Integer, Integer> out = new TreeMap<>();
		if (container == null)
		{
			return out;
		}
		for (Item item : container.getItems())
		{
			if (item.getId() == ItemID.BANK_FILLER)
			{
				continue;
			}
			ItemCounts.add(out, item.getId(), item.getQuantity(), itemManager::canonicalize);
		}
		return out;
	}

	/** Client thread. The real-world tile, also inside instances; null when not known. */
	private WorldPoint position()
	{
		Player player = client.getLocalPlayer();
		if (player == null || player.getLocalLocation() == null)
		{
			return null;
		}
		return WorldPoint.fromLocalInstance(client, player.getLocalLocation());
	}

	private String localName()
	{
		Player player = client.getLocalPlayer();
		if (player != null && player.getName() != null)
		{
			return player.getName();
		}
		PartyMember local = partyService.getLocalMember();
		return local != null && local.getDisplayName() != null ? local.getDisplayName() : "You";
	}

	private void checkLowStock(Map<Integer, Integer> storage)
	{
		if (lowStock.isEmpty())
		{
			return;
		}
		// Names only resolve on the client thread, and the check matches by name.
		clientThread.invokeLater(() -> checkLowStockNow(storage, false));
	}

	/** Client thread. {@code own}: this player's visit, the one whose client posts the event. */
	private void checkLowStockNow(Map<Integer, Integer> storage, boolean own)
	{
		if (lowStock.isEmpty())
		{
			return;
		}
		resolveNow(storage.keySet());
		GroupEventEngine engine = events;
		for (LowStock.Alert alert : lowStock.check(storage, lookup))
		{
			notifier.notify(GroupIronmanPlugin.NAME + ": " + alert.message());
			if (engine != null)
			{
				engine.minimum(alert, own);
			}
		}
	}

	/** Any thread: queues name and price lookups for ids not seen yet. */
	private void resolve(Collection<Integer> ids)
	{
		List<Integer> missing = new ArrayList<>();
		for (int id : ids)
		{
			// Names loaded from the disk cache come without a slot: look those up again too.
			if (!names.containsKey(id) || !slots.containsKey(id))
			{
				missing.add(id);
			}
		}
		if (!missing.isEmpty())
		{
			clientThread.invokeLater(() ->
			{
				resolveNow(missing);
				onChange.run();
			});
		}
	}

	/** Client thread. */
	private void resolveNow(Collection<Integer> ids)
	{
		for (int id : ids)
		{
			if (names.containsKey(id) && slots.containsKey(id))
			{
				continue;
			}
			ItemComposition item = itemManager.getItemComposition(id);
			String name = item == null ? null : item.getName();
			if (name != null && !name.isEmpty() && !"null".equalsIgnoreCase(name))
			{
				names.put(id, name);
				prices.put(id, (long) itemManager.getItemPrice(id));
				ItemStats stats = itemManager.getItemStats(id);
				slots.put(id, stats == null || stats.getEquipment() == null ? -1 : stats.getEquipment().getSlot());
			}
		}
	}

	/** Executor thread. */
	private void loadCache()
	{
		try
		{
			if (!readCache())
			{
				return;
			}
		}
		finally
		{
			// Read before any tick marks this session as played.
			since = roster.lastPlayed();
			cacheLoaded = true;
		}
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				// Item definitions need a loaded cache; try again on a later tick.
				return false;
			}
			resolveNow(roster.knownItemIds());
			onChange.run();
			return true;
		});
	}

	/** Executor thread. False when there is no file or it cannot be read. */
	private boolean readCache()
	{
		try
		{
			Filepath file = cacheFile();
			if (!file.isFile())
			{
				return false;
			}
			String json;
			try (InputStream in = file.openInputStream())
			{
				json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
			if (!GroupCache.read(gson, json, roster))
			{
				log.debug("group cache unreadable, starting empty");
				return false;
			}
			restoreWebhooks(GroupCache.webhooks(gson, json));
			return true;
		}
		catch (Exception e)
		{
			log.debug("could not read group cache", e);
			return false;
		}
	}

	/** Executor thread. The cached webhooks document, checked like one from the party; a newer one held wins. */
	private void restoreWebhooks(String json)
	{
		GovernanceSync g = governance;
		if (json == null || g == null)
		{
			return;
		}
		if (cachedWebhooks == null)
		{
			// Kept as read until the governance accepts (or replaces) it, so a save before the roles load keeps it.
			cachedWebhooks = json;
		}
		g.restoreWebhooks(json);
	}

	private void saveCache()
	{
		if (!cacheLoaded)
		{
			// Whatever arrived meanwhile is in the roster and goes out with the first save after loading.
			return;
		}
		String json = GroupCache.write(gson, roster, cachedWebhooks);
		executor.execute(() ->
		{
			try
			{
				Filepath file = cacheFile();
				Filepath dir = file.getParent();
				dir.createDirectories();
				// Write a temp file beside the cache, then move it over, so a crash never leaves half a file.
				Filepath tmp = dir.createTempFile("group-cache", ".tmp");
				try
				{
					try (OutputStream out = tmp.openOutputStream())
					{
						out.write(json.getBytes(StandardCharsets.UTF_8));
					}
					tmp.moveTo(file, StandardCopyOption.REPLACE_EXISTING);
				}
				finally
				{
					tmp.deleteIfExists();
				}
			}
			catch (Exception e)
			{
				log.debug("could not write group cache", e);
			}
		});
	}

	/** Executor thread. The cache inside the plugin's data directory (.runelite/plugin-data/gim-utilities). */
	private Filepath cacheFile() throws Exception
	{
		return dataDirectory.call().joinSegment(CACHE_FILE);
	}

	/** Kept for the panel: is any group feature on, so the panel button should show. */
	boolean anyOn()
	{
		return switches().any() || !lowStock.isEmpty();
	}
}
