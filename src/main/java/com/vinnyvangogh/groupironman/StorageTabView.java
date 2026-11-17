package com.vinnyvangogh.groupironman;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The Storage tab, built once and then changed in place: the summary's text,
 * the grid cells that differ ({@link ItemGridView}) and the change log's lines.
 * Swing thread only.
 */
final class StorageTabView
{
	private final JPanel tab;
	private final JLabel warning = GroupIronmanPanel.text("'Share group storage changes' is off: your own visits are"
		+ " kept here only and nothing is sent, and members' changes don't arrive.", ColorScheme.BRAND_ORANGE);
	private final JLabel summary = GroupIronmanPanel.text("", GroupIronmanPanel.MUTED);
	private final JLabel notSeen = GroupIronmanPanel.text("Not seen yet. Open group storage once and close it; what it"
		+ " holds shows here.", GroupIronmanPanel.MUTED);
	private final JLabel noItems = GroupIronmanPanel.text("Empty, or no item matches the filter.",
		GroupIronmanPanel.MUTED);
	private final JLabel noChanges = GroupIronmanPanel.text("No changes yet. Deposits and withdrawals are logged when"
		+ " you press Save or close storage. Members' changes arrive while you are synced with them.",
		GroupIronmanPanel.MUTED);
	private final JPanel rows = new GroupIronmanPanel.Column();
	private final List<JLabel> rowLabels = new ArrayList<>();
	private final ItemGridView grid;
	private boolean built;

	StorageTabView(JPanel tab, ItemGridView.CellPainter painter, int columns, int max)
	{
		this.tab = tab;
		this.grid = new ItemGridView(painter, columns, max);
		rows.setOpaque(false);
	}

	/** The tab was cleared (a failed draw): the next {@link #apply} builds it again. */
	void clear()
	{
		built = false;
	}

	/** Shows {@code s}; returns how many grid cells and log lines changed. */
	int apply(PanelModel.StorageView s, boolean sharing)
	{
		if (!built)
		{
			build();
		}
		warning.setVisible(!sharing);
		boolean seen = s.snapshot != null;
		notSeen.setVisible(!seen);
		summary.setVisible(seen);
		noItems.setVisible(seen && s.items.isEmpty());
		grid.component().setVisible(seen && !s.items.isEmpty());
		int touched = 0;
		if (seen)
		{
			summary.setText(GroupIronmanPanel.html(s.snapshot + ", " + Amounts.GAME.of(s.value) + " gp"));
			summary.setToolTipText(Amounts.EXACT.of(s.value) + " gp");
			touched += grid.update(s.items);
		}
		noChanges.setVisible(s.log.isEmpty());
		return touched + updateRows(s.log);
	}

	private void build()
	{
		tab.removeAll();
		rowLabels.clear();
		rows.removeAll();
		tab.add(warning);
		JPanel contents = Ui.card();
		contents.add(Ui.label("Group storage", FontManager.getRunescapeBoldFont(), null));
		contents.add(summary);
		contents.add(notSeen);
		contents.add(noItems);
		contents.add(grid.component());
		tab.add(contents);
		JPanel log = Ui.card();
		log.add(Ui.label("Changes", FontManager.getRunescapeBoldFont(), null));
		log.add(noChanges);
		log.add(rows);
		tab.add(log);
		built = true;
		tab.revalidate();
		tab.repaint();
	}

	/** One label per log line, kept: a new visit or a new minute changes a line's text, not the labels. */
	private int updateRows(List<PanelModel.LogRow> log)
	{
		int touched = 0;
		int before = rowLabels.size();
		for (int i = 0; i < log.size(); i++)
		{
			if (i == rowLabels.size())
			{
				JLabel label = GroupIronmanPanel.text("", Color.WHITE);
				rowLabels.add(label);
				rows.add(label);
				touched++;
			}
			PanelModel.LogRow row = log.get(i);
			String html = GroupIronmanPanel.html(row.text + " (" + row.ago + ")");
			JLabel label = rowLabels.get(i);
			if (!html.equals(label.getText()))
			{
				label.setText(html);
				label.setToolTipText(row.exact);
				touched++;
			}
		}
		while (rowLabels.size() > log.size())
		{
			rows.remove(rowLabels.remove(rowLabels.size() - 1));
			touched++;
		}
		if (rowLabels.size() != before)
		{
			rows.revalidate();
		}
		return touched;
	}
}
