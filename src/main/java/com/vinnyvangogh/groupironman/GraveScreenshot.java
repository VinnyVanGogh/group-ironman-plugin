package com.vinnyvangogh.groupironman;

/**
 * Which picture the "Gravestone expiring" post carries (Board 2026-11-10: the
 * warning showed wherever the player stood five minutes before the grave ran
 * out, not where they died). Public, like every setting type: RuneLite reads
 * settings through a proxy that cannot see a package-private type
 * (ConfigKeysTest).
 */
public enum GraveScreenshot
{
	/**
	 * The frame taken for the death post, kept in memory only until the grave is gone. Labels stay short: the
	 * dropdown is as wide as its longest one and squeezes the setting's name beside it.
	 */
	DEATH("Where I died"),
	/** The frame drawn when the warning goes out. */
	CURRENT("Current view"),
	NONE("None");

	private final String label;

	GraveScreenshot(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
