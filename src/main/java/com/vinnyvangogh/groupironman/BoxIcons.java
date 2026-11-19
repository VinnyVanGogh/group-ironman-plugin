package com.vinnyvangogh.groupironman;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.IdentityHashMap;
import java.util.Map;

/** Draws a {@link BoxText.Icon} in a square: RuneLite's skill and item pictures, or a small built-in glyph. */
final class BoxIcons
{
	/** Where the pictures come from; the plugin answers from SkillIconManager and ItemManager. */
	interface Source
	{
		/** The skill's small icon, or null when the name is not a skill or the image is not ready. */
		BufferedImage skill(String name);

		/** The item's inventory image, or null while it loads. */
		BufferedImage item(int id);
	}

	private static final Color GREY = new Color(0x9A, 0x9A, 0xA2);
	private static final Color RED = new Color(0xE0, 0x5A, 0x50);

	private BoxIcons()
	{
	}

	/** A source with no pictures, for tests and for a plugin that has none yet. */
	static Source none()
	{
		return new Source()
		{
			@Override
			public BufferedImage skill(String name)
			{
				return null;
			}

			@Override
			public BufferedImage item(int id)
			{
				return null;
			}
		};
	}

	static void draw(Graphics2D g, BoxText.Icon icon, Source source, int x, int y, int size)
	{
		BufferedImage image = icon.skill != null ? source.skill(icon.skill)
			: icon.glyph == null ? source.item(icon.itemId) : null;
		Object old = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		if (image != null)
		{
			g.drawImage(scaled(image, size), x, y, null);
		}
		else if (icon.glyph != null)
		{
			glyph(g, icon.glyph, x, y, size);
		}
		else if (icon.skill != null)
		{
			// The sprite is not ready: a drawn star keeps the slot filled; never a letter.
			star(g, x, y, size);
		}
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
			old == null ? RenderingHints.VALUE_ANTIALIAS_DEFAULT : old);
	}

	/** How long a scaled picture is re-checked: an item's image fills in after it loads, so a blank one must not stick. */
	private static final long SETTLE_MS = 5_000;
	private static final long RECHECK_MS = 250;
	private static final int MAX_CACHED = 256;

	private static final class Scaled
	{
		final BufferedImage source;
		final int size;
		final long born;
		long checked;
		BufferedImage image;

		Scaled(BufferedImage source, int size, long now)
		{
			this.source = source;
			this.size = size;
			this.born = now;
			this.checked = now;
			this.image = render(source, size);
		}
	}

	/** Picture scaled once, not per frame; keyed by the source image itself. Only the render thread draws. */
	private static final Map<BufferedImage, Scaled> SCALED = new IdentityHashMap<>();

	/** {@code image} at size x size: the Group box asks every frame, so the scaling is done once per picture and size. */
	static BufferedImage scaled(BufferedImage image, int size)
	{
		long now = System.currentTimeMillis();
		synchronized (SCALED)
		{
			Scaled hit = SCALED.get(image);
			if (hit == null || hit.size != size)
			{
				if (SCALED.size() >= MAX_CACHED)
				{
					SCALED.clear();
				}
				hit = new Scaled(image, size, now);
				SCALED.put(image, hit);
			}
			else if (now - hit.born < SETTLE_MS && now - hit.checked >= RECHECK_MS)
			{
				hit.checked = now;
				hit.image = render(image, size);
			}
			return hit.image;
		}
	}

	private static BufferedImage render(BufferedImage source, int size)
	{
		BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(source, 0, 0, size, size, null);
		g.dispose();
		return out;
	}

	/** Draws one built-in glyph; every glyph is a drawn picture, never a letter. */
	static void glyph(Graphics2D g, BoxText.Glyph glyph, int x, int y, int size)
	{
		switch (glyph)
		{
			case ONLINE:
				dot(g, OverlayStyle.ONLINE, x, y, size);
				break;
			case OFFLINE:
				dot(g, GREY, x, y, size);
				break;
			case DEATH:
				cross(g, x, y, size);
				break;
			case COMBAT:
				swords(g, x, y, size);
				break;
			case QUEST:
				scroll(g, x, y, size);
				break;
			case DIARY:
				book(g, x, y, size);
				break;
			case CLOG:
				grid(g, x, y, size);
				break;
			case PET:
				paw(g, x, y, size);
				break;
			case KILLS:
				skull(g, x, y, size);
				break;
			default:
				star(g, x, y, size);
				break;
		}
	}

	private static void dot(Graphics2D g, Color colour, int x, int y, int size)
	{
		int d = Math.max(5, size * 2 / 3);
		int o = (size - d) / 2;
		g.setColor(Color.BLACK);
		g.fillOval(x + o + 1, y + o + 1, d, d);
		g.setColor(colour);
		g.fillOval(x + o, y + o, d, d);
	}

	private static void cross(Graphics2D g, int x, int y, int size)
	{
		int m = Math.max(2, size / 5);
		g.setStroke(new BasicStroke(2f));
		g.setColor(RED);
		g.drawLine(x + m, y + m, x + size - m, y + size - m);
		g.drawLine(x + size - m, y + m, x + m, y + size - m);
		g.setStroke(new BasicStroke(1f));
	}

	private static final Color STEEL = new Color(0xD8, 0xDC, 0xE4);
	private static final Color HILT = new Color(0xB0, 0x7A, 0x30);
	private static final Color GOLD = new Color(0xE0, 0xB0, 0x40);
	private static final Color PAPER = new Color(0xE8, 0xDC, 0xB8);
	private static final Color TEAL = new Color(0x4F, 0xC3, 0xA1);
	private static final Color BONE = new Color(0xE6, 0xE2, 0xD6);

	private static void line(Graphics2D g, Color colour, float width, int x1, int y1, int x2, int y2)
	{
		g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(Color.BLACK);
		g.drawLine(x1 + 1, y1 + 1, x2 + 1, y2 + 1);
		g.setColor(colour);
		g.drawLine(x1, y1, x2, y2);
		g.setStroke(new BasicStroke(1f));
	}

	/** Two crossed swords: the combat level. */
	private static void swords(Graphics2D g, int x, int y, int size)
	{
		int m = Math.max(1, size / 8);
		float w = Math.max(2f, size / 6f);
		line(g, STEEL, w, x + m, y + m, x + size - m - 2, y + size - m - 2);
		line(g, STEEL, w, x + size - m, y + m, x + m + 2, y + size - m - 2);
		int h = size * 5 / 8;
		line(g, HILT, w, x + h - 2, y + h + 2, x + h + 2, y + h - 2);
		line(g, HILT, w, x + size - h - 2, y + h - 2, x + size - h + 2, y + h + 2);
	}

	/** A rolled scroll: quests. */
	private static void scroll(Graphics2D g, int x, int y, int size)
	{
		int w = size * 2 / 3;
		int o = (size - w) / 2;
		g.setColor(Color.BLACK);
		g.fillRoundRect(x + o + 1, y + 2, w, size - 3, 3, 3);
		g.setColor(PAPER);
		g.fillRoundRect(x + o, y + 1, w, size - 3, 3, 3);
		g.setColor(HILT);
		g.fillRect(x + o - 1, y + 1, w + 2, 2);
		g.fillRect(x + o - 1, y + size - 4, w + 2, 2);
		g.setColor(HILT.darker());
		for (int row = y + size / 3; row < y + size - 5; row += 2)
		{
			g.drawLine(x + o + 2, row, x + o + w - 3, row);
		}
	}

	/** A closed book: achievement diaries. */
	private static void book(Graphics2D g, int x, int y, int size)
	{
		int w = size * 3 / 4;
		int o = (size - w) / 2;
		g.setColor(Color.BLACK);
		g.fillRoundRect(x + o + 1, y + 2, w, size - 3, 2, 2);
		g.setColor(TEAL);
		g.fillRoundRect(x + o, y + 1, w, size - 3, 2, 2);
		g.setColor(PAPER);
		g.fillRect(x + o + 3, y + 3, w - 5, size - 7);
		g.setColor(TEAL.darker());
		g.drawLine(x + o + 2, y + 1, x + o + 2, y + size - 3);
	}

	/** A grid of slots: the collection log. */
	private static void grid(Graphics2D g, int x, int y, int size)
	{
		int cell = Math.max(2, (size - 2) / 3);
		for (int r = 0; r < 3; r++)
		{
			for (int c = 0; c < 3; c++)
			{
				g.setColor(Color.BLACK);
				g.fillRect(x + c * (cell + 1) + 1, y + r * (cell + 1) + 1, cell, cell);
				g.setColor((r + c) % 2 == 0 ? GOLD : GOLD.darker());
				g.fillRect(x + c * (cell + 1), y + r * (cell + 1), cell, cell);
			}
		}
	}

	/** A paw print: pets. */
	private static void paw(Graphics2D g, int x, int y, int size)
	{
		int toe = Math.max(2, size / 4);
		int[][] toes = {{size / 8, size / 3}, {size / 3 - 1, size / 8}, {size / 2 + 1, size / 8}, {size - size / 8 - toe, size / 3}};
		for (int[] t : toes)
		{
			g.setColor(Color.BLACK);
			g.fillOval(x + t[0] + 1, y + t[1] + 1, toe, toe + 1);
			g.setColor(GOLD);
			g.fillOval(x + t[0], y + t[1], toe, toe + 1);
		}
		int pw = size * 5 / 8;
		int ph = size * 7 / 20 + 1;
		g.setColor(Color.BLACK);
		g.fillOval(x + (size - pw) / 2 + 1, y + size - ph - 1, pw, ph);
		g.setColor(GOLD);
		g.fillOval(x + (size - pw) / 2, y + size - ph - 2, pw, ph);
	}

	/** A skull: kill counts. */
	private static void skull(Graphics2D g, int x, int y, int size)
	{
		int w = size * 3 / 4;
		int o = (size - w) / 2;
		g.setColor(Color.BLACK);
		g.fillOval(x + o + 1, y + 1, w, w);
		g.fillRect(x + o + w / 4 + 1, y + w - 2, w / 2, size - w + 1);
		g.setColor(BONE);
		g.fillOval(x + o, y, w, w);
		g.fillRect(x + o + w / 4, y + w - 3, w / 2, size - w + 1);
		g.setColor(Color.BLACK);
		int eye = Math.max(2, w / 4);
		g.fillOval(x + o + w / 5, y + w / 3, eye, eye);
		g.fillOval(x + o + w - w / 5 - eye, y + w / 3, eye, eye);
	}

	/** A five-point star: personal bests, and a skill whose sprite has not loaded. */
	private static void star(Graphics2D g, int x, int y, int size)
	{
		double cx = x + size / 2.0;
		double cy = y + size / 2.0 + 0.5;
		double outer = size / 2.0;
		double inner = outer * 0.42;
		Polygon p = new Polygon();
		for (int i = 0; i < 10; i++)
		{
			double r = i % 2 == 0 ? outer : inner;
			double a = -Math.PI / 2 + i * Math.PI / 5;
			p.addPoint((int) Math.round(cx + r * Math.cos(a)), (int) Math.round(cy + r * Math.sin(a)));
		}
		g.setColor(Color.BLACK);
		g.translate(1, 1);
		g.fillPolygon(p);
		g.translate(-1, -1);
		g.setColor(GOLD);
		g.fillPolygon(p);
	}
}
