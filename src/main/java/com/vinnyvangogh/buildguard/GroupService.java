package com.vinnyvangogh.buildguard;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.Notifier;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.party.messages.UserSync;

/**
 * The Group Ironman extras: member banks, the group storage log, live stats,
 * "who has it?" and storage minimums.
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
	static final int CARRIED_INTERVAL_TICKS = 5;

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
	private String lastStatsSent;
	private String lastCarriedSent;

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
		reloadConfig();
		executor.execute(this::loadCache);
	}

	void shutDown()
	{
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
			config.groupShareStats(), config.groupShareCarried());
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
		clientThread.invokeLater(() ->
		{
			lastStatsSent = null;
			lastCarriedSent = null;
			statsDirty = true;
			carriedDirty = true;
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
		if (client.getGameState() != GameState.LOGGED_IN || !partyService.isInParty())
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
			int region = region();
			if (run != lastRun || spec != lastSpec || world != lastWorld || region != lastRegion)
			{
				lastRun = run;
				lastSpec = spec;
				lastWorld = world;
				lastRegion = region;
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
	}

	/** Someone joined and asked for a resend. */
	@Subscribe
	public void onUserSync(UserSync event)
	{
		clientThread.invokeLater(() ->
		{
			lastStatsSent = null;
			lastCarriedSent = null;
			statsDirty = true;
			carriedDirty = true;
			if (lastBank != null && partyService.isInParty())
			{
				for (String payload : GroupOutbox.bank(gson, switches(), lastBank))
				{
					partyService.send(new BuildGuardGroupMessage(payload));
				}
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
		statsDirty = true;
		carriedDirty = true;
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
			default:
				return;
		}
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
		MemberStats stats = new MemberStats(skills, lastRun, lastSpec, lastWorld, lastRegion);
		String payload = GroupOutbox.stats(gson, s, stats);
		if (payload == null || Objects.equals(payload, lastStatsSent))
		{
			return;
		}
		partyService.send(new BuildGuardGroupMessage(payload));
		lastStatsSent = payload;
		ticksSinceStats = 0;
		roster.putStats(localName(), stats);
		onChange.run();
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

	/** Client thread. The real-world region, also inside instances; 0 when not known. */
	private int region()
	{
		Player player = client.getLocalPlayer();
		if (player == null || player.getLocalLocation() == null)
		{
			return 0;
		}
		WorldPoint point = WorldPoint.fromLocalInstance(client, player.getLocalLocation());
		return point == null ? 0 : point.getRegionID();
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
		File file = cacheFile();
		if (!file.isFile())
		{
			return;
		}
		try
		{
			String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
			if (!GroupCache.read(gson, json, roster))
			{
				log.debug("group cache unreadable, starting empty");
				return;
			}
		}
		catch (IOException e)
		{
			log.debug("could not read group cache", e);
			return;
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

	private void saveCache()
	{
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
