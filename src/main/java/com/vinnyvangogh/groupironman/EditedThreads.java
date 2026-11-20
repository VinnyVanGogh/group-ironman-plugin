package com.vinnyvangogh.groupironman;

import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Discord messages that one member's later events edit in place: the first event posts and keeps the message's id,
 * more within the window (counted from the first post) mark it for an edit, and {@link #due} hands out at most one
 * edit per message every {@link #EDIT_GAP}, so quicker events fold into the next edit. An edit Discord refuses (the
 * message deleted, the webhook changed) is posted anew and that post is the one kept from then on.
 *
 * <p>The open messages survive a client restart: {@link #saved} hands out one line per message when they change,
 * keyed by the webhook's id from its URL ({@link StatusBoard#hookOf}, never the URL or its token), and
 * {@link #restore} takes them back, dropping any past the window. A restored message is joined only through the same
 * webhook. Shared by {@link LevelThreads} (a card per skill) and {@link EventThreads} (every other kind); each says
 * what a message holds, how it is keyed, and how it is saved. Pure and clock-driven; every method holds the lock.
 *
 * @param <T> the message and what it holds
 * @param <E> one post or edit of it, frozen when handed out
 */
abstract class EditedThreads<T extends EditedThreads.Message, E>
{
	/** At most one edit per message this often. */
	static final long EDIT_GAP = TimeUnit.SECONDS.toMillis(10);
	/** Edits in a row a server or network failure may cost before the message gives up and posts fresh. */
	static final int MAX_EDIT_FAILURES = 3;
	/** Cards in one message: Discord takes at most 10 embeds in a message. */
	static final int MAX_CARDS = 10;

	/** One message and its post state. Read and written only under the owner's lock. */
	static class Message
	{
		final String member;
		/** The webhook it posts to ({@link #tag}): what an event must match to join. Never null. */
		final String hook;
		/** The latest URL it was joined through; null for a restored message until an event joins it. */
		String url;
		final long first;
		long last;
		/** The message's one picture: the latest event's that had one. Never saved. */
		byte[] png;
		String messageId;
		/** A post or edit of it is with the sender. */
		boolean busy;
		/** Holds an event the message does not show yet. */
		boolean dirty;
		long sentAt;
		/** Edits in a row lost to a server or network failure. */
		int failures;

		/** A new message, its first post about to go to the sender. */
		Message(String member, String url, long now, byte[] png)
		{
			this.member = member;
			this.hook = tag(url);
			this.url = url;
			this.first = now;
			this.last = now;
			this.png = png;
			this.busy = true;
		}

		/** A saved message back after a restart: posted, not busy, its next event an edit. */
		Message(String hook, String member, long first, long last, String messageId)
		{
			this.hook = hook;
			this.member = member;
			this.first = first;
			this.last = last;
			this.messageId = messageId;
		}

		/** Whether an event through {@code url} at {@code now} may still join: same webhook, inside the window. */
		final boolean open(String url, long now, long window)
		{
			return hook.equals(tag(url)) && now - first < window;
		}

		/** An event joined: the message is owed an edit, with the newest picture if this one has one. */
		final void grew(String url, long now, byte[] picture)
		{
			this.url = url;
			last = now;
			png = picture == null ? png : picture;
			dirty = true;
		}
	}

	/** The messages an event can still join, by {@link #key}. */
	final Map<String, T> open = new HashMap<>();
	/** Every message with work left: the open ones, and closed ones still owed an edit. */
	private final List<T> live = new ArrayList<>();
	/** The open messages changed since {@link #saved} last handed them out. */
	boolean changed;

	/** What a message is found by: the member, and for {@link EventThreads} the kind of event too. */
	abstract String key(T m);

	/** One post or edit of {@code m}, copied so it is safe to read off the lock. */
	abstract E snapshot(T m);

	/** The message as one saved line; never holds a newline. */
	abstract String line(T m);

	/** A {@link #line} back; null when unreadable or from another format. */
	abstract T parse(String line);

	/** Holding the lock: {@code fresh} replaces any open message under its key; its first post goes back. */
	final E start(T fresh)
	{
		if (open.put(key(fresh), fresh) != null)
		{
			changed = true;
		}
		live.add(fresh);
		return snapshot(fresh);
	}

	/**
	 * The open messages to keep for a restart, one {@link #line} each, when they changed since the last call; null
	 * when they did not. Only messages with an id (and a Discord webhook to match them by) are kept.
	 */
	synchronized String saved()
	{
		if (!changed)
		{
			return null;
		}
		changed = false;
		List<String> lines = new ArrayList<>();
		for (T m : open.values())
		{
			if (m.messageId != null && m.hook.startsWith("w"))
			{
				lines.add(line(m));
			}
		}
		return String.join("\n", lines);
	}

	/**
	 * Messages {@link #saved} by an earlier run, back as open: the next event within {@code window} of the first post
	 * edits that message. Lines past the window or unreadable are dropped, and the next {@link #saved} no longer has
	 * them; a message this run already started wins.
	 */
	synchronized void restore(String text, long now, long window)
	{
		for (String line : text == null ? new String[0] : text.split("\n"))
		{
			T m = line.isEmpty() ? null : parse(line);
			if (m == null || now - m.first >= window || open.containsKey(key(m)))
			{
				changed |= !line.isEmpty();
				continue;
			}
			open.put(key(m), m);
			live.add(m);
		}
	}

	/** How a message's new post went: on success it is the one edited from now on. */
	synchronized void posted(T m, int status, String messageId, long now)
	{
		m.busy = false;
		if (ok(status) && messageId != null)
		{
			m.messageId = messageId;
			m.sentAt = now;
			changed = true;
			return;
		}
		// Nothing to edit (refused, failed, or dropped by a full queue): the member's next event posts anew.
		end(m);
	}

	/** The edits to send now: posted messages with a newer event, each at most once per {@link #EDIT_GAP}. */
	synchronized List<E> due(long now)
	{
		List<E> out = new ArrayList<>();
		for (Iterator<T> i = live.iterator(); i.hasNext(); )
		{
			T m = i.next();
			if (m.busy)
			{
				continue;
			}
			if (m.dirty && m.messageId != null && now - m.sentAt >= EDIT_GAP)
			{
				m.dirty = false;
				m.busy = true;
				m.sentAt = now;
				out.add(snapshot(m));
			}
			else if (!m.dirty && !open.containsValue(m))
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
	 * ({@link DiscordQueue#DROPPED}), the message ends and the member's next event posts anew. Null when there is
	 * nothing more to send.
	 */
	synchronized E edited(T m, int status)
	{
		m.busy = false;
		if (ok(status))
		{
			m.failures = 0;
			return null;
		}
		if (status >= 400 && status < 500)
		{
			m.messageId = null;
			m.dirty = false;
			m.busy = true;
			return snapshot(m);
		}
		if (status == DiscordQueue.DROPPED || ++m.failures >= MAX_EDIT_FAILURES)
		{
			end(m);
			return null;
		}
		m.dirty = true;
		return null;
	}

	/** Holding the lock: no more edits or joining; {@link #due} forgets it. */
	private void end(T m)
	{
		m.dirty = false;
		changed |= open.values().remove(m);
	}

	/** A logout: no more joining, but an edit already owed still goes. Saved messages end too. */
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

	/** The webhook a URL posts to: its id ({@link StatusBoard#hookOf}), so a thread or URL form is the same one. */
	static String tag(String url)
	{
		String hook = StatusBoard.hookOf(url);
		if (hook != null)
		{
			return hook;
		}
		return url == null ? "" : url;
	}

	static boolean ok(int status)
	{
		return status >= 200 && status < 300;
	}
}
