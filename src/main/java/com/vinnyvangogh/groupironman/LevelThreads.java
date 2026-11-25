package com.vinnyvangogh.groupironman;

import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A member's level-ups, one Discord message (Board 2026-11-14, final design): the message holds one embed card per
 * skill, and each card is edited in place on that skill's next level ("Thieving 44 → 46 (+2) in 10 min"). A new
 * skill adds a card; combat level is a card of its own. Cards are ordered by the most recent level-up first. A
 * message holds at most {@link #MAX_CARDS} cards (Discord's limit on embeds); a skill past that starts a new message.
 *
 * <p>How the message is posted, edited, refused, ended at a logout and kept across a restart is
 * {@link EditedThreads}'; this class says what a level adds to it. A saved line has every card's state.
 */
final class LevelThreads extends EditedThreads<LevelThreads.Streak, LevelThreads.Edit>
{
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
	static final class Streak extends EditedThreads.Message
	{
		/** Its cards, the most recently levelled first. */
		final List<Card> cards = new ArrayList<>();

		private Streak(String member, String url, long now, byte[] png)
		{
			super(member, url, now, png);
		}

		/** A saved streak ({@link #line}) back after a restart: posted, not busy, its next level an edit. */
		private Streak(String[] f)
		{
			super(f[1], f[2], Long.parseLong(f[3]), Long.parseLong(f[4]), f[5]);
			for (int i = HEAD_FIELDS; i < f.length; i += CARD_FIELDS)
			{
				cards.add(new Card(f, i));
			}
		}

		/** Whether a moment joins this message: same webhook, inside the window, and room for its cards. */
		private boolean joins(List<GroupEvent> events, String url, long now, long window)
		{
			return open(url, now, window) && fits(events);
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
				grew(url, now, picture);
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
			if (!levelCard(e))
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
		return e != null && e.broadcast && levelCard(e) ? e : null;
	}

	/** A skill's level below 99, or a combat level: what a card is made of. */
	private static boolean levelCard(GroupEvent e)
	{
		return e.type == GroupEvent.Type.LEVEL_UP && e.number < MAX_LEVEL || e.type == GroupEvent.Type.COMBAT_LEVEL;
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
		return start(fresh);
	}

	@Override
	String key(Streak s)
	{
		return s.member;
	}

	@Override
	Edit snapshot(Streak s)
	{
		return new Edit(s);
	}

	/** version, hook, member, first, last, message id, then each card; tab-separated. */
	@Override
	String line(Streak s)
	{
		List<String> f = new ArrayList<>();
		Collections.addAll(f, VERSION, s.hook, s.member, Long.toString(s.first), Long.toString(s.last), s.messageId);
		for (Card c : s.cards)
		{
			c.fields(f);
		}
		return String.join(SEP, f);
	}

	@Override
	Streak parse(String line)
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
}
