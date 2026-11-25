package com.vinnyvangogh.groupironman;

/** What the Group box shows when nobody else is online. Public for the config proxy (see {@link OnlineStatus}). */
public enum EmptyBoxMode
{
	USEFUL("Useful"),
	FUN("Fun"),
	BOTH("Both"),
	CLASSIC("Classic");

	private final String label;

	EmptyBoxMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
