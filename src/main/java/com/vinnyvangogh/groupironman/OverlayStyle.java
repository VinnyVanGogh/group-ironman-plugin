package com.vinnyvangogh.groupironman;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.PanelComponent;

/**
 * One look for every Group Ironman overlay: a dark panel, a header with an
 * accent bar and a count badge, thin rules between sections, and rows with a
 * state dot, a right-aligned value and wrapped detail. Colour carries state
 * (green online, amber to-do, red trouble, grey for the rest) and nothing else.
 *
 * <p>The blocks only measure and draw; what they say is the overlay's business.
 * Everything here runs every frame, so no block allocates beyond its text lines.
 */
final class OverlayStyle
{
	static final Color BACKGROUND = new Color(22, 22, 26, 220);
	static final Color ACCENT = new Color(0xE0, 0xB0, 0x40);
	static final Color TEXT = new Color(0xEE, 0xEE, 0xEE);
	static final Color MUTED = new Color(0x9A, 0x9A, 0xA2);
	static final Color ONLINE = new Color(0x5C, 0xC8, 0x5C);
	static final Color DANGER = new Color(0xE0, 0x5A, 0x50);
	private static final Color RULE = new Color(255, 255, 255, 36);

	/** The width of the box and the path card; banners are a little wider. */
	static final int WIDTH = 220;
	static final int BANNER_WIDTH = 250;
	private static final int ACCENT_BAR = 3;
	private static final int DOT = 7;
	private static final int DOT_SLOT = 12;
	private static final int GAP = 6;
	private static final int PAD_X = 8;
	private static final int PAD_Y = 6;

	private OverlayStyle()
	{
	}

	/** Dark background, even padding and a steady gap between blocks. */
	static void frame(PanelComponent panel, int width)
	{
		panel.setBackgroundColor(BACKGROUND);
		panel.setBorder(new Rectangle(PAD_X, PAD_Y, PAD_X, PAD_Y));
		panel.setGap(new Point(0, 3));
		panel.setPreferredSize(new Dimension(width, 0));
	}

	/**
	 * How tall a framed panel drew these children, from their bounds once drawn. PanelComponent sizes itself from
	 * the previous frame's children, so the height it returns lags a frame (only the padding on the first).
	 */
	static int drawnHeight(List<LayoutableRenderableEntity> children)
	{
		int top = Integer.MAX_VALUE;
		int bottom = Integer.MIN_VALUE;
		for (LayoutableRenderableEntity c : children)
		{
			Rectangle b = c.getBounds();
			top = Math.min(top, b.y);
			bottom = Math.max(bottom, b.y + b.height);
		}
		return children.isEmpty() ? 0 : bottom - top + 2 * PAD_Y;
	}

	/** Text cut to {@code max} pixels with ".." on the end. */
	static String fit(String text, FontMetrics fm, int max)
	{
		if (max <= 0)
		{
			return "";
		}
		if (fm.stringWidth(text) <= max)
		{
			return text;
		}
		int end = text.length();
		while (end > 0 && fm.stringWidth(text.substring(0, end) + "..") > max)
		{
			end--;
		}
		return text.substring(0, end).trim() + "..";
	}

	/** Greedy word wrap to {@code max} pixels; the last of {@code maxLines} lines is cut with "..". */
	static List<String> wrap(String text, FontMetrics fm, int max, int maxLines)
	{
		List<String> lines = new ArrayList<>();
		String rest = text.trim();
		while (!rest.isEmpty())
		{
			if (lines.size() == maxLines - 1 || fm.stringWidth(rest) <= max)
			{
				lines.add(fit(rest, fm, max));
				return lines;
			}
			int cut = rest.length();
			while (cut > 1 && fm.stringWidth(rest.substring(0, cut)) > max)
			{
				cut--;
			}
			int space = rest.lastIndexOf(' ', cut);
			if (space > 0)
			{
				cut = space;
			}
			lines.add(rest.substring(0, cut).trim());
			rest = rest.substring(cut).trim();
		}
		if (lines.isEmpty())
		{
			lines.add("");
		}
		return lines;
	}

