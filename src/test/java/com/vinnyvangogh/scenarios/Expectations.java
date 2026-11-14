package com.vinnyvangogh.scenarios;

import java.util.ArrayList;
import java.util.List;

/** Checks a scenario's {@code expect} entries against the probes; each failure says what was seen instead. */
public final class Expectations
{
	private Expectations()
	{
	}

	/** Empty when every expectation holds. */
	public static List<String> check(Scenario scenario, Probes probes)
	{
		List<String> failures = new ArrayList<>();
		for (Scenario.Expect e : scenario.expect)
		{
			String failure = check(e, probes);
			if (failure != null)
			{
				failures.add(scenario.source + ": expect " + e.number + ": " + failure);
			}
		}
		return failures;
	}

	private static String check(Scenario.Expect e, Probes probes)
	{
		List<String> lines = probes.lines(e.probe);
		if (lines == null)
		{
			return "unknown probe '" + e.probe + "'; known probes: " + probes.names();
		}
		if (e.contains != null && lines.stream().noneMatch(l -> l.contains(e.contains)))
		{
			return "no line of '" + e.probe + "' contains '" + e.contains + "'; saw " + lines;
		}
		if (e.absent != null && lines.stream().anyMatch(l -> l.contains(e.absent)))
		{
			return "a line of '" + e.probe + "' contains '" + e.absent + "', expected none; saw " + lines;
		}
		if (e.count != null && lines.size() != e.count)
		{
			return "'" + e.probe + "' has " + lines.size() + " lines, expected count " + e.count + "; saw " + lines;
		}
		return null;
	}
}
