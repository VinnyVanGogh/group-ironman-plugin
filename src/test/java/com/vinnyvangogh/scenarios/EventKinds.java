package com.vinnyvangogh.scenarios;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The event kinds a plugin's scenarios may use, and the named constants their fields may use.
 *
 * <p>Start from {@link #core()} (RuneLite's own events, {@link CoreKinds}) and {@link #add} the plugin's.
 * Adding a kind never replaces one: two factories answering to one name would make a scenario mean whichever
 * registered last.
 */
public final class EventKinds
{
	private final Map<String, EventFactory> factories = new TreeMap<>();
	private final Map<String, Integer> constants = new TreeMap<>();

	/** The RuneLite events every plugin can post. */
	public static EventKinds core()
	{
		EventKinds kinds = new EventKinds();
		CoreKinds.register(kinds);
		return kinds;
	}

	public EventKinds add(String kind, EventFactory factory)
	{
		if (factories.containsKey(kind))
		{
			throw new ScenarioException("event kind '" + kind + "' is already registered");
		}
		factories.put(kind, factory);
		return this;
	}

	/** A name a scenario can write instead of a number: an interface, inventory or item id. */
	public EventKinds constant(String name, int value)
	{
		Integer was = constants.put(name, value);
		if (was != null && was != value)
		{
			throw new ScenarioException("constant '" + name + "' is already " + was + ", not " + value);
		}
		return this;
	}

	/** The factory for {@code kind}, refusing an unknown kind with the list of known ones. */
	EventFactory factory(String kind)
	{
		EventFactory f = factories.get(kind);
		if (f == null)
		{
			throw new ScenarioException("unknown kind '" + kind + "'; known kinds: " + names()
				+ ". A new kind is one EventFactory registered with EventKinds.add (see TESTING.md).");
		}
		return f;
	}

	public List<String> names()
	{
		return Collections.unmodifiableList(new ArrayList<>(factories.keySet()));
	}

	Integer constantValue(String name)
	{
		return constants.get(name);
	}

	List<String> constantNames()
	{
		return new ArrayList<>(constants.keySet());
	}
}
