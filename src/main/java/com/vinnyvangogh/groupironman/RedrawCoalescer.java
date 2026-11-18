package com.vinnyvangogh.groupironman;

import java.util.function.LongSupplier;

/**
 * Folds a burst of redraw requests into one run, and at most one run per
 * {@code gapMs}. Members' syncs arrive many times a second while they play;
 * the Banks tab redrawing on each one kept the Swing thread busy enough to
 * drop clicks on the game (Board, 2026-11-15, at Revenants).
 *
 * <p>The first request after a quiet spell runs straight away; requests made
 * while a run is pending are dropped (the run reads the newest state), and the
 * next run waits until {@code gapMs} after the last one.
 */
final class RedrawCoalescer
{
	/** Runs {@code task} after {@code delayMs}. */
	interface Scheduler
	{
		void schedule(Runnable task, long delayMs);
	}

	/** Runs the task at once on the caller's thread, ignoring the delay: tests that check what a tab shows. */
	static final Scheduler DIRECT = (task, delayMs) -> task.run();

	private final long gapMs;
	private final LongSupplier clock;
	private final Scheduler scheduler;
	private final Runnable task;
	private boolean pending;
	private boolean ran;
	private long lastRun;

	RedrawCoalescer(long gapMs, LongSupplier clock, Scheduler scheduler, Runnable task)
	{
		this.gapMs = gapMs;
		this.clock = clock;
		this.scheduler = scheduler;
		this.task = task;
	}

	/** Asks for a run; one already waiting covers this request. */
	void request()
	{
		long delay;
		synchronized (this)
		{
			if (pending)
			{
				return;
			}
			pending = true;
			delay = ran ? Math.max(0, lastRun + gapMs - clock.getAsLong()) : 0;
		}
		scheduler.schedule(this::run, delay);
	}

	private void run()
	{
		synchronized (this)
		{
			pending = false;
			ran = true;
			lastRun = clock.getAsLong();
		}
		task.run();
	}
}
