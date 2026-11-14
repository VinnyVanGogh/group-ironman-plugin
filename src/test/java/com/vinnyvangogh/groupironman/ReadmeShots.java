package com.vinnyvangogh.groupironman;

import com.vinnyvangogh.testkit.ui.ConfigPanelHarness;
import com.vinnyvangogh.testkit.ui.RuneLiteLook;
import com.vinnyvangogh.testkit.ui.SwingDriver;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JToggleButton;
import net.runelite.client.ui.ColorScheme;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Renders the README's screenshots from the shot list, each at a fixed size,
 * from made-up scenes (no real account names). Headless.
 *
 * <p>{@code ./gradlew screenshots} writes them into docs/images. In
 * {@code ./gradlew test} the same code writes to build/screenshots, so a
 * change that breaks a shot fails the build rather than the README.
 *
 * <p>Item icons are placeholders with the item's initials: the game cache is
 * not available outside the client. The shots chosen for the README are the
 * ones that read without real icons.
 */
public class ReadmeShots
{
	/** Shots are drawn at the sidebar's width and saved at twice the size, so they stay sharp on a retina screen. */
	static final double SCALE = 2.0;
	static final int WIDTH = GimPanelFixture.PANEL_WIDTH;
	static final Color BACKGROUND = ColorScheme.DARK_GRAY_COLOR;

	/** Name, then the shot's height in panel pixels: every shot of one name is always the same size. */
	static final Map<String, Integer> SHOTS = new LinkedHashMap<>();

	static
	{
		SHOTS.put("settings-tab-leader", 760);
		SHOTS.put("settings-tab-member", 640);
		SHOTS.put("settings-who-posts", 760);
		SHOTS.put("panel-group-hiscores", 760);
		SHOTS.put("storage-log", 520);
		SHOTS.put("settings-discord-connection", 640);
		SHOTS.put("settings-thresholds-advanced", 760);
	}

	@Before
	public void lookLikeRuneLite()
	{
		SwingDriver.onEdt(RuneLiteLook::install);
	}

	static File outputDir()
	{
		String dir = System.getProperty("screenshots.dir");
		return dir != null && !dir.isEmpty() ? new File(dir) : new File("build/screenshots");
	}

	@Test
	public void rendersEveryShotAtItsFixedSize() throws Exception
	{
		Map<String, Supplier<BufferedImage>> renders = new LinkedHashMap<>();
		renders.put("settings-tab-leader", ReadmeShots::settingsLeader);
		renders.put("settings-tab-member", ReadmeShots::settingsMember);
		renders.put("settings-who-posts", ReadmeShots::whoPosts);
		renders.put("panel-group-hiscores", ReadmeShots::groupHiscores);
		renders.put("storage-log", ReadmeShots::storageLog);
		renders.put("settings-discord-connection", ReadmeShots::discordConnection);
		renders.put("settings-thresholds-advanced", ReadmeShots::thresholdsAdvanced);
		assertEquals("every shot in the list has a render", SHOTS.keySet(), renders.keySet());

		File dir = outputDir();
		for (Map.Entry<String, Supplier<BufferedImage>> e : renders.entrySet())
		{
			int w = (int) Math.round(WIDTH * SCALE);
			int h = (int) Math.round(SHOTS.get(e.getKey()) * SCALE);
			BufferedImage shot = SwingDriver.onEdt(() -> SwingDriver.fit(e.getValue().get(), w, h, BACKGROUND));
			File file = new File(dir, e.getKey() + ".png");
			SwingDriver.writePng(shot, file);
			BufferedImage back = ImageIO.read(file);
			assertEquals(e.getKey(), w, back.getWidth());
			assertEquals(e.getKey(), h, back.getHeight());
			assertTrue(e.getKey() + " is not blank", notBlank(back));
		}
	}

	private static boolean notBlank(BufferedImage img)
	{
		int first = img.getRGB(0, 0);
		for (int y = 0; y < img.getHeight(); y += 4)
		{
			for (int x = 0; x < img.getWidth(); x += 4)
			{
				if (img.getRGB(x, y) != first)
				{
					return true;
				}
			}
		}
		return false;
	}

	// ---- The shots ----

	private static BufferedImage panelShot(GimPanelFixture f)
	{
		return f.ui.render(WIDTH, 0, SCALE);
	}

	private static GimPanelFixture settingsTab(String self)
	{
		GimPanelFixture.Scene s = GimPanelFixture.groupScene();
		s.self = self;
		s.settings = new GimPanelFixture.Governance().led(GimPanelFixture.SELF, GimPanelFixture.MOSS, s.now)
			.view(self, true);
		return new GimPanelFixture().show(s).tab("Settings");
	}

	static BufferedImage settingsLeader()
	{
		return panelShot(settingsTab(GimPanelFixture.SELF));
	}

	static BufferedImage settingsMember()
	{
		return panelShot(settingsTab(GimPanelFixture.WREN));
	}

	/** The leader's grid of who posts what, from its header down: it sits below everything else in the tab. */
	static BufferedImage whoPosts()
	{
		GimPanelFixture f = settingsTab(GimPanelFixture.SELF);
		f.ui.click("- " + GroupSettingsSchema.THRESHOLDS);
		f.ui.click("+ Who posts what");
		BufferedImage full = panelShot(f);
		int top = f.ui.yOf(f.ui.button("- Who posts what")) - 4;
		return SwingDriver.from(full, (int) Math.round(top * SCALE));
	}

	static BufferedImage groupHiscores()
	{
		return panelShot(new GimPanelFixture().show(GimPanelFixture.groupScene()));
	}

	/** The Log with the Storage chip ticked. */
	static BufferedImage storageLog()
	{
		GimPanelFixture f = new GimPanelFixture().show(GimPanelFixture.logScene()).tab("Log");
		AbstractButton chip = f.ui.all(JToggleButton.class).stream()
			.filter(b -> "Storage".equals(b.getText()) && b.getToolTipText() != null)
			.findFirst().orElseThrow(() -> new AssertionError("no Storage chip"));
		f.ui.click(chip);
		return panelShot(f);
	}

	static BufferedImage discordConnection()
	{
		ConfigPanelHarness h = ConfigPanelHarness.of("GIM Utilities", GimPanelFixture.DEFAULTS, discordOn());
		h.setOpen("Discord: connection", true);
		return SwingDriver.stack(WIDTH, CONFIG_PAD, SCALE, BACKGROUND, h.section("Discord: connection"));
	}

	static BufferedImage thresholdsAdvanced()
	{
		ConfigPanelHarness h = ConfigPanelHarness.of("GIM Utilities", GimPanelFixture.DEFAULTS,
			Collections.emptyMap());
		h.setOpen("Thresholds", true);
		h.setOpen("Advanced", true);
		return SwingDriver.stack(WIDTH, CONFIG_PAD, SCALE, BACKGROUND, h.section("Thresholds"),
			h.section("Advanced"));
	}

	/** The margin RuneLite's settings page puts around its sections. */
	static final int CONFIG_PAD = 6;

	/** Post to Discord ticked and made-up webhooks, so the fields show as filled secrets. */
	private static Map<String, Object> discordOn()
	{
		Map<String, Object> v = new LinkedHashMap<>();
		v.put("discordEnabled", true);
		String hook = "https://discord.com/api/webhooks/0/made-up";
		for (String key : new String[]{"discordWebhookUrl", "discordGroupUrl", "discordDropsUrl", "discordProgressUrl",
			"discordDeathsUrl", "discordStorageUrl", "discordDigestUrl"})
		{
			v.put(key, hook);
		}
		return v;
	}
}
