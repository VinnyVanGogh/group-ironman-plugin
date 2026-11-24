package com.vinnyvangogh.groupironman;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import net.runelite.api.Client;
import net.runelite.api.Player;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * "Above player" drew off to the side (RUNELITE-512): the head point is in canvas pixels, but RuneLite hands a
 * banner the player once dragged or snapped to a corner a graphics already moved to that corner. These render the
 * real overlay into an image, as the renderer would in each case, and check where the banner's pixels land. The
 * shots go to build/above-player-shots.
 */
public class AbovePlayerBannerTest
{
	private static final int CANVAS_W = 1000;
	private static final int CANVAS_H = 700;
	private static final Point HEAD = new Point(420, 330);

	private static net.runelite.api.Point rl(Point p)
	{
		return p == null ? null : new net.runelite.api.Point(p.x, p.y);
	}

	private static Client client(Point head, int w, int h)
	{
		Client client = mock(Client.class);
		Player me = mock(Player.class);
		when(client.getLocalPlayer()).thenReturn(me);
		when(client.getCanvasWidth()).thenReturn(w);
		when(client.getCanvasHeight()).thenReturn(h);
		when(me.getLogicalHeight()).thenReturn(200);
		when(me.getCanvasTextLocation(any(Graphics2D.class), anyString(), anyInt())).thenReturn(rl(head));
		return client;
	}

	private static EventToastOverlay overlay(Client client)
	{
		ToastQueue toasts = new ToastQueue();
		toasts.add(EventFixtures.sample(GroupEvent.Type.values()[0]), System.currentTimeMillis());
		GroupIronmanConfig config = new GroupIronmanConfig()
		{
			@Override
			public ToastPosition overlayPosition()
			{
				return ToastPosition.ABOVE_PLAYER;
			}
		};
		ItemLookup lookup = new ItemLookup()
		{
			@Override
			public String name(int id)
			{
				return "item " + id;
			}

			@Override
			public long price(int id)
			{
				return 0;
			}
		};
		return new EventToastOverlay(toasts, config, lookup, () -> new EasterEggs.Settings(false, 0, 0), client);
	}

	/** Two frames (the first learns the height), the second drawn with the renderer's move {@code dx, dy}. */
	private static BufferedImage frame(EventToastOverlay o, int w, int h, int dx, int dy)
	{
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for (int i = 0; i < 2; i++)
		{
			img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = img.createGraphics();
			g.translate(dx, dy);
			o.render(g);
			g.dispose();
		}
		return img;
	}

	/** Every pixel drawn, in canvas pixels. */
	private static Rectangle drawn(BufferedImage img)
	{
		Rectangle r = null;
		for (int y = 0; y < img.getHeight(); y++)
		{
			for (int x = 0; x < img.getWidth(); x++)
			{
				if ((img.getRGB(x, y) >>> 24) != 0)
				{
					Rectangle px = new Rectangle(x, y, 1, 1);
					r = r == null ? px : r.union(px);
				}
			}
		}
		return r;
	}

	private static void save(BufferedImage img, String name) throws IOException
	{
		File dir = new File("build/above-player-shots");
		assertTrue(dir.isDirectory() || dir.mkdirs());
		assertTrue(ImageIO.write(img, "png", new File(dir, name + ".png")));
	}

	private static void assertAboveHead(Rectangle r)
	{
		assertNotNull("drew nothing", r);
		assertEquals("centred on the head: " + r, HEAD.x, r.getCenterX(), 2);
		assertEquals("bottom at the head: " + r, HEAD.y, r.getMaxY(), 2);
	}

	@Test
	public void aDynamicBannerSitsCentredAboveTheHead() throws IOException
	{
		BufferedImage img = frame(overlay(client(HEAD, CANVAS_W, CANVAS_H)), CANVAS_W, CANVAS_H, 0, 0);
		save(img, "dynamic");
		assertAboveHead(drawn(img));
	}

	@Test
	public void aBannerOnceSnappedToACornerStillSitsAboveTheHead() throws IOException
	{
		// What RuneLite does when a saved preferred position outranks DYNAMIC: the graphics is moved to the corner.
		BufferedImage topRight = frame(overlay(client(HEAD, CANVAS_W, CANVAS_H)), CANVAS_W, CANVAS_H, 760, 10);
		save(topRight, "snapped-top-right");
		assertAboveHead(drawn(topRight));
		BufferedImage dragged = frame(overlay(client(HEAD, CANVAS_W, CANVAS_H)), CANVAS_W, CANVAS_H, 37, 512);
		save(dragged, "dragged");
		assertAboveHead(drawn(dragged));
	}

	@Test
	public void fixedModeSizedCanvasToo() throws IOException
	{
		// Fixed mode is a 765x503 canvas; stretched mode draws overlays at that size before stretching.
		Point head = new Point(256, 220);
		BufferedImage img = frame(overlay(client(head, 765, 503)), 765, 503, 0, 0);
		save(img, "fixed");
		Rectangle r = drawn(img);
		assertNotNull(r);
		assertEquals(head.x, r.getCenterX(), 2);
		assertEquals(head.y, r.getMaxY(), 2);
	}

	@Test
	public void nearTheEdgeTheBannerStaysOnTheCanvas()
	{
		Rectangle r = drawn(frame(overlay(client(new Point(15, 40), CANVAS_W, CANVAS_H)), CANVAS_W, CANVAS_H, 0, 0));
		assertNotNull(r);
		assertTrue("inside: " + r, r.x >= 0 && r.y >= 0 && r.getMaxX() <= CANVAS_W);
	}

	@Test
	public void offScreenKeepsTheLastPlaceThenTopCentreWhenNeverPlaced()
	{
		Point last = new Point(300, 200);
		assertEquals(last, BannerAnchor.place(null, last, CANVAS_W, CANVAS_H, 200, 60));
		assertEquals(last, BannerAnchor.place(new Point(-1, 5), last, CANVAS_W, CANVAS_H, 200, 60));
		assertEquals(new Point((CANVAS_W - 200) / 2, 10), BannerAnchor.place(null, null, CANVAS_W, CANVAS_H, 200, 60));
	}

	@Test
	public void theRenderersMoveIsTakenOffIncludingAScale()
	{
		AffineTransform t = new AffineTransform();
		t.scale(2, 2);
		t.translate(100, 50);
		assertEquals(new Point(200, 250), BannerAnchor.toGraphics(new Point(300, 300), t));
		assertEquals(new Point(300, 300), BannerAnchor.toGraphics(new Point(300, 300), new AffineTransform()));
	}
}
