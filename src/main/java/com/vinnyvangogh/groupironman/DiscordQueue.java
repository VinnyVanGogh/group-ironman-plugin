package com.vinnyvangogh.groupironman;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;

/**
 * The posts waiting for Discord, and when the next may go. Pure and
 * clock-driven, so rate limits, back-off and quiet hours are tested without
 * a network.
 *
 * <ul>
 * <li>At most {@link #MAX_QUEUED} wait; a new post past that drops the oldest.</li>
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
	private static final long MINUTE = TimeUnit.MINUTES.toMillis(1);

	static final class Post
	{
		final String url;
		final String json;
		/** A PNG to attach, or null. */
		final byte[] png;
		int attempts;

		Post(String url, String json, byte[] png)
		{
			this.url = url;
			this.json = json;
			this.png = png;
		}
	}

	private final Deque<Post> waiting = new ArrayDeque<>();
	private final Deque<Long> sent = new ArrayDeque<>();
	private long notBefore;
	private Post inFlight;

	synchronized void offer(Post post)
	{
		while (waiting.size() >= MAX_QUEUED)
		{
			waiting.pollFirst();
		}
		waiting.addLast(post);
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
	 * {@code retryAfterMs} is the 429's Retry-After, negative when absent.
	 */
	synchronized void done(int status, long retryAfterMs, long now)
	{
		Post post = inFlight;
		inFlight = null;
		if (post == null || status >= 200 && status < 300)
		{
			return;
		}
		if (status == 429)
		{
			notBefore = now + (retryAfterMs >= 0 ? retryAfterMs : RETRY_MS);
			waiting.addFirst(post);
			return;
		}
		if ((status == 0 || status >= 500) && post.attempts < MAX_ATTEMPTS)
		{
			notBefore = now + RETRY_MS;
			waiting.addFirst(post);
		}
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
