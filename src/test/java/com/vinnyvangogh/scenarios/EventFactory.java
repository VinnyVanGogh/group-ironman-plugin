package com.vinnyvangogh.scenarios;

/**
 * Builds the event one step posts. One per kind; a plugin adds its own with {@link EventKinds#add}.
 *
 * <p>Return what the plugin's {@code @Subscribe} methods take: a RuneLite event, or an object of the plugin's own
 * that only a headless rig subscribes to (then say so in {@link #headlessOnly}).
 */
@FunctionalInterface
public interface EventFactory
{
	Object create(Fields fields);

	/**
	 * Null when the event is safe to post into the developer client's real EventBus. Otherwise why not: the panel
	 * skips the step and shows this. Every plugin sees a posted event, not only the one under test.
	 */
	default String headlessOnly()
	{
		return null;
	}

	/** A factory whose events only a headless rig receives. */
	static EventFactory headless(String why, EventFactory factory)
	{
		return new EventFactory()
		{
			@Override
			public Object create(Fields fields)
			{
				return factory.create(fields);
			}

			@Override
			public String headlessOnly()
			{
				return why;
			}
		};
	}
}
