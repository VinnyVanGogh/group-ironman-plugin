package com.vinnyvangogh.groupironman;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The panel's Settings tab: the group's shared settings (editable by the
 * leader and editors, read-only for everyone else), who changed what, the
 * leader's editor list, this member's own opt-outs, and the read-only grid of
 * who posts what.
 *
 * <p>Edits go into a draft and are only sent on "Save to group". The tab is
 * rebuilt only when what it shows changed, so a half-typed number survives
 * the many redraws member stats cause.
 */
final class GroupSettingsTab
{
	/** What the tab's buttons do. */
	interface Actions
	{
		boolean claim();

		boolean setEditor(String name, boolean editor);

		GovernanceSync.SaveResult save(Map<String, Long> values);

		/** The leader's "Share webhooks with the group"; false when refused. */
		boolean shareWebhooks(boolean share);

		void forgetLeader();

		void optOut(GroupEvent.Type type, boolean overlay, boolean out);

		void optOutAll(boolean out);
	}

	/** Everything the tab draws, captured with the rest of the panel's view. */
	static final class View
	{
		final GroupGovernance.Role role;
		final GroupRoles roles;
		final GroupSettings settings;
		final boolean unclaimed;
		final boolean syncing;
		final List<String> members;
		final String self;
		/** Each shared setting's value now: the group's, or this member's own while the group has none. */
		final Map<String, Long> current;
		final MemberPrefs mine;
		final Map<String, MemberPrefs> prefs;
		/** Members whose key this client has heard: only they can be made editor. */
		final Set<String> heard;
		/** Who shares the group's webhooks (the leader), or null when nobody does. Never a URL. */
		final String webhooksBy;

		View(GroupGovernance.Role role, GroupRoles roles, GroupSettings settings, boolean unclaimed, boolean syncing,
			List<String> members, String self, Map<String, Long> current, MemberPrefs mine,
			Map<String, MemberPrefs> prefs, Set<String> heard, String webhooksBy)
		{
			this.webhooksBy = webhooksBy;
			this.role = role;
			this.roles = roles;
			this.settings = settings;
			this.unclaimed = unclaimed;
			this.syncing = syncing;
			this.members = members;
			this.self = self;
			this.current = current;
			this.mine = mine;
			this.prefs = prefs;
			this.heard = heard;
		}

		/**
		 * The view from this client's governance: {@code roster} is the group's names,
		 * {@code current} each shared setting's value now. Never a webhook URL, only who shares them.
		 */
		static View of(GroupGovernance g, String self, Collection<String> roster, Map<String, Long> current,
			MemberPrefs mine, boolean syncing)
		{
			List<String> members = gridMembers(roster, self);
			Set<String> heard = new HashSet<>();
			for (String m : members)
			{
				if (g.knownKey(m) != null)
				{
					heard.add(m);
				}
			}
			GroupWebhooks shared = g.sharedWebhooks();
			return new View(g.myRole(self), g.roles(), g.settings(), g.unclaimed(), syncing, members, self, current,
				mine, g.prefs(), heard, shared == null ? null : shared.by);
		}

		boolean canEdit()
		{
			return role != GroupGovernance.Role.MEMBER;
		}

		/**
		 * Changes when anything drawn changes; the draft is not part of it. Only
		 * decides whether to rebuild: what replaces the draft is {@link SettingsDraft#update}.
		 */
		String signature()
		{
			return String.join("|", String.valueOf(role), roles == null ? "" : roles.signature,
				settings == null ? "" : settings.signature, String.valueOf(unclaimed), String.valueOf(syncing),
				String.valueOf(members), self, String.valueOf(mine.all), String.valueOf(mine.noPost),
				String.valueOf(mine.mute), String.valueOf(prefsText()), String.valueOf(heard),
				settings == null ? String.valueOf(current) : "", String.valueOf(webhooksBy));
		}

		private String prefsText()
		{
			StringBuilder out = new StringBuilder();
			for (Map.Entry<String, MemberPrefs> e : prefs.entrySet())
			{
				out.append(e.getKey()).append(e.getValue().all).append(e.getValue().noPost);
			}
			return out.toString();
		}
	}

	private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR.darker();

	private final Actions actions;
	/** Redraws the panel; a button's result shows at once rather than at the next member update. */
	private final Runnable refresh;
	/** Section names the player opened (or closed, for those open by default). */
	private final Set<String> open = new HashSet<>();
	private final SettingsDraft draft = new SettingsDraft();
	/** The number inputs on screen: text typed into one is only in the draft once committed. */
	private final List<JSpinner> spinners = new ArrayList<>();
	private String drawn;
	private String note;

	GroupSettingsTab(Actions actions, Runnable refresh)
	{
		this.actions = actions;
		this.refresh = refresh;
	}

