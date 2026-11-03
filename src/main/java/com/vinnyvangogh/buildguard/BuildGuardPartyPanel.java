package com.vinnyvangogh.buildguard;

import java.awt.BorderLayout;
import java.util.List;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The party side panel: every member's caps and levels, who is at risk, and
 * who has what. Text only; it draws what {@link PartyRoster} hands it.
 */
class BuildGuardPartyPanel extends PluginPanel
{
	private final JPanel body = new JPanel();

	BuildGuardPartyPanel()
	{
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(8, 8, 8, 8));
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(body, BorderLayout.NORTH);
	}

	/** Must be called on the Swing thread. */
	void render(boolean inParty, PartyRoster roster)
	{
		body.removeAll();
		if (!inParty)
		{
			line("Not in a RuneLite Party.");
			line("Join one from the Party panel; every member needs Build Guard with 'Share build with party' on.");
		}
		else if (roster.isEmpty())
		{
			line("No builds shared yet.");
			line("Members appear here once they turn on 'Share build with party'.");
		}
		else
		{
			heading("Builds");
			for (String s : roster.memberLines())
			{
				line(s);
			}

			List<String> risks = roster.riskLines();
			if (!risks.isEmpty())
			{
				heading("At risk");
				for (String s : risks)
				{
					line(s);
				}
			}

			Map<String, List<String>> gear = roster.gearIndex();
			if (!gear.isEmpty())
			{
				heading("Who has what");
				for (Map.Entry<String, List<String>> e : gear.entrySet())
				{
					line(e.getKey() + ": " + String.join(", ", e.getValue()));
				}
			}
		}
		body.revalidate();
		body.repaint();
	}

	private void heading(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setBorder(new EmptyBorder(8, 0, 2, 0));
		body.add(label);
	}

	private void line(String text)
	{
		// The html wrapper makes long lines wrap at the panel width. Text from
		// peers is escaped first: a display name or item name is data, not markup.
		JLabel label = new JLabel("<html><body style='width:180px'>" + escape(text) + "</body></html>");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setBorder(new EmptyBorder(1, 0, 1, 0));
		body.add(label);
	}

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
