package com.vinnyvangogh.groupironman;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
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
import net.runelite.api.NPC;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.gameval.NpcID;
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
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.input.KeyManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Group events: notices this player's own (drops, pets, deaths, levels,
 * quests, logins, Barrows chests, storage visits), takes in members' from
 * {@link GroupService}, and feeds both outputs through one
 * {@link EventRouter}: Discord (own events only, handed to the Discord Relay
 * plugin, {@link DiscordRelay}) and the overlays (everyone's).
 *
 * <p>Nothing is detected for a kind no output wants and the party is not
 * sent: every switch is off by default, so a fresh install does nothing here.
 * The plugin makes no network call of its own: Discord Relay posts.
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
	/** The digest and recap schedule is checked this often, not every tick. */
	static final int SCHEDULE_EVERY_TICKS = 100;
	// RuneLite profile keys for saved progress. Not settings: never shown, read only here.
	static final String DRY_KEY = "dryStreaks";
	/** Profile key: the game broadcasts posted in the last day ({@link GroupBroadcasts.Seen}). Not a setting. */
	static final String BROADCASTS_KEY = "broadcastsPosted";
	static final String WEEK_KEY = "weekTally";
	static final String RECAP_LAST_KEY = "recapLastAt";
	static final String FARM_KEY = "farmTimers";
	/**
	 * Config key (not per profile: there is none before a login) for a logout still in its grace, so a
	 * client closed in it can post the offline on the next start. Not a setting: never shown.
	 */
	static final String PRESENCE_KEY = "presencePending";
	/**
	 * Before the split, GIM's own Discord state: the live board's message ids (this config key, then
	 * {@link #BOARD_FILE}), and the open level-streak and grouped posts. Read only, once per run, and handed to
	 * Discord Relay ({@link DiscordRelay#legacy}) so it keeps editing the same messages. Never written now.
	 */
	static final String BOARD_KEY = "statusBoardMessage";
	static final String BOARD_FILE = "status-board.txt";
	static final String LEVEL_FILE = "level-streaks.txt";
	static final String GROUPED_FILE = "grouped-posts.txt";
	/** Config key: set once 'Online status' has been chosen for this install, so an upgrade keeps its old choice. */
	static final String ONLINE_STATUS_CHOSEN_KEY = "onlineStatusChosen";
	/** Profile key: this player's wave run ({@link WaveTracker#save}), so a logout mid-run resumes. Not a setting. */
	static final String WAVE_RUN_KEY = "activityRun";
	/** Profile key: this player's fastest time per wave ({@link WaveTracker#saveBests}). Not a setting. */
	static final String WAVE_BESTS_KEY = "activityBests";
	/** An activity is sent again this often while it lasts, so members can tell a crashed client's run apart. */
	static final long ACTIVITY_KEEPALIVE_MS = 60_000;
	/** "Everyone is online" fires at most this often, so a member's reconnects do not repeat it. */
	static final long EVERYONE_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(10);
	/** Between one player's announcements. */
	static final long ANNOUNCE_COOLDOWN_MS = TimeUnit.SECONDS.toMillis(5);
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
	private SkillIconManager skillIconManager;

	@Inject
	private EventBus eventBus;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private KeyManager keyManager;

	private final ToastQueue toasts = new ToastQueue();
	/** Every Discord post goes out through the Discord Relay plugin; set in {@link #startUp}. */
	private DiscordRelay relay = new DiscordRelay(m ->
	{
	});
	/** The pre-split Discord state was handed to Discord Relay this run ({@link DiscordRelay#legacy}). */
	private volatile boolean legacySent;
	/** A death frame is held in Discord Relay for the gravestone warning ({@link DiscordRelay#GRAVE}). Client thread. */
	private boolean graveHeld;
	private final ProgressTracker progress = new ProgressTracker(Skill.values().length - 1);
	/** Guards {@link #presence}: the client thread logs in and out, the executor polls. */
	private final Object presenceLock = new Object();
	private PresenceDebounce presence = new PresenceDebounce(ZoneId.systemDefault());
	/** Members without the plugin, as the Group channel sees them; guarded by {@link #presenceLock} too. */
	private final ChannelPosts channelPosts = new ChannelPosts(ZoneId.systemDefault());
	private final EventRouter router = new EventRouter(this::toDiscord, this::toast, this::toDigest, this::toLog);
	/** One banner per event, however many sources report it ({@link AlertDedup}). */
	private final AlertDedup alertDedup = new AlertDedup();
	/** Timer: the change to "everyone in the group is online" ({@link EveryoneOnline}). */
	private final EveryoneOnline everyone = new EveryoneOnline();
	private long everyoneAt;
	/** Client thread: when this player last announced, so one cannot flood the group. */
	private long announcedAt;
	/** Clears every banner on screen: the dismiss key, and the banner's right-click Dismiss. */
	private final HotkeyListener dismissKey = new HotkeyListener(() -> config.overlayDismissKey())
	{
		@Override
		public void hotkeyPressed()
		{
			toasts.dismissAll();
		}
	};
	private volatile EventSettings settings = EventSettings.OFF;
	/**
	 * {@link #settings} without this member's own opt-outs: what posts for members without the plugin, who cannot
	 * opt out in-game (Board 2026-11-14). The poster's "don't post my events" is about their own.
	 */
	private volatile EventSettings broadcastSettings = EventSettings.OFF;
	/** Guards {@link #seen}: the client thread reads broadcasts, the party thread hears claims. */
	private final Object seenLock = new Object();
	/** Game broadcasts posted by this client or claimed by another, per RS profile; loaded at login. */
	private GroupBroadcasts.Seen seen = new GroupBroadcasts.Seen();
	/** The shared settings the event settings were built from: quiet hours, grace and Easter eggs read these. */
	private volatile GroupSettingsSchema.Resolved values;
	private final EventWatchers watchers = new EventWatchers(this::own, this::wanted);
	/** A login's first minute of this player's achievements, held; a burst of them is never posted. */
	private final LoginBurst ownBurst = new LoginBurst(LoginBurst.ACHIEVEMENTS::contains);
	/** The same for the Discord posts of members without the plugin, every kind. */
	private final LoginBurst broadcastBurst = new LoginBurst(t -> true);
	/** Whose values the baselines hold, and when they count. Client thread. */
	private final LoginGate gate = new LoginGate(progress, watchers, ownBurst, broadcastBurst);
	/** Client thread: this player's Fight Caves, Inferno or Colosseum run (docs/plans/activities.md Slice A). */
	private final WaveTracker waves = new WaveTracker(this::own, this::teamMode);
	/** Client thread: when this player's activity last went to the roster and the party. */
	private long activitySentAt;
	private DryStreak dry = DryStreak.parse("");
	private Digest.Week week = Digest.Week.parse("");
	private FarmRuns farm = new FarmRuns(this::own, this::wanted);
	private int scheduleTicks;
	/** This player for the live board: set on the client thread, read by the timer. Null before a login. */
	private volatile BoardLines.Self boardSelf;
	/** This run's first login, 0 before one: the board waits {@link BoardLines#SETTLE_MS} after it. */
	private volatile long boardLoginAt;
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
		relay = new DiscordRelay(eventBus::post);
		legacySent = false;
		chooseOnlineStatus();
		reloadConfig();
		restorePresence();
		toastOverlay = new EventToastOverlay(toasts, config, group.lookup, this::eggSettings, client);
		boxOverlay = new GroupBoxOverlay(toasts, config, group, this::eggSettings,
			new PluginIcons(skillIconManager, itemManager));
		overlayManager.add(toastOverlay);
		overlayManager.add(boxOverlay);
		keyManager.registerKeyListener(dismissKey);
		pump = executor.scheduleWithFixedDelay(this::tick, 1, 1, TimeUnit.SECONDS);
		loginPending = client.getGameState() == GameState.LOGGED_IN;
	}

	/**
	 * Once this engine is on the event bus: asks Discord Relay to answer ({@code ready}, heard by
	 * {@link #onPluginMessage}). Until it does, nothing is posted and the settings show the install hint.
	 */
	void greetRelay()
	{
		relay.send(DiscordRelay.HELLO, DiscordRelay.hello(UpdateNotes.VERSION));
	}

	/** True once Discord Relay has answered: GIM's Discord posts have somewhere to go. */
	boolean relayInstalled()
	{
		return relay.installed();
	}

	/** A message on RuneLite's bus: Discord Relay's {@code ready} or {@code bye}; anything else is ignored. */
	@Subscribe
	public void onPluginMessage(PluginMessage message)
	{
		Webhooks own = relay.receive(message);
		if (own == null)
		{
			return;
		}
		GroupService g = group;
		if (g != null)
		{
			g.relayWebhooks(own);
		}
		if (relay.installed() && !legacySent)
		{
			legacySent = true;
			relay.send(DiscordRelay.LEGACY, DiscordRelay.legacy(legacyBoards(), readData(LEVEL_FILE),
				readData(GROUPED_FILE)));
		}
		if (relay.installed())
		{
			// What Relay holds was forgotten with the ready: the settings and board go again on the next tick.
			reloadConfig();
		}
	}

	/** The live board's saved ids from before the split: the plugin's file, else the config line before that. */
	private String legacyBoards()
	{
		String text = readData(BOARD_FILE);
		return text != null ? text : getConfig(BOARD_KEY);
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
		keyManager.unregisterKeyListener(dismissKey);
		overlayManager.remove(toastOverlay);
		overlayManager.remove(boxOverlay);
		alertDedup.clear();
		everyone.reset();
		toastOverlay = null;
		boxOverlay = null;
		boardLoginAt = 0;
		graveHeld = false;
		// Discord Relay stops keeping the board for GIM and drops what it held for it.
		relay.send(DiscordRelay.BYE, DiscordRelay.bye());
		toasts.clear();
		// Held login events are dropped with the rest of the unsent posts.
		gate.startOver();
		ownBurst.end();
		broadcastBurst.end();
		settings = EventSettings.OFF;
		broadcastSettings = EventSettings.OFF;
	}

	/** A config change, or new group settings (they are kept in the config, so both arrive here). */
	void reloadConfig()
	{
		GroupSettingsSchema.Resolved v = group == null ? new GroupSettingsSchema.Resolved(config, null)
			: group.groupValues();
		values = v;
		OnlineStatus online = BoardLines.effective(config.discordOnlineStatus(), v, webhooks());
		settings = EventSettings.from(config, v, MemberPrefs.of(config), online);
		broadcastSettings = EventSettings.from(config, v, MemberPrefs.NONE, online);
		if (toastOverlay != null)
		{
			toastOverlay.reposition();
		}
		sendSettings();
	}

	/** The group's Discord settings, to Discord Relay when they changed (or it has just answered). */
	private void sendSettings()
	{
		if (!relay.installed())
		{
			return;
		}
		GroupSettingsSchema.Resolved v = resolved();
		GroupService g = group;
		relay.sendIfChanged(DiscordRelay.SETTINGS, DiscordRelay.settings(settings, v.intValue("discordQuietStart"),
			v.intValue("discordQuietEnd"), offlineGrace(), eggSettings(),
			BoardLines.effective(config.discordOnlineStatus(), v, webhooks()),
			g == null ? null : g.sharedWebhooks()));
	}

	/** The shared values now; this member's own config until the first {@link #reloadConfig}. */
	private GroupSettingsSchema.Resolved resolved()
	{
		GroupSettingsSchema.Resolved v = values;
		return v != null ? v : new GroupSettingsSchema.Resolved(config, null);
	}

	/** The group's offline grace when it has settings, else this member's own. */
	private int offlineGrace()
	{
		return resolved().intValue("discordOfflineGrace");
	}

	/** Executor, once a second: a logout that has settled into "offline", the board, and the shared settings. */
	private void tick()
	{
		try
		{
			GroupEvent offline;
			List<GroupEvent> others;
			synchronized (presenceLock)
			{
				// The name is the one the login gave: after a logout localPlayer() is "You".
				offline = presence.poll(System.currentTimeMillis(), offlineGrace());
				savePresence();
				others = channelPosts.poll(System.currentTimeMillis(), offlineGrace());
			}
			if (offline != null)
			{
				// Not own(): that is client thread only. The router is fine here, as before.
				router.route(offline, true, settings, group.lookup);
			}
			routeChannelPosts(others);
			everyoneTick(System.currentTimeMillis());
			boardTick(System.currentTimeMillis());
			// The leader's shared webhooks can change with no config change: sent only when they differ.
			sendSettings();
		}
		catch (RuntimeException e)
		{
			// A scheduled task that throws is never run again; keep the pump alive.
			log.debug("group event tick failed", e);
		}
	}

	/** Timer: every member online at once is one event, worked out here from the roster and presence. */
	private void everyoneTick(long now)
	{
		Set<String> names = group.members().names();
		BoardLines.Self self = boardSelf;
		if (!group.member() || names.isEmpty() || self == null || !self.online)
		{
			// Logged out or not in the group: no baseline, so the next login starts from what it sees.
			everyone.reset();
			return;
		}
		int count = everyone.update(names, onlineMembers(names, self.name, now));
		if (count > 0 && now - everyoneAt >= EVERYONE_COOLDOWN_MS)
		{
			everyoneAt = now;
			String group = this.group.members().group();
			// The leader's client alone posts it to Discord; every client shows it, so each is told once.
			router.route(GroupEvent.of(GroupEvent.Type.EVERYONE_ONLINE, now, group.isEmpty() ? "Group" : group)
				.number(count).build(), this.group.isLeader(), settings, this.group.lookup);
		}
	}

	/** The roster members online now: the game's Group channel where it speaks for them, else who was heard lately. */
	private Set<String> onlineMembers(Set<String> names, String self, long now)
	{
		GamePresence game = group.presence();
		Map<String, Long> heard = new HashMap<>();
		group.roster().lastHeard().forEach((n, at) -> heard.put(GroupMembers.key(n), at));
		Set<String> online = new HashSet<>();
		for (String name : names)
		{
			String key = GroupMembers.key(name);
			GamePresence.State state = game.state(name);
			boolean heardLately = Presence.online(heard.getOrDefault(key, 0L), now);
			if (key.equals(GroupMembers.key(self)) || state == GamePresence.State.ONLINE
				|| state == GamePresence.State.UNKNOWN && heardLately)
			{
				online.add(name);
			}
		}
		return online;
	}

	/** One banner for a repeat, and none in the banner quiet hours (the Log still records the event). */
	private void toast(GroupEvent event)
	{
		long now = System.currentTimeMillis();
		if (alertDedup.fresh(event, now)
			&& !ToastQueue.quiet(LocalTime.now().getHour(), config.overlayQuietStart(), config.overlayQuietEnd()))
		{
			toasts.add(event, now);
		}
	}

	/** Right-click Dismiss on a banner. */
	@Subscribe
	public void onOverlayMenuClicked(OverlayMenuClicked click)
	{
		if (click.getOverlay() == toastOverlay && EventToastOverlay.DISMISS.equals(click.getEntry().getOption()))
		{
			toasts.dismissAll();
		}
	}

	/**
	 * Client thread. This player's {@code ::announce} note to the group: shown here, and sent to the party when
	 * "Share group events" is on, like every other event. Returns a line to tell the player, or null.
	 */
	String announce(String text)
	{
		String note = text == null ? "" : text.trim();
		long now = System.currentTimeMillis();
		if (note.isEmpty())
		{
			return "Type a note, e.g. ::announce Raid at 8pm";
		}
		if (announcedAt > 0 && now - announcedAt < ANNOUNCE_COOLDOWN_MS)
		{
			return "Wait a few seconds between announcements.";
		}
		announcedAt = now;
		own(GroupEvent.of(GroupEvent.Type.ANNOUNCEMENT, now, group.localPlayer()).text(note).build());
		if (!settings.share)
		{
			return "Shown to you only. Turn on 'Share group events' to send announcements to the group.";
		}
		return note.length() > GroupEvent.MAX_TEXT ? "Cut to " + GroupEvent.MAX_TEXT + " characters." : null;
	}

	/**
	 * Timer: the live board, when 'Online status' is the board, Discord Relay posts, and the Online status webhook
	 * (or Main) is set. Members are read from the roster the party fills; Discord Relay posts and edits the
	 * message. On the leader's shared webhook only the leader's client keeps it ({@link BoardLines#keeps}).
	 */
	private void boardTick(long now)
	{
		boolean keep = relay.posting() && BoardLines.keeps(BoardLines.effective(config.discordOnlineStatus(),
			resolved(), webhooks()), webhooks(), group.isLeader());
		// Tells the party this client keeps the board, so the others post no separate online/offline messages.
		group.keepingBoard(keep);
		if (!relay.installed())
		{
			return;
		}
		// Board 2026-11-14: nothing goes out until the leader's role and the shared webhooks are in.
		boolean settled = BoardLines.settled(boardLoginAt, now, group.cacheLoaded());
		relay.sendIfChanged(DiscordRelay.BOARD, DiscordRelay.board(keep, settled, keep ? boardLines(now) : ""));
	}

	/** Timer: the board's lines now. Logged out too: the last plugin user's client marks the board stale. */
	private String boardLines(long now)
	{
		BoardLines.Self self = boardSelf;
		GroupGovernance g = group.governance();
		Map<String, String> activities = BoardLines.activitySuffixes(group.roster().activities(now),
			g == null ? Collections.<String, MemberPrefs>emptyMap() : g.prefs(), self == null ? "" : self.name,
			MemberPrefs.of(config), config.groupShareEvents());
		return BoardLines.lines(group.members(), group.roster().lastHeard(), group.roster().lastSeen(),
			group.roster().allStats(), self, group.presence(), activities, now);
	}

	private String getConfig(String key)
	{
		return configManager.getConfiguration(GroupIronmanConfig.GROUP, key);
	}

	/** Sets {@code key} in this plugin's config group; null unsets it. */
	private void setConfig(String key, String value)
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

	/** A value ConfigManager turns into its config string itself (a number, an enum). */
	private void setConfig(String key, Object value)
	{
		configManager.setConfiguration(GroupIronmanConfig.GROUP, key, value);
	}

	/** A plugin data file's text; null when it is missing or unreadable. */
	private String readData(String file)
	{
		try
		{
			return PluginFiles.read(group.dataFile(file));
		}
		catch (Exception e)
		{
			log.debug("could not read {}", file, e);
			return null;
		}
	}

	/**
	 * Once per install: an upgrade keeps what its old "Online and offline" switch did ({@link OnlineStatus#upgrading}).
	 * Both old switches are read as stored: their settings moved to Discord Relay ("discordOnline" was retired
	 * before that), and the stored values are never removed.
	 */
	private void chooseOnlineStatus()
	{
		if (getConfig(ONLINE_STATUS_CHOSEN_KEY) != null)
		{
			return;
		}
		OnlineStatus kept = OnlineStatus.upgrading("true".equals(getConfig("discordOnline")),
			"true".equals(getConfig("discordEnabled")));
		if (kept != null)
		{
			setConfig("discordOnlineStatus", kept);
		}
		setConfig(ONLINE_STATUS_CHOSEN_KEY, "1");
	}

	// ---- Inputs from GroupService ----

	/**
	 * Client thread: the game's Group channel changed. Logins and logouts of members without the plugin become
	 * Online posts ({@link ChannelPosts}); members with it post their own.
	 */
	void gamePresence(GamePresence game)
	{
		Predicate<String> plugin = pluginUser();
		List<GroupEvent> posts;
		List<GroupEvent> shows;
		Player me = client.getLocalPlayer();
		String self = me == null || me.getName() == null ? "" : GroupMembers.key(me.getName());
		long now = System.currentTimeMillis();
		synchronized (presenceLock)
		{
			shows = channelPosts.shows(game, self, now);
			posts = channelPosts.update(game, plugin, now, offlineGrace());
		}
		// The banner and Log do not wait on the grace; the debounced posts below are Discord's.
		for (GroupEvent e : shows)
		{
			router.show(e, settings, group.lookup);
		}
		routeChannelPosts(posts);
	}

	/**
	 * Client thread. Who posts their own events: this player, and every member whose client was ever heard from over
	 * the group sync (the board's "(no plugin)" rule). Everyone else is read from the game's Group channel.
	 */
	private Predicate<String> pluginUser()
	{
		Player me = client.getLocalPlayer();
		String self = me == null || me.getName() == null ? "" : GroupMembers.key(me.getName());
		Set<String> plugin = new HashSet<>();
		group.roster().lastHeard().keySet().forEach(n -> plugin.add(GroupMembers.key(n)));
		group.roster().allStats().keySet().forEach(n -> plugin.add(GroupMembers.key(n)));
		return n -> plugin.contains(GroupMembers.key(n)) || GroupMembers.key(n).equals(self);
	}

	/**
	 * Client thread. A line of the game's Group channel broadcast ({@link GroupBroadcasts}): a member without the
	 * plugin's level, quest, drop and so on. Every plugin user shows it; one posts it and tells the others.
	 */
	private void groupBroadcast(String message, long now)
	{
		GroupEvent event = GroupBroadcasts.parse(message, now, this::itemId);
		if (event == null)
		{
			return;
		}
		boolean posts = resolved().on(GroupSettingsSchema.NO_PLUGIN_EVENTS) && leadsChannelPosts(now);
		GroupBroadcasts.Action action;
		synchronized (seenLock)
		{
			action = GroupBroadcasts.decide(event, pluginUser(), posts, seen, now);
			if (action == GroupBroadcasts.Action.POST)
			{
				saveProfile(BROADCASTS_KEY, seen.save());
			}
		}
		if (action == GroupBroadcasts.Action.SKIP)
		{
			return;
		}
		group.resolveItems(event.items.keySet());
		router.route(event, false, settings, group.lookup);
		if (action == GroupBroadcasts.Action.POST)
		{
			// Claimed at once either way: a login's burst held here is never posted by another client either.
			group.sharePosted(GroupBroadcasts.key(event));
			if (!broadcastBurst.hold(event, now))
			{
				router.post(event, broadcastSettings, group.lookup);
			}
		}
	}

	/** Any thread. Game broadcasts another plugin user's client posted: never posted here as well. */
	void claimed(List<String> keys)
	{
		long now = System.currentTimeMillis();
		synchronized (seenLock)
		{
			keys.forEach(k -> seen.add(k, now));
		}
	}

	/** A broadcast drop's item by its exact name (tradeables: the only drops worth a broadcast), or null. */
	private Integer itemId(String name)
	{
		for (var item : itemManager.search(name))
		{
			if (item.getName().equalsIgnoreCase(name))
			{
				return itemManager.canonicalize(item.getId());
			}
		}
		return null;
	}

	/**
	 * Any thread: posts for members without the plugin. Every client shows them in its Log and overlay; only the
	 * one plugin user that {@link ChannelPosts#leads} sends them to Discord, so each is posted once.
	 */
	private void routeChannelPosts(List<GroupEvent> posts)
	{
		if (posts.isEmpty())
		{
			return;
		}
		boolean leads = leadsChannelPosts(System.currentTimeMillis());
		for (GroupEvent e : posts)
		{
			// Shown already, the moment the game's channel changed ({@link ChannelPosts#shows}): this is Discord's.
			if (leads)
			{
				router.post(e, settings, group.lookup);
			}
		}
	}

	/** The plugin users the others hear and the game does not say left, without this player; then who leads. */
	private boolean leadsChannelPosts(long now)
	{
		BoardLines.Self self = boardSelf;
		String name = self == null ? "" : self.name;
		GamePresence game = group.presence();
		List<String> others = new ArrayList<>();
		group.roster().lastHeard().forEach((member, at) ->
		{
			if (Presence.online(at, now) && game.state(member) != GamePresence.State.OFFLINE
				&& !GroupMembers.key(member).equals(GroupMembers.key(name)))
			{
				others.add(member);
			}
		});
		return ChannelPosts.leads(name, group.syncing(), others, group.leaderName());
	}

	/** A member's event from the party, already filed under their name. */
	void member(GroupEvent event)
	{
		if (event.type == GroupEvent.Type.ONLINE
			&& group.presence().state(event.member) != GamePresence.State.UNKNOWN)
		{
			// Their login or logout was shown when the game's Group channel changed; their client's, held by the
			// offline grace, would be a second, late line.
			return;
		}
		router.route(event, false, settings, group.lookup);
	}

	/** A member's earlier event, uploaded when they joined the sync: the Log only, no toast and no post. */
	void backfill(GroupEvent event)
	{
		router.backfill(event, settings, group.lookup);
	}

	/** Client thread. A storage visit: this player's own, or a member's from the party. */
	void storage(StorageChange change, boolean own)
	{
		group.resolveItems(change.took().keySet());
		group.resolveItems(change.deposited().keySet());
		// A member's visit that arrives late (a reconnect, a queued resend) is history: the Log only (RUNELITE-510).
		boolean late = !own && EventBackfill.late(change.at(), System.currentTimeMillis());
		for (GroupEvent e : EventDetect.storage(change, group.lookup))
		{
			if (late)
			{
				router.backfill(e, settings, group.lookup);
				continue;
			}
			router.route(e, own, settings, group.lookup);
			if (own && e.type == GroupEvent.Type.STORAGE_SUMMARY && relay.installed())
			{
				// A big move's post lists the whole visit, whether or not the summary is switched on.
				relay.send(DiscordRelay.EVENT, DiscordRelay.event(e, group.lookup, false, false, true));
			}
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

	/** Client thread. This player's Shopping tab request made, claimed or done ({@link RequestBoard}). */
	void request(GroupEvent event)
	{
		own(event);
	}

	/**
	 * Client thread. Routes one of this player's events and, when shared, sends it to the party. An achievement in a
	 * login's first minute waits in {@link #ownBurst} first.
	 */
	private void own(GroupEvent event)
	{
		if (event == null || !event.isValid())
		{
			// A drop with no untradeable in it, for one: nothing to show, nothing to send.
			return;
		}
		if (!ownBurst.hold(event, System.currentTimeMillis()))
		{
			ownNow(event);
		}
	}

	private void ownNow(GroupEvent event)
	{
		group.resolveItems(event.items.keySet());
		router.route(event, true, settings, group.lookup);
		if (settings.share && event.type.travels() && !event.type.logged())
		{
			group.shareGroupEvent(event, settings.share);
		}
	}

	/** Worth noticing at all: an output (the Log takes every kind) wants it, or members get it from us. */
	private boolean wanted(GroupEvent.Type type)
	{
		EventSettings s = settings;
		return s.detects(type) || s.share && type.travels() && !type.logged();
	}

	/**
	 * A Fight Caves wave-start monster in the player's own cave: its spawn point tells the run's rotation. Only the
	 * level 22 and 45 monsters that open waves 1 to 4; the 22s a Tz-Kek splits into are not spawn points.
	 */
	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		NPC npc = event.getNpc();
		int id = npc.getId();
		if (id != NpcID.TZHAAR_FIGHTCAVE_SWARM_1A && id != NpcID.TZHAAR_FIGHTCAVE_SWARM_1B
			&& id != NpcID.TZHAAR_FIGHTCAVE_SWARM_2A && id != NpcID.TZHAAR_FIGHTCAVE_SWARM_2B)
		{
			return;
		}
		WorldPoint at = npc.getLocalLocation() == null ? null : WorldPoint.fromLocalInstance(client, npc.getLocalLocation());
		if (at != null)
		{
			waves.spawn(FightCaveRotation.point(at.getX() & 63, at.getY() & 63), System.currentTimeMillis());
		}
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		if (event.getNpc() == null || !group.member())
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
		if (!group.member())
		{
			// Board 2026-11-15: an account not in the group posts nothing, its own or its group's broadcasts.
			return;
		}
		long now = System.currentTimeMillis();
		String message = event.getMessage();
		if (event.getType() == ChatMessageType.FRIENDSCHATNOTIFICATION)
		{
			// Chambers announces its loot here.
			watchChat(message, now);
			return;
		}
		if (event.getType() == ChatMessageType.CLAN_GIM_MESSAGE)
		{
			groupBroadcast(message, now);
			return;
		}
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}
		if (event.getType() == ChatMessageType.GAMEMESSAGE)
		{
			// Waves, a Colosseum wave cleared, a defeat, and the kill count and duration that complete a run.
			waves.chat(message, now, group.localPlayer());
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
		gameMessage(message, now);
	}

	/** A game message that is not a count: a pet or a collection log slot. */
	private void gameMessage(String message, long now)
	{
		// A level-up line is Discord Relay's cue for the level-up's picture; it reads the chat itself.
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
		int region = templateRegion(me);
		return region < 0 ? null : group.regions().describe(region);
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		Player me = client.getLocalPlayer();
		if (me == null || event.getActor() != me || !group.member())
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
		if (postsPlainDeath())
		{
			deathPost(me, where);
		}
		// The death's frame, held by Discord Relay for the gravestone warning (its 'Grave picture' decides).
		if (wantsGraveFrame())
		{
			graveHeld = true;
			relay.send(DiscordRelay.CAPTURE, DiscordRelay.capture(DiscordRelay.GRAVE));
		}
	}

	/**
	 * This player's death, told to the wave run first (which posts its activity death). False when the plain death
	 * is not wanted, or when it was a death in a run whose activity death posts to Discord: one death, one post
	 * (docs/plans/activities.md §0, "Activity death vs plain DEATH").
	 */
	private boolean postsPlainDeath()
	{
		boolean inRun = waves.died(System.currentTimeMillis(), group.localPlayer());
		return wanted(GroupEvent.Type.DEATH) && !(inRun && settings.discord.contains(GroupEvent.Type.ACTIVITY_DEATH));
	}

	private void deathPost(Player me, String where)
	{
		Actor killer = me.getInteracting();
		boolean hardcore = client.getVarbitValue(VarbitID.IRONMAN) == EventDetect.HARDCORE_GROUP_IRONMAN;
		own(GroupEvent.of(GroupEvent.Type.DEATH, System.currentTimeMillis(), group.localPlayer())
			.text(killer == null ? null : killer.getName())
			.location(where)
			.world(client.getWorld())
			.number(hardcore ? GroupEvent.LIFE_LOST : 0)
			.build());
	}

	/** The gravestone warning posts to Discord, so its death frame may be wanted. */
	private boolean wantsGraveFrame()
	{
		return relay.posting() && settings.discord.contains(GroupEvent.Type.GRAVESTONE_EXPIRING)
			&& webhooks().url(Webhook.of(GroupEvent.Type.GRAVESTONE_EXPIRING)) != null;
	}

	/** Client thread: the held death frame is no longer wanted (warned, looted, or a logout). */
	private void releaseGrave()
	{
		if (graveHeld)
		{
			graveHeld = false;
			relay.send(DiscordRelay.RELEASE, DiscordRelay.release(DiscordRelay.GRAVE));
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() == Skill.OVERALL || !group.member())
		{
			return;
		}
		// Before the value is judged: another account's levels are never the baseline for this one's.
		checkAccount(System.currentTimeMillis());
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
			checkAccount(System.currentTimeMillis());
			if (!group.member())
			{
				return;
			}
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
		if (!group.member())
		{
			return;
		}
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
		Map<Integer, Integer> items = rewardChest(GroupEvent.Type.CLUE);
		if (items != null)
		{
			watchers.clueReward(items, EventText.value(items, group.lookup), now, group.localPlayer());
		}
	}

	/** The reward chest's items (clue or Barrows), names resolved, when {@code type} is wanted; else null. */
	private Map<Integer, Integer> rewardChest(GroupEvent.Type type)
	{
		ItemContainer chest = client.getItemContainer(InventoryID.TRAIL_REWARDINV);
		if (chest == null || !wanted(type))
		{
			return null;
		}
		Map<Integer, Integer> items = new TreeMap<>();
		addAll(items, chest);
		group.resolveItems(items.keySet());
		return items;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (LoginGate.endsSession(state))
		{
			// First, so nothing below that throws can skip it: whatever logs in next starts every baseline over
			// (Board 2026-11-15: an account switch posted the other account's old diaries and levels). A login's
			// held minute ends here; a handful still posts, a burst does not.
			gate.startOver();
			endBursts();
		}
		if (state == GameState.LOGGED_IN)
		{
			loginPending = true;
		}
		// A hop or logout flushes Discord Relay's held moments and ends its level streaks: it reads the game state.
		if (state == GameState.LOGIN_SCREEN)
		{
			// A world hop (HOPPING) is never a logout: it posts nothing, whatever the grace.
			synchronized (presenceLock)
			{
				presence.logout(System.currentTimeMillis());
				savePresence();
				// The channel goes with this player: the next login's channel is a new baseline.
				channelPosts.reset();
			}
			BoardLines.Self was = boardSelf;
			if (was != null && was.online)
			{
				boardSelf = new BoardLines.Self(was.name, false, was.world, System.currentTimeMillis());
			}
			// A run stays in the profile, paused, for this account's next login; members see it end now.
			long now = System.currentTimeMillis();
			waves.pause(now);
			if (waves.active())
			{
				saveProfile(WAVE_RUN_KEY, waves.save());
			}
			waves.reset();
			waves.takeChanged();
			group.shareActivity(waves.state(now));
			// Another account may log in next: what this one was in the middle of (a grave, a raid) goes too.
			watchers.reset();
			releaseGrave();
			backWait = -1;
		}
	}

	/**
	 * The RuneLite profile changed (an account's own settings loaded). Logged in, the values the baselines hold may
	 * be the last profile's: start over as a new login does.
	 */
	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		long now = System.currentTimeMillis();
		gate.rebaseline(now);
		seedProgress(now);
	}

	/** Client thread: a different account than the baselines hold starts them over (no logout came between). */
	private void checkAccount(long now)
	{
		if (gate.account(client.getAccountHash(), now))
		{
			log.debug("account changed without a logout: every baseline starts over");
			seedProgress(now);
		}
	}

	/**
	 * The client's skills and quest points now, recorded as the baseline (the tracker is quiet after a reset), so a
	 * skill that changes nothing until later still has its value and the total is judged.
	 */
	private void seedProgress(long now)
	{
		String me = group.localPlayer();
		for (Skill skill : Skill.values())
		{
			if (skill != Skill.OVERALL)
			{
				progress.skill(skill.getName(), client.getRealSkillLevel(skill), client.getSkillExperience(skill), now,
					me);
			}
		}
		progress.questPoints(client.getVarpValue(VarPlayerID.QP), now, me);
	}

	/** Client thread, each tick and at a session's end: a login's held minute, posted or dropped as a burst. */
	private void releaseBursts(long now)
	{
		postHeld(ownBurst.due(now), broadcastBurst.due(now));
	}

	private void endBursts()
	{
		postHeld(ownBurst.end(), broadcastBurst.end());
	}

	private void postHeld(List<GroupEvent> mine, List<GroupEvent> broadcasts)
	{
		mine.forEach(this::ownNow);
		broadcasts.forEach(e -> router.post(e, broadcastSettings, group.lookup));
		int own = ownBurst.takeDropped();
		int members = broadcastBurst.takeDropped();
		if (own + members > 0)
		{
			log.info("login burst held and not posted: {} own events, {} member broadcasts", own, members);
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
		checkAccount(now);
		if (!group.member())
		{
			// Board 2026-11-15: an account not in the group (or not known to be yet) is never logged in here: no
			// presence, board, activity or event. Its login runs once its roster says it is a member.
			return;
		}
		if (loginPending)
		{
			loginPending = false;
			login(now, me.getName());
			// A run this account left mid-way: paused until the player is back in its arena.
			waves.restoreBests(profile(WAVE_BESTS_KEY));
			waves.restore(profile(WAVE_RUN_KEY));
		}
		releaseBursts(now);
		activityTick(now, me);
		checkBack(now);
		BoardLines.Self was = boardSelf;
		if (was != null && was.online && was.world != client.getWorld())
		{
			// A hop: the board moves the world, and posts nothing.
			boardSelf = new BoardLines.Self(was.name, true, client.getWorld(), 0);
		}
		slayer(now, me.getName());
		GroupEvent combat = progress.combat(me.getCombatLevel(), now, me.getName());
		if (combat != null && wanted(combat.type))
		{
			own(combat);
		}
		readInterfaces(now);
		dry.due(now).forEach(this::own);
		watch(now, me);
	}

	/**
	 * Each tick: the wave run. A completion first (its kill count and duration came this tick, before the teleport
	 * out), then where the player stands, then the Colosseum's chosen modifiers and glory. A changed run, and a
	 * run every {@link #ACTIVITY_KEEPALIVE_MS} while it lasts, goes to the roster, the profile and the party.
	 */
	private void activityTick(long now, Player me)
	{
		String name = me.getName();
		waves.every(resolved().intValue(GroupSettingsSchema.WAVE_EVERY));
		waves.enabled(a -> a == WaveTracker.Arena.FIGHT_CAVES ? config.activityFightCaves()
			: a == WaveTracker.Arena.INFERNO ? config.activityInferno() : config.activityColosseum());
		waves.tick(now, name);
		waves.region(templateRegion(me), client.getWorld(), now, name);
		if (waves.active())
		{
			waves.colosseum(ColosseumModifiers.describe(client::getVarbitValue),
				client.getVarpValue(VarPlayerID.COLOSSEUM_GLORY));
		}
		if (waves.takeBestsChanged())
		{
			saveProfile(WAVE_BESTS_KEY, waves.saveBests());
		}
		boolean keepAlive = waves.active() && now - activitySentAt >= ACTIVITY_KEEPALIVE_MS;
		if (waves.takeChanged() || keepAlive)
		{
			activitySentAt = now;
			saveProfile(WAVE_RUN_KEY, waves.save());
			group.shareActivity(waves.state(now));
		}
	}

	/** The template region the player stands in, unwrapped from an instance (Fight Caves, Inferno); -1 if unknown. */
	private int templateRegion(Player me)
	{
		WorldPoint at = me.getLocalLocation() == null ? null : WorldPoint.fromLocalInstance(client, me.getLocalLocation());
		return at == null ? -1 : at.getRegionID();
	}

	/** "solo" or the team, group members named and anyone else counted ({@link TeamSummary}). */
	private String teamMode()
	{
		return TeamSummary.of(group.localPlayer(), groupMembers(), playersHere()).mode();
	}

	/**
	 * The NPC dialogue, where a Slayer master hands out a task (RuneLite's SlayerPlugin read the same widget,
	 * {@code DIALOG_NPC_TEXT}, before 1f6ae893), and a finished task still waiting for its streak line.
	 */
	private void slayer(long now, String name)
	{
		if (!wanted(GroupEvent.Type.SLAYER))
		{
			return;
		}
		Widget text = client.getWidget(InterfaceID.ChatLeft.TEXT);
		Widget npc = client.getWidget(InterfaceID.ChatLeft.NAME);
		boolean open = text != null && !text.isHidden();
		watchers.slayerDialog(open ? text.getText() : null, npc == null ? "" : npc.getText(), now, name);
		watchers.slayerTick(now, name);
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
		if (!gate.tick())
		{
			return;
		}
		String name = me.getName();
		if (POLLED.stream().anyMatch(this::wanted))
		{
			watchers.poll(id -> id == EventWatchers.SKULL ? (me.getSkullIcon() != -1 ? 1 : 0) : client.getVarbitValue(id),
				now, name);
		}
		deathTick(now, name);
		farmTick(now, me);
		if (++scheduleTicks >= SCHEDULE_EVERY_TICKS)
		{
			scheduleTicks = 0;
			schedule(now, name);
		}
	}

	/** After a death: its value once settled, then the gravestone's timer and the death frame it holds. */
	private void deathTick(long now, String name)
	{
		if (wanted(GroupEvent.Type.DEATH_VALUE) || wanted(GroupEvent.Type.GRAVESTONE_EXPIRING))
		{
			// Also for the gravestone alone: its warning lists what the death left in it.
			watchers.deathTick(carried(), group.lookup, now, name);
		}
		if (wanted(GroupEvent.Type.GRAVESTONE_EXPIRING))
		{
			watchers.graveTick(client.getVarbitValue(VarbitID.GRAVESTONE_VISIBLE), settings.graveWarnMinutes, now, name);
		}
		if (!watchers.graveOut())
		{
			// Warned (already posted, the picture read), looted, or never wanted: the death's frame goes.
			releaseGrave();
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

	/**
	 * The weekly recap when due. A first run records the time and sends nothing. The daily digest is Discord
	 * Relay's, at the group's digest hour ({@code settings} message).
	 */
	private void schedule(long now, String name)
	{
		farm.due(now / 1000L, name);
		saveFarm();
		ZoneId zone = ZoneId.systemDefault();
		int hour = settings.digestHour;
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

	private void login(long now, String name)
	{
		synchronized (seenLock)
		{
			seen = GroupBroadcasts.Seen.load(profile(BROADCASTS_KEY), now);
		}
		dry = DryStreak.parse(profile(DRY_KEY));
		week = Digest.Week.parse(profile(WEEK_KEY));
		farm = FarmRuns.parse(profile(FARM_KEY), this::own, this::wanted);
		gate.login(now);
		if (!gate.settled())
		{
			seedProgress(now);
		}
		boardSelf = new BoardLines.Self(name, true, client.getWorld(), 0);
		if (boardLoginAt == 0)
		{
			// A hop logs in again too; only the run's first login starts the board's wait.
			boardLoginAt = now;
		}
		List<GroupEvent> posts;
		synchronized (presenceLock)
		{
			posts = presence.login(now, client.getWorld(), name, offlineGrace());
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
			presence = PresenceDebounce.parse(getConfig(PRESENCE_KEY), ZoneId.systemDefault());
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				presence.resume();
			}
		}
	}

	/** Holding {@link #presenceLock}: writes the pending logout only when it changed. */
	private void savePresence()
	{
		if (presence.takeDirty())
		{
			String saved = presence.save();
			setConfig(PRESENCE_KEY, saved.isEmpty() ? null : saved);
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
		Map<Integer, Integer> items = rewardChest(GroupEvent.Type.BARROWS);
		if (items == null)
		{
			return;
		}
		BarrowsProgress sets = BarrowsProgress.parse(profile(BARROWS_KEY));
		String progressText = sets.add(items, group.lookup);
		saveProfile(BARROWS_KEY, sets.save());
		own(GroupEvent.of(GroupEvent.Type.BARROWS, now, group.localPlayer()).text("Barrows").items(items)
			.number(kc("Barrows", now)).detail(progressText.isEmpty() ? "" : progressText + " from the chest")
			.build());
	}

	// ---- Discord ----

	/** This member's own webhooks, with the leader's shared ones filling any left empty ({@link GroupService#webhooks}). */
	private Webhooks webhooks()
	{
		GroupService g = group;
		return g == null ? Webhooks.NONE : g.webhooks();
	}

	/** The sidebar Log: every routed event, yours and members'. */
	private void toLog(GroupEvent event)
	{
		GroupService g = group;
		if (g != null)
		{
			g.logEvent(event);
		}
	}

	/**
	 * Router output: one of this player's events for its category's daily digest, held by Discord Relay. Separate
	 * from {@link #toDiscord}: the digest is a summary on top, never a hold.
	 */
	private void toDigest(GroupEvent event)
	{
		if (relay.installed())
		{
			relay.send(DiscordRelay.EVENT, DiscordRelay.event(event, group.lookup, false, true, false));
		}
	}

	/**
	 * Router output: one of this player's events (or one this client posts for the group) to post now. Discord
	 * Relay groups a moment's events, picks the webhook and takes the picture.
	 */
	private void toDiscord(GroupEvent event)
	{
		if (!relay.installed())
		{
			return;
		}
		if (event.type == GroupEvent.Type.ONLINE && group.boardKeptByOthers(System.currentTimeMillis()))
		{
			// Another member's client keeps the live board: it already shows who is online, so no separate post.
			return;
		}
		relay.send(DiscordRelay.EVENT, DiscordRelay.event(event, group.lookup, true, false, false));
	}

	/** The Easter egg settings now: the group's when it has them. Also read by the overlays for the 67/69 tag. */
	private EasterEggs.Settings eggSettings()
	{
		return EasterEggs.Settings.of(resolved());
	}

}
