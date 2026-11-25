package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.io.IOException;
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
import java.util.function.Consumer;
import java.util.function.Predicate;
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
import net.runelite.api.NPC;
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
import net.runelite.api.widgets.Widget;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemStats;
import net.runelite.client.game.ItemStack;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.Text;
import net.runelite.client.party.messages.UserSync;
import net.runelite.http.api.item.ItemPrice;

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
	/** How often the group roster is re-read from the game's clan data. */
	static final int ROSTER_INTERVAL_TICKS = 10;
	/** Roster reads with no group before a login is told it is not in the group: 5 reads, about 30 s. */
	static final int NOT_MEMBER_WAITS = 5;
	/** How often this player's quest progress is re-read for the Shopping tab (about half a minute). */
	static final int QUEST_INTERVAL_TICKS = 50;
	static final int SHOP_ALERT_INTERVAL_TICKS = 3;
	/** How often the owner's client closes request items the group now holds (about three seconds). */
	static final int SEAL_INTERVAL_TICKS = 5;
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

	/** Log basket, fish barrel and coal bag contents, from the game's own lines. Client thread. */
	@Inject
	private CarriedContainers containers;

	private final GroupRoster roster = new GroupRoster();
	private final BankAssembler assembler = new BankAssembler();
	private final Map<Integer, String> names = new ConcurrentHashMap<>();
	private final Map<Integer, Long> prices = new ConcurrentHashMap<>();
	/** Equipment slot per item, read on the client thread with the name (ItemManager stats need it). */
	private final Map<Integer, Integer> slots = new ConcurrentHashMap<>();
	/** {@link ItemStyles} bits per item, read from its equipment stats with the slot. */
	private final Map<Integer, Integer> styles = new ConcurrentHashMap<>();
	/** High Alchemy value per item, read with the name. */
	private final Map<Integer, Long> highAlchs = new ConcurrentHashMap<>();
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
	/** The Shopping tab's shared lists and members' quest progress; null when not running. */
	private volatile ShoppingSync shopping;
	/** Members' boss bests and quests/diaries; null when not running. */
	private volatile MemberProgressBook progress;
	private volatile QuestData questData = QuestData.empty();
	private volatile ShopPresets presets = new ShopPresets();
	/** Whether each item can change hands, read with its name on the client thread. */
	private final Map<Integer, Boolean> tradeable = new ConcurrentHashMap<>();
	/** This player's real levels, read with their quest progress, for which shops will serve them. */
	private volatile Map<String, Integer> ownLevels = Collections.emptyMap();
	/** Where this player stood on the last tick, for "Path to"; null when logged out. */
	private volatile WorldPoint here;
	/** Where the panel last drew from: "Path to next" is nearest-first from here, so it is redrawn when the player has moved well away. */
	private WorldPoint drawnFrom;
	// Client thread only.
	private final ShopVisits shopVisits = new ShopVisits();
	/** This player's own boss bests, read from their own chat; shared only under "Share group events". */
	private final PbCapture pbCapture = new PbCapture();
	private volatile boolean pbDirty;
	/** This player's quests, quest points and diaries, read with their quest progress; null until read. */
	private volatile AchievementState ownAchievements;
	private int ticksSinceQuests = QUEST_INTERVAL_TICKS;
	private int ticksSinceShopAlert;
	private int ticksSinceSeal;

	private volatile LowStock lowStock = LowStock.parse("");
	/** The rules text {@link #lowStock} was parsed from; null before the first config load. */
	private String lowStockText;
	private volatile GroupMembers members = GroupMembers.UNKNOWN;
	private volatile GamePresence presence = GamePresence.UNKNOWN;
	/**
	 * {@link GroupMembers#key} of the account last found a member of this client's group ({@link GroupBinding}),
	 * "" when the account logged in is not one (or is not known yet). See {@link #member()}.
	 */
	private volatile String memberName = "";
	/** Client thread: this login was already told it is not in the group, or roster reads that found no group. */
	private boolean toldNotMember;
	private int rosterWaits;
	/** The group key the binding was made under; a different key clears it ({@link #reloadConfig}). */
	private String keySeen;
	/** The key changed this run: the next member binds the group whatever the old group's roles and cache say. */
	private volatile boolean rebind;

	// Client thread only.
	private final StorageSession storage = new StorageSession();
	/** The text child of the game's loading modal ({@link InterfaceID#LOADING_ICON_MODAL}). */
	private static final int STORAGE_LOADER_TEXT = 1;
	/** Client thread. Whether "Saving..." showed last tick, so each save is taken once. */
	private boolean storageSaving;
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
	private MemberStatus lastStatus = MemberStatus.UNKNOWN;
	private String lastStatsSent;
	private String lastCarriedSent;
	/** This player's carried items as last put on their own card, sent or not. */
	private String lastCarriedLocal;
	private boolean extrasDirty;
	private int ticksSinceExtras = EXTRAS_INTERVAL_TICKS;
	private String lastExtrasSent;
	/** Client thread: this player's activity as last shown, for a joiner's resend. */
	private ActivityState lastActivity = ActivityState.NONE;
	/** Client thread: an active state went to the party and no clear has followed it yet. */
	private boolean activityOut;
	/** Client thread: the name this player's live activity is filed under in the roster, null when none. */
	private String activityName;
	/** When another member's client last said it keeps the live status board ({@link BoardBeacon}). */
	private final BoardBeacon boardBeacon = new BoardBeacon();
	/** This client keeps the live status board: set by the event engine's timer, read on the client thread. */
	private volatile boolean keepingBoard;
	/** Client thread: ticks since this client's last board beacon. */
	private int ticksSinceBeacon = STATS_HEARTBEAT_TICKS;
	private Map<Integer, Integer> seedVault = new TreeMap<>();
	private long seedVaultAt = -1;
	private boolean session;
	/** This login has not asked the hiscores about the roster yet. */
	private boolean hiscoresOnLogin = true;

	/** The looting bag's item container id (InventoryID.LOOTING_BAG), sent by the game while the bag is viewed. */
	private static final int LOOTING_BAG_CONTAINER = 516;

	private volatile RegionNames regions = RegionNames.parse("");
	/** Nothing is written to disk until the file has been read, or a save would replace it with less. */
	private volatile boolean cacheLoaded;
	/** When this player last played before this session; 0 when not known. */
	private volatile long since;
	private volatile String self = "";
	/** Untradeable items by name (a collection log line's Giant key), for the Group box's pictures. */
	private final ItemNameIndex itemsByName = new ItemNameIndex();
	/** Items read per client frame: small enough that a cold item cache (one decode per id) does not stutter a frame. */
	static final int NAME_SCAN_SLICE = 500;
	/** The client's item list for {@link #itemsByName}'s scan; client thread only. */
	private final ItemNameIndex.Items clientItems = new ItemNameIndex.Items()
	{
		@Override
		public int count()
		{
			return client.getItemCount();
		}

		@Override
		public String baseName(int item)
		{
			ItemComposition c = itemManager.getItemComposition(item);
			String n = c.getName();
			return c.getNote() != -1 || c.getPlaceholderTemplateId() != -1 || n == null
				|| "null".equalsIgnoreCase(n) ? null : n;
		}
	};

	final ItemLookup lookup = new ItemLookup()
	{
		@Override
		public int itemId(String name)
		{
			int id = itemsByName.id(name);
			if (itemsByName.claim())
			{
				// A slice per client frame until done (invokeLater repeats a supplier until it returns true).
				clientThread.invokeLater(() -> itemsByName.step(clientItems, NAME_SCAN_SLICE));
			}
			return id;
		}

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

		@Override
		public int combatStyles(int id)
		{
			return styles.getOrDefault(id, 0);
		}

		@Override
		public long highAlch(int id)
		{
			return highAlchs.getOrDefault(id, 0L);
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
				return getConfig(key);
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
					setConfig(key, value);
				}
			}
		}, this::send, () -> this.onChange.run(), webhooks ->
		{
			// The leader's shared webhooks: kept in the group cache file, never the RuneLite config.
			cachedWebhooks = webhooks;
			saveCache();
		});
		shopping = new ShoppingSync(gson, this::send, () ->
		{
			saveCache();
			this.onChange.run();
		});
		progress = new MemberProgressBook(gson, this::send);
		cacheLoaded = false;
		since = 0;
		reloadConfig();
		executor.execute(() ->
		{
			regions = RegionNames.load();
			questData = QuestData.load(gson);
			presets = ShopPresets.load(gson);
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
		shopping = null;
		progress = null;
		tradeable.clear();
		ownLevels = Collections.emptyMap();
		cachedWebhooks = null;
		cacheLoaded = false;
		session = false;
		forgetOwnBests();
		hiscoresOnLogin = true;
		seedVault = new TreeMap<>();
		seedVaultAt = -1;
		lastExtrasSent = null;
		roster.clearAll();
		assembler.clear();
		names.clear();
		prices.clear();
		itemsByName.stop();
		storage.reset();
		storageSaving = false;
		lastBank = null;
		lastStatsSent = null;
		lastCarriedSent = null;
		lastCarriedLocal = null;
		containers.loggedOut();
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
		if (syncing())
		{
			GroupOutbox.groupEvents(gson, share, Collections.singletonList(event)).forEach(this::send);
		}
	}

	/**
	 * Client thread. This player's live activity: always kept in the roster, so the panel shows it (only on this
	 * screen while sharing is off), and sent to the party only under "Share group events" ({@link GroupOutbox}).
	 */
	void shareActivity(ActivityState state)
	{
		if (state.active() && !member())
		{
			// An account not in the group has no run on the group's cards; a member's run still clears.
			return;
		}
		lastActivity = state;
		// Filed under the name of the login it started in: after a logout the local name may read differently.
		String name = state.active() || activityName == null ? localName() : activityName;
		activityName = state.active() ? name : null;
		roster.putActivity(name, state);
		String payload = GroupOutbox.activity(gson, syncing(), config.groupShareEvents(), state, activityOut);
		if (payload != null)
		{
			send(payload);
			activityOut = config.groupShareEvents() && state.active();
		}
		onChange.run();
	}

	/** A member's activity, only taken in under "Share group events"; anything malformed is dropped. */
	private void receiveActivity(String name, String payload)
	{
		if (!config.groupShareEvents())
		{
			return;
		}
		ActivityState state = ActivityCodec.decode(gson, payload);
		if (state == null)
		{
			log.debug("ignored unreadable activity from {}", name);
			return;
		}
		roster.putActivity(name, state);
		roster.heard(name, System.currentTimeMillis());
		onChange.run();
	}

	/**
	 * Client thread. A game broadcast this client just posted ({@link GroupBroadcasts}), so another plugin user's
	 * client that also thinks it posts skips it. Carries no data of this player's own: every member's game showed
	 * the same line.
	 */
	void sharePosted(String key)
	{
		if (syncing())
		{
			send(GroupCodec.encodePosted(gson, Collections.singletonList(key)));
		}
	}

	/** The group's signed leader, empty when this client has no roles yet. */
	String leaderName()
	{
		GroupGovernance g = governance();
		GroupRoles roles = g == null ? null : g.roles();
		return roles == null ? "" : roles.leader;
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

	/**
	 * True only in this group's own party, logged in as one of its members: nothing is sent to, or taken from,
	 * any other party, and nothing goes out for an account that is not in the group (Board 2026-11-15).
	 */
	boolean syncing()
	{
		return member() && syncState() == GroupSync.State.CONNECTED;
	}

	/**
	 * Board 2026-11-15: whether the account logged in is a member of the group this client is set up for
	 * ({@link GroupBinding}). False for an account in another group or none, and until the roster is read: then
	 * nothing is posted, logged, shown on the board or shared for it. Logged out it stays what the last account
	 * was, so that account's own logout (a run ending, its presence) still goes out; a different account logging
	 * in is not a member before its own roster is read, whichever subscriber's tick runs first.
	 */
	boolean member()
	{
		String verdictFor = memberName;
		if (verdictFor.isEmpty())
		{
			return false;
		}
		Player player = client.getLocalPlayer();
		String name = player == null ? null : player.getName();
		return name == null || GroupMembers.key(name).equals(verdictFor);
	}

	/**
	 * The "Join group sync" button, and a successful "Paste group key". Joining is
	 * always the player's own click: "Auto-rejoin for parties" is a rejected
	 * RuneLite feature ("would put undue strain on the party servers",
	 * github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features).
	 */
	void joinGroup()
	{
		if (member() && sync.join(config.groupSync(), members.group(), config.groupKey()))
		{
			onChange.run();
		}
	}

	/** The "Switch to group sync" button, shown only while in a party the player joined themselves. */
	void switchToGroup()
	{
		if (member() && sync.switchParty(config.groupSync(), members.group(), config.groupKey()))
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
		setConfig(GroupIronmanConfig.KEY_GROUP_KEY, GroupKey.generate());
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
		return asSelf(governance, g -> g.claim(self, System.currentTimeMillis()));
	}

	/** The leader's "Make editor" and "Remove editor". False when refused. */
	boolean setEditor(String name, boolean editor)
	{
		return asSelf(governance, g -> g.setEditor(self, name, editor, System.currentTimeMillis()));
	}

	/** {@code action} on {@code target} (read once by the caller), false when not running or not logged in. */
	private <T> boolean asSelf(T target, Predicate<T> action)
	{
		return target != null && !self.isEmpty() && action.test(target);
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
		return asSelf(governance, g -> g.shareWebhooks(self, share, Webhooks.of(config), System.currentTimeMillis()));
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
		ifSet(governance, GovernanceSync::forgetLeader);
	}

	/** {@code action} on {@code target} (read once by the caller) unless it is null: a part not running. */
	private static <T> void ifSet(T target, Consumer<T> action)
	{
		if (target != null)
		{
			action.accept(target);
		}
	}

	/** This member's own "don't post" (or overlay "don't show") for one kind. */
	void optOut(GroupEvent.Type type, boolean overlay, boolean out)
	{
		String key = overlay ? GroupIronmanConfig.KEY_MUTE_TYPES : GroupIronmanConfig.KEY_OPT_OUT_TYPES;
		Set<GroupEvent.Type> types = MemberPrefs.parse(overlay ? config.overlayMuteTypes() : config.eventOptOutTypes());
		if (out ? types.add(type) : types.remove(type))
		{
			setConfig(key, MemberPrefs.format(types));
		}
	}

	private String getConfig(String key)
	{
		return configManager.getConfiguration(GroupIronmanConfig.GROUP, key);
	}

	private void setConfig(String key, String value)
	{
		configManager.setConfiguration(GroupIronmanConfig.GROUP, key, value);
	}

	/** For the config writes that are not strings (SharingPrompt's), which ConfigManager stringifies itself. */
	private void setConfigObject(String key, Object value)
	{
		configManager.setConfiguration(GroupIronmanConfig.GROUP, key, value);
	}

	/** "Don't post my events". */
	void optOutAll(boolean out)
	{
		setConfigObject(GroupIronmanConfig.KEY_OPT_OUT_ALL, out);
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
		setConfig(GroupIronmanConfig.KEY_GROUP_KEY, key);
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
		SharingPrompt.turnOnSharing(this::setConfigObject);
	}

	/** "Post my events to Discord": the player's own click. */
	void postToDiscord()
	{
		SharingPrompt.postToDiscord(this::setConfigObject);
	}

	void reloadConfig()
	{
		if (sync != null && !config.groupSync())
		{
			// Turning sync off is the player's own action: leave our party, never someone else's.
			sync.leaveOwn();
		}
		String lowStockText = config.groupLowStock();
		if (!Objects.equals(lowStockText, this.lowStockText))
		{
			// Only a rules edit starts a new baseline; any other setting keeps what has already alerted.
			this.lowStockText = lowStockText;
			lowStock = LowStock.parse(lowStockText);
		}
		String key = GroupKey.normalize(config.groupKey());
		if (keySeen != null && !keySeen.equals(key) && !key.isEmpty())
		{
			// A new group key is a new group: the next member's roster binds it (GroupBinding).
			configManager.unsetConfiguration(GroupIronmanConfig.GROUP, GroupIronmanConfig.KEY_BOUND_GROUP);
			memberName = "";
			rebind = true;
			ticksSinceRoster = ROSTER_INTERVAL_TICKS;
		}
		keySeen = key;
		ifSet(governance, g -> g.prefs(MemberPrefs.of(config)));
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
			resendLive();
			// Turning sharing off cleared every card's items, this player's own too: put it back.
			lastCarriedLocal = null;
		});
		onChange.run();
	}

	/** Stats, carried items and extras all go out again on their next turn, unchanged or not. */
	private void resendLive()
	{
		lastStatsSent = null;
		lastCarriedSent = null;
		lastExtrasSent = null;
		statsDirty = true;
		carriedDirty = true;
		extrasDirty = true;
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
		// Fill / Empty on a log basket, barrel or coal bag: read only, the click goes through untouched.
		containers.clicked(event.getMenuOption(), event.getMenuTarget());
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
			// Only the first contents of a visit can come back: a silent snapshot correction.
			commitStorage(storage.contents(System.currentTimeMillis(), localName(), counts(event.getItemContainer())));
		}
		else if (id == InventoryID.INV || id == InventoryID.WORN)
		{
			containers.itemsChanged();
			carriedDirty = true;
		}
		else if (id == LOOTING_BAG_CONTAINER)
		{
			containers.lootingBag(event.getItemContainer());
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

	/** Logout or shutdown: the next login may be another account, whose bests must not start as these. */
	private void forgetOwnBests()
	{
		pbCapture.load(Collections.emptyMap());
		pbDirty = false;
		ownAchievements = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN && session)
		{
			// Logged out: remember when, so the next login knows what is new since.
			session = false;
			forgetOwnBests();
			roster.markPlayed(System.currentTimeMillis());
			saveCache();
		}
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			// Another account may log in next: its own counts are restored from its profile.
			containers.loggedOut();
			hiscoresOnLogin = true;
			refreshPresence();
			// The next login may be another account: its roster is read on its first tick, before anything runs.
			ticksSinceRoster = ROSTER_INTERVAL_TICKS;
			toldNotMember = false;
			rosterWaits = 0;
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
		if (event.getType() == ChatMessageType.GAMEMESSAGE || event.getType() == ChatMessageType.SPAM)
		{
			carriedDirty |= containers.chat(event.getMessage());
		}
		if (event.getType() == ChatMessageType.GAMEMESSAGE && member() && config.groupShareEvents()
			&& pbCapture.chat(event.getMessage()))
		{
			pbDirty = true;
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
		String bound = getConfig(GroupIronmanConfig.KEY_BOUND_GROUP);
		if (!member() && bound != null && !bound.isEmpty())
		{
			// Another group's chat: its key is not this client's group's, and would tie the client to it.
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
		setConfig(GroupIronmanConfig.KEY_GROUP_KEY, key);
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

	/** The chat command that sends a short note to the group: {@code ::announce Raid at 8pm}. */
	static final String ANNOUNCE_COMMAND = "announce";

	/** Client thread. The player's own typed note, from the engine; a line back when it could not go as typed. */
	private void announce(String text)
	{
		GroupEventEngine engine = events;
		if (engine == null || !member())
		{
			tell("Announcements need you to be in the group.");
			return;
		}
		String reply = engine.announce(text);
		if (reply != null)
		{
			tell(reply);
		}
	}

	private void tell(String text)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(GroupIronmanPlugin.NAME + ": " + text)
			.build());
	}

	/** One payload to the party, which RuneLite sends on its own thread. */
	private void send(String payload)
	{
		partyService.send(new VvgGroupIronmanMessage(payload));
	}

	/**
	 * {@code ::find dragon hunter wand}. RuneLite's ChatInputManager posts
	 * this instead of sending the text, so the query never reaches public
	 * chat, and the answer goes only to this player's chatbox.
	 */
	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		if (ANNOUNCE_COMMAND.equalsIgnoreCase(event.getCommand()))
		{
			announce(String.join(" ", event.getArguments()));
			return;
		}
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
		if (!member() || !roster.events().add(event))
		{
			return;
		}
		resolveNow(event.items.keySet());
		if (syncing())
		{
			GroupOutbox.events(gson, switches(), Collections.singletonList(event)).forEach(this::send);
		}
		saveCache();
		onChange.run();
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.SHARED_BANK)
		{
			// Not a save: closing without Save discards the changes (Board 2026-11-14).
			storage.close(System.currentTimeMillis());
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
			here = null;
			drawnFrom = null;
			return;
		}
		WorldPoint before = here;
		here = position();
		redrawIfMoved();
		if (here != null && (before == null || before.getRegionID() != here.getRegionID())
			&& GearChecks.forRegion(here.getRegionID()) != null)
		{
			// Entering a place with a gear rule: the Gear tab picks it up on this redraw.
			onChange.run();
		}
		long now = System.currentTimeMillis();
		refreshRoster();
		if (!member())
		{
			// Board 2026-11-15: an account not in the group gets nothing recorded, saved or sent.
			return;
		}
		watchStorageSave(now);
		if (!session)
		{
			session = true;
			// Fill your card now: turning the plugin on while logged in fires no container or stat events.
			statsDirty = true;
			carriedDirty = true;
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
		pollQuests();
		shopAlert();
		sealRequests();
		carriedDirty |= containers.tick();
		GroupOutbox.Switches s = switches();
		updateCarried(s);
		if (syncing())
		{
			flushGovernance();
			sendBoardBeacon();
		}
		// Your own card is filled from this client, synced or not; only the party send waits on syncing.
		pollStats(s);

		if (extrasDirty && ticksSinceExtras >= EXTRAS_INTERVAL_TICKS)
		{
			extrasDirty = false;
			sendExtras(s);
		}
	}

	/** Names already reported as removed this session, so each is logged once. */
	private final Set<String> purgeLogged = new HashSet<>();

	/**
	 * Removes every entry filed under a name that is not on the group roster from this client's Log, storage log,
	 * banks and live data. Each client runs this on its own copy, so the entries go from everyone's, including the
	 * client that recorded them (Board 2026-11-17). Never the local player or a roster member, and nothing while
	 * the roster is unknown.
	 */
	private void purgeNonRoster(GroupMembers roster)
	{
		String self = localName();
		Set<String> hit = this.roster.purge(name -> EventBackfill.offRoster(roster, self, name));
		if (hit.isEmpty())
		{
			return;
		}
		for (String name : hit)
		{
			if (purgeLogged.add(GroupMembers.key(name)))
			{
				log.debug("removed entries from {}: not on the group roster", name);
			}
		}
		saveCache();
		onChange.run();
	}

	/** Any thread: whether this client keeps the live status board, so its beacon goes out while it does. */
	void keepingBoard(boolean keeping)
	{
		keepingBoard = keeping;
	}

	/**
	 * Any thread: whether another member's client keeps the live status board, heard of in the last
	 * {@link BoardBeacon#STALE_MS}. This client then posts no separate online or offline messages.
	 */
	boolean boardKeptByOthers(long now)
	{
		return boardBeacon.fresh(now);
	}

	/** Client thread, syncing: a beacon about once a minute while this client keeps the board. */
	private void sendBoardBeacon()
	{
		if (!keepingBoard)
		{
			// The next time it keeps the board, the beacon goes out at once.
			ticksSinceBeacon = STATS_HEARTBEAT_TICKS;
			return;
		}
		if (++ticksSinceBeacon >= STATS_HEARTBEAT_TICKS)
		{
			ticksSinceBeacon = 0;
			send(BoardBeacon.encode(gson, System.currentTimeMillis()));
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
		boolean wasMember = member();
		checkMember(now);
		boolean isMember = member();
		if (isMember)
		{
			// Another group's roster must never clear this group's cache.
			purgeNonRoster(now);
		}
		changed |= wasMember != isMember;
		if (now.known() && isMember)
		{
			ifSet(governance, g -> g.group(now.group()));
		}
		// Anything members sent while this account was not one was dropped: ask again.
		if ((first || isMember && !wasMember) && syncing())
		{
			partyService.send(new UserSync());
		}
		if (changed)
		{
			onChange.run();
		}
		refreshPresence();
		if (isMember)
		{
			// Another group's roster is not looked up: its levels would land in this group's cache.
			hiscoresAfterRoster();
		}
	}

	/**
	 * Client thread, with each roster read: whether the account logged in is in this client's group
	 * ({@link GroupBinding}), binding the group on the first member's login. Says so once per login when it is not.
	 */
	private void checkMember(GroupMembers roster)
	{
		Player player = client.getLocalPlayer();
		String name = player == null || player.getName() == null ? "" : player.getName();
		String bound = getConfig(GroupIronmanConfig.KEY_BOUND_GROUP);
		boolean unbound = bound == null || bound.isEmpty();
		if (unbound && !cacheLoaded && !rebind)
		{
			// The members this client has heard from decide the first binding: not before the cache is read.
			memberName = "";
			return;
		}
		GroupBinding.Result r = rebind
			? GroupBinding.check(bound, roster, name, "", Collections.emptyList())
			: GroupBinding.check(bound, roster, name, savedRolesGroup(), heardNames());
		if (r.bind != null)
		{
			setConfig(GroupIronmanConfig.KEY_BOUND_GROUP, r.bind);
			rebind = false;
		}
		memberName = r.member() ? GroupMembers.key(name) : "";
		if (r.member())
		{
			rosterWaits = 0;
			return;
		}
		rosterWaits++;
		// A roster that is still loading waits quietly; one that never comes (no group) is said after ~30 s.
		boolean say = r.verdict == GroupBinding.Verdict.NOT_MEMBER || rosterWaits >= NOT_MEMBER_WAITS;
		if (say && !toldNotMember && !name.isEmpty())
		{
			toldNotMember = true;
			log.info("logged-in account is not in this client's group: nothing is posted, logged or shared for it");
			tell(name + (r.verdict == GroupBinding.Verdict.NOT_MEMBER
				? " isn't in the group this client is set up for" : " isn't on a Group Ironman group")
				+ ": nothing is posted to Discord, logged or shared for this account.");
		}
	}

	/** The group the saved signed roles are for ({@link GovernanceSync#KEY_ROLES}), "" when none are saved. */
	private String savedRolesGroup()
	{
		GovernanceCodec.Message m = GovernanceCodec.decode(gson, getConfig(GovernanceSync.KEY_ROLES));
		return m == null || m.roles == null || m.roles.group == null ? "" : m.roles.group;
	}

	/** Every member this client holds data for, from the group cache. */
	private Set<String> heardNames()
	{
		Set<String> out = new HashSet<>(roster.lastHeard().keySet());
		out.addAll(roster.allStats().keySet());
		return out;
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
	 * Client thread. Starts a hiscore lookup, on OkHttp's threads, for each of {@code names} and for this
	 * player, when it is due ({@link HiscoreBook#begin}); shared stats still win over the
	 * looked-up levels wherever both exist. {@code asked}:
	 * this player just logged in or these members just came online.
	 */
	private void lookUpHiscores(Collection<String> names, boolean asked)
	{
		if (!cacheLoaded || !anyOn() || names.isEmpty())
		{
			return;
		}
		// Everyone, this player too: members who share stats still need their boss, clue and minigame counts for
		// the Leaderboard. Shared levels still win wherever both exist.
		List<String> everyone = new ArrayList<>(names);
		if (!self.isEmpty())
		{
			everyone.add(self);
		}
		long now = System.currentTimeMillis();
		for (String name : everyone)
		{
			if (roster.hiscores().begin(name, now, asked))
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
			roster.hiscores().succeeded(name, levels, HiscoreBook.scores(result), now);
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
		// Another group's channel is not this group's: an account not in it shows, and posts, nobody online.
		GamePresence now = client.getGameState() == GameState.LOGGED_IN && member()
			? GamePresence.read(members, readChannel(), readFriends()) : GamePresence.UNKNOWN;
		if (now.equals(presence))
		{
			return;
		}
		GamePresence before = presence;
		presence = now;
		lookUpHiscores(GamePresence.cameOnline(members.names(), before, now), true);
		ifSet(events, e -> e.gamePresence(now));
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

	/** Client thread. The pet following the local player, by name; "" when none is out. */
	private String followerName()
	{
		NPC pet = client.getFollower();
		String name = pet == null ? null : pet.getName();
		return name == null ? "" : name;
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
		MemberStatus status = MemberStatus.fromGame(client.getVarbitValue(VarbitID.PRAYER_ALLACTIVE),
			client.getVarpValue(VarPlayerID.POISON), client.getVarpValue(VarPlayerID.DISEASE))
			.withPet(followerName());
		if (run != lastRun || spec != lastSpec || world != lastWorld || region != lastRegion
			|| plane != lastPlane || Math.abs(x - lastX) + Math.abs(y - lastY) >= MOVE_TILES
			|| !status.equals(lastStatus))
		{
			lastStatus = status;
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
			// Nothing changed for a minute: recapture so your own card stays online, and while syncing send the
			// same again so members still see us online.
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
			resendLive();
			// Sent on the next tick: roles first, then the settings they authorise.
			resendShared();
			if (!syncing())
			{
				return;
			}
			if (lastBank != null)
			{
				GroupOutbox.bank(gson, switches(), lastBank).forEach(this::send);
			}
			// The joiner may have been offline for these: this is how "since you last played" works with no server.
			uploadOwnEvents();
			if (lastActivity.active())
			{
				// A joiner mid-run sees it now, not at the next wave.
				shareActivity(lastActivity);
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
		resendShared();
		assembler.clear();
		roster.clearStrangers();
		resendLive();
		if (syncing())
		{
			// Joining the sync later: what this player did while not synced goes up now (Board 2026-11-17).
			uploadOwnEvents();
		}
		onChange.run();
	}

	/**
	 * Sends this player's recent events, drops and log slots under the drops switch and every other kind under
	 * "Share group events". Receivers keep their original times and log each once ({@link EventBackfill}).
	 */
	private void uploadOwnEvents()
	{
		if (!syncing() || !member())
		{
			return;
		}
		long now = System.currentTimeMillis();
		List<GroupEvent> mine = EventBackfill.upload(roster, localName(), now);
		List<GroupEvent> drops = new ArrayList<>();
		for (GroupEvent e : mine)
		{
			if (e.type.logged())
			{
				drops.add(e);
			}
		}
		GroupOutbox.events(gson, switches(), drops).forEach(this::send);
		GroupOutbox.groupEvents(gson, config.groupShareEvents(), mine).forEach(this::send);
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
				resolve(decoded.carried.ids());
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
					resolve(ItemCounts.ids(change.after(), change.took(), change.deposited()));
					// A late or out-of-order visit is history: it must not alert on a stale snapshot (RUNELITE-510).
					if (!EventBackfill.late(change.at(), now) && roster.storageAt() == change.at())
					{
						checkLowStock(change.after());
					}
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
				resolve(ItemCounts.ids(decoded.extras.runePouch, decoded.extras.quiver, decoded.extras.seedVault));
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

	/**
	 * A payload {@link GroupCodec#decode} does not read: roles, group settings or an announce, a "posted" claim, or
	 * one from a newer build.
	 */
	private void notData(String name, VvgGroupIronmanMessage message)
	{
		GovernanceSync g = governance;
		String payload = message.getPayload();
		if (ActivityCodec.isActivity(payload))
		{
			receiveActivity(name, payload);
			return;
		}
		if (BoardBeacon.isBeacon(payload))
		{
			if (BoardBeacon.valid(gson, payload))
			{
				boardBeacon.heard(System.currentTimeMillis());
			}
			return;
		}
		if (g != null && GovernanceSync.isGovernance(payload) && g.receive(name, payload))
		{
			roster.heard(name, System.currentTimeMillis());
			return;
		}
		MemberProgressBook book = progress;
		if (book != null && MemberProgressCodec.isProgress(payload) && book.receive(name, payload) != null)
		{
			roster.heard(name, System.currentTimeMillis());
			onChange.run();
			return;
		}
		ShoppingSync shop = shopping;
		if (shop != null && ShoppingSync.isShopping(payload) && shop.receive(name, payload))
		{
			roster.heard(name, System.currentTimeMillis());
			resolve(shoppingItemIds());
			return;
		}
		List<String> posted = GroupCodec.posted(gson, payload);
		if (posted != null)
		{
			receivePosted(posted);
			roster.heard(name, System.currentTimeMillis());
			return;
		}
		log.debug("ignored unreadable group payload from {}", message.getMemberId());
	}

	/** Game tick, connected: roles, settings and this member's announce, then shopping lists, whichever changed. */
	private void flushGovernance()
	{
		ifSet(governance, g -> g.flush(MemberPrefs.of(config)));
		ifSet(shopping, shop -> shop.flush(self));
		if (pbDirty && config.groupShareEvents())
		{
			pbDirty = false;
			PersonalBests mine = pbCapture.bests();
			if (!mine.isEmpty())
			{
				ifSet(progress, book -> book.publish(mine, null));
			}
		}
		AchievementState ach = ownAchievements;
		if (ach != null && config.groupShareEvents())
		{
			ifSet(progress, book -> book.publish(null, ach));
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
		long now = System.currentTimeMillis();
		for (GroupEvent e : received)
		{
			GroupEvent mine = e.withMember(name);
			boolean logged = e.type.logged();
			if (logged ? !s.drops || !roster.events().add(mine) : !share)
			{
				continue;
			}
			// A message carrying several events is catch-up, never something happening now (RUNELITE-510).
			EventBackfill.Arrival arrival = EventBackfill.classify(roster.feed(), mine, now, received.size());
			if (arrival == EventBackfill.Arrival.DUPLICATE)
			{
				continue;
			}
			resolve(e.items.keySet());
			added |= logged;
			if (engine != null)
			{
				// Earlier events a member uploaded on joining the sync, or any catch-up batch, reach the Log only.
				if (arrival == EventBackfill.Arrival.BACKFILL)
				{
					engine.backfill(mine);
				}
				else
				{
					engine.member(mine);
				}
			}
		}
		if (added)
		{
			saveCache();
		}
		return true;
	}

	/** A member's client posted these game broadcasts: this one never posts them too. */
	private void receivePosted(List<String> keys)
	{
		ifSet(events, e -> e.claimed(keys));
	}

	private void memberStorage(StorageChange change)
	{
		// Names resolve on the client thread; the value of a big move needs them.
		ifSet(events, e -> clientThread.invokeLater(() -> e.storage(change, false)));
	}

	/**
	 * Client thread. The game's "Saving..." loader (interface 293, child 1) is
	 * shown for every save, including one a close makes, so its first tick is
	 * a save; the same check the Group Ironmen Tracker plugin commits on.
	 */
	private void watchStorageSave(long now)
	{
		Widget loader = client.getWidget(InterfaceID.LOADING_ICON_MODAL, STORAGE_LOADER_TEXT);
		boolean saving = loader != null && !loader.isHidden()
			&& "Saving...".equalsIgnoreCase(Text.removeTags(loader.getText()));
		if (saving && !storageSaving)
		{
			commitStorage(storage.save(now, localName()));
		}
		storageSaving = saving;
	}

	/**
	 * Client thread. A save the game made, or the first contents of a visit
	 * differing from the last snapshot: the contents become the storage
	 * snapshot even when nothing moved, a change is logged locally whether or
	 * not sync is on, and only a change is sent.
	 */
	private void commitStorage(StorageChange change)
	{
		// Another group's storage is not this group's: not logged, not checked, not sent.
		if (change == null || !member())
		{
			return;
		}
		resolveNow(ItemCounts.ids(change.after(), change.took(), change.deposited()));

		// Minimums are checked on every own save, shared or not: the alert is for this player.
		checkLowStockNow(change.after(), true);
		ifSet(events, e -> e.storage(change, true));

		// Kept locally whether shared or not; with sharing off it is marked so and nothing is sent.
		String payload = GroupOutbox.ownStorage(gson, switches(), change, roster);
		if (payload != null && syncing())
		{
			send(payload);
		}
		saveCache();
		onChange.run();
	}

	/** Client thread. */
	private void closeBank()
	{
		GroupOutbox.Switches s = switches();
		if (!s.bank || !member())
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
			GroupOutbox.bank(gson, s, snapshot).forEach(this::send);
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
		MemberStats stats = new MemberStats(skills, lastRun, lastSpec, lastWorld, lastRegion, lastX, lastY, lastPlane,
			lastStatus);
		String me = localName();
		boolean changed = !stats.equals(roster.allStats().get(me));
		roster.putStats(me, stats);
		roster.heard(me, System.currentTimeMillis());
		if (changed)
		{
			onChange.run();
		}
		String payload = syncing() ? GroupOutbox.stats(gson, s, stats) : null;
		if (payload == null)
		{
			// Nothing goes out (sync off or the stats switch off); this still paces the next local capture.
			ticksSinceStats = 0;
			return;
		}
		if (Objects.equals(payload, lastStatsSent))
		{
			return;
		}
		send(payload);
		lastStatsSent = payload;
		ticksSinceStats = 0;
	}

	/** Client thread. */
	private void sendExtras(GroupOutbox.Switches s)
	{
		MemberExtras extras = new MemberExtras(runePouch(), quiver(), seedVault, seedVaultAt);
		String me = localName();
		boolean changed = !extras.equals(roster.extras().get(me));
		if (changed)
		{
			// Names first: a changed card redraws straight away.
			resolveNow(ItemCounts.ids(extras.runePouch, extras.quiver, extras.seedVault));
			roster.putExtras(me, extras);
			onChange.run();
		}
		String payload = syncing() ? GroupOutbox.extras(gson, s, extras) : null;
		if (payload == null)
		{
			ticksSinceExtras = 0;
			return;
		}
		if (Objects.equals(payload, lastExtrasSent))
		{
			return;
		}
		send(payload);
		lastExtrasSent = payload;
		ticksSinceExtras = 0;
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

	/**
	 * Client thread. This player's own inventory, worn items and container contents onto their own card, joined
	 * or not (Board 2026-11-14: the card shows them while 'Not connected'); sent to the party only when syncing
	 * and shared.
	 */
	private void updateCarried(GroupOutbox.Switches s)
	{
		if (!carriedDirty || ticksSinceCarried < CARRIED_INTERVAL_TICKS)
		{
			return;
		}
		carriedDirty = false;
		ticksSinceCarried = 0;
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		GroupCodec.Carried carried = new GroupCodec.Carried(counts(inv),
			counts(client.getItemContainer(InventoryID.WORN)), inventorySlots(inv), containers.held(),
			jewelleryCharges(inv, client.getItemContainer(InventoryID.WORN)));
		String local = GroupCodec.encodeCarried(gson, carried);
		if (!local.equals(lastCarriedLocal))
		{
			lastCarriedLocal = local;
			resolveNow(carried.ids());
			roster.putCarried(localName(), carried);
			onChange.run();
		}
		String payload = syncing() ? GroupOutbox.carried(gson, s, carried) : null;
		if (payload != null && !Objects.equals(payload, lastCarriedSent))
		{
			send(payload);
			lastCarriedSent = payload;
		}
	}

	/** Client thread. Charges on jewellery in the inventory or worn, read as Item Charges shows them. */
	private Map<String, Integer> jewelleryCharges(ItemContainer... sources)
	{
		List<String> names = new ArrayList<>();
		for (ItemContainer container : sources)
		{
			if (container == null)
			{
				continue;
			}
			for (Item item : container.getItems())
			{
				if (item.getId() > 0)
				{
					names.add(itemManager.getItemComposition(item.getId()).getName());
				}
			}
		}
		return JewelleryCharges.of(names, key ->
			configManager.getRSProfileConfiguration(JewelleryCharges.CONFIG_GROUP, key, Integer.class));
	}

	/** Client thread. The inventory's slots in order with raw ids, so a noted stack draws noted; none without one. */
	private static List<GroupCodec.Slot> inventorySlots(ItemContainer container)
	{
		List<GroupCodec.Slot> out = new ArrayList<>();
		if (container == null)
		{
			return out;
		}
		for (Item item : container.getItems())
		{
			out.add(GroupCodec.Slot.of(item.getId(), item.getQuantity()));
		}
		// The game trims trailing empty slots; an inventory with nothing in it is still 28 empty slots.
		if (out.isEmpty())
		{
			out.add(GroupCodec.Slot.EMPTY);
		}
		return out;
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
		// A member's first state after a restart or reconnect is a baseline, not an alert for every short item.
		LowStock rules = lowStock;
		for (LowStock.Alert alert : own ? rules.check(storage, lookup) : rules.checkMember(storage, lookup))
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

	private static int styleOf(ItemEquipmentStats g)
	{
		return ItemStyles.classify(g.getSlot(), Math.max(g.getAstab(), Math.max(g.getAslash(), g.getAcrush())),
			g.getArange(), g.getAmagic(), g.getStr(), g.getRstr(), g.getMdmg(),
			Math.max(g.getDstab(), Math.max(g.getDslash(), g.getDcrush())), g.getDrange(), g.getDmagic(),
			g.getPrayer());
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
			String name = item == null ? null : ItemNames.resolve(id,
				i -> itemManager.getItemComposition(i).getName(), itemManager::canonicalize);
			if (name != null)
			{
				names.put(id, name);
				tradeable.put(id, item.isTradeable());
				prices.put(id, (long) itemManager.getItemPrice(id));
				readGear(id, item);
			}
		}
	}

	/** Client thread: the item's equipment slot and combat style from its stats, and its alchemy value. */
	private void readGear(int id, ItemComposition item)
	{
		ItemStats stats = itemManager.getItemStats(id);
		ItemEquipmentStats gear = stats == null ? null : stats.getEquipment();
		slots.put(id, gear == null ? -1 : gear.getSlot());
		styles.put(id, gear == null ? 0 : styleOf(gear));
		highAlchs.put(id, (long) item.getHaPrice());
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
			String json = PluginFiles.read(cacheFile());
			if (json == null)
			{
				return false;
			}
			if (!GroupCache.read(gson, json, roster))
			{
				log.debug("group cache unreadable, starting empty");
				return false;
			}
			restoreWebhooks(GroupCache.webhooks(gson, json));
			ifSet(shopping, shop -> shop.fromCache(GroupCache.shop(gson, json), System.currentTimeMillis()));
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
		ShoppingSync shop = shopping;
		String json = GroupCache.write(gson, roster, cachedWebhooks, shop == null ? null : shop.toCache());
		executor.execute(() ->
		{
			try
			{
				PluginFiles.write(cacheFile(), json, "group-cache");
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
		return dataFile(CACHE_FILE);
	}

	/** Executor thread. {@code name} inside the plugin's data directory. */
	Filepath dataFile(String name) throws Exception
	{
		return dataDirectory.call().joinSegment(name);
	}

	/** True once the group cache (roster, the leader's shared webhooks) has been read at startup. */
	boolean cacheLoaded()
	{
		return cacheLoaded;
	}

	// ---- The Shopping tab ----

	/** Members' boss bests and quests, or null when not running. */
	MemberProgressBook progress()
	{
		return progress;
	}

	/** Everyone's shared bests and quests, this player's own included, for the panel. */
	ProgressView progressView()
	{
		return ProgressView.of(progress, self, member() && config.groupShareEvents() ? pbCapture.bests() : null,
			config.groupShareEvents() ? ownAchievements : null);
	}

	/** What the Compare tab shows. */
	CompareTab.View compareView(ProgressView view)
	{
		List<String> names = new ArrayList<>(members.names());
		if (!self.isEmpty() && names.stream().noneMatch(n -> n.equalsIgnoreCase(self)))
		{
			names.add(self);
		}
		return new CompareTab.View(self, CompareSupport.figures(roster, names, view), names,
			CompareSupport.banked(roster, names), CompareSupport.storageBanked(roster));
	}

	/** What the Gear tab shows: this player's last shared inventory and worn items, the region, and the group's items. */
	GearTab.View gearView()
	{
		GroupCodec.Carried mine = roster.allCarried().get(self);
		WorldPoint at = here;
		return new GearTab.View(self, at == null ? 0 : at.getRegionID(),
			mine == null ? Collections.emptyMap() : mine.inventory, mine == null ? Collections.emptyMap() : mine.worn,
			roster.searchSources(), lookup);
	}

	/**
	 * Gear tab: ask the group for {@code itemName}, or put it on this player's own shopping list. The id comes from
	 * the item search on the client thread; {@code done} gets the line to show, on that thread.
	 */
	void gearItem(String itemName, boolean shopping, Consumer<String> done)
	{
		clientThread.invokeLater(() ->
		{
			Integer id = null;
			for (ItemPrice p : itemManager.search(itemName))
			{
				if (GearChecks.matches(p.getName(), itemName))
				{
					id = p.getId();
					names.putIfAbsent(p.getId(), p.getName());
					break;
				}
			}
			if (id == null)
			{
				done.accept("Could not find " + itemName + " to add.");
				return;
			}
			Map<Integer, Integer> one = Collections.singletonMap(id, 1);
			boolean ok = shopping
				? addShoppingList(itemName, "", Collections.emptyList(), Collections.singletonList(self), one)
				: addRequest(itemName, one);
			done.accept(ok ? (shopping ? "Added " + itemName + " to your shopping lists."
				: "Asked the group for " + itemName + ".") : "Not added: log in first, or the list is full.");
		});
	}

	/** "Who benefits most" for an item picked from the dropdown; {@code done} runs on the client thread. */
	void benefit(int itemId, Consumer<BenefitFinder.Outcome> done)
	{
		List<String> names = new ArrayList<>(members.names());
		if (!self.isEmpty() && names.stream().noneMatch(n -> n.equalsIgnoreCase(self)))
		{
			names.add(self);
		}
		ItemBenefit.Settings settings = ItemBenefit.Settings.parse(config.compareStats(), config.compareCategories());
		clientThread.invokeLater(() -> done.accept(BenefitFinder.runId(itemId, CompareSupport.source(itemManager),
			CompareSupport.members(roster, names), settings)));
	}

	/** Summed hiscore experience per skill for up to a group's worth of players; null when none were read. */
	void groupExperience(List<String> names, Consumer<Map<String, Long>> done)
	{
		CompareSupport.groupExperience(hiscoreClient,
			names.size() > CompareTab.MAX_OTHER_NAMES ? names.subList(0, CompareTab.MAX_OTHER_NAMES) : names, done);
	}

	/** The shared lists, or null when not running. */
	ShoppingSync shopping()
	{
		return shopping;
	}

	ShopPresets presets()
	{
		return presets;
	}

	/** Client thread. Redraws the panel once the player is far enough from where it last drew "Path to next". */
	private void redrawIfMoved()
	{
		if (ItemPaths.movedFar(drawnFrom, here))
		{
			drawnFrom = here;
			onChange.run();
		}
	}

	/** Where this player stood on the last game tick; null when logged out. Any thread. */
	WorldPoint here()
	{
		return here;
	}

	/** What a {@link ShoppingBoard} works from, as this client knows it now. Any thread. */
	ShoppingBoard.Inputs shoppingInputs()
	{
		ShoppingSync shop = shopping;
		QuestProgress mine = shop == null ? QuestProgress.UNKNOWN : shop.progress(self);
		return new ShoppingBoard.Inputs(questData, presets,
			member -> shop == null ? QuestProgress.UNKNOWN : shop.progress(member),
			ShopAllocation.Holdings.from(roster.searchSources()), id -> tradeable.getOrDefault(id, false),
			lookup::name, new ShopSources.Viewer(ownLevels, mine));
	}

	/** "Add list": false when not logged in, not started or the book is full. */
	boolean addShoppingList(String name, String preset, List<String> picks, List<String> members,
		Map<Integer, Integer> items)
	{
		boolean ok = !members.isEmpty() && asSelf(shopping, shop -> shop.add(ShoppingList.create(name, preset, picks,
			members, items, self, System.currentTimeMillis())));
		if (ok)
		{
			resolve(shoppingItemIds());
		}
		return ok;
	}

	boolean renameShoppingList(String id, String name)
	{
		return asSelf(shopping, shop -> shop.rename(id, name, self, System.currentTimeMillis()));
	}

	boolean removeShoppingList(String id)
	{
		return asSelf(shopping, shop -> shop.remove(id, self, System.currentTimeMillis()));
	}

	/** A custom list's item search, on the client thread; {@code done} gets tradeable matches, best first. */
	void searchItems(String query, Consumer<List<ShoppingTab.Hit>> done)
	{
		clientThread.invokeLater(() ->
		{
			List<ShoppingTab.Hit> hits = new ArrayList<>();
			for (ItemPrice p : itemManager.search(query))
			{
				if (hits.size() >= ShoppingTab.MAX_HITS)
				{
					break;
				}
				names.putIfAbsent(p.getId(), p.getName());
				hits.add(new ShoppingTab.Hit(p.getId(), p.getName()));
			}
			done.accept(hits);
		});
	}

	// ---- Requests: each member's own lists the group fills ----

	/** A request of this player's own; a blank name becomes "Name's requests". False when refused. */
	boolean addRequest(String name, Map<Integer, Integer> items)
	{
		ShoppingSync shop = shopping;
		if (shop == null || self.isEmpty())
		{
			return false;
		}
		long now = System.currentTimeMillis();
		RequestList r = RequestList.create(name, items, self, now);
		boolean ok = shop.addRequest(r);
		if (ok)
		{
			resolve(r.items.keySet());
			postRequest(RequestBoard.made(r, lookup::name, now));
		}
		return ok;
	}

	/** Owner only, as every request edit below. */
	boolean renameRequest(String id, String name)
	{
		return asSelf(shopping, shop -> shop.renameRequest(id, name, self, System.currentTimeMillis()));
	}

	boolean removeRequest(String id)
	{
		return asSelf(shopping, shop -> shop.removeRequest(id, self, System.currentTimeMillis()));
	}

	/** The owner closes the whole list by hand: no "done" post, since the group may not have it all. */
	boolean closeRequest(String id)
	{
		return asSelf(shopping, shop -> shop.closeRequest(id, self, System.currentTimeMillis()));
	}

	boolean closeRequestItem(String id, int itemId)
	{
		ShoppingSync shop = shopping;
		if (shop == null || self.isEmpty())
		{
			return false;
		}
		RequestList next = shop.closeItems(id, Collections.singleton(itemId), self, System.currentTimeMillis());
		postIfDone(next);
		return next != null;
	}

	/** "I'll get it" (or taking it back): anyone, on an open item. */
	boolean claimRequest(String id, int itemId, boolean claimed)
	{
		ShoppingSync shop = shopping;
		long now = System.currentTimeMillis();
		if (shop == null || self.isEmpty() || !shop.claim(id, itemId, self, claimed, now))
		{
			return false;
		}
		RequestList r = shop.request(id);
		if (claimed && r != null)
		{
			postRequest(RequestBoard.claimed(r, itemId, lookup.name(itemId), self, now));
		}
		return true;
	}

	/** Every request, worked out against what the group holds now. Any thread. */
	List<RequestBoard.Card> requestCards()
	{
		ShoppingSync shop = shopping;
		List<RequestBoard.Card> out = new ArrayList<>();
		if (shop == null)
		{
			return out;
		}
		ShopAllocation.Holdings holdings = ShopAllocation.Holdings.from(roster.searchSources());
		for (RequestList r : shop.requests())
		{
			out.add(RequestBoard.card(r, shop.claims(r.id), holdings, lookup::name));
		}
		return out;
	}

	/**
	 * Client thread, every {@link #SEAL_INTERVAL_TICKS}. Only the owner's client writes a request, so it is the
	 * one that closes items the group now holds enough of, and posts "done" (once) when that closes the list.
	 */
	private void sealRequests()
	{
		ShoppingSync shop = shopping;
		if (shop == null || self.isEmpty() || ++ticksSinceSeal < SEAL_INTERVAL_TICKS)
		{
			return;
		}
		ticksSinceSeal = 0;
		ShopAllocation.Holdings holdings = null;
		for (RequestList r : shop.requests())
		{
			if (!r.open() || !r.ownedBy(self))
			{
				continue;
			}
			if (holdings == null)
			{
				holdings = ShopAllocation.Holdings.from(roster.searchSources());
			}
			Set<Integer> reached = RequestBoard.reached(RequestBoard.card(r, Collections.emptyList(), holdings,
				lookup::name));
			postIfDone(shop.closeItems(r.id, reached, self, System.currentTimeMillis()));
		}
	}

	private void postIfDone(RequestList next)
	{
		if (next != null && next.closed)
		{
			postRequest(RequestBoard.done(next, next.at));
		}
	}

	/** One of this player's request events, to Discord and the banners under their own switches. */
	private void postRequest(GroupEvent event)
	{
		ifSet(events, e -> clientThread.invokeLater(() -> e.request(event)));
	}

	/** Someone joined or the party changed: roles, settings and every list, removals included, go out next tick. */
	private void resendShared()
	{
		ifSet(governance, GovernanceSync::resendAll);
		ifSet(shopping, ShoppingSync::resendAll);
		ifSet(progress, MemberProgressBook::resendAll);
		pbDirty = true;
	}

	/** Every item any live list may show, for names and whether it can change hands. */
	private Set<Integer> shoppingItemIds()
	{
		Set<Integer> ids = new HashSet<>();
		ShoppingSync shop = shopping;
		if (shop == null)
		{
			return ids;
		}
		for (RequestList r : shop.requests())
		{
			ids.addAll(r.items.keySet());
		}
		for (ShoppingList l : shop.active())
		{
			ids.addAll(l.items.keySet());
			ShopPresets.Preset p = l.custom() ? null : presets.preset(l.preset);
			if (p == null)
			{
				continue;
			}
			for (QuestData.Quest q : ShopNeeds.closure(questData, p.roots(l.picks)))
			{
				q.items().stream().filter(ShopNeeds::shoppable).forEach(i -> ids.add(i.itemId()));
			}
		}
		return ids;
	}

	/** Client thread. This player's quest progress and levels, every {@link #QUEST_INTERVAL_TICKS}. */
	private void pollQuests()
	{
		ShoppingSync shop = shopping;
		if (++ticksSinceQuests < QUEST_INTERVAL_TICKS || shop == null || self.isEmpty())
		{
			return;
		}
		ticksSinceQuests = 0;
		Map<String, Integer> levels = new HashMap<>();
		for (Skill skill : Skill.values())
		{
			if (skill != Skill.OVERALL)
			{
				levels.put(skill.getName(), client.getRealSkillLevel(skill));
			}
		}
		ownLevels = levels;
		shop.ownProgress(self, QuestStates.read(QuestStates.wanted(questData), q -> q.getState(client)));
		ownAchievements = readAchievements();
		resolveNow(shoppingItemIds());
	}

	/** Client thread. Every quest the game reports, quest points and diary tiers. */
	private AchievementState readAchievements()
	{
		List<String> finished = new ArrayList<>();
		List<String> started = new ArrayList<>();
		for (net.runelite.api.Quest q : net.runelite.api.Quest.values())
		{
			net.runelite.api.QuestState state = q.getState(client);
			if (state == net.runelite.api.QuestState.FINISHED)
			{
				finished.add(q.getName());
			}
			else if (state == net.runelite.api.QuestState.IN_PROGRESS)
			{
				started.add(q.getName());
			}
		}
		return AchievementCapture.state(client.getVarpValue(net.runelite.api.gameval.VarPlayerID.QP), finished,
			started, EventWatchers.diaryVarbits(), client::getVarbitValue);
	}

	/** Client thread. "Shopping: shop alert": once per approach to a shop selling something still needed. */
	private void shopAlert()
	{
		if (!config.shoppingShopAlert() || ++ticksSinceShopAlert < SHOP_ALERT_INTERVAL_TICKS)
		{
			return;
		}
		ticksSinceShopAlert = 0;
		ShoppingSync shop = shopping;
		WorldPoint at = position();
		List<ShoppingList> lists = shop == null ? Collections.emptyList() : shop.active();
		if (at == null || lists.isEmpty())
		{
			return;
		}
		Map<String, List<ShopAllocation.Line>> shops = ShoppingBoard.byShop(ShoppingBoard.of(lists, shoppingInputs()));
		for (String name : shopVisits.arrived(at.getX(), at.getY(), at.getPlane(), shops.keySet(), questData))
		{
			tell(name + ": " + ShoppingBoard.describe(shops.get(name)) + ".");
		}
	}

	/** Kept for the panel: is any group feature on; false shows its "everything is off" notice. */
	boolean anyOn()
	{
		return switches().any() || !lowStock.isEmpty();
	}
}
