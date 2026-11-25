package com.vinnyvangogh.groupironman;

/** How the Group box words when an event happened. Public for the config proxy (see {@link OnlineStatus}). */
public enum BoxTimes
{
	SHORT("Short"),
	FULL("Full"),
	OFF("Off");

	private final String label;

	BoxTimes(String label)
	{
		this.label = label;
	}

	/** "8m" short, "8m ago" full, nothing off. */
	String of(long now, long then)
	{
		switch (this)
		{
			case OFF:
				return "";
			case FULL:
				return BoostText.ago(now, then);
			default:
				return BoostText.shortAgo(now, then);
		}
	}

	@Override
	public String toString()
	{
		return label;
	}
}
