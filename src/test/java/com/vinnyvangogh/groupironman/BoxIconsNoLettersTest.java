package com.vinnyvangogh.groupironman;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Board 2026-11-19: every picture is an icon; no event type may fall back to a letter tile. */
public class BoxIconsNoLettersTest
{
	private static boolean drewSomething(BoxText.Icon icon)
	{
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		BoxIcons.draw(g, icon, BoxIcons.none(), 2, 2, 12);
		g.dispose();
		for (int y = 0; y < 16; y++)
		{
			for (int x = 0; x < 16; x++)
			{
				if ((img.getRGB(x, y) >>> 24) != 0)
				{
					return true;
				}
			}
		}
		return false;
	}

	@Test
	public void iconPainterNeverDrawsText() throws IOException
	{
		String src = new String(Files.readAllBytes(
			Paths.get("src/main/java/com/vinnyvangogh/groupironman/BoxIcons.java")), StandardCharsets.UTF_8);
		assertFalse("BoxIcons must not draw letters", src.contains("drawString"));
		assertFalse("BoxIcons must not set a font", src.contains("setFont"));
	}

	@Test
	public void everyGlyphIsDrawnAsAPicture()
	{
		for (BoxText.Glyph glyph : BoxText.Glyph.values())
		{
			assertTrue(glyph + " draws nothing", drewSomething(BoxText.Icon.glyph(glyph)));
		}
	}

	@Test
	public void skillWithoutSpriteStillDrawsAPicture()
	{
		assertTrue(drewSomething(BoxText.Icon.skill("Attack")));
	}

	@Test
	public void everyEventTypeIconIsAPictureEvenWithNoSprites()
	{
		for (Map.Entry<GroupEvent.Type, GroupEvent> e : EventFixtures.samples().entrySet())
		{
			BoxText.Line line = BoxText.format(e.getValue(), new FakeItems(), BoxSize.MEDIUM, true, BoxTimes.SHORT,
				EventFixtures.NOW);
			if (line.icon != null && line.icon.itemId < 0)
			{
				assertTrue(e.getKey() + " has no drawn icon", drewSomething(line.icon));
			}
		}
	}
}
