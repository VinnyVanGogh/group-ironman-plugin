package com.vinnyvangogh.groupironman;

import java.awt.Color;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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
	/** A GIM group has at most five members; more names than this typed in is a mistake, not a group. */
	static final int MAX_OTHER_NAMES = 5;

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
	private final JComboBox<String> memberA = new JComboBox<>();
	private final JComboBox<String> memberB = new JComboBox<>();
	private final JTextField other = new JTextField();
	private final JTextField item = new JTextField();
	private final JComboBox<ShoppingTab.Hit> hits = new JComboBox<>();
	private String mode = "Players";
	private String sort = "Name";
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
		for (String m : Arrays.asList("Players", "Leaderboard", "Group", "Item"))
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
		Object a = memberA.getSelectedItem();
		Object b = memberB.getSelectedItem();
		memberA.removeAllItems();
		memberB.removeAllItems();
		names.forEach(n ->
		{
			memberA.addItem(n);
			memberB.addItem(n);
		});
		memberA.setSelectedItem(a != null && names.contains(a) ? a : v.self);
		if (b != null && names.contains(b))
		{
			memberB.setSelectedItem(b);
		}
		else if (names.size() > 1)
		{
			memberB.setSelectedIndex(names.get(0).equals(memberA.getSelectedItem()) ? 1 : 0);
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
		results.add(Ui.wide(memberA));
		results.add(Ui.wide(memberB));
		results.add(Ui.button("Compare", "Side by side, skill by skill and boss by boss", this::showResults));
		GroupCompare.Figures a = figures(v, (String) memberA.getSelectedItem());
		GroupCompare.Figures b = figures(v, (String) memberB.getSelectedItem());
		if (a == null || b == null)
		{
			return;
		}
		results.add(line(a.name + " vs " + b.name, Color.WHITE));
		for (String s : playerLines(GroupCompare.players(a, b), a.name, b.name))
		{
			results.add(line(s, Color.WHITE));
		}
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
	static List<String> playerLines(List<GroupCompare.Row> rows, String a, String b)
	{
		List<String> out = new ArrayList<>();
		for (GroupCompare.Row r : rows)
		{
			if (r.a == null && r.b == null)
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
		JPanel sorts = new JPanel(new GridLayout(1, 3, 2, 2));
		for (String s : Arrays.asList("Name", "Best", "Worst"))
		{
			javax.swing.JToggleButton b = Ui.toggle(s, s.equals(sort), 2);
			b.addActionListener(e ->
			{
				sort = s;
				showResults();
			});
			sorts.add(b);
		}
		results.add(sorts);
		for (String s : leaderboardLines(GroupCompare.leaderboard(v.figures), sort))
		{
			results.add(line(s, Color.WHITE));
		}
	}

	/** "Magic: best GIM2, worst GIM1", ordered by {@code sort}: "Name" (as built) or by best or worst holder. */
	static List<String> leaderboardLines(List<GroupCompare.Leader> leaders, String sort)
	{
		List<GroupCompare.Leader> ordered = new ArrayList<>(leaders);
		if ("Best".equals(sort))
		{
			ordered.sort(Comparator.comparing((GroupCompare.Leader l) -> String.join(",", l.best)));
		}
		else if ("Worst".equals(sort))
		{
			ordered.sort(Comparator.comparing((GroupCompare.Leader l) -> String.join(",", l.worst)));
		}
		List<String> out = new ArrayList<>();
		for (GroupCompare.Leader l : ordered)
		{
			String worst = l.worst.isEmpty() ? "all equal" : "worst " + String.join(", ", l.worst);
			out.add(l.label + ": best " + String.join(", ", l.best) + ", " + worst);
		}
		return out;
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
