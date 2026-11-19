package com.vinnyvangogh.groupironman;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BannerEffectsTest
{
	@Test
	public void levelsGetFireworksAndMilestonesGetTheBigShow()
	{
		assertEquals(BannerEffects.Kind.FIREWORKS, BannerEffects.kindOf(GroupEvent.Type.LEVEL_UP));
		assertEquals(BannerEffects.Kind.FIREWORKS, BannerEffects.kindOf(GroupEvent.Type.TOTAL_LEVEL));
		assertEquals(BannerEffects.Kind.GRAND, BannerEffects.kindOf(GroupEvent.Type.MAXED));
		assertEquals(BannerEffects.Kind.GRAND, BannerEffects.kindOf(GroupEvent.Type.XP_MILESTONE));
		assertEquals(BannerEffects.Kind.GRAND, BannerEffects.kindOf(GroupEvent.Type.KC_MILESTONE));
		assertEquals(BannerEffects.Kind.NONE, BannerEffects.kindOf(GroupEvent.Type.DEATH));
		assertEquals(BannerEffects.Kind.NONE, BannerEffects.kindOf(GroupEvent.Type.ONLINE));
	}

	@Test
	public void theBigShowIsBiggerAndLongerThanTheFirework()
	{
		assertTrue(BannerEffects.Kind.GRAND.bursts > BannerEffects.Kind.FIREWORKS.bursts);
		assertTrue(BannerEffects.Kind.GRAND.sparks > BannerEffects.Kind.FIREWORKS.sparks);
		assertTrue(BannerEffects.Kind.GRAND.durationMs > BannerEffects.Kind.FIREWORKS.durationMs);
	}

	@Test
	public void everyBurstHasGoneOffAndDiedWithinTheShow()
	{
		for (BannerEffects.Kind k : new BannerEffects.Kind[]{BannerEffects.Kind.FIREWORKS, BannerEffects.Kind.GRAND})
		{
			long last = BannerEffects.burstStart(k.bursts - 1) + BannerEffects.SPARK_LIFE_MS;
			assertTrue(k + " ends before its last spark dies", k.durationMs >= last);
		}
	}

	@Test
	public void aSparkIsAbsentBeforeItsBurstAndAfterItsLife()
	{
		double[] out = new double[3];
		assertFalse(BannerEffects.spark(5, 2, 0, BannerEffects.burstStart(2) - 1, 80, out));
		assertTrue(BannerEffects.spark(5, 2, 0, BannerEffects.burstStart(2), 80, out));
		assertFalse(BannerEffects.spark(5, 2, 0, BannerEffects.burstStart(2) + BannerEffects.SPARK_LIFE_MS, 80, out));
	}

	@Test
	public void theSameSeedAndTimeAlwaysGiveTheSameSpark()
	{
		double[] a = new double[3];
		double[] b = new double[3];
		assertTrue(BannerEffects.spark(99, 1, 4, 900, 80, a));
		assertTrue(BannerEffects.spark(99, 1, 4, 900, 80, b));
		assertEquals(a[0], b[0], 0);
		assertEquals(a[1], b[1], 0);
		assertEquals(a[2], b[2], 0);
	}

	@Test
	public void sparksFadeOutAndNeverLeaveTheValidOpacityRange()
	{
		double[] out = new double[3];
		for (long t = 0; t < BannerEffects.SPARK_LIFE_MS; t += 50)
		{
			assertTrue(BannerEffects.spark(7, 0, 3, t, 120, out));
			assertTrue(out[2] >= 0 && out[2] <= 1);
		}
		BannerEffects.spark(7, 0, 3, BannerEffects.SPARK_LIFE_MS - 1, 120, out);
		assertTrue(out[2] < 0.05);
	}

	@Test
	public void theFlashOnlyOpensTheShow()
	{
		assertEquals(1, BannerEffects.flash(0), 1e-9);
		assertEquals(0, BannerEffects.flash(600), 0);
		assertEquals(0, BannerEffects.flash(-5), 0);
	}

	@Test
	public void drawingPaintsDuringTheShowAndNothingAfterIt()
	{
		Rectangle panel = new Rectangle(100, 100, 250, 60);
		assertTrue(painted(BannerEffects.Kind.GRAND, 100, panel));
		assertTrue(painted(BannerEffects.Kind.FIREWORKS, 900, panel));
		assertFalse(painted(BannerEffects.Kind.GRAND, BannerEffects.Kind.GRAND.durationMs, panel));
		assertFalse(painted(BannerEffects.Kind.NONE, 100, panel));
	}

	private static boolean painted(BannerEffects.Kind kind, long elapsed, Rectangle panel)
	{
		BufferedImage img = new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB);
		BannerEffects.draw(img.createGraphics(), kind, 1234, elapsed, panel);
		for (int x = 0; x < img.getWidth(); x++)
		{
			for (int y = 0; y < img.getHeight(); y++)
			{
				if ((img.getRGB(x, y) >>> 24) != 0)
				{
					return true;
				}
			}
		}
		return false;
	}
}
