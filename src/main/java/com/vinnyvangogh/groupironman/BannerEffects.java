package com.vinnyvangogh.groupironman;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.Random;

/**
 * Fireworks and flourishes behind a banner. Level-ups get a small firework; milestones (max level, XP and kill-count
 * milestones, diaries, combat tasks, pets, raid uniques, personal bests, wave milestones) get a bigger show with more
 * bursts, a flash and a gold ring.
 *
 * <p>Pure and clock-driven: where a spark is depends only on the event's seed and the time since the banner appeared,
 * so nothing is kept between frames and the maths is tested without a client. Drawing allocates nothing.
 */
final class BannerEffects
{
	enum Kind
	{
		NONE(0, 0, 0), FIREWORKS(2, 14, 2200), GRAND(5, 26, 4000);

		final int bursts;
		final int sparks;
		/** How long the show lasts; a banner outlives it or is cut short with it. */
		final long durationMs;

		Kind(int bursts, int sparks, long durationMs)
		{
			this.bursts = bursts;
			this.sparks = sparks;
			this.durationMs = durationMs;
		}
	}

	/** Time between one burst going off and the next. */
	static final long BURST_GAP_MS = 380;
	/** How long one burst's sparks live. */
	static final long SPARK_LIFE_MS = 1400;
	private static final double GRAVITY = 70;
	private static final Color[] PALETTE = {
		new Color(0xFF, 0xD7, 0x4A), new Color(0xFF, 0x6B, 0x5C), new Color(0x5C, 0xC8, 0x5C),
		new Color(0x5C, 0xB8, 0xFF), new Color(0xE0, 0x7B, 0xFF), new Color(0xFF, 0xFF, 0xFF)
	};

	private BannerEffects()
	{
	}

	/** The show an event earns; {@link Kind#NONE} for the rest. */
	static Kind kindOf(GroupEvent.Type type)
	{
		switch (type)
		{
			case LEVEL_UP:
			case COMBAT_LEVEL:
			case TOTAL_LEVEL:
				return Kind.FIREWORKS;
			case MAXED:
			case XP_MILESTONE:
			case KC_MILESTONE:
			case ACTIVITY_WAVE:
			case ACTIVITY_COMPLETE:
			case DIARY:
			case COMBAT_TASK:
			case QUEST_POINTS:
			case PERSONAL_BEST:
			case PET:
			case RAID_UNIQUE:
				return Kind.GRAND;
			default:
				return Kind.NONE;
		}
	}

	/** When burst {@code burst} goes off, in ms after the banner appeared. */
	static long burstStart(int burst)
	{
		return burst * BURST_GAP_MS;
	}

	/**
	 * One spark. Writes {@code out[0]} as the x and {@code out[1]} as the y offset from the burst's centre in pixels,
	 * and {@code out[2]} as its opacity 0..1. False while the burst has not gone off or the spark has died.
	 */
	static boolean spark(long seed, int burst, int index, long elapsedMs, double reach, double[] out)
	{
		long age = elapsedMs - burstStart(burst);
		if (age < 0 || age >= SPARK_LIFE_MS)
		{
			return false;
		}
		Random r = new Random(seed * 31 + burst * 1009L + index * 7919L);
		double angle = r.nextDouble() * Math.PI * 2;
		double speed = reach * (0.45 + r.nextDouble() * 0.55);
		double t = age / 1000.0;
		double life = age / (double) SPARK_LIFE_MS;
		out[0] = Math.cos(angle) * speed * t;
		out[1] = Math.sin(angle) * speed * t + GRAVITY * t * t;
		out[2] = life < 0.6 ? 1 : (1 - life) / 0.4;
		return true;
	}

	/** Where burst {@code burst} is centred, as fractions (x, y) of the banner's width and height; some sit above it. */
	static void burstCentre(long seed, int burst, double[] out)
	{
		Random r = new Random(seed * 17 + burst * 104729L);
		out[0] = 0.1 + r.nextDouble() * 0.8;
		out[1] = -0.25 + r.nextDouble() * 0.7;
	}

	/** Opacity of the flash and ring a grand show opens with: 1 at the start, 0 after about 600 ms. */
	static double flash(long elapsedMs)
	{
		return elapsedMs < 0 || elapsedMs >= 600 ? 0 : 1 - elapsedMs / 600.0;
	}

	/** Draws the show over {@code panel} (the banner's bounds). Nothing once it is over. */
	static void draw(Graphics2D g, Kind kind, long seed, long elapsedMs, Rectangle panel)
	{
		if (kind == Kind.NONE || elapsedMs < 0 || elapsedMs >= kind.durationMs || panel.width <= 0)
		{
			return;
		}
		Object old = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		java.awt.Composite oldComposite = g.getComposite();
		if (kind == Kind.GRAND)
		{
			drawFlash(g, elapsedMs, panel);
		}
		double reach = kind == Kind.GRAND ? 120 : 80;
		double[] p = new double[3];
		double[] c = new double[2];
		for (int b = 0; b < kind.bursts; b++)
		{
			burstCentre(seed, b, c);
			double cx = panel.x + c[0] * panel.width;
			double cy = panel.y + c[1] * panel.height;
			Color base = PALETTE[(int) ((seed + b * 3L) & 0x7fffffff) % PALETTE.length];
			for (int i = 0; i < kind.sparks; i++)
			{
				if (!spark(seed, b, i, elapsedMs, reach, p))
				{
					continue;
				}
				g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), (int) Math.round(p[2] * 255)));
				int size = kind == Kind.GRAND ? 4 : 3;
				g.fillOval((int) Math.round(cx + p[0]) - size / 2, (int) Math.round(cy + p[1]) - size / 2, size, size);
			}
		}
		g.setComposite(oldComposite);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
			old == null ? RenderingHints.VALUE_ANTIALIAS_DEFAULT : old);
	}

	/** A gold glow round the banner and a ring that grows out from its centre. */
	private static void drawFlash(Graphics2D g, long elapsedMs, Rectangle panel)
	{
		double f = flash(elapsedMs);
		if (f <= 0)
		{
			return;
		}
		int a = (int) Math.round(f * 200);
		g.setColor(new Color(0xFF, 0xD7, 0x4A, a));
		g.drawRect(panel.x - 1, panel.y - 1, panel.width + 1, panel.height + 1);
		g.drawRect(panel.x - 2, panel.y - 2, panel.width + 3, panel.height + 3);
		double grow = 1 - f;
		int rw = (int) Math.round(panel.width * (0.4 + grow * 0.9));
		int rh = (int) Math.round(panel.height * (0.4 + grow * 1.4));
		g.setColor(new Color(0xFF, 0xF0, 0xA0, (int) Math.round(f * 160)));
		g.drawOval(panel.x + (panel.width - rw) / 2, panel.y + (panel.height - rh) / 2, rw, rh);
	}
}
