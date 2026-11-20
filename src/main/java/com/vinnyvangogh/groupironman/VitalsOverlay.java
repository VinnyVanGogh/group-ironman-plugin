package com.vinnyvangogh.groupironman;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/**
 * Draws {@link VitalsHud} beside the local player, or in a screen corner. Group members only: the cards come from
 * the group sync and the game's Group channel, never from the players around you.
 */
final class VitalsOverlay extends Overlay
{
	/** How long after the last fight the "only in combat" setting keeps the HUD up. */
	static final long COMBAT_HOLD_MS = 8_000;
	private static final int SIDE_GAP = 36;
	private static final int MARGIN = 8;

	private final GroupIronmanConfig config;
	private final GroupService group;
	private final Client client;
	private long lastCombat;

	VitalsOverlay(GroupIronmanConfig config, GroupService group, Client client)
	{
		this.config = config;
		this.group = group;
		this.client = client;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(OverlayPriority.LOW);
	}

	/** Whether the HUD shows right now, given the "only in combat" setting and when the last fight was. */
	static boolean visible(boolean combatOnly, boolean fighting, long now, long lastCombat)
	{
		return !combatOnly || fighting || now - lastCombat <= COMBAT_HOLD_MS;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.vitalsHud())
		{
			return null;
		}
		Player me = client.getLocalPlayer();
		long now = System.currentTimeMillis();
		boolean fighting = me != null && me.getInteracting() != null;
		if (fighting)
		{
			lastCombat = now;
		}
		if (!visible(config.vitalsHudCombatOnly(), fighting, now, lastCombat))
		{
			return null;
		}
		Map<String, MemberStats> stats = group.roster().onlineStats(now);
		Map<String, Integer> names = GamePresence.othersOnline(stats, group.presence(), group.self());
		List<VitalsHud.Card> cards = VitalsHud.cards(stats, names, group.roster().activities(now),
			VitalsHud.parseMembers(config.vitalsHudMembers()), group.self(), config.vitalsHudSelf());
		if (cards.isEmpty())
		{
			return null;
		}
		VitalsHud.Bars bars = new VitalsHud.Bars(config.vitalsHudHp(), config.vitalsHudPrayer(),
			config.vitalsHudSpec(), config.vitalsHudRun());
		float scale = config.vitalsHudSize().fontScale;
		Dimension size = VitalsHud.measure(graphics, cards, bars, scale);
		int w = client.getCanvasWidth();
		int h = client.getCanvasHeight();
		int x;
		int y;
		switch (config.vitalsHudPosition())
		{
			case TOP_LEFT:
				x = MARGIN;
				y = MARGIN;
				break;
			case TOP_RIGHT:
				x = w - size.width - MARGIN;
				y = MARGIN;
				break;
			case BOTTOM_LEFT:
				x = MARGIN;
				y = h - size.height - MARGIN;
				break;
			case BOTTOM_RIGHT:
				x = w - size.width - MARGIN;
				y = h - size.height - MARGIN;
				break;
			default:
				net.runelite.api.Point at = me == null ? null : Perspective.localToCanvas(client,
					me.getLocalLocation() == null ? new LocalPoint(0, 0) : me.getLocalLocation(), client.getPlane(),
					me.getLogicalHeight() / 2);
				if (at == null)
				{
					return null;
				}
				x = at.getX() + SIDE_GAP;
				y = at.getY() - size.height / 2;
		}
		x = Math.max(0, Math.min(x, w - size.width));
		y = Math.max(0, Math.min(y, h - size.height));
		VitalsHud.draw(graphics, x, y, cards, bars, scale);
		return null;
	}
}
