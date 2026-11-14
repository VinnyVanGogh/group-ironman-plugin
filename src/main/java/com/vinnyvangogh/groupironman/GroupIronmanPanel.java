package com.vinnyvangogh.groupironman;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.Icon;
import lombok.extern.slf4j.Slf4j;
import javax.swing.SwingUtilities;
import net.runelite.api.SpriteID;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.AsyncBufferedImage;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
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
	interface Actions extends GroupSettingsTab.Actions
	{
		void join();

		void switchParty();

		void leave();

		void newKey();

		/** The group key for "Copy group key", or null without one. */
		String groupKey();

		/** Saves a pasted key; false when the text holds none. */
		boolean pasteKey(String text);

		/** Ticks Sync with my group and every Share switch ({@link SharingPrompt#SHARING_KEYS}). */
		void turnOnSharing();

		/** Ticks Post to Discord. */
		void postToDiscord();
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
		/** What the "Turn on sharing" card reads. */
		final SharingPrompt.Settings sharing;

		View(GroupSync.State sync, GroupRoster group, GroupMembers members, ItemLookup items,
			GroupOutbox.Switches switches, RegionNames regions, long since, String self, GamePresence presence,
			GroupSettingsTab.View settings, SharingPrompt.Settings sharing)
		{
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
	}

	private static final int GRID_COLUMNS = 5;
	/** Bigger grids get a "use the filter" line instead: hundreds of icons in a side panel help nobody. */
	private static final int MAX_GRID_ITEMS = 200;
	private static final Color HP = new Color(0xB0, 0x2E, 0x26);
	private static final Color PRAYER = new Color(0x2E, 0x8B, 0xB0);
	private static final Color RUN = new Color(0xC8, 0xA0, 0x2A);
	private static final Color SPEC = new Color(0x3A, 0x9A, 0x4A);
	private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR.darker();
	/** Rows drawn at once; 2,000 labels in a side panel help nobody, the search and filters find the rest. */
	private static final int MAX_LOG_ROWS = 200;
	/** A row's text width, leaving room for its item or skill icon. */
	private static final int LOG_TEXT_WIDTH = 140;
	private static final String ALL_MEMBERS = "All members";

	private final ItemManager itemManager;
	private final SkillIconManager skillIcons;
	/** The minimap orbs' own icons, by bar label; filled in as the sprites load. */
	private final Map<String, BufferedImage> orbIcons = new ConcurrentHashMap<>();
	private final Actions actions;
	private final GroupSettingsTab settingsTab;

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
	/** Member cards the player collapsed, by name. */
	private final Set<String> collapsed = new HashSet<>();
	/** Bank cards folded in the Banks tab, kept apart from the Group tab's. */
	private final BankFolds bankFolds = new BankFolds();
	private String current = "Group";
	/** What the last Copy/Paste key press did, shown under the buttons for a while. */
	private String keyNote;
	private long keyNoteAt;
	private final SharingPrompt sharing = new SharingPrompt();
	private View view;

	GroupIronmanPanel(ItemManager itemManager, SkillIconManager skillIcons, SpriteManager sprites, Actions actions)
	{
		loadOrbIcon(sprites, "HP", SpriteID.MINIMAP_ORB_HITPOINTS_ICON);
		loadOrbIcon(sprites, "Prayer", SpriteID.MINIMAP_ORB_PRAYER_ICON);
		loadOrbIcon(sprites, "Run", SpriteID.MINIMAP_ORB_RUN_ICON);
		loadOrbIcon(sprites, "Spec", SpriteID.MINIMAP_ORB_SPECIAL_ICON);
		this.itemManager = itemManager;
		this.skillIcons = skillIcons;
		this.actions = actions;
		this.settingsTab = new GroupSettingsTab(actions, this::redraw);
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(8, 8, 8, 8));

		JPanel top = new Column();
		top.add(header);
		top.add(tabBar("Group", "Banks", "Storage", "Find", "Log", "Settings"));
		add(top, BorderLayout.NORTH);

		addTab("Group", null);
		addTab("Banks", bankFilter);
		addTab("Storage", storageFilter);
		addTab("Find", findQuery);
		addTab("Log", logControls());
		addTab("Settings", null);
		add(views, BorderLayout.CENTER);
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
		redraw();
	}

	private void redraw()
	{
		View v = view;
		if (v == null)
		{
			return;
		}
		drawHeader(v);
		JPanel tab = tabs.get(current);
		if (settingsUnchanged(tab, v))
		{
			return;
		}
		tab.removeAll();
		long now = System.currentTimeMillis();
		try
		{
			switch (current)
			{
				case "Banks":
					drawBanks(tab, v, now);
					break;
				case "Storage":
					drawStorage(tab, v, now);
					break;
				case "Find":
					drawFind(tab, v, now);
					break;
				case "Log":
					drawLog(tab, v, now);
					break;
				case "Settings":
					drawSettings(tab, v, now);
					break;
				default:
					drawGroup(tab, v, now);
					break;
			}
		}
		catch (RuntimeException | AssertionError e)
		{
			// A tab that fails to draw says so instead of going blank (the Group tab did, 2026-11-04).
			log.warn("GIM Utilities: drawing the {} tab failed", current, e);
			tab.removeAll();
			empty(tab, "Could not draw this tab.", e.getClass().getSimpleName() + ": " + e.getMessage()
				+ ". Please report it with the RuneLite log.");
		}
		tab.revalidate();
		tab.repaint();
	}

	/** The Settings tab is on screen and shows the same as before: rebuilding would lose a half-typed number. */
	private boolean settingsUnchanged(JPanel tab, View v)
	{
		if (!"Settings".equals(current) || tab.getComponentCount() == 0)
		{
			return false;
		}
		return v.settings == null || !settingsTab.changed(v.settings);
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
			JToggleButton b = new JToggleButton(name, name.equals(current));
			b.setFont(FontManager.getRunescapeSmallFont());
			b.setFocusable(false);
			b.setMargin(new java.awt.Insets(2, 0, 2, 0));
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
		header.removeAll();
		SyncHeader h = SyncHeader.of(v.sync, v.group.othersOnline(v.self, System.currentTimeMillis()), v.members);
		JLabel title = new JLabel(h.title);
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(h.ok ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.BRAND_ORANGE);
		header.add(title);
		if (h.detail != null)
		{
			header.add(text(h.detail, MUTED));
		}
		// Two per row: a single FlowLayout row never wraps inside the BoxLayout header, so a
		// third button (Leave) was cut off past the panel's edge.
		JPanel buttons = new JPanel(new GridLayout(0, 2, 2, 2));
		for (SyncHeader.Button b : h.buttons)
		{
			buttons.add(button(b));
		}
		header.add(buttons);
		if (keyNote != null && System.currentTimeMillis() - keyNoteAt < 30_000)
		{
			header.add(text(keyNote, Color.WHITE));
		}
		drawSharingCard(v.sharing);
		for (String stranger : v.group.strangers())
		{
			header.add(text("Not in your group: " + stranger + " (ignored)", ColorScheme.PROGRESS_ERROR_COLOR));
		}
		header.revalidate();
		header.repaint();
	}

	/** "Turn on sharing" after a pasted key, or while sync is on with every Share switch off. Nothing without a click. */
	private void drawSharingCard(SharingPrompt.Settings s)
	{
		SharingPrompt.Card card = s == null ? null : sharing.card(s);
		if (card == null)
		{
			return;
		}
		JPanel box = section();
		JLabel title = new JLabel("Share with your group?");
		title.setFont(FontManager.getRunescapeBoldFont());
		box.add(title);
		box.add(text(SharingPrompt.TEXT, MUTED));
		JPanel buttons = new JPanel(new GridLayout(0, 1, 2, 2));
		buttons.setOpaque(false);
		if (card.sharing)
		{
			buttons.add(cardButton("Turn on sharing", "Ticks Sync with my group and every Share switch",
				actions::turnOnSharing));
		}
		if (card.discord)
		{
			buttons.add(cardButton("Post my events to Discord", "Ticks Post to Discord, for your webhook",
				actions::postToDiscord));
		}
		buttons.add(cardButton("Not now", "Hides this until the group key changes", () -> sharing.notNow(s.key)));
		box.add(buttons);
		header.add(box);
	}

	private JButton cardButton(String label, String tooltip, Runnable action)
	{
		JButton button = new JButton(label);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setFocusable(false);
		button.setToolTipText(tooltip);
		button.addActionListener(e ->
		{
			action.run();
			redraw();
		});
		return button;
	}

	private JButton button(SyncHeader.Button b)
	{
		JButton button = new JButton(b.label);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setFocusable(false);
		button.setToolTipText(b.tooltip);
		button.addActionListener(e -> press(b));
		return button;
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
					Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(key), null);
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
		for (PanelModel.MemberCard c : cardsList)
		{
			anyOffline |= !c.online && !c.you;
			tab.add(memberCard(c));
		}
		if (anyOffline)
		{
			tab.add(text("Offline members show what they had the last time you were both online.", MUTED));
		}
	}

	private JComponent memberCard(PanelModel.MemberCard c)
	{
		JPanel card = section();
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

		addStats(body, c);
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
		if (c.seedVault != null)
		{
			body.add(text(c.seedVault, MUTED));
		}
		card.add(body);
		return card;
	}

	/** A card's clickable title with its fold arrow; {@code toggle} flips the card, then the tab redraws. */
	private JLabel foldHeader(String title, boolean open, Color color, Runnable toggle)
	{
		// A drawn arrow: the RuneScape font has no glyph for ▾/▸ and showed a box.
		JLabel name = new JLabel(title, new Arrow(open), SwingConstants.LEFT);
		name.setIconTextGap(4);
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(color);
		name.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				toggle.run();
				redraw();
			}
		});
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
		JLabel label = new JLabel(caption);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(MUTED);
		p.add(label);
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
			grid.add(cells[i] != null ? cells[i] : WORN_LAYOUT[i] < 0 ? spacer() : emptySlot());
		}
		return grid;
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

	/** The inventory tab's 4 x 7 grid. Shared data has counts, not slots, so items fill it in order. */
	private JComponent inventoryLayout(List<PanelModel.ItemCell> items)
	{
		JPanel grid = new JPanel(new GridLayout(7, 4, 1, 1));
		grid.setOpaque(false);
		int shown = 0;
		for (PanelModel.ItemCell item : items)
		{
			if (shown == 28)
			{
				break;
			}
			grid.add(smallItem(item));
			shown++;
		}
		for (; shown < 28; shown++)
		{
			grid.add(emptySlot());
		}
		return grid;
	}

	private JComponent smallItem(PanelModel.ItemCell item)
	{
		return new SmallItem(icon(item.id, item.quantity), item.tooltip());
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
		return new SmallItem(null, null);
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
			Skill skill = skill(s.skill);
			if (skill != null)
			{
				cell.setIcon(new javax.swing.ImageIcon(skillIcons.getSkillImage(skill, true)));
			}
			grid.add(cell);
		}
		return grid;
	}

	private static Skill skill(String name)
	{
		for (Skill s : Skill.values())
		{
			if (s.getName().equalsIgnoreCase(name))
			{
				return s;
			}
		}
		return null;
	}

	private JComponent itemGrid(List<PanelModel.ItemCell> items)
	{
		JPanel wrap = new Column();
		wrap.setOpaque(false);
		JPanel grid = new JPanel(new GridLayout(0, GRID_COLUMNS, 1, 1));
		grid.setOpaque(false);
		for (int i = 0; i < items.size() && i < MAX_GRID_ITEMS; i++)
		{
			PanelModel.ItemCell item = items.get(i);
			JLabel cell = new JLabel();
			cell.setPreferredSize(new Dimension(36, 32));
			cell.setHorizontalAlignment(SwingConstants.CENTER);
			cell.setToolTipText(item.tooltip() + (item.value > 0 ? " (" + Amounts.EXACT.of(item.value) + " gp)" : ""));
			icon(item.id, item.quantity).addTo(cell);
			grid.add(cell);
		}
		wrap.add(grid);
		if (items.size() > MAX_GRID_ITEMS)
		{
			wrap.add(text("and " + (items.size() - MAX_GRID_ITEMS) + " more: use the filter to find them.", MUTED));
		}
		return wrap;
	}

	// Banks and Storage: filterable icon grids with value.

	private void drawBanks(JPanel tab, View v, long now)
	{
		if (!v.switches.bank)
		{
			empty(tab, "Bank sharing is off.", "Turn on 'Share bank' in GIM Utilities' settings to see members' banks.");
			return;
		}
		List<PanelModel.BankCard> banks = PanelModel.banks(v.group, v.items, bankFilter.getText(), now);
		if (banks.isEmpty())
		{
			empty(tab, "No banks yet.", "Open and close your bank. Members' banks appear after they do the same.");
			return;
		}
		bankFolds.filter(bankFilter.getText());
		Map<String, Boolean> matches = new LinkedHashMap<>();
		banks.forEach(b -> matches.put(b.member, !b.items.isEmpty()));
		if (banks.size() >= 2)
		{
			tab.add(foldAllLink(matches));
		}
		for (PanelModel.BankCard b : banks)
		{
			tab.add(bankCard(b, v, matches.get(b.member)));
		}
	}

	/** Collapse all while any bank is open, Expand all once every one is folded. */
	private JComponent foldAllLink(Map<String, Boolean> matches)
	{
		boolean anyOpen = bankFolds.anyOpen(matches.keySet(), matches::get);
		JLabel link = new JLabel(anyOpen ? "Collapse all" : "Expand all");
		link.setFont(FontManager.getRunescapeSmallFont());
		link.setForeground(ColorScheme.BRAND_ORANGE);
		link.setBorder(new EmptyBorder(0, 0, 4, 0));
		link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		link.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				bankFolds.setAll(matches.keySet(), !anyOpen);
				redraw();
			}
		});
		return link;
	}

	private JComponent bankCard(PanelModel.BankCard b, View v, boolean matches)
	{
		JPanel card = section();
		boolean open = bankFolds.open(b.member, matches);
		boolean mine = !v.self.isEmpty() && v.self.equalsIgnoreCase(b.member);
		card.add(foldHeader(mine ? "Your bank (" + b.member + ")" : b.member + "'s bank", open, Color.WHITE,
			() -> bankFolds.toggle(b.member, matches)));
		card.add(tip(text(b.summary, MUTED), b.exact));
		if (open)
		{
			card.add(b.items.isEmpty() ? text("No item matches the filter.", MUTED) : itemGrid(b.items));
		}
		return card;
	}

	private void drawStorage(JPanel tab, View v, long now)
	{
		if (!v.switches.storage)
		{
			// Own visits are still kept here, so a withdrawal made before ticking the switch is not a mystery.
			tab.add(text("'Share group storage changes' is off: your own visits are kept here only and nothing is"
				+ " sent, and members' changes don't arrive.", ColorScheme.BRAND_ORANGE));
		}
		PanelModel.StorageView s = PanelModel.storage(v.group, v.items, v.self, storageFilter.getText(), now);
		JPanel contents = section();
		JLabel title = new JLabel("Group storage");
		title.setFont(FontManager.getRunescapeBoldFont());
		contents.add(title);
		if (s.snapshot == null)
		{
			contents.add(text("Not seen yet. Open group storage once and close it; what it holds shows here.", MUTED));
		}
		else
		{
			contents.add(tip(text(s.snapshot + ", " + Amounts.GAME.of(s.value) + " gp", MUTED),
				Amounts.EXACT.of(s.value) + " gp"));
			contents.add(s.items.isEmpty() ? text("Empty, or no item matches the filter.", MUTED) : itemGrid(s.items));
		}
		tab.add(contents);

		JPanel log = section();
		JLabel heading = new JLabel("Changes");
		heading.setFont(FontManager.getRunescapeBoldFont());
		log.add(heading);
		if (s.log.isEmpty())
		{
			log.add(text("No changes yet. Deposits and withdrawals are logged when you press Save or close storage."
				+ " Members' changes arrive while you are synced with them.", MUTED));
		}
		for (PanelModel.LogRow row : s.log)
		{
			log.add(tip(text(row.text + " (" + row.ago + ")", Color.WHITE), row.exact));
		}
		tab.add(log);
	}

	// Find: one icon row per item and place.

	private void drawFind(JPanel tab, View v, long now)
	{
		String q = findQuery.getText();
		if (ItemQuery.parse(q) == null)
		{
			empty(tab, "Who has it?", "Type at least " + GroupSearch.MIN_QUERY + " letters of an item name. Searches"
				+ " members' last banks, inventories, equipment, pouches and group storage. '::find guam' in the"
				+ " chatbox gives the same answer.");
			return;
		}
		List<PanelModel.FindRow> rows = PanelModel.find(q, v.group, v.items, now, v.self);
		if (rows.isEmpty())
		{
			empty(tab, "Nobody has it.", "Nothing shared matches '" + ItemQuery.normalize(q) + "'. Only members running"
				+ " GIM Utilities with sharing on are searched, as of their last sync.");
		}
		for (PanelModel.FindRow r : rows)
		{
			JLabel row = new JLabel(r.text + (r.ago.isEmpty() ? "" : " (" + r.ago + ")"));
			row.setFont(FontManager.getRunescapeSmallFont());
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
		JToggleButton b = new JToggleButton(label);
		b.setFont(FontManager.getRunescapeSmallFont());
		b.setFocusable(false);
		b.setMargin(new java.awt.Insets(1, 0, 1, 0));
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
		JLabel row = new JLabel("<html><body style='width:" + LOG_TEXT_WIDTH + "px'><b>" + escape(r.event.member)
			+ "</b> " + escape(r.text) + " <font color='#9a9a9a'>(" + escape(r.ago) + ")</font></body></html>");
		row.setFont(FontManager.getRunescapeSmallFont());
		row.setForeground(Color.WHITE);
		row.setBorder(new EmptyBorder(2, 0, 2, 0));
		row.setIconTextGap(4);
		Skill skill = r.skill == null ? null : skill(r.skill);
		if (r.itemId >= 0)
		{
			icon(r.itemId, r.quantity).addTo(row);
		}
		else if (skill != null)
		{
			row.setIcon(new javax.swing.ImageIcon(skillIcons.getSkillImage(skill, true)));
		}
		row.setToolTipText("<html>" + escape(r.full + "\nRight-click to copy.").replace("\n", "<br>") + "</html>");
		JPopupMenu menu = new JPopupMenu();
		JMenuItem copy = new JMenuItem("Copy");
		copy.addActionListener(e ->
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(r.copy), null));
		menu.add(copy);
		row.setComponentPopupMenu(menu);
		return row;
	}

	// Building blocks.

	private static JPanel section()
	{
		JPanel p = new Column();
		p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		p.setBorder(BorderFactory.createCompoundBorder(new EmptyBorder(0, 0, 6, 0), new EmptyBorder(6, 6, 6, 6)));
		return p;
	}

	private static void empty(JPanel tab, String title, String next)
	{
		tab.add(heading(title));
		tab.add(text(next, MUTED));
	}

	static JLabel heading(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setBorder(new EmptyBorder(6, 0, 2, 0));
		return label;
	}

	static JLabel text(String text, Color color)
	{
		// The html wrapper makes long lines wrap at the panel width. Text from
		// peers is escaped first: a display name or item name is data, not markup.
		JLabel label = new JLabel("<html><body style='width:175px'>" + escape(text) + "</body></html>");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		label.setBorder(new EmptyBorder(1, 0, 1, 0));
		return label;
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

	/** An item icon drawn smaller than ItemManager's 36x32, for the worn and inventory layouts. */
	private static final class SmallItem extends JComponent
	{
		static final int W = 22;
		static final int H = 20;
		private final AsyncBufferedImage image;

		SmallItem(AsyncBufferedImage image, String tooltip)
		{
			this.image = image;
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
			g.setColor(ColorScheme.DARK_GRAY_COLOR);
			g.fillRect(0, 0, W, H);
			if (image != null)
			{
				g.drawImage(image, 0, 0, W, H, null);
			}
		}
	}
}
