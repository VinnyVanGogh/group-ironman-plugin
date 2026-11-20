package com.vinnyvangogh.groupironman;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** The side panel's small Swing building blocks, shared by every tab so each piece is built one way. */
final class Ui
{
	private Ui()
	{
	}

	/** A plain label in {@code font}, and in {@code color} unless that is null (the look and feel's own). */
	static JLabel label(String text, Font font, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(font);
		if (color != null)
		{
			label.setForeground(color);
		}
		return label;
	}

	/** A small-font button that never takes focus; {@code tooltip} may be null. */
	static JButton button(String text, String tooltip, Runnable action)
	{
		JButton b = new JButton(text);
		b.setFont(FontManager.getRunescapeSmallFont());
		b.setFocusable(false);
		b.setToolTipText(tooltip);
		b.addActionListener(e -> action.run());
		return b;
	}

	/** A small-font toggle that never takes focus, {@code margin} pixels above and below its text. */
	static JToggleButton toggle(String text, boolean selected, int margin)
	{
		JToggleButton b = new JToggleButton(text, selected);
		b.setFont(FontManager.getRunescapeSmallFont());
		b.setFocusable(false);
		b.setMargin(new Insets(margin, 0, margin, 0));
		return b;
	}

	/** {@code c} alone in a row, stretched to its full width. */
	static JPanel wide(JComponent c)
	{
		JPanel row = new JPanel(new GridLayout(1, 1));
		row.add(c);
		return row;
	}

	/** An empty card: a dark box with padding, holding a column. */
	static JPanel card()
	{
		JPanel box = new GroupIronmanPanel.Column();
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setBorder(BorderFactory.createCompoundBorder(new EmptyBorder(0, 0, 6, 0), new EmptyBorder(6, 6, 6, 6)));
		return box;
	}

	/** Runs {@code action} when {@code c} is pressed. */
	static void onPress(JComponent c, Runnable action)
	{
		c.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				action.run();
			}
		});
	}

	/** {@link #onPress} with the hand cursor, so it looks clickable. */
	static void clickable(JComponent c, Runnable action)
	{
		c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		onPress(c, action);
	}

	/** Puts {@code text} on the computer's clipboard. */
	static void copy(String text)
	{
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
	}

	/** Text on the computer's clipboard, or "" when there is none or it cannot be read. Call it only on a button press. */
	static String clipboardText()
	{
		try
		{
			Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
			return data instanceof String ? (String) data : "";
		}
		catch (Exception e)
		{
			return "";
		}
	}
}
