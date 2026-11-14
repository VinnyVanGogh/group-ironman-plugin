package com.vinnyvangogh.scenarios;

import java.util.Collections;
import java.util.List;

/** A named list of steps, read from one JSON file, and what the plugin should have done once they all fired. */
public final class Scenario
{
	/** The file it came from, for messages. */
	public final String source;
	public final String name;
	public final String description;
	public final List<Step> steps;
	public final List<Expect> expect;

	Scenario(String source, String name, String description, List<Step> steps, List<Expect> expect)
	{
		this.source = source;
		this.name = name;
		this.description = description;
		this.steps = Collections.unmodifiableList(steps);
		this.expect = Collections.unmodifiableList(expect);
	}

	@Override
	public String toString()
	{
		return name;
	}

	/** One check on a probe's lines: one of contains, absent or count is set. */
	public static final class Expect
	{
		public final int number;
		public final String probe;
		public final String contains;
		public final String absent;
		public final Integer count;

		Expect(int number, String probe, String contains, String absent, Integer count)
		{
			this.number = number;
			this.probe = probe;
			this.contains = contains;
			this.absent = absent;
			this.count = count;
		}
	}
}
