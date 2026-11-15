package com.vinnyvangogh.groupironman;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
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
 * <p>Every list and request starts folded to a header with a preview: its
 * progress and the first still-needed items' icons (Board 2026-11-14: a 39-item
 * preset was too much). What the player opens is saved for the character
 * ({@link ShopFolds}). An open list shows still-needed items first, one compact
 * line each with the details behind a click, and its finished items folded into
 * "N done".
 *
 * <p>Rebuilt only when what it shows changed ({@link View#signature}) or a fold flipped, so a
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

		/** This player's own request; a blank name becomes "Name's requests". */
		boolean addRequest(String name, Map<Integer, Integer> items);

		boolean renameRequest(String id, String name);

		boolean removeRequest(String id);

		boolean closeRequest(String id);

		boolean closeRequestItem(String id, int itemId);

		/** "I'll get it", or taking it back. */
		boolean claimRequest(String id, int itemId, boolean claimed);

		/** What this player opened on the tab ({@link ShopFolds}), saved for this character; null when nothing is. */
		String openLists();

		/** Saves what is open for this character only; null clears it. Never sent to the group. */
		void saveOpenLists(String keys);

		/** Hands {@code to} to Shortest Path for {@code why}; the line to show. */
		String pathTo(ItemPaths.Target why, ItemPaths.Stop to);

		/** Clears the path; the line to show, empty when there was none. */
		String clearPath();
	}

	static final int MAX_HITS = 10;
	private static final Color MUTED = ShopCards.MUTED;
	private static final Color DONE = ShopCards.DONE;
	private static final String CUSTOM = "Custom list";
	private static final String REQUEST = "Request: ask the group";
	/** Entries before the presets in the kind box: custom, then request. */
	private static final int FIXED_KINDS = 2;
	/** Sources named in an item's details; the rest are a count. */
	private static final int SOURCES_SHOWN = 4;

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
		/** Members' requests, oldest first. */
		final List<RequestBoard.Card> requests;
		/** This player, for "mine" and "I'll get it"; empty when logged out. */
		final String self;
		/** Where "Path to" leads; null draws no path buttons. */
		final ItemPaths.Planner paths;
		/** The stop being pathed to; empty for none. */
		final String pathing;

		View(List<ShoppingBoard.Card> cards, List<ShopPresets.Preset> presets, List<String> roster,
			List<ShoppingSync.Change> history, boolean loggedIn, boolean syncing, List<RequestBoard.Card> requests,
			String self, ItemPaths.Planner paths, String pathing)
		{
			this.cards = cards;
			this.presets = presets;
			this.roster = roster;
			this.history = history;
			this.loggedIn = loggedIn;
			this.syncing = syncing;
			this.requests = requests;
			this.self = self;
			this.paths = paths;
			this.pathing = pathing;
		}

		/** Changes when anything drawn changes. */
		String signature()
		{
			StringBuilder s = new StringBuilder();
			s.append(roster).append(loggedIn).append(syncing).append(history.size()).append(pathing)
				.append(RequestCards.signature(requests, self, paths));
			for (ShoppingSync.Change c : history)
			{
				s.append(c.at).append(c.text);
			}
			for (ShoppingBoard.Card c : cards)
			{
				s.append('|').append(c.list.id).append(c.list.rev).append(c.progressUnknown).append(c.itemsUnknown)
					.append(paths == null ? "" : first(paths.next(c)));
				for (ShoppingBoard.Row r : c.rows)
				{
					s.append(summary(r.line)).append(needText(r.line)).append(details(r))
						.append(paths == null ? "" : first(paths.shopping(r)));
				}
			}
			return s.toString();
		}
	}

	/** The first stop's name, for signatures; empty for none. */
	static String first(List<ItemPaths.Stop> stops)
	{
		return stops.isEmpty() ? "" : stops.get(0).name;
	}

	private final Actions actions;
	private final ItemManager items;
	private final Runnable refresh;
	private final Form form = new Form();
	private final RequestCards requestCards;
	private final ShopFolds folds;
	/** Every fold key the last draw showed: a toggle keeps only these, so the saved set never outgrows the tab. */
	private final Set<String> live = new HashSet<>();
	private String drawn;
	private String note;
	/** A fold flipped: redraw even though the view did not change. */
	private boolean refold;

	ShoppingTab(Actions actions, ItemManager items, Runnable refresh)
	{
		this.actions = actions;
		this.items = items;
		this.refresh = refresh;
		this.folds = new ShopFolds(new ShopFolds.Store()
		{
			@Override
			public String load()
			{
				return actions.openLists();
			}

			@Override
			public void save(String value)
			{
				actions.saveOpenLists(value);
			}
		});
		this.requestCards = new RequestCards(actions, items, this::say, this);
	}

	/** Whether {@code v} would draw differently from what is on screen. */
	boolean changed(View v)
	{
		return !v.signature().equals(drawn) || note != null || refold;
	}

	void draw(JPanel tab, View v)
	{
		drawn = v.signature();
		refold = false;
		live.clear();
		if (note != null)
		{
			tab.add(ShopCards.tabText(note, Color.WHITE));
			note = null;
		}
		if (!v.pathing.isEmpty())
		{
			tab.add(GroupIronmanPanel.text("Pathing to " + v.pathing + " with Shortest Path.", MUTED));
			JPanel stop = new JPanel(new GridLayout(1, 1));
			stop.add(button("Stop path", () -> say(actions.clearPath())));
			tab.add(stop);
		}
		requestCards.draw(tab, v.requests, v.self, v.paths);
		if (v.cards.isEmpty() && v.requests.isEmpty())
		{
			tab.add(GroupIronmanPanel.heading("No shopping lists yet"));
			tab.add(ShopCards.tabText("Add one below: a build milestone, like Mithril gloves for a 1 Defence"
				+ " pure, your own items, or a request the group fills for you. Every member running GIM Utilities"
				+ " sees it.", MUTED));
		}
		if (!v.cards.isEmpty())
		{
			tab.add(GroupIronmanPanel.heading("Lists"));
		}
		for (ShoppingBoard.Card c : v.cards)
		{
			tab.add(card(c, v.paths));
		}
		if (!v.syncing)
		{
			tab.add(ShopCards.tabText("Not in group sync: lists stay on this client until you join.", MUTED));
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
			tab.add(ShopCards.tabText(c.text + " (" + BoostText.ago(now, c.at) + ")", MUTED));
		}
	}

	/** Whether {@code key} is drawn open; it joins the keys this draw shows. */
	boolean isOpen(String key)
	{
		live.add(key);
		return folds.open(key);
	}

	/** Flips {@code key} (a header, "N done" or an item was clicked) and redraws. */
	void fold(String key)
	{
		live.add(key);
		folds.toggle(key, live);
		refold = true;
		refresh.run();
	}

	// ---- A list ----

	/** Folded: the header and its preview. Open: who it is for, its buttons, still-needed items, then "N done". */
	private JPanel card(ShoppingBoard.Card c, ItemPaths.Planner paths)
	{
		String key = ShopFolds.list(c.list.id);
		boolean open = isOpen(key);
		List<ShoppingBoard.Row> needed = new ArrayList<>();
		List<ShoppingBoard.Row> done = new ArrayList<>();
		for (ShoppingBoard.Row r : c.rows)
		{
			(r.line.done() ? done : needed).add(r);
		}
		List<Integer> preview = new ArrayList<>();
		needed.forEach(r -> preview.add(r.line.itemId));
		JPanel box = ShopCards.box();
		ShopCards.header(box, c.list.name, open, c.done(), c.rows.size(), preview, items, () -> fold(key));
		if (!open)
		{
			return box;
		}
		box.add(new ShopCards.Wrap(byline(c.list, System.currentTimeMillis()), MUTED, ShopCards.CARD_TEXT));
		box.add(buttons(c.list));
		List<ItemPaths.Stop> tour = paths == null ? Collections.emptyList() : paths.next(c);
		if (!tour.isEmpty())
		{
			box.add(pathButton(nextLabel(tour), tour.get(0), ItemPaths.Target.next(c.list.id)));
		}
		for (String line : warnings(c))
		{
			box.add(new ShopCards.Wrap(line, ColorScheme.BRAND_ORANGE, ShopCards.CARD_TEXT));
		}
		if (c.empty())
		{
			box.add(new ShopCards.Wrap("Nothing to get: every quest is done, or the list is empty.", MUTED,
				ShopCards.CARD_TEXT));
		}
		for (ShoppingBoard.Row r : needed)
		{
			box.add(row(key, c.list.id, r, paths));
		}
		addDone(box, key, done.size(), () -> done.forEach(r -> box.add(row(key, c.list.id, r, paths))));
		return box;
	}

	/** "▸ N done", and the finished items under it once opened; nothing when none is finished. */
	void addDone(JPanel box, String cardKey, int count, Runnable drawRows)
	{
		if (count == 0)
		{
			return;
		}
		String key = ShopFolds.done(cardKey);
		boolean open = isOpen(key);
		box.add(ShopCards.doneToggle(count, open, () -> fold(key)));
		if (open)
		{
			drawRows.run();
		}
	}

	/** "Path to next: Harry's Fishing Shop (1 of 3 stops)". */
	static String nextLabel(List<ItemPaths.Stop> tour)
	{
		return "Path to next: " + tour.get(0).name + (tour.size() > 1 ? " (1 of " + tour.size() + " stops)" : "");
	}

	private JPanel pathButton(String label, ItemPaths.Stop to, ItemPaths.Target why)
	{
		return pathButton(label, () -> say(actions.pathTo(why, to)));
	}

	/** A full-width "Path to" button; the whole label is also its tooltip, for long shop names. */
	static JPanel pathButton(String label, Runnable action)
	{
		JPanel wrap = new JPanel(new GridLayout(1, 1));
		wrap.setOpaque(false);
		JButton b = button(label, action);
		b.setToolTipText(label);
		wrap.add(b);
		return wrap;
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

	/**
	 * One item, one compact line: its icon, "Air rune  12 / 30", a bar and "Need 18 more". A click (or the
	 * tooltip) shows the details: who holds it, where to get it, whether it can be traded. "Path to" under it.
	 */
	private JPanel row(String cardKey, String listId, ShoppingBoard.Row r, ItemPaths.Planner paths)
	{
		ShopAllocation.Line l = r.line;
		String key = ShopFolds.item(cardKey, l.itemId);
		boolean open = isOpen(key);
		List<String> details = details(r);
		JPanel words = new GroupIronmanPanel.Column();
		words.add(new ShopCards.Wrap(summary(l), l.done() ? DONE : Color.WHITE, ShopCards.ROW_TEXT));
		words.add(new ShopCards.Bar(l.have(), l.totalNeeded(), ShopCards.ROW_TEXT));
		ShopCards.Wrap need = new ShopCards.Wrap(needText(l), l.done() ? DONE : MUTED, ShopCards.ROW_TEXT);
		if (!details.isEmpty())
		{
			need.setIcon(new GroupIronmanPanel.Arrow(open));
			need.setIconTextGap(4);
		}
		words.add(need);
		if (open)
		{
			details.forEach(d -> words.add(new ShopCards.Wrap(d, MUTED, ShopCards.ROW_TEXT)));
		}
		List<ItemPaths.Stop> stops = paths == null ? Collections.emptyList() : paths.shopping(r);
		if (!stops.isEmpty())
		{
			words.add(pathButton(stops.get(0).label(), stops.get(0), ItemPaths.Target.listItem(listId, l.itemId)));
		}
		JPanel line = itemLine(l.itemId, words);
		if (!details.isEmpty())
		{
			line.setToolTipText("<html>" + GroupIronmanPanel.escape(String.join("\n", details)).replace("\n", "<br>")
				+ "</html>");
			ShopCards.clickable(line, () -> fold(key));
		}
		return line;
	}

	/** An item's icon on the left, {@code words} beside it. */
	JPanel itemLine(int itemId, JPanel words)
	{
		JPanel line = new JPanel(new BorderLayout(4, 0));
		line.setOpaque(false);
		line.setBorder(new EmptyBorder(3, 0, 3, 0));
		JLabel icon = new JLabel();
		icon.setVerticalAlignment(JLabel.TOP);
		if (items != null)
		{
			items.getImage(itemId).addTo(icon);
		}
		line.add(icon, BorderLayout.WEST);
		words.setOpaque(false);
		line.add(words, BorderLayout.CENTER);
		return line;
	}

	/** "Air rune  12 / 30": what the group holds towards it of everything needed, "30+" when the wiki gave no count. */
	static String summary(ShopAllocation.Line l)
	{
		return l.name + "  " + Amounts.GAME.of(l.have()) + " / " + Amounts.GAME.of(l.totalNeeded()) + (l.stated ? ""
			: "+");
	}

	/**
	 * "Need 18 more", "Done", or what a member who shares no items means for the count: "Need 18 more, plus 5 for
	 * Tin (shares no items, may already have them)". Their need is never in "more": it may be in their bank.
	 */
	static String needText(ShopAllocation.Line l)
	{
		if (l.done())
		{
			return "Done";
		}
		List<String> unseen = new ArrayList<>();
		for (ShopAllocation.Share s : l.shares)
		{
			if (s.unknown && s.need > 0)
			{
				unseen.add(s.member);
			}
		}
		String still = l.stillNeeded() > 0 ? "Need " + Amounts.GAME.of(l.stillNeeded()) + " more" : "";
		if (unseen.isEmpty())
		{
			return still;
		}
		String unknown = Amounts.GAME.of(l.unknownNeed()) + " for " + String.join(", ", unseen) + " ("
			+ (unseen.size() == 1 ? "shares" : "share") + " no items, may already have them)";
		return still.isEmpty() ? "Need " + unknown : still + ", plus " + unknown;
	}

	/** Behind the click: who holds it, whether it can be traded, a quest pickup, and where to get more. */
	static List<String> details(ShoppingBoard.Row r)
	{
		ShopAllocation.Line l = r.line;
		List<String> out = new ArrayList<>();
		String held = heldText(l);
		if (!held.isEmpty())
		{
			out.add(held);
		}
		if (!l.tradeable)
		{
			out.add("Can't be traded: each member's own copy counts only for them.");
		}
		if (l.obtainable)
		{
			out.add("Can also be picked up during the quest.");
		}
		if (!l.done())
		{
			out.add(sourceText(r.sources));
		}
		return out;
	}

	/** "Held by Alice 2, Bob 1, group storage 4"; empty when nobody holds any. */
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
		all.forEach((who, n) -> parts.add(who + " " + Amounts.GAME.of(n)));
		return "Held by " + String.join(", ", parts);
	}

	/** "Get: Shop: Tynan's Fishing Supplies (2 gp); Drop: Goblin (Always)" or why there is nowhere. */
	static String sourceText(ShopSources.Options o)
	{
		if (o.sources.isEmpty())
		{
			return o.unreachableShops > 0
				? "No shop you can use: " + o.unreachableShops + " need levels or quests you lack."
				: "Not sold in any shop, and no drop on file: check the wiki.";
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
		note = text == null || text.isEmpty() ? null : text;
		refresh.run();
	}

	static JButton button(String text, Runnable action)
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
		private final JLabel forLabel = ShopCards.tabText("For:", MUTED);
		private final JLabel nameLabel = ShopCards.tabText("Name", MUTED);
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
			panel.add(forLabel);
			panel.add(members);
			JPanel add = new JPanel(new GridLayout(1, 1));
			add.add(button("Add list", this::submit));
			panel.add(add);
		}

		private void buildCustom()
		{
			custom.add(nameLabel);
			custom.add(name);
			custom.add(ShopCards.tabText("Item (tradeable items)", MUTED));
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
				kind.addItem(REQUEST);
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
			int i = kind.getSelectedIndex() - FIXED_KINDS;
			return i >= 0 && i < presets.size() ? presets.get(i) : null;
		}

		/** A request is always this player's own: no members to tick, and the name may be left blank. */
		private boolean request()
		{
			return REQUEST.equals(kind.getSelectedItem());
		}

		private void showKind()
		{
			ShopPresets.Preset p = selected();
			custom.setVisible(p == null);
			forLabel.setVisible(!request());
			members.setVisible(!request());
			nameLabel.setText("<html>" + (request() ? "Name (blank: your name's requests)" : "Name") + "</html>");
			picks.removeAll();
			pickBoxes.clear();
			ShopPresets.Choice choice = p == null ? null : p.choice();
			if (choice != null)
			{
				picks.add(ShopCards.tabText("Pick " + choice.count() + ":", MUTED));
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
				line.add(new ShopCards.Wrap(Amounts.GAME.of(e.getValue()) + " x " + stagedNames.get(e.getKey()),
					Color.WHITE, ShopCards.TAB_TEXT - 40), BorderLayout.CENTER);
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
			if (request())
			{
				submitRequest();
				return;
			}
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

		private void submitRequest()
		{
			if (stagedItems.isEmpty())
			{
				say("Add at least one item.");
				return;
			}
			boolean ok = actions.addRequest(name.getText(), stagedItems);
			if (ok)
			{
				name.setText("");
				stagedItems.clear();
				redrawStaged();
			}
			say(ok ? null : "Not added: log in first (the group holds at most " + RequestBook.MAX_REQUESTS
				+ " requests).");
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
