package com.vinnyvangogh.groupironman;

import java.awt.Color;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;

/**
 * The Shopping tab's request cards: each member's own list, what the group
 * holds of each item and who said they will get it. The owner renames,
 * closes or removes; anyone claims. Folded like the lists (Board 2026-11-14):
 * a header with a preview until opened. The words are static so tests read
 * them.
 */
final class RequestCards
{
	private static final Color MUTED = ShopCards.MUTED;
	private static final Color DONE = ShopCards.DONE;

	private final ShoppingTab.Actions actions;
	private final ItemManager items;
	/** Shows a refusal, or clears it with null. */
	private final Consumer<String> say;
	/** Folds and item lines, shared with the lists. */
	private final ShoppingTab tab;

	RequestCards(ShoppingTab.Actions actions, ItemManager items, Consumer<String> say, ShoppingTab tab)
	{
		this.actions = actions;
		this.items = items;
		this.say = say;
		this.tab = tab;
	}

	void draw(JPanel panel, List<RequestBoard.Card> cards, String self)
	{
		if (cards.isEmpty())
		{
			return;
		}
		panel.add(GroupIronmanPanel.heading("Requests"));
		for (RequestBoard.Card c : cards)
		{
			panel.add(card(c, self));
		}
	}

	private JPanel card(RequestBoard.Card c, String self)
	{
		String key = ShopFolds.request(c.request.id);
		boolean open = tab.isOpen(key);
		List<RequestBoard.Row> needed = new ArrayList<>();
		List<RequestBoard.Row> done = new ArrayList<>();
		for (RequestBoard.Row r : c.rows)
		{
			(r.done() ? done : needed).add(r);
		}
		List<Integer> preview = new ArrayList<>();
		needed.forEach(r -> preview.add(r.itemId));
		JPanel box = ShopCards.box();
		int finished = c.request.closed ? c.rows.size() : c.done();
		ShopCards.header(box, c.request.name, open, finished, c.rows.size(), preview, items, () -> tab.fold(key));
		if (!open)
		{
			return box;
		}
		box.add(new ShopCards.Wrap(byline(c, System.currentTimeMillis()), MUTED, ShopCards.CARD_TEXT));
		boolean mine = c.request.ownedBy(self);
		if (mine)
		{
			box.add(ownerButtons(c.request));
		}
		for (RequestBoard.Row r : needed)
		{
			box.add(row(c.request, r, self, mine));
		}
		tab.addDone(box, key, done.size(), () -> done.forEach(r -> box.add(row(c.request, r, self, mine))));
		return box;
	}

	/** "Asked by Alice, 5 min ago. 1 of 2 done." or "... Done." */
	static String byline(RequestBoard.Card c, long now)
	{
		String state = c.request.closed ? "Closed." : c.allDone() ? "Done." : c.done() + " of " + c.rows.size()
			+ " done.";
		return "Asked by " + c.request.owner + ", " + BoostText.ago(now, c.request.created) + ". " + state;
	}

	/** "Shark  450 / 500". */
	static String summary(RequestBoard.Row r)
	{
		return r.name + "  " + Amounts.GAME.of(Math.min(r.have, r.wanted)) + " / " + Amounts.GAME.of(r.wanted);
	}

	/** "Need 50 more", "Done", or "Closed" when the owner closed it short. */
	static String needText(RequestBoard.Row r)
	{
		if (r.have >= r.wanted)
		{
			return "Done";
		}
		return r.closed ? "Closed" : "Need " + Amounts.GAME.of(r.wanted - r.have) + " more";
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
		JPanel words = new GroupIronmanPanel.Column();
		words.add(new ShopCards.Wrap(summary(r), r.done() ? DONE : Color.WHITE, ShopCards.ROW_TEXT));
		words.add(new ShopCards.Bar(Math.min(r.have, r.wanted), r.wanted, ShopCards.ROW_TEXT));
		words.add(new ShopCards.Wrap(needText(r), r.done() ? DONE : MUTED, ShopCards.ROW_TEXT));
		String claims = claimText(r);
		if (!claims.isEmpty())
		{
			words.add(new ShopCards.Wrap(claims, MUTED, ShopCards.ROW_TEXT));
		}
		if (req.open() && !r.done())
		{
			words.add(rowButtons(req, r, self, mine));
		}
		return tab.itemLine(r.itemId, words);
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
				s.append(summary(r)).append(needText(r)).append(claimText(r));
			}
		}
		return s.toString();
	}
}
