package com.vinnyvangogh.groupironman;

import java.awt.Color;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The panel's Compare tab: two members side by side, who leads each stat, this group against another group's
 * hiscores, and "who benefits most" from an item. Everything shown is what members share or the public hiscores;
 * a figure nobody shared reads "?" and never counts as a loss.
 *
 * <p>The widgets are built once and kept, so a half-typed name or item survives the redraws member stats cause;
 * only the result blocks are rebuilt.
 */
final class CompareTab
{
	private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR;
	private static final Color ACCENT = ColorScheme.BRAND_ORANGE;
	private static final Color BEHIND = new Color(0xE0, 0x80, 0x60);
	private static final Color AHEAD = new Color(0x70, 0xC0, 0x70);
	/** A GIM group has at most five members; more names than this typed in is a mistake, not a group. */
	static final int MAX_OTHER_NAMES = 5;

	/** What the tab's buttons do. Callbacks may arrive on another thread. */
	interface Actions
	{
		/** Rank the group for a typed item. */
		default void benefit(String query, Consumer<BenefitFinder.Outcome> done)
		{
		}

		/** Summed hiscore experience per skill for these players; null when none could be looked up. */
		default void groupExperience(List<String> names, Consumer<Map<String, Long>> done)
		{
		}
	}

	/** What a redraw reads. */
	static final class View
	{
		final String self;
		final List<GroupCompare.Figures> figures;
		final List<String> groupNames;

		View(String self, List<GroupCompare.Figures> figures, List<String> groupNames)
		{
			this.self = self;
			this.figures = figures;
			this.groupNames = groupNames;
		}
	}

	private final Actions actions;
	private final Runnable redraw;
	private final JPanel root = new GroupIronmanPanel.Column();
	private final javax.swing.ButtonGroup bar = new javax.swing.ButtonGroup();
	private final JPanel results = new GroupIronmanPanel.Column();
	private final JComboBox<String> statBox = new JComboBox<>(SkillBoard.STATS.toArray(new String[0]));
	private final Map<String, javax.swing.JToggleButton> modeButtons = new java.util.HashMap<>();
	private boolean selectingStat;
	private SkillBoard.Filter filter = SkillBoard.Filter.BEHIND;
	private final JComboBox<String> memberB = new JComboBox<>();
	private final JTextField other = new JTextField();
	private final JTextField item = new JTextField();
	private String mode = "Compare";
	private boolean built;
	private View view;
	private List<String> lastNames = new ArrayList<>();
	private List<String> groupLines = Collections.emptyList();
	private List<String> benefitLines = Collections.emptyList();

	CompareTab(Actions actions, Runnable redraw)
	{
		this.actions = actions;
		this.redraw = redraw;
	}

	void draw(JPanel tab, View v)
	{
		view = v;
		if (!built)
		{
			build();
			built = true;
		}
		fillMembers(v);
		tab.add(root);
		showResults();
	}

	private void build()
	{
		JPanel modes = new JPanel(new GridLayout(0, 2, 2, 2));
		modes.setBorder(new EmptyBorder(0, 0, 6, 0));
		for (String m : Arrays.asList("Compare", "Leaderboard", "Group", "Item"))
		{
			javax.swing.JToggleButton b = Ui.toggle(m, m.equals(mode), 2);
			b.addActionListener(e ->
			{
				mode = m;
				showResults();
			});
			modes.add(b);
			modeButtons.put(m, b);
			bar.add(b);
		}
		root.add(modes);
		root.add(results);
		statBox.setFont(FontManager.getRunescapeSmallFont());
		statBox.setMaximumRowCount(14);
		statBox.addActionListener(e ->
		{
			if (!selectingStat)
			{
				showResults();
			}
		});
	}

	private void fillMembers(View v)
	{
		List<String> names = new ArrayList<>();
		v.figures.forEach(f -> names.add(f.name));
		if (names.equals(lastNames))
		{
			return;
		}
		lastNames = names;
		Object b = memberB.getSelectedItem();
		memberB.removeAllItems();
		names.stream().filter(n -> !n.equals(v.self)).forEach(memberB::addItem);
		if (b != null && names.contains(b) && !b.equals(v.self))
		{
			memberB.setSelectedItem(b);
		}
	}

	private void showResults()
	{
		results.removeAll();
		View v = view;
		if (v == null || v.figures.isEmpty())
		{
			results.add(line("Nothing to compare yet. Members appear once they share stats.", MUTED));
		}
		else
		{
			switch (mode)
			{
				case "Leaderboard":
					leaderboard(v);
					break;
				case "Group":
					group(v);
					break;
				case "Item":
					item(v);
					break;
				default:
					players(v);
					break;
			}
		}
		results.revalidate();
		results.repaint();
	}

	// ---- Players ----

