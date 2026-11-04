package com.vinnyvangogh.groupironman;

import java.awt.BorderLayout;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The group side panel: who is online and where, drops and collection log
 * slots since you last played, carried items and the rune pouch, quiver and
 * seed vault, live stats, last banks, the group storage log and the "Who has
 * it?" search. Text only; it draws what {@link GroupRoster} hands it.
 */
class GroupIronmanPanel extends PluginPanel
{
	/** What the panel last drew, so typing in the search box can redraw on its own. */
	static final class View
	{
		final boolean inParty;
		final GroupRoster group;
		final ItemLookup items;
		final GroupOutbox.Switches switches;
		final RegionNames regions;
		/** When this player last played before this session, 0 when not known. */
		final long since;
		final String self;

		View(boolean inParty, GroupRoster group, ItemLookup items, GroupOutbox.Switches switches,
			RegionNames regions, long since, String self)
		{
			this.inParty = inParty;
			this.group = group;
			this.items = items;
			this.switches = switches;
			this.regions = regions;
			this.since = since;
			this.self = self;
		}
	}

	private final JPanel body = new JPanel();
	private final JTextField search = new JTextField();
	private View view;

	GroupIronmanPanel()
	{
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(8, 8, 8, 8));

		JPanel top = new JPanel(new BorderLayout());
		top.setBorder(new EmptyBorder(0, 0, 6, 0));
		JLabel label = new JLabel("Who has it?");
		label.setFont(FontManager.getRunescapeSmallFont());
		top.add(label, BorderLayout.NORTH);
		search.setToolTipText("Search members' last banks, inventories, equipment and group storage by item name");
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				redraw();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				redraw();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				redraw();
			}
		});
		top.add(search, BorderLayout.CENTER);
		add(top, BorderLayout.NORTH);

		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(body, BorderLayout.CENTER);
	}

	/** Must be called on the Swing thread. */
	void render(View view)
	{
		this.view = view;
		redraw();
	}

	private void redraw()
	{
		View v = view;
		body.removeAll();
		if (v == null)
		{
			return;
		}
		long now = System.currentTimeMillis();

		String query = search.getText();
		if (query != null && query.trim().length() >= GroupSearch.MIN_QUERY)
		{
			heading("Who has it?");
			List<String> hits = GroupSearch.search(query, v.group.searchSources(), v.items, now);
			if (hits.isEmpty())
			{
				line("Nobody's shared bank, inventory, equipment or the group storage has it.");
			}
			for (String s : hits)
			{
				line(s);
			}
			for (String s : GroupSearch.history(query, v.group.log().entries(), v.items, now,
				FindCommand.HISTORY_LIMIT))
			{
				line(s);
			}
		}

		if (!v.inParty)
		{
			line("Not in a RuneLite Party.");
			line("Join one from the Party panel; every member needs Group Ironman with sharing turned on.");
		}
		else if (!v.group.hasAnything())
		{
			line("Nothing shared yet.");
			line("Members appear here once they turn on sharing in Group Ironman's settings.");
		}

		List<String> members = v.group.memberLines(v.regions, now);
		if (!members.isEmpty())
		{
			heading("Members");
			for (String s : members)
			{
				line(s);
			}
		}

		if (v.switches.drops)
		{
			renderEvents(v, now);
		}

		List<String> held = v.group.itemLines(v.items, now);
		if (!held.isEmpty())
		{
			heading("Items");
			for (String s : held)
			{
				line(s);
			}
		}

		List<List<String>> stats = v.group.statsBlocks(v.regions);
		if (!stats.isEmpty())
		{
			heading("Group stats");
		}
		for (List<String> block : stats)
		{
			for (String s : block)
			{
				line(s);
			}
			gap();
		}

		List<String> banks = v.group.bankLines(v.items, now);
		if (!banks.isEmpty())
		{
			heading("Banks (last seen)");
			for (String s : banks)
			{
				line(s);
			}
		}

		if (v.switches.storage)
		{
			heading("Group storage log");
			line("Only changes by members running Group Ironman with storage sharing on are listed.");
			List<String> log = v.group.log().lines(v.items, now);
			if (log.isEmpty())
			{
				line("No changes seen yet.");
			}
			for (String s : log)
			{
				line(s);
			}
		}

		body.revalidate();
		body.repaint();
	}

	/** Drops and new collection log slots since this player last played, or the latest when that is not known. */
	private void renderEvents(View v, long now)
	{
		List<GroupEvent> events;
		if (v.since > 0)
		{
			heading("Since you last played (" + BoostText.ago(now, v.since) + ")");
			events = v.group.events().since(v.since, v.self);
		}
		else
		{
			heading("Drops and collection log");
			events = v.group.events().entries();
		}
		if (events.isEmpty())
		{
			line(v.since > 0 ? "Nothing new from the group." : "None seen yet.");
			return;
		}
		List<String> lines = DropLog.lines(events, v.items, now);
		for (int i = 0; i < lines.size() && i < GroupService.MAX_EVENT_LINES; i++)
		{
			line(lines.get(i));
		}
		if (lines.size() > GroupService.MAX_EVENT_LINES)
		{
			line("and " + (lines.size() - GroupService.MAX_EVENT_LINES) + " more.");
		}
	}

	private void heading(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setBorder(new EmptyBorder(8, 0, 2, 0));
		body.add(label);
	}

	private void gap()
	{
		JLabel label = new JLabel(" ");
		label.setBorder(new EmptyBorder(0, 0, 2, 0));
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
