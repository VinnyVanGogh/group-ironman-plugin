package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.vinnyvangogh.scenarios.Probes;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.eventbus.Subscribe;

/**
 * The headless stand-in for GIM Utilities' event handlers. {@code GroupService} and {@code GroupEventEngine} need
 * a live {@code Client} (local player, clan channel, party), so a JUnit run cannot start them. This rig subscribes
 * to the same events on a real {@code EventBus} and hands them to the same classes those handlers call
 * ({@link StorageSession}, {@link ProgressTracker}, {@link LevelPosts}, {@link EventRouter}, {@link ChannelPosts},
 * {@link GovernanceSync}), with a fake Discord transport and a scenario clock. Only the few lines of glue are its
 * own; everything a probe reports is the plugin's code.
 *
 * <p>Not covered here: DiscordBatch's merging of a moment's events, screenshots, and anything read from the
 * client (worlds, regions, kill counts). Those stay in their own unit tests and the in-game checklist.
 */
final class GimScenarioRig
{
	static final String WEBHOOK = "https://discord.com/api/webhooks/1/scenario";
	static final String GROUP = "Iron Pals";
	static final int GRACE_MINUTES = 2;
	private static final long LEVEL_WINDOW = TimeUnit.MINUTES.toMillis(30);
	private static final GroupIronmanConfig DEFAULTS = new GroupIronmanConfig()
	{
	};

	/** The clock every component reads; waits in a scenario move it. */
	long now = 1_700_000_000_000L;
	final String self;
	private final Gson gson = new Gson();
	private final FakeItems items = new FakeItems();
	private final EventSettings settings = new EventSettings(EventFixtures.all(), EventFixtures.all(), 1_000_000,
		1_000_000, 1, 7, false);

	private final StorageSession storage = new StorageSession();
	private final GroupRoster roster = new GroupRoster();
	private final ProgressTracker progress = new ProgressTracker(Skill.values().length - 1);
	private final List<DiscordQueue.Post> sent = new ArrayList<>();
	private final List<DiscordSender.Done> unanswered = new ArrayList<>();
	private final DiscordSender sender = new DiscordSender((post, done) ->
	{
		sent.add(post);
		unanswered.add(done);
	}, () -> now, () -> 12);
	private final LevelPosts levels = new LevelPosts(sender, gson, () -> now, () -> DiscordStyle.EMBED,
		() -> items, () -> DiscordWebhook.USERNAME);
	private final List<String> overlay = new ArrayList<>();
	private final EventRouter router = new EventRouter(this::toDiscord, e -> overlay.add(EventText.sentence(e, items)));
	private final ChannelPosts channel = new ChannelPosts(ZoneOffset.UTC);
	private final Map<String, GovernanceClient> members = new HashMap<>();
	private final GovernanceClient me;
	private final List<String> saves = new ArrayList<>();
	private int messageIds;

	GimScenarioRig(String self)
	{
		this.self = self;
		this.me = new GovernanceClient(self, gson);
	}

	/** What a scenario's {@code expect} entries can check. */
	Probes probes()
	{
		return new Probes()
			.add("discord", this::discordLines)
			.add("overlay", () -> overlay)
			.add("storage.log", this::storageLog)
			.add("settings", this::settingsLines)
			.add("settings.saves", () -> saves);
	}

