package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.awt.Image;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.clan.ClanID;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import okhttp3.OkHttpClient;

/**
 * Group events: notices this player's own (drops, pets, deaths, levels,
 * quests, logins, Barrows chests, storage visits), takes in members' from
 * {@link GroupService}, and feeds both outputs through one
 * {@link EventRouter}: the Discord webhook (own events only) and the
 * overlays (everyone's).
 *
 * <p>Nothing is detected for a kind no output wants and the party is not
 * sent: every switch is off by default, so a fresh install does nothing here.
 * The only network call of the plugin's own is the Discord webhook the player
 * sets, through RuneLite's injected {@link OkHttpClient}, never on the client
 * thread.
 */
@Slf4j
class GroupEventEngine
{
	/** A kill count line counts for a drop this soon after it (the game sends it on the kill tick). */
	static final long KC_WINDOW_MS = TimeUnit.SECONDS.toMillis(10);
	/** The chest count line arrives on the same tick as the chest; wait this many ticks for it. */
	static final int BARROWS_WAIT_TICKS = 2;
	/** RuneLite profile key for Barrows set progress. Not a setting: never shown, read only here. */
	static final String BARROWS_KEY = "barrowsPieces";
	/** How long, at most, a login waits for "last played" to load before "back after" is skipped. */
	static final int BACK_WAIT_TICKS = 100;
	/** Ticks after a login before variables are read: the login's burst of values has settled by then. */
	static final int VAR_WAIT_TICKS = 5;
	/** The digest and recap schedule is checked this often, not every tick. */
	static final int SCHEDULE_EVERY_TICKS = 100;
	// RuneLite profile keys for saved progress. Not settings: never shown, read only here.
	static final String DRY_KEY = "dryStreaks";
	static final String WEEK_KEY = "weekTally";
	static final String DIGEST_KEY = "digestLines";
	static final String DIGEST_LAST_KEY = "digestLastAt";
	static final String RECAP_LAST_KEY = "recapLastAt";
	static final String FARM_KEY = "farmTimers";
	/**
	 * Config key (not per profile: there is none before a login) for a logout still in its grace, so a
	 * client closed in it can post the offline on the next start. Not a setting: never shown.
	 */
	static final String PRESENCE_KEY = "presencePending";
	private static final EnumSet<GroupEvent.Type> POLLED = EnumSet.of(GroupEvent.Type.DIARY,
		GroupEvent.Type.WILDERNESS, GroupEvent.Type.HC_LIFE_LOST);

	@Inject
	private Client client;

	@Inject
	private GroupIronmanConfig config;

	@Inject
	private ConfigManager configManager;

	/** The plugin's own instance, handed over in {@link #startUp}: GroupService is not a singleton. */
	private GroupService group;

	@Inject
	private ItemManager itemManager;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private DrawManager drawManager;

	@Inject
	private OverlayManager overlayManager;

	private final ToastQueue toasts = new ToastQueue();
	private final ProgressTracker progress = new ProgressTracker(Skill.values().length - 1);
	/** Guards {@link #presence}: the client thread logs in and out, the executor polls. */
	private final Object presenceLock = new Object();
	private PresenceDebounce presence = new PresenceDebounce(ZoneId.systemDefault());
	private final EventRouter router = new EventRouter(this::toDiscord, e -> toasts.add(e, System.currentTimeMillis()));
	private volatile EventSettings settings = EventSettings.OFF;
	private final EventWatchers watchers = new EventWatchers(this::own, this::wanted);
	private volatile Digest digest = new Digest();
	private DryStreak dry = DryStreak.parse("");
	private Digest.Week week = Digest.Week.parse("");
	private FarmRuns farm = new FarmRuns(this::own, this::wanted);
	private int varWait = -1;
	private int scheduleTicks;
	private DiscordSender sender;
	private ScheduledFuture<?> pump;
	private EventToastOverlay toastOverlay;
	private GroupBoxOverlay boxOverlay;

	// Client thread only.
	private EventText.Count lastKc;
	private long lastKcAt;
	private int barrowsTicks = -1;
	private int clueTicks = -1;
	private boolean questPending;
	private boolean loginPending;
	private int backWait = -1;

