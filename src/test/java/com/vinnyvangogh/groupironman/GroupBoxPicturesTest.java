package com.vinnyvangogh.groupironman;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The Group box draws real pictures (the item, or the game's own sprite) rather than a letter badge, and never cuts
 * a line with "..": it grows to the longest line up to the size's widest, then wraps.
 */
public class GroupBoxPicturesTest
{
	private static final long NOW = EventFixtures.NOW;
	private static final int GIANT_KEY = 20754;

	/** FakeItems plus a name index that knows the Giant key, as the plugin's ItemNameIndex would. */
	private final ItemLookup items = new ItemLookup()
	{
		private final FakeItems base = new FakeItems();

		@Override
		public String name(int id)
		{
			return id == GIANT_KEY ? "Giant key" : base.name(id);
		}

		@Override
		public long price(int id)
		{
			return base.price(id);
		}

		@Override
		public int itemId(String name)
		{
			return "Giant key".equalsIgnoreCase(name) ? GIANT_KEY : ItemNameIndex.PENDING;
		}
	};

	private static final BufferedImage ITEM = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
	private static final BufferedImage SPRITE = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);

	/** Every source answers: items with ITEM, every glyph with SPRITE. */
	private static final BoxIcons.Source ALL = new BoxIcons.Source()
	{
		@Override
		public BufferedImage skill(String name)
		{
			return null;
		}

		@Override
		public BufferedImage item(int id)
		{
			return ITEM;
		}

		@Override
		public BufferedImage glyph(BoxText.Glyph glyph)
		{
			return SPRITE;
		}
	};

	private BoxText.Line line(GroupEvent.Type type, String text, long number, BoxSize size)
	{
		GroupEvent e = GroupEvent.of(type, NOW - 30_000, "Member 1").text(text).number(number).build();
		return BoxText.format(e, items, size, true, BoxTimes.SHORT, NOW);
	}

	@Test
	public void giantKeyClogDrawsTheItemNotALetter()
	{
		BoxText.Line l = line(GroupEvent.Type.CLOG, "Giant key", 0, BoxSize.MEDIUM);
		assertEquals(GIANT_KEY, l.icon.itemId);
		assertSame(ITEM, BoxIcons.picture(l.icon, ALL));
	}

	@Test
	public void unknownClogItemShowsTheCollectionLogBook()
	{
		BoxText.Line l = line(GroupEvent.Type.CLOG, "Some new unique", 0, BoxSize.MEDIUM);
		assertEquals(-1, l.icon.itemId);
		assertEquals(BoxText.Glyph.CLOG, l.icon.glyph);
		assertEquals(ItemID.COLLECTION_LOG, PluginIcons.itemOf(BoxText.Glyph.CLOG));
		assertSame(SPRITE, BoxIcons.picture(l.icon, ALL));
	}

	@Test
	public void aClogItemCarriedOnTheEventWinsOverTheName()
	{
		GroupEvent e = GroupEvent.of(GroupEvent.Type.CLOG, NOW, "Member 1").text("Giant key")
			.items(EventFixtures.items(FakeItems.WHIP, 1)).build();
		assertEquals(FakeItems.WHIP, BoxText.format(e, items, BoxSize.MEDIUM, true, BoxTimes.SHORT, NOW).icon.itemId);
	}

	@Test
	public void petByNameIsItsItemAndWithoutOneTheCatSprite()
	{
		assertEquals(GIANT_KEY, line(GroupEvent.Type.PET, "Giant key", 0, BoxSize.MEDIUM).icon.itemId);
		BoxText.Line unnamed = line(GroupEvent.Type.PET, "", 0, BoxSize.MEDIUM);
		assertEquals(BoxText.Glyph.PET, unnamed.icon.glyph);
		assertTrue(PluginIcons.spriteOf(BoxText.Glyph.PET) >= 0);
		assertSame(SPRITE, BoxIcons.picture(unnamed.icon, ALL));
	}

	@Test
	public void questDiaryBestKillsAndCombatEachHaveAGameSprite()
	{
		Object[][] cases = {
			{GroupEvent.Type.QUEST, "Dragon Slayer II", 0L, BoxText.Glyph.QUEST},
			{GroupEvent.Type.DIARY, "Western Provinces Elite", 0L, BoxText.Glyph.DIARY},
			{GroupEvent.Type.PERSONAL_BEST, "Vorkath 1:12", 0L, BoxText.Glyph.BEST},
			{GroupEvent.Type.KC_MILESTONE, "Zulrah", 500L, BoxText.Glyph.KILLS},
			{GroupEvent.Type.COMBAT_LEVEL, "", 120L, BoxText.Glyph.COMBAT},
		};
		for (Object[] c : cases)
		{
			BoxText.Line l = line((GroupEvent.Type) c[0], (String) c[1], (Long) c[2], BoxSize.MEDIUM);
			BoxText.Glyph glyph = (BoxText.Glyph) c[3];
			assertEquals(c[0].toString(), glyph, l.icon.glyph);
			assertTrue(c[0] + " has no sprite", PluginIcons.spriteOf(glyph) >= 0);
			assertSame(c[0] + " drew no picture", SPRITE, BoxIcons.picture(l.icon, ALL));
		}
	}

	@Test
	public void theLetterIsTheLastResortWhenNoSourceAnswers()
	{
		BoxIcons.Source none = new BoxIcons.Source()
		{
			@Override
			public BufferedImage skill(String name)
			{
				return null;
			}

			@Override
			public BufferedImage item(int id)
			{
				return null;
			}
		};
		BoxText.Line l = line(GroupEvent.Type.QUEST, "Dragon Slayer II", 0, BoxSize.MEDIUM);
		assertNull(BoxIcons.picture(l.icon, none));
		// The item picture missing still falls through to the glyph's sprite before the letter.
		BoxText.Line clog = line(GroupEvent.Type.CLOG, "Giant key", 0, BoxSize.MEDIUM);
		BoxIcons.Source spritesOnly = new BoxIcons.Source()
		{
			@Override
			public BufferedImage skill(String name)
			{
				return null;
			}

			@Override
			public BufferedImage item(int id)
			{
				return null;
			}

			@Override
			public BufferedImage glyph(BoxText.Glyph glyph)
			{
				return SPRITE;
			}
		};
		assertSame(SPRITE, BoxIcons.picture(clog.icon, spritesOnly));
	}

	private static ItemNameIndex.Items table(Map<Integer, String> names, int count)
	{
		return new ItemNameIndex.Items()
		{
			@Override
			public int count()
			{
				return count;
			}

			@Override
			public String baseName(int id)
			{
				return names.get(id);
			}
		};
	}

	@Test
	public void nameIndexFindsAnUntradeableInSlices()
	{
		Map<Integer, String> names = new HashMap<>();
		names.put(GIANT_KEY, "Giant key");
		ItemNameIndex.Items all = table(names, 30_000);
		ItemNameIndex index = new ItemNameIndex();
		assertEquals(ItemNameIndex.PENDING, index.id("Giant key"));
		assertEquals(ItemNameIndex.PENDING, index.id("Not an item"));
		assertTrue(index.claim());
		assertFalse("only one scan at a time", index.claim());
		assertEquals(500, GroupService.NAME_SCAN_SLICE);
		int steps = 0;
		while (!index.step(all, GroupService.NAME_SCAN_SLICE))
		{
			steps++;
			assertTrue("scan never ends", steps < 200);
		}
		assertTrue("scanned in slices, not one frame", steps >= 40);
		assertEquals(GIANT_KEY, index.id("giant KEY "));
		assertEquals(ItemNameIndex.NOT_FOUND, index.id("Not an item"));
		assertEquals(ItemNameIndex.NOT_FOUND, index.id(""));
		assertFalse("nothing new asked for", index.claim());
	}

	@Test
	public void nameIndexWaitsForTheItemListInsteadOfCallingEverythingMissing()
	{
		Map<Integer, String> names = new HashMap<>();
		names.put(5, "Giant key");
		ItemNameIndex index = new ItemNameIndex();
		index.id("Giant key");
		assertTrue(index.claim());
		assertFalse(index.step(table(names, 0), GroupService.NAME_SCAN_SLICE));
		assertEquals(ItemNameIndex.PENDING, index.id("Giant key"));
		assertTrue(index.step(table(names, 10), GroupService.NAME_SCAN_SLICE));
		assertEquals(5, index.id("Giant key"));
	}

	private static Graphics2D graphics()
	{
		Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		return g;
	}

	private static OverlayStyle.EventRow row(String member, String detail, String ago)
	{
		return new OverlayStyle.EventRow(new BoxText.Line(BoxText.Icon.glyph(BoxText.Glyph.CLOG), member, detail, ago),
			Color.WHITE, 1f, ALL);
	}

	private static int boxWidth(OverlayStyle.EventRow r, Graphics2D g, BoxSize size)
	{
		return GroupBoxOverlay.width(java.util.Collections.singletonList(r), g, size);
	}

	/** The padded width the box wants for {@code r}, measured with the same FontMetrics the box uses. */
	private static int wants(OverlayStyle.EventRow r, Graphics2D g)
	{
		return r.natural(g) + GroupBoxOverlay.PADDING;
	}

	/**
	 * A row whose padded width is just past {@code min}, built a word at a time and measured with {@code g}, so the
	 * fixture is wide enough on whatever sans-serif font the machine has (macOS and Linux CI measure text differently).
	 */
	private static OverlayStyle.EventRow rowWiderThan(int min, Graphics2D g)
	{
		StringBuilder detail = new StringBuilder("Western Provinces Elite");
		OverlayStyle.EventRow r = row("Member 1", detail.toString(), "3d");
		for (int words = 0; wants(r, g) <= min; words++)
		{
			assertTrue("text never got wider than " + min, words < 500);
			detail.append(" more");
			r = row("Member 1", detail.toString(), "3d");
		}
		return r;
	}

	@Test
	public void theBoxGrowsToItsLongestLineAndNoFurther()
	{
		Graphics2D g = graphics();
		BoxSize size = BoxSize.MEDIUM;
		assertEquals(330, size.maxWidth());

		OverlayStyle.EventRow small = row("Member 1", "38", "now");
		assertTrue("short fixture fits the size's own width", wants(small, g) <= size.width);
		assertEquals(size.width, boxWidth(small, g, size));

		OverlayStyle.EventRow longer = rowWiderThan(size.width, g);
		assertTrue("one word never jumps past the widest box", wants(longer, g) <= size.maxWidth());
		assertEquals(wants(longer, g), boxWidth(longer, g, size));

		OverlayStyle.EventRow huge = rowWiderThan(size.maxWidth(), g);
		assertEquals(size.maxWidth(), boxWidth(huge, g, size));
	}

	@Test
	public void noLineAtTheDefaultWidthIsCut()
	{
		BufferedImage img = new BufferedImage(600, 80, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		int lineHeight = g.getFontMetrics().getHeight();
		for (BoxSize size : BoxSize.values())
		{
			for (String detail : new String[] {"Giant key", "online W315", "Western Provinces Elite", "Vorkath 1:12"})
			{
				OverlayStyle.EventRow r = row("Member 1", detail, "12m");
				int box = GroupBoxOverlay.width(java.util.Collections.singletonList(r), g, size);
				// The row draws inside the frame's padding. From the default size up a usual line fits on one line;
				// the smaller sizes may wrap it under the member, but never past a second line.
				int h = r.draw(g, 0, 0, box - 16);
				if (size.width >= BoxSize.MEDIUM.width)
				{
					assertEquals(size + " " + detail, lineHeight, h);
				}
				else
				{
					assertTrue(size + " " + detail, h <= 2 * lineHeight);
					List<String> parts = OverlayStyle.EventRow.split(detail, g.getFontMetrics(), 0, box - 16 - 24);
					assertFalse(size + " " + detail + " cut", parts.get(parts.size() - 1).endsWith(".."));
				}
			}
		}
	}

	@Test
	public void aLineWiderThanTheWidestBoxWrapsWholeWordsUnderTheMember()
	{
		FontMetrics fm = graphics().getFontMetrics();
		String detail = "Western Provinces Elite with a long tail";
		int first = fm.stringWidth("Western Provinces") + 2;
		List<String> parts = OverlayStyle.EventRow.split(detail, fm, first, 1_000);
		assertEquals(2, parts.size());
		assertEquals("Western Provinces", parts.get(0));
		assertEquals("Elite with a long tail", parts.get(1));
		assertFalse(parts.get(1).endsWith(".."));
		// Not even the first word fits beside the member: all of it goes to the second line.
		List<String> under = OverlayStyle.EventRow.split("Giant key", fm, 3, 1_000);
		assertEquals("", under.get(0));
		assertEquals("Giant key", under.get(1));
		assertTrue(OverlayStyle.EventRow.split("", fm, 3, 1_000).isEmpty());
	}

	@Test
	public void memberAndTimeStayOnTheFirstLineAndTheRowGrowsForTheWrap()
	{
		Graphics2D g = graphics();
		FontMetrics fm = g.getFontMetrics();
		OverlayStyle.EventRow wraps = row("Member 1", repeat("Giant key ", 8).trim(), "3d");
		BufferedImage img = new BufferedImage(200, 80, BufferedImage.TYPE_INT_ARGB);
		Graphics2D draw = img.createGraphics();
		draw.setFont(g.getFont());
		int h = wraps.draw(draw, 0, 0, 180);
		assertEquals(fm.getHeight() * 2, h);
		int one = row("Member 1", "38", "3d").draw(draw, 0, 0, 180);
		assertEquals(fm.getHeight(), one);
		assertNotNull(img);
	}

	private static String repeat(String s, int n)
	{
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < n; i++)
		{
			b.append(s);
		}
		return b.toString();
	}
}
