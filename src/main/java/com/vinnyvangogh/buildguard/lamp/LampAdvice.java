package com.vinnyvangogh.buildguard.lamp;

/**
 * Scope item 4 — the lamp advisor: grey the skill squares a build rule
 * closes, box the ones worth choosing.
 *
 * <p><b>Not implemented.</b> questpath-plugin has the whole stack — advisor,
 * verdict states, lamp catalogue, the item-click watcher that identifies which
 * lamp is being used, the interface's component ids, and the overlay that
 * draws it — plus nine shared golden vectors. `docs/SOURCE-MAP.md` names every
 * file. This is a port, not a rewrite.
 *
 * <p>Four verdict states, not two, and the port must keep all four: "never do
 * this", "this costs something you said you care about", "the lamp's amount is
 * unknown and this skill is capped, so the honest answer is that nobody
 * knows", and "this is fine". Collapsing them makes the loud one stop meaning
 * anything.
 *
 * <p>It draws over the interface and does nothing else. It does not hide
 * squares, does not remove menu entries, and does not consume clicks.
 */
public final class LampAdvice
{
	private LampAdvice()
	{
	}

	/**
	 * What the advisor says about pointing the held lamp at one skill.
	 *
	 * @throws UnsupportedOperationException always, until this is ported
	 */
	public static String adviseFor(String skill)
	{
		throw new UnsupportedOperationException(
			"The lamp advisor is not implemented: see docs/SPEC.md scope item 4 and docs/SOURCE-MAP.md");
	}
}
