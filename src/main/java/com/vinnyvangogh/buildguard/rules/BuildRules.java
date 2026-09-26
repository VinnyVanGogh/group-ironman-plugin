package com.vinnyvangogh.buildguard.rules;

/**
 * Scope item 1 — the per-skill build rules and the XP headroom left under
 * each cap.
 *
 * <p><b>Not implemented.</b> Nothing here is ported yet. The rule model, its
 * codec, the per-account store and the editor all exist in questpath-plugin;
 * `docs/SOURCE-MAP.md` names the files and says what each one drags in.
 *
 * <p>Two things the port must not lose, both recorded in the source map:
 * a rule's blocking-ness is derived from its <em>type</em> and not trusted
 * from a wire field, and the four types are two different sentences — NO_XP
 * and HARD_CAP end a build, SOFT_CAP and GOAL only inform.
 */
public final class BuildRules
{
	private BuildRules()
	{
	}

	/**
	 * The XP still available in a skill before its cap is crossed.
	 *
	 * @throws UnsupportedOperationException always, until this is ported
	 */
	public static long headroom(String skill)
	{
		throw new UnsupportedOperationException(
			"Build rules are not implemented: see docs/SPEC.md scope item 1 and docs/SOURCE-MAP.md");
	}
}
