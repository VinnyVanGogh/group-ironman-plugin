package com.vinnyvangogh.testkit.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * Drives a plugin's own Swing panel from a test: find a component by its text,
 * click it, type into it, read what is on screen, and render it to a PNG.
 *
 * <p>Nothing here moves the mouse or sends a key to the operating system. A
 * click is the button's own {@link AbstractButton#doClick(int)} or a
 * {@link MouseEvent} handed straight to the component's listeners, so it never
 * leaves the component tree, never reaches the game, and needs no display: it
 * runs with {@code java.awt.headless=true}, on CI as on the Mac. (Why not
 * {@code java.awt.Robot} or AssertJ Swing: both drive the real OS pointer, need
 * a display, and take over the developer's mouse while they run.)
 *
 * <p>The tree is never put in a window. {@link #layout} sizes and lays it out
 * by hand, and "visible" means the component and every parent up to the root
 * are {@link Component#isVisible() visible}: a CardLayout's hidden cards and a
 * collapsed section's body do not count.
 *
 * <p>Copy-in template: the same file in every plugin, no plugin types. Call
 * everything on the Swing thread ({@link #onEdt}).
 */
public final class SwingDriver
{
	private final Container root;

	public SwingDriver(Container root)
	{
		this.root = root;
	}

	public Container root()
	{
		return root;
	}

	// ---- Threading ----

	/** Runs {@code r} on the Swing thread and waits, rethrowing what it threw. */
	public static void onEdt(Runnable r)
	{
		onEdt(() ->
		{
			r.run();
			return null;
		});
	}

	public static <T> T onEdt(Callable<T> c)
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			return call(c);
		}
		List<T> out = new ArrayList<>();
		try
		{
			SwingUtilities.invokeAndWait(() -> out.add(call(c)));
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
		catch (InvocationTargetException e)
		{
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException)
			{
				throw (RuntimeException) cause;
			}
			if (cause instanceof Error)
			{
				throw (Error) cause;
			}
			throw new IllegalStateException(cause);
		}
		return out.get(0);
	}

	private static <T> T call(Callable<T> c)
	{
		try
		{
			return c.call();
		}
		catch (RuntimeException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new IllegalStateException(e);
		}
	}

	// ---- Layout ----

	/** Lays the tree out {@code width} wide and as tall as it wants; returns that height. */
	public int layout(int width)
	{
		// Until the height settles: a wrapping text area only knows its height once it
		// has been given a width and a height, so each pass can grow the one above.
		int height = 10_000;
		for (int pass = 0; pass < 6; pass++)
		{
			layout(width, height);
			int wanted = Math.max(1, root.getPreferredSize().height);
			if (wanted == height)
			{
				break;
			}
			height = wanted;
		}
		layout(width, height);
		return height;
	}

	/** Lays the tree out at exactly {@code width} x {@code height}. */
	public void layout(int width, int height)
	{
		root.setSize(width, height);
		layoutTree(root);
		layoutTree(root);
	}

	/** Drops every cached size: BoxLayout keeps its children's sizes until invalidated, and a window would. */
	private static void invalidateTree(Component c)
	{
		c.invalidate();
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				invalidateTree(child);
			}
		}
	}

	/** Where {@code c} sits from the root's top edge, in layout pixels. */
	public int yOf(Component c)
	{
		return SwingUtilities.convertPoint(c.getParent(), c.getLocation(), root).y;
	}

	private static void layoutTree(Component root)
	{
		invalidateTree(root);
		doLayoutTree(root);
	}

	private static void doLayoutTree(Component c)
	{
		if (c instanceof Container)
		{
			Container k = (Container) c;
			k.doLayout();
			for (Component child : k.getComponents())
			{
				doLayoutTree(child);
			}
		}
	}

	// ---- Reading ----

	/** Visible components of {@code type}, in tree order. */
	public <T extends Component> List<T> all(Class<T> type)
	{
		List<T> out = new ArrayList<>();
		collect(root, type, out);
		return out;
	}

	private static <T extends Component> void collect(Component c, Class<T> type, List<T> out)
	{
		if (!c.isVisible())
		{
			return;
		}
		if (type.isInstance(c))
		{
			out.add(type.cast(c));
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				collect(child, type, out);
			}
		}
	}

	/** The first visible {@code type} matching {@code test}; fails naming what was on screen. */
	public <T extends Component> T find(Class<T> type, Predicate<T> test, String what)
	{
		for (T c : all(type))
		{
			if (test.test(c))
			{
				return c;
			}
		}
		throw new AssertionError("No " + what + " on screen. Showing: " + texts());
	}

	/** Every visible label, button and text field's text, html stripped, in tree order. */
	public List<String> texts()
	{
		List<String> out = new ArrayList<>();
		for (Component c : all(Component.class))
		{
			String t = textOf(c);
			if (t != null && !t.isEmpty())
			{
				out.add(t);
			}
		}
		return out;
	}

	/** Whether some visible text contains {@code fragment}. */
	public boolean shows(String fragment)
	{
		for (String t : texts())
		{
			if (t.contains(fragment))
			{
				return true;
			}
		}
		return false;
	}

	public void assertShows(String fragment)
	{
		if (!shows(fragment))
		{
			throw new AssertionError("Expected '" + fragment + "' on screen. Showing: " + texts());
		}
	}

	public void assertHides(String fragment)
	{
		if (shows(fragment))
		{
			throw new AssertionError("Expected no '" + fragment + "' on screen. Showing: " + texts());
		}
	}

	/** A visible button whose text is exactly {@code text}. */
	public AbstractButton button(String text)
	{
		return find(AbstractButton.class, b -> text.equals(plain(b.getText())), "button '" + text + "'");
	}

	/** A visible label whose text contains {@code fragment}. */
	public JLabel label(String fragment)
	{
		return find(JLabel.class, l -> plain(l.getText()).contains(fragment), "label '" + fragment + "'");
	}

	/**
	 * A visible text field or area whose tooltip contains {@code fragment}: its
	 * own, or a wrapper's (RuneLite's IconTextField puts it on the panel around
	 * the field).
	 */
	public JTextComponent field(String tooltipFragment)
	{
		return find(JTextComponent.class, f ->
		{
			for (Component c = f; c != null && c != root.getParent(); c = c.getParent())
			{
				String tip = tooltipOf(c);
				if (tip != null && tip.contains(tooltipFragment))
				{
					return true;
				}
			}
			return false;
		}, "text field '" + tooltipFragment + "'");
	}

	// ---- Acting ----

	/** Clicks the button with exactly this text, or else the first label containing it. */
	public SwingDriver click(String text)
	{
		for (AbstractButton b : all(AbstractButton.class))
		{
			if (text.equals(plain(b.getText())))
			{
				return click(b);
			}
		}
		return click(label(text));
	}

	/** A button's own click, or a left press, release and click handed to the component's listeners. */
	public SwingDriver click(Component c)
	{
		if (c instanceof AbstractButton)
		{
			if (!c.isEnabled())
			{
				throw new AssertionError("Button '" + textOf(c) + "' is disabled");
			}
			((AbstractButton) c).doClick(0);
			return this;
		}
		long when = System.currentTimeMillis();
		MouseListener[] listeners = c.getMouseListeners();
		for (int id : new int[]{MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED})
		{
			MouseEvent e = new MouseEvent(c, id, when, 0, 1, 1, 1, false, MouseEvent.BUTTON1);
			for (MouseListener l : listeners)
			{
				if (id == MouseEvent.MOUSE_PRESSED)
				{
					l.mousePressed(e);
				}
				else if (id == MouseEvent.MOUSE_RELEASED)
				{
					l.mouseReleased(e);
				}
				else
				{
					l.mouseClicked(e);
				}
			}
		}
		return this;
	}

	/** Replaces the field's text, firing its document listeners as typing would. */
	public SwingDriver type(JTextComponent field, String text)
	{
		field.setText(text);
		return this;
	}

	public SwingDriver select(JComboBox<?> box, Object item)
	{
		box.setSelectedItem(item);
		if (box.getSelectedItem() == null || !box.getSelectedItem().equals(item))
		{
			throw new AssertionError("'" + item + "' is not in the list");
		}
		return this;
	}

	// ---- Rendering ----

	/**
	 * Renders the tree at {@code width} x {@code height} (height 0: as tall as it
	 * wants), scaled by {@code scale} for a sharp README image. Whatever does not
	 * fit is cut off; empty space is the root's background.
	 */
	public BufferedImage render(int width, int height, double scale)
	{
		int h = height > 0 ? height : layout(width);
		layout(width, h);
		BufferedImage image = new BufferedImage((int) Math.round(width * scale), (int) Math.round(h * scale),
			BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.scale(scale, scale);
			Color bg = root.getBackground();
			g.setColor(bg == null ? Color.DARK_GRAY : bg);
			g.fillRect(0, 0, width, h);
			root.printAll(g);
		}
		finally
		{
			g.dispose();
		}
		return image;
	}

	/**
	 * Stacks renders of several components, one under the other, {@code width}
	 * wide overall with {@code pad} layout pixels of margin around them (the
	 * margin the component's own parent panel would give it).
	 */
	public static BufferedImage stack(int width, int pad, double scale, Color background, Component... parts)
	{
		List<BufferedImage> images = new ArrayList<>();
		int padPx = (int) Math.round(pad * scale);
		int total = 2 * padPx;
		for (Component part : parts)
		{
			if (!(part instanceof Container))
			{
				throw new IllegalArgumentException("Only containers can be stacked: " + part);
			}
			BufferedImage img = new SwingDriver((Container) part).render(width - 2 * pad, 0, scale);
			images.add(img);
			total += img.getHeight();
		}
		BufferedImage out = new BufferedImage((int) Math.round(width * scale), Math.max(1, total),
			BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		try
		{
			g.setColor(background);
			g.fillRect(0, 0, out.getWidth(), out.getHeight());
			int y = padPx;
			for (BufferedImage img : images)
			{
				g.drawImage(img, padPx, y, null);
				y += img.getHeight();
			}
		}
		finally
		{
			g.dispose();
		}
		return out;
	}

	/** The part of {@code image} from {@code top} (in image pixels) down, e.g. to start a shot at a section. */
	public static BufferedImage from(BufferedImage image, int top)
	{
		int y = Math.max(0, Math.min(top, image.getHeight() - 1));
		return image.getSubimage(0, y, image.getWidth(), image.getHeight() - y);
	}

	/** Pads or cuts {@code image} to exactly {@code width} x {@code height}, so every README shot has a fixed size. */
	public static BufferedImage fit(BufferedImage image, int width, int height, Color background)
	{
		BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		try
		{
			g.setColor(background);
			g.fillRect(0, 0, width, height);
			g.drawImage(image, 0, 0, null);
		}
		finally
		{
			g.dispose();
		}
		return out;
	}

	public static void writePng(BufferedImage image, File file)
	{
		File dir = file.getAbsoluteFile().getParentFile();
		if (dir != null && !dir.isDirectory() && !dir.mkdirs())
		{
			throw new IllegalStateException("Cannot create " + dir);
		}
		try
		{
			if (!ImageIO.write(image, "png", file))
			{
				throw new IllegalStateException("No PNG writer");
			}
		}
		catch (IOException e)
		{
			throw new IllegalStateException("Cannot write " + file, e);
		}
	}

	// ---- Text ----

	static String textOf(Component c)
	{
		if (c instanceof JLabel)
		{
			return plain(((JLabel) c).getText());
		}
		if (c instanceof AbstractButton)
		{
			return plain(((AbstractButton) c).getText());
		}
		if (c instanceof JTextComponent)
		{
			return ((JTextComponent) c).getText();
		}
		return null;
	}

	private static String tooltipOf(Component c)
	{
		return c instanceof JComponent ? ((JComponent) c).getToolTipText() : null;
	}

	/** {@code <html>} label text as the reader sees it: tags dropped, entities decoded, spaces collapsed. */
	public static String plain(String text)
	{
		if (text == null)
		{
			return "";
		}
		String s = text.replaceAll("(?i)<br\\s*/?>", " ").replaceAll("<[^>]*>", "");
		s = s.replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("&amp;", "&");
		return s.replaceAll("\\s+", " ").trim();
	}

	/** The size the tree wants at its current width. */
	public Dimension preferred()
	{
		return root.getPreferredSize();
	}
}
