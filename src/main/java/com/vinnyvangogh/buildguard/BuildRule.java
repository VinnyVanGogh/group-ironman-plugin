package com.vinnyvangogh.buildguard;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Skill;

/**
 * One skill's declared limit: no XP at all, a hard cap, a soft cap, or a goal.
 *
 * <p>A port of questpath's rule model, with one thing severed. There, rules
 * arrived over a push from a web app and the plugin only ever displayed them.
 * Build Guard has no web app and makes no requests, so the account is the only
 * author of its own rules and this plugin is the only editor of them.
 *
 * <p>The distinction that matters is {@link #blocking}: NO_XP and HARD_CAP end
 * a build if crossed, while SOFT_CAP and GOAL only inform. Drawing them the
 * same would turn "you are approaching a goal" into "you are about to ruin
 * your account", and the second warning stops meaning anything if it fires for
 * the first.
 */
class BuildRule
{
	/**
	 * The four rule types, spelled as the generated vectors spell them. That
	 * spelling is a wire contract with {@code planner-vectors.json} and {@code
	 * lamp-vectors.json}, so it is questpath's and not Java's conventions.
	 */
	static final String NO_XP = "NO_XP";
	static final String HARD_CAP = "HARD_CAP";
	static final String SOFT_CAP = "SOFT_CAP";
	static final String GOAL = "GOAL";

	@SerializedName("skill")
	private String skill;

	@SerializedName("type")
	private String type;

	/** The cap or target level. Absent — and so zero — when the rule carries none. */
	@SerializedName("value")
	private int value;

	/**
	 * Vestigial, and kept deliberately.
	 *
	 * <p>It was the wire field on a push response, and Build Guard has no push.
	 * Nothing sets it but {@link #local}, and {@link #blocking()} derives the
	 * answer from the type regardless. It stays because the generated vectors
	 * are parsed into this same class and a future export may carry it, and
	 * because dropping it would invite dropping the OR in {@code blocking()}
	 * with it -- see that method for what that cost last time.
	 */
	@SerializedName("blocking")
	private boolean blocking;

	@SerializedName("note")
	private String note;

	/**
	 * A rule the player wrote themselves — in Build Guard, the only kind there
	 * is.
	 *
	 * <p>Deliberately the same type as a rule parsed from JSON. Nothing
	 * downstream — not the verdict engine, not the lamp advisor — should be able
	 * to tell an authored rule from a decoded one: a rule is a rule, and giving
	 * them separate types would mean two code paths through every check, which
	 * is how one of them comes to be missing a case.
	 */
	static BuildRule local(String skill, String type, int value, String note)
	{
		BuildRule rule = new BuildRule();
		rule.skill = skill;
		rule.type = type;
		rule.value = value;
		rule.note = note;
		// blocking() derives this from the type regardless; it is set here
		// for anything that serialises the rule back out.
		rule.blocking = NO_XP.equals(type) || HARD_CAP.equals(type);
		return rule;
	}

	String skillName()
	{
		return skill == null ? "" : skill;
	}

	String type()
	{
		return type == null ? "" : type;
	}

	int value()
	{
		return value;
	}

	/** Whether the rule carries a level at all. SOFT_CAP never does. */
	boolean hasValue()
	{
		return value > 0;
	}

	/**
	 * Whether crossing this rule ends the build.
	 *
	 * Derived from the type as well as read from the field, and the two are
	 * OR-ed rather than the field being trusted alone. <b>Neither generated
	 * vector file carries a {@code blocking} key at all</b>, so trusting the
	 * field alone makes every rule in them non-blocking.
	 *
	 * <p><b>Measured here, against this repo's bundle, on 2026-09-17</b> by
	 * replacing this method's body with {@code return blocking;} and running
	 * {@code PlannerVectorsTest}: <b>99 of 218 verdicts disagree</b> with the Go
	 * planner. 58 of them should have been BUILD_BLOCKED and 41 UNREACHABLE; 13
	 * came back flatly ELIGIBLE for quests that end a pure. questpath's own
	 * javadoc records 94 for the same experiment — a figure from an earlier
	 * graph version, which is why the number is re-measured here rather than
	 * carried over.
	 *
	 * <p>Do not "simplify" this to the field, and do not simplify it to the type
	 * either. Both readings pass a casual review, and the first has already
	 * shipped a plugin that told a capped account it could do 13 quests that
	 * would end it.
	 */
	boolean blocking()
	{
		return blocking || NO_XP.equals(type()) || HARD_CAP.equals(type());
	}

	String note()
	{
		return note == null ? "" : note;
	}

	/**
	 * The client Skill this rule is about, or null when the rule names one this
	 * client does not know.
	 *
	 * Null rather than a guess: a stored rule naming a skill the client does
	 * not have is a real disagreement -- a rule saved against a newer game --
	 * and the UI says so instead of silently dropping a rule that might be the
	 * one ending the build.
	 */
	Skill skill()
	{
		for (Skill candidate : Skill.values())
		{
			if (candidate.getName().equalsIgnoreCase(skillName()))
			{
				return candidate;
			}
		}
		return null;
	}

	/** A short description of the limit, for a row that already names the skill. */
	String describe()
	{
		switch (type())
		{
			case NO_XP:
				return "no xp at all";
			case HARD_CAP:
				return hasValue() ? "capped at " + value : "capped";
			case SOFT_CAP:
				return "warns, never blocks";
			case GOAL:
				return hasValue() ? "goal " + value : "goal";
			default:
				// A type this build does not know. Shown rather than hidden: a
				// rule silently missing from this list is worse than one
				// rendered plainly. BuildRuleCodec refuses to decode one of
				// these at all, so reaching here means the rule was built in
				// memory rather than read back from storage.
				return type();
		}
	}

	/**
	 * Orders rules the way they matter: what ends a build first, then caps,
	 * then goals, and alphabetically within each.
	 */
	static List<BuildRule> ordered(List<BuildRule> rules)
	{
		List<BuildRule> out = new ArrayList<>(rules);
		Collections.sort(out, (a, b) ->
		{
			int byWeight = Integer.compare(weight(a), weight(b));
			return byWeight != 0 ? byWeight : a.skillName().compareToIgnoreCase(b.skillName());
		});
		return out;
	}

	private static int weight(BuildRule rule)
	{
		switch (rule.type())
		{
			case NO_XP:
				return 0;
			case HARD_CAP:
				return 1;
			case SOFT_CAP:
				return 2;
			case GOAL:
				return 3;
			default:
				return 4;
		}
	}
}
