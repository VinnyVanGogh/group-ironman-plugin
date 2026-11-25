package com.vinnyvangogh.groupironman;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import javax.swing.Icon;
import lombok.extern.slf4j.Slf4j;
import javax.swing.SwingUtilities;
import net.runelite.api.SpriteID;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.AsyncBufferedImage;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.Skill;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;

/**
 * The group side panel: a sync header, then five tabs (Group, Banks,
 * Storage, Find, Log). Layout after Quest Helper's panel
 * (Zoinkwiz/quest-helper {@code QuestHelperPanel}): an {@link IconTextField}
 * search bar, views swapped with a {@link CardLayout}, and sections that
 * collapse from their header. Items are icon grids from {@link ItemManager},
 * skills an icon grid from {@link SkillIconManager} in the skills tab's
 * order.
 *
 * <p>It draws what {@link PanelModel} builds; nothing here decides what is
 * shown. Only the visible tab is rebuilt on a redraw.
 */
@Slf4j
class GroupIronmanPanel extends PluginPanel
{
	/** What the buttons in the header, and in the Settings tab, do. */
	interface Actions extends GroupSettingsTab.Actions, GearTab.Actions
	{
		/** Compare tab: hand the group to Player Compare and show its group view. */
		default void openCompare()
		{
		}

		/** Shopping tab: hand the group to Group Shopping and show its lists. */
		default void openShopping()
		{
		}

		void join();

		void switchParty();

		void leave();

		void newKey();

		/** The group key for "Copy group key", or null without one. */
		String groupKey();

		/** Saves a pasted key; false when the text holds none. */
		boolean pasteKey(String text);

		/** Opens GIM Utilities' own page in RuneLite's settings. */
		void openSettings();

		/** Ticks Sync with my group and every Share switch ({@link SharingPrompt#SHARING_KEYS}). */
		void turnOnSharing();

		/** The quick-settings switches' values now, in {@link QuickSettings#ITEMS} order. */
		boolean[] quickValues();

		/** A quick-settings click: writes that config key, so RuneLite's settings list follows. */
		void setQuick(String key, boolean on);
	}

	/** Everything a redraw needs, captured on the client side. */
	static final class View
	{
		final GroupSync.State sync;
		final GroupRoster group;
		final GroupMembers members;
		final ItemLookup items;
		final GroupOutbox.Switches switches;
		final RegionNames regions;
		/** When this player last played before this session, 0 when not known. */
		final long since;
		final String self;
		/** Who is online, plugin or not, as the game's Group channel shows it. */
		final GamePresence presence;
		/** The Settings tab's view, or null before the plugin has started its group settings. */
		final GroupSettingsTab.View settings;
		/** Any share or sync feature on; false leads the panel with the "everything is off" notice. */
		final boolean anyOn;
		/** What the "Turn on sharing" card reads. */
		final SharingPrompt.Settings sharing;

		View(GroupSync.State sync, GroupRoster group, GroupMembers members, ItemLookup items,
			GroupOutbox.Switches switches, RegionNames regions, long since, String self, GamePresence presence,
			GroupSettingsTab.View settings, boolean anyOn, SharingPrompt.Settings sharing)
		{
			this.anyOn = anyOn;
			this.sharing = sharing;
			this.presence = presence;
			this.settings = settings;
			this.sync = sync;
			this.group = group;
			this.members = members;
			this.items = items;
			this.switches = switches;
			this.regions = regions;
			this.since = since;
			this.self = self;
		}

		/** Members' shared boss bests, quests and diaries; set by the plugin, empty until it does. */
		ProgressView progress = ProgressView.EMPTY;
		/** Player Compare has answered: the Compare tab opens its group view, else it shows the install card. */
		boolean compareInstalled;
		/** Group Shopping has answered: the Shopping tab opens its lists, else it shows the install card. */
		boolean shoppingInstalled;
		/** The Gear tab's view, or null before the plugin has started. */
		GearTab.View gear;
	}

	private static final int GRID_COLUMNS = 5;
	/** Bigger grids get a "use the filter" line instead: hundreds of icons in a side panel help nobody. */
	private static final int MAX_GRID_ITEMS = 200;
	private static final Color HP = new Color(0xB0, 0x2E, 0x26);
	private static final Color PRAYER = new Color(0x2E, 0x8B, 0xB0);
	/** The ring and "ON" label of a member whose prayer is switched on. */
	private static final Color PRAYER_ON = new Color(0xFF, 0xE0, 0x4A);
	private static final Color POISON = new Color(0x3C, 0xC8, 0x3C);
	private static final Color VENOM = new Color(0xB0, 0x5C, 0xE0);
	private static final Color DISEASE = new Color(0xD8, 0xB0, 0x40);
	private static final Color RUN = new Color(0xC8, 0xA0, 0x2A);
	private static final Color SPEC = new Color(0x3A, 0x9A, 0x4A);
	static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR.darker();
	/** At most one data-driven redraw of the Banks or Storage tab per this long; a click or keystroke is never held. */
	static final long GRID_REDRAW_GAP_MS = 1000;
	/** Rows drawn at once; 2,000 labels in a side panel help nobody, the search and filters find the rest. */
	private static final int MAX_LOG_ROWS = 200;
	/** A row's text width, leaving room for its item or skill icon. */
	private static final int LOG_TEXT_WIDTH = 140;
	private static final String ALL_MEMBERS = "All members";

	private final ItemManager itemManager;
	private final SkillIconManager skillIcons;
	/** The minimap orbs' own icons, by bar label; filled in as the sprites load. */
	private final Map<String, BufferedImage> orbIcons = new ConcurrentHashMap<>();
	/** The worn tab's empty-slot silhouettes, by equipment slot; filled in as the sprites load. */
	private final Map<Integer, BufferedImage> slotIcons = new ConcurrentHashMap<>();
	private final Actions actions;
	private final GroupSettingsTab settingsTab;
	private final GearTab gearTab;

	private final JPanel header = new Column();
	private final CardLayout cards = new CardLayout();
	private final JPanel views = new JPanel(cards);
	private final Map<String, JPanel> tabs = new LinkedHashMap<>();
	private final IconTextField bankFilter = searchBar("Filter banks by item name");
	private final IconTextField storageFilter = searchBar("Filter storage by item name");
	private final IconTextField findQuery = searchBar("Item name, e.g. guam or ppot");
	private final IconTextField logQuery = searchBar("Search the log: member, item, place or text");
	/** Ticked category chips; none ticked shows every category. */
	private final Set<GroupEvent.Category> logCategories = EnumSet.noneOf(GroupEvent.Category.class);
	private final JComboBox<String> logMember = new JComboBox<>();
	private final JComboBox<ActivityLog.Range> logRange = new JComboBox<>(ActivityLog.Range.values());
	private List<String> logMembers = Collections.emptyList();
	/** Set while the member picker is rebuilt, so its own events do not redraw mid-draw. */
	private boolean updatingMembers;
	/** Whether the header's quick-settings card is unfolded. */
	private boolean quickOpen = true;
	/** Member cards the player collapsed, by name. */
	private final Set<String> collapsed = new HashSet<>();
	/** Members whose run detail (wave-by-wave splits) is open on the Activities tab; closed by default. */
	private final Set<String> splitsOpen = new HashSet<>();
	/** Bank cards folded in the Banks tab, kept apart from the Group tab's. */
	private final BankFolds bankFolds = new BankFolds();
	private String current = "Group";
	/** The Activities tab's All / Bossing / Minigames pill. */
	private ActivityRows.Filter activityFilter = ActivityRows.Filter.ALL;
	/** A live activity's line on its member's card, in the soft gold the plugin uses for live status. */
	private static final Color ACTIVITY = new Color(0xE0, 0xB0, 0x40);
	/** What the last Copy/Paste key press did, shown under the buttons for a while. */
	private String keyNote;
	/** What the header on screen shows; an update that would draw the same leaves it alone. */
	private String headerDrawn;
	private long keyNoteAt;
	private final SharingPrompt sharing = new SharingPrompt();
	private volatile View view;

