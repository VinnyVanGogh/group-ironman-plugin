package com.vinnyvangogh.groupironman;

import com.vinnyvangogh.testkit.ui.SwingDriver;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.AsyncBufferedImage;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The real {@link GroupIronmanPanel} with nothing of the game behind it: item
 * icons are drawn placeholders (there is no game cache), skill icons are
 * RuneLite's own, and every header or Settings button lands in
 * {@link Recorder}. Names are made up; none is a real account.
 *
 * <p>A {@link Scene} is the panel's {@link GroupIronmanPanel.View} built the way
 * {@code GroupIronmanPlugin#redraw} builds it, from state a scenario sets up.
 */
final class GimPanelFixture
{
	static final String GROUP = "Iron Teacups";
	static final String SELF = "Ember Fox";
	static final String MOSS = "Moss Kettle";
	static final String WREN = "Pebble Wren";
	static final String TIN = "Tin Lantern";
	static final List<String> NAMES = Collections.unmodifiableList(Arrays.asList(SELF, MOSS, WREN, TIN));
	static final String KEY = "gim-key-made-up-for-tests-0001";
	static final long MIN = TimeUnit.MINUTES.toMillis(1);
	static final long HOUR = TimeUnit.HOURS.toMillis(1);
	static final RegionNames REGIONS = RegionNames.parse("Lumbridge=12850\nDraynor=12338\nVarrock=12853\n");
	static final GroupIronmanConfig DEFAULTS = new GroupIronmanConfig()
	{
	};

	/** What the panel's buttons asked for, in order. */
	static final class Recorder implements GroupIronmanPanel.Actions
	{
		final List<String> calls = new ArrayList<>();
		String key = KEY;
		boolean pasteAccepts = true;
		GovernanceSync.SaveResult saveResult = GovernanceSync.SaveResult.SAVED;
		Map<String, Long> saved;

		@Override
		public void join()
		{
			calls.add("join");
		}

		@Override
		public void switchParty()
		{
			calls.add("switchParty");
		}

		@Override
		public void leave()
		{
			calls.add("leave");
		}

		@Override
		public void newKey()
		{
			calls.add("newKey");
		}

		@Override
		public String groupKey()
		{
			return key;
		}

		@Override
		public boolean pasteKey(String text)
		{
			calls.add("pasteKey:" + text);
			return pasteAccepts;
		}

		@Override
		public void openSettings()
		{
			calls.add("openSettings");
		}

		@Override
		public void turnOnSharing()
		{
			calls.add("turnOnSharing");
		}

		@Override
		public void postToDiscord()
		{
			calls.add("postToDiscord");
		}

		@Override
		public boolean claim()
		{
			calls.add("claim");
			return true;
		}

		@Override
		public boolean setEditor(String name, boolean editor)
		{
			calls.add("setEditor:" + name + "=" + editor);
			return true;
		}

		@Override
		public GovernanceSync.SaveResult save(Map<String, Long> values)
		{
			calls.add("save");
			saved = values;
			return saveResult;
		}

		@Override
		public boolean shareWebhooks(boolean share)
		{
			calls.add("shareWebhooks:" + share);
			return true;
		}

		@Override
		public void forgetLeader()
		{
			calls.add("forgetLeader");
		}

		@Override
		public void optOut(GroupEvent.Type type, boolean overlay, boolean out)
		{
			calls.add("optOut:" + type + (overlay ? ":show" : ":post") + "=" + out);
		}

		@Override
		public void optOutAll(boolean out)
		{
			calls.add("optOutAll=" + out);
		}
	}

	/** Everything the panel draws from; change a field, then {@link #show}. */
	static final class Scene
	{
		final long now = System.currentTimeMillis();
		GroupSync.State sync = GroupSync.State.CONNECTED;
		final GroupRoster roster = new GroupRoster();
		List<String> members = NAMES;
		GroupOutbox.Switches switches = new GroupOutbox.Switches(true, true, true, true, true, true);
		long since;
		String self = SELF;
		GamePresence presence = GamePresence.UNKNOWN;
		GroupSettingsTab.View settings;
		boolean anyOn = true;
		SharingPrompt.Settings sharing = new SharingPrompt.Settings(KEY, true, true, true, false, false);

		GroupIronmanPanel.View view()
		{
			return new GroupIronmanPanel.View(sync, roster, GroupMembers.of(GROUP, members), new FakeItems(), switches,
				REGIONS, since, self, presence, settings, anyOn, sharing);
		}
	}

	final Recorder actions = new Recorder();
	final GroupIronmanPanel panel;
	final SwingDriver ui;

	/** Call on the Swing thread. */
	GimPanelFixture()
	{
		panel = new GroupIronmanPanel(itemManager(), new SkillIconManager(), mock(SpriteManager.class), actions);
		ui = new SwingDriver(panel);
	}

	GimPanelFixture show(Scene scene)
	{
		panel.render(scene.view());
		ui.layout(PANEL_WIDTH);
		return this;
	}

	/** Clicks a tab in the tab bar. */
	GimPanelFixture tab(String name)
	{
		ui.click(name);
		ui.layout(PANEL_WIDTH);
		return this;
	}

	/** The sidebar's content width, as RuneLite gives a plugin panel. */
	static final int PANEL_WIDTH = net.runelite.client.ui.PluginPanel.PANEL_WIDTH;

	// ---- Scenario state, as the harness's events leave it ----

	static Map<Integer, Integer> items(int... idQty)
	{
		return EventFixtures.items(idQty);
	}

	static MemberStats stats(int world, int region, int attack, int hitpoints, int hpNow)
	{
		Map<String, MemberStats.Level> skills = new LinkedHashMap<>();
		skills.put("Attack", new MemberStats.Level(attack, attack));
		skills.put("Strength", new MemberStats.Level(attack - 2, attack - 2));
		skills.put("Defence", new MemberStats.Level(attack - 5, attack - 5));
		skills.put("Hitpoints", new MemberStats.Level(hitpoints, hpNow));
		skills.put("Prayer", new MemberStats.Level(70, 64));
		skills.put("Mining", new MemberStats.Level(61, 61));
		skills.put("Herblore", new MemberStats.Level(55, 55));
		skills.put("Fishing", new MemberStats.Level(68, 68));
		skills.put("Slayer", new MemberStats.Level(72, 72));
		return new MemberStats(skills, 87, 50, world, region, 3222, 3218, 0);
	}

	/** Levels as a hiscore lookup returns them, for a member without the plugin. */
	static Map<String, Integer> hiscoreLevels()
	{
		Map<String, Integer> out = new TreeMap<>();
		String[] skills = {"Attack", "Strength", "Defence", "Ranged", "Prayer", "Magic", "Runecraft", "Construction",
			"Hitpoints", "Agility", "Herblore", "Thieving", "Crafting", "Fletching", "Slayer", "Hunter", "Mining",
			"Smithing", "Fishing", "Cooking", "Firemaking", "Woodcutting", "Farming"};
		int level = 40;
		for (String s : skills)
		{
			out.put(s, level);
			level = level >= 78 ? 41 : level + 3;
		}
		return out;
	}

	/** Two members online with the plugin, one online without it (hiscores only), one offline. */
	static Scene groupScene()
	{
		Scene s = new Scene();
		s.roster.heard(SELF, s.now);
		s.roster.putStats(SELF, stats(330, 12850, 80, 85, 85));
		s.roster.putCarried(SELF, new GroupCodec.Carried(items(FakeItems.SHARK, 12, FakeItems.PRAYER_POT_4, 4),
			items(FakeItems.WHIP, 1)));
		s.roster.heard(MOSS, s.now - MIN);
		s.roster.putStats(MOSS, stats(330, 12338, 75, 79, 52));
		s.roster.hiscores().restore(WREN, s.now - 2 * HOUR, hiscoreLevels());
		Map<String, Integer> channel = new TreeMap<>();
		channel.put(SELF, 330);
		channel.put(MOSS, 330);
		channel.put(WREN, 416);
		s.presence = GamePresence.read(GroupMembers.of(GROUP, NAMES), channel, Collections.emptyMap());
		return s;
	}

	/** Group storage: one visit by a member (withdrew 14 sharks, deposited coins), then the snapshot. */
	static Scene storageScene()
	{
		Scene s = groupScene();
		s.roster.putStorage(new StorageChange(s.now - 3 * HOUR, WREN, items(FakeItems.LOBSTER, 6), items(),
			items(FakeItems.SHARK, 40, FakeItems.COINS, 50_000)));
		s.roster.putStorage(new StorageChange(s.now - 20 * MIN, MOSS, items(FakeItems.SHARK, 14),
			items(FakeItems.COINS, 250_000), items(FakeItems.SHARK, 26, FakeItems.COINS, 300_000,
				FakeItems.PRAYER_POT_4, 8)));
		return s;
	}

	static Scene bankScene()
	{
		Scene s = groupScene();
		s.roster.putBank(SELF, new BankSnapshot(s.now - 5 * MIN, items(FakeItems.SHARK, 120, FakeItems.COAL, 400,
			FakeItems.GUAM_LEAF, 30)));
		s.roster.putBank(MOSS, new BankSnapshot(s.now - HOUR, items(FakeItems.WHIP, 1, FakeItems.LOBSTER, 80)));
		return s;
	}

	/** The Log: a drop, a death, two level-ups, a storage visit and a login, all within the last few hours. */
	static Scene logScene()
	{
		Scene s = storageScene();
		DropLog feed = s.roster.feed();
		feed.add(GroupEvent.drop(s.now - 4 * HOUR, WREN, "Vorkath", items(FakeItems.WHIP, 1)));
		feed.add(GroupEvent.of(GroupEvent.Type.DEATH, s.now - 3 * HOUR, TIN).location("Lumbridge").number(1).build());
		feed.add(GroupEvent.of(GroupEvent.Type.LEVEL_UP, s.now - 2 * HOUR, MOSS).text("Herblore").number(56).build());
		feed.add(GroupEvent.of(GroupEvent.Type.LEVEL_UP, s.now - 90 * MIN, SELF).text("Slayer").number(73).build());
		feed.add(GroupEvent.of(GroupEvent.Type.STORAGE_DEPOSIT, s.now - 3 * HOUR, WREN)
			.items(items(FakeItems.PRAYER_POT_4, 8)).build());
		feed.add(GroupEvent.of(GroupEvent.Type.STORAGE_WITHDRAWAL, s.now - 20 * MIN, MOSS)
			.items(items(FakeItems.SHARK, 14)).build());
		feed.add(GroupEvent.of(GroupEvent.Type.STORAGE_DEPOSIT, s.now - 20 * MIN, MOSS)
			.items(items(FakeItems.COINS, 250_000)).build());
		feed.add(GroupEvent.of(GroupEvent.Type.ONLINE, s.now - 10 * MIN, MOSS).text("online").world(330).build());
		return s;
	}

	// ---- Group settings, signed the way members' clients sign them ----

	/** Every member's client, each with its own key, having heard each other. */
	static final class Governance
	{
		final Map<String, GroupSigner.Identity> ids = new LinkedHashMap<>();
		final Map<String, GroupGovernance> clients = new LinkedHashMap<>();

		Governance()
		{
			for (String name : NAMES)
			{
				GroupSigner.Identity id = GroupSigner.Identity.create();
				ids.put(name, id);
				GroupGovernance g = new GroupGovernance(id);
				g.group(GROUP);
				clients.put(name, g);
			}
			for (GroupGovernance g : clients.values())
			{
				for (Map.Entry<String, GroupSigner.Identity> e : ids.entrySet())
				{
					g.receiveMember(e.getKey(), e.getValue().publicKey(), prefsOf(e.getKey()));
				}
			}
		}

		/** Tin opted out of posting deaths; the others post everything. */
		static MemberPrefs prefsOf(String name)
		{
			if (TIN.equals(name))
			{
				return new MemberPrefs(false, java.util.EnumSet.of(GroupEvent.Type.DEATH),
					java.util.EnumSet.noneOf(GroupEvent.Type.class));
			}
			return MemberPrefs.NONE;
		}

		/** {@code leader} claims, makes {@code editor} an editor, saves a level threshold; everyone hears it. */
		Governance led(String leader, String editor, long now)
		{
			GroupGovernance g = clients.get(leader);
			g.claim(leader, now - 2 * HOUR);
			if (editor != null)
			{
				g.setEditor(leader, editor, true, now - HOUR);
			}
			Map<String, Long> values = GroupSettingsSchema.seed(DEFAULTS);
			values.put("eventLevelMin", 70L);
			g.save(leader, values, now - 30 * MIN);
			for (GroupGovernance other : clients.values())
			{
				if (other != g)
				{
					other.receiveRoles(g.roles());
					other.receiveSettings(g.settings());
				}
			}
			return this;
		}

		/** The Settings tab's view on {@code self}'s client, as GroupIronmanPlugin#settingsView builds it. */
		GroupSettingsTab.View view(String self, boolean syncing)
		{
			GroupGovernance g = clients.get(self);
			Map<String, Long> current = new TreeMap<>(g.settings() != null ? g.settings().values
				: GroupSettingsSchema.seed(DEFAULTS));
			return GroupSettingsTab.View.of(g, self, NAMES, current, prefsOf(self), syncing);
		}
	}

	// ---- Item icons without a game cache ----

	private static ItemManager itemManager()
	{
		FakeItems names = new FakeItems();
		ItemManager items = mock(ItemManager.class);
		when(items.getImage(anyInt(), anyInt(), anyBoolean())).thenAnswer(inv ->
			placeholder(names.name(inv.getArgument(0)), inv.getArgument(1), inv.getArgument(2)));
		when(items.getImage(anyInt())).thenAnswer(inv -> placeholder(names.name(inv.getArgument(0)), 1, false));
		return items;
	}

	/**
	 * A 36x32 tile with the item's initials, and its stack number as the game
	 * draws one. Never marked loaded: AsyncBufferedImage would then hand its
	 * repaint to a client thread there is none of.
	 */
	static AsyncBufferedImage placeholder(String name, int quantity, boolean stackable)
	{
		AsyncBufferedImage img = new AsyncBufferedImage(null, 36, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			int hue = Math.abs(name.hashCode());
			g.setColor(Color.getHSBColor((hue % 360) / 360f, 0.45f, 0.55f));
			g.fillRoundRect(4, 4, 28, 26, 8, 8);
			g.setColor(Color.WHITE);
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
			String initials = initials(name);
			int w = g.getFontMetrics().stringWidth(initials);
			g.drawString(initials, 18 - w / 2, 22);
			if (stackable && quantity > 1)
			{
				g.setColor(Color.YELLOW);
				g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
				g.drawString(quantity >= 100_000 ? quantity / 1000 + "K" : String.valueOf(quantity), 1, 9);
			}
		}
		finally
		{
			g.dispose();
		}
		return img;
	}

	static String initials(String name)
	{
		StringBuilder out = new StringBuilder();
		for (String word : name.split("[^A-Za-z]+"))
		{
			if (!word.isEmpty() && out.length() < 2)
			{
				out.append(Character.toUpperCase(word.charAt(0)));
			}
		}
		return out.length() == 0 ? "?" : out.toString();
	}
}
