package com.vinnyvangogh.groupironman;

import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Where event banners appear. Public for the config proxy (see
 * {@link DiscordStyle}); a short list of RuneLite's own overlay positions.
 */
public enum ToastPosition
{
	TOP_CENTER("Top centre", OverlayPosition.TOP_CENTER),
	TOP_LEFT("Top left", OverlayPosition.TOP_LEFT),
	TOP_RIGHT("Top right", OverlayPosition.TOP_RIGHT),
	ABOVE_CHATBOX("Above chatbox", OverlayPosition.ABOVE_CHATBOX_RIGHT);

	private final String label;
	final OverlayPosition position;

	ToastPosition(String label, OverlayPosition position)
	{
		this.label = label;
		this.position = position;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
