package com.vinnyvangogh.groupironman;

/**
 * How big the on-screen Group box is: its width, its font and how much each event line says. Public for the
 * config proxy (see {@link DiscordStyle}). {@link #MEDIUM} is the width the box has always had.
 */
public enum BoxSize
{
	COMPACT("Compact", 160, 0.85f, 0),
	SMALL("Small", 190, 0.92f, 1),
	MEDIUM("Medium", OverlayStyle.WIDTH, 1f, 1),
	WIDE("Wide", 270, 1f, 2),
	LARGE("Large", 320, 1.15f, 2);

	private final String label;
	final int width;
	final float fontScale;
	/** 0 is the shortest text each event has, 1 the usual short form, 2 adds the extra detail. */
	final int tier;

	BoxSize(String label, int width, float fontScale, int tier)
	{
		this.label = label;
		this.width = width;
		this.fontScale = fontScale;
		this.tier = tier;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
