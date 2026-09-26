package com.vinnyvangogh.buildguard.verdict;

/**
 * Scope item 2 — one verdict per quest, with the reason that produced it
 * ("blocked via Death Plateau: 3,000 Attack XP").
 *
 * <p><b>Not implemented.</b> It throws rather than returning a verdict,
 * because a plausible wrong verdict is the one failure this plugin cannot
 * afford: an account acting on a false ELIGIBLE has spent XP it cannot get
 * back.
 *
 * <p>The engine to port is questpath-plugin's {@code QuestClassifier}, held
 * to 218 generated verdicts by {@code PlannerVectorsTest}. Those vectors come
 * across unchanged; see `docs/SOURCE-MAP.md`.
 *
 * <p>The rule the whole engine exists for: <b>build restrictions are about XP,
 * not levels.</b> Both sides are checked — a reward that would push a capped
 * skill past its cap, and a requirement that demands a level above one — and
 * a permanently blocked prerequisite poisons everything downstream of it.
 */
public final class QuestVerdictEngine
{
	private QuestVerdictEngine()
	{
	}

	/**
	 * The verdict for one quest, and why.
	 *
	 * @throws UnsupportedOperationException always, until this is ported
	 */
	public static String verdictFor(long questId)
	{
		throw new UnsupportedOperationException(
			"The verdict engine is not implemented: see docs/SPEC.md scope item 2 and docs/SOURCE-MAP.md");
	}
}