	// Group storage, as GroupService.onWidgetLoaded / onItemContainerChanged / onWidgetClosed.

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded e)
	{
		if (e.getGroupId() == InterfaceID.SHARED_BANK)
		{
			storage.open();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged e)
	{
		if (e.getContainerId() == InventoryID.INV_GROUP_TEMP)
		{
			storage.contents(counts(e.getItemContainer()));
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed e)
	{
		if (e.getGroupId() == InterfaceID.SHARED_BANK)
		{
			StorageChange change = storage.close(now, self);
			if (change != null)
			{
				roster.putStorage(change);
			}
		}
	}

	private static Map<Integer, Integer> counts(ItemContainer container)
	{
		Map<Integer, Integer> out = new TreeMap<>();
		for (Item item : container.getItems())
		{
			ItemCounts.add(out, item.getId(), item.getQuantity(), id -> id);
		}
		return out;
	}

	private List<String> storageLog()
	{
		List<String> out = new ArrayList<>();
		for (PanelModel.LogRow row : PanelModel.storage(roster, items, self, null, now).log)
		{
			out.add(row.text);
		}
		return out;
	}

	// Progress, as GroupEventEngine.onStatChanged and its level post.

	@Subscribe
	public void onStatChanged(StatChanged e)
	{
		if (e.getSkill() == Skill.OVERALL)
		{
			return;
		}
		for (GroupEvent event : progress.skill(e.getSkill().getName(), e.getLevel(), e.getXp(), now, self))
		{
			if (event.type == GroupEvent.Type.LEVEL_UP)
			{
				String json = DiscordWebhook.payload(gson, DiscordMerge.single(event, items), items,
					DiscordStyle.EMBED, "", false, null, DiscordWebhook.USERNAME);
				levels.level(WEBHOOK, event, LEVEL_WINDOW, null, json);
				overlay.add(EventText.sentence(event, items));
			}
			else
			{
				router.route(event, true, settings, items);
			}
		}
	}

	// A detected event of this player's own (a drop, a death), as GroupEventEngine.own routes it.

	@Subscribe
	public void onGroupEvent(GroupEvent e)
	{
		if (e.isValid())
		{
			router.route(e, self.equals(e.member), settings, items);
		}
	}

	private void toDiscord(GroupEvent e)
	{
		sender.post(WEBHOOK, DiscordWebhook.payload(gson, e, items, DiscordStyle.EMBED, "", false), null);
	}

	// Members' presence from the game's Group channel, as GroupEventEngine feeds ChannelPosts.

	/** The Group channel as the game shows it now; {@code plugin}: members whose own client posts for them. */
	static final class Channel
	{
		final Map<String, Integer> online;
		final Set<String> plugin;
		final List<String> roster;

		Channel(Map<String, Integer> online, Set<String> plugin, List<String> roster)
		{
			this.online = online;
			this.plugin = plugin;
			this.roster = roster;
		}
	}

	@Subscribe
	public void onChannel(Channel c)
	{
		GroupMembers group = GroupMembers.of(GROUP, c.roster);
		GamePresence game = GamePresence.read(group, c.online, null);
		for (GroupEvent e : channel.update(game, name -> c.plugin.contains(name), now, GRACE_MINUTES))
		{
			router.route(e, true, settings, items);
		}
	}

	// Group settings saved by another member and received over the party, as GovernanceSync does.

	static final class SettingsSave
	{
		final String member;
		final Map<String, Long> values;

		SettingsSave(String member, Map<String, Long> values)
		{
			this.member = member;
			this.values = values;
		}
	}

	@Subscribe
	public void onSettingsSave(SettingsSave s)
	{
		GovernanceClient them = members.computeIfAbsent(s.member, name ->
		{
			GovernanceClient c = new GovernanceClient(name, gson);
			// Joining the party: each announces itself to the other.
			c.tick(me);
			me.tick(c);
			c.sync.claim(name, now);
			return c;
		});
		Map<String, Long> values = GroupSettingsSchema.seed(DEFAULTS);
		values.putAll(s.values);
		saves.add(s.member + ": " + them.sync.save(s.member, values, now));
		them.tick(me);
	}

	private List<String> settingsLines()
	{
		GroupSettings got = me.sync.governance().settings();
		if (got == null)
		{
			return Collections.emptyList();
		}
		GroupSettingsSchema.Resolved resolved = new GroupSettingsSchema.Resolved(DEFAULTS, got);
		List<String> out = new ArrayList<>();
		for (GroupSettingsSchema.Setting s : GroupSettingsSchema.ALL)
		{
			out.add(s.key + "=" + resolved.value(s.key));
		}
		return out;
	}

	// The engine's once-a-second timer, run on each tick and when the scenario ends.

	@Subscribe
	public void onGameTick(GameTick t)
	{
		for (GroupEvent e : channel.poll(now, GRACE_MINUTES))
		{
			router.route(e, true, settings, items);
		}
		settle();
	}

	/** Sends what is owed and answers each request as Discord would, until nothing is left. */
	void settle()
	{
		for (int i = 0; i < 20; i++)
		{
			levels.tick();
			sender.pump();
			if (unanswered.isEmpty())
			{
				return;
			}
			List<DiscordSender.Done> answering = new ArrayList<>(unanswered);
			unanswered.clear();
			for (DiscordSender.Done done : answering)
			{
				done.done(200, null, "m" + (++messageIds));
			}
		}
	}

	/** "post: Alex: Level up | Prayer level 24", one line per request in order; an edit says "edit". */
	private List<String> discordLines()
	{
		List<String> out = new ArrayList<>();
		for (DiscordQueue.Post post : sent)
		{
			out.add((post.edit == null ? "post: " : "edit: ") + words(post.json));
		}
		return out;
	}

	private String words(String json)
	{
		JsonObject o = gson.fromJson(json, JsonObject.class);
		if (o.has("embeds") && o.getAsJsonArray("embeds").size() > 0)
		{
			JsonObject embed = o.getAsJsonArray("embeds").get(0).getAsJsonObject();
			String title = embed.has("title") ? embed.get("title").getAsString() : "";
			String description = embed.has("description") ? embed.get("description").getAsString() : "";
			return title + " | " + description;
		}
		return o.has("content") ? o.get("content").getAsString() : json;
	}
}
