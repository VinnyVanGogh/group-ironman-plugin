package com.vinnyvangogh.groupironman;

import com.vinnyvangogh.testkit.ui.ConfigPanelHarness;
import com.vinnyvangogh.testkit.ui.RuneLiteLook;
import com.vinnyvangogh.testkit.ui.SwingDriver;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JPasswordField;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * GIM Utilities' page in RuneLite's own settings panel, as players open it
 * from the wrench: eight sections, only Group open, and the widgets each
 * section holds. Headless.
 */
public class GimConfigPanelUiTest
{
	static final List<String> SECTIONS = Arrays.asList("Group", "Discord: what to post",
		"Discord: how to post", "Discord: daily digest", "In-game", "Thresholds", "Advanced");

	@Before
	public void lookLikeRuneLite()
	{
		SwingDriver.onEdt(RuneLiteLook::install);
	}

	static ConfigPanelHarness settings()
	{
		return ConfigPanelHarness.of("GIM Utilities", GimPanelFixture.DEFAULTS, Collections.emptyMap());
	}

	@Test
	public void eightSectionsInOrderAndOnlyGroupOpen()
	{
		SwingDriver.onEdt(() ->
		{
			ConfigPanelHarness h = settings();
			SwingDriver ui = new SwingDriver(h.panel());
			List<String> texts = ui.texts();
			int last = -1;
			for (String s : SECTIONS)
			{
				int at = texts.indexOf(s);
				assertTrue(s + " missing from " + texts, at >= 0);
				assertTrue(s + " out of order", at > last);
				last = at;
				assertEquals(s, "Group".equals(s), h.isOpen(s));
			}
			ui.assertHides("Main webhook");
		});
	}

	/** Webhooks are Discord Relay's settings: GIM's page holds no secret field and no "Post to Discord". */
	@Test
	public void noWebhookFieldsAreLeftInGimsSettings()
	{
		SwingDriver.onEdt(() ->
		{
			ConfigPanelHarness h = settings();
			h.setOpen("Discord: what to post", true);
			h.setOpen("Discord: how to post", true);
			SwingDriver ui = new SwingDriver(h.panel());
			assertEquals(0, ui.all(JPasswordField.class).size());
			ui.assertHides("Main webhook");
			ui.assertShows("Don't post my events");
			JCheckBox drop = new SwingDriver(h.section("Discord: what to post")).find(JCheckBox.class, b -> true,
				"a per-event switch");
			assertFalse("every switch off on a fresh install", drop.isSelected());
		});
	}

	@Test
	public void thresholdsAndAdvancedOpenTogether()
	{
		SwingDriver.onEdt(() ->
		{
			ConfigPanelHarness h = settings();
			h.setOpen("Thresholds", true);
			h.setOpen("Advanced", true);
			assertTrue(h.isOpen("Thresholds"));
			assertTrue(h.isOpen("Advanced"));
			assertFalse(new SwingDriver(h.section("Thresholds")).texts().size() < 3);
			assertFalse(new SwingDriver(h.section("Advanced")).texts().size() < 2);
		});
	}
}
