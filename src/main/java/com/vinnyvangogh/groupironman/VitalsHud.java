package com.vinnyvangogh.groupironman;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * The group vitals HUD: one small card per online group member with HP, prayer, special attack and run energy
 * as bars, their world, and a marker for the Wilderness or a running activity. The same numbers the base Party
 * plugin's HUD shows, fed from this plugin's own sync so nobody has to join a party.
 *
 * <p>Only members of this group, and only what they chose to share under "Share stats". Nothing is read about
 * any other player. This class is the model and the drawing; {@link VitalsOverlay} owns where it goes.
 */
final class VitalsHud
{
	static final Color HP = new Color(0x5C, 0xC8, 0x5C);
	static final Color HP_LOW = new Color(0xE0, 0x5A, 0x50);
	static final Color PRAYER = new Color(0x4F, 0xB6, 0xD8);
	static final Color SPEC = new Color(0xE0, 0xB0, 0x40);
	static final Color RUN = new Color(0xE0, 0x8A, 0x3A);
	private static final Color TRACK = new Color(0, 0, 0, 170);
	private static final int BAR_H = 5;
	private static final int BAR_GAP = 3;
	private static final int PAD = 4;
	private static final int ROW_GAP = 3;

	private VitalsHud()
	{
	}

	/** Which bars are drawn. */
	static final class Bars
	{
		final boolean hp;
		final boolean prayer;
		final boolean spec;
		final boolean run;

		Bars(boolean hp, boolean prayer, boolean spec, boolean run)
		{
			this.hp = hp;
			this.prayer = prayer;
			this.spec = spec;
			this.run = run;
		}

		int count()
		{
			return (hp ? 1 : 0) + (prayer ? 1 : 0) + (spec ? 1 : 0) + (run ? 1 : 0);
		}
	}

	/** One member's card, already reduced to what is drawn. A max of 0 means that bar is not known. */
	static final class Card
	{
		final String name;
		final int world;
		final int hp;
		final int hpMax;
		final int prayer;
		final int prayerMax;
		final int spec;
		final int run;
		/** "Wilderness" or the activity's line, or empty. */
		final String marker;

		Card(String name, int world, int hp, int hpMax, int prayer, int prayerMax, int spec, int run, String marker)
		{
			this.name = name;
			this.world = world;
			this.hp = hp;
			this.hpMax = hpMax;
			this.prayer = prayer;
			this.prayerMax = prayerMax;
			this.spec = spec;
			this.run = run;
			this.marker = marker == null ? "" : marker;
		}
	}

	/** Whether a world tile is in the overworld Wilderness (the map's own bounds, not the instanced parts). */
	static boolean wilderness(int x, int y)
	{
		return x >= 2944 && x <= 3391 && y >= 3523 && y <= 3967;
	}

	/** The names the "Members" setting lists: comma or newline separated, case and spacing as the game keys them. */
	static Set<String> parseMembers(String csv)
	{
		Set<String> out = new HashSet<>();
		if (csv == null)
		{
			return out;
		}
		for (String part : csv.split("[,\n]"))
		{
			String key = GroupMembers.key(part);
			if (!key.isEmpty())
			{
				out.add(key);
			}
		}
		return out;
	}

	/**
	 * The cards to draw, in name order.
	 *
	 * @param online online members' last stats (this player's included, under their own name)
	 * @param names who counts as online, name to world (the game's Group channel, plugin or not)
	 * @param only member keys to show, empty for everyone
	 * @param self this player's name
	 * @param showSelf whether this player gets a card
	 */
	static List<Card> cards(Map<String, MemberStats> online, Map<String, Integer> names,
		Map<String, ActivityState> activities, Set<String> only, String self, boolean showSelf)
	{
		String selfKey = GroupMembers.key(self == null ? "" : self);
		List<Card> out = new ArrayList<>();
		Map<String, MemberStats> byKey = new java.util.HashMap<>();
		online.forEach((n, s) -> byKey.put(GroupMembers.key(n), s));
		Map<String, Integer> shown = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		shown.putAll(names);
		if (showSelf && !selfKey.isEmpty() && online.containsKey(self))
		{
			shown.put(self, online.get(self).world());
		}
		for (Map.Entry<String, Integer> e : shown.entrySet())
		{
			String key = GroupMembers.key(e.getKey());
			if (!showSelf && key.equals(selfKey))
			{
				continue;
			}
			if (!only.isEmpty() && !only.contains(key))
			{
				continue;
			}
			MemberStats s = byKey.get(key);
			if (s == null)
			{
				// Online in the game's Group channel but sharing no stats: nothing to draw bars from.
				continue;
			}
			out.add(card(e.getKey(), s, activities.get(e.getKey())));
		}
		return out;
	}

