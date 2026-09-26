package com.vinnyvangogh.buildguard;

/**
 * The one verdict a quest gets, matching questpath's domain.QuestStatus
 * exactly.
 *
 * <p>The names are the wire contract with the generated planner vectors, so
 * they are questpath's spelling and not Java's conventions.
 *
 * <p>The distinction the whole plugin exists for is {@link #permanent}:
 * SKILL_BLOCKED means go and train, BUILD_BLOCKED and UNREACHABLE mean this
 * quest is off the table for as long as the build stands. Every other quest
 * tool tells you the first; the second is the answer a capped account
 * actually needs, and it is the one that is expensive to be wrong about.
 */
enum QuestStatus
{
	/** Already done. */
	COMPLETE,
	/** Every requirement met; do it now. */
	ELIGIBLE,
	/** Every requirement met once a temporary boost is applied. */
	BOOSTABLE,
	/** A prerequisite quest is not done, but all of them are reachable. */
	QUEST_BLOCKED,
	/** A skill level is short, and training would close it. */
	SKILL_BLOCKED,
	/** This quest itself breaks a build rule. Permanent. */
	BUILD_BLOCKED,
	/** A prerequisite is permanently blocked, so this one is too. Permanent. */
	UNREACHABLE,
	/** Marked as never going to be done. Permanent. */
	REFUSED;

	/**
	 * Whether this verdict means "not while the build stands" rather than
	 * "not yet".
	 */
	boolean permanent()
	{
		return this == BUILD_BLOCKED || this == UNREACHABLE || this == REFUSED;
	}

	/** Whether the account can start this quest right now. */
	boolean doable()
	{
		return this == ELIGIBLE || this == BOOSTABLE;
	}
}
