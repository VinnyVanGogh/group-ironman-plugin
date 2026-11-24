package com.vinnyvangogh.groupironman;

import java.awt.Point;
import java.awt.geom.AffineTransform;

/**
 * Where the "Above player" banner stack goes: its bottom-centre sits at the player's head, kept inside the canvas.
 * When the head is not on screen it stays where it was last drawn, or top centre before it was ever placed. Pure,
 * so it is tested without a client.
 *
 * <p>The head point is in canvas pixels, but the banner is drawn in whatever space RuneLite's overlay renderer
 * hands over. A dynamic overlay gets the canvas itself; one the player once dragged or snapped to a corner (its
 * saved preferred position outranks the overlay's own) gets a graphics already moved to that corner, which is how
 * the banner ended up off to the side. {@link #toGraphics} undoes that move, so the banner lands on the head either
 * way.
 */
final class BannerAnchor
{
	/** Local units above the head, past overhead text and hitsplats. */
	static final int LIFT = 80;
	private static final int TOP_MARGIN = 10;

	private BannerAnchor()
	{
	}

	/** As {@link #place(Point, Point, int, int, int, int)} with no earlier placement. */
	static Point place(Point head, int canvasW, int canvasH, int w, int h)
	{
		return place(head, null, canvasW, canvasH, w, h);
	}

	/**
	 * The top-left corner, in canvas pixels, for a panel of the given size. {@code head} is null when the player is
	 * off screen; {@code last} is the corner drawn last time, or null.
	 */
	static Point place(Point head, Point last, int canvasW, int canvasH, int w, int h)
	{
		if (head == null || head.x < 0 || head.y < 0 || head.x > canvasW || head.y > canvasH)
		{
			return last != null ? clamp(last, canvasW, canvasH, w, h) : new Point((canvasW - w) / 2, TOP_MARGIN);
		}
		return clamp(new Point(head.x - w / 2, head.y - h), canvasW, canvasH, w, h);
	}

	/** Kept wholly on the canvas where it fits; a panel wider or taller than the canvas starts at its edge. */
	private static Point clamp(Point p, int canvasW, int canvasH, int w, int h)
	{
		int x = Math.max(0, Math.min(p.x, canvasW - w));
		int y = Math.max(0, Math.min(p.y, canvasH - h));
		return new Point(x, y);
	}

	/**
	 * {@code canvas}, a point in canvas pixels, in the space of a graphics whose transform is {@code t}: the move
	 * the overlay renderer made is taken off. Scale is allowed for; a rotated or sheared graphics never occurs.
	 */
	static Point toGraphics(Point canvas, AffineTransform t)
	{
		double sx = t.getScaleX() == 0 ? 1 : t.getScaleX();
		double sy = t.getScaleY() == 0 ? 1 : t.getScaleY();
		return new Point((int) Math.round(canvas.x - t.getTranslateX() / sx),
			(int) Math.round(canvas.y - t.getTranslateY() / sy));
	}
}
