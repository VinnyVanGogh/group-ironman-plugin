package com.vinnyvangogh.groupironman;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * The panel's Gear tab: "bring this" for the place the player is standing (picked from the region on entry) or one
 * chosen by hand, each line OK, Equip (carried, not worn), Short or Missing, and for a missing one who in the group
 * has it. "Make shopping list" turns every gap into one request and one shopping list; "Edit" changes the list,
 * and the player can add places of their own. Edits are saved for this player only ({@link GearBook}); nothing is
 * sent unless the player presses a request or shopping button.
 */
final class GearTab
{
	private static final Color OK = new Color(0x3A, 0x9A, 0x4A);
	private static final Color EQUIP = new Color(0xC8, 0xA0, 0x2A);
	private static final Color MISSING = new Color(0xB0, 0x2E, 0x26);
	private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR;

	/** What the tab's buttons do. {@code done} may be called on any thread. */
	interface Actions
	{
		/**
		 * Asks the group for this item ({@code shopping} false) or puts it on this player's own shopping list
		 * ({@code shopping} true); {@code done} gets the line to show.
		 */
		default void gearItem(String itemName, boolean shopping, Consumer<String> done)
		{
			done.accept("Not available yet.");
		}

		/** "Make shopping list" for {@code place}; {@code done} gets the line to show. */
		default void gearShoppingList(String place, GearChecks.Plan plan, Consumer<String> done)
		{
			done.accept("Not available yet.");
		}

		/** Saves the view's {@link GearBook} after an edit. */
		default void saveGearBook()
		{
		}
	}

	/** What a redraw reads. */
	static final class View
	{
		final String self;
		final int region;
		final Map<Integer, Integer> inventory;
		final Map<Integer, Integer> worn;
		final List<GroupSearch.Source> sources;
		final ItemLookup items;
		final GearBook book;

		View(String self, int region, Map<Integer, Integer> inventory, Map<Integer, Integer> worn,
			List<GroupSearch.Source> sources, ItemLookup items, GearBook book)
		{
			this.self = self;
			this.region = region;
			this.inventory = inventory;
			this.worn = worn;
			this.sources = sources;
			this.items = items;
			this.book = book == null ? GearBook.empty() : book;
		}
	}

	private final Actions actions;
	private final ItemManager itemManager;
	private final Runnable redraw;
	private final JComboBox<GearChecks.Rule> picker = new JComboBox<>();
	private final JTextField addName = new JTextField();
	private final JComboBox<GearChecks.Kind> addKind = new JComboBox<>(GearChecks.Kind.values());
	private final JTextField title = new JTextField();
	private String selectedId = GearChecks.RULES.get(0).id;
	private List<String> listed = Collections.emptyList();
	private int lastRegion = Integer.MIN_VALUE;
	private boolean filling;
	private boolean editing;
	private boolean creating;
	private String status = "";

	GearTab(Actions actions, ItemManager itemManager, Runnable redraw)
	{
		this.actions = actions;
		this.itemManager = itemManager;
		this.redraw = redraw;
		addKind.removeItem(GearChecks.Kind.NONE);
		picker.addActionListener(e ->
		{
			Object picked = picker.getSelectedItem();
			if (!filling && picked instanceof GearChecks.Rule)
			{
				selectedId = ((GearChecks.Rule) picked).id;
				status = "";
				redraw.run();
			}
		});
	}

	/** Refills the selector when the book's rules changed (an added, removed or renamed place). */
	private void fill(List<GearChecks.Rule> rules, GearChecks.Rule selected)
	{
		List<String> keys = new ArrayList<>();
		for (GearChecks.Rule r : rules)
		{
			keys.add(r.id + "\n" + r.title);
		}
		filling = true;
		if (!keys.equals(listed))
		{
			picker.removeAllItems();
			rules.forEach(picker::addItem);
			listed = keys;
		}
		for (int i = 0; i < picker.getItemCount(); i++)
		{
			if (picker.getItemAt(i).id.equals(selected.id))
			{
				picker.setSelectedIndex(i);
			}
		}
		filling = false;
	}

