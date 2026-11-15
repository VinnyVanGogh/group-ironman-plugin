package com.vinnyvangogh.groupironman;

import java.util.function.LongSupplier;

/**
 * What {@link LevelPosts} and {@link EventPosts} share around their {@link EditedThreads}: the restart file, a
 * logout and shutdown. Any thread.
 */
abstract class EditedPosts
{
	private final EditedThreads<?, ?> threads;
	final LongSupplier clock;

	EditedPosts(EditedThreads<?, ?> threads, LongSupplier clock)
	{
		this.threads = threads;
		this.clock = clock;
	}

	/** The open messages to keep for a restart when they changed since the last call, else null ({@link EditedThreads#saved}). */
	String saved()
	{
		return threads.saved();
	}

	/** Messages an earlier run {@link #saved}, back as open while inside {@code window} of their first post. */
	void restore(String text, long window)
	{
		threads.restore(text, clock.getAsLong(), window);
	}

	/** A logout: no more joining, owed edits still go. */
	void close()
	{
		threads.close();
	}

	void clear()
	{
		threads.clear();
	}
}
