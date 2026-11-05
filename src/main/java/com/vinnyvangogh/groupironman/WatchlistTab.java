package com.vinnyvangogh.groupironman;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;

/**
 * The panel's Watch tab: an item search to add to either list, then the
 * group's Suggested box (Accept / Dismiss / Accept all), the group list and
 * the personal list, each item with who in the group has it.
 *
 * <p>Its own class so the main panel only gains a tab. Draws what
 * {@link Watchlist} holds; every button goes through {@link Actions}.
 */
final class WatchlistTab
{
	/** What the tab's buttons do, and what it reads. Swing thread. */
	interface Actions
	{
		Watchlist watchlist();

		boolean sharing();

		/** Item id to name, everything the search can offer. */
		Map<Integer, String> searchNames();

		void addPersonal(int itemId, int quantity);

		void addGroup(int itemId, int quantity);

		void removePersonal(int itemId);

		void removeGroup(int itemId);

		void accept(int itemId);

		void dismiss(int itemId);

		void acceptAll();
	}

	/** Search results shown at once; more is a longer query away. */
	static final int MAX_RESULTS = 8;
	/** Places listed per watched item before "and N more". */
	private static final int MAX_PLACES = 3;
	private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR.darker();

	private final ItemManager itemManager;
	private final Actions actions;
	private final IconTextField search = new IconTextField();
	private final JSpinner quantity = new JSpinner(new SpinnerNumberModel(1, 1, Integer.MAX_VALUE, 1));
	private final JPanel top = new JPanel(new BorderLayout(4, 0));

