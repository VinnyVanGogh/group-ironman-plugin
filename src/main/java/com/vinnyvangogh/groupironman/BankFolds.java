package com.vinnyvangogh.groupironman;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Which bank cards in the Banks tab are open (Board 2026-11-13). Every bank
 * starts open and the player folds them from the header, like the Group tab's
 * member cards. While the bank filter searches, banks with a match show open
 * and the rest fold to their header; the player can still flip one for that
 * filter, and clearing the filter brings back what they had chosen. Held in
 * memory only, like the Group tab: nothing is saved.
 */
final class BankFolds
{
	/** Banks the player folded with no filter, by member. */
	private final Set<String> folded = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
	/** Banks flipped while the current filter searches: member to open. Cleared when the filter changes. */
	private final Map<String, Boolean> flipped = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	private String query = "";

	/** Call before reading a draw's state, with the bank filter's text. */
	void filter(String text)
	{
		ItemQuery q = ItemQuery.parse(text);
		String now = q == null ? "" : ItemQuery.normalize(text);
		if (!now.equals(query))
		{
			flipped.clear();
			query = now;
		}
	}

	/** Whether {@code member}'s bank is drawn open; {@code matches} is whether it has an item the filter finds. */
	boolean open(String member, boolean matches)
	{
		if (query.isEmpty())
		{
			return !folded.contains(member);
		}
		Boolean flip = flipped.get(member);
		return flip != null ? flip : matches;
	}

	/** The header was clicked: fold an open bank, open a folded one. */
	void toggle(String member, boolean matches)
	{
		set(member, !open(member, matches));
	}

	/** Collapse all ({@code open} false) or Expand all. */
	void setAll(Collection<String> members, boolean open)
	{
		for (String m : members)
		{
			set(m, open);
		}
	}

	/** Whether any bank is drawn open, so the link offers Collapse all rather than Expand all. */
	boolean anyOpen(Collection<String> members, Predicate<String> matches)
	{
		for (String m : members)
		{
			if (open(m, matches.test(m)))
			{
				return true;
			}
		}
		return false;
	}

	private void set(String member, boolean open)
	{
		if (!query.isEmpty())
		{
			flipped.put(member, open);
		}
		else if (open)
		{
			folded.remove(member);
		}
		else
		{
			folded.add(member);
		}
	}
}
