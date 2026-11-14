package com.vinnyvangogh.scenarios;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Named views of what the plugin did, as lines of text: the Discord posts a fake sender captured, the panel's log
 * rows, a setting's value. A scenario's {@code expect} entries check these by name.
 */
public final class Probes
{
	private final Map<String, Supplier<List<String>>> probes = new TreeMap<>();

	public Probes add(String name, Supplier<List<String>> lines)
	{
		if (probes.put(name, lines) != null)
		{
			throw new ScenarioException("probe '" + name + "' is already registered");
		}
		return this;
	}

	/** The probe's lines now; null when there is no such probe. */
	List<String> lines(String name)
	{
		Supplier<List<String>> p = probes.get(name);
		return p == null ? null : new ArrayList<>(p.get());
	}

	List<String> names()
	{
		return new ArrayList<>(probes.keySet());
	}
}