	WatchlistTab(ItemManager itemManager, Actions actions, Runnable redraw)
	{
		this.itemManager = itemManager;
		this.actions = actions;
		search.setIcon(IconTextField.Icon.SEARCH);
		search.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 80, 30));
		search.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		search.setToolTipText("Search for an item to add, e.g. shark or ppot");
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				redraw.run();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				redraw.run();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				redraw.run();
			}
		});
		quantity.setToolTipText("How many you want the group to have. 1 means 'any'.");
		quantity.setPreferredSize(new Dimension(56, 30));
		top.add(search, BorderLayout.CENTER);
		top.add(quantity, BorderLayout.EAST);
	}

	/** Above the tab's content, so typing in it never loses focus to a redraw. */
	JComponent top()
	{
		return top;
	}

	void draw(JPanel tab, GroupIronmanPanel.View v)
	{
		Watchlist w = actions.watchlist();
		List<GroupSearch.Source> seen = v.group.searchSources();
		drawSearch(tab);

		List<Watchlist.Entry> suggested = w.suggested();
		if (!suggested.isEmpty())
		{
			JPanel box = GroupIronmanPanel.section();
			JPanel head = new JPanel(new BorderLayout());
			head.setOpaque(false);
			head.add(bold("Suggested (" + suggested.size() + ")"), BorderLayout.CENTER);
			head.add(button("Accept all", "Add every suggestion to your group watchlist", actions::acceptAll),
				BorderLayout.EAST);
			box.add(head);
			box.add(GroupIronmanPanel.text("Added by other members. Nothing joins your list until you accept it.",
				MUTED));
			for (Watchlist.Entry e : suggested)
			{
				String who = e.by.isEmpty() ? "a member" : e.by;
				box.add(row(e, v, "From " + who + ", " + BoostText.ago(System.currentTimeMillis(), e.at),
					MUTED, buttons(
						button("Accept", "Add to your group watchlist", () -> actions.accept(e.itemId)),
						button("Dismiss", "Hide this suggestion", () -> actions.dismiss(e.itemId)))));
			}
			tab.add(box);
		}

		List<Watchlist.Entry> group = w.group();
		tab.add(GroupIronmanPanel.heading("Group watchlist"));
		if (!actions.sharing())
		{
			tab.add(GroupIronmanPanel.text("Not shared: turn on 'Share group watchlist' in GIM Utilities' settings to"
				+ " send it to your group and see what they add.", MUTED));
		}
		if (group.isEmpty())
		{
			tab.add(GroupIronmanPanel.text("Nothing yet. Search above and press Group.", MUTED));
		}
		drawList(tab, v, group, seen, actions::removeGroup);

		List<Watchlist.Entry> personal = w.personal();
		tab.add(GroupIronmanPanel.heading("My watchlist"));
		if (personal.isEmpty())
		{
			tab.add(GroupIronmanPanel.text("Nothing yet. Search above and press Mine. Only you see this list.",
				MUTED));
		}
		drawList(tab, v, personal, seen, actions::removePersonal);
	}

	private void drawSearch(JPanel tab)
	{
		String q = search.getText();
		if (ItemQuery.parse(q) == null)
		{
			if (!q.trim().isEmpty())
			{
				tab.add(GroupIronmanPanel.text("Type at least " + GroupSearch.MIN_QUERY + " letters.", MUTED));
			}
			return;
		}
		Map<Integer, String> names = actions.searchNames();
		List<Integer> ids = Watchlist.search(q, names, MAX_RESULTS);
		JPanel box = GroupIronmanPanel.section();
		box.add(bold("Add to a watchlist"));
		if (ids.isEmpty())
		{
			box.add(GroupIronmanPanel.text("No item matches '" + ItemQuery.normalize(q) + "'. Untradeable items show"
				+ " up once someone in your group has had one.", MUTED));
		}
		for (int id : ids)
		{
			box.add(row(id, 1, names.get(id), null, null, buttons(
				button("Mine", "Add to your personal watchlist", () -> actions.addPersonal(id, wanted())),
				button("Group", "Add to your group watchlist", () -> actions.addGroup(id, wanted())))));
		}
		tab.add(box);
	}

	private void drawList(JPanel tab, GroupIronmanPanel.View v, List<Watchlist.Entry> list,
		List<GroupSearch.Source> seen, IntConsumer remove)
	{
		for (Watchlist.Entry e : list)
		{
			long have = Watchlist.total(e.itemId, seen);
			String status;
			Color colour;
			if (have == 0)
			{
				status = seen.isEmpty() ? "Not known yet: nothing shared so far." : "Nobody has it.";
				colour = seen.isEmpty() ? MUTED : ColorScheme.PROGRESS_ERROR_COLOR;
			}
			else
			{
				status = (e.quantity > 1 ? (have >= e.quantity ? "Enough: " : "Short: ") + have + " of " + e.quantity
					+ ". " : "") + places(Watchlist.holders(e.itemId, seen));
				colour = have >= e.quantity ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.BRAND_ORANGE;
			}
			tab.add(row(e, v, status, colour,
				buttons(button("Remove", "Take it off this list", () -> remove.accept(e.itemId)))));
		}
	}

	/** "Alex bank 120, Sam inventory 5, Group storage 300 and 2 more". */
	static String places(List<Watchlist.Holder> holders)
	{
		List<String> parts = new ArrayList<>();
		for (int i = 0; i < holders.size() && i < MAX_PLACES; i++)
		{
			Watchlist.Holder h = holders.get(i);
			parts.add(h.member + " " + h.where + " " + h.quantity);
		}
		String out = String.join(", ", parts);
		return holders.size() > MAX_PLACES ? out + " and " + (holders.size() - MAX_PLACES) + " more" : out;
	}

	private int wanted()
	{
		Object value = quantity.getValue();
		return value instanceof Integer ? Math.max(1, (Integer) value) : 1;
	}

	private JComponent row(Watchlist.Entry e, GroupIronmanPanel.View v, String status, Color colour, JComponent end)
	{
		return row(e.itemId, e.quantity, v.items.name(e.itemId) + (e.quantity > 1 ? " x " + e.quantity : ""), status,
			colour, end);
	}

	/** Icon, name over a status line, and the row's buttons at the end. */
	private JComponent row(int itemId, int stack, String name, String status, Color colour, JComponent end)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(2, 0, 2, 0));
		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(36, 32));
		itemManager.getImage(itemId, stack, stack > 1).addTo(icon);
		row.add(icon, BorderLayout.WEST);
		JPanel middle = new GroupIronmanPanel.Column();
		middle.setOpaque(false);
		middle.add(small(name, Color.WHITE));
		if (status != null)
		{
			middle.add(small(status, colour));
		}
		row.add(middle, BorderLayout.CENTER);
		row.add(end, BorderLayout.EAST);
		return row;
	}

	private static JComponent buttons(JButton... buttons)
	{
		JPanel p = new JPanel(new GridLayout(buttons.length, 1, 0, 2));
		p.setOpaque(false);
		for (JButton b : buttons)
		{
			p.add(b);
		}
		return p;
	}

	private static JButton button(String label, String tooltip, Runnable onClick)
	{
		JButton b = new JButton(label);
		b.setFont(FontManager.getRunescapeSmallFont());
		b.setFocusable(false);
		b.setMargin(new Insets(1, 3, 1, 3));
		b.setToolTipText(tooltip);
		b.addActionListener(e -> onClick.run());
		return b;
	}

	private static JLabel bold(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		return label;
	}

	/** Wraps inside the narrow middle of a row; names come from peers, so they are escaped. */
	private static JLabel small(String text, Color colour)
	{
		JLabel label = new JLabel("<html><body style='width:95px'>" + GroupIronmanPanel.escape(text)
			+ "</body></html>");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(colour);
		return label;
	}
}
