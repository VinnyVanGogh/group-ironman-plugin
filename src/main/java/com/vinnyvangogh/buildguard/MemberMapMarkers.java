package com.vinnyvangogh.buildguard;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

/**
 * Online members as markers on the game's world map, from the position in
 * their shared stats. Off unless the player turns on "Show members on world
 * map"; only members heard from in the last few minutes are drawn.
 *
 * <p>This is the in-game world map, not a map in the side panel: the client
 * has no map image a plugin can draw from without downloading tiles, and this
 * plugin makes no network calls.
 */
class MemberMapMarkers
{
	private static final BufferedImage DOT = dot();

	@Inject
	private WorldMapPointManager manager;

	private final Map<String, WorldMapPoint> points = new HashMap<>();

	/** Replaces the markers with {@code online}, leaving out {@code self}: the map already shows you. */
	synchronized void update(Map<String, MemberStats> online, String self)
	{
		Iterator<Map.Entry<String, WorldMapPoint>> it = points.entrySet().iterator();
		while (it.hasNext())
		{
			Map.Entry<String, WorldMapPoint> e = it.next();
			MemberStats s = online.get(e.getKey());
			if (s == null || !s.hasPosition() || !e.getValue().getWorldPoint().equals(at(s)))
			{
				manager.remove(e.getValue());
				it.remove();
			}
		}
		for (Map.Entry<String, MemberStats> e : online.entrySet())
		{
			if (points.containsKey(e.getKey()) || !e.getValue().hasPosition() || e.getKey().equalsIgnoreCase(self))
			{
				continue;
			}
			WorldMapPoint point = WorldMapPoint.builder()
				.worldPoint(at(e.getValue()))
				.image(DOT)
				.name(e.getKey())
				.tooltip(e.getKey())
				.snapToEdge(true)
				.jumpOnClick(true)
				.build();
			manager.add(point);
			points.put(e.getKey(), point);
		}
	}

	synchronized void clear()
	{
		for (WorldMapPoint point : points.values())
		{
			manager.remove(point);
		}
		points.clear();
	}

	private static WorldPoint at(MemberStats s)
	{
		return new WorldPoint(s.x(), s.y(), s.plane());
	}

	/** A 10x10 orange dot with a dark rim, drawn rather than shipped. */
	private static BufferedImage dot()
	{
		BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(0x2B, 0x2B, 0x2B));
		g.fillOval(0, 0, 10, 10);
		g.setColor(new Color(0xD4, 0x8A, 0x1E));
		g.fillOval(2, 2, 6, 6);
		g.dispose();
		return image;
	}
}