	/** Whether {@link #draw} would show something different from what is on screen. */
	boolean changed(View v)
	{
		return !Objects.equals(drawn, v.signature());
	}

	void draw(JPanel tab, View v, long now)
	{
		// A number typed but not yet committed would be lost with the old inputs.
		commitTyped();
		spinners.clear();
		draft.update(v.role, v.roles, v.settings, v.current);
		drawn = v.signature();
		drawHeader(tab, v, now);
		drawEditors(tab, v);
		drawWebhooks(tab, v);
		drawSettings(tab, v);
		drawHistory(tab, v, now);
		drawMine(tab, v);
		drawGrid(tab, v);
	}

	private void redraw(JPanel tab, View v)
	{
		tab.removeAll();
		drawn = null;
		draw(tab, v, System.currentTimeMillis());
		tab.revalidate();
		tab.repaint();
	}

	// ---- Leader, role, claim ----

	private void drawHeader(JPanel tab, View v, long now)
	{
		tab.add(heading("Group settings"));
		for (String line : SettingsModel.header(v.role, v.roles, v.settings, now))
		{
			tab.add(text(line, Color.WHITE));
		}
		if (!v.syncing)
		{
			tab.add(text("Join group sync to send or receive group settings.", MUTED));
		}
		if (note != null)
		{
			tab.add(text(note, ColorScheme.BRAND_ORANGE));
		}
		JPanel buttons = new JPanel(new GridLayout(0, 2, 2, 2));
		if (v.unclaimed)
		{
			buttons.add(button("Claim leadership", "Become your group's leader. Only works while your group has no"
				+ " leader; members accept it once they have heard from you.", () ->
				note = actions.claim() ? "You are the leader. Save group settings below." : "Could not claim: log in"
					+ " on your Group Ironman first."));
		}
		if (v.roles != null)
		{
			buttons.add(button("Forget leader", "This client only: forget who leads, e.g. if the leader lost their"
				+ " settings. The next claim you hear becomes your leader.", () ->
			{
				if (confirm("Forget your group's leader on this client? The next leader you hear from is trusted."))
				{
					actions.forgetLeader();
				}
			}));
		}
		tab.add(buttons);
	}

	// ---- Editors (leader only) ----

	private void drawEditors(JPanel tab, View v)
	{
		if (v.role != GroupGovernance.Role.LEADER)
		{
			return;
		}
		tab.add(heading("Editors"));
		tab.add(text("Editors can change group settings. Only you can add or remove them.", MUTED));
		for (String member : v.members)
		{
			if (GroupMembers.key(member).equals(GroupMembers.key(v.self)))
			{
				continue;
			}
			boolean editor = v.roles.editorKey(member) != null;
			boolean known = containsName(v.heard, member);
			JPanel row = new JPanel(new BorderLayout());
			row.add(text(member + (known ? "" : " (not heard from yet)"), Color.WHITE), BorderLayout.CENTER);
			JButton b = button(editor ? "Remove editor" : "Make editor", null,
				() -> note = actions.setEditor(member, !editor) ? null : "Could not change " + member + "'s role.");
			b.setEnabled(editor || known);
			row.add(b, BorderLayout.EAST);
			tab.add(row);
		}
	}

	// ---- Shared webhooks (the switch is the leader's only) ----

	private void drawWebhooks(JPanel tab, View v)
	{
		for (String line : SettingsModel.webhookLines(v.role, v.webhooksBy))
		{
			tab.add(text(line, MUTED));
		}
		if (v.role != GroupGovernance.Role.LEADER)
		{
			return;
		}
		JCheckBox box = new JCheckBox("Share webhooks with the group", v.webhooksBy != null);
		small(box);
		box.setToolTipText("Send your seven webhooks and poster names to every member, so they only tick 'Post to"
			+ " Discord'. Anyone with the group key can read them. Off, or a new group key, stops sending them.");
		box.addActionListener(e ->
		{
			boolean on = box.isSelected();
			if (on && !confirm("Send your Discord webhooks to every member? Anyone with the group key can read"
				+ " them. A member's own webhook still wins for that category."))
			{
				box.setSelected(false);
				return;
			}
			note = actions.shareWebhooks(on) ? null : "Could not change sharing: you are no longer the leader.";
			refresh.run();
		});
		tab.add(box);
	}

	// ---- The shared settings ----

