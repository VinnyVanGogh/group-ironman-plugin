package com.vinnyvangogh.buildguard;

/**
 * What the advisor says about pointing one lamp at one skill.
 *
 * <p>A port of questpath's {@code domain.LampSkillVerdict}. The enum names
 * are the wire contract with the shared golden vectors, so they are
 * questpath's spelling rather than Java's conventions — the same rule
 * {@link QuestStatus} follows and for the same reason.
 *
 * <p>Four states, not two, because "never do this" and "be careful here" are
 * different sentences. Drawing them the same way makes the loud one stop
 * meaning anything, which is the failure the whole build-guard idea is
 * designed against.
 */
class LampVerdict
{
	/** The one classification a skill gets. Exactly one applies. */
	enum State
	{
		/** Never point the lamp here. Drawn greyed out. */
		BLOCKED,
		/**
		 * Legal, but it costs something the account has said it cares about.
		 * Never suggested, and deliberately not drawn as grey: the account
		 * may not have written a rule for this, and greying it would claim
		 * it had.
		 */
		RISKY,
		/**
		 * The catalogue does not carry this lamp's experience and this skill is
		 * capped, so whether the lamp crosses the cap is unanswerable. Not
		 * greyed — the skill may well be fine — and never ranked.
		 */
		UNKNOWN,
		/** Nothing stands in the way. Only these are ranked. */
		OK
	}

	/**
	 * Why a skill is greyed out.
	 *
	 * <p>All of these mean "not here", and they are still not the same fact:
	 * {@link #BELOW_MINIMUM} stops applying on its own as the account trains,
	 * and the others never do.
	 */
	enum Block
	{
		/** No reason; the verdict is not blocked. */
		NONE,
		/** A NO_XP rule. Does not depend on the lamp's amount. */
		NO_XP,
		/** A HARD_CAP the lamp's experience would push the level past. */
		HARD_CAP,
		/**
		 * A HARD_CAP the account has already reached. Legal — the level
		 * cannot move — but the experience is thrown away.
		 */
		AT_CAP,
		/** The lamp's own level floor, not a build rule. */
		BELOW_MINIMUM,
		/** The lamp does not allow this skill at all. */
		NOT_ALLOWED
	}

	/** Why a legal skill is still not suggested. */
	enum Risk
	{
		/** No risk. */
		NONE,
		/**
		 * A SOFT_CAP rule. SOFT_CAP carries no level, so any experience at
		 * all is the thing the account asked to be told about.
		 */
		SOFT_CAP,
		/**
		 * The lamp would carry Ranged or Magic above the level at which it
		 * starts raising the combat level. Nothing blocks it — the account
		 * may never have set a cap — but on a build whose entire point is a
		 * combat level it is the most expensive square on the interface, and
		 * it looks exactly like the others.
		 */
		FREE_CEILING
	}

	/** The band a rankable skill falls in. Lower sorts first. */
	enum Tier
	{
		/**
		 * A skill with a level the saved route still needs. A lamp here does
		 * not merely add experience, it removes a gate.
		 */
		ROUTE,
		/** A skill carrying the account's own GOAL rule, not yet met. */
		GOAL,
		/**
		 * Every other legal skill. Still ranked, because "nothing here is
		 * ranked above anything else" is a worse answer than an honest
		 * ordering by what the lamp actually buys.
		 */
		OTHER
	}

	private final String skill;
	private final State state;
	private final Block block;
	private final Risk risk;
	private final double xp;
	private final boolean xpKnown;
	private final int levelNow;
	private final int levelAfter;
	private double levelsGained;
	private final String reason;

	private int rank;
	private Tier tier = Tier.OTHER;

	LampVerdict(String skill, State state, Block block, Risk risk,
		double xp, boolean xpKnown, int levelNow, int levelAfter,
		double levelsGained, String reason)
	{
		this.skill = skill;
		this.state = state;
		this.block = block;
		this.risk = risk;
		this.xp = xp;
		this.xpKnown = xpKnown;
		this.levelNow = levelNow;
		this.levelAfter = levelAfter;
		this.levelsGained = levelsGained;
		this.reason = reason;
	}

	String skill()
	{
		return skill;
	}

	State state()
	{
		return state;
	}

	Block block()
	{
		return block;
	}

	Risk risk()
	{
		return risk;
	}

	double xp()
	{
		return xp;
	}

	boolean xpKnown()
	{
		return xpKnown;
	}

	int levelNow()
	{
		return levelNow;
	}

	int levelAfter()
	{
		return levelAfter;
	}

	double levelsGained()
	{
		return levelsGained;
	}

	String reason()
	{
		return reason == null ? "" : reason;
	}

	/** 1 for the best target, 2 and 3 for the next two, 0 for unranked. */
	int rank()
	{
		return rank;
	}

	Tier tier()
	{
		return tier;
	}

	void rankAs(int rank, Tier tier)
	{
		this.rank = rank;
		this.tier = tier;
	}

	void setTier(Tier tier)
	{
		this.tier = tier;
	}

	/**
	 * Recorded by the ranking rather than by the classification, because the
	 * clamp depends on what the account is working towards and only the
	 * ranking knows which tier a skill landed in.
	 */
	void setLevelsGained(double levelsGained)
	{
		this.levelsGained = levelsGained;
	}

	boolean blocked()
	{
		return state == State.BLOCKED;
	}

	boolean suggested()
	{
		return rank > 0;
	}
}
