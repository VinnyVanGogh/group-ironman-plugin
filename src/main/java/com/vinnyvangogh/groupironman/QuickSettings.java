package com.vinnyvangogh.groupironman;

import java.awt.Color;
import java.util.*;
import java.util.function.*;
import javax.swing.*;
import net.runelite.client.ui.*;

/**
 * The sidebar's quick settings: the switches players change most, each the real config key, so
 * RuneLite's settings list and this card always agree (every change comes back through
 * {@code onConfigChanged}, which redraws the panel). Keys are existing ones and are never renamed
 * (ConfigKeysTest freezes them).
 */
final class QuickSettings
{
	/** One switch: its config key, the words on the card, and how to read it with its default. */
	static final class Item
	{
		final String key;
		final String label;
		final String tooltip;
		final Predicate<GroupIronmanConfig> read;

		Item(String key, String label, String tooltip, Predicate<GroupIronmanConfig> read)
		{
			this.key = key;
			this.label = label;
			this.tooltip = tooltip;
			this.read = read;
		}
	}

	static final String TITLE = "Quick settings";
	static final String ALL = "All settings";

	/** Most-used first: what a group changes weekly, then the in-game extras. */
	static final List<Item> ITEMS = Collections.unmodifiableList(Arrays.asList(
		new Item("groupSync", "Sync with my group", "Join and share with your group's party.",
			GroupIronmanConfig::groupSync),
		new Item("discordEnabled", "Post to Discord", "Send your events to your webhooks.",
			GroupIronmanConfig::discordEnabled),
		new Item(GroupIronmanConfig.KEY_OPT_OUT_ALL, "Keep my events off Discord",
			"None of your events go to Discord, whatever the group says. Same as \"Don't post my events\".", GroupIronmanConfig::eventOptOutAll),
		new Item("groupShareBank", "Share bank", "Let your group see your bank.",
			GroupIronmanConfig::groupShareBank),
		new Item("groupShareStorage", "Share storage changes", "Tell your group about storage deposits.",
			GroupIronmanConfig::groupShareStorage),
		new Item("groupShareStats", "Share stats", "Let your group see your levels and status.",
			GroupIronmanConfig::groupShareStats),
		new Item("overlayGroupBox", "Group box", "On screen: who is online and the latest events.",
			GroupIronmanConfig::overlayGroupBox),
		new Item("groupWorldMap", "Members on world map", "Needs 'Share stats' on both sides.",
			GroupIronmanConfig::groupWorldMap),
		new Item("groupFindCommand", "::find chat command", "'::find whip' shows who has it.",
			GroupIronmanConfig::groupFindCommand),
		new Item("overlayPathStop", "Path stop card", "On screen while you path from the Shopping tab.",
			GroupIronmanConfig::overlayPathStop)));

	private QuickSettings()
	{
	}

	/** Every switch's value now, in {@link #ITEMS} order; part of the header's signature. */
	static boolean[] read(GroupIronmanConfig c)
	{
		boolean[] out = new boolean[ITEMS.size()];
		for (int i = 0; i < out.length; i++)
		{
			out[i] = ITEMS.get(i).read.test(c);
		}
		return out;
	}

	/** The card's checkboxes: a click writes the item's key through {@code set}. */
	static void draw(JPanel box, boolean[] values, BiConsumer<String, Boolean> set)
	{
		for (int i = 0; i < ITEMS.size(); i++)
		{
			Item item = ITEMS.get(i);
			JCheckBox cb = new JCheckBox(item.label, values[i]);
			cb.setFont(FontManager.getRunescapeSmallFont());
			cb.setForeground(Color.WHITE);
			cb.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			cb.setFocusable(false);
			cb.setToolTipText(item.tooltip);
			cb.addActionListener(e -> set.accept(item.key, cb.isSelected()));
			box.add(cb);
		}
	}
}