	// Banks and Storage: models built off the Swing thread, drawn in place, at most once a second per tab.
	private final RedrawCoalescer.Scheduler background;
	private final Executor edt;
	private final RedrawCoalescer banksRedraw;
	private final RedrawCoalescer storageRedraw;
	private final BanksTabView banksView;
	private final StorageTabView storageView;
	/** The filters' text, read off the Swing thread by the model builders. */
	private volatile String bankQuery = "";
	private volatile String storageQuery = "";
	/** The category pills and the Banks tab's "Show all", likewise read by the model builders. */
	private volatile ItemCategory bankCategory = ItemCategory.ALL;
	private volatile ItemCategory storageCategory = ItemCategory.ALL;
	private volatile boolean bankShowAll;
	/** Find's quick-find dropdowns: category, sub-type, and who or where. */
	private final JComboBox<FindQuick.Kind> findKind = new JComboBox<>(FindQuick.Kind.values());
	private final JComboBox<String> findSub = new JComboBox<>();
	private final JComboBox<String> findHolder = new JComboBox<>();
	private List<String> findHolders = Collections.emptyList();
	/** Set while the Find dropdowns are filled in code, so their own events do not redraw mid-update. */
	private boolean updatingFind;
	/** The last models handed to the Swing thread; null banks is "bank sharing is off". */
	private volatile List<PanelModel.BankCard> lastBanks;
	private volatile boolean banksHanded;
	private volatile PanelModel.StorageView lastStorage;
	private volatile boolean lastStorageSharing;
	/** Banks/Storage models built and drawn: tests check what a sync costs. */
	volatile int gridModels;
	volatile int gridDraws;

	/** Tests: everything runs at once on the caller's thread, so a test reads the tab straight after. */
	GroupIronmanPanel(ItemManager itemManager, SkillIconManager skillIcons, SpriteManager sprites, Actions actions)
	{
		this(itemManager, skillIcons, sprites, actions, RedrawCoalescer.DIRECT, Runnable::run);
	}

	/**
	 * @param background where the Banks and Storage models are built (sorting, filtering, value totals), never the
	 *                   Swing thread: the game's clicks wait behind it
	 * @param edt        hands the finished model to the Swing thread
	 */
	GroupIronmanPanel(ItemManager itemManager, SkillIconManager skillIcons, SpriteManager sprites, Actions actions,
		RedrawCoalescer.Scheduler background, Executor edt)
	{
		this.background = background;
		this.edt = edt;
		this.banksRedraw = new RedrawCoalescer(GRID_REDRAW_GAP_MS, System::currentTimeMillis, background,
			() -> safely(() -> buildBanks(false)));
		this.storageRedraw = new RedrawCoalescer(GRID_REDRAW_GAP_MS, System::currentTimeMillis, background,
			() -> safely(() -> buildStorage(false)));
		loadOrbIcon(sprites, "HP", SpriteID.MINIMAP_ORB_HITPOINTS_ICON);
		loadOrbIcon(sprites, "Prayer", SpriteID.MINIMAP_ORB_PRAYER_ICON);
		loadOrbIcon(sprites, "Run", SpriteID.MINIMAP_ORB_RUN_ICON);
		loadOrbIcon(sprites, "Spec", SpriteID.MINIMAP_ORB_SPECIAL_ICON);
		for (Map.Entry<Integer, Integer> slot : WornSlotIcons.SPRITES.entrySet())
		{
			sprites.getSpriteAsync(slot.getValue(), 0, image ->
			{
				BufferedImage light = WornSlotIcons.lighten(image);
				if (light != null)
				{
					slotIcons.put(slot.getKey(), light);
				}
				SwingUtilities.invokeLater(this::redraw);
			});
		}
		this.itemManager = itemManager;
		this.skillIcons = skillIcons;
		this.actions = actions;
		this.settingsTab = new GroupSettingsTab(actions, this::redraw);
		this.gearTab = new GearTab(actions, this::redraw);
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(8, 8, 8, 8));

		JPanel top = new Column();
		top.add(header);
		top.add(tabBar("Group", "Banks", "Storage", "Find", "Log", "Activities", "Compare", "Shopping", "Gear", "Settings"));
		add(top, BorderLayout.NORTH);