	private void drawSettings(JPanel tab, View v)
	{
		if (!v.canEdit() && v.settings == null)
		{
			return;
		}
		tab.add(heading(v.canEdit() ? "Edit group settings" : "Your group's settings"));
		if (v.canEdit() && v.settings == null)
		{
			tab.add(text("Nothing saved yet: these are your own settings. Save to make them the group's.", MUTED));
		}
		if (v.canEdit() && draft.notice() != null)
		{
			tab.add(text(draft.notice(), ColorScheme.BRAND_ORANGE));
		}
		drawInputs(tab, v);
		if (v.canEdit())
		{
			JPanel buttons = new JPanel(new GridLayout(0, 2, 2, 2));
			buttons.add(button("Save to group", "Send these to every member. They replace each member's own.",
				this::save));
			buttons.add(button("Discard changes", null, () ->
			{
				draft.discard(v.current);
				note = null;
			}));
			tab.add(buttons);
		}
	}

	private void save()
	{
		commitTyped();
		GovernanceSync.SaveResult r = actions.save(new TreeMap<>(draft.values()));
		if (r == GovernanceSync.SaveResult.SAVED || r == GovernanceSync.SaveResult.SAVED_OFFLINE
			|| r == GovernanceSync.SaveResult.UNCHANGED)
		{
			draft.saved();
		}
		note = saveNote(r);
	}

	/** What the tab says after "Save to group": each refusal names its reason. */
	static String saveNote(GovernanceSync.SaveResult r)
	{
		switch (r)
		{
			case SAVED:
				return "Saved and sent to your group.";
			case SAVED_OFFLINE:
				return "Saved. It goes to your group when you join group sync.";
			case UNCHANGED:
				return "Nothing to save: these are already your group's settings.";
			case NOT_EDITOR:
				return "Not saved: you are no longer leader or editor.";
			case NOT_LOGGED_IN:
				return "Not saved: log in on your Group Ironman first.";
			default:
				return "Not saved: group settings are still starting. Try again in a moment.";
		}
	}

	/** Text typed into a number input counts once it parses; the Save button never takes focus to commit it. */
	private void commitTyped()
	{
		for (JSpinner s : spinners)
		{
			try
			{
				s.commitEdit();
			}
			catch (java.text.ParseException e)
			{
				// Not a number: the input keeps its last good value.
			}
		}
	}

	/** One collapsible section per schema section; only "What counts" starts open. */
	private void drawInputs(JPanel tab, View v)
	{
		String section = null;
		JPanel body = null;
		for (GroupSettingsSchema.Setting s : GroupSettingsSchema.ALL)
		{
			if (!s.section.equals(section))
			{
				section = s.section;
				body = collapsible(tab, section, !section.equals(GroupSettingsSchema.THRESHOLDS), v);
			}
			if (body != null)
			{
				body.add(input(s, v.canEdit()));
			}
		}
	}

	private JComponent input(GroupSettingsSchema.Setting s, boolean editable)
	{
		long value = draft.values().getOrDefault(s.key, 0L);
		if (s.flag)
		{
			JCheckBox box = new JCheckBox(s.label, value != 0);
			small(box);
			box.setEnabled(editable);
			box.addActionListener(e -> draft.put(s.key, box.isSelected() ? 1L : 0L));
			return box;
		}
		JPanel row = new JPanel(new BorderLayout());
		row.add(text(s.label, Color.WHITE), BorderLayout.CENTER);
		JSpinner spinner = new JSpinner(new SpinnerNumberModel(Long.valueOf(value), Long.valueOf(s.min),
			Long.valueOf(s.max), Long.valueOf(1)));
		spinner.setEnabled(editable);
		spinner.addChangeListener(e -> draft.put(s.key, ((Number) spinner.getValue()).longValue()));
		if (editable)
		{
			spinners.add(spinner);
		}
		row.add(spinner, BorderLayout.EAST);
		return row;
	}

	private void drawHistory(JPanel tab, View v, long now)
	{
		List<String> lines = SettingsModel.history(v.settings, now);
		if (lines.isEmpty())
		{
			return;
		}
		tab.add(heading("Recent changes"));
		for (String line : lines)
		{
			tab.add(text(line, MUTED));
		}
	}

	// ---- This member's own ----

	private void drawMine(JPanel tab, View v)
	{
		tab.add(heading("My events"));
		tab.add(text("Yours alone; the group cannot change these. Posting is your own events to Discord; showing is"
			+ " banners on your screen.", MUTED));
		JCheckBox all = new JCheckBox("Don't post my events", v.mine.all);
		small(all);
		all.addActionListener(e -> actions.optOutAll(all.isSelected()));
		tab.add(all);
		JPanel body = collapsible(tab, "Per event kind", true, v);
		if (body == null)
		{
			return;
		}
		for (GroupEvent.Type t : GroupEvent.Type.values())
		{
			JPanel row = new JPanel(new BorderLayout());
			row.add(text(EventText.title(t), Color.WHITE), BorderLayout.CENTER);
			JPanel boxes = new JPanel(new GridLayout(1, 2));
			boxes.add(optBox("Post", !v.mine.noPost.contains(t) && !v.mine.all, !v.mine.all,
				on -> actions.optOut(t, false, !on)));
			boxes.add(optBox("Show", !v.mine.mute.contains(t), true, on -> actions.optOut(t, true, !on)));
			row.add(boxes, BorderLayout.EAST);
			body.add(row);
		}
	}

