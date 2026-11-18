package com.vinnyvangogh.groupironman;

import java.awt.Point;

/**
 * Where the "Above player" banner stack goes: its bottom-centre sits at the player's head, and it
 * falls back to top centre when the head is not on screen. Pure, so it is tested without a client.
 */
final class BannerAnchor
{
	/** Local units above the head, past overhead text and hitsplats. */
	static final int LIFT = 80;
	private static final int TOP_MARGIN = 10;

	private BannerAnchor()
	{
	}

	/** The top-left corner for a panel of the given size; {@code head} is null when the player is off screen. */
	static Point place(Point head, int canvasW, int canvasH, int w, int h)
	{
		if (head == null || head.x < 0 || head.y < 0 || head.x > canvasW || head.y > canvasH)
		{
			return new Point((canvasW - w) / 2, TOP_MARGIN);
		}
		return new Point(head.x - w / 2, head.y - h);
	}
}
