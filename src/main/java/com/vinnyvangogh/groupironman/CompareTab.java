package com.vinnyvangogh.groupironman;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ImageUtil;

/**
 * The panel's Compare tab: the group's Leaderboard (a grid of the real skill icons, tap one to rank the members),
 * two members side by side, this group against another group's hiscores, and "who benefits most" from an item.
 * Everything shown is what members share or the public hiscores; a figure nobody shared reads "?" and is never a
 * loss. Each member keeps one colour (see {@link MemberColors}) on every name, bar, tile level and table cell.
 *
 * <p>The widgets are built once and kept, so a half-typed name or item survives the redraws member stats cause;
 * only the result blocks are rebuilt.
 */
final class CompareTab
{
	private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR;
	private static final Color ACCENT = ColorScheme.BRAND_ORANGE;
	/** A GIM group has at most five members; more names than this typed in is a mistake, not a group. */
	static final int MAX_OTHER_NAMES = 5;

	/** The mode that shows what each member's bank would give in experience. */
	static final String BANKED = "Banked XP";

	/** What the tab's buttons do. Callbacks may arrive on another thread. */
	interface Actions
	{
		/** Rank the group for an item picked from the dropdown. */
		default void benefit(int itemId, Consumer<BenefitFinder.Outcome> done)
		{
		}

		/** Items matching typed text, best first, for the dropdown (same method the Shopping tab uses). */
		void searchItems(String query, Consumer<List<ShoppingTab.Hit>> done);

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

		/** Banked experience per member (bank plus inventory), and the group storage's, counted once. */
		final Map<String, Map<String, Double>> banked;
		final Map<String, Double> storageBanked;

		View(String self, List<GroupCompare.Figures> figures, List<String> groupNames)
		{
			this(self, figures, groupNames, Collections.emptyMap(), Collections.emptyMap());
		}

		View(String self, List<GroupCompare.Figures> figures, List<String> groupNames,
			Map<String, Map<String, Double>> banked, Map<String, Double> storageBanked)
		{
			this.self = self;
			this.figures = figures;
			this.groupNames = groupNames;
			this.banked = banked;
			this.storageBanked = storageBanked;
		}
	}

	private final Actions actions;
	private final SkillIconManager skillIcons;
	private final Runnable redraw;
	private final JPanel root = new GroupIronmanPanel.Column();
	private final javax.swing.ButtonGroup bar = new javax.swing.ButtonGroup();
	private final JPanel results = new GroupIronmanPanel.Column();
	private final JComboBox<String> memberA = new JComboBox<>();
	private final JComboBox<String> memberB = new JComboBox<>();
	private final JTextField other = new JTextField();
	private final JTextField item = new JTextField();
	private final JComboBox<ShoppingTab.Hit> hits = new JComboBox<>();
	private final Map<StatCategory, String> openCount = new java.util.EnumMap<>(StatCategory.class);
	private final Map<String, Icon> iconCache = new java.util.HashMap<>();
	private StatCategory category = StatCategory.SKILLING;
	private String stat = Skill.AGILITY.getName();
	private String mode = "Leaderboard";
	private boolean withStorage = true;
	private boolean built;
	private boolean fillingMembers;
	private View view;
	private List<String> lastNames = new ArrayList<>();
	private List<String> groupLines = Collections.emptyList();
	private List<String> benefitLines = Collections.emptyList();

