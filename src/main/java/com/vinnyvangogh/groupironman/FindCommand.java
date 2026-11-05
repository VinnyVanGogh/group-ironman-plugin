package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code ::find <item>} answer: who holds a matching item now (the
 * panel's "Who has it?") and who last moved it in or out of group storage.
 *
 * <p>{@code ::} rather than {@code !}: RuneLite's {@code ChatInputManager}
 * turns {@code ::} text into a {@code CommandExecuted} event and sends
 * nothing to the game, while a {@code !} command is only seen after the
 * message has gone out to public chat. The answer is shown to this player
 * only, as game messages.
 *
 * <p>Pure, so the text can be tested without a client.
 */
final class FindCommand
{
	static final String NAME = "find";
	static final int MAX_LINES = 5;
	static final int HISTORY_LIMIT = 3;

	private FindCommand()
	{
	}

	static List<String> answer(String query, List<GroupSearch.Source> sources, List<StorageChange> log,
		ItemLookup items, long now)
	{
		List<String> out = new ArrayList<>();
		String q = ItemQuery.normalize(query);
		if (ItemQuery.parse(q) == null)
		{
			out.add("Type at least " + GroupSearch.MIN_QUERY + " letters of an item name, e.g. ::find dragon hunter wand");
			return out;
		}

		List<String> lines = new ArrayList<>(GroupSearch.search(q, sources, items, now));
		lines.addAll(GroupSearch.history(q, log, items, now, HISTORY_LIMIT));
		if (lines.isEmpty())
		{
			out.add("Nothing found for '" + plain(query.trim()) + "'. Only members running GIM Utilities with sharing"
				+ " on are tracked, as of their last sync; trades and drops between members are not.");
			return out;
		}
		for (int i = 0; i < lines.size() && i < MAX_LINES; i++)
		{
			out.add(lines.get(i));
		}
		if (lines.size() > MAX_LINES)
		{
			out.add((lines.size() - MAX_LINES) + " more, see the GIM Utilities panel");
		}
		return out;
	}

	/** The query as typed, without anything the chatbox would read as a tag. */
	static String plain(String text)
	{
		return text.replace("<", "").replace(">", "");
	}
}
