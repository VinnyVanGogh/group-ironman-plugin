package com.vinnyvangogh.groupironman;

import java.awt.*;
import java.awt.Point;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.runelite.api.*;
import net.runelite.client.ui.overlay.*;

/**
 * Event banners: one title and one line per event, newest on top, each for
 * the configured time. Draws only what {@link ToastQueue} says is visible;
 * the text is built when the event arrives, not every frame.
 */
final class EventToastOverlay extends OverlayPanel
{
	/** The right-click entry that clears every banner. */
	static final String DISMISS = "Dismiss";

	private final ToastQueue toasts;
	private final GroupIronmanConfig config;
	private final ItemLookup lookup;
	private final Supplier<EasterEggs.Settings> eggs;
	private final Client client;
	/** Height drawn last frame: "Above player" grows upward from the head, so it needs it before drawing. */
	private int lastHeight;

	EventToastOverlay(ToastQueue toasts, GroupIronmanConfig config, ItemLookup lookup,
		Supplier<EasterEggs.Settings> eggs, Client client)
	{
		this.client = client;
		this.toasts = toasts;
		this.config = config;
		this.lookup = lookup;
		this.eggs = eggs;
		setPosition(config.overlayPosition().position);
		OverlayStyle.frame(panelComponent, OverlayStyle.BANNER_WIDTH);
		getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, DISMISS, "Group banners"));
	}

	void reposition()
	{
		setPosition(config.overlayPosition().position);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		long now = System.currentTimeMillis();
		long duration = TimeUnit.SECONDS.toMillis(config.overlayDurationSeconds());
		List<ToastQueue.Toast> visible = toasts.visible(now, duration, config.overlayMaxStacked(), e -> true);
		int held = toasts.held(now, duration);
		if (visible.isEmpty() && held == 0)
		{
			return null;
		}
		EasterEggs.Settings egg = eggs.get();
		boolean first = true;
		for (ToastQueue.Toast t : visible)
		{
			if (!first)
			{
				panelComponent.getChildren().add(new OverlayStyle.Rule());
			}
			first = false;
			Color colour = new Color(t.event.type.category.colour);
			panelComponent.getChildren().add(new OverlayStyle.Header(EventText.title(t.event), t.event.member, colour));
			panelComponent.getChildren().add(new OverlayStyle.Row(
				EasterEggs.tagged(EventText.describe(t.event, lookup), t.event, egg), OverlayStyle.TEXT).lines(3));
		}
		if (held > 0)
		{
			if (!first)
			{
				panelComponent.getChildren().add(new OverlayStyle.Rule());
			}
			panelComponent.getChildren().add(new OverlayStyle.Row(ToastQueue.heldLine(held), OverlayStyle.TEXT));
		}
		if (config.overlayPosition() == ToastPosition.ABOVE_PLAYER)
		{
			panelComponent.setPreferredLocation(BannerAnchor.place(headPoint(graphics),
				client.getCanvasWidth(), client.getCanvasHeight(), OverlayStyle.BANNER_WIDTH, lastHeight));
		}
		else
		{
			panelComponent.setPreferredLocation(new Point());
		}
		Dimension drawn = super.render(graphics);
		lastHeight = drawn == null ? 0 : drawn.height;
		return drawn;
	}

	/** Just above the overhead text, so the name and hitsplats stay clear; null while off screen. */
	private Point headPoint(Graphics2D graphics)
	{
		Player me = client.getLocalPlayer();
		if (me == null)
		{
			return null;
		}
		net.runelite.api.Point p = me.getCanvasTextLocation(graphics, " ", me.getLogicalHeight() + BannerAnchor.LIFT);
		return p == null ? null : new Point(p.getX(), p.getY());
	}
}
