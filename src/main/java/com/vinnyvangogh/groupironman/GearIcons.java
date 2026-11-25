package com.vinnyvangogh.groupironman;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import javax.swing.Icon;

/**
 * The Gear tab's small painted icons: a state dot and the editor's glyphs. Painted rather than loaded, so there
 * is no resource to ship and they follow the panel's colours.
 */
final class GearIcons
{
	static final int SIZE = 12;
	private static final Color INK = new Color(0xDC, 0xDC, 0xDC);

	/** What a glyph draws. */
	enum Glyph
	{
		PLUS, MINUS, CROSS, PENCIL, RESET, CART, NEW
	}

	private GearIcons()
	{
	}

	/** A filled circle: the line's state when there is no item picture. */
	static Icon dot(Color color)
	{
		return new Painted(g ->
		{
			g.setColor(color);
			g.fillOval(2, 2, SIZE - 4, SIZE - 4);
		});
	}

	static Icon glyph(Glyph glyph)
	{
		return new Painted(g ->
		{
			g.setColor(INK);
			g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			int m = SIZE / 2;
			switch (glyph)
			{
				case PLUS:
					g.drawLine(2, m, SIZE - 2, m);
					g.drawLine(m, 2, m, SIZE - 2);
					break;
				case MINUS:
					g.drawLine(2, m, SIZE - 2, m);
					break;
				case CROSS:
					g.drawLine(3, 3, SIZE - 3, SIZE - 3);
					g.drawLine(SIZE - 3, 3, 3, SIZE - 3);
					break;
				case PENCIL:
					g.drawLine(3, SIZE - 3, SIZE - 3, 3);
					g.drawLine(2, SIZE - 2, 4, SIZE - 2);
					g.drawLine(SIZE - 5, 2, SIZE - 2, 5);
					break;
				case RESET:
					g.draw(new Arc2D.Double(2, 2, SIZE - 4, SIZE - 4, 60, 280, Arc2D.OPEN));
					g.drawLine(m + 2, 1, m + 3, 4);
					g.drawLine(m + 3, 4, m, 5);
					break;
				case CART:
					g.drawLine(1, 2, 3, 2);
					g.drawLine(3, 2, 4, 8);
					g.drawLine(4, 8, SIZE - 2, 8);
					g.drawLine(SIZE - 2, 8, SIZE - 1, 4);
					g.drawLine(SIZE - 1, 4, 3, 4);
					g.fillOval(4, 9, 2, 2);
					g.fillOval(SIZE - 4, 9, 2, 2);
					break;
				case NEW:
					g.drawRect(2, 1, SIZE - 5, SIZE - 3);
					g.drawLine(5, m, SIZE - 6, m);
					g.drawLine(m - 1, m - 2, m - 1, m + 2);
					break;
				default:
					break;
			}
		});
	}

	/** An icon from a paint routine, antialiased, {@link #SIZE} square. */
	private static final class Painted implements Icon
	{
		interface Paint
		{
			void paint(Graphics2D g);
		}

		private final Paint paint;

		Painted(Paint paint)
		{
			this.paint = paint;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.translate(x, y);
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				paint.paint(g2);
			}
			finally
			{
				g2.dispose();
			}
		}

		@Override
		public int getIconWidth()
		{
			return SIZE;
		}

		@Override
		public int getIconHeight()
		{
			return SIZE;
		}
	}
}
