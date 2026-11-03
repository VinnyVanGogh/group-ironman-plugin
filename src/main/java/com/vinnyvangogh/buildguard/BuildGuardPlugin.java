package com.vinnyvangogh.buildguard;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ParamID;
import net.runelite.api.Skill;
import net.runelite.api.StructComposition;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.party.messages.UserSync;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

/**
 * Build Guard.
 *
 * <p>What is wired so far: the player's build rules (typed into the config
 * panel, read by {@link RuleText}), a chat warning when the selected attack
 * style trains a skill those rules cap, and the opt-in group features over
 * RuneLite Party: each member's rules and levels, the at-risk line, and a
 * "who has what" gear list. The Group Ironman extras (banks, the storage log,
 * live stats, search, storage minimums) live in {@link GroupService}, which
 * this plugin registers on the event bus. Party traffic goes only through RuneLite's own
 * {@link PartyService}; this plugin makes no network calls of its own.
 *
 * <p>The quest verdict, quest-start warning and lamp facades are still stubs
 * and still throw: see {@code SkeletonStubsTest}.
 */
@Slf4j
@PluginDescriptor(
	name = "Build Guard",
	description = "Warns before a quest, lamp or activity gives XP that breaks a capped account's build",
	tags = {"build", "pure", "zerker", "skiller", "quest", "lamp", "xp", "party", "group", "gim"}
)
public class BuildGuardPlugin extends Plugin
{
	/** Fewest ticks between two party sends, so a busy inventory cannot spam the relay. */
	private static final int SEND_INTERVAL_TICKS = 5;

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
	private WSClient wsClient;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private EventBus eventBus;

	@Inject
	private GroupService group;

	@Inject
	private MemberMapMarkers markers;

	private final PartyRoster roster = new PartyRoster();
	private BuildGuardPartyPanel panel;
	private NavigationButton navButton;

	// Written from config changes (Swing thread) and read on the client
	// thread; each is replaced whole, never mutated, so volatile suffices.
	private volatile List<BuildRule> rules = Collections.emptyList();
	private volatile List<BuildRule> atRisk = Collections.emptyList();
	private volatile String riskReason = "";
	private volatile List<String> gear = Collections.emptyList();

	private volatile String lastSent;
	private volatile boolean dirty;
	private int ticksSinceSend = SEND_INTERVAL_TICKS;

