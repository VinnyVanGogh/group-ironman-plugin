package com.vinnyvangogh.groupironman;

import java.awt.Color;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The Banks tab's banked-experience section: what the items in each member's bank and inventory (and the shared
 * storage) would give if used, for the skills picked in a row of skill icons. Defaults to the resource-using skills.
 * Swing thread only.
 */
final class BankedXpPanel
{
	static final List<String> USING = Collections.unmodifiableList(Arrays.asList("Cooking", "Crafting", "Fletching",
		"Herblore", "Smithing", "Construction", "Prayer", "Firemaking", "Runecraft", "Magic"));
	static final List<String> GATHERING = Collections.unmodifiableList(Arrays.asList("Mining", "Woodcutting",
		"Fishing", "Farming", "Hunter"));

	final JPanel root = new GroupIronmanPanel.Column();
	private final Function<String, Icon> icons;
	private final Runnable changed;
	private final Set<String> picked = new LinkedHashSet<>(USING);
	private CompareTab.View last;

	BankedXpPanel(Function<String, Icon> icons, Runnable changed)
	{
		this.icons = icons;
		this.changed = changed;
		root.setOpaque(false);
	}

	/** Skills drawn now; a copy. */
	Set<String> picked()
	{
		return new LinkedHashSet<>(picked);
	}

	/** Redraws for {@code v} (null: nothing shared yet, the section hides). */
	void update(CompareTab.View v)
	{
		last = v;
		root.removeAll();
		boolean any = v != null && (!v.banked.isEmpty() || !v.storageBanked.isEmpty());
		root.setVisible(any);
		if (any)
		{
			root.add(GroupIronmanPanel.heading("Banked XP"));
			root.add(GroupIronmanPanel.text("What the items in each bank and inventory would give if used. Pick"
				+ " skills below.", GroupIronmanPanel.MUTED));
			root.add(picker());
			Map<String, Map<String, Integer>> levels = new HashMap<>();
			v.figures.forEach(f -> levels.put(f.name, f.levels));
			v.banked.forEach((member, xp) -> root.add(block(member, xp, levels.get(member))));
			if (!v.storageBanked.isEmpty())
			{
				root.add(block("Group storage", v.storageBanked, null));
			}
		}
		root.revalidate();
		root.repaint();
	}

	private JPanel picker()
	{
		JPanel box = new GroupIronmanPanel.Column();
		box.setOpaque(false);
		JPanel quick = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		quick.setOpaque(false);
		quick.add(quickLabel("Using skills", USING));
		quick.add(quickLabel("Gathering skills", GATHERING));
		box.add(quick);
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
		row.setOpaque(false);
		List<String> all = new ArrayList<>(USING);
		all.addAll(GATHERING);
		for (String skill : all)
		{
			row.add(skillButton(skill));
		}
		box.add(row);
		return box;
	}

	private JLabel quickLabel(String text, List<String> skills)
	{
		JLabel l = Ui.label(text, FontManager.getRunescapeSmallFont(), ColorScheme.BRAND_ORANGE);
		Ui.clickable(l, () -> pick(skills));
		return l;
	}

	private JLabel skillButton(String skill)
	{
		boolean on = picked.contains(skill);
		JLabel l = new JLabel(icons.apply(skill));
		if (l.getIcon() == null)
		{
			l.setText(skill.substring(0, 3));
			l.setFont(FontManager.getRunescapeSmallFont());
			l.setForeground(Color.WHITE);
		}
		l.setToolTipText(skill + (on ? " (shown, click to hide)" : " (hidden, click to show)"));
		l.setBorder(BorderFactory.createLineBorder(on ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR));
		Ui.clickable(l, () ->
		{
			if (!picked.remove(skill))
			{
				picked.add(skill);
			}
			redraw();
		});
		return l;
	}

	private void pick(List<String> skills)
	{
		picked.clear();
		picked.addAll(skills);
		redraw();
	}

	private void redraw()
	{
		update(last);
		changed.run();
	}

	private JPanel block(String name, Map<String, Double> xp, Map<String, Integer> levels)
	{
		JPanel card = Ui.card();
		card.add(Ui.label(name, FontManager.getRunescapeBoldFont(), Color.WHITE));
		Map<String, Double> shown = new HashMap<>();
		xp.forEach((skill, v) ->
		{
			if (picked.contains(skill))
			{
				shown.put(skill, v);
			}
		});
		if (shown.isEmpty())
		{
			card.add(GroupIronmanPanel.text("Nothing banked for the picked skills.", GroupIronmanPanel.MUTED));
			return card;
		}
		// Without levels (group storage) a skill reads as xp only.
		List<BankedXp.Gain> gains = levels == null ? Collections.emptyList() : BankedXp.gains(shown, levels);
		for (BankedXp.Gain g : gains)
		{
			card.add(row(g.skill, BankedXp.describe(g)));
		}
		shown.forEach((skill, v) ->
		{
			if (gains.stream().noneMatch(g -> g.skill.equals(skill)))
			{
				card.add(row(skill, skill + ", " + String.format("%,d", Math.round(v)) + " xp"));
			}
		});
		return card;
	}

	private JLabel row(String skill, String text)
	{
		JLabel l = GroupIronmanPanel.text(text, Color.WHITE);
		l.setIcon(icons.apply(skill));
		l.setIconTextGap(4);
		return l;
	}
}
