package com.vinnyvangogh.scenarios;

import com.google.gson.JsonObject;

/**
 * One line of a scenario: an event kind, its fields, and how long to wait before it fires.
 *
 * <p>Reserved keys in the step object are {@code kind}, {@code wait} (milliseconds before this step) and
 * {@code note} (shown in the panel); every other key is a field for the kind's {@link EventFactory}.
 */
public final class Step
{
	static final String KIND = "kind";
	static final String WAIT = "wait";
	static final String NOTE = "note";

	/** 1-based, as error messages and the panel number it. */
	public final int number;
	public final String kind;
	public final long waitMs;
	public final String note;
	final JsonObject fields;

	Step(int number, String kind, long waitMs, String note, JsonObject fields)
	{
		this.number = number;
		this.kind = kind;
		this.waitMs = waitMs;
		this.note = note;
		this.fields = fields;
	}

	/** "step 2 (stat)": how every message about this step starts. */
	public String label()
	{
		return "step " + number + " (" + kind + ")";
	}

	@Override
	public String toString()
	{
		return label() + (note == null ? "" : " " + note);
	}
}