	/** A block that knows its place and width; subclasses say how tall they draw. */
	abstract static class Block implements LayoutableRenderableEntity
	{
		private final Rectangle bounds = new Rectangle();
		private Point location = new Point();
		private Dimension size = new Dimension(WIDTH, 0);

		@Override
		public Rectangle getBounds()
		{
			return bounds;
		}

		@Override
		public void setPreferredLocation(Point location)
		{
			this.location = location;
		}

		@Override
		public void setPreferredSize(Dimension size)
		{
			this.size = size;
		}

		@Override
		public Dimension render(Graphics2D g)
		{
			int w = size.width > 0 ? size.width : WIDTH;
			int h = draw(g, location.x, location.y, w);
			bounds.setBounds(location.x, location.y, w, h);
			return bounds.getSize();
		}

		/** Draws at ({@code x}, {@code y}) within {@code w} pixels; returns the height used. */
		abstract int draw(Graphics2D g, int x, int y, int w);
	}

	/** Accent bar, title in capitals, and an optional badge on the right ("3 online"). */
	static final class Header extends Block
	{
		private final String title;
		private final String badge;
		private final Color accent;

		Header(String title, String badge, Color accent)
		{
			this.title = title.toUpperCase(java.util.Locale.ROOT);
			this.badge = badge == null ? "" : badge;
			this.accent = accent;
		}

		@Override
		int draw(Graphics2D g, int x, int y, int w)
		{
			Font plain = g.getFont();
			Font bold = plain.deriveFont(Font.BOLD);
			g.setFont(bold);
			FontMetrics fm = g.getFontMetrics();
			int h = fm.getHeight() + 2;
			g.setColor(accent);
			g.fillRect(x, y, ACCENT_BAR, h);
			int badgeW = badge.isEmpty() ? 0 : g.getFontMetrics(plain).stringWidth(badge);
			int room = w - ACCENT_BAR - 5 - (badgeW == 0 ? 0 : badgeW + GAP);
			OverlayUtil.renderTextLocation(g, new net.runelite.api.Point(x + ACCENT_BAR + 5, y + fm.getAscent() + 1),
				fit(title, fm, room), accent);
			g.setFont(plain);
			if (badgeW > 0)
			{
				OverlayUtil.renderTextLocation(g, new net.runelite.api.Point(x + w - badgeW, y + fm.getAscent() + 1), badge, MUTED);
			}
			return h;
		}
	}

	/** A one-pixel rule, to part sections. */
	static final class Rule extends Block
	{
		@Override
		int draw(Graphics2D g, int x, int y, int w)
		{
			g.setColor(RULE);
			g.fillRect(x, y + 1, w, 1);
			return 3;
		}
	}

	/**
	 * One Group box event: [icon] member detail ... time. The time is kept and so is the member; only the detail
	 * is cut, with "..", when the line is too long. The font is the box size's scale of the overlay font, and the
	 * time is a little smaller and dimmer.
	 */
	static final class EventRow extends Block
	{
		private static final int NAME_GAP = 4;
		private static final int ICON_GAP = 3;
		private static final float TIME_SCALE = 0.85f;
		private static final Color DIM = new Color(0x80, 0x80, 0x88);

		private final BoxText.Line line;
		private final Color colour;
		private final float scale;
		private final BoxIcons.Source icons;

		EventRow(BoxText.Line line, Color colour, float scale, BoxIcons.Source icons)
		{
			this.line = line;
			this.colour = colour;
			this.scale = scale;
			this.icons = icons;
		}

		/** Pixels left for the detail once the icon, the member and the time have theirs; zero or less means none. */
		static int detailRoom(int width, int iconSlot, int nameWidth, int timeWidth)
		{
			return width - iconSlot - nameWidth - NAME_GAP - (timeWidth == 0 ? 0 : timeWidth + GAP);
		}

