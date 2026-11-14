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
 * streak, but an edit already owed still goes. Pure and clock-driven; ids live in memory only.
 */
final class LevelThreads
{
	/** At most one edit per message this often. */
	static final long EDIT_GAP = TimeUnit.SECONDS.toMillis(10);
	/** Edits in a row a server or network failure may cost before the streak gives up and posts fresh. */
	static final int MAX_EDIT_FAILURES = 3;
	private static final int MAX_LEVEL = 99;

	/** One skill's run of level-ups and its post. Read and written only under the owner's lock. */
	static final class Streak
	{
		final String member;
		final String skill;
		final String url;
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
			this.skill = e.text;
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
	 * A level-up within {@code window} ms of its skill's first: joins that streak (null back; {@link #due} edits it
	 * in time). Otherwise a new streak, returned: post it and report back to {@link #posted}.
	 */
	synchronized Streak level(GroupEvent e, String url, long now, long window, byte[] png)
	{
		String key = e.member + '\n' + e.text;
		Streak s = open.get(key);
		if (s != null && s.url.equals(url) && now - s.first < window)
		{
			if (e.number > s.to)
			{
				s.to = e.number;
				s.last = now;
				s.latest = e;
				s.png = png == null ? s.png : png;
				s.dirty = true;
			}
			return null;
		}
		Streak fresh = new Streak(e, url, now, png);
		open.put(key, fresh);
		live.add(fresh);
		return fresh;
	}

	/** How a streak's new post went: on success its message is the one edited from now on. */
	synchronized void posted(Streak s, int status, String messageId, long now)
	{
		s.busy = false;
		if (ok(status) && messageId != null)
		{
			s.messageId = messageId;
			s.sentAt = now;
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
		open.values().remove(s);
	}

	/** A logout: no more joining, but an edit already owed still goes. */
	synchronized void close()
	{
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
