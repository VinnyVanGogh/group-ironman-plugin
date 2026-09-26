package com.vinnyvangogh.buildguard.warning;

/**
 * Scope item 3 — the warning shown when the player starts a quest that is
 * build-blocked or unreachable.
 *
 * <p><b>Not implemented.</b> Nothing decides when this fires yet, and it must
 * not guess: a warning that fires on the wrong quest teaches the player to
 * dismiss it, and then it is not there for the one that matters.
 *
 * <p>It warns and nothing more. It never removes the quest's menu entries and
 * never blocks the click — see `BRIEF.md` Phase 0 for why that line is where
 * it is.
 */
public final class QuestStartWarning
{
	private QuestStartWarning()
	{
	}

	/**
	 * Whether starting this quest should warn, and what the warning says.
	 *
	 * @throws UnsupportedOperationException always, until this is built
	 */
	public static String warningFor(long questId)
	{
		throw new UnsupportedOperationException(
			"Quest start warnings are not implemented: see docs/SPEC.md scope item 3");
	}
}
