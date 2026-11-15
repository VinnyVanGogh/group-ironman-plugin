package com.vinnyvangogh.groupironman;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The Shopping tab's request cards: each member's own list, what the group
 * holds of each item and who said they will get it. The owner renames,
 * closes or removes; anyone claims. The words are static so tests read them.
 */
final class RequestCards
{
	private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR.darker();
	private static final Color DONE = ColorScheme.PROGRESS_COMPLETE_COLOR;

	private final ShoppingTab.Actions actions;
	private final ItemManager items;
	/** Shows a refusal, or clears it with null. */
	private final Consumer<String> say;

	RequestCards(ShoppingTab.Actions actions, ItemManager items, Consumer<String> say)
	{
		this.actions = actions;
		this.items = items;
		this.say = say;
	}

	void draw(JPanel tab, List<RequestBoard.Card> cards, String self)
	{
		if (cards.isEmpty())
		{
			return;
		}
		tab.add(GroupIronmanPanel.heading("Requests"));
		for (RequestBoard.Card c : cards)
		{
			tab.add(card(c, self));
		}
	}

	private JPanel card(RequestBoard.Card c, String self)
	{
		JPanel box = new GroupIronmanPanel.Column();
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setBorder(BorderFactory.createCompoundBorder(new EmptyBorder(0, 0, 6, 0), new EmptyBorder(6, 6, 6, 6)));
		JLabel title = new JLabel(GroupIronmanPanel.escape(c.request.name));
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(c.allDone() ? DONE : Color.WHITE);
		box.add(title);
		box.add(GroupIronmanPanel.text(byline(c, System.currentTimeMillis()), MUTED));
		boolean mine = c.request.ownedBy(self);
		if (mine)
		{
			box.add(ownerButtons(c.request));
		}
		for (RequestBoard.Row r : c.rows)
		{
			box.add(row(c.request, r, self, mine));
		}
		return box;
	}

	/** "Asked by Alice, 5 min ago. 1 of 2 done." or "... Done." */
	static String byline(RequestBoard.Card c, long now)
	{
		String state = c.request.closed ? "Closed." : c.allDone() ? "Done." : c.done() + " of " + c.rows.size()
			+ " done.";
		return "Asked by " + c.request.owner + ", " + BoostText.ago(now, c.request.created) + ". " + state;
	}

	/** "Shark: want 500, group has 450, 50 to go" or "Shark: done (500)". */
	static String rowText(RequestBoard.Row r)
	{
		String want = Amounts.GAME.of(r.wanted);
		if (r.done())
		{
			return r.name + ": done (" + want + ")";
		}
		return r.name + ": want " + want + ", group has " + Amounts.GAME.of(r.have) + ", "
			+ Amounts.GAME.of(r.wanted - r.have) + " to go";
	}

	/** "Bob and Cat will get it"; empty when nobody said. */
	static String claimText(RequestBoard.Row r)
	{
		if (r.claimedBy.isEmpty())
		{
			return "";
		}
		List<String> names = r.claimedBy;
		String who = names.size() == 1 ? names.get(0)
			: String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
		return who + " will get it";
	}

	private JPanel row(RequestList req, RequestBoard.Row r, String self, boolean mine)
	{
		JPanel line = new JPanel(new BorderLayout(4, 0));
		line.setOpaque(false);
		line.setBorder(new EmptyBorder(3, 0, 3, 0));
		JLabel icon = new JLabel();
		if (items != null)
		{
			items.getImage(r.itemId).addTo(icon);
		}
		line.add(icon, BorderLayout.WEST);
		JPanel words = new GroupIronmanPanel.Column();
		words.setOpaque(false);
		words.add(ShoppingTab.label(rowText(r), r.done() ? DONE : Color.WHITE));
		String claims = claimText(r);
		if (!claims.isEmpty())
		{
			words.add(ShoppingTab.label(claims, MUTED));
		}
		if (req.open() && !r.done())
		{
			words.add(rowButtons(req, r, self, mine));
		}
		line.add(words, BorderLayout.CENTER);
		return line;
	}

	private JPanel rowButtons(RequestList req, RequestBoard.Row r, String self, boolean mine)
	{
		JPanel buttons = new JPanel(new GridLayout(1, 0, 2, 0));
		buttons.setOpaque(false);
		boolean claimed = claimedBy(r, self);
		buttons.add(ShoppingTab.button(claimed ? "Not getting it" : "I'll get it", () -> say.accept(
			actions.claimRequest(req.id, r.itemId, !claimed) ? null : "Not changed: log in first.")));
		if (mine)
		{
			buttons.add(ShoppingTab.button("Close item", () -> say.accept(
				actions.closeRequestItem(req.id, r.itemId) ? null : "Not closed: log in first.")));
		}
		return buttons;
	}

	static boolean claimedBy(RequestBoard.Row r, String self)
	{
		for (String m : r.claimedBy)
		{
			if (GroupMembers.key(m).equals(GroupMembers.key(self)))
			{
				return true;
			}
		}
		return false;
	}

	private JPanel ownerButtons(RequestList r)
	{
		JPanel row = new JPanel(new GridLayout(1, 0, 2, 0));
		row.setOpaque(false);
		row.add(ShoppingTab.button("Rename", () ->
		{
			String name = JOptionPane.showInputDialog(row, "New name for '" + r.name + "'", r.name);
			if (name != null)
			{
				say.accept(actions.renameRequest(r.id, name) ? null : "Not renamed: log in, and give it a new name.");
			}
		}));
		if (r.open())
		{
			row.add(ShoppingTab.button("Close", () -> say.accept(actions.closeRequest(r.id) ? null
				: "Not closed: log in first.")));
		}
		row.add(ShoppingTab.button("Remove", () ->
		{
			if (JOptionPane.showConfirmDialog(row, "Remove your request '" + r.name + "'?", "Remove request",
				JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION)
			{
				say.accept(actions.removeRequest(r.id) ? null : "Not removed: log in first.");
			}
		}));
		return row;
	}

	/** Changes when anything a request card draws changes. */
	static String signature(List<RequestBoard.Card> cards, String self)
	{
		StringBuilder s = new StringBuilder(self);
		for (RequestBoard.Card c : cards)
		{
			s.append('|').append(c.request.id).append(c.request.rev);
			for (RequestBoard.Row r : c.rows)
			{
				s.append(rowText(r)).append(claimText(r));
			}
		}
		return s.toString();
	}
}
