package com.vinnyvangogh.groupironman;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

/**
 * An item icon grid that is kept, not rebuilt: {@link #update} repaints only
 * the cells whose item or stack changed and adds or drops cells at the end.
 * A full rebuild (remove every label, add hundreds back) blanked the grid for
 * a frame on every sync and held the Swing thread the game's clicks wait on.
 */
final class ItemGridView
{
	/** Sets a cell's icon and tooltip for an item; called only for cells that changed. */
	interface CellPainter
	{
		void paint(JLabel cell, PanelModel.ItemCell item);
	}

	private final JPanel root = new GroupIronmanPanel.Column();
	private final JPanel grid;
	private final JLabel more = GroupIronmanPanel.text("", GroupIronmanPanel.MUTED);
	private final List<JLabel> cells = new ArrayList<>();
	private final List<PanelModel.ItemCell> shown = new ArrayList<>();
	private final CellPainter painter;
	private final int max;

	ItemGridView(CellPainter painter, int columns, int max)
	{
		this.painter = painter;
		this.max = max;
		root.setOpaque(false);
		grid = new JPanel(new GridLayout(0, columns, 1, 1));
		grid.setOpaque(false);
		root.add(grid);
		more.setVisible(false);
		root.add(more);
	}

	JPanel component()
	{
		return root;
	}

	/** Shows {@code items} (the first {@code max}); returns how many cells were painted, added or removed. */
	int update(List<PanelModel.ItemCell> items)
	{
		int want = Math.min(items.size(), max);
		int before = cells.size();
		int touched = 0;
		for (int i = 0; i < want; i++)
		{
			PanelModel.ItemCell item = items.get(i);
			if (i == cells.size())
			{
				JLabel cell = new JLabel();
				cell.setPreferredSize(new Dimension(36, 32));
				cell.setHorizontalAlignment(SwingConstants.CENTER);
				cells.add(cell);
				shown.add(null);
				grid.add(cell);
			}
			if (!item.sameAs(shown.get(i)))
			{
				painter.paint(cells.get(i), item);
				shown.set(i, item);
				touched++;
			}
		}
		while (cells.size() > want)
		{
			int last = cells.size() - 1;
			grid.remove(cells.remove(last));
			shown.remove(last);
			touched++;
		}
		boolean hidden = items.size() > max;
		more.setVisible(hidden);
		if (hidden)
		{
			more.setText(GroupIronmanPanel.html("and " + (items.size() - max) + " more: use the filter to find them."));
		}
		if (cells.size() != before)
		{
			// Only a changed cell count moves anything; a repainted cell keeps its size.
			grid.revalidate();
		}
		return touched;
	}

	/** The cell labels, in order: tests check they are kept across updates. */
	List<JLabel> cells()
	{
		return cells;
	}
}
