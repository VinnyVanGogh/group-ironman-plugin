package com.vinnyvangogh.groupironman;

import java.awt.Point;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class BannerAnchorTest
{
	@Test
	public void theNewValueIsLastAndTheExistingOnesKeepTheirOrder()
	{
		ToastPosition[] v = ToastPosition.values();
		assertEquals(ToastPosition.TOP_CENTER, v[0]);
		assertEquals(ToastPosition.ABOVE_CHATBOX, v[3]);
		assertEquals(ToastPosition.ABOVE_PLAYER, v[v.length - 1]);
		assertEquals("Above player", ToastPosition.ABOVE_PLAYER.toString());
		assertEquals(ToastPosition.ABOVE_PLAYER, ToastPosition.valueOf("ABOVE_PLAYER"));
	}

	@Test
	public void theDefaultIsStillTopCentre()
	{
		assertEquals(ToastPosition.TOP_CENTER, ((GroupIronmanConfig) new GroupIronmanConfig() { }).overlayPosition());
	}

	@Test
	public void theStackSitsCentredAboveTheHead()
	{
		assertEquals(new Point(400 - 100, 300 - 60), BannerAnchor.place(new Point(400, 300), 1000, 700, 200, 60));
	}

	@Test
	public void anOffScreenPlayerFallsBackToTopCentre()
	{
		Point top = new Point((1000 - 200) / 2, 10);
		assertEquals(top, BannerAnchor.place(null, 1000, 700, 200, 60));
		assertEquals(top, BannerAnchor.place(new Point(-5, 300), 1000, 700, 200, 60));
		assertEquals(top, BannerAnchor.place(new Point(400, 900), 1000, 700, 200, 60));
	}
}
