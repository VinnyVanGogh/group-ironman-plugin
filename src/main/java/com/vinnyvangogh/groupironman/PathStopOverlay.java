package com.vinnyvangogh.groupironman;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import java.util.function.Supplier;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.PanelComponent;

/**
 * The Shopping tab's "Path to" stop, on screen while the path runs: where you
 * are heading, what to get there and for whom. It shows only while a path is
 * in force, so it needs no screen space the rest of the time. Reads the plan
 * the plugin builds each redraw; draws nothing of its own accord.
 */
final class PathStopOverlay extends OverlayPanel
{
	/** Lines shown at most; the rest are counted. */
	static final int MOST_ENTRIES = 6;

	private final GroupIronmanConfig config;
	private final Supplier<PathStopPlan> plan;

	PathStopOverlay(GroupIronmanConfig config, Supplier<PathStopPlan> plan)
	{
		this.config = config;
		this.plan = plan;
		setPosition(OverlayPosition.TOP_LEFT);
		OverlayStyle.frame(panelComponent, OverlayStyle.WIDTH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		PathStopPlan p = plan.get();
		if (p == null || !config.overlayPathStop())
		{
			return null;
		}
		build(panelComponent, p);
		return super.render(graphics);
	}

	static void build(PanelComponent panel, PathStopPlan p)
	{
		List<LayoutableRenderableEntity> kids = panel.getChildren();
		kids.add(new OverlayStyle.Header("Path to", null, OverlayStyle.ACCENT));
		kids.add(new OverlayStyle.Row(p.stop, OverlayStyle.TEXT).lines(2));
		if (p.entries.isEmpty())
		{
			kids.add(new OverlayStyle.Row("Nothing left to get here", OverlayStyle.MUTED));
		}
		else
		{
			kids.add(new OverlayStyle.Rule());
			kids.add(new OverlayStyle.Row(p.verb(), OverlayStyle.MUTED));
		}
		int shown = Math.min(MOST_ENTRIES, p.entries.size());
		for (int i = 0; i < shown; i++)
		{
			PathStopPlan.Entry e = p.entries.get(i);
			kids.add(new OverlayStyle.Row(e.item, OverlayStyle.TEXT).dot(OverlayStyle.ACCENT)
				.right("x" + Amounts.GAME.of(e.amount), OverlayStyle.ACCENT));
			String detail = e.forWho.isEmpty() ? e.price : e.price.isEmpty() ? "for " + e.forWho
				: e.price + " - for " + e.forWho;
			if (!detail.isEmpty())
			{
				kids.add(new OverlayStyle.Row(detail, OverlayStyle.MUTED).indent(12));
			}
		}
		if (p.entries.size() > shown)
		{
			kids.add(new OverlayStyle.Row("+" + (p.entries.size() - shown) + " more in the Shopping tab",
				OverlayStyle.MUTED).indent(12));
		}
		if (!p.then.isEmpty())
		{
			kids.add(new OverlayStyle.Rule());
			kids.add(new OverlayStyle.Row("Then: " + p.then, OverlayStyle.MUTED));
		}
	}
}