	@Provides
	BuildGuardConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BuildGuardConfig.class);
	}

	@Override
	protected void startUp()
	{
		wsClient.registerMessage(BuildGuardPartyMessage.class);
		wsClient.registerMessage(BuildGuardGroupMessage.class);
		eventBus.register(group);
		group.startUp(this::redraw);
		panel = new BuildGuardPartyPanel();
		navButton = NavigationButton.builder()
			.tooltip("Build Guard: party")
			.icon(icon())
			.priority(9)
			.panel(panel)
			.build();
		reloadRules();
		updateNavButton();
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				refreshStyleRisk();
				refreshGear();
			}
		});
	}

	@Override
	protected void shutDown()
	{
		wsClient.unregisterMessage(BuildGuardPartyMessage.class);
		wsClient.unregisterMessage(BuildGuardGroupMessage.class);
		eventBus.unregister(group);
		group.shutDown();
		markers.clear();
		clientToolbar.removeNavigation(navButton);
		roster.clear();
		rules = Collections.emptyList();
		atRisk = Collections.emptyList();
		riskReason = "";
		gear = Collections.emptyList();
		lastSent = null;
		panel = null;
		navButton = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!BuildGuardConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		reloadRules();
		group.reloadConfig();
		updateNavButton();
		lastSent = null;
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				refreshStyleRisk();
				refreshGear();
			}
		});
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarpId() == VarPlayerID.COM_MODE
			|| event.getVarbitId() == VarbitID.COMBAT_WEAPON_CATEGORY
			|| event.getVarbitId() == VarbitID.AUTOCAST_DEFMODE)
		{
			refreshStyleRisk();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		for (BuildRule rule : rules)
		{
			if (rule.skill() == event.getSkill())
			{
				refreshStyleRisk();
				return;
			}
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.INV || event.getContainerId() == InventoryID.WORN)
		{
			refreshGear();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			dirty = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		ticksSinceSend++;
		if (!dirty || ticksSinceSend < SEND_INTERVAL_TICKS)
		{
			return;
		}
		dirty = false;
		sendIfChanged();
	}

	/** Someone joined the party and asked everyone to resend. */
	@Subscribe
	public void onUserSync(UserSync event)
	{
		lastSent = null;
		dirty = true;
	}

	@Subscribe
	public void onUserPart(UserPart event)
	{
		roster.remove(event.getMemberId());
		redraw();
	}

	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		roster.clear();
		group.onPartyChanged();
		lastSent = null;
		dirty = true;
		redraw();
	}

	@Subscribe
	public void onBuildGuardPartyMessage(BuildGuardPartyMessage message)
	{
		if (!config.partyShareBuild())
		{
			// Opted out: neither send nor collect.
			return;
		}
		PartyBuild build = PartyBuildCodec.decode(gson, message.getPayload());
		if (build == null)
		{
			log.debug("ignored unreadable party payload from {}", message.getMemberId());
			return;
		}
		PartyMember member = partyService.getMemberById(message.getMemberId());
		roster.put(message.getMemberId(), member == null ? null : member.getDisplayName(), build);
		redraw();
	}

	private void reloadRules()
	{
		String text = config.buildRules();
		rules = RuleText.parse(text);
		for (String problem : RuleText.problems(text))
		{
			log.debug("build rule not understood: {}", problem);
		}
	}

	private void updateNavButton()
	{
		if (!config.partyShareBuild())
		{
			roster.clear();
		}
		if (config.partyShareBuild() || group.anyOn())
		{
			clientToolbar.addNavigation(navButton);
		}
		else
		{
			clientToolbar.removeNavigation(navButton);
		}
		redraw();
	}

	/** Client thread. Recomputes which rules the selected style is eating into. */
	private void refreshStyleRisk()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		int weaponType = client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY);
		int comMode = client.getVarpValue(VarPlayerID.COM_MODE);
		int defCast = client.getVarbitValue(VarbitID.AUTOCAST_DEFMODE);
		List<String> styles = AttackStyleXp.resolveStyles(styleNames(weaponType));
		if (styles.isEmpty())
		{
			styles = AttackStyleXp.fallbackStyles(weaponType);
		}

		Set<Skill> trained = AttackStyleXp.skillsTrained(styles, comMode, defCast);
		List<BuildRule> now = AttackStyleXp.rulesAtRisk(trained, rules, realLevels());
		String style = AttackStyleXp.styleName(styles, comMode, defCast);

		boolean newlyAtRisk = false;
		for (BuildRule rule : now)
		{
			if (!containsSkill(atRisk, rule.skillName()))
			{
				newlyAtRisk = true;
			}
		}
		atRisk = now;
		riskReason = now.isEmpty() ? "" : style + " attack style";

		// Only on the transition into risk, so re-reading the same style on a
		// stat change does not repeat the warning every level-up.
		if (newlyAtRisk && config.warnAttackStyle())
		{
			List<String> parts = new ArrayList<>();
			for (BuildRule rule : now)
			{
				parts.add(rule.skillName() + " (" + rule.describe() + ")");
			}
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.GAMEMESSAGE)
				.runeLiteFormattedMessage("<col=ff0000>Build Guard: " + style + " trains "
					+ String.join(", ", parts) + ". Change attack style before attacking.</col>")
				.build());
		}
		dirty = true;
	}

	/** The style names the game lists for this weapon type, the way {@code attackstyles} reads them. */
	private List<String> styleNames(int weaponType)
	{
		EnumComposition weaponStyles = client.getEnum(EnumID.WEAPON_STYLES);
		int enumId = weaponStyles.getIntValue(weaponType);
		if (enumId == -1)
		{
			return Collections.emptyList();
		}
		List<String> names = new ArrayList<>();
		for (int structId : client.getEnum(enumId).getIntVals())
		{
			StructComposition struct = client.getStructComposition(structId);
			names.add(struct.getStringValue(ParamID.ATTACK_STYLE_NAME));
		}
		return names;
	}

	private Map<Skill, Integer> realLevels()
	{
		Map<Skill, Integer> out = new EnumMap<>(Skill.class);
		for (BuildRule rule : rules)
		{
			Skill skill = rule.skill();
			if (skill != null)
			{
				out.put(skill, client.getRealSkillLevel(skill));
			}
		}
		return out;
	}

	/** Client thread. Names of worn and carried items, only read when the member shares gear. */
	private void refreshGear()
	{
		if (!config.partyShareBuild() || !config.partyShareGear())
		{
			if (!gear.isEmpty())
			{
				gear = Collections.emptyList();
				dirty = true;
			}
			return;
		}
		Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (int containerId : new int[]{InventoryID.WORN, InventoryID.INV})
		{
			ItemContainer container = client.getItemContainer(containerId);
			if (container == null)
			{
				continue;
			}
			for (Item item : container.getItems())
			{
				if (item.getId() <= 0)
				{
					continue;
				}
				String name = itemManager.getItemComposition(itemManager.canonicalize(item.getId())).getName();
				if (name != null && !name.isEmpty() && !"null".equalsIgnoreCase(name))
				{
					names.add(name);
				}
			}
		}
		List<String> now = new ArrayList<>(names);
		if (!now.equals(gear))
		{
			gear = now;
			dirty = true;
		}
	}

	/** Client thread. Sends this member's payload if it differs from the last one sent. */
	private void sendIfChanged()
	{
		if (!partyService.isInParty())
		{
			return;
		}
		Map<String, Integer> levels = new LinkedHashMap<>();
		for (Map.Entry<Skill, Integer> e : realLevels().entrySet())
		{
			levels.put(e.getKey().getName(), e.getValue());
		}
		List<String> riskSkills = new ArrayList<>();
		for (BuildRule rule : atRisk)
		{
			riskSkills.add(rule.skillName());
		}
		PartyOutbox.Switches switches = new PartyOutbox.Switches(
			config.partyShareBuild(), config.partyShareGear(), config.partyShareRisk());
		String payload = PartyOutbox.payload(gson, switches, rules, levels, gear, riskSkills, riskReason);
		if (payload == null || Objects.equals(payload, lastSent))
		{
			return;
		}
		partyService.send(new BuildGuardPartyMessage(payload));
		lastSent = payload;
		ticksSinceSend = 0;

		// Show ourselves in the panel straight away rather than waiting on the relay's echo.
		PartyMember local = partyService.getLocalMember();
		PartyBuild self = PartyBuildCodec.decode(gson, payload);
		if (local != null && self != null)
		{
			roster.put(local.getMemberId(), local.getDisplayName(), self);
			redraw();
		}
	}

	private void redraw()
	{
		BuildGuardPartyPanel p = panel;
		if (p == null)
		{
			return;
		}
		BuildGuardPartyPanel.View view = new BuildGuardPartyPanel.View(partyService.isInParty(), roster,
			group.roster(), group.lookup, group.switches(), group.regions(), group.since(), group.self());
		SwingUtilities.invokeLater(() -> p.render(view));
		updateMarkers();
	}

	private void updateMarkers()
	{
		if (config.groupWorldMap() && config.groupShareStats() && partyService.isInParty())
		{
			markers.update(group.roster().onlineStats(System.currentTimeMillis()), group.self());
		}
		else
		{
			markers.clear();
		}
	}

	private static boolean containsSkill(List<BuildRule> list, String skill)
	{
		for (BuildRule rule : list)
		{
			if (rule.skillName().equalsIgnoreCase(skill))
			{
				return true;
			}
		}
		return false;
	}

	/** A plain 16x16 shield, drawn rather than shipped: icon.png is still the scaffold's placeholder. */
	private static BufferedImage icon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setColor(new Color(0xD4, 0x8A, 0x1E));
		g.fillRoundRect(2, 1, 12, 14, 6, 6);
		g.setColor(new Color(0x2B, 0x2B, 0x2B));
		g.fillRect(7, 4, 2, 8);
		g.fillRect(4, 7, 8, 2);
		g.dispose();
		return image;
	}
}