	CompareTab(Actions actions, SkillIconManager skillIcons, Runnable redraw)
	{
		this.actions = actions;
		this.skillIcons = skillIcons;
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
		for (String m : Arrays.asList("Leaderboard", "Compare", "Group", "Item", BANKED))
		{
			javax.swing.JToggleButton b = Ui.toggle(m, m.equals(mode), 2);
			b.addActionListener(e ->
			{
				mode = m;
				showResults();
			});
			modes.add(b);
			bar.add(b);
		}
		root.add(modes);
		root.add(results);
		for (JComboBox<String> box : Arrays.asList(memberA, memberB))
		{
			box.setFont(FontManager.getRunescapeSmallFont());
			box.addActionListener(e ->
			{
				if (!fillingMembers)
				{
					showResults();
				}
			});
		}
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
		fillingMembers = true;
		Object a = memberA.getSelectedItem();
		Object b = memberB.getSelectedItem();
		memberA.removeAllItems();
		memberB.removeAllItems();
		names.forEach(n ->
		{
			memberA.addItem(n);
			memberB.addItem(n);
		});
		String firstOther = names.stream().filter(n -> !n.equals(v.self)).findFirst().orElse(null);
		memberA.setSelectedItem(a != null && names.contains(a) ? a : names.contains(v.self) ? v.self
			: names.isEmpty() ? null : names.get(0));
		memberB.setSelectedItem(b != null && names.contains(b) ? b : firstOther != null ? firstOther
			: names.isEmpty() ? null : names.get(0));
		fillingMembers = false;
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
				case "Group":
					group(v);
					break;
				case "Item":
					item(v);
					break;
				case BANKED:
					banked(v);
					break;
				case "Compare":
					categories();
					legend(v);
					players(v);
					break;
				default:
					categories();
					legend(v);
					leaderboard(v);
					break;
			}
		}
		results.revalidate();
		results.repaint();
	}

	// ---- Shared pieces ----

	private void categories()
	{
		JPanel row = new JPanel(new GridLayout(0, 2, 2, 2));
		row.setBorder(new EmptyBorder(0, 0, 4, 0));
		javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
		for (StatCategory c : StatCategory.values())
		{
			javax.swing.JToggleButton b = Ui.toggle(c.label, c == category, 2);
			b.addActionListener(e ->
			{
				category = c;
				showResults();
			});
			group.add(b);
			row.add(b);
		}
		results.add(row);
	}

	/** Every member's name in their own colour, so the colours mean something before the first table. */
	private void legend(View v)
	{
		JPanel row = new JPanel(new GridLayout(0, 2, 4, 0));
		row.setBorder(new EmptyBorder(0, 0, 4, 0));
		for (int i = 0; i < v.figures.size(); i++)
		{
			row.add(Ui.label(v.figures.get(i).name, FontManager.getRunescapeSmallFont(), MemberColors.of(i)));
		}
		results.add(row);
	}

	/** The small icon for a stat: the real RuneLite skill icon, the combat icon, or null (quest points, counts). */
	private Icon icon(String name)
	{
		if (skillIcons == null || !(PanelModel.SKILL_ORDER.contains(name) || SkillBoard.TOTAL.equals(name)
			|| SkillBoard.COMBAT.equals(name)))
		{
			return null;
		}
		return iconCache.computeIfAbsent(name, n ->
		{
			java.awt.image.BufferedImage image = null;
			if (SkillBoard.COMBAT.equals(n))
			{
				image = ImageUtil.loadImageResource(SkillIconManager.class, "/skill_icons_small/combat.png");
			}
			else if (SkillBoard.TOTAL.equals(n))
			{
				// Skill.OVERALL is null in current RuneLite, so getSkillImage
				// cannot draw the Total tile; load the client's own icon instead.
				image = ImageUtil.loadImageResource(SkillIconManager.class, "/skill_icons_small/overall.png");
			}
			else
			{
				Skill s = skillOf(n);
				image = s == null ? null : skillIcons.getSkillImage(s, true);
			}
			return image == null ? null : new ImageIcon(image);
		});
	}

	private static Skill skillOf(String name)
	{
		for (Skill s : Skill.values())
		{
			if (s.getName().equals(name))
			{
				return s;
			}
		}
		return null;
	}

	/** An icon label for {@code name}, or its short text ("QP") when it has no icon. */
	private JLabel iconLabel(String name)
	{
		Icon icon = icon(name);
		JLabel l = new JLabel(icon);
		if (icon == null)
		{
			l.setText(SkillBoard.QUEST_POINTS.equals(name) ? "QP" : "");
			l.setFont(FontManager.getRunescapeSmallFont());
			l.setForeground(MUTED);
		}
		return l;
	}

	private static int indexOf(View v, String name)
	{
		for (int i = 0; i < v.figures.size(); i++)
		{
			if (v.figures.get(i).name.equals(name))
			{
				return i;
			}
		}
		return -1;
	}

	private static Color colorOf(View v, String name)
	{
		return MemberColors.of(Math.max(0, indexOf(v, name)));
	}

	private static Color leaderColor(View v, String stat)
	{
		List<Integer> values = new ArrayList<>();
		for (GroupCompare.Figures f : v.figures)
		{
			values.add(SkillBoard.value(f, stat));
		}
		int lead = SkillBoard.leader(values);
		return lead < 0 ? Color.WHITE : MemberColors.of(lead);
	}

	private static String top(View v, String stat)
	{
		Integer best = null;
		for (GroupCompare.Figures f : v.figures)
		{
			Integer x = SkillBoard.value(f, stat);
			best = x != null && (best == null || x > best) ? x : best;
		}
		return best == null ? "?" : String.valueOf(best);
	}

	// ---- Leaderboard ----

	private void leaderboard(View v)
	{
		if (category == StatCategory.SKILLING)
		{
			skillGrid(v);
			results.add(rankCard(v, stat, true));
			return;
		}
		countList(v);
	}

	/** The Hiscores-style grid: one tile per stat, the group's best figure in its leader's colour. */
	private void skillGrid(View v)
	{
		JPanel grid = new JPanel(new GridLayout(0, 3, 2, 2));
		grid.setBorder(new EmptyBorder(0, 0, 4, 0));
		for (String s : SkillBoard.STATS)
		{
			JPanel tile = new JPanel(new BorderLayout(2, 0));
			tile.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			tile.setBorder(new LineBorder(s.equals(stat) ? ACCENT : ColorScheme.DARKER_GRAY_COLOR, 1));
			tile.setToolTipText(s);
			tile.add(iconLabel(s), BorderLayout.WEST);
			tile.add(Ui.label(top(v, s), FontManager.getRunescapeSmallFont(), leaderColor(v, s)),
				BorderLayout.CENTER);
			Ui.clickable(tile, () ->
			{
				stat = s;
				showResults();
			});
			grid.add(tile);
		}
		results.add(grid);
	}

	/** One stat ranked across the group: place, name in its colour, value, and a bar in the same colour. */
	private JPanel rankCard(View v, String statName, boolean header)
	{
		JPanel card = Ui.card();
		if (header)
		{
			JPanel head = new JPanel(new BorderLayout(4, 0));
			head.setOpaque(false);
			head.add(iconLabel(statName), BorderLayout.WEST);
			head.add(Ui.label(statName, FontManager.getRunescapeSmallFont(), Color.WHITE), BorderLayout.CENTER);
			card.add(head);
		}
		List<SkillBoard.Entry> ranked = SkillBoard.rank(v.figures, statName);
		int scale = SkillBoard.scale(statName, ranked);
		for (SkillBoard.Entry e : ranked)
		{
			Color c = colorOf(v, e.name);
			JPanel row = new JPanel(new BorderLayout(4, 0));
			row.setOpaque(false);
			row.setToolTipText(SkillBoard.hint(e));
			row.add(Ui.label(e.rank == 0 ? "-" : String.valueOf(e.rank), FontManager.getRunescapeSmallFont(),
				MUTED), BorderLayout.WEST);
			row.add(Ui.label(e.name + (e.name.equals(view.self) ? " (you)" : ""),
				FontManager.getRunescapeSmallFont(), c), BorderLayout.CENTER);
			row.add(Ui.label(e.value == null ? "?" : String.format(Locale.ROOT, "%,d", e.value),
				FontManager.getRunescapeSmallFont(), Color.WHITE), BorderLayout.EAST);
			card.add(row);
			if (e.value != null)
			{
				card.add(new LevelBar(e.value, scale, c));
			}
		}
		return card;
	}

	/** Bossing, Activities and Minigames: a row per count anyone has, tap one to rank the group on it. */
	private void countList(View v)
	{
		String note = countNote(v);
		if (note != null)
		{
			results.add(line(note, MUTED));
			return;
		}
		results.add(line("Hiscore counts. Tap a row to rank the group.", MUTED));
		String open = openCount.get(category);
		for (String name : SkillBoard.activeCounts(v.figures, category))
		{
			JPanel row = new JPanel(new BorderLayout(4, 0));
			row.setBorder(new EmptyBorder(2, 2, 2, 2));
			row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			row.add(Ui.label(name, FontManager.getRunescapeSmallFont(), Color.WHITE), BorderLayout.CENTER);
			row.add(Ui.label(top(v, name), FontManager.getRunescapeSmallFont(), leaderColor(v, name)),
				BorderLayout.EAST);
			Ui.clickable(row, () ->
			{
				openCount.put(category, name.equals(open) ? null : name);
				showResults();
			});
			results.add(row);
			if (name.equals(open))
			{
				results.add(rankCard(v, name, false));
			}
		}
	}

	/** Why a count category shows nothing, or null when there is something to show. */
	private String countNote(View v)
	{
		boolean anyRead = v.figures.stream().anyMatch(f -> f.scores != null);
		if (!anyRead)
		{
			return "Hiscore counts are not loaded yet. They are looked up when you log in and every 6 hours.";
		}
		if (SkillBoard.activeCounts(v.figures, category).isEmpty())
		{
			return "Nobody in the group is ranked in " + category.label + " on the hiscores yet.";
		}
		return null;
	}

	/** A thin horizontal bar, filled to {@code value} out of {@code scale}. */
	static final class LevelBar extends javax.swing.JComponent
	{
		private final double fill;
		private final Color color;

		LevelBar(int value, int scale, Color color)
		{
			this.fill = Math.max(0, Math.min(1, value / (double) Math.max(1, scale)));
			this.color = color;
			setPreferredSize(new java.awt.Dimension(10, 5));
			setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 5));
		}

		@Override
		protected void paintComponent(java.awt.Graphics g)
		{
			g.setColor(ColorScheme.DARK_GRAY_COLOR);
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setColor(color);
			g.fillRect(0, 0, (int) Math.round(getWidth() * fill), getHeight());
		}
	}

	// ---- Compare ----

	private void players(View v)
	{
		JPanel pair = new JPanel(new GridLayout(1, 2, 2, 0));
		pair.add(memberA);
		pair.add(memberB);
		results.add(pair);
		String an = (String) memberA.getSelectedItem();
		String bn = (String) memberB.getSelectedItem();
		GroupCompare.Figures a = figures(v, an);
		GroupCompare.Figures b = figures(v, bn);
		if (a == null || b == null)
		{
			results.add(line("Nothing to compare yet: the members' stats have not been shared.", MUTED));
			return;
		}
		List<String> rows = category == StatCategory.SKILLING ? SkillBoard.COMPARE_ROWS
			: SkillBoard.activeCounts(v.figures, category);
		if (category != StatCategory.SKILLING && countNote(v) != null)
		{
			results.add(line(countNote(v), MUTED));
			return;
		}
		JPanel table = new JPanel(new java.awt.GridBagLayout());
		table.setBorder(new EmptyBorder(4, 0, 4, 0));
		int ai = indexOf(v, an);
		int bi = indexOf(v, bn);
		addRow(table, 0, label(category == StatCategory.SKILLING ? "Skill" : "Count", MUTED),
			label(shortName(an), MemberColors.of(ai)), label(shortName(bn), MemberColors.of(bi)));
		int r = 1;
		for (String s : rows)
		{
			Integer x = SkillBoard.value(a, s);
			Integer y = SkillBoard.value(b, s);
			JPanel name = new JPanel(new BorderLayout(3, 0));
			name.setOpaque(false);
			name.setToolTipText(s);
			if (category == StatCategory.SKILLING)
			{
				name.add(iconLabel(s), BorderLayout.WEST);
			}
			name.add(label(rowName(s), Color.WHITE), BorderLayout.CENTER);
			addRow(table, r++, name, cell(x, SkillBoard.higher(x, y), ai), cell(y, SkillBoard.higher(y, x), bi));
		}
		results.add(table);
		results.add(line("The higher value is tinted in that member's colour.", MUTED));
		if (category == StatCategory.BOSSING)
		{
			for (String s : playerLines(GroupCompare.players(a, b), a.name, b.name, true))
			{
				results.add(line(s, Color.WHITE));
			}
		}
	}

	private static String shortName(String name)
	{
		return name == null ? "" : name.length() > 9 ? name.substring(0, 8) + "." : name;
	}

	private static String rowName(String stat)
	{
		return SkillBoard.QUEST_POINTS.equals(stat) ? "Quests" : SkillBoard.COMBAT.equals(stat) ? "Combat"
			: SkillBoard.TOTAL.equals(stat) ? "Total" : stat;
	}

	private static JLabel label(String text, Color color)
	{
		return Ui.label(text, FontManager.getRunescapeSmallFont(), color);
	}

	private static JLabel cell(Integer v, boolean higher, int memberIndex)
	{
		JLabel l = label(v == null ? "?" : String.format(Locale.ROOT, "%,d", v),
			higher ? MemberColors.of(memberIndex) : Color.WHITE);
		l.setHorizontalAlignment(SwingConstants.CENTER);
		l.setOpaque(higher);
		if (higher)
		{
			l.setBackground(MemberColors.soft(memberIndex));
		}
		return l;
	}

	private static void addRow(JPanel table, int y, java.awt.Component name, java.awt.Component a, java.awt.Component b)
	{
		java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
		c.gridy = y;
		c.fill = java.awt.GridBagConstraints.HORIZONTAL;
		c.insets = new java.awt.Insets(1, 1, 1, 1);
		c.gridx = 0;
		c.weightx = 1.0;
		table.add(name, c);
		c.gridx = 1;
		c.weightx = 0;
		c.ipadx = 22;
		table.add(a, c);
		c.gridx = 2;
		table.add(b, c);
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

	// ---- Banked experience ----

	private void banked(View v)
	{
		results.add(line("Experience the items in each member's bank and inventory would give if used. "
			+ "Levels shown are at least what they would reach.", MUTED));
		javax.swing.JCheckBox storage = new javax.swing.JCheckBox("Count group storage once in the total",
			withStorage);
		storage.setFont(FontManager.getRunescapeSmallFont());
		storage.setOpaque(false);
		storage.addActionListener(e ->
		{
			withStorage = storage.isSelected();
			showResults();
		});
		results.add(storage);
		Map<String, Map<String, Integer>> levels = new java.util.HashMap<>();
		v.figures.forEach(f -> levels.put(f.name, f.levels));
		for (String s : BankedXp.lines(v.banked, v.storageBanked, levels, withStorage))
		{
			results.add(line(s, s.endsWith(":") ? Color.WHITE : MUTED));
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
		results.add(line("Search an item, then pick it from the list:", MUTED));
		results.add(Ui.wide(item));
		results.add(Ui.button("Search", "Finds items matching the text, for example whip or dragon defender",
			this::searchItems));
		results.add(Ui.wide(hits));
		results.add(Ui.button("Who benefits most?", "Ranks members by what it does for them", this::rank));
		for (String s : benefitLines)
		{
			results.add(line(s, Color.WHITE));
		}
	}

	/** Looks the typed text up on the client thread and fills the dropdown with the matches. */
	private void searchItems()
	{
		String q = item.getText().trim();
		if (q.length() < 2)
		{
			benefitLines = Collections.singletonList("Type at least two letters of an item name, then press Search.");
			showResults();
			return;
		}
		actions.searchItems(q, found -> SwingUtilities.invokeLater(() ->
		{
			hits.removeAllItems();
			found.forEach(hits::addItem);
			benefitLines = found.isEmpty() ? Collections.singletonList("No item found for \"" + q + "\".")
				: Collections.<String>emptyList();
			showResults();
		}));
	}

	private void rank()
	{
		ShoppingTab.Hit picked = (ShoppingTab.Hit) hits.getSelectedItem();
		if (picked == null)
		{
			benefitLines = Collections.singletonList("Search for an item and pick it from the list first.");
			showResults();
			return;
		}
		benefitLines = Collections.singletonList("Looking the item up...");
		showResults();
		actions.benefit(picked.id, o -> SwingUtilities.invokeLater(() ->
		{
			benefitLines = benefitLines(o);
			showResults();
		}));
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
			out.add(r.name + ": " + what(r));
		}
		if (o.requirementsUnknown)
		{
			out.add("Level requirements for this item are not known, so \"can use it now\" only means nothing is"
				+ " known to block it.");
		}
		return out;
	}

	/** One member's verdict: what blocks them, or what the item gains over what they wear in that slot. */
	private static String what(ItemBenefit.Result r)
	{
		if (r.alreadyOwned)
		{
			return "already has an equal or better one";
		}
		if (!r.usableNow)
		{
			return !r.levelsKnown ? "levels unknown" : r.missing.isEmpty()
				? r.levelsShort + " level" + (r.levelsShort == 1 ? "" : "s") + " short" : String.join("; ", r.missing);
		}
		if (!r.gearKnown)
		{
			return "can use it now, gear unknown";
		}
		return "can use it now, +" + r.upgrade + (r.comparedTo == null ? " over current gear" : " vs " + r.comparedTo);
	}

	private static JLabel line(String text, Color color)
	{
		JLabel l = Ui.label("<html><body style='width:175px'>" + GroupIronmanPanel.escape(text) + "</body></html>",
			FontManager.getRunescapeSmallFont(), color);
		l.setBorder(new EmptyBorder(1, 0, 1, 0));
		return l;
	}
}
