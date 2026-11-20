package com.vinnyvangogh.groupironman;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VitalsHudTest
{
	private static MemberStats stats(int hp, int hpMax, int prayer, int prayerMax, int run, int spec, int world,
		int x, int y)
	{
		Map<String, MemberStats.Level> skills = new LinkedHashMap<>();
		skills.put("Hitpoints", new MemberStats.Level(hpMax, hp));
		skills.put("Prayer", new MemberStats.Level(prayerMax, prayer));
		return new MemberStats(skills, run, spec, world, 0, x, y, 0);
	}

	private static Map<String, MemberStats> group()
	{
		Map<String, MemberStats> m = new LinkedHashMap<>();
		m.put("GIM1", stats(70, 99, 43, 70, 80, 100, 301, 3200, 3200));
		m.put("GIM2", stats(12, 80, 0, 52, 15, 50, 301, 3100, 3700));
		m.put("Me", stats(99, 99, 70, 70, 100, 100, 301, 3200, 3200));
		return m;
	}

	private static Map<String, Integer> names()
	{
		Map<String, Integer> n = new LinkedHashMap<>();
		n.put("GIM1", 301);
		n.put("GIM2", 301);
		return n;
	}

	@Test
	public void yourselfIsHiddenUnlessAsked()
	{
		List<VitalsHud.Card> cards = VitalsHud.cards(group(), names(), Map.of(), Set.of(), "Me", false);
		assertEquals(2, cards.size());
		assertEquals("GIM1", cards.get(0).name);
		List<VitalsHud.Card> withSelf = VitalsHud.cards(group(), names(), Map.of(), Set.of(), "Me", true);
		assertEquals(3, withSelf.size());
	}

	@Test
	public void aMemberListNarrowsTheCards()
	{
		Set<String> only = VitalsHud.parseMembers(" gim2 ,\n");
		List<VitalsHud.Card> cards = VitalsHud.cards(group(), names(), Map.of(), only, "Me", false);
		assertEquals(1, cards.size());
		assertEquals("GIM2", cards.get(0).name);
	}

	@Test
	public void onlineMembersWhoShareNothingGetNoCard()
	{
		Map<String, Integer> n = names();
		n.put("GIM3", 301);
		assertEquals(2, VitalsHud.cards(group(), n, Map.of(), Set.of(), "Me", false).size());
	}

	@Test
	public void aMemberWhoLeftGetsNoCard()
	{
		Map<String, Integer> n = names();
		n.remove("GIM2");
		List<VitalsHud.Card> cards = VitalsHud.cards(group(), n, Map.of(), Set.of(), "Me", false);
		assertEquals(1, cards.size());
	}

	@Test
	public void valuesComeFromBoostedAndRealLevels()
	{
		VitalsHud.Card c = VitalsHud.cards(group(), names(), Map.of(), Set.of(), "Me", false).get(0);
		assertEquals(70, c.hp);
		assertEquals(99, c.hpMax);
		assertEquals(43, c.prayer);
		assertEquals(70, c.prayerMax);
		assertEquals(80, c.run);
		assertEquals(100, c.spec);
	}

	@Test
	public void wildernessAndActivityMarkers()
	{
		List<VitalsHud.Card> cards = VitalsHud.cards(group(), names(), Map.of(), Set.of(), "Me", false);
		assertEquals("", cards.get(0).marker);
		assertEquals("Wilderness", cards.get(1).marker);
		Map<String, ActivityState> acts = Map.of("GIM1", ActivityCodecTest.inferno(1));
		VitalsHud.Card inferno = VitalsHud.cards(group(), names(), acts, Set.of(), "Me", false).get(0);
		assertEquals(acts.get("GIM1").overlayLine(), inferno.marker);
		assertFalse(inferno.marker.isEmpty());
	}

	@Test
	public void wildernessBounds()
	{
		assertTrue(VitalsHud.wilderness(3000, 3600));
		assertFalse(VitalsHud.wilderness(3200, 3200));
		assertFalse(VitalsHud.wilderness(3200, 3522));
	}

	@Test
	public void fillIsClamped()
	{
		assertEquals(0f, VitalsHud.fill(5, 0), 0f);
		assertEquals(1f, VitalsHud.fill(120, 99), 0f);
		assertEquals(0f, VitalsHud.fill(-3, 99), 0f);
		assertEquals(0.5f, VitalsHud.fill(50, 100), 0f);
	}

	@Test
	public void combatOnlyHoldsForAFewSeconds()
	{
		assertTrue(VitalsOverlay.visible(false, false, 100_000, 0));
		assertFalse(VitalsOverlay.visible(true, false, 100_000, 0));
		assertTrue(VitalsOverlay.visible(true, true, 100_000, 0));
		assertTrue(VitalsOverlay.visible(true, false, 100_000, 100_000 - VitalsOverlay.COMBAT_HOLD_MS));
		assertFalse(VitalsOverlay.visible(true, false, 100_000, 100_000 - VitalsOverlay.COMBAT_HOLD_MS - 1));
	}

	@Test
	public void barsAreCountedAndTheCardStaysCompact()
	{
		assertEquals(0, new VitalsHud.Bars(false, false, false, false).count());
		assertEquals(4, new VitalsHud.Bars(true, true, true, true).count());
		BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		List<VitalsHud.Card> cards = VitalsHud.cards(group(), names(), Map.of(), Set.of(), "Me", false);
		Dimension d = VitalsHud.measure(g, cards, new VitalsHud.Bars(true, true, true, true), 1f);
		g.dispose();
		assertTrue("narrow: " + d, d.width <= 130);
	}

	/** Headless shot with placeholder names, into build/vitals-hud-shots/. */
	@Test
	public void rendersAShot() throws IOException
	{
		Map<String, ActivityState> acts = Map.of("GIM1", ActivityCodecTest.inferno(1));
		List<VitalsHud.Card> cards = VitalsHud.cards(group(), names(), acts, Set.of(), "Me", false);
		BufferedImage img = new BufferedImage(260, 170, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setColor(new java.awt.Color(60, 90, 50));
		g.fillRect(0, 0, 260, 170);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		Dimension d = VitalsHud.draw(g, 10, 10, cards, new VitalsHud.Bars(true, true, true, true), 1f);
		g.dispose();
		assertTrue(d.height > 0 && d.width < 260 && d.height < 170);
		File dir = new File("build/vitals-hud-shots");
		assertTrue(dir.isDirectory() || dir.mkdirs());
		assertTrue(ImageIO.write(img, "png", new File(dir, "vitals-hud.png")));
	}
}
