package com.vinnyvangogh.groupironman;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;

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
			// The sprite is not ready: the skill's first letter keeps the slot meaningful.
			badge(g, new Color(0x6A, 0x8C, 0xC8), icon.skill.substring(0, 1).toUpperCase(java.util.Locale.ROOT), x, y, size);
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

	private static void glyph(Graphics2D g, BoxText.Glyph glyph, int x, int y, int size)
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
			default:
				badge(g, glyph, x, y, size);
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

	/** A rounded square in the glyph's colour with its letters, for events without a skill or item. */
	private static void badge(Graphics2D g, BoxText.Glyph glyph, int x, int y, int size)
	{
		badge(g, colour(glyph), label(glyph), x, y, size);
	}

	private static void badge(Graphics2D g, Color fill, String label, int x, int y, int size)
	{
		g.setColor(fill);
		g.fillRoundRect(x, y, size, size, 4, 4);
		Font old = g.getFont();
		g.setFont(old.deriveFont(Font.BOLD, Math.max(7f, size * 0.62f)));
		FontMetrics fm = g.getFontMetrics();
		g.setColor(Color.BLACK);
		g.drawString(label, x + (size - fm.stringWidth(label)) / 2, y + (size + fm.getAscent() - fm.getDescent()) / 2);
		g.setFont(old);
	}

	static String label(BoxText.Glyph glyph)
	{
		switch (glyph)
		{
			case QUEST:
				return "Q";
			case DIARY:
				return "D";
			case CLOG:
				return "L";
			case PET:
				return "P";
			case BEST:
				return "B";
			case KILLS:
				return "K";
			case COMBAT:
				return "C";
			default:
				return "";
		}
	}

	private static Color colour(BoxText.Glyph glyph)
	{
		switch (glyph)
		{
			case QUEST:
			case DIARY:
			case COMBAT:
				return new Color(0x4F, 0xC3, 0xA1);
			case PET:
			case CLOG:
				return new Color(0xE0, 0xB0, 0x40);
			default:
				return new Color(0xB0, 0x8A, 0xE0);
		}
	}
}
