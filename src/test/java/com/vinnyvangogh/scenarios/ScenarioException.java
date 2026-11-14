package com.vinnyvangogh.scenarios;

/** A scenario that cannot be read or run, with a message that says where and why. */
public class ScenarioException extends RuntimeException
{
	public ScenarioException(String message)
	{
		super(message);
	}

	public ScenarioException(String message, Throwable cause)
	{
		super(message, cause);
	}
}
