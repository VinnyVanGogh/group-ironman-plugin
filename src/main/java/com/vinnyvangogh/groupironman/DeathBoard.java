package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * The deaths message's board (Board 2026-11-19): a member's latest death keeps its full card, and the deaths before
 * it fold into one "Recent deaths" card, one line each with the time. The time is a Discord timestamp, so each
 * reader sees their own clock. Pure; {@link EventPosts} folds when it builds the message.
 */
final class DeathBoard
{
	/** The {@link EventThreads#family} of deaths. */
	static final String FAMILY = "DEATHS";
	/** Older deaths listed, the newest first. */
	static final int MAX_ROWS = 10;
	static final String TITLE = "Recent deaths";

	private DeathBoard()
	{
	}

	/** One death's board line from its moment's events, or null when they hold no death. */
	static String row(List<GroupEvent> events, ItemLookup lookup)
	{
		GroupEvent first = null;
		String where = "";
		String killer = "";
		String lost = "";
		for (GroupEvent e : events)
		{
			if (e.type != GroupEvent.Type.DEATH && e.type != GroupEvent.Type.DEATH_VALUE
				&& e.type != GroupEvent.Type.ACTIVITY_DEATH)
			{
				continue;
			}
			first = first == null ? e : first;
			where = where.isEmpty() ? (e.type == GroupEvent.Type.ACTIVITY_DEATH ? e.text : e.location) : where;
			killer = e.type == GroupEvent.Type.DEATH && killer.isEmpty() ? e.text : killer;
			lost = e.type == GroupEvent.Type.DEATH_VALUE && e.number > 0 ? lookup.amount(e.number) + " gp" : lost;
		}
		if (first == null)
		{
			return null;
		}
		StringBuilder row = new StringBuilder("<t:").append(first.at / 1000).append(":t> ").append(first.member);
		append(row, where);
		append(row, killer.isEmpty() ? "" : "killed by " + killer);
		append(row, lost.isEmpty() ? "" : "lost " + lost);
		return row.toString();
	}

	private static void append(StringBuilder row, String part)
	{
		if (!part.isEmpty())
		{
			row.append(" · ").append(part);
		}
	}

	/**
	 * The cards as the message shows them, newest first: the latest card, then one board card for the rest. A
	 * card saved before the board has no row and reads as its plain line, or is left out. Fewer than two cards: as is.
	 */
	static List<EventThreads.Card> fold(Gson gson, List<EventThreads.Card> cards)
	{
		if (cards.size() < 2)
		{
			return cards;
		}
		List<String> rows = new ArrayList<>();
		for (EventThreads.Card c : cards.subList(1, cards.size()))
		{
			String row = c.row != null ? c.row : c.line;
			if (row != null && rows.size() < MAX_ROWS)
			{
				rows.add(row);
			}
		}
		if (rows.isEmpty())
		{
			return cards.subList(0, 1);
		}
		String text = String.join("\n", rows);
		JsonObject embed = new JsonObject();
		embed.addProperty("title", TITLE);
		embed.addProperty("description", text);
		JsonObject newest = cards.get(0).embed == null ? null : gson.fromJson(cards.get(0).embed, JsonObject.class);
		if (newest != null && newest.has("color"))
		{
			embed.add("color", newest.get("color"));
		}
		List<EventThreads.Card> out = new ArrayList<>();
		out.add(cards.get(0));
		out.add(new EventThreads.Card(gson.toJson(embed), TITLE + ":\n" + text));
		return out;
	}
}