	/** The rule region entry picked, or null; also moves the selector. Swing thread. */
	GearChecks.Rule onRegion(int region, GearBook book)
	{
		if (region == lastRegion)
		{
			return null;
		}
		lastRegion = region;
		GearChecks.Rule r = book.forRegion(region);
		if (r != null && !r.id.equals(selectedId))
		{
			selectedId = r.id;
			return r;
		}
		return null;
	}

	void draw(JPanel tab, View v)
	{
		GearBook book = v.book;
		onRegion(v.region, book);
		GearChecks.Rule selected = book.byId(selectedId);
		if (selected == null)
		{
			selected = book.rules().get(0);
			selectedId = selected.id;
		}
		fill(book.rules(), selected);

		tab.add(GroupIronmanPanel.heading("Bring this"));
		tab.add(GroupIronmanPanel.text("Picks itself when you enter a boss, raid or the Revenant Caves; or choose one."
			+ " Reads what you carry and what the group shares.", MUTED));
		JPanel pick = new JPanel(new BorderLayout(4, 0));
		pick.setBorder(new EmptyBorder(4, 0, 4, 0));
		pick.add(picker, BorderLayout.CENTER);
		JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
		JToggleButton edit = new JToggleButton(GearIcons.glyph(GearIcons.Glyph.PENCIL), editing);
		edit.setFocusable(false);
		edit.setToolTipText(editing ? "Done editing" : "Edit this list: items, counts, worn, optional");
		edit.addActionListener(e ->
		{
			editing = !editing;
			status = "";
			redraw.run();
		});
		tools.add(edit);
		tools.add(icon(GearIcons.Glyph.NEW, "Add a list for a place of your own", () ->
		{
			creating = !creating;
			title.setText("");
			redraw.run();
		}));
		pick.add(tools, BorderLayout.EAST);
		tab.add(pick);
		if (creating)
		{
			tab.add(creator(book, v.region));
		}

		if (book.shippedChanged(selected.id))
		{
			tab.add(shippedChanged(book, selected));
		}

		Set<String> skipped = book.skipped(selected.id);
		for (String fight : selected.fights())
		{
			tab.add(skipToggle(book, selected, fight, skipped.stream().anyMatch(fight::equalsIgnoreCase)));
		}

		List<GearChecks.Line> lines = GearChecks.check(selected, skipped, v.inventory, v.worn, v.sources, v.self,
			v.items);
		int problems = GearChecks.problems(lines);
		tab.add(GroupIronmanPanel.text(selected.reqs.isEmpty() ? "Nothing on this list yet: press the pencil to add."
			: problems == 0 ? "All set for " + selected.title + "."
			: problems + " to sort out for " + selected.title + ".", problems == 0 ? OK : EQUIP));
		GearChecks.Plan plan = GearChecks.plan(lines);
		if (!plan.empty())
		{
			String place = selected.title;
			JButton make = Ui.button("Make shopping list", "Ask the group for what a teammate holds ("
				+ plan.request.size() + "), and list the rest for yourself (" + plan.shop.size() + ")",
				() -> actions.gearShoppingList(place, plan, this::show));
			make.setIcon(GearIcons.glyph(GearIcons.Glyph.CART));
			tab.add(Ui.wide(make));
		}
		if (!plan.unnamed.isEmpty())
		{
			tab.add(GroupIronmanPanel.text("Nobody holds one for: " + String.join(", ", plan.unnamed)
				+ ". Edit the line to name an item to buy.", MUTED));
		}
		for (GearChecks.Line line : lines)
		{
			tab.add(row(line));
		}
		if (editing)
		{
			editor(tab, book, selected, v.region);
		}
		if (!status.isEmpty())
		{
			tab.add(GroupIronmanPanel.text(status, MUTED));
		}
	}

