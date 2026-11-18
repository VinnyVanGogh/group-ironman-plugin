package com.vinnyvangogh.groupironman;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.PanelComponent;

/**
 * The combined group box: who is online and on which world, then the latest
 * events. Off by default ("Group box"). Online is what the game's Group channel
 * shows, plugin or not ({@link GamePresence}), and members heard from in the
 * last three minutes ({@link Presence}) unless the game says they left.
 */
final class GroupBoxOverlay extends OverlayPanel
{
	private static final Color ACTIVITY = new Color(0xE0, 0xB0, 0x40);
	private final ToastQueue toasts;
	private final GroupIronmanConfig config;
	private final GroupService group;
	private final Supplier<EasterEggs.Settings> eggs;

	GroupBoxOverlay(ToastQueue toasts, GroupIronmanConfig config, GroupService group,
		Supplier<EasterEggs.Settings> eggs)
	{
		this.toasts = toasts;
		this.config = config;
		this.group = group;
		this.eggs = eggs;
		setPosition(OverlayPosition.TOP_LEFT);
		OverlayStyle.frame(panelComponent, OverlayStyle.WIDTH);
	}

	/** One recent event, already worded. */
	static final class EventLine
	{
		final String text;
		final String ago;
		final Color colour;

		EventLine(String text, String ago, Color colour)
		{
			this.text = text;
			this.ago = ago;
			this.colour = colour;
		}
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.overlayGroupBox())
		{
			return null;
		}
		long now = System.currentTimeMillis();
		Map<String, Integer> online = GamePresence.othersOnline(group.roster().onlineStats(now), group.presence(),
			group.self());
		EasterEggs.Settings egg = eggs.get();
		List<EventLine> events = new ArrayList<>();
		for (GroupEvent event : toasts.recent(config.overlayGroupBoxEvents()))
		{
			events.add(new EventLine(EasterEggs.tagged(EventText.sentence(event, group.lookup), event, egg),
				BoostText.ago(now, event.at), new Color(event.type.category.colour)));
		}
		build(panelComponent, online, group.roster().activities(now), events);
		return super.render(graphics);
	}

	/**
	 * The box's content: who is online and where, what they are doing (formatted when the
	 * state arrived, not here: this runs every frame), then the latest events.
	 */
	static void build(PanelComponent panel, Map<String, Integer> online, Map<String, ActivityState> activities,
		List<EventLine> events)
	{
		List<LayoutableRenderableEntity> kids = panel.getChildren();
		kids.add(new OverlayStyle.Header("Group", online.isEmpty() ? "" : online.size() + " online",
			OverlayStyle.ACCENT));
		if (online.isEmpty())
		{
			kids.add(new OverlayStyle.Row("Nobody else online", OverlayStyle.MUTED));
		}
		for (Map.Entry<String, Integer> e : online.entrySet())
		{
			int world = e.getValue();
			kids.add(new OverlayStyle.Row(e.getKey(), OverlayStyle.TEXT).dot(OverlayStyle.ONLINE)
				.right(world > 0 ? "W" + world : "", OverlayStyle.MUTED));
			ActivityState a = activities.get(e.getKey());
			if (a != null)
			{
				kids.add(new OverlayStyle.Row(a.overlayLine(), ACTIVITY).indent(12));
			}
		}
		if (!events.isEmpty())
		{
			kids.add(new OverlayStyle.Rule());
		}
		for (EventLine event : events)
		{
			kids.add(new OverlayStyle.Row(event.text, event.colour).right(event.ago, OverlayStyle.MUTED));
		}
	}
}
