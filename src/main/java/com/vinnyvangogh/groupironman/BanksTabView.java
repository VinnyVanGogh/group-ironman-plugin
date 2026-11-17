package com.vinnyvangogh.groupironman;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The Banks tab's cards, kept between syncs. The cards are built once per set
 * of members; a sync then changes only the text and grid cells that differ,
 * so the tab never blanks and the Swing thread is back for the game's clicks
 * within a few milliseconds. Swing thread only.
 */
final class BanksTabView
{
	private final JPanel tab;
	private final BankFolds folds;
	private final ItemGridView.CellPainter painter;
	private final int columns;
	private final int max;
	/** Draws the tab again from the last model: a fold changed, no data did. */
	private final Runnable refold;
	private final Map<String, Card> cards = new LinkedHashMap<>();
	/** Whether each bank has an item matching the filter, from the last {@link #apply}. */
	private Map<String, Boolean> matches = Collections.emptyMap();
	private JLabel foldAll;
	private boolean showing;
	private int rebuilds;

	BanksTabView(JPanel tab, BankFolds folds, ItemGridView.CellPainter painter, int columns, int max, Runnable refold)
	{
		this.tab = tab;
		this.folds = folds;
		this.painter = painter;
		this.columns = columns;
		this.max = max;
		this.refold = refold;
	}

	/** The tab now shows something else (an empty state): the next {@link #apply} builds the cards again. */
	void clear()
	{
		showing = false;
		cards.clear();
		foldAll = null;
	}

	/** Times the cards were built from scratch: tests check a sync does not. */
	int rebuilds()
	{
		return rebuilds;
	}

	/** Shows {@code banks}; returns how many grid cells changed. */
	int apply(List<PanelModel.BankCard> banks, String self, String filter)
	{
		folds.filter(filter);
		Map<String, Boolean> m = new LinkedHashMap<>();
		banks.forEach(b -> m.put(b.member, !b.items.isEmpty()));
		matches = m;
		if (!showing || !new ArrayList<>(cards.keySet()).equals(new ArrayList<>(m.keySet())))
		{
			build(m.keySet());
		}
		if (foldAll != null)
		{
			foldAll.setText(anyOpen() ? "Collapse all" : "Expand all");
		}
		int touched = 0;
		for (PanelModel.BankCard b : banks)
		{
			touched += cards.get(b.member).update(b, self, folds.open(b.member, m.get(b.member)));
		}
		return touched;
	}

	private boolean anyOpen()
	{
		return folds.anyOpen(matches.keySet(), matches::get);
	}

	private void build(Collection<String> members)
	{
		tab.removeAll();
		cards.clear();
		foldAll = null;
		if (members.size() >= 2)
		{
			// Collapse all while any bank is open, Expand all once every one is folded.
			foldAll = Ui.label("", FontManager.getRunescapeSmallFont(), ColorScheme.BRAND_ORANGE);
			foldAll.setBorder(new EmptyBorder(0, 0, 4, 0));
			Ui.clickable(foldAll, () ->
			{
				folds.setAll(matches.keySet(), !anyOpen());
				refold.run();
			});
			tab.add(foldAll);
		}
		for (String member : members)
		{
			Card card = new Card(member);
			cards.put(member, card);
			tab.add(card.root);
		}
		showing = true;
		rebuilds++;
		tab.revalidate();
		tab.repaint();
	}

	private final class Card
	{
		final JPanel root = Ui.card();
		final String member;
		final JLabel header = GroupIronmanPanel.foldLabel("", true, Color.WHITE);
		final JLabel summary = GroupIronmanPanel.text("", GroupIronmanPanel.MUTED);
		final JLabel noMatch = GroupIronmanPanel.text("No item matches the filter.", GroupIronmanPanel.MUTED);
		final ItemGridView grid = new ItemGridView(painter, columns, max);

		Card(String member)
		{
			this.member = member;
			Ui.onPress(header, () ->
			{
				folds.toggle(member, matches.getOrDefault(member, false));
				refold.run();
			});
			root.add(header);
			root.add(summary);
			root.add(noMatch);
			root.add(grid.component());
		}

		/** Swing skips a setText or setVisible that changes nothing, so an unchanged card costs no layout. */
		int update(PanelModel.BankCard b, String self, boolean open)
		{
			boolean mine = !self.isEmpty() && self.equalsIgnoreCase(member);
			header.setText(mine ? "Your bank (" + member + ")" : member + "'s bank");
			if (((GroupIronmanPanel.Arrow) header.getIcon()).open != open)
			{
				header.setIcon(new GroupIronmanPanel.Arrow(open));
			}
			summary.setText(GroupIronmanPanel.html(b.summary));
			summary.setToolTipText(b.exact);
			noMatch.setVisible(open && b.items.isEmpty());
			grid.component().setVisible(open && !b.items.isEmpty());
			// A folded grid waits: opening it applies the model again.
			return open ? grid.update(b.items) : 0;
		}
	}
}
