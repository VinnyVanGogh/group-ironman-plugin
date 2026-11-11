package com.vinnyvangogh.groupironman;

/**
 * How Discord hears about logins and logouts (Board 2026-11-10). Public, like every setting type: RuneLite reads
 * settings through a proxy that cannot see a package-private type (ConfigKeysTest).
 */
public enum OnlineStatus
{
	/** A post for each login and logout, with the offline grace. */
	POSTS("Posts"),
	/** One message in the Group channel, edited as members log in, log out or hop ({@link StatusBoard}). */
	LIVE_BOARD("Live board"),
	OFF("Off");

	private final String label;

	OnlineStatus(String label)
	{
		this.label = label;
	}

	/**
	 * What an install that had the older "Online and offline" switch keeps, the first time this setting exists:
	 * posts when it posted them, nothing when it used Discord without them. Null for an install that never used
	 * Discord, which takes the default, posts (Board 2026-11-10: a member opts into the live board).
	 */
	static OnlineStatus upgrading(boolean postedOnline, boolean usedDiscord)
	{
		if (postedOnline)
		{
			return POSTS;
		}
		return usedDiscord ? OFF : null;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
