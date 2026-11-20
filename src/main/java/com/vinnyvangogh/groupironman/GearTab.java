package com.vinnyvangogh.groupironman;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The panel's Gear tab: "bring this" for the place the player is standing (picked from the region on entry) or one
 * chosen by hand, each line OK, Equip (carried, not worn) or Missing, and for a missing one who in the group has it.
 * Reads what members share; nothing is sent unless the player presses "Request from group" or "Add to shopping".
 */
final class GearTab
{
	private static final Color OK = new Color(0x3A, 0x9A, 0x4A);
	private static final Color EQUIP = new Color(0xC8, 0xA0, 0x2A);
	private static final Color MISSING = new Color(0xB0, 0x2E, 0x26);

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

		View(String self, int region, Map<Integer, Integer> inventory, Map<Integer, Integer> worn,
			List<GroupSearch.Source> sources, ItemLookup items)
		{
			this.self = self;
			this.region = region;
			this.inventory = inventory;
			this.worn = worn;
			this.sources = sources;
			this.items = items;
		}
	}

	private final Actions actions;
	private final Runnable redraw;
	private final JComboBox<GearChecks.Rule> picker = new JComboBox<>();
	private GearChecks.Rule selected = GearChecks.RULES.get(0);
	private int lastRegion = Integer.MIN_VALUE;
	private boolean filling;
	private String status = "";

	GearTab(Actions actions, Runnable redraw)
	{
		this.actions = actions;
		this.redraw = redraw;
		filling = true;
		GearChecks.RULES.forEach(picker::addItem);
		picker.setSelectedItem(selected);
		filling = false;
		picker.addActionListener(e ->
		{
			if (!filling && picker.getSelectedItem() != null)
			{
				selected = (GearChecks.Rule) picker.getSelectedItem();
				status = "";
				redraw.run();
			}
		});
	}

	/** The rule region entry picked, or null; also moves the selector. Swing thread. */
	GearChecks.Rule onRegion(int region)
	{
		if (region == lastRegion)
		{
			return null;
		}
		lastRegion = region;
		GearChecks.Rule r = GearChecks.forRegion(region);
		if (r != null && r != selected)
		{
			selected = r;
			filling = true;
			picker.setSelectedItem(r);
			filling = false;
			return r;
		}
		return null;
	}

	void draw(JPanel tab, View v)
	{
		onRegion(v.region);
		tab.add(GroupIronmanPanel.heading("Bring this"));
		tab.add(GroupIronmanPanel.text("Picks itself when you enter a boss, raid or the Revenant Caves; or choose one."
			+ " Reads what you carry and what the group shares.", ColorScheme.LIGHT_GRAY_COLOR));
		JPanel pick = new JPanel(new BorderLayout());
		pick.setBorder(new EmptyBorder(4, 0, 4, 0));
		pick.add(picker, BorderLayout.CENTER);
		tab.add(pick);

		List<GearChecks.Line> lines = GearChecks.check(selected, v.inventory, v.worn, v.sources, v.self, v.items);
		int problems = GearChecks.problems(lines);
		tab.add(GroupIronmanPanel.text(problems == 0 ? "All set for " + selected.title + "."
			: problems + " to sort out for " + selected.title + ".", problems == 0 ? OK : EQUIP));
		for (GearChecks.Line line : lines)
		{
			tab.add(row(line));
		}
		if (!status.isEmpty())
		{
			tab.add(GroupIronmanPanel.text(status, ColorScheme.LIGHT_GRAY_COLOR));
		}
	}

	private JPanel row(GearChecks.Line line)
	{
		JPanel row = new GroupIronmanPanel.Column();
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
			default:
				tag = "Missing";
				color = MISSING;
				break;
		}
		JLabel head = Ui.label(tag + "  " + line.req.label, FontManager.getRunescapeSmallFont(), color);
		head.setToolTipText("<html>" + GroupIronmanPanel.escape("Any of: " + String.join(", ", line.req.names)
			+ (line.req.need == GearChecks.Need.WORN ? " (worn)" : "")) + "</html>");
		row.add(head);
		if (line.state == GearChecks.State.EQUIP)
		{
			row.add(GroupIronmanPanel.text("In your inventory: wear it.", ColorScheme.LIGHT_GRAY_COLOR));
		}
		else if (line.requestable())
		{
			row.add(GroupIronmanPanel.text("Has it: " + String.join("; ", line.holders), ColorScheme.LIGHT_GRAY_COLOR));
			row.add(Ui.button("Request from group", "Ask the group for " + line.itemName, () -> send(line, false)));
		}
		else if (line.nobody())
		{
			row.add(GroupIronmanPanel.text("Nobody in the group has shared one.", ColorScheme.LIGHT_GRAY_COLOR));
			row.add(Ui.button("Add to shopping", "Put " + line.itemName + " on your own shopping list",
				() -> send(line, true)));
		}
		return row;
	}

	private void send(GearChecks.Line line, boolean shopping)
	{
		actions.gearItem(line.itemName, shopping, text -> SwingUtilities.invokeLater(() ->
		{
			status = text == null ? "" : text;
			redraw.run();
		}));
	}

	/** An empty view for a player with nothing shared yet. */
	static View empty(String self, int region, ItemLookup items)
	{
		return new View(self, region, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList(), items);
	}
}
