package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A member's other Discord posts grouped like their level-ups (Board 2026-11-15: "everything needs to do that"): per
 * member and per kind of event ({@link #family}), the first moment posts and later ones within the window edit that
 * same message. Most kinds add an embed card each, the newest on top, up to {@link #MAX_CARDS} (Discord's limit on
 * embeds); the next moment past that starts a new message. Group storage visits net out instead: one card, the items
 * moved summed per item over every visit ("took 2 x Rope", then "deposited 10 x Rope", reads "deposited 8 x Rope"),
 * with the list of visits under it.
 *
 * <p>A moment that pings the configured mention never joins: it starts a message of its own, so the ping goes
 * (an edit never pings). Posting, editing, refusals and the restart file are {@link EditedThreads}'; a saved line is
 * one JSON object.
 */
final class EventThreads extends EditedThreads<EventThreads.Feed, EventThreads.Edit>
{
	/** Group storage's family: its moments net out into one card. */
	static final String STORAGE = "STORAGE";
	/** Visits listed under a storage card, newest first; Discord caps the field anyway ({@link DiscordMerge#MAX_FIELD}). */
	static final int MAX_MOVES = 25;
	/** Marks a saved line of this format. */
	private static final int VERSION = 1;

	private final Gson gson;

	EventThreads(Gson gson)
	{
		this.gson = gson;
	}

	/**
	 * Which message a kind of event groups into, or null for a kind that always posts on its own: online and offline
	 * (they have their own once-per-10-minutes rule), the gravestone warning (a timer that must notify) and the
	 * weekly recap (a summary already). Everything else groups by its category, with slayer tasks on their own.
	 */
	static String family(GroupEvent.Type type)
	{
		switch (type)
		{
			case ONLINE:
			case BACK:
			case GRAVESTONE_EXPIRING:
			case WEEKLY_RECAP:
			case ANNOUNCEMENT:
			case EVERYONE_ONLINE:
				return null;
			case SLAYER:
				return "SLAYER";
			default:
				return type.category.name();
		}
	}

	/** Most cards a message keeps: the deaths' latest full card and its board rows, else Discord's embed limit. */
	static int limit(String family)
	{
		return DeathBoard.FAMILY.equals(family) ? 1 + DeathBoard.MAX_ROWS : MAX_CARDS;
	}

	/** The facts of a moment's lead event a storage card is drawn from: whose, when, the world and place. */
	static final class Lead
	{
		final GroupEvent.Type type;
		final long at;
		final String member;
		final int world;
		final String location;
		final boolean broadcast;

		Lead(GroupEvent e)
		{
			this.type = e.type;
			this.at = e.at;
			this.member = e.member;
			this.world = e.world;
			this.location = e.location;
			this.broadcast = e.broadcast;
		}

		GroupEvent event()
		{
			return GroupEvent.of(type, at, member).world(world).location(location).broadcast(broadcast).build();
		}
	}

	/** One card of a message: the moment's embed, and its line for plain style. */
	static final class Card
	{
		final String embed;
		final String line;
		/** A death's line on the {@link DeathBoard}; null for every other card and for one saved before the board. */
		final String row;

		Card(String embed, String line)
		{
			this(embed, line, null);
		}

		Card(String embed, String line, String row)
		{
			this.embed = embed;
			this.line = line;
			this.row = row;
		}
	}

	/** One moment going to Discord, already worded: what it adds to its member's message. */
	static final class Moment
	{
		final String member;
		final String family;
		final String url;
		/** The poster name its first post goes under; a replacement post uses it too. Null for the webhook's own. */
		final String username;
		/** It pings the configured mention: it never joins a message, so the ping goes. */
		final boolean pings;
		final Card card;
		/** Storage: what the visit took and deposited; empty otherwise. */
		final Map<Integer, Integer> taken;
		final Map<Integer, Integer> deposited;
		/** Storage: the visit's line in the list of visits ("took 2 x Rope"). */
		final String move;
		final Lead lead;
		/** Storage: the visit's other facts (its minimum and last-one lines), {title, line} each. */
		final List<String[]> extras;

		Moment(String family, String url, String username, boolean pings, Card card, GroupEvent lead,
			List<String[]> extras, String move)
		{
			this.member = lead.member;
			this.family = family;
			this.url = url;
			this.username = username;
			this.pings = pings;
			this.card = card;
			boolean visit = lead.type == GroupEvent.Type.STORAGE_SUMMARY;
			this.taken = visit ? lead.taken : Collections.emptyMap();
			this.deposited = visit ? lead.items : Collections.emptyMap();
			this.move = move;
			this.lead = new Lead(lead);
			this.extras = extras;
		}
	}

	/** One member's message for one family. Read and written only under the owner's lock. */
	static final class Feed extends EditedThreads.Message
	{
		final String family;
		String username;
		/** Its cards, the newest first. Storage keeps none: its one card is drawn from {@link #net}. */
		final List<Card> cards = new ArrayList<>();
		/** Storage: each item's amount over every visit, deposits positive; zeros are dropped. */
		final Map<Integer, Long> net = new TreeMap<>();
		/** Storage: each visit's line, the newest first. */
		final List<String> moves = new ArrayList<>();
		Lead lead;
		List<String[]> extras = Collections.emptyList();

		private Feed(Moment m, long now, byte[] png)
		{
			super(m.member, m.url, now, png);
			this.family = m.family;
		}

		private Feed(Saved s)
		{
			super(s.hook, s.member, s.first, s.last, s.id);
			this.family = s.family;
			this.username = s.username;
			cards.addAll(s.cards);
			net.putAll(s.net);
			moves.addAll(s.moves);
			this.lead = s.lead;
			this.extras = s.extras;
		}

		boolean storage()
		{
			return STORAGE.equals(family);
		}

		/** Room for one more moment: storage always has it, others while under {@link #limit}. */
		private boolean fits()
		{
			return storage() || rolls() || cards.size() < limit(family);
		}

		/** The deaths' board rolls: the oldest row drops off, the message goes on. */
		private boolean rolls()
		{
			return DeathBoard.FAMILY.equals(family);
		}

		private void add(Moment m)
		{
			username = m.username;
			lead = m.lead;
			extras = m.extras;
			if (!storage())
			{
				cards.add(0, m.card);
				if (rolls() && cards.size() > limit(family))
				{
					cards.subList(limit(family), cards.size()).clear();
				}
				return;
			}
			m.taken.forEach((id, n) -> net.merge(id, -(long) n, Long::sum));
			m.deposited.forEach((id, n) -> net.merge(id, (long) n, Long::sum));
			net.values().removeIf(n -> n == 0);
			if (m.move != null && !m.move.isEmpty())
			{
				moves.add(0, m.move);
				if (moves.size() > MAX_MOVES)
				{
					moves.subList(MAX_MOVES, moves.size()).clear();
				}
			}
		}
	}

	/** What one post or edit of a message says, frozen when it is handed out: safe to read off the lock. */
	static final class Edit
	{
		final Feed feed;
		final String url;
		final String messageId;
		final String username;
		final boolean storage;
		/** Copies of the cards: newest first, or oldest first when {@link #chronological}. */
		final List<Card> cards;
		/** The cards read in the order things happened (a Slayer "task done" above the "new task" after it). */
		final boolean chronological;
		final Map<Integer, Long> net;
		final List<String> moves;
		final Lead lead;
		final List<String[]> extras;
		final byte[] png;

		private Edit(Feed f)
		{
			this.feed = f;
			this.url = f.url;
			this.messageId = f.messageId;
			this.username = f.username;
			this.storage = f.storage();
			this.chronological = "SLAYER".equals(f.family);
			List<Card> shown = new ArrayList<>(f.cards.subList(0, Math.min(limit(f.family), f.cards.size())));
			if (chronological)
			{
				Collections.reverse(shown);
			}
			this.cards = Collections.unmodifiableList(shown);
			this.net = Collections.unmodifiableMap(new TreeMap<>(f.net));
			this.moves = Collections.unmodifiableList(new ArrayList<>(f.moves));
			this.lead = f.lead;
			this.extras = f.extras;
			this.png = f.png;
		}
	}

	/** A {@link Feed} as its saved line holds it. */
	private static final class Saved
	{
		int v;
		String hook;
		String member;
		String family;
		long first;
		long last;
		String id;
		String username;
		List<Card> cards = new ArrayList<>();
		Map<Integer, Long> net = new TreeMap<>();
		List<String> moves = new ArrayList<>();
		Lead lead;
		List<String[]> extras = new ArrayList<>();

		/** Written by this format, with a message to edit and what its card is drawn from. */
		boolean complete()
		{
			return v == VERSION && hook != null && member != null && family != null && id != null && !id.isEmpty()
				&& lead != null && lead.type != null && lists();
		}

		/** Gson leaves a list null only for a line that names it null: never one of ours. */
		private boolean lists()
		{
			return cards != null && net != null && moves != null && extras != null;
		}
	}

	/**
	 * One moment into its member's message for its family: joined when that message is open on the same webhook,
	 * within {@code window} ms of its first post, has room, and the moment does not ping (null back; {@link #due}
	 * edits it in time). Otherwise a new message, its first post returned: send it and report back to
	 * {@link #posted} with {@link Edit#feed}.
	 */
	synchronized Edit add(Moment m, byte[] png, long now, long window)
	{
		Feed f = open.get(key(m.member, m.family));
		if (f != null && !m.pings && f.open(m.url, now, window) && f.fits())
		{
			f.add(m);
			f.grew(m.url, now, png);
			changed = true;
			return null;
		}
		Feed fresh = new Feed(m, now, png);
		fresh.add(m);
		return start(fresh);
	}

	private static String key(String member, String family)
	{
		return family + '\t' + member;
	}

	@Override
	String key(Feed f)
	{
		return key(f.member, f.family);
	}

	@Override
	Edit snapshot(Feed f)
	{
		return new Edit(f);
	}

	@Override
	String line(Feed f)
	{
		Saved s = new Saved();
		s.v = VERSION;
		s.hook = f.hook;
		s.member = f.member;
		s.family = f.family;
		s.first = f.first;
		s.last = f.last;
		s.id = f.messageId;
		s.username = f.username;
		s.cards = f.cards;
		s.net = f.net;
		s.moves = f.moves;
		s.lead = f.lead;
		s.extras = f.extras;
		// Gson escapes every newline inside a string, so one message is always one line.
		return gson.toJson(s);
	}

	@Override
	Feed parse(String line)
	{
		try
		{
			Saved s = gson.fromJson(line, Saved.class);
			return s != null && s.complete() ? new Feed(s) : null;
		}
		catch (JsonParseException | IllegalStateException | NumberFormatException e)
		{
			return null;
		}
	}
}
