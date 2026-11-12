package com.vinnyvangogh.groupironman;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Units;
import net.runelite.client.ui.FontManager;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Every setting's name fits beside its control in RuneLite's settings panel, and every gp number box shows its whole
 * value (Board 2026-11-11 screenshots: "P..." beside the poster-name dropdown, "Valuable drop at least...",
 * "::find colour: your sea...", and 1,000,000 shown as "1,000,00").
 *
 * <p>How the panel lays a row out (RuneLite ConfigPanel): the name is a JLabel in the row's centre and the control
 * sits at its preferred width on the right, so a long name is cut with "...", never the control. A number box is
 * always 6 columns wide, whatever the name. A dropdown is as wide as its longest choice. Text settings go on their
 * own line below the name, so they always fit.
 *
 * <p>The widths are measured in RuneLite's own font, and each budget is a name the Board's screenshots show fitting
 * beside that kind of control: no guessed pixel counts. The calibration test then checks the names the screenshots
 * show cut off really do measure over budget, so the model reproduces what the Board saw.
 */
public class ConfigFitTest
{
	/** Fitted beside a number box (Discord: how to post). Colour buttons are the same width (Advanced). */
	private static final String FITS_BESIDE_NUMBER = "Offline grace (minutes)";
	/** Fitted beside a tick box (Discord: daily digest). */
	private static final String FITS_BESIDE_TICK = "Include in daily digest: Activities";
	/** The visible part of 1,000,000 in a number box (Thresholds): the box holds this much text. */
	private static final String FITS_IN_NUMBER_BOX = "1,000,00";
	/** Name plus longest choice pairs seen fitting beside a dropdown (how to post, In-game). */
	private static final List<String[]> FITS_BESIDE_DROPDOWN = Arrays.asList(
		new String[]{"Online status", "Live board"},
		new String[]{"Message style", "Rich embed"},
		new String[]{"Banner position", "Above chatbox"});

	private static FontMetrics fm;

	@BeforeClass
	public static void font()
	{
		Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		fm = g.getFontMetrics(FontManager.getRunescapeFont());
		g.dispose();
	}

	@Test
	public void theModelReproducesWhatTheScreenshotsShowCutOff()
	{
		for (String cut : Arrays.asList("Valuable drop at least (gp)", "Clue reward at least (gp)",
			"Combine level-ups within (minutes)", "Kill count milestone every", "Gravestone warning (minutes left)"))
		{
			assertTrue(cut + " was cut off beside a number box", w(cut) > w(FITS_BESIDE_NUMBER));
		}
		assertTrue(w("Share rune pouch, quiver, seed vault") > w(FITS_BESIDE_TICK));
		assertTrue(w("Gravestone screenshot") + w("From the death") > dropdownBudget());
		assertTrue(w("Poster name") + w("Use the webhook's own name") > dropdownBudget());
		assertTrue("1,000,000 was cut to 1,000,00", w("1,000,000") > w(FITS_IN_NUMBER_BOX));
	}

	@Test
	public void everyNameFitsBesideItsControl()
	{
		List<String> tooLong = new ArrayList<>();
		for (Method m : items())
		{
			ConfigItem item = m.getAnnotation(ConfigItem.class);
			Class<?> type = m.getReturnType();
			int width = w(item.name());
			boolean fits;
			if (type == String.class || item.hidden())
			{
				continue;
			}
			else if (type == boolean.class)
			{
				fits = width <= w(FITS_BESIDE_TICK);
			}
			else if (type.isEnum())
			{
				fits = width + longestChoice(type) <= dropdownBudget();
			}
			else
			{
				fits = width <= w(FITS_BESIDE_NUMBER);
			}
			if (!fits)
			{
				tooLong.add(item.name());
			}
		}
		assertTrue("cut off in the settings panel: " + tooLong, tooLong.isEmpty());
	}

	/**
	 * A {@code @Units} number box prints {@code value + units} with no thousands commas (RuneLite UnitFormatter),
	 * so every gp box carries the empty unit, and its default, and a million, fit the box.
	 */
	@Test
	public void everyGpNumberBoxShowsItsWholeValue() throws Exception
	{
		GroupIronmanConfig defaults = new GroupIronmanConfig()
		{
		};
		int gpBoxes = 0;
		for (Method m : items())
		{
			ConfigItem item = m.getAnnotation(ConfigItem.class);
			if (m.getReturnType() != int.class || !item.name().contains("(gp)"))
			{
				continue;
			}
			gpBoxes++;
			Units units = m.getAnnotation(Units.class);
			assertNotNull(item.name() + " needs @Units(GP_PLAIN) or it shows commas", units);
			assertEquals(item.name(), GroupIronmanConfig.GP_PLAIN, units.value());
			String shown = m.invoke(defaults) + units.value();
			assertTrue(item.name() + " default shows as " + shown, w(shown) <= w(FITS_IN_NUMBER_BOX));
		}
		assertEquals("Legend, Freeloader, Valuable drop, Big storage, Clue reward, Shared drops", 6, gpBoxes);
		assertTrue(w("1000000" + GroupIronmanConfig.GP_PLAIN) <= w(FITS_IN_NUMBER_BOX));
	}

	private static int dropdownBudget()
	{
		int best = 0;
		for (String[] pair : FITS_BESIDE_DROPDOWN)
		{
			best = Math.max(best, w(pair[0]) + w(pair[1]));
		}
		return best;
	}

	private static int longestChoice(Class<?> enumType)
	{
		int best = 0;
		for (Object choice : enumType.getEnumConstants())
		{
			best = Math.max(best, w(choice.toString()));
		}
		return best;
	}

	private static int w(String s)
	{
		return fm.stringWidth(s);
	}

	private static List<Method> items()
	{
		return ConfigLayoutTest.items();
	}
}