	static Card card(String name, MemberStats s, ActivityState activity)
	{
		MemberStats.Level hp = s.skills().get("Hitpoints");
		MemberStats.Level prayer = s.skills().get("Prayer");
		String marker = activity != null && !activity.overlayLine().isEmpty() ? activity.overlayLine()
			: s.hasPosition() && wilderness(s.x(), s.y()) ? "Wilderness" : "";
		return new Card(name, s.world(), hp == null ? 0 : hp.boosted, hp == null ? 0 : hp.real,
			prayer == null ? 0 : prayer.boosted, prayer == null ? 0 : prayer.real, s.specialAttack(),
			s.runEnergy(), marker);
	}

	/** Fill of a bar, 0 to 1. */
	static float fill(int value, int max)
	{
		if (max <= 0)
		{
			return 0f;
		}
		return Math.max(0f, Math.min(1f, value / (float) max));
	}

	/** The card's size at this font scale: what {@link #draw} will take, so an overlay can place it first. */
	static Dimension measure(Graphics2D g, List<Card> cards, Bars bars, float scale)
	{
		Font base = g.getFont();
		g.setFont(base.deriveFont(base.getSize2D() * scale * 0.9f));
		FontMetrics fm = g.getFontMetrics();
		int w = 0;
		int h = 0;
		for (Card c : cards)
		{
			w = Math.max(w, fm.stringWidth(head(c)) + 2 * PAD);
			h += cardHeight(fm, c, bars) + ROW_GAP;
		}
		g.setFont(base);
		int barsW = bars.count() * 26 + Math.max(0, bars.count() - 1) * BAR_GAP + 2 * PAD;
		return new Dimension(Math.max(w, Math.max(barsW, 60)), Math.max(0, h - ROW_GAP));
	}

	private static String head(Card c)
	{
		String right = c.world > 0 ? " W" + c.world : "";
		return c.name + right;
	}

	private static int cardHeight(FontMetrics fm, Card c, Bars bars)
	{
		int lines = 1 + (c.marker.isEmpty() ? 0 : 1);
		return PAD + lines * fm.getHeight() + (bars.count() > 0 ? BAR_H + 2 : 0) + PAD;
	}

	/** Draws the cards with the top left at {@code x, y}; returns the size drawn. */
	static Dimension draw(Graphics2D g, int x, int y, List<Card> cards, Bars bars, float scale)
	{
		Dimension size = measure(g, cards, bars, scale);
		Font base = g.getFont();
		g.setFont(base.deriveFont(base.getSize2D() * scale * 0.9f));
		FontMetrics fm = g.getFontMetrics();
		Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		int top = y;
		for (Card c : cards)
		{
			int h = cardHeight(fm, c, bars);
			g.setColor(OverlayStyle.BACKGROUND);
			g.fillRoundRect(x, top, size.width, h, 6, 6);
			int ty = top + PAD + fm.getAscent();
			String name = OverlayStyle.fit(c.name, fm, size.width - 2 * PAD - (c.world > 0 ? fm.stringWidth(" W" + c.world) : 0));
			OverlayUtil.renderTextLocation(g, new net.runelite.api.Point(x + PAD, ty), name, OverlayStyle.TEXT);
			if (c.world > 0)
			{
				String w = "W" + c.world;
				OverlayUtil.renderTextLocation(g, new net.runelite.api.Point(x + size.width - PAD - fm.stringWidth(w), ty),
					w, OverlayStyle.MUTED);
			}
			int by = top + PAD + fm.getHeight();
			if (!c.marker.isEmpty())
			{
				String m = OverlayStyle.fit(c.marker, fm, size.width - 2 * PAD);
				OverlayUtil.renderTextLocation(g, new net.runelite.api.Point(x + PAD, by + fm.getAscent()), m,
					c.marker.equals("Wilderness") ? OverlayStyle.DANGER : OverlayStyle.ACCENT);
				by += fm.getHeight();
			}
			if (bars.count() > 0)
			{
				int n = bars.count();
				int bw = (size.width - 2 * PAD - (n - 1) * BAR_GAP) / n;
				int bx = x + PAD;
				if (bars.hp)
				{
					bar(g, bx, by + 1, bw, fill(c.hp, c.hpMax), fill(c.hp, c.hpMax) < 0.3f ? HP_LOW : HP);
					bx += bw + BAR_GAP;
				}
				if (bars.prayer)
				{
					bar(g, bx, by + 1, bw, fill(c.prayer, c.prayerMax), PRAYER);
					bx += bw + BAR_GAP;
				}
				if (bars.spec)
				{
					bar(g, bx, by + 1, bw, fill(c.spec, 100), SPEC);
					bx += bw + BAR_GAP;
				}
				if (bars.run)
				{
					bar(g, bx, by + 1, bw, fill(c.run, 100), RUN);
				}
			}
			top += h + ROW_GAP;
		}
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
			aa == null ? RenderingHints.VALUE_ANTIALIAS_DEFAULT : aa);
		g.setFont(base);
		return size;
	}

	private static void bar(Graphics2D g, int x, int y, int w, float fill, Color colour)
	{
		g.setColor(TRACK);
		g.fillRect(x, y, w, BAR_H);
		g.setColor(colour);
		g.fillRect(x, y, Math.round(w * fill), BAR_H);
	}
}
