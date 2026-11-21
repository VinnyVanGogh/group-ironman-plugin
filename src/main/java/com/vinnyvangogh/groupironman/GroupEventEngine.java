package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
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
import java.util.function.Consumer;
import java.util.function.Predicate;
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
import net.runelite.api.events.ItemContainerChanged;
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
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.input.KeyManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.SkillIconManager;
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
	/** The digest and recap schedule is checked this often, not every tick. */
	static final int SCHEDULE_EVERY_TICKS = 100;
	// RuneLite profile keys for saved progress. Not settings: never shown, read only here.
	static final String DRY_KEY = "dryStreaks";
	/** Profile key: the game broadcasts posted in the last day ({@link GroupBroadcasts.Seen}). Not a setting. */
	static final String BROADCASTS_KEY = "broadcastsPosted";
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
	/**
	 * Config key: where the live board's message id was kept before {@link #BOARD_FILE}; read once to carry it
	 * over, then unset. Also the fallback when the file cannot be written. Not a setting; never sent to the party.
	 */
	static final String BOARD_KEY = "statusBoardMessage";
	/** Plugin data file: each webhook's live board ids ({@link StatusBoard#merge}), written on every change. */
	static final String BOARD_FILE = "status-board.txt";
	/**
	 * Plugin data file: each open level streak's post ({@link LevelThreads#saved}), so a restart keeps grouping.
	 * Written the moment it changes, as {@link #BOARD_FILE} is: a client killed between level-ups still has it.
	 */
	static final String LEVEL_FILE = "level-streaks.txt";
	/** Plugin data file: each open grouped post of every other kind ({@link EventThreads}), kept as {@link #LEVEL_FILE}. */
	static final String GROUPED_FILE = "grouped-posts.txt";
	/** Config key: set once 'Online status' has been chosen for this install, so an upgrade keeps its old choice. */
	static final String ONLINE_STATUS_CHOSEN_KEY = "onlineStatusChosen";
	/** Config key: set once 'Combine level-ups' has been settled for this install ({@link #keepCombineWindow}). */
	static final String COMBINE_CHOSEN_KEY = "combineLevelsChosen";
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
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private DrawManager drawManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private KeyManager keyManager;

	private final ToastQueue toasts = new ToastQueue();
	private final WebhookWarnings webhookWarnings = new WebhookWarnings();
	private final PosterNames posterNames = new PosterNames();
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
	/** Client thread: Discord events waiting for the rest of their moment. */
	private final DiscordBatch batch = new DiscordBatch();
	/** A member's level-ups as one message, a card per skill, edited in place. Any thread; kept in {@link #LEVEL_FILE}. */
	private LevelPosts levels;
	/** Whether this run has read {@link #LEVEL_FILE}: nothing is written over it before. Timer and shutdown. */
	private volatile boolean levelsLoaded;
	/** A member's other posts of one kind as one message, edited in place. Any thread; kept in {@link #GROUPED_FILE}. */
	private EventPosts grouped;
	/** Whether this run has read {@link #GROUPED_FILE}. Timer and shutdown. */
	private volatile boolean groupedLoaded;
	/** Posted drops waiting for their pickup ({@link PickupWatch}). Any thread; never saved. */
	private final PickupWatch pickups = new PickupWatch();
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
	/** The death's frame for the gravestone warning; memory only, so a restart loses it. */
	private final GraveShot graveShot = new GraveShot();
	/** Level-up pictures, taken once the chat shows the level. Client thread. */
	private final LevelShot levelShot = new LevelShot();
	private volatile Digest digest = new Digest();
	private DryStreak dry = DryStreak.parse("");
	private Digest.Week week = Digest.Week.parse("");
	private FarmRuns farm = new FarmRuns(this::own, this::wanted);
	private int scheduleTicks;
	private DiscordSender sender;
	private final StatusBoard board = new StatusBoard();
	/** This player for the live board: set on the client thread, read by the timer. Null before a login. */
	private volatile StatusBoard.Self boardSelf;
	/** The saved boards as {@link #BOARD_FILE} holds them; null until read this run. */
	private volatile String boardSaved;
	/** This run's first login, 0 before one: the board waits {@link StatusBoard#SETTLE_MS} after it. */
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
		sender = new DiscordSender(DiscordSender.okHttp(okHttpClient, gson), System::currentTimeMillis,
			() -> LocalTime.now().getHour());
		levels = new LevelPosts(sender, gson, System::currentTimeMillis, config::discordStyle,
			() -> posterName(Webhook.PROGRESS));
		grouped = new EventPosts(sender, gson, System::currentTimeMillis, config::discordStyle, this::discordWords);
		keepCombineWindow();
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
		if (sender != null)
		{
			sender.clear();
		}
		// The queue's board request is gone with it: forget it was out, so a restart sends again. A board
		// answered since the last tick is saved first.
		saveBoard();
		board.target(null, () -> null);
		boardSaved = null;
		boardLoginAt = 0;
		// Unsent posts are dropped at shutdown, as the sender's queue is.
		batch.drain();
		levelShot.clear();
		if (levels != null)
		{
			// A level posted since the last tick is kept for the next start.
			saveLevels();
			levelsLoaded = false;
			levels.clear();
		}
		if (grouped != null)
		{
			saveGrouped();
			groupedLoaded = false;
			grouped.clear();
		}
		pickups.clear();
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
		OnlineStatus online = StatusBoard.effective(config.discordOnlineStatus(), v, webhooks());
		settings = EventSettings.from(config, v, MemberPrefs.of(config), online);
		broadcastSettings = EventSettings.from(config, v, MemberPrefs.NONE, online);
		if (sender != null)
		{
			sender.quietHours(v.intValue("discordQuietStart"), v.intValue("discordQuietEnd"));
		}
		if (toastOverlay != null)
		{
			toastOverlay.reposition();
		}
		warnBadWebhooks();
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

	/**
	 * A webhook field that is set but not a Discord webhook link gets one
	 * chat line, on login or a config change, naming the field and never the
	 * URL. Logged out, it waits for the login.
	 */
	private void warnBadWebhooks()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		// This member's own fields only: a leader's shared webhook is not theirs to fix, and its URL is never named.
		Webhooks webhooks = Webhooks.of(config);
		List<String> lines = new ArrayList<>(webhookWarnings.check(webhooks.urlFields()));
		lines.addAll(posterNames.check(webhooks.customNames()));
		for (String line : lines)
		{
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.GAMEMESSAGE)
				.runeLiteFormattedMessage(line)
				.build());
		}
	}

	/** Executor, once a second: the next Discord post, and a logout that has settled into "offline". */
	private void tick()
	{
		try
		{
			loadLevels();
			levels.tick();
			saveLevels();
			loadGrouped();
			grouped.tick();
			saveGrouped();
			sender.pump();
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
		StatusBoard.Self self = boardSelf;
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
			&& !DiscordQueue.quiet(LocalTime.now().getHour(), config.overlayQuietStart(), config.overlayQuietEnd()))
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
	 * Timer: the live board, when 'Online status' is the board and the Online status webhook (or Main) is set.
	 * Members are read from the roster the party fills; the webhook and the message id stay in this client. On
	 * the leader's shared webhook only the leader's client keeps it ({@link StatusBoard#keeps}).
	 */
	private void boardTick(long now)
	{
		loadBoards();
		// What Discord answered since the last tick is saved before the webhook can change.
		saveBoard();
		String url = webhookFor(Webhook.ONLINE);
		GroupSettingsSchema.Resolved v = values != null ? values : new GroupSettingsSchema.Resolved(config, null);
		if (!StatusBoard.keeps(StatusBoard.effective(config.discordOnlineStatus(), v, webhooks()), webhooks(),
			group.isLeader()))
		{
			url = null;
		}
		// Tells the party this client keeps the board, so the others post no separate online/offline messages.
		group.keepingBoard(url != null);
		// Board 2026-11-14: nothing goes out until the leader's role, the shared webhooks and the saved boards are in.
		board.settle(StatusBoard.settled(boardLoginAt, now, boardSaved != null && group.cacheLoaded()));
		board.target(url, () -> boardSaved);
		if (url != null)
		{
			sendBoard(now);
		}
		saveBoard();
	}

	/** Timer: the board's lines now, and its next request when one is due. */
	private void sendBoard(long now)
	{
		// Logged out too: the last plugin user's client marks the board as no longer kept current.
		StatusBoard.Self self = boardSelf;
		GroupGovernance g = group.governance();
		Map<String, String> activities = StatusBoard.activitySuffixes(group.roster().activities(now),
			g == null ? Collections.<String, MemberPrefs>emptyMap() : g.prefs(), self == null ? "" : self.name, MemberPrefs.of(config),
			config.groupShareEvents());
		String lines = StatusBoard.lines(group.members(), group.roster().lastHeard(), group.roster().lastSeen(),
			group.roster().allStats(), self, group.presence(), activities, now);
		if (!lines.isEmpty())
		{
			board.show(lines, now);
		}
		GroupSettingsSchema.Resolved v = resolved();
		boolean quiet = DiscordQueue.quiet(LocalTime.now().getHour(), v.intValue("discordQuietStart"),
			v.intValue("discordQuietEnd"));
		StatusBoard.Send send = board.next(now, quiet, LocalDate.now().toEpochDay(), config.discordRepostBoard());
		if (send != null)
		{
			board.send(sender, send, StatusBoard.payload(gson, send, config.discordStyle(),
				posterName(Webhook.ONLINE)), System::currentTimeMillis);
		}
	}

	/** Timer: the saved boards, read once a run: the plugin's file, else the line the config kept before it. */
	private void loadBoards()
	{
		if (boardSaved != null)
		{
			return;
		}
		String text = readData(BOARD_FILE);
		if (text == null)
		{
			text = getConfig(BOARD_KEY);
		}
		boardSaved = text == null ? "" : text;
	}

	/**
	 * Timer, and shutdown: a changed board id, written to the plugin's file at once. Not the RuneLite config:
	 * ConfigManager keeps a change in memory and writes it every five minutes or at a clean shutdown, so a client
	 * killed sooner (a dev client stopped from Gradle) lost the id and the next start posted a new board.
	 */
	private void saveBoard()
	{
		String before = boardSaved;
		String line = before == null ? null : board.takeSaved();
		if (line == null)
		{
			return;
		}
		String text = StatusBoard.merge(before, line);
		boardSaved = text;
		try
		{
			PluginFiles.write(group.dataFile(BOARD_FILE), text, "status-board");
			if (getConfig(BOARD_KEY) != null)
			{
				setConfig(BOARD_KEY, null);
			}
		}
		catch (Exception e)
		{
			log.debug("could not write the saved boards", e);
			setConfig(BOARD_KEY, text);
		}
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

	/** Timer, once a run: the level streaks a restart left open, while still inside 'Combine level-ups'. */
	private void loadLevels()
	{
		if (levelsLoaded)
		{
			return;
		}
		levels.restore(readData(LEVEL_FILE), TimeUnit.MINUTES.toMillis(config.discordCombineLevels()));
		levelsLoaded = true;
	}

	/** Timer, and shutdown: the open level streaks, written to the plugin's file when they changed. */
	private void saveLevels()
	{
		writeData(LEVEL_FILE, levelsLoaded ? levels.saved() : null, "level-streaks");
	}

	/** Timer, once a run: the grouped posts a restart left open, while still inside 'Group posts'. */
	private void loadGrouped()
	{
		if (groupedLoaded)
		{
			return;
		}
		grouped.restore(readData(GROUPED_FILE), TimeUnit.MINUTES.toMillis(config.discordGroupPosts()));
		groupedLoaded = true;
	}

	/** Timer, and shutdown: the open grouped posts, written to the plugin's file when they changed. */
	private void saveGrouped()
	{
		writeData(GROUPED_FILE, groupedLoaded ? grouped.saved() : null, "grouped-posts");
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

	/** {@code text} into a plugin data file; nothing when null. {@code what} names it in the file's temp copy. */
	private void writeData(String file, String text, String what)
	{
		if (text == null)
		{
			return;
		}
		try
		{
			PluginFiles.write(group.dataFile(file), text, what);
		}
		catch (Exception e)
		{
			log.debug("could not write {}", file, e);
		}
	}

	/**
	 * Once per install, before {@link #chooseOnlineStatus} marks it as run: an install that had the plugin keeps
	 * 'Combine level-ups' at its old 30 min default; only new installs get 60 ({@link LevelPosts#windowToKeep}).
	 */
	private void keepCombineWindow()
	{
		if (getConfig(COMBINE_CHOSEN_KEY) != null)
		{
			return;
		}
		// Every earlier build that ran set this marker at its first start.
		boolean upgrading = getConfig(ONLINE_STATUS_CHOSEN_KEY) != null;
		Integer kept = LevelPosts.windowToKeep(upgrading, getConfig(GroupIronmanConfig.KEY_COMBINE_LEVELS));
		if (kept != null)
		{
			setConfig(GroupIronmanConfig.KEY_COMBINE_LEVELS, kept);
		}
		setConfig(COMBINE_CHOSEN_KEY, "1");
	}

	/** Once per install: an upgrade keeps what its old "Online and offline" switch did ({@link OnlineStatus#upgrading}). */
	private void chooseOnlineStatus()
	{
		if (getConfig(ONLINE_STATUS_CHOSEN_KEY) != null)
		{
			return;
		}
		OnlineStatus kept = OnlineStatus.upgrading(config.discordOnline(), config.discordEnabled());
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
		StatusBoard.Self self = boardSelf;
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
		for (GroupEvent e : EventDetect.storage(change, group.lookup))
		{
			router.route(e, own, settings, group.lookup);
			if (own && e.type == GroupEvent.Type.STORAGE_SUMMARY)
			{
				// A big move's post lists the whole visit, whether or not the summary is switched on.
				batch.attach(e);
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

	/** A game message that is not a count: a level-up line (for its picture), a pet or a collection log slot. */
	private void gameMessage(String message, long now)
	{
		if (levelShot.chat(message, now))
		{
			shootLevels(now);
			return;
		}
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
		int grave = graveShot.died();
		if (postsPlainDeath())
		{
			deathPost(me, where);
		}
		// No death post took the frame (death posts off, or joined an open moment): take it for the grave alone.
		if (keepsDeathFrame() && graveShot.ask(grave))
		{
			drawManager.requestNextFrameListener(image -> executor.execute(() -> graveShot.keep(grave, png(image))));
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

	/** The gravestone warning posts to Discord with "Where I died" ({@link GraveScreenshot#DEATH}) and screenshots on. */
	private boolean keepsDeathFrame()
	{
		return config.discordScreenshot() && config.discordGravestoneScreenshot() == GraveScreenshot.DEATH
			&& settings.discord.contains(GroupEvent.Type.GRAVESTONE_EXPIRING)
			&& webhookFor(Webhook.of(GroupEvent.Type.GRAVESTONE_EXPIRING)) != null;
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
		if (state == GameState.HOPPING || state == GameState.LOGIN_SCREEN)
		{
			// No ticks until the new world loads, or the next login: post the held moments now.
			batch.drain().forEach(this::flush);
			levelShot.drain().forEach(this::shoot);
		}
		if (state == GameState.LOGIN_SCREEN)
		{
			// A logout ends every level streak; an edit already owed still goes.
			levels.close();
			// A world hop (HOPPING) is never a logout: it posts nothing, whatever the grace.
			synchronized (presenceLock)
			{
				presence.logout(System.currentTimeMillis());
				savePresence();
				// The channel goes with this player: the next login's channel is a new baseline.
				channelPosts.reset();
			}
			StatusBoard.Self was = boardSelf;
			if (was != null && was.online)
			{
				boardSelf = new StatusBoard.Self(was.name, false, was.world, System.currentTimeMillis());
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
			graveShot.free();
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
		batch.due(now).forEach(this::flush);
		shootLevels(now);
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
		StatusBoard.Self was = boardSelf;
		if (was != null && was.online && was.world != client.getWorld())
		{
			// A hop: the board moves the world, and posts nothing.
			boardSelf = new StatusBoard.Self(was.name, true, client.getWorld(), 0);
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
			graveShot.free();
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
		// Every digest goes to the Daily digest webhook (Main when empty), under its poster name.
		String url = webhookFor(Webhook.DIGEST);
		// Draining first would lose the day's lines when the webhook has been cleared.
		if (url != null)
		{
			String name = posterName(Webhook.DIGEST);
			for (GroupEvent.Category category : settings.digest)
			{
				sender.post(url, DiscordWebhook.digest(gson, category, digest.drain(category), name), null);
			}
		}
		saveProfile(DIGEST_KEY, digest.save());
	}

	private void login(long now, String name)
	{
		synchronized (seenLock)
		{
			seen = GroupBroadcasts.Seen.load(profile(BROADCASTS_KEY), now);
		}
		dry = DryStreak.parse(profile(DRY_KEY));
		week = Digest.Week.parse(profile(WEEK_KEY));
		digest = Digest.parse(profile(DIGEST_KEY));
		farm = FarmRuns.parse(profile(FARM_KEY), this::own, this::wanted);
		warnBadWebhooks();
		gate.login(now);
		if (!gate.settled())
		{
			seedProgress(now);
		}
		boardSelf = new StatusBoard.Self(name, true, client.getWorld(), 0);
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

	/**
	 * Where {@code webhook}'s posts go: its own URL, else Main's ({@link Webhooks#url}); null when Discord is off or
	 * neither is set. Every sender asks this; none reads a webhook setting itself.
	 */
	private String webhookFor(Webhook webhook)
	{
		if (!config.discordEnabled() || sender == null)
		{
			return null;
		}
		return webhooks().url(webhook);
	}

	/** The name {@code webhook}'s posts go out under, by its own choice ({@link Webhooks#name}). */
	private String posterName(Webhook webhook)
	{
		return webhooks().name(webhook);
	}

	/** This member's own webhooks, with the leader's shared ones filling any left empty ({@link GroupService#webhooks}). */
	private Webhooks webhooks()
	{
		GroupService g = group;
		return g == null ? Webhooks.of(config) : g.webhooks();
	}

	/**
	 * Router output: one of this player's events for its category's daily
	 * digest. Separate from {@link #toDiscord}, which posts it now when its
	 * kind is ticked: the digest is a summary on top, never a hold.
	 */
	/** The sidebar Log: every routed event, yours and members'. */
	private void toLog(GroupEvent event)
	{
		GroupService g = group;
		if (g != null)
		{
			g.logEvent(event);
		}
	}

	private void toDigest(GroupEvent event)
	{
		if (webhookFor(Webhook.DIGEST) == null)
		{
			return;
		}
		// A Discord line: "450K (450,001)", as the webhook's own posts read. A 0 gp death has no value
		// line, so without an egg there is nothing left to say ("lost about 0 gp" is never listed).
		String words = DiscordWebhook.words(event, discordWords(), egg(Collections.singletonList(event)));
		if (words == null)
		{
			return;
		}
		digest.add(event.type.category, event.member + ": " + words.replace('\n', ' '));
		saveProfile(DIGEST_KEY, digest.save());
	}

	/**
	 * Router output: one of this player's events posted now, when posting is on and a webhook is set. Kinds
	 * that share a moment with others wait in {@link #batch} so the moment is one post.
	 */
	private void toDiscord(GroupEvent event)
	{
		String url = webhookFor(Webhook.of(event.type));
		if (url == null)
		{
			return;
		}
		if (event.type == GroupEvent.Type.ONLINE && group.boardKeptByOthers(System.currentTimeMillis()))
		{
			// Another member's client keeps the live board: it already shows who is online, so no separate post.
			return;
		}
		if (event.broadcast)
		{
			broadcastToDiscord(url, event);
			return;
		}
		DiscordMerge.Family family = DiscordMerge.family(event.type);
		if (family == null)
		{
			GraveShot.Choice shot = picture(event);
			post(url, DiscordMerge.single(event, discordWords()), Collections.singletonList(event), shot.picture,
				shot.png);
			return;
		}
		List<DiscordBatch.Pending> done = new ArrayList<>();
		DiscordBatch.Pending moment = batch.add(event, System.currentTimeMillis(),
			DiscordMerge.window(family, settings), done);
		if (framesAtStart(event, moment, done))
		{
			// The frame of the moment itself, not of whenever the post goes out (a death's respawn). A death's
			// frame is also the gravestone warning's, when that wants it: encoded once, held for that grave.
			int live = graveShot.live();
			int grave = family == DiscordMerge.Family.DEATH && keepsDeathFrame() && graveShot.ask(live) ? live : 0;
			// A death frame kept for the gravestone stays the full screen: that post shows the scene.
			Rectangle chat = grave == 0 && ChatCrop.chatOnly(event.type) ? chatBounds() : null;
			Dimension real = client.getRealDimensions();
			drawManager.requestNextFrameListener(image -> executor.execute(() ->
			{
				moment.screenshot = png(ChatCrop.crop(ImageUtil.bufferedImageFromImage(image), chat, real));
				graveShot.keep(grave, moment.screenshot);
			}));
		}
		done.forEach(this::flush);
	}

	/**
	 * Another member's moment: never merged with this player's own, never a screenshot of this screen. A level
	 * joins that member's streak post as a plugin user's own does (Board 2026-11-14).
	 */
	private void broadcastToDiscord(String url, GroupEvent event)
	{
		List<GroupEvent> one = Collections.singletonList(event);
		DiscordMerge.Post single = DiscordMerge.single(event, discordWords());
		Consumer<byte[]> streak = combineLevels(url,
			LevelThreads.combinableBroadcast(event) == null ? null : one, single, one);
		if (streak != null)
		{
			streak.accept(null);
			return;
		}
		post(url, single, one, false, null);
	}

	/**
	 * A moment just opened, still to be posted, wants the frame of its start. Not a level-up's: that frame waits
	 * for its chat line ({@link LevelShot}) and is taken when the moment is posted.
	 */
	private boolean framesAtStart(GroupEvent event, DiscordBatch.Pending moment, List<DiscordBatch.Pending> done)
	{
		return moment.events.size() == 1 && !done.contains(moment) && config.discordScreenshot()
			&& event.type != GroupEvent.Type.LEVEL_UP;
	}

	/** The picture for a post made at once: a logout settles at the login screen, a gravestone by its setting. */
	private GraveShot.Choice picture(GroupEvent event)
	{
		if (event.type == GroupEvent.Type.GRAVESTONE_EXPIRING)
		{
			return graveShot.choose(config.discordGravestoneScreenshot(), config.discordScreenshot());
		}
		return event.type == GroupEvent.Type.ONLINE ? GraveShot.Choice.NOTHING : GraveShot.Choice.NEXT_FRAME;
	}

	/** Names and prices, amounts read the Discord way: "450K (450,001)" (Board 2026-11-10). */
	private ItemLookup discordWords()
	{
		return Amounts.BOTH.words(group.lookup);
	}

	/** Client thread. One held moment as one post; a level-up's waits for its picture ({@link LevelShot}). */
	private void flush(DiscordBatch.Pending moment)
	{
		DiscordMerge.Post post = DiscordMerge.merge(moment.events, settings, discordWords());
		String url = post == null ? null : webhookFor(Webhook.of(post.lead.type));
		if (url == null)
		{
			return;
		}
		Consumer<byte[]> send = combineLevels(url, LevelThreads.combinable(moment.events), post, moment.events);
		if (send == null)
		{
			send = poster(url, post, moment.events, true);
		}
		if (send == null)
		{
			return;
		}
		GroupEvent level = firstLevelUp(moment.events);
		if (!config.discordScreenshot() || moment.screenshot != null)
		{
			send.accept(moment.screenshot);
		}
		else if (level != null)
		{
			levelShot.hold(level.text, level.number, level.at, send);
			shootLevels(System.currentTimeMillis());
		}
		else
		{
			nextFrame(send, allChatOnly(moment.events));
		}
	}

	private static GroupEvent firstLevelUp(List<GroupEvent> events)
	{
		return events.stream().filter(e -> e.type == GroupEvent.Type.LEVEL_UP).findFirst().orElse(null);
	}

	/** Client thread. The level-up pictures due now: one frame each, for every post waiting on it. */
	private void shootLevels(long now)
	{
		levelShot.due(now).forEach(this::shoot);
	}

	private void shoot(LevelShot.Held shot)
	{
		nextFrame(png -> shot.sends.forEach(send -> send.accept(png)), true);
	}

	/**
	 * A moment of one member's level-ups ({@code levels}: skills below 99 and combat levels, null when it is
	 * anything else) joins that member's post when the combine window is on: the first posts {@code first} and is
	 * kept, later ones edit it ({@link LevelThreads}). Returns what sends it, given the picture; null when the moment
	 * posts as usual.
	 */
	private Consumer<byte[]> combineLevels(String url, List<GroupEvent> levels, DiscordMerge.Post first,
		List<GroupEvent> events)
	{
		long window = TimeUnit.MINUTES.toMillis(config.discordCombineLevels());
		if (window <= 0 || levels == null)
		{
			return null;
		}
		// The egg reads the item cache: on the client thread, as every other post's does.
		String egg = egg(events);
		return png -> levelUp(url, levels, first, window, png, egg);
	}

	/** Any thread. Level-ups into their member's streak, with the usual post should they start a new one. */
	private void levelUp(String url, List<GroupEvent> levelUps, DiscordMerge.Post first, long window, byte[] png,
		String egg)
	{
		String json = DiscordWebhook.payload(gson, first, discordWords(), config.discordStyle(),
			config.discordMention(), png != null, egg, posterName(Webhook.PROGRESS));
		levels.level(url, levelUps, window, png, json);
	}

	/** The Easter egg line for a moment's events; null when Easter eggs are off or none applies. */
	private String egg(List<GroupEvent> events)
	{
		return EasterEggs.line(events, group.lookup, eggSettings());
	}

	/** The Easter egg settings now: the group's when it has them. Also read by the overlays for the 67/69 tag. */
	private EasterEggs.Settings eggSettings()
	{
		return EasterEggs.Settings.of(resolved());
	}

	/**
	 * {@code shot}: a frame already taken; when a picture is wanted and none is ready, the next frame. Nothing is
	 * posted when the post has nothing to say (a 0 gp death on its own, no egg).
	 */
	private void post(String url, DiscordMerge.Post post, List<GroupEvent> events, boolean picture, byte[] shot)
	{
		Consumer<byte[]> send = poster(url, post, events, picture);
		if (send == null)
		{
			return;
		}
		if (!picture || !config.discordScreenshot() || shot != null)
		{
			send.accept(shot);
			return;
		}
		nextFrame(send, allChatOnly(events));
	}

	/**
	 * Client thread. What sends a post, given its picture (dropped when none is wanted); null for nothing to say.
	 * Within 'Group posts', a kind that groups ({@link EventThreads#family}) joins its member's message for that kind
	 * (Board 2026-11-15); its first post is this usual one.
	 */
	private Consumer<byte[]> poster(String url, DiscordMerge.Post post, List<GroupEvent> events, boolean picture)
	{
		boolean picked = picture && config.discordScreenshot();
		ItemLookup words = discordWords();
		String egg = egg(events);
		String mention = config.discordMention();
		String username = posterName(Webhook.of(post.lead.type));
		String json = DiscordWebhook.payload(gson, post, words, config.discordStyle(), mention, picked, egg, username);
		if (json == null)
		{
			return null;
		}
		if (picked && config.discordPickupShot() && pickable(post))
		{
			return pickupPost(url, json, post.lead.items);
		}
		long window = TimeUnit.MINUTES.toMillis(config.discordGroupPosts());
		boolean pings = post.big && mention != null && !mention.trim().isEmpty();
		EventThreads.Moment moment = window <= 0 ? null
			: EventPosts.moment(gson, url, post, words, egg, username, pings);
		if (moment != null)
		{
			return png -> grouped.add(moment, json, picked ? png : null, window);
		}
		return png -> sender.post(url, json, picked ? png : null);
	}

	/** A post of this player's own drop, with items to pick up. */
	private static boolean pickable(DiscordMerge.Post post)
	{
		return post.lead.type == GroupEvent.Type.DROP && !post.lead.broadcast && !post.lead.items.isEmpty();
	}

	/**
	 * Client thread. A drop's post that goes on its own (never in a grouped message, whose edits would drop the second
	 * picture) and is then watched for its pickup: {@link #pickedUp} edits it. Where the drop was, and what the
	 * inventory held, are read now.
	 */
	private Consumer<byte[]> pickupPost(String url, String json, Map<Integer, Integer> items)
	{
		Player me = client.getLocalPlayer();
		if (me == null)
		{
			return png -> sender.post(url, json, png);
		}
		WorldPoint at = me.getWorldLocation();
		Map<Integer, Integer> baseline = inventoryCounts();
		long expires = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(config.discordPickupMinutes());
		return png -> sender.post(url, json, png, (status, id) ->
		{
			if (EditedThreads.ok(status) && id != null && png != null)
			{
				pickups.add(new PickupWatch.Watch(url, id, json, png, items, baseline, at.getX(), at.getY(),
					at.getPlane(), expires));
			}
		});
	}

	/** Client thread. The inventory's amounts by canonical item id. */
	private Map<Integer, Integer> inventoryCounts()
	{
		Map<Integer, Integer> counts = new TreeMap<>();
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		if (inv != null)
		{
			for (Item item : inv.getItems())
			{
				if (item.getId() > 0)
				{
					ItemCounts.add(counts, item.getId(), item.getQuantity(), itemManager::canonicalize);
				}
			}
		}
		return counts;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INV || pickups.size() == 0)
		{
			return;
		}
		Player me = client.getLocalPlayer();
		if (me == null)
		{
			return;
		}
		WorldPoint at = me.getWorldLocation();
		for (PickupWatch.Watch w : pickups.inventory(inventoryCounts(), at.getX(), at.getY(), at.getPlane(),
			System.currentTimeMillis()))
		{
			pickedUp(w);
		}
	}

	/** The pickup's frame, then the post's edit: its own screenshot again with the new one after it. */
	private void pickedUp(PickupWatch.Watch w)
	{
		String edit = DiscordWebhook.pickupEdit(gson, w.json);
		if (edit != null)
		{
			nextFrame(png -> sender.edit(w.url, w.messageId, edit, w.shot, png, null));
		}
	}

	/** The ScreenshotPlugin pattern: the next drawn frame, encoded off the client thread. */
	private void nextFrame(Consumer<byte[]> send)
	{
		nextFrame(send, false);
	}

	/** {@code chatOnly}: the chat box alone ({@link ChatCrop}) when the setting is on and the chat is showing. */
	private void nextFrame(Consumer<byte[]> send, boolean chatOnly)
	{
		Rectangle chat = chatOnly ? chatBounds() : null;
		Dimension real = chat == null ? null : client.getRealDimensions();
		drawManager.requestNextFrameListener(image -> executor.execute(() ->
			send.accept(png(ChatCrop.crop(ImageUtil.bufferedImageFromImage(image), chat, real)))));
	}

	/** Client thread. The chat box's canvas bounds; null when chat-only crops are off or the chat is not showing. */
	private Rectangle chatBounds()
	{
		if (!config.discordChatShot())
		{
			return null;
		}
		Widget w = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
		return w == null || w.isHidden() ? null : w.getBounds();
	}

	private static boolean allChatOnly(List<GroupEvent> events)
	{
		return !events.isEmpty() && events.stream().allMatch(e -> ChatCrop.chatOnly(e.type));
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
