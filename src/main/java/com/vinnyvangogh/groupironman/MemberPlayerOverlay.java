package com.vinnyvangogh.groupironman;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Group members around you, in their Compare colour: a name above their head, an optional tile outline, and a dot on
 * the minimap. Like core Player Indicators for friends and clan, it reads the players the game already lists on your
 * world, and marks only those on the group's roster. Nothing is drawn in PvP worlds or the Wilderness.
 */
final class MemberPlayerOverlay extends Overlay
{
	/** Which of the two layers this instance draws. */
	enum Layer
	{
		SCENE,
		MINIMAP
	}

	private final Client client;
	private final GroupIronmanConfig config;
	private final Supplier<Map<String, Color>> colours;
	private final Layer layer;

	MemberPlayerOverlay(Client client, GroupIronmanConfig config, Supplier<Map<String, Color>> colours, Layer layer)
	{
		this.client = client;
		this.config = config;
		this.colours = colours;
		this.layer = layer;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(layer == Layer.SCENE ? OverlayLayer.ABOVE_SCENE : OverlayLayer.ABOVE_WIDGETS);
	}

	/** True where marking other players is off the table: a PvP world or the Wilderness. */
	static boolean suppressed(Collection<WorldType> worldTypes, boolean inWilderness)
	{
		return inWilderness || WorldType.isPvpWorld(worldTypes);
	}

	/** The colour to mark {@code name} in, or null when they are not on the roster (or are you). */
	static Color colourFor(Map<String, Color> roster, String name, String self)
	{
		String key = GroupMembers.key(name);
		if (key.isEmpty() || key.equals(GroupMembers.key(self)))
		{
			return null;
		}
		return roster.get(key);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		boolean names = layer == Layer.SCENE && config.memberNames();
		boolean outline = layer == Layer.SCENE && config.memberOutline();
		boolean dots = layer == Layer.MINIMAP && config.memberMinimapDots();
		Map<String, Color> roster = colours.get();
		if (!(names || outline || dots) || roster.isEmpty() || client.getTopLevelWorldView() == null
			|| suppressed(client.getWorldType(), client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) != 0))
		{
			return null;
		}
		Player me = client.getLocalPlayer();
		String self = me == null ? "" : me.getName();
		for (Player p : client.getTopLevelWorldView().players())
		{
			Color c = p == null || p == me ? null : colourFor(roster, p.getName(), self);
			if (c == null)
			{
				continue;
			}
			if (dots)
			{
				Point at = p.getMinimapLocation();
				if (at != null)
				{
					OverlayUtil.renderMinimapLocation(graphics, at, c);
				}
			}
			if (outline)
			{
				Polygon tile = p.getCanvasTilePoly();
				if (tile != null)
				{
					OverlayUtil.renderPolygon(graphics, tile, c);
				}
			}
			if (names)
			{
				OverlayUtil.renderActorOverlay(graphics, p, p.getName(), c);
			}
		}
		return null;
	}
}