		@Override
		int draw(Graphics2D g, int x, int y, int w)
		{
			Font base = g.getFont();
			Font font = base.deriveFont(base.getSize2D() * scale);
			g.setFont(font);
			FontMetrics fm = g.getFontMetrics();
			Font small = font.deriveFont(font.getSize2D() * TIME_SCALE);
			int timeW = line.ago.isEmpty() ? 0 : g.getFontMetrics(small).stringWidth(line.ago);
			int size = Math.max(8, fm.getHeight() - 2);
			int slot = line.icon == null ? 0 : size + ICON_GAP;
			int nameW = fm.stringWidth(line.member);
			int room = detailRoom(w, slot, nameW, timeW);
			int baseline = y + fm.getAscent();
			if (line.icon != null)
			{
				BoxIcons.draw(g, line.icon, icons, x, y + (fm.getHeight() - size) / 2, size);
			}
			OverlayUtil.renderTextLocation(g, new net.runelite.api.Point(x + slot, baseline), line.member, colour);
			if (room > fm.stringWidth("..") && !line.detail.isEmpty())
			{
				OverlayUtil.renderTextLocation(g, new net.runelite.api.Point(x + slot + nameW + NAME_GAP, baseline),
					fit(line.detail, fm, room), TEXT);
			}
			if (timeW > 0)
			{
				g.setFont(small);
				OverlayUtil.renderTextLocation(g, new net.runelite.api.Point(x + w - timeW, baseline), line.ago, DIM);
			}
			int h = fm.getHeight();
			g.setFont(base);
			return h;
		}
	}

	/** One row: optional state dot, text (wrapped), and a value on the right. */
	static final class Row extends Block
	{
		private final String text;
		private final Color color;
		private String right = "";
		private Color rightColor = MUTED;
		private Color dot;
		private int indent;
		private int maxLines = 1;
		private float scale = 1f;

		Row(String text, Color color)
		{
			this.text = text;
			this.color = color;
		}

		Row right(String value, Color colour)
		{
			this.right = value == null ? "" : value;
			this.rightColor = colour;
			return this;
		}

		Row dot(Color colour)
		{
			this.dot = colour;
			return this;
		}

		Row indent(int pixels)
		{
			this.indent = pixels;
			return this;
		}

		Row lines(int most)
		{
			this.maxLines = most;
			return this;
		}

		/** Draws in this multiple of the overlay font (the Group box size's font). */
		Row scale(float factor)
		{
			this.scale = factor;
			return this;
		}

		@Override
		int draw(Graphics2D g, int x, int y, int w)
		{
			Font base = g.getFont();
			if (scale != 1f)
			{
				g.setFont(base.deriveFont(base.getSize2D() * scale));
			}
			int h = drawRow(g, x, y, w);
			g.setFont(base);
			return h;
		}

		private int drawRow(Graphics2D g, int x, int y, int w)
		{
			FontMetrics fm = g.getFontMetrics();
			int left = x + indent + (dot == null ? 0 : DOT_SLOT);
			int rightW = right.isEmpty() ? 0 : fm.stringWidth(right);
			int room = w - (left - x) - (rightW == 0 ? 0 : rightW + GAP);
			List<String> lines = wrap(text, fm, room, maxLines);
			int lh = fm.getHeight();
			if (dot != null)
			{
				Object old = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setColor(Color.BLACK);
				g.fillOval(x + indent + 1, y + (lh - DOT) / 2 + 1, DOT, DOT);
				g.setColor(dot);
				g.fillOval(x + indent, y + (lh - DOT) / 2, DOT, DOT);
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					old == null ? RenderingHints.VALUE_ANTIALIAS_DEFAULT : old);
			}
			for (int i = 0; i < lines.size(); i++)
			{
				OverlayUtil.renderTextLocation(g, new net.runelite.api.Point(left, y + i * lh + fm.getAscent()), lines.get(i), color);
			}
			if (rightW > 0)
			{
				OverlayUtil.renderTextLocation(g, new net.runelite.api.Point(x + w - rightW, y + fm.getAscent()), right, rightColor);
			}
			return lines.size() * lh;
		}
	}
}
