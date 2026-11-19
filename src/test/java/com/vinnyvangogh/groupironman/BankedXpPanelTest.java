package com.vinnyvangogh.groupironman;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JLabel;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The Banks tab's banked-XP section: hidden with nothing shared, resource-using skills by default. */
public class BankedXpPanelTest
{
	private static CompareTab.View view()
	{
		Map<String, Map<String, Double>> banked = new HashMap<>();
		Map<String, Double> xp = new HashMap<>();
		xp.put("Cooking", 5000.0);
		xp.put("Mining", 900.0);
		banked.put("GIM One", xp);
		return new CompareTab.View("GIM One", Collections.emptyList(), Collections.emptyList(), banked,
			Collections.emptyMap());
	}

	@Test
	public void hidesWithNothingShared()
	{
		BankedXpPanel p = new BankedXpPanel(s -> null, () -> { });
		p.update(null);
		assertFalse(p.root.isVisible());
	}

	@Test
	public void defaultsToUsingSkillsAndQuickPicksSwitch()
	{
		BankedXpPanel p = new BankedXpPanel(s -> null, () -> { });
		p.update(view());
		assertTrue(p.root.isVisible());
		assertEquals(new java.util.HashSet<>(BankedXpPanel.USING), p.picked());
		assertTrue(text(p).contains("Cooking"));
		assertFalse(text(p).contains("Mining"));
	}

	private static String text(BankedXpPanel p)
	{
		StringBuilder sb = new StringBuilder();
		walk(p.root, sb);
		return sb.toString();
	}

	private static void walk(java.awt.Container c, StringBuilder sb)
	{
		for (java.awt.Component k : c.getComponents())
		{
			if (k instanceof JLabel)
			{
				sb.append(((JLabel) k).getText()).append('\n');
			}
			if (k instanceof java.awt.Container)
			{
				walk((java.awt.Container) k, sb);
			}
		}
	}
}
