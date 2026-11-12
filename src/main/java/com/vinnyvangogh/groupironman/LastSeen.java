package com.vinnyvangogh.groupironman;

/**
 * When this client last saw a member online, and on which world: from their own client (the party), from this
 * player's own login, or from the game's Group channel for a member without the plugin. Kept in the group cache, so
 * the live board's "last seen" survives a restart. Immutable.
 */
final class LastSeen
{
	/** Epoch millis. */
	final long at;
	/** 0 when not known. */
	final int world;
	/** True once any sighting came from the member's own plugin client. */
	final boolean plugin;

	LastSeen(long at, int world, boolean plugin)
	{
		this.at = at;
		this.world = world;
		this.plugin = plugin;
	}

	/** The newer sighting's time and world (the older world when the newer has none); plugin once either had it. */
	static LastSeen merge(LastSeen a, LastSeen b)
	{
		LastSeen newer = b.at >= a.at ? b : a;
		LastSeen older = newer == b ? a : b;
		int world = newer.world > 0 ? newer.world : older.world;
		return new LastSeen(newer.at, world, a.plugin || b.plugin);
	}
}
