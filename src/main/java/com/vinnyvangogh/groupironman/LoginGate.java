package com.vinnyvangogh.groupironman;

import net.runelite.api.GameState;

/**
 * Whose values the progress baselines hold, and when they count. Nothing earned before this login ever posts
 * (Board 2026-11-15, after an account switch posted the other account's old diaries and levels to Discord):
 *
 * <ul>
 * <li>Every state that is not in the game ({@link #endsSession}: the login screen, logging in, a hop, a lost
 * connection) forgets every baseline. The reset runs first, before anything else the engine does then, so nothing
 * that throws on the way out can skip it.</li>
 * <li>A different account than the one baselined (its hash, from any event that reads it) forgets them too, so a
 * switch the game states never show still starts over.</li>
 * <li>After a login, variables wait {@link #VAR_WAIT_TICKS}, and until {@link #SETTLE_TICKS} every value (skills,
 * total, combat level, quest points, diaries) only records: the game's login burst, and a value it sends a few
 * ticks late, is never read as progress.</li>
 * </ul>
 * <p>{@link LoginBurst} is the net under all of this. Client thread only.
 */
final class LoginGate
{
	/** Ticks after a login before variables are read: the login's burst of values has settled by then. */
	static final int VAR_WAIT_TICKS = 5;
	/** Ticks after a login before any value counts as progress: about 9 seconds. */
	static final int SETTLE_TICKS = 15;
	/** {@code Client.getAccountHash()} when logged out. */
	static final long NO_ACCOUNT = -1;

	private final ProgressTracker progress;
	private final EventWatchers watchers;
	private final LoginBurst own;
	private final LoginBurst broadcasts;
	private long account = NO_ACCOUNT;
	private int varWait = -1;
	private int settle = -1;

	LoginGate(ProgressTracker progress, EventWatchers watchers, LoginBurst own, LoginBurst broadcasts)
	{
		this.progress = progress;
		this.watchers = watchers;
		this.own = own;
		this.broadcasts = broadcasts;
	}

	/** Every state but in the game: the next values may be another account's. */
	static boolean endsSession(GameState state)
	{
		return state != GameState.LOGGED_IN && state != GameState.LOADING;
	}

	/**
	 * Out of the game: every baseline starts over, and nothing is read until the next login. The watchers keep what
	 * is not a baseline (a grave's timer survives a hop); the engine resets them fully at the login screen.
	 */
	void startOver()
	{
		forget();
		account = NO_ACCOUNT;
		varWait = -1;
		settle = -1;
	}

	/** In the game, the profile changed: start over as if this were a new login. */
	void rebaseline(long now)
	{
		forget();
		begin(now);
	}

	private void forget()
	{
		progress.reset();
		watchers.rebaseline();
	}

	/**
	 * The account the client is logged in as now ({@code NO_ACCOUNT} when none: logged out, or a login still in
	 * progress). A different one than the baselines hold forgets them and starts a new login's wait; true then. No
	 * account after one was known is a session's end, whatever the game states said.
	 */
	boolean account(long hash, long now)
	{
		if (hash == account)
		{
			return false;
		}
		if (hash == NO_ACCOUNT)
		{
			startOver();
			return false;
		}
		boolean switched = account != NO_ACCOUNT;
		account = hash;
		if (switched)
		{
			// Another account: nothing the last one was in the middle of is this one's.
			progress.reset();
			watchers.reset();
		}
		if (switched || varWait < 0)
		{
			begin(now);
		}
		return switched;
	}

	/** The first tick of a login, with the player loaded. A login already begun keeps its wait. */
	void login(long now)
	{
		if (varWait < 0)
		{
			begin(now);
		}
	}

	private void begin(long now)
	{
		progress.quiet();
		watchers.quiet();
		varWait = VAR_WAIT_TICKS;
		settle = SETTLE_TICKS;
		own.open(now);
		broadcasts.open(now);
	}

	/** Each tick while logged in. True when variables may be read this tick. */
	boolean tick()
	{
		if (settle > 0 && --settle == 0)
		{
			progress.settle();
			watchers.settle();
		}
		if (varWait > 0)
		{
			varWait--;
			return false;
		}
		return varWait == 0;
	}

	/** Values are counted as progress again (the login's quiet ticks are over). */
	boolean settled()
	{
		return settle <= 0 && varWait == 0;
	}
}
