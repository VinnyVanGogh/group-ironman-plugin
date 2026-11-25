package com.vinnyvangogh.groupironman;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.PanelComponent;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * "Above player" sat 50px low (PR #142 review): it grows up from the head by the height drawn last frame, and that
 * came from PanelComponent, which sizes itself from the previous frame's children, so a new banner read as only its
 * padding. The height now comes from the blocks once drawn.
 */
public class BannerHeightTest
{
	private static List<LayoutableRenderableEntity> fill(PanelComponent panel)
	{
		panel.getChildren().clear();
		panel.getChildren().add(new OverlayStyle.Header("Level up", "Alice", OverlayStyle.ACCENT));
		panel.getChildren().add(new OverlayStyle.Row("Alice reached 70 Fishing, a long line that wraps", OverlayStyle.TEXT)
			.lines(3));
		return new ArrayList<>(panel.getChildren());
	}

	private static Dimension render(PanelComponent panel)
	{
		Graphics2D g = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB).createGraphics();
		try
		{
			return panel.render(g);
		}
		finally
		{
			g.dispose();
		}
	}

	@Test
	public void theFirstFrameKnowsTheRealHeight()
	{
		PanelComponent panel = new PanelComponent();
		OverlayStyle.frame(panel, OverlayStyle.BANNER_WIDTH);

		List<LayoutableRenderableEntity> first = fill(panel);
		Dimension stale = render(panel);
		int measured = OverlayStyle.drawnHeight(first);
		assertTrue("blocks drew something: " + measured, measured > 12 + 10);
		assertTrue("the panel's own answer lags: " + stale.height, stale.height < measured);

		fill(panel);
		Dimension caughtUp = render(panel);
		assertEquals("one frame on, the panel agrees", caughtUp.height, measured);
	}

	@Test
	public void noBlocksNoHeight()
	{
		assertEquals(0, OverlayStyle.drawnHeight(new ArrayList<>()));
	}
}
