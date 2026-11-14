package com.vinnyvangogh.scenarios;

import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * One pass through a scenario, a step at a time. It never sleeps: {@link #nextWait} says how long the next step
 * wants, and the caller waits its own way (a fake clock headless, a Swing timer in the panel).
 */
public final class ScenarioRun
{
	private final Scenario scenario;
	private final EventKinds kinds;
	private final Consumer<Object> post;
	private boolean live;
	private int next;

	/** {@code post}: where each event goes, an EventBus's {@code post} or a test's list. */
	public ScenarioRun(Scenario scenario, EventKinds kinds, Consumer<Object> post)
	{
		this.scenario = scenario;
		this.kinds = kinds;
		this.post = post;
	}

	/** Live, in the developer client: steps whose kind is headless-only are skipped, not posted. */
	public ScenarioRun live(boolean live)
	{
		this.live = live;
		return this;
	}

	public Scenario scenario()
	{
		return scenario;
	}

	public boolean hasNext()
	{
		return next < scenario.steps.size();
	}

	/** Milliseconds the next step waits before it fires; 0 when there is none. */
	public long nextWait()
	{
		return hasNext() ? scenario.steps.get(next).waitMs : 0;
	}

	/** Fires the next step and says what happened; null when every step has fired. */
	public Fired step()
	{
		if (!hasNext())
		{
			return null;
		}
		Step step = scenario.steps.get(next++);
		EventFactory factory = kinds.factory(step.kind);
		String why = factory.headlessOnly();
		if (live && why != null)
		{
			return new Fired(step, null, "skipped, headless only: " + why);
		}
		Object event = ScenarioParser.build(step, factory, kinds);
		post.accept(event);
		return new Fired(step, event, null);
	}

	/** Every remaining step in order, handing each wait to {@code waiter} first. */
	public void runAll(LongConsumer waiter)
	{
		while (hasNext())
		{
			long wait = nextWait();
			if (wait > 0)
			{
				waiter.accept(wait);
			}
			step();
		}
	}

	/** One step's outcome: the event posted, or why it was skipped. */
	public static final class Fired
	{
		public final int number;
		public final Step step;
		/** Null when skipped. */
		public final Object event;
		/** Null when posted. */
		public final String skipped;

		Fired(Step step, Object event, String skipped)
		{
			this.number = step.number;
			this.step = step;
			this.event = event;
			this.skipped = skipped;
		}

		@Override
		public String toString()
		{
			String what = skipped != null ? skipped : "posted " + event.getClass().getSimpleName();
			return step + ": " + what;
		}
	}
}