	private static JCheckBox optBox(String label, boolean on, boolean enabled, java.util.function.Consumer<Boolean> set)
	{
		JCheckBox box = new JCheckBox(label, on);
		small(box);
		box.setEnabled(enabled);
		box.addActionListener(e -> set.accept(box.isSelected()));
		return box;
	}

	// ---- Who posts what (leader and editors, read-only) ----

	private void drawGrid(JPanel tab, View v)
	{
		if (!v.canEdit())
		{
			return;
		}
		JPanel body = collapsible(tab, "Who posts what", true, v);
		if (body == null)
		{
			return;
		}
		body.add(text("Each member's own choice; only they can change it. ? = not heard from yet.", MUTED));
		Map<String, MemberPrefs> prefs = new TreeMap<>(v.prefs);
		prefs.put(v.self, v.mine);
		SettingsModel.Grid grid = SettingsModel.grid(v.members, prefs);
		body.add(gridRow("", grid.members, Function.identity()));
		for (SettingsModel.GridRow r : grid.rows)
		{
			body.add(gridRow(r.label, r.cells, GroupSettingsTab::cell));
		}
	}

	private static <T> JComponent gridRow(String label, List<T> cells, Function<T, String> show)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.add(text(label, Color.WHITE), BorderLayout.CENTER);
		JPanel right = new JPanel(new GridLayout(1, Math.max(1, cells.size()), 2, 0));
		for (T c : cells)
		{
			JLabel l = new JLabel(show.apply(c));
			l.setFont(FontManager.getRunescapeSmallFont());
			l.setForeground(Color.WHITE);
			right.add(l);
		}
		row.add(right, BorderLayout.EAST);
		return row;
	}

	private static String cell(SettingsModel.Cell c)
	{
		switch (c)
		{
			case IN:
				return "yes";
			case OUT:
				return "no";
			default:
				return "?";
		}
	}

	// ---- Building blocks ----

	/** A section header that opens and closes; the body to fill, or null while closed. */
	private JPanel collapsible(JPanel tab, String name, boolean closedByDefault, View v)
	{
		boolean shown = closedByDefault ? open.contains(name) : !open.contains(name);
		JPanel parent = tab;
		JButton header = new JButton((shown ? "- " : "+ ") + name);
		header.setFont(FontManager.getRunescapeBoldFont());
		header.setForeground(ColorScheme.BRAND_ORANGE);
		header.setFocusable(false);
		header.setContentAreaFilled(false);
		header.setBorderPainted(false);
		header.setHorizontalAlignment(JButton.LEFT);
		header.addActionListener(e ->
		{
			if (!open.remove(name))
			{
				open.add(name);
			}
			redraw(parent, v);
		});
		tab.add(header);
		if (!shown)
		{
			return null;
		}
		JPanel body = new GroupIronmanPanel.Column();
		tab.add(body);
		return body;
	}

	private JButton button(String label, String tooltip, Runnable action)
	{
		JButton b = new JButton(label);
		b.setFont(FontManager.getRunescapeSmallFont());
		b.setFocusable(false);
		b.setToolTipText(tooltip);
		b.addActionListener(e ->
		{
			action.run();
			drawn = null;
			refresh.run();
		});
		return b;
	}

	private static boolean confirm(String question)
	{
		return JOptionPane.showConfirmDialog(null, question, "GIM Utilities", JOptionPane.YES_NO_OPTION)
			== JOptionPane.YES_OPTION;
	}

	private static boolean containsName(Set<String> names, String name)
	{
		String key = GroupMembers.key(name);
		for (String n : names)
		{
			if (GroupMembers.key(n).equals(key))
			{
				return true;
			}
		}
		return false;
	}

	private static void small(JComponent c)
	{
		c.setFont(FontManager.getRunescapeSmallFont());
		c.setForeground(Color.WHITE);
	}

	private static JLabel heading(String text)
	{
		return GroupIronmanPanel.heading(text);
	}

	private static JLabel text(String text, Color color)
	{
		return GroupIronmanPanel.text(text, color);
	}

	/** Names as the grid lists them: the roster, or this player alone before it is known. */
	static List<String> gridMembers(Collection<String> roster, String self)
	{
		List<String> out = new ArrayList<>(roster);
		if (out.isEmpty() && !self.isEmpty())
		{
			out.add(self);
		}
		return out;
	}
}