	void startUp(GroupService group)
	{
		this.group = group;
		group.events(this);
		sender = new DiscordSender(DiscordSender.okHttp(okHttpClient), System::currentTimeMillis,
			() -> LocalTime.now().getHour());
		reloadConfig();
		restorePresence();
		toastOverlay = new EventToastOverlay(toasts, config, group.lookup);
		boxOverlay = new GroupBoxOverlay(toasts, config, group);
		overlayManager.add(toastOverlay);
		overlayManager.add(boxOverlay);
		pump = executor.scheduleWithFixedDelay(this::tick, 1, 1, TimeUnit.SECONDS);
		loginPending = client.getGameState() == GameState.LOGGED_IN;
	}

	void shutDown()
	{
		synchronized (presenceLock)
		{
			// The client closing (or the plugin stopping) while logged in: the one moment its logout time is known.
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				presence.logout(System.currentTimeMillis());
			}
			savePresence();
		}
		if (pump != null)
		{
			pump.cancel(false);
			pump = null;
		}
		if (group != null)
		{
			group.events(null);
		}
		overlayManager.remove(toastOverlay);
		overlayManager.remove(boxOverlay);
		toastOverlay = null;
		boxOverlay = null;
		if (sender != null)
		{
			sender.clear();
		}
		toasts.clear();
		progress.reset();
		settings = EventSettings.OFF;
	}

	void reloadConfig()
	{
		settings = EventSettings.from(config);
		if (sender != null)
		{
			sender.quietHours(config.discordQuietStart(), config.discordQuietEnd());
		}
		if (toastOverlay != null)
		{
			toastOverlay.reposition();
		}
	}

	/** Executor, once a second: the next Discord post, and a logout that has settled into "offline". */
	private void tick()
	{
		try
		{
			sender.pump();
			GroupEvent offline;
			synchronized (presenceLock)
			{
				// The name is the one the login gave: after a logout localPlayer() is "You".
				offline = presence.poll(System.currentTimeMillis(), config.discordOfflineGrace());
				savePresence();
			}
			if (offline != null)
			{
				// Not own(): that is client thread only. The router is fine here, as before.
				router.route(offline, true, settings, group.lookup);
			}
		}
		catch (RuntimeException e)
		{
			// A scheduled task that throws is never run again; keep the pump alive.
			log.debug("group event tick failed", e);
		}
	}

	// ---- Inputs from GroupService ----

	/** A member's event from the party, already filed under their name. */
	void member(GroupEvent event)
	{
		router.route(event, false, settings, group.lookup);
	}

	/** Client thread. A storage visit: this player's own, or a member's from the party. */
	void storage(StorageChange change, boolean own)
	{
		group.resolveItems(change.took().keySet());
		group.resolveItems(change.deposited().keySet());
		for (GroupEvent e : EventDetect.storage(change, group.lookup))
		{
			router.route(e, own, settings, group.lookup);
		}
		if (own && wanted(GroupEvent.Type.WEEKLY_RECAP))
		{
			if (!change.deposited().isEmpty())
			{
				week.storage(true);
			}
			if (!change.took().isEmpty())
			{
				week.storage(false);
			}
			saveProfile(WEEK_KEY, week.save());
		}
	}

	private void saveProfile(String key, String value)
	{
		configManager.setRSProfileConfiguration(GroupIronmanConfig.GROUP, key, value);
	}

	private String profile(String key)
	{
		return configManager.getRSProfileConfiguration(GroupIronmanConfig.GROUP, key);
	}

	/** Client thread. A storage minimum hit; own when this player's visit caused it. */
	void minimum(LowStock.Alert alert, boolean own)
	{
		String by = own ? group.localPlayer() : "Group storage";
		router.route(EventDetect.minimum(System.currentTimeMillis(), by, alert), own, settings, group.lookup);
	}

	// ---- This player's own events ----

	/** Client thread. Routes one of this player's events and, when shared, sends it to the party. */
	private void own(GroupEvent event)
	{
		if (event == null || !event.isValid())
		{
			// A drop with no untradeable in it, for one: nothing to show, nothing to send.
			return;
		}
		group.resolveItems(event.items.keySet());
		router.route(event, true, settings, group.lookup);
		if (settings.share && event.type.travels() && !event.type.logged())
		{
			group.shareGroupEvent(event, settings.share);
		}
	}

	/** Worth noticing at all: an output wants it, or members get it from us. */
	private boolean wanted(GroupEvent.Type type)
	{
		EventSettings s = settings;
		return s.wants(type) || s.share && type.travels() && !type.logged();
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		if (event.getNpc() == null)
		{
			return;
		}
		tallyDrop(event);
		if (!wanted(GroupEvent.Type.DROP) && !wanted(GroupEvent.Type.UNIQUE))
		{
			return;
		}
		Map<Integer, Integer> items = new TreeMap<>();
		for (ItemStack stack : event.getItems())
		{
			ItemCounts.add(items, stack.getId(), stack.getQuantity(), itemManager::canonicalize);
		}
		group.resolveItems(items.keySet());
		long now = System.currentTimeMillis();
		String npc = event.getNpc().getName();
		if (wanted(GroupEvent.Type.DROP))
		{
			own(GroupEvent.of(GroupEvent.Type.DROP, now, group.localPlayer()).text(npc).items(items)
				.number(kc(npc, now)).build());
		}
		if (wanted(GroupEvent.Type.UNIQUE))
		{
			Map<Integer, Integer> uniques = EventDetect.uniques(items, this::tradeable, group.lookup,
				config.eventUntradeableIgnore());
			own(GroupEvent.of(GroupEvent.Type.UNIQUE, now, group.localPlayer()).text(npc).items(uniques).build());
		}
	}

	/** The week's drop count, and whether this drop ends a dry streak. */
	private void tallyDrop(NpcLootReceived event)
	{
		if (wanted(GroupEvent.Type.WEEKLY_RECAP))
		{
			week.drop();
			saveProfile(WEEK_KEY, week.save());
		}
		if (wanted(GroupEvent.Type.DRY_STREAK))
		{
			Set<String> names = new HashSet<>();
			for (ItemStack stack : event.getItems())
			{
				names.add(itemManager.getItemComposition(stack.getId()).getName());
			}
			dry.drop(event.getNpc().getName(), names, System.currentTimeMillis());
			saveProfile(DRY_KEY, dry.save());
		}
	}

	private boolean tradeable(int id)
	{
		ItemComposition item = itemManager.getItemComposition(id);
		return item == null || item.isTradeable();
	}

	/** The kill count the game just sent for {@code name}, 0 when none came in time. */
	private long kc(String name, long now)
	{
		EventText.Count c = lastKc;
		return c != null && now - lastKcAt <= KC_WINDOW_MS && c.name.equalsIgnoreCase(name) ? c.count : 0;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		long now = System.currentTimeMillis();
		String message = event.getMessage();
		if (event.getType() == ChatMessageType.FRIENDSCHATNOTIFICATION)
		{
			// Chambers announces its loot here.
			watchChat(message, now);
			return;
		}
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}
		EventText.Count count = EventText.killCount(message);
		if (count != null)
		{
			lastKc = count;
			lastKcAt = now;
			counted(count, now);
			return;
		}
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}
		watchChat(message, now);
		if (EventText.isPet(message) && wanted(GroupEvent.Type.PET))
		{
			own(GroupEvent.of(GroupEvent.Type.PET, now, group.localPlayer()).build());
			return;
		}
		String clog = ClogMessage.parse(message);
		if (clog != null && wanted(GroupEvent.Type.CLOG))
		{
			own(GroupEvent.clog(now, group.localPlayer(), clog));
		}
	}

	/** A kill or raid count line: the raid's name, a milestone, the week's count and the dry streak. */
	private void counted(EventText.Count count, long now)
	{
		String me = group.localPlayer();
		if (isRaid(count.name))
		{
			watchers.raidCount(count.name, count.count);
		}
		watchers.kill(count.name, count.count, settings.kcEvery, now, me);
		if (wanted(GroupEvent.Type.WEEKLY_RECAP))
		{
			week.kill();
			saveProfile(WEEK_KEY, week.save());
		}
		if (wanted(GroupEvent.Type.DRY_STREAK))
		{
			dry.kill(count.name, now, me);
			saveProfile(DRY_KEY, dry.save());
		}
	}

	private static boolean isRaid(String name)
	{
		return name.contains("Chambers of Xeric") || name.contains("Theatre of Blood") || name.contains("Tombs of Amascut");
	}

	private void watchChat(String message, long now)
	{
		watchers.chat(message, now, group.localPlayer(), groupMembers(), playersHere());
	}

	/** This group's in-game roster, from the client's own clan data. */
	private Set<String> groupMembers()
	{
		Set<String> names = new HashSet<>();
		ClanSettings clan = client.getClanSettings(ClanID.GROUP_IRONMAN);
		if (clan != null)
		{
			for (ClanMember member : clan.getMembers())
			{
				names.add(member.getName());
			}
		}
		return names;
	}

	private List<String> playersHere()
	{
		List<String> names = new ArrayList<>();
		for (Player player : client.getTopLevelWorldView().players())
		{
			if (player.getName() != null)
			{
				names.add(player.getName());
			}
		}
		return names;
	}

	private Map<Integer, Integer> carried()
	{
		Map<Integer, Integer> items = new TreeMap<>();
		addAll(items, client.getItemContainer(InventoryID.INV));
		addAll(items, client.getItemContainer(InventoryID.WORN));
		return items;
	}

	private void addAll(Map<Integer, Integer> items, ItemContainer container)
	{
		if (container != null)
		{
			for (Item item : container.getItems())
			{
				if (item.getId() >= 0 && item.getQuantity() > 0)
				{
					ItemCounts.add(items, item.getId(), item.getQuantity(), itemManager::canonicalize);
				}
			}
		}
	}

	private String whereIs(Player me)
	{
		WorldPoint at = me.getLocalLocation() == null ? null : WorldPoint.fromLocalInstance(client, me.getLocalLocation());
		return at == null ? null : group.regions().describe(at.getRegionID());
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		Player me = client.getLocalPlayer();
		if (me == null || event.getActor() != me)
		{
			return;
		}
		String where = whereIs(me);
		if (wanted(GroupEvent.Type.DEATH_VALUE) || wanted(GroupEvent.Type.GRAVESTONE_EXPIRING))
		{
			Map<Integer, Integer> items = carried();
			group.resolveItems(items.keySet());
			watchers.died(items, where == null ? "" : where, client.getWorld());
		}
		if (!wanted(GroupEvent.Type.DEATH))
		{
			return;
		}
		Actor killer = me.getInteracting();
		boolean hardcore = client.getVarbitValue(VarbitID.IRONMAN) == EventDetect.HARDCORE_GROUP_IRONMAN;
		own(GroupEvent.of(GroupEvent.Type.DEATH, System.currentTimeMillis(), group.localPlayer())
			.text(killer == null ? null : killer.getName())
			.location(where)
			.world(client.getWorld())
			.number(hardcore ? GroupEvent.LIFE_LOST : 0)
			.build());
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() == Skill.OVERALL)
		{
			return;
		}
		List<GroupEvent> out = progress.skill(event.getSkill().getName(), event.getLevel(), event.getXp(),
			System.currentTimeMillis(), group.localPlayer());
		for (GroupEvent e : out)
		{
			if (wanted(e.type))
			{
				own(e);
			}
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarpId() == VarPlayerID.QP)
		{
			GroupEvent e = progress.questPoints(client.getVarpValue(VarPlayerID.QP), System.currentTimeMillis(),
				group.localPlayer());
			if (e != null && wanted(e.type))
			{
				own(e);
			}
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.QUESTSCROLL)
		{
			// The title is filled in by a script after the load; read it next tick.
			questPending = true;
		}
		else if (event.getGroupId() == InterfaceID.BARROWS_REWARD)
		{
			barrowsTicks = BARROWS_WAIT_TICKS;
		}
		else if (event.getGroupId() == InterfaceID.TRAIL_REWARDSCREEN)
		{
			clueTicks = BARROWS_WAIT_TICKS;
		}
	}

	private void clueReward(long now)
	{
		ItemContainer chest = client.getItemContainer(InventoryID.TRAIL_REWARDINV);
		if (chest == null || !wanted(GroupEvent.Type.CLUE))
		{
			return;
		}
		Map<Integer, Integer> items = new TreeMap<>();
		addAll(items, chest);
		group.resolveItems(items.keySet());
		watchers.clueReward(items, EventText.value(items, group.lookup), now, group.localPlayer());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGGED_IN)
		{
			loginPending = true;
		}
		else if (state == GameState.LOGIN_SCREEN)
		{
			// A world hop (HOPPING) is never a logout: it posts nothing, whatever the grace.
			synchronized (presenceLock)
			{
				presence.logout(System.currentTimeMillis());
				savePresence();
			}
			// Another account may log in next: start every baseline over.
			progress.reset();
			watchers.reset();
			backWait = -1;
			varWait = -1;
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		long now = System.currentTimeMillis();
		Player me = client.getLocalPlayer();
		if (me == null || me.getName() == null)
		{
			return;
		}
		if (loginPending)
		{
			loginPending = false;
			login(now, me.getName());
		}
		checkBack(now);
		GroupEvent combat = progress.combat(me.getCombatLevel(), now, me.getName());
		if (combat != null && wanted(combat.type))
		{
			own(combat);
		}
		readInterfaces(now);
		dry.due(now).forEach(this::own);
		watch(now, me);
	}

	/** The quest, Barrows and clue interfaces, a tick or two after they loaded. */
	private void readInterfaces(long now)
	{
		if (questPending)
		{
			questPending = false;
			quest(now);
		}
		if (barrowsTicks >= 0 && barrowsTicks-- == 0)
		{
			barrows(now);
		}
		if (clueTicks >= 0 && clueTicks-- == 0)
		{
			clueReward(now);
		}
	}

	/** Each tick, once a login has settled: variables, death, the gravestone and the schedule. */
	private void watch(long now, Player me)
	{
		if (varWait > 0)
		{
			varWait--;
			return;
		}
		if (varWait < 0)
		{
			return;
		}
		String name = me.getName();
		if (POLLED.stream().anyMatch(this::wanted))
		{
			watchers.poll(id -> id == EventWatchers.SKULL ? (me.getSkullIcon() != -1 ? 1 : 0) : client.getVarbitValue(id),
				now, name);
		}
		if (wanted(GroupEvent.Type.DEATH_VALUE))
		{
			watchers.deathTick(carried(), group.lookup, now, name);
		}
		if (wanted(GroupEvent.Type.GRAVESTONE_EXPIRING))
		{
			watchers.graveTick(client.getVarbitValue(VarbitID.GRAVESTONE_VISIBLE), settings.graveWarnMinutes, now, name);
		}
		farmTick(now, me);
		if (++scheduleTicks >= SCHEDULE_EVERY_TICKS)
		{
			scheduleTicks = 0;
			schedule(now, name);
		}
	}

	/** Reads the patches and bird houses where the player is standing, when either kind is switched on. */
	private void farmTick(long now, Player me)
	{
		if (wanted(GroupEvent.Type.FARMING) || wanted(GroupEvent.Type.BIRDHOUSE))
		{
			WorldPoint at = me.getWorldLocation();
			farm.observe(at.getRegionID(), at.getX(), at.getY(), at.getPlane(), client::getVarbitValue,
				client::getVarpValue, now / 1000L);
			saveFarm();
		}
	}

	private void saveFarm()
	{
		if (farm.takeDirty())
		{
			saveProfile(FARM_KEY, farm.save());
		}
	}

	/** Sends the daily digest and the weekly recap when due. A first run records the time and sends nothing. */
	private void schedule(long now, String name)
	{
		farm.due(now / 1000L, name);
		saveFarm();
		ZoneId zone = ZoneId.systemDefault();
		int hour = settings.digestHour;
		if (!settings.digest.isEmpty())
		{
			long lastDigest = savedTime(DIGEST_LAST_KEY, now);
			if (Digest.dailyDue(lastDigest, now, hour, zone))
			{
				flushDigest();
				saveProfile(DIGEST_LAST_KEY, Long.toString(now));
			}
		}
		if (!wanted(GroupEvent.Type.WEEKLY_RECAP))
		{
			return;
		}
		week.xp(client.getOverallExperience());
		long lastRecap = savedTime(RECAP_LAST_KEY, now);
		if (Digest.weeklyDue(lastRecap, now, hour, zone))
		{
			own(week.recap(now, name));
			week.startOver();
			saveProfile(RECAP_LAST_KEY, Long.toString(now));
		}
		saveProfile(WEEK_KEY, week.save());
	}

	/** The saved time; when none is saved yet, saves {@code now} and returns 0, which only sets the baseline. */
	private long savedTime(String key, long now)
	{
		try
		{
			String saved = profile(key);
			if (saved != null)
			{
				return Long.parseLong(saved);
			}
		}
		catch (NumberFormatException e)
		{
			log.debug("saved schedule time unreadable: {}", key);
		}
		saveProfile(key, Long.toString(now));
		return 0;
	}

	private void flushDigest()
	{
		if (sender == null || !config.discordEnabled())
		{
			return;
		}
		Map<GroupEvent.Category, String> overrides = DiscordWebhook.overrides(config);
		for (GroupEvent.Category category : settings.digest)
		{
			String url = DiscordWebhook.route(category, overrides, config.discordWebhookUrl());
			// Draining first would lose held lines when the webhook has been cleared.
			if (url != null)
			{
				sender.post(url, DiscordWebhook.digest(gson, category, digest.drain(category)), null);
			}
		}
		saveProfile(DIGEST_KEY, digest.save());
	}

	private void login(long now, String name)
	{
		dry = DryStreak.parse(profile(DRY_KEY));
		week = Digest.Week.parse(profile(WEEK_KEY));
		digest = Digest.parse(profile(DIGEST_KEY));
		farm = FarmRuns.parse(profile(FARM_KEY), this::own, this::wanted);
		if (varWait < 0)
		{
			varWait = VAR_WAIT_TICKS;
		}
		List<GroupEvent> posts;
		synchronized (presenceLock)
		{
			posts = presence.login(now, client.getWorld(), name, config.discordOfflineGrace());
			savePresence();
		}
		for (GroupEvent e : posts)
		{
			if ("online".equals(e.text))
			{
				own(e);
			}
			else
			{
				// The last account's offline: routed like the timer's, never sent to this account's party.
				router.route(e, true, settings, group.lookup);
			}
		}
		if (backWait < 0)
		{
			backWait = BACK_WAIT_TICKS;
		}
	}

	/** Startup: a logout saved by the last client, still in its grace; a restart while logged in is not one. */
	private void restorePresence()
	{
		synchronized (presenceLock)
		{
			presence = PresenceDebounce.parse(configManager.getConfiguration(GroupIronmanConfig.GROUP, PRESENCE_KEY),
				ZoneId.systemDefault());
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				presence.resume();
			}
		}
	}

	/** Holding {@link #presenceLock}: writes the pending logout only when it changed. */
	private void savePresence()
	{
		if (!presence.takeDirty())
		{
			return;
		}
		String saved = presence.save();
		if (saved.isEmpty())
		{
			configManager.unsetConfiguration(GroupIronmanConfig.GROUP, PRESENCE_KEY);
		}
		else
		{
			configManager.setConfiguration(GroupIronmanConfig.GROUP, PRESENCE_KEY, saved);
		}
	}

	/** "Back after N days", once per login, as soon as "last played" has loaded from disk. */
	private void checkBack(long now)
	{
		if (backWait <= 0)
		{
			return;
		}
		long since = group.since();
		if (since <= 0)
		{
			backWait--;
			return;
		}
		backWait = 0;
		if (wanted(GroupEvent.Type.BACK))
		{
			own(PresenceDebounce.back(since, now, settings.backAfterDays, group.localPlayer()));
		}
	}

	private void quest(long now)
	{
		Widget title = client.getWidget(InterfaceID.Questscroll.QUEST_TITLE);
		if (title == null || !wanted(GroupEvent.Type.QUEST) && !wanted(GroupEvent.Type.QUEST_POINTS))
		{
			return;
		}
		int qp = client.getVarpValue(VarPlayerID.QP);
		if (wanted(GroupEvent.Type.QUEST))
		{
			own(GroupEvent.of(GroupEvent.Type.QUEST, now, group.localPlayer()).text(EventText.questName(title.getText()))
				.number(qp).build());
		}
		if (wanted(GroupEvent.Type.QUEST_POINTS) && everyQuestDone())
		{
			own(GroupEvent.of(GroupEvent.Type.QUEST_POINTS, now, group.localPlayer()).text("every quest done")
				.number(qp).build());
		}
	}

	/** Every quest RuneLite lists, miniquests included, is finished. Client thread (each runs a script). */
	private boolean everyQuestDone()
	{
		for (Quest q : Quest.values())
		{
			if (q.getState(client) != QuestState.FINISHED)
			{
				return false;
			}
		}
		return true;
	}

	private void barrows(long now)
	{
		ItemContainer chest = client.getItemContainer(InventoryID.TRAIL_REWARDINV);
		if (chest == null || !wanted(GroupEvent.Type.BARROWS))
		{
			return;
		}
		Map<Integer, Integer> items = new TreeMap<>();
		for (Item item : chest.getItems())
		{
			ItemCounts.add(items, item.getId(), item.getQuantity(), itemManager::canonicalize);
		}
		group.resolveItems(items.keySet());
		BarrowsProgress sets = BarrowsProgress.parse(
			configManager.getRSProfileConfiguration(GroupIronmanConfig.GROUP, BARROWS_KEY));
		String progressText = sets.add(items, group.lookup);
		configManager.setRSProfileConfiguration(GroupIronmanConfig.GROUP, BARROWS_KEY, sets.save());
		own(GroupEvent.of(GroupEvent.Type.BARROWS, now, group.localPlayer()).text("Barrows").items(items)
			.number(kc("Barrows", now)).detail(progressText.isEmpty() ? "" : progressText + " from the chest")
			.build());
	}

	// ---- Discord ----

	/** Router output: one of this player's events for Discord, when posting is on and a webhook is set. */
	private void toDiscord(GroupEvent event)
	{
		if (!config.discordEnabled() || sender == null)
		{
			return;
		}
		String url = DiscordWebhook.route(event.type.category, DiscordWebhook.overrides(config),
			config.discordWebhookUrl());
		if (url == null)
		{
			return;
		}
		if (settings.digest.contains(event.type.category) && event.type != GroupEvent.Type.WEEKLY_RECAP)
		{
			// A Discord line: "450K (450,001)", as the webhook's own posts read.
			digest.add(event.type.category, EventText.sentence(event, Amounts.BOTH.words(group.lookup)));
			saveProfile(DIGEST_KEY, digest.save());
			return;
		}
		DiscordStyle style = config.discordStyle();
		String mention = config.discordMention();
		// A logout settles at the login screen: nothing worth a picture.
		if (!config.discordScreenshot() || event.type == GroupEvent.Type.ONLINE)
		{
			sender.post(url, DiscordWebhook.payload(gson, event, group.lookup, style, mention, false), null);
			return;
		}
		String json = DiscordWebhook.payload(gson, event, group.lookup, style, mention, true);
		// The ScreenshotPlugin pattern: the next drawn frame, encoded off the client thread.
		drawManager.requestNextFrameListener(image -> executor.execute(() ->
			sender.post(url, json, png(image))));
	}

	private static byte[] png(Image image)
	{
		try
		{
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(ImageUtil.bufferedImageFromImage(image), "png", out);
			return out.toByteArray();
		}
		catch (IOException e)
		{
			log.debug("screenshot could not be encoded", e);
			return null;
		}
	}
}
