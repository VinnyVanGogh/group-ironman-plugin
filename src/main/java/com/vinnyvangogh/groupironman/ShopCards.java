package com.vinnyvangogh.groupironman;

import java.awt.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.*;

/**
 * The pieces every Shopping card is drawn from, lists and requests alike: a
 * header that folds the card with a one-line preview under it (Board
 * 2026-11-14), a thin progress bar, and text that wraps at the card's real
 * width.
 *
 * <p>Widths are in screen pixels. The tab's {@code <body style='width:175px'>}
 * labels are not: Swing's HTML reads CSS {@code px} as 1.3 screen pixels, so
 * that body is about 227 pixels wide and ran past a card's right edge ("For
 * GroupArk. Added by GroupShip, just r"). {@link Wrap} measures with the label's
 * own HTML view at a width given in pixels instead.
 */
final class ShopCards
{
	static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR.darker();
	static final Color DONE = ColorScheme.PROGRESS_COMPLETE_COLOR;
	/** The tab's content: the side panel (225) less the GIM panel's 8 + 8 border. */
	static final int TAB_TEXT = PluginPanel.PANEL_WIDTH - 16;
	/** Inside a card: less the card's 6 + 6 padding. */
	static final int CARD_TEXT = TAB_TEXT - 12;
	/** Beside a row's item icon (36) and gap (4). */
	static final int ROW_TEXT = CARD_TEXT - 40;
	/** Still-needed items whose icons the folded card shows. */
	static final int PREVIEW_ICONS = 3;

	private ShopCards()
	{
	}

	/** Plain text (escaped) that wraps at {@code width} screen pixels and asks for exactly the height it needs. */
	static final class Wrap extends JLabel
	{
		private final int width;

		Wrap(String text, Color color, int width)
		{
			super("<html>" + GroupIronmanPanel.escape(text) + "</html>");
			this.width = width;
			setFont(FontManager.getRunescapeSmallFont());
			setForeground(color);
			setVerticalAlignment(SwingConstants.TOP);
		}

		@Override
		public Dimension getPreferredSize()
		{
			View view = (View) getClientProperty(BasicHTML.propertyKey);
			Insets in = getInsets();
			if (view == null)
			{
				return super.getPreferredSize();
			}
			int icon = getIcon() == null ? 0 : getIcon().getIconWidth() + getIconTextGap();
			view.setSize(width - in.left - in.right - icon, 0);
			int height = (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS));
			if (getIcon() != null)
			{
				height = Math.max(height, getIcon().getIconHeight());
			}
			return new Dimension(width, height + in.top + in.bottom);
		}

		@Override
		public Dimension getMaximumSize()
		{
			return getPreferredSize();
		}

		@Override
		public Dimension getMinimumSize()
		{
			return getPreferredSize();
		}
	}

	/** Text straight on the tab, outside any card. */
	static Wrap tabText(String text, Color color)
	{
		return new Wrap(text, color, TAB_TEXT);
	}

	/** A thin bar filled by {@code done} of {@code total}: green when full. */
	static final class Bar extends JComponent
	{
		private final long done;
		private final long total;

		Bar(long done, long total, int width)
		{
			this.done = done;
			this.total = total;
			Dimension d = new Dimension(width, 4);
			setPreferredSize(d);
			setMaximumSize(d);
			setMinimumSize(d);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			g.setColor(ColorScheme.DARK_GRAY_COLOR);
			g.fillRect(0, 0, getWidth(), getHeight());
			int fill = total <= 0 ? 0 : (int) Math.round(getWidth() * Math.min(1.0, done / (double) total));
			g.setColor(done >= total && total > 0 ? DONE : ColorScheme.BRAND_ORANGE);
			g.fillRect(0, 0, fill, getHeight());
		}
	}

	/** "0 of 39 done" / "Done: 39 of 39". */
	static String progress(int done, int total)
	{
		return done >= total && total > 0 ? "Done: " + done + " of " + total : done + " of " + total + " done";
	}

	/**
	 * The card's header: its title with the fold arrow (a click flips it), "3 of 39 done" and a bar, then while
	 * folded the first few still-needed items' icons and how many more.
	 */
	static void header(JPanel box, String title, boolean open, int done, int total, List<Integer> needed,
		ItemManager items, Runnable toggle)
	{
		Wrap name = new Wrap(title, done >= total && total > 0 ? DONE : Color.WHITE, CARD_TEXT);
		name.setIcon(new GroupIronmanPanel.Arrow(open));
		name.setIconTextGap(4);
		name.setFont(FontManager.getRunescapeBoldFont());
		Ui.clickable(name, toggle);
		box.add(name);
		box.add(new Wrap(progress(done, total), MUTED, CARD_TEXT));
		box.add(new Bar(done, total, CARD_TEXT));
		if (!open && !needed.isEmpty())
		{
			box.add(preview(needed, items, toggle));
		}
	}

	private static JPanel preview(List<Integer> needed, ItemManager items, Runnable toggle)
	{
		JPanel icons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		icons.setOpaque(false);
		int shown = Math.min(PREVIEW_ICONS, needed.size());
		for (int id : needed.subList(0, shown))
		{
			JLabel icon = new JLabel();
			if (items != null)
			{
				items.getImage(id).addTo(icon);
			}
			icons.add(icon);
		}
		int more = needed.size() - shown;
		if (more > 0)
		{
			icons.add(Ui.label("+" + more + " more", FontManager.getRunescapeSmallFont(), MUTED));
		}
		icons.setMaximumSize(new Dimension(CARD_TEXT, 34));
		icons.setPreferredSize(new Dimension(CARD_TEXT, 34));
		Ui.clickable(icons, toggle);
		return icons;
	}

	/** "▸ 3 done": opens the card's finished items. */
	static JLabel doneToggle(int count, boolean open, Runnable toggle)
	{
		JLabel l = new JLabel(Amounts.GAME.of(count) + " done", new GroupIronmanPanel.Arrow(open), SwingConstants.LEFT);
		l.setIconTextGap(4);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(DONE);
		l.setBorder(new EmptyBorder(4, 0, 2, 0));
		Ui.clickable(l, toggle);
		return l;
	}
}