	/** An edited starting list whose shipped version changed since: reset to it, or keep the edit and stop asking. */
	private JPanel shippedChanged(GearBook book, GearChecks.Rule rule)
	{
		JPanel box = Ui.card();
		box.add(GroupIronmanPanel.text("The default list for " + rule.title + " changed since you edited it.", EQUIP));
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
		JButton reset = Ui.button("Reset to default", "Replace your edit with the new default list", () ->
		{
			book.reset(rule.id);
			actions.saveGearBook();
			redraw.run();
		});
		reset.setIcon(GearIcons.glyph(GearIcons.Glyph.RESET));
		buttons.add(reset);
		buttons.add(Ui.button("Keep mine", "Keep your edited list and stop showing this", () ->
		{
			book.keepMine(rule.id);
			actions.saveGearBook();
			redraw.run();
		}));
		box.add(buttons);
		return box;
	}

	private JToggleButton skipToggle(GearBook book, GearChecks.Rule rule, String fight, boolean on)
	{
		JToggleButton t = Ui.toggle("Skip " + fight, on, 2);
		t.setToolTipText(on ? "Count " + fight + "'s line again" : "Not fighting " + fight + ": drop that line");
		t.addActionListener(e ->
		{
			book.skip(rule.id, fight, t.isSelected());
			actions.saveGearBook();
			redraw.run();
		});
		return t;
	}

	private JPanel creator(GearBook book, int region)
	{
		JPanel box = Ui.card();
		box.add(GroupIronmanPanel.text("Name your place", MUTED));
		box.add(title);
		JCheckBox here = new JCheckBox("Pick it when I'm here", region > 0);
		here.setEnabled(region > 0);
		here.setFont(FontManager.getRunescapeSmallFont());
		box.add(here);
		box.add(Ui.wide(Ui.button("Create list", null, () ->
		{
			GearChecks.Rule r = book.addCustom(title.getText(), here.isSelected() ? region : 0);
			if (r == null)
			{
				status = "You have " + GearBook.MAX_CUSTOM + " lists already.";
			}
			else
			{
				selectedId = r.id;
				editing = true;
				creating = false;
				status = "";
				actions.saveGearBook();
			}
			redraw.run();
		})));
		return box;
	}

