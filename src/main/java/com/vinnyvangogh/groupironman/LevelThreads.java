package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * A member's level-ups, one Discord message (Board 2026-11-14, final design): the message holds one embed card per
 * skill, and each card is edited in place on that skill's next level ("Thieving 44 → 46 (+2) in 10 min"). A new
 * skill adds a card; combat level is a card of its own. Cards are ordered by the most recent level-up first. A
 * message holds at most {@link #MAX_CARDS} cards (Discord's limit on embeds); a skill past that starts a new message.
 *
 * <p>The first level-up of a member posts and keeps the message's id; more levels within the window (counted from
 * the message's first post) mark it for an edit. {@link #due} hands out at most one edit per message every
 * {@link #EDIT_GAP}, so levels quicker than that fold into the next edit. An edit Discord refuses (the message
 * deleted, the webhook changed) is posted anew and that post is the one kept from then on. A logout ends every
 * streak, but an edit already owed still goes. Pure and clock-driven.
 *
 * <p>Open streaks with a message survive a client restart (Board 2026-11-14): {@link #saved} hands out one line
 * per streak when they change, keyed by the webhook's id from its URL ({@link StatusBoard#hookOf}, never the URL
 * or its token), with every card's state, and {@link #restore} takes them back, dropping any past the window. A
 * restored streak is joined only through the same webhook; a different one, or an edit Discord refuses, posts
 * anew as before.
 */
final class LevelThreads
{
	/** At most one edit per message this often. */
	static final long EDIT_GAP = TimeUnit.SECONDS.toMillis(10);
	/** Edits in a row a server or network failure may cost before the streak gives up and posts fresh. */
	static final int MAX_EDIT_FAILURES = 3;
	/** Cards in one message: Discord takes at most 10 embeds in a message. */
	static final int MAX_CARDS = 10;
	private static final int MAX_LEVEL = 99;
	/** A combat level's card name: "Combat 99 → 101 (+2) in 10 min". */
	static final String COMBAT = "Combat";
	private static final String SEP = "\t";
	/** Marks a saved line of this format; a line saved by an earlier build is dropped. */
	private static final String VERSION = "L2";
	/** version, hook, member, first, last, message id; then each card's {@link Card#fields}. */
	private static final int HEAD_FIELDS = 6;
	private static final int CARD_FIELDS = 7;

	/** One skill's card in a message: its levels from the level before its first to its latest, and when. */
	static final class Card
	{
		final String skill;
		final long from;
		long to;
		final long firstAt;
		long lastAt;
		/** Level-ups it holds: one reads as the plain line, more as the span. */
		int ups;
		/** Read from the game's group broadcast: the footer says so. */
		boolean broadcast;

		private Card(String skill, long from, long to, long at, boolean broadcast)
		{
			this.skill = skill;
			this.from = from;
			this.to = to;
			this.firstAt = at;
			this.lastAt = at;
			this.ups = 1;
			this.broadcast = broadcast;
		}

		/** A saved card ({@link #fields}). */
		private Card(String[] f, int i)
		{
			if (f[i].isEmpty())
			{
				throw new NumberFormatException("no skill");
			}
			this.skill = f[i];
			this.from = Long.parseLong(f[i + 1]);
			this.to = Long.parseLong(f[i + 2]);
			this.firstAt = Long.parseLong(f[i + 3]);
			this.lastAt = Long.parseLong(f[i + 4]);
			this.ups = Integer.parseInt(f[i + 5]);
			this.broadcast = "1".equals(f[i + 6]);
		}

		private Card copy()
		{
			Card c = new Card(skill, from, to, firstAt, broadcast);
			c.lastAt = lastAt;
			c.ups = ups;
			return c;
		}

		boolean combat()
		{
			return COMBAT.equals(skill);
		}

		/** "Thieving level 45" for its first level; then "Thieving 44 → 46 (+2) in 10 min". */
		String text()
		{
			if (ups <= 1)
			{
				return combat() ? "combat level " + to : skill + " level " + to;
			}
			return LevelThreads.text(skill, from, to, lastAt - firstAt);
		}

		private void fields(List<String> f)
		{
			Collections.addAll(f, skill, Long.toString(from), Long.toString(to), Long.toString(firstAt),
				Long.toString(lastAt), Integer.toString(ups), broadcast ? "1" : "0");
		}
	}

	/** One member's message and its cards. Read and written only under the owner's lock. */
	static final class Streak
	{
		final String member;
		/** The webhook it posts to ({@link LevelThreads#tag}): what a level-up must match to join. Never null. */
		final String hook;
		/** The latest URL it was joined through; null for a restored streak until a level-up joins it. */
		String url;
		final long first;
		long last;
		/** Its cards, the most recently levelled first. */
		final List<Card> cards = new ArrayList<>();
		/** The message's one picture: the latest level-up's. */
		byte[] png;
		String messageId;
		/** A post or edit of it is with the sender. */
		boolean busy;
		/** Holds a level the message does not show yet. */
		boolean dirty;
		long sentAt;
		/** Edits in a row lost to a server or network failure. */
		int failures;

		private Streak(String member, String url, long now, byte[] png)
		{
			this.member = member;
			this.hook = tag(url);
			this.url = url;
			this.first = now;
			this.last = now;
			this.png = png;
			this.busy = true;
		}

		/** A saved streak ({@link #line}) back after a restart: posted, not busy, its next level an edit. */
		private Streak(String[] f)
		{
			this.hook = f[1];
			this.member = f[2];
			this.first = Long.parseLong(f[3]);
			this.last = Long.parseLong(f[4]);
			this.messageId = f[5];
			for (int i = HEAD_FIELDS; i < f.length; i += CARD_FIELDS)
			{
				cards.add(new Card(f, i));
			}
		}

		/** Whether a moment joins this message: same webhook, inside the window, and room for its cards. */
		private boolean joins(List<GroupEvent> events, String url, long now, long window)
		{
			return hook.equals(tag(url)) && now - first < window && fits(events);
		}

		/** A moment's levels into this message; true when any was new, and the message is then owed an edit. */
		private boolean join(List<GroupEvent> events, String url, long now, byte[] picture)
		{
			boolean grew = false;
			for (GroupEvent e : events)
			{
				grew |= add(e, now);
			}
			if (grew)
			{
				this.url = url;
				last = now;
				png = picture == null ? png : picture;
				dirty = true;
			}
			return grew;
		}

		/** Whether {@code events} fit: the cards they would add stay within {@link #MAX_CARDS}. */
		private boolean fits(List<GroupEvent> events)
		{
			Set<String> skills = new HashSet<>();
			for (Card c : cards)
			{
				skills.add(c.skill);
			}
			for (GroupEvent e : events)
			{
				skills.add(skill(e));
			}
			return skills.size() <= MAX_CARDS;
		}

		/**
		 * A level into this message: its card moves to the top with the level raised, or a new card goes on top.
		 * True when it is a level the message did not hold yet (a repeat or an older level changes nothing).
		 */
		private boolean add(GroupEvent e, long now)
		{
			String skill = skill(e);
			for (Iterator<Card> i = cards.iterator(); i.hasNext(); )
			{
				Card c = i.next();
				if (c.skill.equals(skill))
				{
					if (e.number <= c.to)
					{
						return false;
					}
					i.remove();
					c.to = e.number;
					c.lastAt = now;
					c.ups++;
					c.broadcast = e.broadcast;
					cards.add(0, c);
					return true;
				}
			}
			// One action can gain several levels (low Prayer): the level before it, when the event knows it.
			cards.add(0, new Card(skill, e.previous > 0 ? e.previous : e.number - 1, e.number, now, e.broadcast));
			return true;
		}

		/** version, hook, member, first, last, message id, then each card; tab-separated. */
		private String line()
		{
			List<String> f = new ArrayList<>();
			Collections.addAll(f, VERSION, hook, member, Long.toString(first), Long.toString(last), messageId);
			for (Card c : cards)
			{
				c.fields(f);
			}
			return String.join(SEP, f);
		}
	}

	/** What one post or edit of a message says, frozen when it is handed out: safe to read off the lock. */
	static final class Edit
	{
		final Streak streak;
		final String url;
		final String messageId;
		final String member;
		/** Copies of the message's cards, the most recently levelled first. */
		final List<Card> cards;
		final byte[] png;

		private Edit(Streak s)
		{
			this.streak = s;
			this.url = s.url;
			this.messageId = s.messageId;
			this.member = s.member;
			List<Card> copies = new ArrayList<>();
			for (Card c : s.cards.subList(0, Math.min(MAX_CARDS, s.cards.size())))
			{
				copies.add(c.copy());
			}
			this.cards = Collections.unmodifiableList(copies);
			this.png = s.png;
		}
	}

	/** The streaks a level-up can still join, by member. */
	private final Map<String, Streak> open = new HashMap<>();
	/** Every streak with work left: the open ones, and closed ones still owed an edit. */
	private final List<Streak> live = new ArrayList<>();
	/** The open streaks changed since {@link #saved} last handed them out. */
	private boolean changed;

	/**
	 * The levels a plugin user's own moment is, when it can share their message: only skill level-ups below 99 and
	 * combat levels, in any number of skills (Board 2026-11-14: grouped per member). Null for anything else (a 99, a
	 * total level, an XP milestone in the same moment): that moment posts as it always has.
	 */
	static List<GroupEvent> combinable(List<GroupEvent> events)
	{
		if (events.isEmpty())
		{
			return null;
		}
		for (GroupEvent e : events)
		{
			boolean skill = e.type == GroupEvent.Type.LEVEL_UP && e.number < MAX_LEVEL;
			if (!skill && e.type != GroupEvent.Type.COMBAT_LEVEL)
			{
				return null;
			}
		}
		return events;
	}

	/**
	 * A level the game's group broadcast read for a member without the plugin ({@link GroupBroadcasts}), when it can
	 * share a message: a skill's level below 99, or a combat level (Board 2026-11-14: "group levels should follow
	 * the same pattern too"). Null for anything else, and for a plugin user's own event, which {@link #combinable}
	 * decides. Only the client that posts the broadcast keeps the streak, so a new poster starts a new message.
	 */
	static GroupEvent combinableBroadcast(GroupEvent e)
	{
		if (e == null || !e.broadcast)
		{
			return null;
		}
		boolean skill = e.type == GroupEvent.Type.LEVEL_UP && e.number < MAX_LEVEL;
		return skill || e.type == GroupEvent.Type.COMBAT_LEVEL ? e : null;
	}

	/** What a level is of: the skill, or "Combat" for a combat level (which names no skill). */
	private static String skill(GroupEvent e)
	{
		return e.type == GroupEvent.Type.COMBAT_LEVEL ? COMBAT : e.text;
	}

	/** One level; see {@link #level(List, String, long, long, byte[])}. */
	synchronized Edit level(GroupEvent e, String url, long now, long window, byte[] png)
	{
		return level(Collections.singletonList(e), url, now, window, png);
	}

	/**
	 * One moment's levels, all of one member, within {@code window} ms of that member's message's first post and
	 * with room for their cards: join it (null back; {@link #due} edits it in time). Otherwise a new message, its
	 * first post returned: send it and report back to {@link #posted} with {@link Edit#streak}.
	 */
	synchronized Edit level(List<GroupEvent> events, String url, long now, long window, byte[] png)
	{
		String member = events.get(0).member;
		Streak s = open.get(member);
		if (s != null && s.joins(events, url, now, window))
		{
			changed |= s.join(events, url, now, png);
			return null;
		}
		Streak fresh = new Streak(member, url, now, png);
		for (GroupEvent e : events)
		{
			fresh.add(e, now);
		}
		if (open.put(member, fresh) != null)
		{
			changed = true;
		}
		live.add(fresh);
		return new Edit(fresh);
	}

	/**
	 * The open streaks to keep for a restart, one {@link Streak#line} each, when they changed since the last call;
	 * null when they did not. Only streaks with a message (and a Discord webhook to match it by) are kept.
	 */
	synchronized String saved()
	{
		if (!changed)
		{
			return null;
		}
		changed = false;
		List<String> lines = new ArrayList<>();
		for (Streak s : open.values())
		{
			if (s.messageId != null && s.hook.startsWith("w"))
			{
				lines.add(s.line());
			}
		}
		return String.join("\n", lines);
	}

	/**
	 * Streaks {@link #saved} by an earlier run, back as open: the member's next level-up within {@code window} of
	 * the first post edits that message. Lines past the window or unreadable (any line from a build before the
	 * cards among them) are dropped, and the next {@link #saved} no longer has them; a streak this run already
	 * started wins.
	 */
	synchronized void restore(String text, long now, long window)
	{
		for (String line : text == null ? new String[0] : text.split("\n"))
		{
			Streak s = parse(line);
			if (s == null || now - s.first >= window || open.containsKey(s.member))
			{
				changed |= !line.isEmpty();
				continue;
			}
			open.put(s.member, s);
			live.add(s);
		}
	}

	private static Streak parse(String line)
	{
		String[] f = line.split(SEP, -1);
		int cards = (f.length - HEAD_FIELDS) / CARD_FIELDS;
		if (!VERSION.equals(f[0]) || cards < 1 || cards > MAX_CARDS
			|| (f.length - HEAD_FIELDS) % CARD_FIELDS != 0 || f[5].isEmpty())
		{
			return null;
		}
		try
		{
			return new Streak(f);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/** The webhook a URL posts to: its id ({@link StatusBoard#hookOf}), so a thread or URL form is the same one. */
	private static String tag(String url)
	{
		String hook = StatusBoard.hookOf(url);
		if (hook != null)
		{
			return hook;
		}
		return url == null ? "" : url;
	}

	/** How a streak's new post went: on success its message is the one edited from now on. */
	synchronized void posted(Streak s, int status, String messageId, long now)
	{
		s.busy = false;
		if (ok(status) && messageId != null)
		{
			s.messageId = messageId;
			s.sentAt = now;
			changed = true;
			return;
		}
		// Nothing to edit (refused, failed, or dropped by a full queue): the member's next level-up posts anew.
		end(s);
	}

	/** The edits to send now: posted streaks with a newer level, each at most once per {@link #EDIT_GAP}. */
	synchronized List<Edit> due(long now)
	{
		List<Edit> out = new ArrayList<>();
		for (Iterator<Streak> i = live.iterator(); i.hasNext(); )
		{
			Streak s = i.next();
			if (s.busy)
			{
				continue;
			}
			if (s.dirty && s.messageId != null && now - s.sentAt >= EDIT_GAP)
			{
				s.dirty = false;
				s.busy = true;
				s.sentAt = now;
				out.add(new Edit(s));
			}
			else if (!s.dirty && !open.containsValue(s))
			{
				i.remove();
			}
		}
		return out;
	}

	/**
	 * How an edit went. Discord refusing it (404 for a deleted message, or any 4xx) gives back the post to send in
	 * its place, its outcome to {@link #posted}. A server or network failure tries the edit again later, up to
	 * {@link #MAX_EDIT_FAILURES} in a row; then, or when the queue dropped the edit unsent
	 * ({@link DiscordQueue#DROPPED}), the streak ends and the member's next level posts anew. Null when there is
	 * nothing more to send.
	 */
	synchronized Edit edited(Streak s, int status)
	{
		s.busy = false;
		if (ok(status))
		{
			s.failures = 0;
			return null;
		}
		if (status >= 400 && status < 500)
		{
			s.messageId = null;
			s.dirty = false;
			s.busy = true;
			return new Edit(s);
		}
		if (status == DiscordQueue.DROPPED || ++s.failures >= MAX_EDIT_FAILURES)
		{
			end(s);
			return null;
		}
		s.dirty = true;
		return null;
	}

	/** Holding the lock: no more edits or joining; {@link #due} forgets it. */
	private void end(Streak s)
	{
		s.dirty = false;
		changed |= open.values().remove(s);
	}

	/** A logout: no more joining, but an edit already owed still goes. Saved streaks end too. */
	synchronized void close()
	{
		changed |= !open.isEmpty();
		open.clear();
	}

	/** Shutdown: everything forgotten. */
	synchronized void clear()
	{
		open.clear();
		live.clear();
	}

	/** "Prayer 23 → 25 (+2) in 4 min". */
	static String text(String skill, long from, long to, long spanMs)
	{
		return skill + ' ' + from + " → " + to + " (+" + (to - from) + ") " + span(spanMs);
	}

	private static String span(long ms)
	{
		long minutes = TimeUnit.MILLISECONDS.toMinutes(ms);
		if (minutes < 1)
		{
			return "in under a minute";
		}
		return minutes < 60 ? "in " + minutes + " min" : "in " + minutes / 60 + " h " + minutes % 60 + " min";
	}

	private static boolean ok(int status)
	{
		return status >= 200 && status < 300;
	}
}