		addTab("Group", null);
		addTab("Banks", gridControls(bankFilter, c -> bankCategory = c, showAllToggle()));
		addTab("Storage", gridControls(storageFilter, c -> storageCategory = c, null));
		addTab("Find", findControls());
		addTab("Log", logControls());
		addTab("Activities", activityFilters());
		addTab("Compare", null);
		addTab("Shopping", null);
		addTab("Gear", null);
		addTab("Settings", null);
		add(views, BorderLayout.CENTER);
		banksView = new BanksTabView(tabs.get("Banks"), bankFolds, this::paintCell, GRID_COLUMNS, MAX_GRID_ITEMS,
			() -> applyGrid("Banks", () -> drawBanks(lastBanks, view), 0));
		storageView = new StorageTabView(tabs.get("Storage"), this::paintCell, GRID_COLUMNS, MAX_GRID_ITEMS);
	}

	/** The search bar stays put above the content, so typing in it never loses focus to a rebuild. */
	private void addTab(String name, JComponent searchBar)
	{
		JPanel wrap = new JPanel(new BorderLayout());
		if (searchBar != null)
		{
			wrap.add(searchBar, BorderLayout.NORTH);
		}
		JPanel content = new Column();
		// Held at the top: in CENTER a short tab (one filtered item) was stretched to the
		// panel's height and its grid drifted to the middle (Board's test, 2026-11-04).
		JPanel top = new JPanel(new BorderLayout());
		top.add(content, BorderLayout.NORTH);
		wrap.add(top, BorderLayout.CENTER);
		tabs.put(name, content);
		views.add(wrap, name);
	}

	/** Must be called on the Swing thread. */
	void render(View view)
	{
		this.view = view;
		// A sync on the Banks or Storage tab: coalesced, built off this thread, drawn only if it changed.
		// A hidden Banks or Storage tab does no work at all; it is built once when shown.
		RedrawCoalescer grid = "Banks".equals(current) ? banksRedraw : "Storage".equals(current) ? storageRedraw
			: null;
		if (grid == null)
		{
			redraw();
			return;
		}
		drawHeader(view);
		grid.request();
	}

	/** A click, keystroke or tab switch: drawn now. */
	private void redraw()
	{
		View v = view;
		if (v == null)
		{
			return;
		}
		drawHeader(v);
		if ("Banks".equals(current) || "Storage".equals(current))
		{
			refreshGrid();
			return;
		}
		JPanel tab = tabs.get(current);
		if (settingsUnchanged(tab, v))
		{
			return;
		}
		tab.removeAll();
		try
		{
			drawCurrent(tab, v, System.currentTimeMillis());
		}
		catch (RuntimeException | AssertionError e)
		{
			drawFailed(tab, current, e);
		}
		tab.revalidate();
		tab.repaint();
	}

	/** A tab that fails to draw says so instead of going blank (the Group tab did, 2026-11-04). */
	private void drawFailed(JPanel tab, String name, Throwable e)
	{
		log.warn("GIM Utilities: drawing the {} tab failed", name, e);
		tab.removeAll();
		empty(tab, "Could not draw this tab.", e.getClass().getSimpleName() + ": " + e.getMessage()
			+ ". Please report it with the RuneLite log.");
		tab.revalidate();
		tab.repaint();
	}

	/** Builds the shown Banks or Storage tab's model now (off this thread) and draws it even if unchanged. */
	private void refreshGrid()
	{
		bankQuery = bankFilter.getText();
		storageQuery = storageFilter.getText();
		boolean banks = "Banks".equals(current);
		background.schedule(() -> safely(() ->
		{
			if (banks)
			{
				buildBanks(true);
			}
			else
			{
				buildStorage(true);
			}
		}), 0);
	}

	/** A model builder off the Swing thread logs its failure: an executor would swallow it. */
	private static void safely(Runnable task)
	{
		try
		{
			task.run();
		}
		catch (RuntimeException e)
		{
			log.warn("GIM Utilities: building a panel tab failed", e);
		}
	}

	/** Off the Swing thread: the Banks model, handed over only when it differs from the last ({@code force}: always). */
	private void buildBanks(boolean force)
	{
		View v = view;
		if (v == null)
		{
			return;
		}
		long start = System.nanoTime();
		List<PanelModel.BankCard> banks = v.switches.bank
			? PanelModel.banks(v.group, v.items, new GridFilter(bankQuery, bankCategory, bankShowAll),
				System.currentTimeMillis()) : null;
		gridModels++;
		if (!force && banksHanded && PanelModel.BankCard.same(banks, lastBanks))
		{
			return;
		}
		lastBanks = banks;
		banksHanded = true;
		long modelNanos = System.nanoTime() - start;
		edt.execute(() -> applyGrid("Banks", () -> drawBanks(banks, v), modelNanos));
	}

	/** Off the Swing thread: the Storage model, handed over only when it differs from the last. */
	private void buildStorage(boolean force)
	{
		View v = view;
		if (v == null)
		{
			return;
		}
		long start = System.nanoTime();
		PanelModel.StorageView s = PanelModel.storage(v.group, v.items, v.self,
			new GridFilter(storageQuery, storageCategory, false), System.currentTimeMillis());
		boolean sharing = v.switches.storage;
		gridModels++;
		if (!force && s.sameAs(lastStorage) && sharing == lastStorageSharing)
		{
			return;
		}
		lastStorage = s;
		lastStorageSharing = sharing;
		long modelNanos = System.nanoTime() - start;
		edt.execute(() -> applyGrid("Storage", () -> storageView.apply(s, sharing), modelNanos));
	}

	/** On the Swing thread: draws a built Banks or Storage model in place, timing it. A hidden tab is skipped. */
	private void applyGrid(String name, java.util.function.IntSupplier draw, long modelNanos)
	{
		if (!name.equals(current) || view == null)
		{
			return;
		}
		long start = System.nanoTime();
		int touched;
		try
		{
			touched = draw.getAsInt();
		}
		catch (RuntimeException | AssertionError e)
		{
			banksView.clear();
			storageView.clear();
			drawFailed(tabs.get(name), name, e);
			touched = -1;
		}
		gridDraws++;
		log.debug("GIM Utilities: {} redraw took {} us on the Swing thread, {} cells changed (model {} us off it)",
			name, (System.nanoTime() - start) / 1000, touched, modelNanos / 1000);
	}

	private void drawCurrent(JPanel tab, View v, long now)
	{
		switch (current)
		{
			case "Find":
				drawFind(tab, v, now);
				break;
			case "Log":
				drawLog(tab, v, now);
				break;
			case "Activities":
				drawActivities(tab, v, now);
				break;
			case "Compare":
				drawCompare(tab, v);
				break;
			case "Shopping":
				drawShopping(tab, v);
				break;
			case "Gear":
				drawGear(tab, v);
				break;
			case "Settings":
				drawSettings(tab, v, now);
				break;
			default:
				drawGroup(tab, v, now);
				break;
		}
	}

	/** The Settings tab is on screen and shows the same as before: rebuilding would lose a half-typed number. */
	private boolean settingsUnchanged(JPanel tab, View v)
	{
		if (tab.getComponentCount() == 0)
		{
			return false;
		}
		if (!"Settings".equals(current))
		{
			return false;
		}
		return v.settings == null || !settingsTab.changed(v.settings);
	}

	private void drawCompare(JPanel tab, View v)
	{
		// Leaderboard, Players, Banked, Group vs group and Item are drawn by Player Compare's "Group" source.
		if (!v.compareInstalled)
		{
			empty(tab, "Compare", PlayerCompare.INSTALL_HINT + " (from the Plugin Hub).");
			return;
		}
		tab.add(heading("Compare"));
		tab.add(text("Your group's levels, bosses, banked XP and gear open in Player Compare.", MUTED));
		tab.add(Ui.button("Open group compare", "Show the group in Player Compare's panel", actions::openCompare));
	}

	private void drawGear(JPanel tab, View v)
	{
		if (v.gear == null)
		{
			empty(tab, "Gear checks are starting.", "Open this tab again in a moment.");
			return;
		}
		gearTab.draw(tab, v.gear);
	}

	private void drawShopping(JPanel tab, View v)
	{
		// Lists, presets, requests and "Path to" are drawn by the Group Shopping plugin.
		if (!v.shoppingInstalled)
		{
			empty(tab, "Shopping", GroupShopping.INSTALL_HINT + " (from the Plugin Hub).");
			return;
		}
		tab.add(heading("Shopping"));
		tab.add(text("Your group's shopping lists and requests open in Group Shopping.", MUTED));
		tab.add(Ui.button("Open shopping lists", "Show the group's lists in Group Shopping's panel",
			actions::openShopping));
	}

	private void drawSettings(JPanel tab, View v, long now)
	{
		if (v.settings == null)
		{
			empty(tab, "Group settings are starting.", "Open this tab again in a moment.");
			return;
		}
		settingsTab.draw(tab, v.settings, now);
	}

	private JPanel tabBar(String... names)
	{
		// Two rows of three: six tabs in one row cut their names off at the panel's width.
		JPanel bar = new JPanel(new GridLayout(0, 3, 2, 2));
		bar.setBorder(new EmptyBorder(6, 0, 6, 0));
		ButtonGroup group = new ButtonGroup();
		for (String name : names)
		{
			JToggleButton b = Ui.toggle(name, name.equals(current), 2);
			b.addActionListener(e ->
			{
				current = name;
				cards.show(views, name);
				redraw();
			});
			group.add(b);
			bar.add(b);
		}
		return bar;
	}

	private IconTextField searchBar(String tooltip)
	{
		IconTextField field = new IconTextField();
		field.setIcon(IconTextField.Icon.SEARCH);
		field.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
		field.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		field.setToolTipText(tooltip);
		field.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				redraw();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				redraw();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				redraw();
			}
		});
		return field;
	}

	// Header: sync status and the one button that moves it forward.

	private void drawHeader(View v)
	{
		long now = System.currentTimeMillis();
		SyncHeader h = SyncHeader.of(v.sync, v.group.othersOnline(v.self, now), v.members);
		String note = keyNote != null && now - keyNoteAt < 30_000 ? keyNote : null;
		SharingPrompt.Card card = v.sharing == null ? null : sharing.card(v.sharing);
		boolean[] quick = actions.quickValues();
		// A header that would read the same is left alone: rebuilding its buttons on every member update was
		// the lag with the Settings tab open (that tab is skipped when unchanged, the header was not).
		String signature = String.join("|", String.valueOf(SidebarButton.offNotice(v.anyOn)), h.title,
			String.valueOf(h.detail), String.valueOf(h.ok), String.valueOf(h.buttons), String.valueOf(note),
			card == null ? "-" : card.sharing + "," + v.sharing.key,
			String.valueOf(v.group.strangers()), quickOpen + Arrays.toString(quick));
		if (signature.equals(headerDrawn) && header.getComponentCount() > 0)
		{
			return;
		}
		headerDrawn = signature;
		header.removeAll();
		if (SidebarButton.offNotice(v.anyOn))
		{
			header.add(offNotice());
		}
		header.add(Ui.label(h.title, FontManager.getRunescapeBoldFont(),
			h.ok ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.BRAND_ORANGE));
		if (h.detail != null)
		{
			header.add(text(h.detail, MUTED));
		}
		// Two per row: a single FlowLayout row never wraps inside the BoxLayout header, so a
		// third button (Leave) was cut off past the panel's edge.
		JPanel buttons = new JPanel(new GridLayout(0, 2, 2, 2));
		for (SyncHeader.Button b : h.buttons)
		{
			buttons.add(Ui.button(b.label, b.tooltip, () -> press(b)));
		}
		header.add(buttons);
		if (note != null)
		{
			header.add(text(note, Color.WHITE));
		}
		drawSharingCard(v.sharing);
		drawQuickSettings(quick);
		for (String stranger : v.group.strangers())
		{
			header.add(text("Not in your group: " + stranger + " (ignored)", ColorScheme.PROGRESS_ERROR_COLOR));
		}
		header.revalidate();
		header.repaint();
	}

	/** A foldable card of the most-used switches and "All settings"; each click writes the real config key. */
	private void drawQuickSettings(boolean[] values)
	{
		JPanel box = Ui.card();
		box.add(Ui.wide(Ui.button((quickOpen ? "▾ " : "▸ ") + QuickSettings.TITLE,
			"Show or hide the switches", () ->
			{
				quickOpen = !quickOpen;
				redraw();
			})));
		if (quickOpen)
		{
			QuickSettings.draw(box, values, actions::setQuick);
			box.add(Ui.wide(Ui.button(QuickSettings.ALL, "Opens GIM Utilities' page in RuneLite's settings",
				actions::openSettings)));
		}
		header.add(box);
	}

	/** Every switch is off: say so first, with a way to the settings, so the panel never looks broken. */
	private JPanel offNotice()
	{
		JPanel box = new Column();
		box.setBorder(new EmptyBorder(0, 0, 8, 0));
		JLabel line = text(SidebarButton.OFF_TEXT, ColorScheme.BRAND_ORANGE);
		line.setFont(FontManager.getRunescapeBoldFont());
		box.add(line);
		box.add(Ui.wide(Ui.button(SidebarButton.OFF_BUTTON, null, actions::openSettings)));
		return box;
	}

	/** "Turn on sharing" after a pasted key, or while sync is on with every Share switch off. Nothing without a click. */
	private void drawSharingCard(SharingPrompt.Settings s)
	{
		SharingPrompt.Card card = s == null ? null : sharing.card(s);
		if (card == null)
		{
			return;
		}
		JPanel box = Ui.card();
		box.add(Ui.label("Share with your group?", FontManager.getRunescapeBoldFont(), null));
		box.add(text(SharingPrompt.TEXT, MUTED));
		JPanel buttons = new JPanel(new GridLayout(0, 1, 2, 2));
		buttons.setOpaque(false);
		if (card.sharing)
		{
			buttons.add(cardButton("Turn on sharing", "Ticks Sync with my group and every Share switch",
				actions::turnOnSharing));
		}
		buttons.add(cardButton("Not now", "Hides this until the group key changes", () -> sharing.notNow(s.key)));
		box.add(buttons);
		header.add(box);
	}

	private JButton cardButton(String label, String tooltip, Runnable action)
	{
		return Ui.button(label, tooltip, () ->
		{
			action.run();
			redraw();
		});
	}

	private void press(SyncHeader.Button b)
	{
		switch (b)
		{
			case JOIN:
				actions.join();
				break;
			case SWITCH:
				actions.switchParty();
				break;
			case LEAVE:
				actions.leave();
				break;
			case CREATE_KEY:
			case NEW_KEY:
				actions.newKey();
				break;
			case COPY_KEY:
				String key = actions.groupKey();
				if (key != null)
				{
					Ui.copy(key);
					keyNote = "Key copied. Send it to your group, e.g. in Discord. Anyone with it can join.";
					keyNoteAt = System.currentTimeMillis();
				}
				redraw();
				break;
			case PASTE_KEY:
				pasteKey();
				break;
			default:
				break;
		}
	}

	/** "Paste group key": saves it, says so, and opens the "Turn on sharing" card for it. */
	private void pasteKey()
	{
		boolean saved = actions.pasteKey(clipboardText());
		keyNote = saved
			? "Key saved. Joining your group's sync."
			: "Your clipboard has no group key. Copy the key a member sent, then press Paste again.";
		keyNoteAt = System.currentTimeMillis();
		if (saved)
		{
			sharing.pasted(actions.groupKey());
		}
		redraw();
	}

	/** Text on the computer's clipboard, or "" when there is none or it cannot be read. */
	private static String clipboardText()
	{
		try
		{
			Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
			return data instanceof String ? (String) data : "";
		}
		catch (Exception e)
		{
			return "";
		}
	}

	// Group tab: one card per member.

	private void drawGroup(JPanel tab, View v, long now)
	{
		List<PanelModel.MemberCard> cardsList = PanelModel.members(v.group, v.members.names(), v.regions, v.items,
			v.self, v.presence, now);
		if (cardsList.isEmpty())
		{
			empty(tab, "No members yet.", "Turn on 'Share stats' and 'Share inventory and equipment' in GIM Utilities'"
				+ " settings, then press 'Join group sync' above. Members appear once they do the same.");
			return;
		}
		boolean anyOffline = false;
		Map<String, ActivityState> activities = v.group.activities(now);
		for (PanelModel.MemberCard c : cardsList)
		{
			anyOffline |= !c.online && !c.you;
			tab.add(memberCard(c, activities.get(c.name)));
		}
		if (anyOffline)
		{
			tab.add(text("Offline members show what they had the last time you were both online.", MUTED));
		}
	}

	/** A member's card; {@code activity} is what they are doing in a tracked activity now, or null. */
	private JComponent memberCard(PanelModel.MemberCard c, ActivityState activity)
	{
		JPanel card = Ui.card();
		JPanel body = new Column();
		body.setOpaque(false);
		boolean open = !collapsed.contains(c.name);
		body.setVisible(open);

		card.add(foldHeader(c.name + (c.you ? " (you)" : ""), open, c.online || c.you ? Color.WHITE : MUTED, () ->
		{
			if (!collapsed.remove(c.name))
			{
				collapsed.add(c.name);
			}
		}));
		card.add(text(c.status, c.online ? ColorScheme.PROGRESS_COMPLETE_COLOR : MUTED));
		addActivity(card, activity);

		addStats(body, c);
		View current = view;
		if (current != null)
		{
			current.progress.cardLines(c.name, 3).forEach(line -> body.add(text(line, ACTIVITY)));
		}
		// Worn then inventory, small and side by side, in the game's own layouts.
		PanelModel.ItemGroup worn = null;
		PanelModel.ItemGroup inventory = null;
		for (PanelModel.ItemGroup g : c.items)
		{
			if ("Worn".equals(g.title))
			{
				worn = g;
			}
			else if ("Inventory".equals(g.title))
			{
				inventory = g;
			}
		}
		if (worn != null || inventory != null)
		{
			JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
			row.setOpaque(false);
			row.add(captioned("Worn", wornLayout(worn == null ? new ArrayList<>() : worn.items)));
			row.add(captioned("Inventory", inventoryLayout(inventory == null ? new ArrayList<>() : inventory.items)));
			body.add(row);
		}
		for (PanelModel.ItemGroup g : c.items)
		{
			if (g == worn || g == inventory)
			{
				continue;
			}
			body.add(text(g.title, MUTED));
			body.add(itemGrid(g.items));
		}
		if (c.charges != null)
		{
			body.add(text("Charges: " + c.charges, MUTED));
		}
		if (c.seedVault != null)
		{
			body.add(text(c.seedVault, MUTED));
		}
		card.add(body);
		return card;
	}

	/** "Fight Caves - wave 37/63 - solo" under the card's status, when the member is in an activity. */
	private static void addActivity(JPanel card, ActivityState activity)
	{
		if (activity != null)
		{
			card.add(text(activity.cardLine(), ACTIVITY));
			String detail = activity.waveLine(System.currentTimeMillis());
			if (!detail.isEmpty())
			{
				card.add(text(detail, ACTIVITY));
			}
		}
	}

	/** A card's clickable title with its fold arrow; {@code toggle} flips the card, then the tab redraws. */
	private JLabel foldHeader(String title, boolean open, Color color, Runnable toggle)
	{
		JLabel name = foldLabel(title, open, color);
		Ui.onPress(name, () ->
		{
			toggle.run();
			redraw();
		});
		return name;
	}

	/** A card title with its fold arrow, not yet clickable. */
	static JLabel foldLabel(String title, boolean open, Color color)
	{
		// A drawn arrow: the RuneScape font has no glyph for ▾/▸ and showed a box.
		JLabel name = new JLabel(title, new Arrow(open), SwingConstants.LEFT);
		name.setIconTextGap(4);
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(color);
		return name;
	}

	/** The orbs, where the numbers came from when not shared, and the skill grid. */
	private void addStats(JPanel body, PanelModel.MemberCard c)
	{
		boolean live = c.online || c.you;
		if (!c.bars.isEmpty())
		{
			JPanel bars = new JPanel(new GridLayout(1, c.bars.size(), 2, 0));
			bars.setOpaque(false);
			for (PanelModel.Bar b : c.bars)
			{
				// An assumed number (run and spec for a member without the plugin) is drawn dimmed.
				bars.add(new Orb(b, barColor(b.label), live && !b.assumed, orbIcons.get(b.label)));
			}
			body.add(bars);
		}
		for (int i = 0; i < c.effects.size(); i++)
		{
			String chip = c.effects.get(i);
			JLabel label = new JLabel(chip);
			label.setFont(FontManager.getRunescapeSmallFont());
			label.setForeground(chip.startsWith("Venom") ? VENOM : chip.startsWith("Disease") ? DISEASE : POISON);
			label.setToolTipText(i < c.effectNotes.size() ? c.effectNotes.get(i) : chip);
			body.add(label);
		}
		if (!c.pet.isEmpty())
		{
			JLabel pet = new JLabel("Pet: " + c.pet);
			pet.setFont(FontManager.getRunescapeSmallFont());
			pet.setForeground(MUTED);
			pet.setToolTipText(c.name + " has " + c.pet + " out");
			body.add(pet);
		}
		if (c.source != null)
		{
			body.add(text(c.source, MUTED));
		}
		if (!c.skills.isEmpty())
		{
			body.add(skillGrid(c.skills, live));
		}
	}

	private void loadOrbIcon(SpriteManager sprites, String label, int spriteId)
	{
		sprites.getSpriteAsync(spriteId, 0, image ->
		{
			orbIcons.put(label, image);
			SwingUtilities.invokeLater(this::redraw);
		});
	}

	private static JComponent captioned(String caption, JComponent content)
	{
		JPanel p = new Column();
		p.setOpaque(false);
		p.add(Ui.label(caption, FontManager.getRunescapeSmallFont(), MUTED));
		p.add(content);
		return p;
	}

	/**
	 * The worn-equipment tab's layout, 3 x 5: head; cape, neck, ammo; weapon, body,
	 * shield; legs; hands, feet, ring. Each item goes to the slot its stats name.
	 */
	private JComponent wornLayout(List<PanelModel.ItemCell> items)
	{
		JComponent[] cells = new JComponent[WORN_LAYOUT.length];
		// Slots come from the client-thread cache: ItemManager's stats may not be read on the
		// Swing thread (doing so blanked the whole Group tab, Board's test 2026-11-04).
		View v = view;
		for (PanelModel.ItemCell item : items)
		{
			int slot = v == null ? -1 : v.items.slot(item.id);
			int at = wornCell(slot);
			if (at < 0 || cells[at] != null)
			{
				at = firstFree(cells);
			}
			if (at >= 0)
			{
				cells[at] = smallItem(item);
			}
		}
		JPanel grid = new JPanel(new GridLayout(5, 3, 1, 1));
		grid.setOpaque(false);
		for (int i = 0; i < cells.length; i++)
		{
			grid.add(cells[i] != null ? cells[i] : WORN_LAYOUT[i] < 0 ? spacer() : emptyWornSlot(WORN_LAYOUT[i]));
		}
		return grid;
	}

	/** An empty worn slot with the game's silhouette for it, as the Worn Equipment tab draws it. */
	private JComponent emptyWornSlot(int slot)
	{
		return new SmallItem(null, null, -1, slot, slotIcons.get(slot));
	}

	/** Equipment slot index (EquipmentInventorySlot) per cell of the 3 x 5 layout; -1 is a gap. */
	private static final int[] WORN_LAYOUT = {
		-1, 0, -1,
		1, 2, 13,
		3, 4, 5,
		-1, 7, -1,
		9, 10, 12,
	};

	static int wornCell(int slot)
	{
		for (int i = 0; i < WORN_LAYOUT.length; i++)
		{
			if (slot >= 0 && WORN_LAYOUT[i] == slot)
			{
				return i;
			}
		}
		return -1;
	}

	private static int firstFree(JComponent[] cells)
	{
		for (int i = 0; i < cells.length; i++)
		{
			if (cells[i] == null && WORN_LAYOUT[i] >= 0)
			{
				return i;
			}
		}
		return -1;
	}

	/**
	 * The inventory tab's 4 x 7 grid. From a sender that shares slots each cell is that slot, empties included;
	 * from an older sender, one cell per item in order.
	 */
	private JComponent inventoryLayout(List<PanelModel.ItemCell> items)
	{
		JPanel grid = new JPanel(new GridLayout(7, 4, 1, 1));
		grid.setOpaque(false);
		int shown = 0;
		for (PanelModel.ItemCell item : items)
		{
			if (shown == GroupCodec.INVENTORY_SLOTS)
			{
				break;
			}
			grid.add(item.empty() ? emptySlot() : smallItem(item));
			shown++;
		}
		for (; shown < GroupCodec.INVENTORY_SLOTS; shown++)
		{
			grid.add(emptySlot());
		}
		return grid;
	}

	private JComponent smallItem(PanelModel.ItemCell item)
	{
		return new SmallItem(icon(item.id, item.quantity), item.tooltip(), item.inside, -1, null);
	}

	/**
	 * The item as the game draws a stack of it. The quantity picks the count
	 * variant (a bigger coin pile, more arrows): ItemManager passes it to the
	 * client's createItemSprite. {@code stackable} only turns the stack number
	 * on, so a single item shows none, as in the inventory.
	 */
	private AsyncBufferedImage icon(int id, long quantity)
	{
		return itemManager.getImage(id, (int) Math.min(Integer.MAX_VALUE, quantity), quantity > 1);
	}

	private static JComponent emptySlot()
	{
		return new SmallItem(null, null, -1, -1, null);
	}

	private static JComponent spacer()
	{
		JPanel p = new JPanel();
		p.setOpaque(false);
		p.setPreferredSize(new Dimension(SmallItem.W, SmallItem.H));
		return p;
	}

	private static Color barColor(String label)
	{
		switch (label)
		{
			case "HP":
				return HP;
			case "Prayer":
				return PRAYER;
			case "Run":
				return RUN;
			default:
				return SPEC;
		}
	}

	private JComponent skillGrid(List<PanelModel.SkillCell> skills, boolean live)
	{
		JPanel grid = new JPanel(new GridLayout(0, 3, 1, 1));
		grid.setOpaque(false);
		for (PanelModel.SkillCell s : skills)
		{
			JLabel cell = new JLabel(s.text());
			cell.setFont(FontManager.getRunescapeSmallFont());
			cell.setForeground(!live ? MUTED : s.boosted > s.real ? ColorScheme.PROGRESS_COMPLETE_COLOR
				: s.boosted < s.real ? ColorScheme.PROGRESS_ERROR_COLOR : Color.WHITE);
			cell.setToolTipText(s.tooltip());
			cell.setIcon(skillIcon(s.skill));
			grid.add(cell);
		}
		return grid;
	}

	/** The skill's small icon, or null when no skill has that name. */
	private Icon skillIcon(String name)
	{
		for (Skill s : Skill.values())
		{
			if (s.getName().equalsIgnoreCase(name))
			{
				return new javax.swing.ImageIcon(skillIcons.getSkillImage(s, true));
			}
		}
		return null;
	}

	private JComponent itemGrid(List<PanelModel.ItemCell> items)
	{
		ItemGridView grid = new ItemGridView(this::paintCell, GRID_COLUMNS, MAX_GRID_ITEMS);
		grid.update(items);
		return grid.component();
	}

	/**
	 * A grid cell's icon and tooltip. ItemManager hands back its cached image or an empty one it fills on
	 * the client thread, so no image is made on the Swing thread.
	 */
	private void paintCell(JLabel cell, PanelModel.ItemCell item)
	{
		String worth = item.value > 0 ? " (" + Amounts.EXACT.of(item.value) + " gp" : "";
		// High alchemy is the secondary figure: what the stack pays when alched, beside its Grand Exchange value.
		String alch = item.highAlch > 0 ? (worth.isEmpty() ? " (" : ", ") + "alch " + Amounts.EXACT.of(item.highAlch)
			+ " gp" : "";
		cell.setToolTipText(item.tooltip() + worth + alch + (worth.isEmpty() && alch.isEmpty() ? "" : ")"));
		icon(item.id, item.quantity).addTo(cell);
	}

	// Banks and Storage: filterable icon grids with value, drawn in place (BanksTabView, StorageTabView).

	/** On the Swing thread: the Banks tab for a built model; null is "bank sharing is off". */
	private int drawBanks(List<PanelModel.BankCard> banks, View v)
	{
		JPanel tab = tabs.get("Banks");
		if (banks == null || banks.isEmpty())
		{
			banksView.clear();
			tab.removeAll();
			if (banks == null)
			{
				empty(tab, "Bank sharing is off.",
					"Turn on 'Share bank' in GIM Utilities' settings to see members' banks.");
			}
			else
			{
				empty(tab, "No banks yet.", "Open and close your bank. Members' banks appear after they do the same.");
			}
			tab.revalidate();
			tab.repaint();
			return -1;
		}
		return banksView.apply(banks, v.self, bankQuery);
	}

	/** Banks cards built from scratch so far: tests check a sync updates in place instead. */
	int bankRebuilds()
	{
		return banksView.rebuilds();
	}

	// Find: one icon row per item and place.

	private void drawFind(JPanel tab, View v, long now)
	{
		String q = findQuery.getText();
		refreshHolders(FindQuick.holders(v.group.searchSources()));
		FindFilter filter = findFilter();
		boolean typed = ItemQuery.parse(q) != null;
		if (!typed && !filter.active())
		{
			empty(tab, "Who has it?", "Type at least " + GroupSearch.MIN_QUERY + " letters of an item name, or pick a"
				+ " category above, or press Runes anywhere, Food or Teleports. Searches members' last banks,"
				+ " inventories, equipment, pouches and group storage. '::find guam' in the chatbox gives the same"
				+ " answer.");
			return;
		}
		List<PanelModel.FindRow> rows = PanelModel.find(q, v.group, v.items, now, v.self, filter);
		if (rows.isEmpty())
		{
			empty(tab, "Nobody has it.", (typed ? "Nothing shared matches '" + ItemQuery.normalize(q) + "'"
				: "Nothing shared matches " + filter.kind.label.toLowerCase(java.util.Locale.ROOT)) + ". Only members"
				+ " running GIM Utilities with sharing on are searched, as of their last sync.");
		}
		for (PanelModel.FindRow r : rows)
		{
			JLabel row = Ui.label(r.text + (r.ago.isEmpty() ? "" : " (" + r.ago + ")"), FontManager.getRunescapeSmallFont(),
				null);
			icon(r.itemId, r.quantity).addTo(row);
			row.setToolTipText(r.tooltip);
			row.setBorder(new EmptyBorder(1, 0, 1, 0));
			tab.add(row);
		}
		List<String> history = GroupSearch.history(q, v.group.log().entries(), v.items, now, FindCommand.HISTORY_LIMIT);
		List<String> exact = GroupSearch.history(q, v.group.log().entries(), Amounts.EXACT.words(v.items), now,
			FindCommand.HISTORY_LIMIT);
		for (int i = 0; i < history.size(); i++)
		{
			tab.add(tip(text(history.get(i), MUTED), exact.get(i)));
		}
	}

	// Banks, Storage and Find controls: category pills, the bank preview's "Show all", and the quick-find dropdowns.

	/** The search box with the category pills below it; {@code extra} (may be null) sits under the pills. */
	private JComponent gridControls(IconTextField search, java.util.function.Consumer<ItemCategory> pick,
		JComponent extra)
	{
		JPanel controls = new Column();
		controls.add(search);
		JPanel pills = new JPanel(new GridLayout(0, 4, 2, 2));
		pills.setBorder(new EmptyBorder(4, 0, 2, 0));
		ButtonGroup group = new ButtonGroup();
		for (ItemCategory c : ItemCategory.values())
		{
			JToggleButton b = Ui.toggle(c.label, c == ItemCategory.ALL, 1);
			b.setToolTipText(c == ItemCategory.ALL ? "Every item" : "Only " + c.label.toLowerCase(java.util.Locale.ROOT));
			b.addActionListener(e ->
			{
				pick.accept(c);
				redraw();
			});
			group.add(b);
			pills.add(b);
		}
		controls.add(pills);
		if (extra != null)
		{
			controls.add(extra);
		}
		return controls;
	}

	/** Lifts the Banks preview: every stack of the chosen category, not just the top 20 by value. */
	private JComponent showAllToggle()
	{
		JToggleButton b = Ui.toggle("Show all items", false, 1);
		b.setToolTipText("Each bank draws its " + PanelModel.BANK_PREVIEW + " most valuable stacks. Search, or turn this"
			+ " on, to see the rest.");
		b.addActionListener(e ->
		{
			bankShowAll = b.isSelected();
			redraw();
		});
		return Ui.wide(b);
	}

	/** Find's search box, three one-click buttons and the category, sub-type and who-or-where dropdowns. */
	private JComponent findControls()
	{
		JPanel controls = new Column();
		controls.add(findQuery);
		JPanel presets = new JPanel(new GridLayout(1, 3, 2, 0));
		presets.setBorder(new EmptyBorder(4, 0, 2, 0));
		presets.add(presetButton("Runes anywhere", FindQuick.RUNES_ANYWHERE));
		presets.add(presetButton("Food", FindQuick.FOOD));
		presets.add(presetButton("Teleports", FindQuick.TELEPORTS));
		controls.add(presets);
		JPanel pickers = new JPanel(new GridLayout(1, 2, 2, 0));
		for (JComboBox<?> box : new JComboBox<?>[]{findKind, findSub, findHolder})
		{
			box.setFont(FontManager.getRunescapeSmallFont());
			box.setFocusable(false);
		}
		fillSubtypes(FindQuick.Kind.ANYTHING);
		findHolder.addItem(FindQuick.ANYWHERE);
		findKind.addActionListener(e ->
		{
			if (!updatingFind)
			{
				fillSubtypes((FindQuick.Kind) findKind.getSelectedItem());
				redraw();
			}
		});
		findSub.addActionListener(e -> findPicked());
		findHolder.addActionListener(e -> findPicked());
		pickers.add(findKind);
		pickers.add(findSub);
		controls.add(pickers);
		JPanel where = new JPanel(new GridLayout(1, 1));
		where.setBorder(new EmptyBorder(2, 0, 0, 0));
		where.add(findHolder);
		controls.add(where);
		return controls;
	}

	private void findPicked()
	{
		if (!updatingFind)
		{
			redraw();
		}
	}

	private JButton presetButton(String label, FindFilter preset)
	{
		JButton b = Ui.button(label, "Search the group for " + label.toLowerCase(java.util.Locale.ROOT), () ->
		{
			updatingFind = true;
			try
			{
				findQuery.setText("");
				findKind.setSelectedItem(preset.kind);
				fillSubtypes(preset.kind);
				findSub.setSelectedItem(preset.subtype);
				findHolder.setSelectedIndex(0);
			}
			finally
			{
				updatingFind = false;
			}
			redraw();
		});
		b.setMargin(new java.awt.Insets(1, 0, 1, 0));
		return b;
	}

	/** The sub-type dropdown for {@code kind}, {@code Any} picked. */
	private void fillSubtypes(FindQuick.Kind kind)
	{
		boolean was = updatingFind;
		updatingFind = true;
		try
		{
			findSub.removeAllItems();
			FindQuick.subtypes(kind).forEach(findSub::addItem);
			findSub.setSelectedIndex(0);
		}
		finally
		{
			updatingFind = was;
		}
	}

	/** The holder dropdown lists who the group has anything from; rebuilt only when that changes, keeping the pick. */
	private void refreshHolders(List<String> holders)
	{
		if (holders.equals(findHolders))
		{
			return;
		}
		findHolders = holders;
		Object picked = findHolder.getSelectedItem();
		updatingFind = true;
		try
		{
			findHolder.removeAllItems();
			holders.forEach(findHolder::addItem);
			findHolder.setSelectedItem(holders.contains(picked) ? picked : FindQuick.ANYWHERE);
		}
		finally
		{
			updatingFind = false;
		}
	}

	private FindFilter findFilter()
	{
		Object holder = findHolder.getSelectedIndex() > 0 ? findHolder.getSelectedItem() : null;
		return new FindFilter((FindQuick.Kind) findKind.getSelectedItem(), (String) findSub.getSelectedItem(),
			holder == null ? null : holder.toString());
	}

	// Log: every group event, yours and members' (Board 2026-11-10), with search and filters.

	/** The search box, category chips, member and time pickers, held above the rows like a search bar. */
	private JComponent logControls()
	{
		JPanel controls = new Column();
		controls.add(logQuery);
		JPanel chips = new JPanel(new GridLayout(0, 3, 2, 2));
		chips.setBorder(new EmptyBorder(4, 0, 2, 0));
		for (GroupEvent.Category c : GroupEvent.Category.values())
		{
			chips.add(chip(c));
		}
		controls.add(chips);
		JPanel pickers = new JPanel(new GridLayout(1, 2, 2, 0));
		logMember.addItem(ALL_MEMBERS);
		logMember.setFont(FontManager.getRunescapeSmallFont());
		logMember.setFocusable(false);
		logMember.addActionListener(e ->
		{
			if (!updatingMembers)
			{
				redraw();
			}
		});
		logRange.setFont(FontManager.getRunescapeSmallFont());
		logRange.setFocusable(false);
		logRange.addActionListener(e -> redraw());
		pickers.add(logMember);
		pickers.add(logRange);
		controls.add(pickers);
		return controls;
	}

	private JToggleButton chip(GroupEvent.Category category)
	{
		String label = ActivityLog.label(category);
		JToggleButton b = Ui.toggle(label, false, 1);
		b.setToolTipText("Show " + label + ". With none ticked, the Log shows everything.");
		b.addActionListener(e ->
		{
			if (b.isSelected())
			{
				logCategories.add(category);
			}
			else
			{
				logCategories.remove(category);
			}
			redraw();
		});
		return b;
	}

	/** The member picker lists who is in the log; rebuilt only when that changes, keeping the pick. */
	private void refreshMembers(List<String> members)
	{
		if (members.equals(logMembers))
		{
			return;
		}
		logMembers = members;
		Object picked = logMember.getSelectedItem();
		updatingMembers = true;
		try
		{
			logMember.removeAllItems();
			logMember.addItem(ALL_MEMBERS);
			members.forEach(logMember::addItem);
			logMember.setSelectedItem(members.contains(picked) ? picked : ALL_MEMBERS);
		}
		finally
		{
			updatingMembers = false;
		}
	}

	private ActivityLog.Filter logFilter()
	{
		Object member = logMember.getSelectedIndex() > 0 ? logMember.getSelectedItem() : null;
		return new ActivityLog.Filter(logQuery.getText(), logCategories, member == null ? "" : member.toString(),
			(ActivityLog.Range) logRange.getSelectedItem());
	}

	// Activities tab: who is in a boss or minigame now, then recent completions and deaths.

	/** The All / Bossing / Minigames pills, above the tab so a redraw never moves them. */
	private JComponent activityFilters()
	{
		JPanel pills = new JPanel(new GridLayout(1, ActivityRows.Filter.values().length, 2, 0));
		pills.setBorder(new EmptyBorder(0, 0, 4, 0));
		ButtonGroup group = new ButtonGroup();
		for (ActivityRows.Filter f : ActivityRows.Filter.values())
		{
			JToggleButton b = Ui.toggle(f.label, f == activityFilter, 2);
			b.addActionListener(e ->
			{
				activityFilter = f;
				redraw();
			});
			group.add(b);
			pills.add(b);
		}
		return pills;
	}

	private void drawActivities(JPanel tab, View v, long now)
	{
		tab.add(heading("Live now"));
		List<ActivityRows.Card> live = ActivityRows.live(v.group.activities(now), activityFilter, v.self, now);
		if (live.isEmpty())
		{
			tab.add(text("No group members in a boss or minigame right now.", MUTED));
		}
		for (ActivityRows.Card c : live)
		{
			JPanel card = Ui.card();
			card.add(Ui.label(c.title, FontManager.getRunescapeBoldFont(), Color.WHITE));
			c.lines.forEach(line -> card.add(text(line, ACTIVITY)));
			if (!c.splits.isEmpty())
			{
				boolean open = splitsOpen.contains(c.member);
				card.add(foldHeader("Wave splits", open, Color.WHITE, () ->
				{
					if (!splitsOpen.remove(c.member))
					{
						splitsOpen.add(c.member);
					}
				}));
				if (open)
				{
					c.splits.forEach(line -> card.add(text(line, MUTED)));
				}
			}
			tab.add(card);
		}
		if (v.sharing != null && !v.sharing.allSharing)
		{
			tab.add(text("Your own activity shows here either way; members see it only with 'Share group events'"
				+ " on.", MUTED));
		}
		drawPersonalBests(tab, v);
		tab.add(heading("Recent runs"));
		List<String> recent = ActivityRows.recent(v.group.feed().entries(), activityFilter, v.items, now);
		if (recent.isEmpty())
		{
			tab.add(text("Completions and deaths in the Fight Caves, the Inferno and the Colosseum appear here.",
				MUTED));
		}
		recent.forEach(line -> tab.add(text(line, Color.WHITE)));
	}

	/**
	 * Its own section, above Recent runs: the newest bests first, in gold, then each boss any member has shared a
	 * best for with every member's time and the group best marked. A boss with a new best is in gold too.
	 */
	private void drawPersonalBests(JPanel tab, View v)
	{
		tab.add(heading("Personal bests"));
		List<String> history = ProgressView.history(v.group.feed().entries(), 10);
		if (!history.isEmpty())
		{
			tab.add(text("New bests", MUTED));
			history.forEach(line -> tab.add(text("NEW " + line, ACTIVITY)));
		}
		List<ProgressView.BossRow> rows = v.progress.bossRows();
		if (rows.isEmpty())
		{
			tab.add(text("Members' boss best times appear here once they share group events and kill a boss.",
				MUTED));
		}
		for (ProgressView.BossRow r : rows)
		{
			boolean fresh = history.stream().anyMatch(h -> h.toLowerCase(Locale.ROOT)
				.contains(r.boss.toLowerCase(Locale.ROOT)));
			tab.add(text(r.line(), fresh ? ACTIVITY : Color.WHITE));
		}
	}

	private void drawLog(JPanel tab, View v, long now)
	{
		List<GroupEvent> all = v.group.feed().entries();
		refreshMembers(ActivityLog.members(all));
		if (all.isEmpty())
		{
			empty(tab, "Nothing logged yet.", "Drops, deaths, levels, storage visits and the rest appear here, yours"
				+ " and your group's, whether or not they go to Discord or the overlay.");
			return;
		}
		ZoneId zone = ZoneId.systemDefault();
		List<GroupEvent> shown = ActivityLog.filter(all, logFilter(), v.items, now, zone);
		if (shown.isEmpty())
		{
			empty(tab, "Nothing matches.", "Clear the search, untick a filter or pick a longer time.");
			return;
		}
		int left = MAX_LOG_ROWS;
		for (ActivityLog.Section s : ActivityLog.sections(shown, v.since, v.self, now, zone))
		{
			if (left <= 0)
			{
				break;
			}
			tab.add(heading(s.title));
			if (s.events.isEmpty())
			{
				tab.add(text("Nothing new from the group.", MUTED));
			}
			for (int i = 0; i < s.events.size() && left > 0; i++, left--)
			{
				tab.add(logRow(ActivityLog.row(s.events.get(i), v.items, now, zone, v.group.log())));
			}
		}
		if (shown.size() > MAX_LOG_ROWS)
		{
			tab.add(text("and " + (shown.size() - MAX_LOG_ROWS) + " more: search or filter to find them.", MUTED));
		}
	}

	/** Icon, member, short line and time; the full line and value on hover; Copy on right-click. */
	private JComponent logRow(ActivityLog.Row r)
	{
		JLabel row = Ui.label("<html><body style='width:" + LOG_TEXT_WIDTH + "px'><b>" + escape(r.event.member)
			+ "</b> " + escape(r.text) + " <font color='#9a9a9a'>(" + escape(r.ago) + ")</font></body></html>",
			FontManager.getRunescapeSmallFont(), Color.WHITE);
		row.setBorder(new EmptyBorder(2, 0, 2, 0));
		row.setIconTextGap(4);
		if (r.itemId >= 0)
		{
			icon(r.itemId, r.quantity).addTo(row);
		}
		else
		{
			row.setIcon(skillIcon(r.skill));
		}
		row.setToolTipText("<html>" + escape(r.full + "\nRight-click to copy.").replace("\n", "<br>") + "</html>");
		JPopupMenu menu = new JPopupMenu();
		JMenuItem copy = new JMenuItem("Copy");
		copy.addActionListener(e -> Ui.copy(r.copy));
		menu.add(copy);
		row.setComponentPopupMenu(menu);
		return row;
	}

	// Building blocks.

	private static void empty(JPanel tab, String title, String next)
	{
		tab.add(heading(title));
		tab.add(text(next, MUTED));
	}

	static JLabel heading(String text)
	{
		JLabel label = Ui.label(text, FontManager.getRunescapeBoldFont(), ColorScheme.BRAND_ORANGE);
		label.setBorder(new EmptyBorder(6, 0, 2, 0));
		return label;
	}

	static JLabel text(String text, Color color)
	{
		// The html wrapper makes long lines wrap at the panel width. Text from
		// peers is escaped first: a display name or item name is data, not markup.
		JLabel label = Ui.label(html(text), FontManager.getRunescapeSmallFont(), color);
		label.setBorder(new EmptyBorder(1, 0, 1, 0));
		return label;
	}

	/** {@link #text}'s label text: escaped, wrapping at the panel width. */
	static String html(String text)
	{
		return "<html><body style='width:175px'>" + escape(text) + "</body></html>";
	}

	/** {@code label} with the exact amounts in its tooltip; the label shows the game's short ones. */
	private static JLabel tip(JLabel label, String exact)
	{
		label.setToolTipText(exact);
		return label;
	}

	static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/**
	 * A vertical BoxLayout panel that left-aligns everything added to it.
	 * BoxLayout lines children up on their alignmentX; JLabel defaults to left
	 * (0.0) and JPanel to centre (0.5), so a mix started labels at the middle
	 * and pushed text past the panel's right edge (Board's test, 2026-11-04).
	 */
	static final class Column extends JPanel
	{
		Column()
		{
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		}

		@Override
		protected void addImpl(Component comp, Object constraints, int index)
		{
			if (comp instanceof JComponent)
			{
				((JComponent) comp).setAlignmentX(Component.LEFT_ALIGNMENT);
			}
			super.addImpl(comp, constraints, index);
		}
	}

	/** A small labelled bar: "HP 72/99" over a fill. Greyed when the member is offline. */
	/**
	 * One stat as the minimap draws it (Board 2026-11-04: "the orbs like they have
	 * near the minimap"): the number on the left, coloured green to red by how full
	 * it is, then a round orb filled from the bottom with the stat's own icon.
	 */
	private static final class Orb extends JComponent
	{
		private static final int SIZE = 18;
		private final PanelModel.Bar bar;
		private final Color color;
		private final boolean live;
		private final BufferedImage icon;

		Orb(PanelModel.Bar bar, Color color, boolean live, BufferedImage icon)
		{
			this.bar = bar;
			this.color = color;
			this.live = live;
			this.icon = icon;
			setPreferredSize(new Dimension(44, SIZE + 2));
			setToolTipText(bar.tooltip());
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			double share = bar.max <= 0 ? 0 : Math.min(1.0, Math.max(0, bar.current) / (double) bar.max);
			int x = getWidth() - SIZE - 1;
			int y = 1;
			g2.setFont(FontManager.getRunescapeSmallFont());
			g2.setColor(live ? numberColor(share) : MUTED);
			String number = String.valueOf(bar.current);
			int textWidth = g2.getFontMetrics().stringWidth(number);
			g2.drawString(number, x - textWidth - 3, y + SIZE - 4);
			// Ring, empty part, then the fill rising from the bottom like the game's orbs.
			// Prayer switched on: a bright gold ring around the orb; off or unshared: plain.
			if (live && bar.on > 0)
			{
				g2.setColor(PRAYER_ON);
				g2.fillOval(x - 2, y - 2, SIZE + 4, SIZE + 4);
			}
			g2.setColor(Color.BLACK);
			g2.fillOval(x - 1, y - 1, SIZE + 2, SIZE + 2);
			g2.setColor(ColorScheme.DARK_GRAY_COLOR);
			g2.fillOval(x, y, SIZE, SIZE);
			int fill = (int) Math.round(SIZE * share);
			Shape clip = g2.getClip();
			g2.clipRect(x, y + SIZE - fill, SIZE, fill);
			g2.setColor(live ? color : MUTED.darker());
			g2.fillOval(x, y, SIZE, SIZE);
			g2.setClip(clip);
			if (icon != null)
			{
				int s = SIZE - 6;
				g2.drawImage(icon, x + 3, y + 3, s, s, null);
			}
			g2.dispose();
		}

		/** The minimap's number colours: green when full, through yellow, to red when low. */
		static Color numberColor(double share)
		{
			int red = (int) Math.round(255 * Math.min(1.0, 2 * (1 - share)));
			int green = (int) Math.round(255 * Math.min(1.0, 2 * share));
			return new Color(red, green, 0);
		}
	}

	/** The collapse arrow on a member card: pointing down when open, right when folded. */
	static final class Arrow implements Icon
	{
		private static final int SIZE = 8;
		final boolean open;

		Arrow(boolean open)
		{
			this.open = open;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(c.getForeground());
			int[] xs = open ? new int[]{x, x + SIZE, x + SIZE / 2} : new int[]{x + 1, x + SIZE - 1, x + 1};
			int[] ys = open ? new int[]{y + 2, y + 2, y + SIZE - 1} : new int[]{y, y + SIZE / 2, y + SIZE};
			g2.fillPolygon(xs, ys, 3);
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return SIZE;
		}

		@Override
		public int getIconHeight()
		{
			return SIZE;
		}
	}

	/**
	 * An item icon drawn smaller than ItemManager's 36x32, for the worn and inventory layouts. A log basket,
	 * fish barrel or coal bag with known contents shows the count in the game's stack-text yellow; an empty
	 * worn slot shows the game's silhouette for it.
	 */
	static final class SmallItem extends JComponent
	{
		static final int W = 22;
		static final int H = 20;
		/** The game's stack-number yellow. */
		private static final Color COUNT = new Color(0xFF, 0xFF, 0x00);
		/**
		 * Every slot's ground, a step lighter than the card (DARKER_GRAY_COLOR) so the grids read as slots,
		 * empty ones included (Board 2026-11-14: DARK_GRAY_COLOR was barely apart from the card).
		 */
		static final Color CELL = new Color(0x3A, 0x3A, 0x3A);
		private final AsyncBufferedImage image;
		/** Items inside a container, or -1 for no number. */
		final int inside;
		/** The equipment slot whose silhouette this empty cell draws, or -1. */
		final int emptySlot;
		private final BufferedImage silhouette;

		SmallItem(AsyncBufferedImage image, String tooltip, int inside, int emptySlot, BufferedImage silhouette)
		{
			this.image = image;
			this.inside = inside;
			this.emptySlot = emptySlot;
			this.silhouette = silhouette;
			setPreferredSize(new Dimension(W, H));
			setToolTipText(tooltip);
			if (image != null)
			{
				image.onLoaded(this::repaint);
			}
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			g.setColor(CELL);
			g.fillRect(0, 0, W, H);
			if (image != null)
			{
				g.drawImage(image, 0, 0, W, H, null);
			}
			else if (silhouette != null)
			{
				// Centred at its own size, at most the cell's, as the worn tab draws it.
				int w = Math.min(W, silhouette.getWidth());
				int h = Math.min(H, silhouette.getHeight());
				g.drawImage(silhouette, (W - w) / 2, (H - h) / 2, w, h, null);
			}
			if (inside >= 0)
			{
				drawCount(g, String.valueOf(inside));
			}
		}

		/** Top left with a black shadow, where the game draws a stack's number. */
		private static void drawCount(Graphics g, String text)
		{
			g.setFont(FontManager.getRunescapeSmallFont());
			int y = g.getFontMetrics().getAscent() - 2;
			g.setColor(Color.BLACK);
			g.drawString(text, 1, y + 1);
			g.setColor(COUNT);
			g.drawString(text, 0, y);
		}
	}
}