	private JPanel row(GearChecks.Line line)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBorder(new EmptyBorder(3, 0, 3, 0));
		String tag;
		Color color;
		switch (line.state)
		{
			case OK:
				tag = "OK";
				color = OK;
				break;
			case EQUIP:
				tag = "Equip";
				color = EQUIP;
				break;
			case SHORT:
				tag = line.have + "/" + line.req.count;
				color = EQUIP;
				break;
			default:
				tag = "Missing";
				color = line.req.optional ? MUTED : MISSING;
				break;
		}
		row.add(itemIcon(line, color), BorderLayout.WEST);
		JPanel body = new GroupIronmanPanel.Column();
		String count = line.req.count > 1 && line.state != GearChecks.State.SHORT ? " x" + line.req.count : "";
		JLabel head = Ui.label(tag + "  " + line.req.label + count + (line.req.optional ? " (optional)" : ""),
			FontManager.getRunescapeSmallFont(), color);
		head.setToolTipText("<html>" + GroupIronmanPanel.escape(line.req.describe()) + "</html>");
		body.add(head);
		if (line.state == GearChecks.State.EQUIP)
		{
			body.add(GroupIronmanPanel.text("In your inventory: wear it.", MUTED));
		}
		else if (line.requestable())
		{
			body.add(GroupIronmanPanel.text("Has it: " + String.join("; ", line.holders), MUTED));
			body.add(Ui.button("Request from group", "Ask the group for " + line.itemName, () -> send(line, false)));
		}
		else if (line.nobody() && !line.itemName.isEmpty())
		{
			body.add(GroupIronmanPanel.text("Nobody in the group has shared one.", MUTED));
			body.add(Ui.button("Add to shopping", "Put " + line.itemName + " on your own shopping list",
				() -> send(line, true)));
		}
		row.add(body, BorderLayout.CENTER);
		return row;
	}

	/** The item's picture when one is known (the player's own, or a teammate's), else a dot in the state colour. */
	private JLabel itemIcon(GearChecks.Line line, Color color)
	{
		JLabel icon = new JLabel(GearIcons.dot(color));
		icon.setVerticalAlignment(JLabel.TOP);
		if (line.itemId >= 0 && itemManager != null)
		{
			AsyncBufferedImage image = itemManager.getImage(line.itemId);
			if (image != null)
			{
				icon.setIcon(null);
				image.addTo(icon);
			}
		}
		return icon;
	}

	/** The pencil's view: every line with count, worn and optional controls; add by name or by kind. */
	private void editor(JPanel tab, GearBook book, GearChecks.Rule rule, int region)
	{
		JPanel box = Ui.card();
		box.add(GroupIronmanPanel.heading("Edit " + rule.title));
		for (int i = 0; i < rule.reqs.size(); i++)
		{
			box.add(editRow(book, rule, i));
		}
		box.add(GroupIronmanPanel.text("Add an item by name (doses count: \"Prayer potion\")", MUTED));
		JPanel byName = new JPanel(new BorderLayout(4, 0));
		byName.add(addName, BorderLayout.CENTER);
		byName.add(icon(GearIcons.Glyph.PLUS, "Add this item", () ->
		{
			String name = addName.getText().trim();
			if (name.isEmpty())
			{
				return;
			}
			addName.setText("");
			change(book,add(rule, new GearChecks.Req(name, GearChecks.Need.CARRIED, name)));
		}), BorderLayout.EAST);
		box.add(byName);
		box.add(GroupIronmanPanel.text("Or any one of a kind", MUTED));
		JPanel byKind = new JPanel(new BorderLayout(4, 0));
		byKind.add(addKind, BorderLayout.CENTER);
		byKind.add(icon(GearIcons.Glyph.PLUS, "Add a line any item of this kind satisfies", () ->
		{
			GearChecks.Kind k = (GearChecks.Kind) addKind.getSelectedItem();
			if (k != null)
			{
				change(book,add(rule, new GearChecks.Req(k.label, Collections.emptyList(),
					GearChecks.Need.CARRIED, 1, k, false, "")));
			}
		}), BorderLayout.EAST);
		box.add(byKind);
		if (rule.custom)
		{
			box.add(GroupIronmanPanel.text("Name", MUTED));
			JPanel name = new JPanel(new BorderLayout(4, 0));
			JTextField field = new JTextField(rule.title);
			name.add(field, BorderLayout.CENTER);
			name.add(icon(GearIcons.Glyph.PENCIL, "Rename", () -> change(book,rule.withTitle(field.getText()))),
				BorderLayout.EAST);
			box.add(name);
			JPanel more = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
			if (region > 0)
			{
				more.add(Ui.button("Pick it here", "Open this list when you enter this area (region " + region + ")",
					() -> change(book,rule.withRegions(new int[] {region}))));
			}
			if (rule.regions.length > 0)
			{
				more.add(Ui.button("Never pick it", "Only open this list by hand",
					() -> change(book,rule.withRegions(new int[0]))));
			}
			JButton delete = icon(GearIcons.Glyph.CROSS, "Delete this list", () ->
			{
				book.remove(rule.id);
				actions.saveGearBook();
				selectedId = GearChecks.RULES.get(0).id;
				editing = false;
				redraw.run();
			});
			more.add(delete);
			box.add(more);
		}
		else if (book.edited(rule.id))
		{
			JButton reset = Ui.button("Reset to default", "Put back the shipped list for " + rule.title, () ->
			{
				book.reset(rule.id);
				actions.saveGearBook();
				redraw.run();
			});
			reset.setIcon(GearIcons.glyph(GearIcons.Glyph.RESET));
			box.add(Ui.wide(reset));
		}
		tab.add(box);
	}

	private JPanel editRow(GearBook book, GearChecks.Rule rule, int i)
	{
		GearChecks.Req req = rule.reqs.get(i);
		JPanel row = new GroupIronmanPanel.Column();
		row.setBorder(new EmptyBorder(2, 0, 2, 0));
		JLabel label = Ui.label(req.label + (req.fight.isEmpty() ? "" : " (" + req.fight + ")"),
			FontManager.getRunescapeSmallFont(), null);
		label.setToolTipText("<html>" + GroupIronmanPanel.escape(req.describe()) + "</html>");
		row.add(label);
		JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
		boolean worn = req.need == GearChecks.Need.WORN;
		controls.add(icon(GearIcons.Glyph.MINUS, "One fewer", () -> change(book,replace(rule, i,
			req.count(req.count - 1)))));
		controls.add(Ui.label(Integer.toString(req.count), FontManager.getRunescapeSmallFont(), null));
		controls.add(icon(GearIcons.Glyph.PLUS, "One more", () -> change(book,replace(rule, i,
			req.count(req.count + 1)))));
		JToggleButton wear = Ui.toggle("Worn", worn, 0);
		wear.setToolTipText(worn ? "Carried is enough" : "Must be worn");
		wear.addActionListener(e -> change(book,replace(rule, i,
			req.need(worn ? GearChecks.Need.CARRIED : GearChecks.Need.WORN))));
		controls.add(wear);
		JToggleButton opt = Ui.toggle("Optional", req.optional, 0);
		opt.setToolTipText("Optional lines are shown but never counted or put on a shopping list");
		opt.addActionListener(e -> change(book,replace(rule, i, req.optional(!req.optional))));
		controls.add(opt);
		controls.add(icon(GearIcons.Glyph.CROSS, "Remove this line", () ->
		{
			List<GearChecks.Req> next = new ArrayList<>(rule.reqs);
			next.remove(i);
			change(book,rule.withReqs(next));
		}));
		row.add(controls);
		return row;
	}

	private static GearChecks.Rule add(GearChecks.Rule rule, GearChecks.Req req)
	{
		List<GearChecks.Req> next = new ArrayList<>(rule.reqs);
		next.add(req);
		return rule.withReqs(next);
	}

	private static GearChecks.Rule replace(GearChecks.Rule rule, int i, GearChecks.Req req)
	{
		List<GearChecks.Req> next = new ArrayList<>(rule.reqs);
		next.set(i, req);
		return rule.withReqs(next);
	}

	private void change(GearBook book, GearChecks.Rule after)
	{
		if (after.reqs.size() > GearBook.MAX_LINES)
		{
			status = "A list holds " + GearBook.MAX_LINES + " lines at most.";
		}
		else if (book.put(after))
		{
			actions.saveGearBook();
			status = "";
		}
		redraw.run();
	}

	private static JButton icon(GearIcons.Glyph glyph, String tooltip, Runnable action)
	{
		JButton b = new JButton(GearIcons.glyph(glyph));
		b.setFocusable(false);
		b.setToolTipText(tooltip);
		b.setMargin(new java.awt.Insets(2, 2, 2, 2));
		b.addActionListener(e -> action.run());
		return b;
	}

	private void send(GearChecks.Line line, boolean shopping)
	{
		actions.gearItem(line.itemName, shopping, this::show);
	}

	private void show(String text)
	{
		SwingUtilities.invokeLater(() ->
		{
			status = text == null ? "" : text;
			redraw.run();
		});
	}

	/** An empty view for a player with nothing shared yet. */
	static View empty(String self, int region, ItemLookup items)
	{
		return new View(self, region, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList(), items,
			GearBook.empty());
	}
}