	private void players(View v)
	{
		results.add(Ui.wide(memberB));
		JPanel filters = new JPanel(new GridLayout(1, SkillBoard.Filter.values().length, 2, 2));
		javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
		for (SkillBoard.Filter f : SkillBoard.Filter.values())
		{
			javax.swing.JToggleButton b = Ui.toggle(f.label, f == filter, 2);
			b.addActionListener(e ->
			{
				filter = f;
				showResults();
			});
			group.add(b);
			filters.add(b);
		}
		results.add(filters);
		GroupCompare.Figures me = figures(v, v.self);
		GroupCompare.Figures them = figures(v, (String) memberB.getSelectedItem());
		if (me == null || them == null)
		{
			results.add(line("Nothing to compare yet: your stats or the other member's have not been shared.",
				MUTED));
			return;
		}
		results.add(line("Biggest gap first. Tap a row to see it on the Leaderboard.", MUTED));
		List<SkillBoard.Gap> gaps = SkillBoard.compare(me, them, filter);
		if (gaps.isEmpty())
		{
			results.add(line("Nothing here.", MUTED));
		}
		for (SkillBoard.Gap g : gaps)
		{
			JLabel row = line(gapText(g, them.name), g.delta < 0 ? BEHIND : g.delta > 0 ? AHEAD : Color.WHITE);
			row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			row.addMouseListener(new java.awt.event.MouseAdapter()
			{
				@Override
				public void mouseClicked(java.awt.event.MouseEvent e)
				{
					openOnLeaderboard(g.stat);
				}
			});
			results.add(row);
		}
		for (String s : playerLines(GroupCompare.players(me, them), me.name, them.name, true))
		{
			results.add(line(s, Color.WHITE));
		}
	}

	/** "Magic 70 vs 85: 15 behind Member 2". */
	static String gapText(SkillBoard.Gap g, String other)
	{
		String tail = g.delta < 0 ? -g.delta + " behind " + other : g.delta > 0 ? g.delta + " ahead of " + other
			: "tied";
		return g.stat + " " + g.mine + " vs " + g.theirs + ": " + tail;
	}

	private void openOnLeaderboard(String stat)
	{
		selectStat(stat);
		mode = "Leaderboard";
		modeButtons.get(mode).setSelected(true);
		showResults();
	}

	private void selectStat(String stat)
	{
		selectingStat = true;
		statBox.setSelectedItem(stat);
		selectingStat = false;
	}

	private static GroupCompare.Figures figures(View v, String name)
	{
		for (GroupCompare.Figures f : v.figures)
		{
			if (f.name.equals(name))
			{
				return f;
			}
		}
		return null;
	}

	/** One line per stat both or either shared: "Magic 50 / 60  GIM2", or "?" for a figure nobody shared. */
	static List<String> playerLines(List<GroupCompare.Row> rows, String a, String b, boolean bossesOnly)
	{
		List<String> out = new ArrayList<>();
		for (GroupCompare.Row r : rows)
		{
			if (r.a == null && r.b == null || bossesOnly && r.category != GroupCompare.Category.BOSS)
			{
				continue;
			}
			String lead = r.verdict == GroupCompare.Verdict.A ? "  " + a : r.verdict == GroupCompare.Verdict.B
				? "  " + b : r.verdict == GroupCompare.Verdict.TIE ? "  tie" : "";
			out.add(r.label + " " + show(r, r.a) + " / " + show(r, r.b) + lead);
		}
		return out;
	}

	private static String show(GroupCompare.Row r, Double v)
	{
		if (v == null)
		{
			return "?";
		}
		return r.category == GroupCompare.Category.BOSS ? PersonalBests.format(v) : String.valueOf(v.longValue());
	}

	// ---- Leaderboard ----

	private void leaderboard(View v)
	{
		JPanel arrows = new JPanel(new java.awt.BorderLayout(2, 0));
		arrows.add(Ui.button("<", "Previous skill", () -> stepStat(-1)), java.awt.BorderLayout.WEST);
		arrows.add(statBox, java.awt.BorderLayout.CENTER);
		arrows.add(Ui.button(">", "Next skill", () -> stepStat(1)), java.awt.BorderLayout.EAST);
		results.add(arrows);
		String stat = (String) statBox.getSelectedItem();
		List<SkillBoard.Entry> ranked = SkillBoard.rank(v.figures, stat);
		results.add(line(youHeader(SkillBoard.entry(ranked, v.self), SkillBoard.rankedCount(ranked)), Color.WHITE));
		int scale = SkillBoard.scale(stat, ranked);
		for (SkillBoard.Entry e : ranked)
		{
			boolean self = e.name.equals(v.self);
			results.add(line((e.rank == 0 ? "-" : "#" + e.rank) + " " + e.name + (self ? " (you)" : "") + "  "
				+ (e.value == null ? "?" : e.value), self ? ACCENT : Color.WHITE));
			if (e.value != null)
			{
				results.add(new LevelBar(e.value, scale, self));
			}
			String hint = SkillBoard.hint(e);
			if (!hint.isEmpty())
			{
				results.add(line(hint, MUTED));
			}
		}
	}

	/** "you: #2 of 4", or that you have not shared the stat. */
	static String youHeader(SkillBoard.Entry mine, int of)
	{
		return mine == null || mine.rank == 0 ? "you: not shared yet" : "you: #" + mine.rank + " of " + of;
	}

