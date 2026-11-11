package com.vinnyvangogh.groupironman;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The posts waiting for Discord, and when the next may go. Pure and
 * clock-driven, so rate limits, back-off and quiet hours are tested without
 * a network.
 *
 * <ul>
 * <li>At most {@link #MAX_QUEUED} wait; a new post past that drops the oldest, whose {@link Result} hears
 * {@link #DROPPED}.</li>
 * <li>At most {@link #PER_MINUTE} go in any minute, well under Discord's own
 * per-webhook limit, so a busy evening never trips it.</li>
 * <li>A 429 waits its Retry-After and tries the same post again; a 5xx or a
 * network failure waits {@link #RETRY_MS}, at most {@link #MAX_ATTEMPTS} times.
 * Any other 4xx (a deleted webhook) drops the post: retrying cannot fix it.</li>
 * <li>One post is in flight at a time, so they arrive in order.</li>
 * </ul>
 */
final class DiscordQueue
{
	static final int MAX_QUEUED = 50;
	static final int PER_MINUTE = 20;
	static final long RETRY_MS = TimeUnit.SECONDS.toMillis(5);
	static final int MAX_ATTEMPTS = 3;
	/** The status a {@link Result} hears when its post was dropped from a full queue, never sent. */
	static final int DROPPED = -1;
	private static final long MINUTE = TimeUnit.MINUTES.toMillis(1);

	/** The final outcome of a post the caller wants to hear about: its status, and the created message's id. */
	interface Result
	{
		void done(int status, String messageId);
	}

	static final class Post
	{
		final String url;
		final String json;
		/** A PNG to attach, or null. */
		final byte[] png;
		/** The message this post edits in place, or null for a new message. */
		final String edit;
		/** Told once how it went (never for a retry); null when nobody asks. A new post with one waits for its id. */
		final Result result;
		int attempts;

		Post(String url, String json, byte[] png)
		{
			this(url, json, png, null, null);
		}

		Post(String url, String json, byte[] png, String edit, Result result)
		{
			this.url = url;
			this.json = json;
			this.png = png;
			this.edit = edit;
			this.result = result;
		}
	}

	private final Deque<Post> waiting = new ArrayDeque<>();
	private final Deque<Long> sent = new ArrayDeque<>();
	private long notBefore;
	private Post inFlight;

	/**
	 * Queues {@code post}; the oldest waiting go when the queue is full. Returns those dropped that wanted their
	 * outcome, for the caller to tell {@link #DROPPED} outside this lock.
	 */
	synchronized List<Post> offer(Post post)
	{
		List<Post> dropped = new ArrayList<>();
		while (waiting.size() >= MAX_QUEUED)
		{
			Post old = waiting.pollFirst();
			if (old.result != null)
			{
				dropped.add(old);
			}
		}
		waiting.addLast(post);
		return dropped;
	}

	synchronized int size()
	{
		return waiting.size() + (inFlight == null ? 0 : 1);
	}

	/** The post to send now, or null: one in flight, quiet hours, a back-off, or the minute's cap reached. */
	synchronized Post next(long now, boolean quiet)
	{
		while (!sent.isEmpty() && now - sent.peekFirst() >= MINUTE)
		{
			sent.pollFirst();
		}
		if (inFlight != null || quiet || now < notBefore || sent.size() >= PER_MINUTE || waiting.isEmpty())
		{
			return null;
		}
		inFlight = waiting.pollFirst();
		inFlight.attempts++;
		sent.addLast(now);
		return inFlight;
	}

	/**
	 * How the in-flight post went: an HTTP status, or 0 for a network failure.
	 * {@code retryAfterMs} is the 429's Retry-After, negative when absent. True when the post will be tried again.
	 */
	synchronized boolean done(int status, long retryAfterMs, long now)
	{
		Post post = inFlight;
		inFlight = null;
		if (post == null || status >= 200 && status < 300)
		{
			return false;
		}
		if (status == 429)
		{
			notBefore = now + (retryAfterMs >= 0 ? retryAfterMs : RETRY_MS);
			waiting.addFirst(post);
			return true;
		}
		if ((status == 0 || status >= 500) && post.attempts < MAX_ATTEMPTS)
		{
			notBefore = now + RETRY_MS;
			waiting.addFirst(post);
			return true;
		}
		return false;
	}

	synchronized void clear()
	{
		waiting.clear();
		inFlight = null;
	}

	/** Quiet from {@code start} up to {@code end} (hours, 0-23), across midnight when start is later; equal means never. */
	static boolean quiet(int hour, int start, int end)
	{
		if (start == end)
		{
			return false;
		}
		return start < end ? hour >= start && hour < end : hour >= start || hour < end;
	}

	/** Discord's Retry-After header is in seconds, possibly fractional; negative when missing or unreadable. */
	static long retryAfterMs(String header)
	{
		if (header == null)
		{
			return -1;
		}
		try
		{
			return (long) Math.ceil(Double.parseDouble(header.trim()) * 1000);
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}
}
