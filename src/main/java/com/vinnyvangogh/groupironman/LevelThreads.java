package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Level-ups in one skill, one Discord post (Board 2026-11-10): Prayer 23 → 24 → 25 is a post that says
 * "Prayer 24", then is edited in place to "Prayer 23 → 25 (+2) in 4 min", not three posts.
 *
 * <p>The first level-up of a skill posts and keeps the message's id; more levels of that skill within the window
 * (counted from the first) mark the post for an edit. {@link #due} hands out at most one edit per message every
 * {@link #EDIT_GAP}, so levels quicker than that fold into the next edit. An edit Discord refuses (the message
 * deleted, the webhook changed) is posted anew and that post is the one kept from then on. A logout ends every
 * streak, but an edit already owed still goes. Pure and clock-driven.
 *
 * <p>Open streaks with a message survive a client restart (Board 2026-11-14): {@link #saved} hands out one line
 * per streak when they change, keyed by the webhook's id from its URL ({@link StatusBoard#hookOf}, never the URL
 * or its token), and {@link #restore} takes them back, dropping any past the window. A restored streak is joined
 * only through the same webhook; a different one, or an edit Discord refuses, posts anew as before.
 */
final class LevelThreads
{
	/** At most one edit per message this often. */
	static final long EDIT_GAP = TimeUnit.SECONDS.toMillis(10);
	/** Edits in a row a server or network failure may cost before the streak gives up and posts fresh. */
	static final int MAX_EDIT_FAILURES = 3;
	private static final int MAX_LEVEL = 99;
	/** A combat level's streak name: "Combat 99 → 101 (+2) in 10 min". */
	private static final String COMBAT = "Combat";
	private static final String SEP = "\t";
	private static final int SAVED_FIELDS = 8;

	/** One skill's run of level-ups and its post. Read and written only under the owner's lock. */
	static final class Streak
	{
		final String member;
		final String skill;
		/** The webhook it posts to ({@link LevelThreads#tag}): what a level-up must match to join. Never null. */
		final String hook;
		/** The latest URL it was joined through; null for a restored streak until a level-up joins it. */
		String url;
		final long from;
		final long first;
		long to;
		long last;
		GroupEvent latest;
		byte[] png;
		String messageId;
		/** A post or edit of it is with the sender. */
		boolean busy;
		/** Holds a level the post does not show yet. */
		boolean dirty;
		long sentAt;
		/** Edits in a row lost to a server or network failure. */
		int failures;

		private Streak(GroupEvent e, String url, long now, byte[] png)
		{
			this.member = e.member;
			this.skill = skill(e);
			this.hook = tag(url);
			this.url = url;
			// One action can gain several levels (low Prayer): the level before it, when the event knows it.
			this.from = e.previous > 0 ? e.previous : e.number - 1;
			this.first = now;
			this.to = e.number;
			this.last = now;
			this.latest = e;
			this.png = png;
			this.busy = true;
		}

		/** A saved streak ({@link #line}) back after a restart: posted, not busy, its next level an edit. */
		private Streak(String[] f)
		{
			this.hook = f[0];
			this.member = f[1];
			this.skill = f[2];
			this.from = Long.parseLong(f[3]);
			this.to = Long.parseLong(f[4]);
			this.first = Long.parseLong(f[5]);
			this.last = Long.parseLong(f[6]);
			this.messageId = f[7];
		}

		private String key()
		{
			return LevelThreads.key(member, skill);
		}

		/** hook, member, skill, from, to, first, last, message id; tab-separated. */
		private String line()
		{
			return String.join(SEP, hook, member, skill, Long.toString(from), Long.toString(to),
				Long.toString(first), Long.toString(last), messageId);
		}
	}

	/** What one edit (or the post replacing a lost message) says, frozen when it is handed out. */
	static final class Edit
	{
		final Streak streak;
		final String url;
		final String messageId;
		final GroupEvent latest;
		final String text;
		final byte[] png;

		private Edit(Streak s)
		{
			this.streak = s;
			this.url = s.url;
			this.messageId = s.messageId;
			this.latest = s.latest;
			this.text = text(s.skill, s.from, s.to, s.last - s.first);
			this.png = s.png;
		}
	}

	/** The streaks a level-up can still join, by member and skill. */
	private final Map<String, Streak> open = new HashMap<>();
	/** Every streak with work left: the open ones, and closed ones still owed an edit. */
	private final List<Streak> live = new ArrayList<>();
	/** The open streaks changed since {@link #saved} last handed them out. */
	private boolean changed;

	/**
	 * The one level-up a moment is, when it can share a post: only level-ups of one skill, below 99. Null for
	 * anything else (a 99, a total or combat level, two skills at once): that moment posts as it always has.
	 */
	static GroupEvent combinable(List<GroupEvent> events)
	{
		GroupEvent best = null;
		for (GroupEvent e : events)
		{
			if (e.type != GroupEvent.Type.LEVEL_UP || e.number >= MAX_LEVEL
				|| best != null && !best.text.equals(e.text))
			{
				return null;
			}
			best = best == null || e.number > best.number ? e : best;
		}
		return best;
	}

	/**
	 * A level the game's group broadcast read for a member without the plugin ({@link GroupBroadcasts}), when it can
	 * share a post: a skill's level below 99, or a combat level (Board 2026-11-14: "group levels should follow the
	 * same pattern too"). Null for anything else, and for a plugin user's own event, which {@link #combinable}
	 * decides as before. Only the client that posts the broadcast keeps the streak, so a new poster starts a new post.
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

	/** What a streak is of: the skill, or "Combat" for a combat level (which names no skill). */
	private static String skill(GroupEvent e)
	{
		return e.type == GroupEvent.Type.COMBAT_LEVEL ? COMBAT : e.text;
	}

	/**
	 * A level-up within {@code window} ms of its skill's first: joins that streak (null back; {@link #due} edits it
	 * in time). Otherwise a new streak, returned: post it and report back to {@link #posted}.
	 */
	synchronized Streak level(GroupEvent e, String url, long now, long window, byte[] png)
	{
		String key = key(e.member, skill(e));
		Streak s = open.get(key);
		if (s != null && s.hook.equals(tag(url)) && now - s.first < window)
		{
			if (e.number > s.to)
			{
				s.url = url;
				s.to = e.number;
				s.last = now;
				s.latest = e;
				s.png = png == null ? s.png : png;
				s.dirty = true;
				changed = true;
			}
			return null;
		}
		Streak fresh = new Streak(e, url, now, png);
		if (open.put(key, fresh) != null)
		{
			changed = true;
		}
		live.add(fresh);
		return fresh;
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
	 * Streaks {@link #saved} by an earlier run, back as open: the next level-up of the skill within {@code window}
	 * of the first post edits that post. Lines past the window or unreadable are dropped (and the next
	 * {@link #saved} no longer has them); a streak this run already started wins.
	 */
	synchronized void restore(String text, long now, long window)
	{
		for (String line : text == null ? new String[0] : text.split("\n"))
		{
			Streak s = parse(line);
			if (s == null || now - s.first >= window || open.containsKey(s.key()))
			{
				changed |= !line.isEmpty();
				continue;
			}
			open.put(s.key(), s);
			live.add(s);
		}
	}

	private static Streak parse(String line)
	{
		String[] f = line.split(SEP, -1);
		if (f.length != SAVED_FIELDS || f[7].isEmpty())
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

	private static String key(String member, String skill)
	{
		return member + '\n' + skill;
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
		// Nothing to edit (refused, failed, or dropped by a full queue): the skill's next level-up posts anew.
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
	 * ({@link DiscordQueue#DROPPED}), the streak ends and the skill's next level posts anew. Null when there is
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
		return skill + " " + from + " → " + to + " (+" + (to - from) + ") " + span(spanMs);
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
