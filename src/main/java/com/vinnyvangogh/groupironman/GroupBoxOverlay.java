package com.vinnyvangogh.groupironman;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Map;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * The combined group box: who is online and on which world, then the latest
 * events. Off by default ("Group box"). Online is what the game's Group channel
 * shows, plugin or not ({@link GamePresence}), and members heard from in the
 * last three minutes ({@link Presence}) unless the game says they left.
 */
final class GroupBoxOverlay extends OverlayPanel
{
	private final ToastQueue toasts;
	private final GroupIronmanConfig config;
	private final GroupService group;

	GroupBoxOverlay(ToastQueue toasts, GroupIronmanConfig config, GroupService group)
	{
		this.toasts = toasts;
		this.config = config;
		this.group = group;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.overlayGroupBox())
		{
			return null;
		}
		long now = System.currentTimeMillis();
		panelComponent.getChildren().add(TitleComponent.builder().text("Group").build());
		Map<String, Integer> online = GamePresence.othersOnline(group.roster().onlineStats(now), group.presence(),
			group.self());
		if (online.isEmpty())
		{
			panelComponent.getChildren().add(LineComponent.builder().left("Nobody else online").leftColor(Color.GRAY)
				.build());
		}
		for (Map.Entry<String, Integer> e : online.entrySet())
		{
			int world = e.getValue();
			panelComponent.getChildren().add(LineComponent.builder().left(e.getKey())
				.right(world > 0 ? "W" + world : "").rightColor(Color.GREEN).build());
		}
		for (GroupEvent event : toasts.recent(config.overlayGroupBoxEvents()))
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(EventText.sentence(event, group.lookup) + " (" + BoostText.ago(now, event.at) + ")")
				.leftColor(new Color(event.type.category.colour)).build());
		}
		return super.render(graphics);
	}
}
