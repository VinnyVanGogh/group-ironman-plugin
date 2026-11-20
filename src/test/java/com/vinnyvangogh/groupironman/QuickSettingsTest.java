package com.vinnyvangogh.groupironman;

import com.vinnyvangogh.testkit.ui.RuneLiteLook;
import com.vinnyvangogh.testkit.ui.SwingDriver;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import javax.swing.JCheckBox;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The sidebar's quick settings: each switch writes the real config key, and a config change redraws it. */
public class QuickSettingsTest
{
	/** The Swing tests below call this first; the two key tests need no look and feel. */
	private static void lookLikeRuneLite()
	{
		SwingDriver.onEdt(RuneLiteLook::install);
	}

	@Test
	public void everySwitchIsARealBooleanConfigKeyAndNoneRepeats()
	{
		Set<String> real = new HashSet<>();
		for (Method m : GroupIronmanConfig.class.getMethods())
		{
			ConfigItem item = m.getAnnotation(ConfigItem.class);
			if (item != null && m.getReturnType() == boolean.class)
			{
				real.add(item.keyName());
			}
		}
		Set<String> seen = new HashSet<>();
		for (QuickSettings.Item item : QuickSettings.ITEMS)
		{
			assertTrue(item.key + " is not a boolean setting", real.contains(item.key));
			assertTrue(item.key + " listed twice", seen.add(item.key));
		}
		int n = QuickSettings.ITEMS.size();
		assertTrue("6 to 10 switches, not " + n, n >= 6 && n <= 10);
	}

	@Test
	public void readFollowsTheConfigsDefaults()
	{
		boolean[] now = QuickSettings.read(GimPanelFixture.DEFAULTS);
		assertEquals(QuickSettings.ITEMS.size(), now.length);
		for (int i = 0; i < now.length; i++)
		{
			assertEquals(QuickSettings.ITEMS.get(i).key, QuickSettings.ITEMS.get(i).read.test(GimPanelFixture.DEFAULTS),
				now[i]);
		}
	}

	@Test
	public void clickingASwitchWritesItsKey()
	{
		lookLikeRuneLite();
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = new GimPanelFixture().show(GimPanelFixture.groupScene());
			f.ui.click(f.ui.button("Group box"));
			assertEquals("setQuick:overlayGroupBox=true", f.actions.calls.get(0));
			f.ui.click(f.ui.button("Group box"));
			assertEquals("setQuick:overlayGroupBox=false", f.actions.calls.get(1));
		});
	}

	@Test
	public void aConfigChangeUpdatesTheSwitches()
	{
		lookLikeRuneLite();
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = new GimPanelFixture();
			GimPanelFixture.Scene scene = GimPanelFixture.groupScene();
			f.show(scene);
			assertFalse(((JCheckBox) f.ui.button("Group box")).isSelected());
			// RuneLite's settings list changed the key: the plugin redraws with the new value.
			f.actions.setQuick("overlayGroupBox", true);
			f.show(scene);
			assertTrue(((JCheckBox) f.ui.button("Group box")).isSelected());
		});
	}

	@Test
	public void allSettingsOpensRuneLitesPageAndTheCardFolds()
	{
		lookLikeRuneLite();
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = new GimPanelFixture().show(GimPanelFixture.groupScene());
			f.ui.click(QuickSettings.ALL);
			assertEquals("openSettings", f.actions.calls.get(0));
			f.ui.click(f.ui.button("▾ " + QuickSettings.TITLE));
			f.ui.layout(GimPanelFixture.PANEL_WIDTH);
			f.ui.assertHides("Group box");
		});
	}
}
