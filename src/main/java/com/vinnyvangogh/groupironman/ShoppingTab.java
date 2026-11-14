package com.vinnyvangogh.groupironman;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The panel's Shopping tab: the group's shared shopping lists, each line what
 * the group still has to get after what members and group storage hold, who
 * holds it and where this player can get it; then the form that adds a list
 * (a build preset or custom items) for one or more members, and who changed
 * what.
 *
 * <p>Rebuilt only when what it shows changed ({@link View#signature}), so a
 * half-typed list name or item search survives the many redraws member stats
 * cause. The form itself is built once and kept.
 */
final class ShoppingTab
{
	/** What the tab's buttons do; each false when refused. */
	interface Actions
	{
		boolean addList(String name, String preset, List<String> picks, List<String> members,
			Map<Integer, Integer> items);

		boolean renameList(String id, String name);

		boolean removeList(String id);

		/** Tradeable items matching {@code query}; {@code done} may be called on another thread. */
		void searchItems(String query, Consumer<List<Hit>> done);
	}

	static final int MAX_HITS = 10;
	private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR.darker();
	private static final Color DONE = ColorScheme.PROGRESS_COMPLETE_COLOR;
	private static final String CUSTOM = "Custom list";
	/** Sources named per line; the rest are a count. */
	private static final int SOURCES_SHOWN = 2;

	/** One item search result. */
	static final class Hit
	{
		final int id;
		final String name;

		Hit(int id, String name)
		{
			this.id = id;
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	/** Everything the tab draws. */
	static final class View
	{
		final List<ShoppingBoard.Card> cards;
		final List<ShopPresets.Preset> presets;
		final List<String> roster;
		final List<ShoppingSync.Change> history;
		final boolean loggedIn;
		final boolean syncing;

		View(List<ShoppingBoard.Card> cards, List<ShopPresets.Preset> presets, List<String> roster,
			List<ShoppingSync.Change> history, boolean loggedIn, boolean syncing)
		{
			this.cards = cards;
			this.presets = presets;
			this.roster = roster;
			this.history = history;
			this.loggedIn = loggedIn;
			this.syncing = syncing;
		}

		/** Changes when anything drawn changes. */
		String signature()
		{
			StringBuilder s = new StringBuilder();
			s.append(roster).append(loggedIn).append(syncing).append(history.size());
			for (ShoppingSync.Change c : history)
			{
				s.append(c.at).append(c.text);
			}
			for (ShoppingBoard.Card c : cards)
			{
				s.append('|').append(c.list.id).append(c.list.rev).append(c.progressUnknown).append(c.itemsUnknown);
				for (ShoppingBoard.Row r : c.rows)
				{
					s.append(rowText(r)).append(heldText(r.line)).append(sourceText(r.sources));
				}
			}
			return s.toString();
		}
	}

	private final Actions actions;
	private final ItemManager items;
	private final Runnable refresh;
	private final Form form = new Form();
	private String drawn;
	private String note;

	ShoppingTab(Actions actions, ItemManager items, Runnable refresh)
	{
		this.actions = actions;
		this.items = items;
		this.refresh = refresh;
	}

	/** Whether {@code v} would draw differently from what is on screen. */
	boolean changed(View v)
	{
		return !v.signature().equals(drawn) || note != null;
	}

	void draw(JPanel tab, View v)
	{
		drawn = v.signature();
		if (note != null)
		{
			tab.add(GroupIronmanPanel.text(note, Color.WHITE));
			note = null;
		}
		if (v.cards.isEmpty())
		{
			tab.add(GroupIronmanPanel.heading("No shopping lists yet"));
			tab.add(GroupIronmanPanel.text("Add one below: a build milestone, like Mithril gloves for a 1 Defence"
				+ " pure, or your own items. Every member running GIM Utilities sees it.", MUTED));
		}
		for (ShoppingBoard.Card c : v.cards)
		{
			tab.add(card(c));
		}
		if (!v.syncing)
		{
			tab.add(GroupIronmanPanel.text("Not in group sync: lists stay on this client until you join.", MUTED));
		}
		tab.add(form.panel(v));
		drawHistory(tab, v.history);
	}

	private void drawHistory(JPanel tab, List<ShoppingSync.Change> history)
	{
		if (history.isEmpty())
		{
			return;
		}
		tab.add(GroupIronmanPanel.heading("Changes"));
		long now = System.currentTimeMillis();
		for (ShoppingSync.Change c : history)
		{
			tab.add(GroupIronmanPanel.text(c.text + " (" + BoostText.ago(now, c.at) + ")", MUTED));
		}
	}

	// ---- A list ----

	private JPanel card(ShoppingBoard.Card c)
	{
		JPanel box = new GroupIronmanPanel.Column();
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setBorder(BorderFactory.createCompoundBorder(new EmptyBorder(0, 0, 6, 0), new EmptyBorder(6, 6, 6, 6)));
		JLabel title = new JLabel(GroupIronmanPanel.escape(c.list.name));
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(c.done() == c.rows.size() && !c.empty() ? DONE : Color.WHITE);
		box.add(title);
		box.add(GroupIronmanPanel.text(byline(c.list, System.currentTimeMillis()), MUTED));
		box.add(buttons(c.list));
		for (String line : warnings(c))
		{
			box.add(GroupIronmanPanel.text(line, ColorScheme.BRAND_ORANGE));
		}
		box.add(GroupIronmanPanel.text(c.empty() ? "Nothing to get: every quest is done, or the list is empty."
			: c.done() + " of " + c.rows.size() + " items done", MUTED));
		List<ShoppingBoard.Row> rows = new ArrayList<>(c.rows);
		rows.sort((a, b) -> Boolean.compare(a.line.done(), b.line.done()));
		for (ShoppingBoard.Row r : rows)
		{
			box.add(row(r));
		}
		return box;
	}

	/** "For Alice, Bob. Added by Bob, 5 min ago." or "... Renamed by Alice, ...". */
	static String byline(ShoppingList l, long now)
	{
		String what = ShoppingList.ADDED.equals(l.action) ? "Added" : capital(l.action);
		return "For " + String.join(", ", l.members) + ". " + what + " by " + l.by + ", " + BoostText.ago(now, l.at)
			+ ".";
	}

	private static String capital(String s)
	{
		return s.isEmpty() ? "Changed" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/** The preset's note, then what this client does not know. */
	static List<String> warnings(ShoppingBoard.Card c)
	{
		List<String> out = new ArrayList<>();
		if (c.preset != null && !c.preset.note().isEmpty())
		{
			out.add(c.preset.note());
		}
		if (!c.list.custom() && c.preset == null)
		{
			out.add("This list is from a newer GIM Utilities: update to see its items.");
		}
		if (!c.progressUnknown.isEmpty())
		{
			out.add("No quest progress from " + String.join(", ", c.progressUnknown)
				+ " yet: every quest counts for them until their client sends it.");
		}
		if (!c.itemsUnknown.isEmpty())
		{
			out.add(String.join(", ", c.itemsUnknown) + " share no items, so what they hold is not counted.");
		}
		return out;
	}

	private JPanel row(ShoppingBoard.Row r)
	{
		JPanel line = new JPanel(new BorderLayout(4, 0));
		line.setOpaque(false);
		line.setBorder(new EmptyBorder(3, 0, 3, 0));
		JLabel icon = new JLabel();
		if (items != null)
		{
			items.getImage(r.line.itemId).addTo(icon);
		}
		line.add(icon, BorderLayout.WEST);
		JPanel words = new GroupIronmanPanel.Column();
		words.setOpaque(false);
		words.add(label(rowText(r), r.line.done() ? DONE : Color.WHITE));
		String held = heldText(r.line);
		if (!held.isEmpty())
		{
			words.add(label(held, MUTED));
		}
		if (!r.line.done())
		{
			words.add(label(sourceText(r.sources), MUTED));
		}
		line.add(words, BorderLayout.CENTER);
		return line;
	}

	private static JLabel label(String text, Color color)
	{
		JLabel l = new JLabel("<html><body style='width:130px'>" + GroupIronmanPanel.escape(text) + "</body></html>");
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(color);
		return l;
	}

	/** "Bucket: need 3, have 1, get 2" / "Bucket: done (3)", with "at least" when the wiki gave no count. */
	static String rowText(ShoppingBoard.Row r)
	{
		ShopAllocation.Line l = r.line;
		String need = (l.stated ? "" : "at least ") + l.totalNeeded();
		if (l.done())
		{
			return l.name + ": done (" + need + ")";
		}
		StringBuilder s = new StringBuilder(l.name).append(": need ").append(need).append(", have ").append(l.have());
		if (l.stillNeeded() > 0)
		{
			s.append(", get ").append(l.stillNeeded());
		}
		if (l.unknownNeed() > 0)
		{
			s.append(", ").append(l.unknownNeed()).append(" not judged");
		}
		if (l.obtainable)
		{
			s.append(". Can be picked up in the quest");
		}
		return s.toString();
	}

	/** "Held: Alice 2, Bob 1, group storage 4"; empty when nobody holds any. */
	static String heldText(ShopAllocation.Line l)
	{
		Map<String, Long> all = new LinkedHashMap<>(l.holders);
		if (l.storageHeld > 0)
		{
			all.put("group storage", l.storageHeld);
		}
		if (all.isEmpty())
		{
			return "";
		}
		List<String> parts = new ArrayList<>();
		all.forEach((who, n) -> parts.add(who + " " + n));
		return "Held: " + String.join(", ", parts) + (l.tradeable ? "" : " (can't be traded: only your own counts)");
	}

	/** "Get: Shop: Tynan's Fishing Supplies (2 gp); Drop: Goblin (Always)" or why there is nowhere. */
	static String sourceText(ShopSources.Options o)
	{
		if (o.sources.isEmpty())
		{
			return o.unreachableShops > 0
				? "No shop you can use: " + o.unreachableShops + " need levels or quests you lack."
				: "No known shop or drop.";
		}
		List<String> parts = new ArrayList<>();
		for (ShopSources.Source s : o.sources.subList(0, Math.min(SOURCES_SHOWN, o.sources.size())))
		{
			parts.add(s.describe());
		}
		int more = o.sources.size() - parts.size();
		return "Get: " + String.join("; ", parts) + (more > 0 ? "; " + more + " more" : "");
	}

	private JPanel buttons(ShoppingList l)
	{
		JPanel row = new JPanel(new GridLayout(1, 0, 2, 0));
		row.setOpaque(false);
		if (l.custom())
		{
			row.add(button("Rename", () ->
			{
				String name = JOptionPane.showInputDialog(row, "New name for '" + l.name + "'", l.name);
				if (name != null)
				{
					say(actions.renameList(l.id, name) ? null : "Not renamed: log in, and give it a new name.");
				}
			}));
		}
		row.add(button("Remove", () ->
		{
			if (JOptionPane.showConfirmDialog(row, "Remove '" + l.name + "' for the whole group?", "Remove list",
				JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION)
			{
				say(actions.removeList(l.id) ? null : "Not removed: log in first.");
			}
		}));
		return row;
	}

	private void say(String text)
	{
		note = text;
		refresh.run();
	}

	private static JButton button(String text, Runnable action)
	{
		JButton b = new JButton(text);
		b.setFont(FontManager.getRunescapeSmallFont());
		b.setFocusable(false);
		b.addActionListener(e -> action.run());
		return b;
	}

	// ---- The form ----

	/** "New list": kept between redraws so typing survives them. */
	private final class Form
	{
		private final JPanel panel = new GroupIronmanPanel.Column();
		private final JComboBox<String> kind = new JComboBox<>();
		private final JPanel picks = new GroupIronmanPanel.Column();
		private final JTextField name = new JTextField();
		private final JTextField query = new JTextField();
		private final JComboBox<Hit> hits = new JComboBox<>();
		private final JSpinner quantity = new JSpinner(new SpinnerNumberModel(1, 1, ShoppingList.MAX_QUANTITY, 1));
		private final JPanel staged = new GroupIronmanPanel.Column();
		private final JPanel custom = new GroupIronmanPanel.Column();
		private final JPanel members = new GroupIronmanPanel.Column();
		private final Map<Integer, Integer> stagedItems = new LinkedHashMap<>();
		private final Map<Integer, String> stagedNames = new LinkedHashMap<>();
		private final List<JCheckBox> pickBoxes = new ArrayList<>();
		private final List<JCheckBox> memberBoxes = new ArrayList<>();
		private List<ShopPresets.Preset> presets = Collections.emptyList();
		private List<String> roster;

		Form()
		{
			panel.add(GroupIronmanPanel.heading("New list"));
			panel.add(kind);
			kind.addActionListener(e -> showKind());
			panel.add(picks);
			buildCustom();
			panel.add(custom);
			panel.add(GroupIronmanPanel.text("For:", MUTED));
			panel.add(members);
			JPanel add = new JPanel(new GridLayout(1, 1));
			add.add(button("Add list", this::submit));
			panel.add(add);
		}

		private void buildCustom()
		{
			custom.add(GroupIronmanPanel.text("Name", MUTED));
			custom.add(name);
			custom.add(GroupIronmanPanel.text("Item (tradeable items)", MUTED));
			JPanel search = new JPanel(new BorderLayout(2, 0));
			search.add(query, BorderLayout.CENTER);
			search.add(button("Search", this::search), BorderLayout.EAST);
			custom.add(search);
			custom.add(hits);
			JPanel add = new JPanel(new BorderLayout(2, 0));
			add.add(quantity, BorderLayout.CENTER);
			add.add(button("Add item", this::stage), BorderLayout.EAST);
			custom.add(add);
			custom.add(staged);
		}

		JPanel panel(View v)
		{
			if (!v.presets.equals(presets))
			{
				presets = v.presets;
				kind.removeAllItems();
				kind.addItem(CUSTOM);
				presets.forEach(p -> kind.addItem(presetLabel(p)));
			}
			if (!v.roster.equals(roster))
			{
				roster = v.roster;
				members.removeAll();
				memberBoxes.clear();
				for (String m : roster)
				{
					JCheckBox box = new JCheckBox(m, true);
					memberBoxes.add(box);
					members.add(box);
				}
			}
			return panel;
		}

		private String presetLabel(ShopPresets.Preset p)
		{
			return p.build().isEmpty() ? p.name() : p.build() + ": " + p.name();
		}

		private ShopPresets.Preset selected()
		{
			int i = kind.getSelectedIndex() - 1;
			return i >= 0 && i < presets.size() ? presets.get(i) : null;
		}

		private void showKind()
		{
			ShopPresets.Preset p = selected();
			custom.setVisible(p == null);
			picks.removeAll();
			pickBoxes.clear();
			ShopPresets.Choice choice = p == null ? null : p.choice();
			if (choice != null)
			{
				picks.add(GroupIronmanPanel.text("Pick " + choice.count() + ":", MUTED));
				for (String option : choice.from())
				{
					JCheckBox box = new JCheckBox(option, choice.defaults().contains(option));
					pickBoxes.add(box);
					picks.add(box);
				}
			}
			panel.revalidate();
			panel.repaint();
		}

		private void search()
		{
			String q = query.getText().trim();
			if (q.length() < 2)
			{
				return;
			}
			actions.searchItems(q, found -> SwingUtilities.invokeLater(() ->
			{
				hits.removeAllItems();
				found.forEach(hits::addItem);
			}));
		}

		private void stage()
		{
			Hit h = (Hit) hits.getSelectedItem();
			if (h == null || stagedItems.size() >= ShoppingList.MAX_ITEMS)
			{
				return;
			}
			stagedItems.merge(h.id, (Integer) quantity.getValue(), Integer::sum);
			stagedNames.put(h.id, h.name);
			redrawStaged();
		}

		private void redrawStaged()
		{
			staged.removeAll();
			for (Map.Entry<Integer, Integer> e : stagedItems.entrySet())
			{
				JPanel line = new JPanel(new BorderLayout(2, 0));
				line.add(GroupIronmanPanel.text(e.getValue() + " x " + stagedNames.get(e.getKey()), Color.WHITE),
					BorderLayout.CENTER);
				int id = e.getKey();
				line.add(button("x", () ->
				{
					stagedItems.remove(id);
					redrawStaged();
				}), BorderLayout.EAST);
				staged.add(line);
			}
			panel.revalidate();
			panel.repaint();
		}

		private List<String> ticked(List<JCheckBox> boxes)
		{
			List<String> out = new ArrayList<>();
			boxes.stream().filter(JCheckBox::isSelected).forEach(b -> out.add(b.getText()));
			return out;
		}

		private void submit()
		{
			ShopPresets.Preset p = selected();
			String problem = problem(p);
			if (problem != null)
			{
				say(problem);
				return;
			}
			boolean ok = p == null
				? actions.addList(name.getText(), "", Collections.emptyList(), ticked(memberBoxes), stagedItems)
				: actions.addList(p.name(), p.id(), ticked(pickBoxes), ticked(memberBoxes), Collections.emptyMap());
			if (ok && p == null)
			{
				name.setText("");
				stagedItems.clear();
				redrawStaged();
			}
			say(ok ? null : "Not added: log in first (the group holds at most " + ShoppingSync.MAX_LISTS
				+ " lists).");
		}

		/** Why the form cannot be sent as it is, or null. */
		private String problem(ShopPresets.Preset p)
		{
			if (ticked(memberBoxes).isEmpty())
			{
				return "Tick at least one member the list is for.";
			}
			if (p == null)
			{
				return name.getText().trim().isEmpty() ? "Give the list a name."
					: stagedItems.isEmpty() ? "Add at least one item." : null;
			}
			ShopPresets.Choice c = p.choice();
			return c != null && ticked(pickBoxes).size() != c.count() ? "Tick exactly " + c.count() + "." : null;
		}
	}
}
