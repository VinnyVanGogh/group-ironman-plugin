package com.vinnyvangogh.groupironman;

import com.vinnyvangogh.testkit.ui.RuneLiteLook;
import com.vinnyvangogh.testkit.ui.SwingDriver;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.event.MouseListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The Shopping tab in the real side panel, from {@link GimPanelFixture#shoppingScene}: the Board's "cox" list
 * and 1 Defence Ancient Magicks preset (2026-11-14). Lists start folded with a preview, opening one is saved for
 * the character, rows are one compact line with details behind a click, and no text runs past the panel. Headless.
 */
public class ShoppingTabUiTest
{
	private static final int WIDTH = GimPanelFixture.PANEL_WIDTH;

	@Before
	public void lookLikeRuneLite()
	{
		SwingDriver.onEdt(RuneLiteLook::install);
	}

	private static GimPanelFixture shopping(GimPanelFixture.Scene scene, String saved)
	{
		GimPanelFixture f = new GimPanelFixture();
		f.actions.openLists = saved;
		return f.show(scene).tab("Shopping");
	}

	private static void click(GimPanelFixture f, Component c)
	{
		f.ui.click(c);
		f.ui.layout(WIDTH);
	}

	/** The nearest component from {@code c} up that a click reaches: the one with a mouse listener. */
	private static Component clickTarget(Component c)
	{
		for (Component p = c; p != null; p = p.getParent())
		{
			for (MouseListener l : p.getMouseListeners())
			{
				if (!(l instanceof javax.swing.ToolTipManager))
				{
					return p;
				}
			}
		}
		throw new AssertionError("nothing clickable around " + c);
	}

	@Test
	public void listsAndRequestsStartFoldedWithAPreview()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = shopping(GimPanelFixture.shoppingScene(), null);
			f.ui.assertShows("cox");
			f.ui.assertShows("Ancient Magicks");
			f.ui.assertShows("1 of 5 done");
			f.ui.assertShows("+1 more");
			f.ui.assertHides("Need 2 more");
			f.ui.assertHides("For " + GimPanelFixture.SELF);
			f.ui.assertHides("Asked by");
			assertEquals(new ArrayList<String>(), clipped(f));
		});
	}

	@Test
	public void openingAListShowsStillNeededFirstAndIsRememberedForTheCharacter()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture.Scene scene = GimPanelFixture.shoppingScene();
			GimPanelFixture f = shopping(scene, null);
			click(f, f.ui.label("cox"));
			f.ui.assertShows("Dragon warhammer 0 / 2");
			f.ui.assertShows("Need 2 more");
			f.ui.assertShows("Shark 12 / 40");
			f.ui.assertShows("Need 28 more");
			f.ui.assertShows("1 done");
			f.ui.assertHides("Coal 10 / 10");
			assertNotNull(f.actions.openLists);
			assertTrue(f.actions.openLists, f.actions.openLists.startsWith("l:"));

			GimPanelFixture again = shopping(scene, f.actions.openLists);
			again.ui.assertShows("Need 2 more");
			again.ui.assertHides("Asked by");
		});
	}

	@Test
	public void theDoneSectionAndAnItemsDetailsOpenOnClick()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = shopping(GimPanelFixture.shoppingScene(), null);
			click(f, f.ui.label("cox"));
			click(f, f.ui.label("1 done"));
			f.ui.assertShows("Coal 10 / 10");
			f.ui.assertHides("Lizardman shaman");
			click(f, clickTarget(f.ui.label("Dragon warhammer 0 / 2")));
			f.ui.assertShows("Get: Drop: Lizardman shaman (1/3,000)");
			f.ui.assertHides("Held by " + GimPanelFixture.SELF + " 12");
			click(f, clickTarget(f.ui.label("Shark 12 / 40")));
			f.ui.assertShows("Held by " + GimPanelFixture.SELF + " 12");
			assertEquals(new ArrayList<String>(), clipped(f));
		});
	}

	@Test
	public void rendersBeforeAndAfterOpeningWithNoLabelCut()
	{
		SwingDriver.onEdt(() ->
		{
			GimPanelFixture f = shopping(GimPanelFixture.shoppingScene(), null);
			SwingDriver.writePng(f.ui.render(WIDTH, 0, ReadmeShots.SCALE),
				new File(ReadmeShots.outputDir(), "shopping-folded.png"));
			assertEquals(new ArrayList<String>(), clipped(f));
			click(f, f.ui.label("cox"));
			click(f, f.ui.label("Ancient Magicks"));
			click(f, clickTarget(f.ui.label("Dragon warhammer 0 / 2")));
			SwingDriver.writePng(f.ui.render(WIDTH, 0, ReadmeShots.SCALE),
				new File(ReadmeShots.outputDir(), "shopping-open.png"));
			assertEquals(new ArrayList<String>(), clipped(f));
		});
	}

	/**
	 * Labels on the Shopping tab whose text is cut: given less width than they ask for, or running past the
	 * panel's right edge. The tab is everything under its first heading.
	 */
	static List<String> clipped(GimPanelFixture f)
	{
		Container root = f.ui.root();
		Container tab = f.ui.label("Requests").getParent();
		List<String> out = new ArrayList<>();
		for (JLabel l : new SwingDriver(tab).all(JLabel.class))
		{
			if (l.getText() == null || l.getText().isEmpty())
			{
				continue;
			}
			Point at = SwingUtilities.convertPoint(l.getParent(), l.getLocation(), root);
			boolean narrow = l.getWidth() + 1 < l.getPreferredSize().width;
			boolean past = at.x + l.getWidth() > root.getWidth();
			if (narrow || past)
			{
				out.add(SwingDriver.plain(l.getText()) + " (" + l.getWidth() + " of " + l.getPreferredSize().width
					+ " at x " + at.x + ")");
			}
		}
		return out;
	}
}
