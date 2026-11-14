package com.vinnyvangogh.testkit.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** The driver itself, on a tiny panel: no display needed. */
public class SwingDriverTest
{
	/** Two cards, a button that switches them, a clickable label, a field and a picker. */
	private static final class Fixture
	{
		final JPanel root = new JPanel(new BorderLayout());
		final CardLayout cards = new CardLayout();
		final JPanel views = new JPanel(cards);
		final AtomicInteger labelClicks = new AtomicInteger();
		final List<String> typed = new ArrayList<>();
		final JTextField field = new JTextField();
		final JComboBox<String> picker = new JComboBox<>(new String[]{"Any", "Alex", "Sam"});
		final JButton disabled = new JButton("Disabled");

		Fixture()
		{
			JPanel top = new JPanel();
			top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
			JButton next = new JButton("Next");
			next.addActionListener(e -> cards.next(views));
			top.add(next);
			JLabel fold = new JLabel("<html><b>Fold</b> &amp; unfold</html>");
			fold.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					labelClicks.incrementAndGet();
				}
			});
			top.add(fold);
			field.setToolTipText("Filter by item name");
			field.getDocument().addDocumentListener(new DocumentListener()
			{
				@Override
				public void insertUpdate(DocumentEvent e)
				{
					typed.add(field.getText());
				}

				@Override
				public void removeUpdate(DocumentEvent e)
				{
				}

				@Override
				public void changedUpdate(DocumentEvent e)
				{
				}
			});
			top.add(field);
			top.add(picker);
			disabled.setEnabled(false);
			top.add(disabled);
			root.add(top, BorderLayout.NORTH);
			views.add(new JLabel("First card"), "one");
			views.add(new JLabel("Second card"), "two");
			root.add(views, BorderLayout.CENTER);
			root.setBackground(Color.BLACK);
		}
	}

	@Test
	public void hiddenCardsAreNotOnScreenAndAButtonClickSwitchesThem()
	{
		SwingDriver.onEdt(() ->
		{
			Fixture f = new Fixture();
			SwingDriver d = new SwingDriver(f.root);
			d.assertShows("First card");
			d.assertHides("Second card");
			d.click("Next");
			d.assertShows("Second card");
			d.assertHides("First card");
		});
	}

	@Test
	public void labelsAreClickedThroughTheirOwnListenersAndReadWithoutHtml()
	{
		SwingDriver.onEdt(() ->
		{
			Fixture f = new Fixture();
			SwingDriver d = new SwingDriver(f.root);
			assertTrue(d.texts().contains("Fold & unfold"));
			d.click("Fold & unfold");
			assertEquals(1, f.labelClicks.get());
		});
	}

	@Test
	public void typingFiresDocumentListenersAndPickingSelects()
	{
		SwingDriver.onEdt(() ->
		{
			Fixture f = new Fixture();
			SwingDriver d = new SwingDriver(f.root);
			d.type(d.field("item name"), "guam");
			assertEquals("guam", f.typed.get(f.typed.size() - 1));
			d.select(f.picker, "Sam");
			assertEquals("Sam", f.picker.getSelectedItem());
		});
	}

	@Test
	public void aMissingComponentFailsWithWhatIsOnScreen()
	{
		SwingDriver.onEdt(() ->
		{
			SwingDriver d = new SwingDriver(new Fixture().root);
			try
			{
				d.button("Nope");
				fail("found a button that is not there");
			}
			catch (AssertionError e)
			{
				assertTrue(e.getMessage(), e.getMessage().contains("First card"));
			}
		});
	}

	@Test
	public void aDisabledButtonRefusesTheClick()
	{
		SwingDriver.onEdt(() ->
		{
			SwingDriver d = new SwingDriver(new Fixture().root);
			try
			{
				d.click("Disabled");
				fail("clicked a disabled button");
			}
			catch (AssertionError e)
			{
				assertTrue(e.getMessage().contains("disabled"));
			}
		});
	}

	@Test
	public void aPickerRefusesAnItemItDoesNotHave()
	{
		SwingDriver.onEdt(() ->
		{
			Fixture f = new Fixture();
			try
			{
				new SwingDriver(f.root).select(f.picker, "Zed");
				fail("selected a missing item");
			}
			catch (AssertionError e)
			{
				assertTrue(e.getMessage().contains("Zed"));
			}
		});
	}

	@Test
	public void rendersAtAFixedScaledSizeAndWritesAPng() throws Exception
	{
		File dir = Files.createTempDirectory("swingdriver").toFile();
		File png = new File(dir, "shot.png");
		SwingDriver.onEdt(() ->
		{
			SwingDriver d = new SwingDriver(new Fixture().root);
			BufferedImage img = d.render(200, 150, 2.0);
			assertEquals(400, img.getWidth());
			assertEquals(300, img.getHeight());
			SwingDriver.writePng(SwingDriver.fit(img, 400, 500, Color.BLACK), png);
		});
		BufferedImage back = ImageIO.read(png);
		assertEquals(400, back.getWidth());
		assertEquals(500, back.getHeight());
	}

	@Test
	public void stackPadsAndCropStartsLower()
	{
		SwingDriver.onEdt(() ->
		{
			JPanel a = new Fixture().root;
			JPanel b = new Fixture().root;
			int ha = new SwingDriver(a).layout(180);
			int hb = new SwingDriver(b).layout(180);
			BufferedImage both = SwingDriver.stack(200, 10, 2.0, Color.RED, a, b);
			assertEquals(400, both.getWidth());
			assertEquals((ha + hb) * 2 + 40, both.getHeight());
			assertEquals("the margin is the background", Color.RED.getRGB(), both.getRGB(5, 5));
			assertEquals(both.getHeight() - 30, SwingDriver.from(both, 30).getHeight());
			JLabel card = new SwingDriver(a).label("First card");
			assertTrue("below the buttons", new SwingDriver(a).yOf(card) > 0);
		});
	}

	@Test
	public void layoutGivesThePreferredHeight()
	{
		SwingDriver.onEdt(() ->
		{
			SwingDriver d = new SwingDriver(new Fixture().root);
			int h = d.layout(220);
			assertTrue("height " + h, h > 20);
			assertEquals(220, d.root().getWidth());
		});
	}

	@Test
	public void runsOnTheSwingThreadFromATestThread()
	{
		assertFalse(SwingUtilities.isEventDispatchThread());
		assertTrue(SwingDriver.onEdt(SwingUtilities::isEventDispatchThread));
	}

	@Test
	public void plainStripsTagsAndBreaks()
	{
		assertEquals("Alex took 3 x Shark (2h ago)",
			SwingDriver.plain("<html><body style='width:140px'><b>Alex</b> took 3 x Shark <font color='#9a9a9a'>(2h"
				+ " ago)</font></body></html>"));
		assertEquals("a b", SwingDriver.plain("a<br>b"));
		assertEquals("", SwingDriver.plain(null));
	}
}
