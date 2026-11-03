package com.vinnyvangogh.buildguard;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
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
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.party.messages.UserSync;

/**
 * The Group Ironman extras: member banks, the group storage log, live stats
 * and where each member is, inventories and equipment, rune pouch, quiver and
 * seed vault, the drops and collection log list, online / last seen, "who has
 * it?" and storage minimums.
 *
 * <p>Registered on the event bus by {@link BuildGuardPlugin} while the plugin
 * runs. Every send goes through {@link GroupOutbox}, so a member with a
 * feature off sends nothing of it; every receive is dropped unless this
 * member shares the same feature, the rule {@link BuildGuardPlugin} already
 * applies to builds.
 *
 * <p>Data only ever comes from this player's own client: their bank and
 * group storage as the game shows them, their own stats. Nothing is read
 * from another player and nothing goes anywhere but RuneLite Party.
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

	private static final String CACHE_DIR = "build-guard";
	private static final String CACHE_FILE = "group-cache.json";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private BuildGuardConfig config;

	@Inject
	private Gson gson;

	@Inject
	private PartyService partyService;

	@Inject
	private ItemManager itemManager;

	@Inject
	private Notifier notifier;

	@Inject
	private ScheduledExecutorService executor;

	private final GroupRoster roster = new GroupRoster();
	private final BankAssembler assembler = new BankAssembler();
	private final Map<Integer, String> names = new ConcurrentHashMap<>();
	private final Map<Integer, Long> prices = new ConcurrentHashMap<>();
	private Runnable onChange = () -> { };

	private volatile LowStock lowStock = LowStock.parse("");

	// Client thread only.
	private boolean storageOpen;
	private Map<Integer, Integer> storageBefore;
	private Map<Integer, Integer> storageLatest;
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
	};

	void startUp(Runnable onChange)
	{
		this.onChange = onChange;
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
		cacheLoaded = false;
		session = false;
		seedVault = new TreeMap<>();
		seedVaultAt = -1;
		lastExtrasSent = null;
		roster.clearAll();
		assembler.clear();
		names.clear();
		prices.clear();
		storageOpen = false;
		storageBefore = null;
		storageLatest = null;
		lastBank = null;
		lastStatsSent = null;
		lastCarriedSent = null;
		onChange = () -> { };
	}

	GroupRoster roster()
	{
		return roster;
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

	void reloadConfig()
	{
		lowStock = LowStock.parse(config.groupLowStock());
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
			storageOpen = true;
			storageBefore = null;
			storageLatest = null;
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == InventoryID.INV_GROUP_TEMP && storageOpen)
		{
			Map<Integer, Integer> now = counts(event.getItemContainer());
			if (storageBefore == null)
			{
				storageBefore = now;
			}
			storageLatest = now;
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

	/** Client thread. Logs one of this player's own events and tells the party. */
	private void recordEvent(GroupEvent event)
	{
		if (!roster.events().add(event))
		{
			return;
		}
		resolveNow(event.items.keySet());
		if (partyService.isInParty())
		{
			for (String payload : GroupOutbox.events(gson, switches(), Collections.singletonList(event)))
			{
				partyService.send(new BuildGuardGroupMessage(payload));
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
			closeStorage();
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
		if (!partyService.isInParty())
		{
			return;
		}
		GroupOutbox.Switches s = switches();

		if (s.stats)
		{
			// Run energy, special attack and location fire no StatChanged; poll the cheap ints.
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
			if (!partyService.isInParty())
			{
				return;
			}
			if (lastBank != null)
			{
				for (String payload : GroupOutbox.bank(gson, switches(), lastBank))
				{
					partyService.send(new BuildGuardGroupMessage(payload));
				}
			}
			// The joiner may have been offline for these: this is how "since you last played" works with no server.
			List<GroupEvent> mine = roster.events().recentBy(localName(), System.currentTimeMillis());
			for (String payload : GroupOutbox.events(gson, switches(), mine))
			{
				partyService.send(new BuildGuardGroupMessage(payload));
			}
		});
	}

	@Subscribe
	public void onUserPart(UserPart event)
	{
		assembler.forget(event.getMemberId());
		PartyMember member = partyService.getMemberById(event.getMemberId());
		if (member != null && member.getDisplayName() != null)
		{
			roster.forgetLive(member.getDisplayName());
		}
		onChange.run();
	}

	void onPartyChanged()
	{
		assembler.clear();
		roster.clearLive();
		lastStatsSent = null;
		lastCarriedSent = null;
		lastExtrasSent = null;
		statsDirty = true;
		carriedDirty = true;
		extrasDirty = true;
		onChange.run();
	}

	@Subscribe
	public void onBuildGuardGroupMessage(BuildGuardGroupMessage message)
	{
		PartyMember local = partyService.getLocalMember();
		if (local != null && local.getMemberId() == message.getMemberId())
		{
			// Our own echo: already applied when sent.
			return;
		}
		GroupCodec.Decoded decoded = GroupCodec.decode(gson, message.getPayload());
		if (decoded == null)
		{
			log.debug("ignored unreadable group payload from {}", message.getMemberId());
			return;
		}
		PartyMember member = partyService.getMemberById(message.getMemberId());
		if (member == null || member.getDisplayName() == null || member.getDisplayName().trim().isEmpty())
		{
			// Banks and the log are kept by name across restarts; without a name there is nothing to file it under.
			log.debug("ignored group payload from unnamed member {}", message.getMemberId());
			return;
		}
		String name = member.getDisplayName();
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
				if (!s.drops)
				{
					return;
				}
				boolean added = false;
				for (GroupEvent e : decoded.events)
				{
					if (roster.events().add(e.withMember(name)))
					{
						resolve(e.items.keySet());
						added = true;
					}
				}
				if (added)
				{
					saveCache();
				}
				break;
			default:
				return;
		}
		// Anything accepted from a member means their client is running and in the party.
		roster.heard(name, now);
		onChange.run();
	}

	/** Client thread. */
	private void closeStorage()
	{
		Map<Integer, Integer> before = storageBefore;
		Map<Integer, Integer> after = storageLatest;
		storageOpen = false;
		storageBefore = null;
		storageLatest = null;
		if (before == null || after == null)
		{
			return;
		}
		StorageChange change = StorageDiff.diff(System.currentTimeMillis(), before, after).withMember(localName());
		resolveNow(after.keySet());
		resolveNow(before.keySet());

		// Minimums are checked on every own close, shared or not: the alert is for this player.
		checkLowStockNow(after);

		GroupOutbox.Switches s = switches();
		if (!s.storage)
		{
			return;
		}
		roster.putStorage(change);
		String payload = GroupOutbox.storage(gson, s, change);
		if (payload != null && partyService.isInParty())
		{
			partyService.send(new BuildGuardGroupMessage(payload));
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
		if (partyService.isInParty())
		{
			for (String payload : GroupOutbox.bank(gson, s, snapshot))
			{
				partyService.send(new BuildGuardGroupMessage(payload));
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
		partyService.send(new BuildGuardGroupMessage(payload));
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
		partyService.send(new BuildGuardGroupMessage(payload));
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
		partyService.send(new BuildGuardGroupMessage(payload));
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
		clientThread.invokeLater(() -> checkLowStockNow(storage));
	}

	/** Client thread. */
	private void checkLowStockNow(Map<Integer, Integer> storage)
	{
		if (lowStock.isEmpty())
		{
			return;
		}
		resolveNow(storage.keySet());
		for (LowStock.Alert alert : lowStock.check(storage, lookup))
		{
			notifier.notify("Build Guard: " + alert.message());
		}
	}

	/** Any thread: queues name and price lookups for ids not seen yet. */
	private void resolve(Collection<Integer> ids)
	{
		List<Integer> missing = new ArrayList<>();
		for (int id : ids)
		{
			if (!names.containsKey(id))
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
			if (names.containsKey(id))
			{
				continue;
			}
			ItemComposition item = itemManager.getItemComposition(id);
			String name = item == null ? null : item.getName();
			if (name != null && !name.isEmpty() && !"null".equalsIgnoreCase(name))
			{
				names.put(id, name);
				prices.put(id, (long) itemManager.getItemPrice(id));
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
		File file = cacheFile();
		if (!file.isFile())
		{
			return false;
		}
		try
		{
			String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
			if (!GroupCache.read(gson, json, roster))
			{
				log.debug("group cache unreadable, starting empty");
				return false;
			}
			return true;
		}
		catch (IOException e)
		{
			log.debug("could not read group cache", e);
			return false;
		}
	}

	private void saveCache()
	{
		if (!cacheLoaded)
		{
			// Whatever arrived meanwhile is in the roster and goes out with the first save after loading.
			return;
		}
		String json = GroupCache.write(gson, roster);
		executor.execute(() ->
		{
			File file = cacheFile();
			try
			{
				Files.createDirectories(file.getParentFile().toPath());
				File tmp = new File(file.getParentFile(), CACHE_FILE + ".tmp");
				Files.write(tmp.toPath(), json.getBytes(StandardCharsets.UTF_8));
				Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
			catch (IOException e)
			{
				log.debug("could not write group cache", e);
			}
		});
	}

	private static File cacheFile()
	{
		return new File(new File(RuneLite.RUNELITE_DIR, CACHE_DIR), CACHE_FILE);
	}

	/** Kept for the panel: is any group feature on, so the panel button should show. */
	boolean anyOn()
	{
		return switches().any() || !lowStock.isEmpty();
	}
}