	private void stepStat(int steps)
	{
		selectStat(SkillBoard.step((String) statBox.getSelectedItem(), steps));
		showResults();
	}

	/** A thin horizontal bar, filled to {@code value} out of {@code scale}. */
	static final class LevelBar extends javax.swing.JComponent
	{
		private final double fill;
		private final boolean self;

		LevelBar(int value, int scale, boolean self)
		{
			this.fill = Math.max(0, Math.min(1, value / (double) Math.max(1, scale)));
			this.self = self;
			setPreferredSize(new java.awt.Dimension(10, 5));
			setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 5));
		}

		@Override
		protected void paintComponent(java.awt.Graphics g)
		{
			g.setColor(ColorScheme.DARKER_GRAY_COLOR);
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setColor(self ? ACCENT : ColorScheme.PROGRESS_COMPLETE_COLOR);
			g.fillRect(0, 0, (int) Math.round(getWidth() * fill), getHeight());
		}
	}

	// ---- Group against group ----

	private void group(View v)
	{
		results.add(line("Another group's players, comma separated (up to " + MAX_OTHER_NAMES + "):", MUTED));
		results.add(Ui.wide(other));
		results.add(Ui.button("Compare with this group", "Looks both groups up on the public hiscores",
			() -> compareGroups(v)));
		for (String s : groupLines)
		{
			results.add(line(s, Color.WHITE));
		}
	}

	private void compareGroups(View v)
	{
		List<String> theirs = parseNames(other.getText());
		if (theirs.isEmpty())
		{
			groupLines = Collections.singletonList("Type the other group's player names first.");
			showResults();
			return;
		}
		groupLines = Collections.singletonList("Looking both groups up on the hiscores...");
		showResults();
		actions.groupExperience(v.groupNames, mine -> actions.groupExperience(theirs, other ->
			SwingUtilities.invokeLater(() ->
			{
				groupLines = groupLines(mine, other);
				showResults();
			})));
	}

	/** Names split on commas, trimmed, deduplicated, at most {@link #MAX_OTHER_NAMES}. */
	static List<String> parseNames(String text)
	{
		List<String> out = new ArrayList<>();
		if (text == null)
		{
			return out;
		}
		for (String s : text.split(","))
		{
			String n = s.trim();
			if (!n.isEmpty() && out.stream().noneMatch(x -> x.equalsIgnoreCase(n)) && out.size() < MAX_OTHER_NAMES)
			{
				out.add(n);
			}
		}
		return out;
	}

	/** Skill by skill, experience of this group against the other, who is ahead and by how much. */
	static List<String> groupLines(Map<String, Long> mine, Map<String, Long> theirs)
	{
		if (mine == null || theirs == null)
		{
			return Collections.singletonList("Could not read the hiscores for "
				+ (mine == null ? "your group" : "that group") + ". Check the names and try again.");
		}
		List<String> out = new ArrayList<>();
		for (GroupCompare.Gap g : GroupCompare.groups(mine, theirs))
		{
			String who = g.delta > 0 ? "you lead by " + String.format(Locale.ROOT, "%,d", g.delta)
				: g.delta < 0 ? "behind by " + String.format(Locale.ROOT, "%,d", -g.delta) : "level";
			out.add(g.label + ": " + who);
		}
		return out;
	}

	// ---- Who benefits most ----

	private void item(View v)
	{
		results.add(line("An item, for example Dragon defender or Abyssal whip:", MUTED));
		results.add(Ui.wide(item));
		results.add(Ui.button("Who benefits most?", "Ranks members by what it does for them", () ->
		{
			benefitLines = Collections.singletonList("Looking the item up...");
			showResults();
			actions.benefit(item.getText(), o -> SwingUtilities.invokeLater(() ->
			{
				benefitLines = benefitLines(o);
				showResults();
			}));
		}));
		for (String s : benefitLines)
		{
			results.add(line(s, Color.WHITE));
		}
	}

	static List<String> benefitLines(BenefitFinder.Outcome o)
	{
		if (!o.message.isEmpty())
		{
			return Collections.singletonList(o.message);
		}
		List<String> out = new ArrayList<>();
		out.add(o.item + ", best first:");
		for (ItemBenefit.Result r : o.ranked)
		{
			String what = r.alreadyOwned ? "already has an equal or better one"
				: r.usableNow ? "can use it now, +" + r.upgrade + " over current gear"
				: r.levelsShort + " level" + (r.levelsShort == 1 ? "" : "s") + " short";
			out.add(r.name + ": " + what);
		}
		if (o.requirementsUnknown)
		{
			out.add("Level requirements for this item are not known, so \"can use it now\" only means nothing is"
				+ " known to block it.");
		}
		return out;
	}

	private static JLabel line(String text, Color color)
	{
		JLabel l = Ui.label("<html><body style='width:175px'>" + GroupIronmanPanel.escape(text) + "</body></html>",
			FontManager.getRunescapeSmallFont(), color);
		l.setBorder(new EmptyBorder(1, 0, 1, 0));
		return l;
	}
}
