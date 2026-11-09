package com.vinnyvangogh.groupironman;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Event banners: one title and one line per event, newest on top, each for
 * the configured time. Draws only what {@link ToastQueue} says is visible;
 * the text is built when the event arrives, not every frame.
 */
final class EventToastOverlay extends OverlayPanel
{
	private final ToastQueue toasts;
	private final GroupIronmanConfig config;
	private final ItemLookup lookup;

	EventToastOverlay(ToastQueue toasts, GroupIronmanConfig config, ItemLookup lookup)
	{
		this.toasts = toasts;
		this.config = config;
		this.lookup = lookup;
		setPosition(config.overlayPosition().position);
	}

	void reposition()
	{
		setPosition(config.overlayPosition().position);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		List<ToastQueue.Toast> visible = toasts.visible(System.currentTimeMillis(),
			TimeUnit.SECONDS.toMillis(config.overlayDurationSeconds()), config.overlayMaxStacked(), e -> true);
		if (visible.isEmpty())
		{
			return null;
		}
		for (ToastQueue.Toast t : visible)
		{
			panelComponent.getChildren().add(TitleComponent.builder()
				.text(t.event.member + ": " + EventText.title(t.event.type))
				.color(new Color(t.event.type.category.colour))
				.build());
			panelComponent.getChildren().add(LineComponent.builder().left(EventText.describe(t.event, lookup)).build());
		}
		return super.render(graphics);
	}
}
