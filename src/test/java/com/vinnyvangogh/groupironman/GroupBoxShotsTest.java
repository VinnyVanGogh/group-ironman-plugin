package com.vinnyvangogh.groupironman;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.overlay.components.PanelComponent;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * Renders the Group box at every size, with and without icons, from placeholder members, into
 * {@code build/group-box-shots/}. Skill icons are RuneLite's own sprites from the client jar; item pictures are
 * flat squares here because ItemManager needs a running client. Also fails if a line's drawn width ever
 * leaves the box.
 */
public class GroupBoxShotsTest
{
	private static final long NOW = EventFixtures.NOW;
	private final FakeItems items = new FakeItems();

	private static final BoxIcons.Source PICTURES = new BoxIcons.Source()
	{
		@Override
		public BufferedImage skill(String name)
		{
			String path = "skill_icons_small/" + name.toLowerCase(java.util.Locale.ROOT) + ".png";
			try (InputStream in = SkillIconManager.class.getClassLoader().getResourceAsStream(path))
			{
				return in == null ? null : ImageIO.read(in);
			}
			catch (IOException e)
			{
				return null;
			}
		}

		@Override
		public BufferedImage item(int id)
		{
			BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = img.createGraphics();
			g.setColor(new Color(0xB0, 0x60, 0x30));
			g.fillRect(2, 2, 12, 12);
			g.dispose();
			return img;
		}
	};

	private List<GroupEvent> events()
	{
		List<GroupEvent> out = new ArrayList<>();
		out.add(GroupEvent.of(GroupEvent.Type.LEVEL_UP, NOW - 20_000, "Member 1").text("Slayer").number(38).build());
		out.add(GroupEvent.of(GroupEvent.Type.ONLINE, NOW - TimeUnit.MINUTES.toMillis(8), "Member 2")
			.text("online").world(480).build());
		out.add(GroupEvent.drop(NOW - TimeUnit.MINUTES.toMillis(40), "Member 3", "Vorkath",
			EventFixtures.items(FakeItems.WHIP, 1)));
		out.add(GroupEvent.of(GroupEvent.Type.PET, NOW - TimeUnit.HOURS.toMillis(2), "Member 1")
			.text("Callisto cub").build());
		out.add(GroupEvent.of(GroupEvent.Type.QUEST, NOW - TimeUnit.HOURS.toMillis(5), "Member 2")
			.text("Dragon Slayer II").number(5).build());
		out.add(GroupEvent.of(GroupEvent.Type.PERSONAL_BEST, NOW - TimeUnit.HOURS.toMillis(30), "Member 3")
			.text("Vorkath 1:12").build());
		out.add(GroupEvent.of(GroupEvent.Type.CLOG, NOW - TimeUnit.DAYS.toMillis(3), "Member 1")
			.text("Twisted bow").build());
		out.add(GroupEvent.of(GroupEvent.Type.DEATH, NOW - 70_000, "Member 3").build());
		return out;
	}

	private BufferedImage shot(BoxSize size, boolean icons)
	{
		List<GroupBoxOverlay.EventLine> lines = new ArrayList<>();
		for (GroupEvent e : events())
		{
			lines.add(new GroupBoxOverlay.EventLine(BoxText.format(e, items, size, icons, BoxTimes.SHORT, NOW),
				new Color(e.type.category.colour)));
		}
		Map<String, Integer> online = new LinkedHashMap<>();
		online.put("Member 1", 480);
		online.put("Member 2", 301);
		PanelComponent panel = new PanelComponent();
		OverlayStyle.frame(panel, size.width);
		GroupBoxOverlay.build(panel, online, new LinkedHashMap<>(), lines, size, PICTURES);
		BufferedImage img = new BufferedImage(size.width + 4, 330, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		g.setColor(new Color(60, 70, 50));
		g.fillRect(0, 0, img.getWidth(), img.getHeight());
		panel.setPreferredLocation(new java.awt.Point(2, 2));
		Dimension used = panel.render(g);
		g.dispose();
		assertTrue("box wider than its size: " + used, used.width <= size.width + 1);
		return img.getSubimage(0, 0, size.width + 4, 250);
	}

	@Test
	public void everySizeRendersInsideItsWidth() throws IOException
	{
		File dir = new File("build/group-box-shots");
		assertTrue(dir.isDirectory() || dir.mkdirs());
		for (BoxSize size : BoxSize.values())
		{
			for (boolean icons : new boolean[] {true, false})
			{
				File out = new File(dir, "group-box-" + size.name().toLowerCase(java.util.Locale.ROOT)
					+ (icons ? "-icons" : "-text") + ".png");
				assertTrue(ImageIO.write(shot(size, icons), "png", out));
			}
		}
	}
}
