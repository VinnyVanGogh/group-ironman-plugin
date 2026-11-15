package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.vinnyvangogh.testkit.ui.RuneLiteLook;
import com.vinnyvangogh.testkit.ui.SwingDriver;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import static com.vinnyvangogh.groupironman.FakeItems.NOTED_YEW_LOGS;
import static com.vinnyvangogh.groupironman.FakeItems.SHARK;
import static com.vinnyvangogh.groupironman.FakeItems.YEW_LOGS;
import static com.vinnyvangogh.groupironman.GimPanelFixture.MOSS;
import static com.vinnyvangogh.groupironman.GimPanelFixture.SELF;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * A member card's worn and inventory icons on the real panel (Board 2026-11-14): a noted stack draws noted
 * the whole way from the sender's slots to the icon ItemManager is asked for, and an empty worn slot's
 * silhouette stands out from its cell.
 */
public class MemberCardIconsTest
{
	private static final Gson GSON = new Gson();

	@Before
	public void lookLikeRuneLite()
	{
		SwingDriver.onEdt(RuneLiteLook::install);
	}

	private static Map<Integer, Integer> items(int... pairs)
	{
		Map<Integer, Integer> out = new TreeMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			out.put(pairs[i], pairs[i + 1]);
		}
		return out;
	}

	/** What a current sender puts on the wire: noted yew logs in slot 0, a gap, a shark in slot 2. */
	private static String notedYewsOnTheWire()
	{
		List<GroupCodec.Slot> slots = Arrays.asList(GroupCodec.Slot.of(NOTED_YEW_LOGS, 50), GroupCodec.Slot.EMPTY,
			GroupCodec.Slot.of(SHARK, 1));
		return GroupCodec.encodeCarried(GSON, items(YEW_LOGS, 50, SHARK, 1), items(), slots);
	}

	private static GroupCodec.Carried decode(String wire)
	{
		GroupCodec.Decoded d = GroupCodec.decode(GSON, wire);
		assertEquals(GroupCodec.Kind.CARRIED, d.kind);
		return d.carried;
	}

	/** The group scene with {@code moss} as Moss's carried items; your own card shows none, so every cell is Moss's. */
	private static GimPanelFixture.Scene scene(GroupCodec.Carried moss)
	{
		GimPanelFixture.Scene s = GimPanelFixture.groupScene();
		s.roster.putCarried(SELF, new GroupCodec.Carried(items(), items()));
		s.roster.putCarried(MOSS, moss);
		return s;
	}

	@Test
	public void aNotedStackFromTheWireAsksForTheNotedIcon()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = new GimPanelFixture().show(scene(decode(notedYewsOnTheWire())));
			verify(f.items, atLeastOnce()).getImage(NOTED_YEW_LOGS, 50, true);
			verify(f.items, never()).getImage(eq(YEW_LOGS), anyInt(), anyBoolean());
			verify(f.items, atLeastOnce()).getImage(SHARK, 1, false);
		});
	}

	/** The disk cache fills a card at login, before the member resends: it used to keep counts only. */
	@Test
	public void aNotedStackSurvivesTheDiskCache()
	{
		GroupRoster before = new GroupRoster();
		before.putCarried(MOSS, decode(notedYewsOnTheWire()));
		GroupRoster after = new GroupRoster();
		assertTrue(GroupCache.read(GSON, GroupCache.write(GSON, before), after));
		GroupCodec.Carried cached = after.allCarried().get(MOSS);

		assertEquals(GroupCodec.INVENTORY_SLOTS, cached.slots.size());
		assertEquals(NOTED_YEW_LOGS, cached.slots.get(0).id);
		assertTrue(cached.slots.get(1).empty());
		// Find, ::find and totals keep counting the item, not the note.
		assertEquals(items(YEW_LOGS, 50, SHARK, 1), cached.inventory);

		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = new GimPanelFixture().show(scene(cached));
			verify(f.items, atLeastOnce()).getImage(NOTED_YEW_LOGS, 50, true);
			verify(f.items, never()).getImage(eq(YEW_LOGS), anyInt(), anyBoolean());
		});
	}

	/** A build from before slots sends canonical counts only: there is no way to know it was noted. */
	@Test
	public void anOlderBuildsCountsOnlyMessageStillDrawsTheItem()
	{
		GroupCodec.Carried old = decode(GroupCodec.encodeCarried(GSON, items(YEW_LOGS, 50), items()));
		assertTrue(old.slots.isEmpty());
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = new GimPanelFixture().show(scene(old));
			verify(f.items, atLeastOnce()).getImage(YEW_LOGS, 50, true);
			verify(f.items, never()).getImage(eq(NOTED_YEW_LOGS), anyInt(), anyBoolean());
		});
	}

	// ---- Empty worn slots ----

	/**
	 * A stand-in for the game's EQUIPMENT_SLOT_* sprite (the cache is not available headless): a dark,
	 * brownish, opaque shape on a transparent ground, about as dark as the game's own.
	 */
	static BufferedImage gameLikeSilhouette()
	{
		BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		try
		{
			g.setColor(new Color(0x3E, 0x35, 0x2B));
			g.fillOval(4, 4, 24, 24);
		}
		finally
		{
			g.dispose();
		}
		return img;
	}

	private static SpriteManager silhouettes()
	{
		BufferedImage sprite = gameLikeSilhouette();
		SpriteManager sprites = mock(SpriteManager.class);
		doAnswer(inv ->
		{
			Consumer<BufferedImage> user = inv.getArgument(2);
			user.accept(sprite);
			return null;
		}).when(sprites).getSpriteAsync(anyInt(), anyInt(), ArgumentMatchers.<Consumer<BufferedImage>>any());
		return sprites;
	}

	private static double luminance(int argb)
	{
		Color c = new Color(argb, true);
		return 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
	}

	private static void collect(Container root, List<GroupIronmanPanel.SmallItem> into)
	{
		for (Component c : root.getComponents())
		{
			if (c instanceof GroupIronmanPanel.SmallItem)
			{
				into.add((GroupIronmanPanel.SmallItem) c);
			}
			else if (c instanceof Container)
			{
				collect((Container) c, into);
			}
		}
	}

	@Test
	public void lighteningKeepsTheShapeAndASpriteNotLoaded()
	{
		assertEquals(null, WornSlotIcons.lighten(null));
		BufferedImage light = WornSlotIcons.lighten(gameLikeSilhouette());
		assertEquals("outside the shape stays clear", 0, light.getRGB(0, 0) >>> 24);
		assertEquals("inside keeps its alpha", 0xFF, light.getRGB(16, 16) >>> 24);
		assertTrue(luminance(light.getRGB(16, 16)) > luminance(gameLikeSilhouette().getRGB(16, 16)) + 40);
	}

	@Test
	public void anEmptyWornSlotStandsOutFromItsCellAndTheCard()
	{
		GroupCodec.Carried moss = new GroupCodec.Carried(items(SHARK, 1), items(FakeItems.WHIP, 1),
			Collections.singletonList(GroupCodec.Slot.of(SHARK, 1)));
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = new GimPanelFixture(silhouettes()).show(scene(moss));
			BufferedImage shot = f.ui.render(GimPanelFixture.PANEL_WIDTH, 0, 1.0);
			List<GroupIronmanPanel.SmallItem> cells = new ArrayList<>();
			collect(f.ui.root(), cells);
			int emptyWorn = 0;
			Rectangle crop = null;
			for (GroupIronmanPanel.SmallItem cell : cells)
			{
				Point at = SwingUtilities.convertPoint(cell, 0, 0, f.ui.root());
				Rectangle r = new Rectangle(at.x, at.y, cell.getWidth(), cell.getHeight());
				crop = crop == null ? r : crop.union(r);
				if (cell.emptySlot < 0)
				{
					continue;
				}
				emptyWorn++;
				double ground = luminance(shot.getRGB(at.x + 1, at.y + 1));
				double shape = luminance(shot.getRGB(at.x + GroupIronmanPanel.SmallItem.W / 2,
					at.y + GroupIronmanPanel.SmallItem.H / 2));
				assertTrue("silhouette " + shape + " stands out from its cell " + ground, shape - ground >= 40);
				assertTrue("silhouette " + shape + " stays muted, not an item", shape <= 170);
				assertTrue("empty cell " + ground + " shows against the card",
					ground - luminance(ColorScheme.DARKER_GRAY_COLOR.getRGB()) >= 15);
			}
			// Head, cape, neck, ammo, body, shield, legs, hands, feet, ring: everything but the weapon.
			assertEquals(10, emptyWorn);
			assertFalse(crop == null);
			crop.grow(4, 4);
			crop = crop.intersection(new Rectangle(0, 0, shot.getWidth(), shot.getHeight()));
			BufferedImage out = SwingDriver.fit(f.ui.render(GimPanelFixture.PANEL_WIDTH, 0, 2.0)
				.getSubimage(crop.x * 2, crop.y * 2, crop.width * 2, crop.height * 2), crop.width * 2,
				crop.height * 2, ReadmeShots.BACKGROUND);
			SwingDriver.writePng(out, new File(ReadmeShots.outputDir(), "member-card-slots.png"));
		});
	}
}
