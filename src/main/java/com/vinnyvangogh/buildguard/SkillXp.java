package com.vinnyvangogh.buildguard;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Skill;

/**
 * An account's real experience per skill, with "not loaded yet" kept distinct
 * from a real value.
 *
 * <p>The panel used to price everything from level thresholds alone —
 * {@code xpFor(target) - xpFor(current)} — which is an upper bound, not a cost.
 * Someone 90% of the way through Strength 83 was told the next level costs
 * 278.3k when it actually costs a fraction of that. A wrong number in the
 * place where an exact one belongs is worse than no number, so this type
 * exists to make the difference representable.
 *
 * <p>Unknown is a real state. A client that has reached LOGGED_IN has not
 * necessarily received the stats packet, and {@code getSkillExperience}
 * answers 0 until it has. Zero is indistinguishable from a genuinely untrained
 * skill, so it is validated rather than trusted: a skill's XP must be at least
 * the threshold for the level the client reports. Level 2 with 40 XP is
 * impossible in the real game, so it means the data has not arrived.
 */
public final class SkillXp
{
	private static final SkillXp UNKNOWN = new SkillXp(new EnumMap<>(Skill.class));

	private final Map<Skill, Integer> xp;

	private SkillXp(Map<Skill, Integer> xp)
	{
		this.xp = xp;
	}

	/**
	 * A cheap identity for what this holds, so a panel can skip rebuilding
	 * itself when the numbers behind it have not moved.
	 *
	 * The Combat tab is rebuilt from this and a CombatStats, and it was being
	 * torn down and remade on every refresh -- about thirty components thrown
	 * away and recreated for a picture that changes when you gain experience,
	 * not when a bank window opens.
	 */
	public int signature()
	{
		return xp.hashCode();
	}

	/** Nothing is known. Every lookup reports unknown. */
	public static SkillXp unknown()
	{
		return UNKNOWN;
	}

	/**
	 * Builds from raw client readings, dropping any skill whose XP disagrees
	 * with its level.
	 *
	 * @param levels     the level the client reports per skill
	 * @param rawXp      the XP the client reports per skill
	 */
	public static SkillXp of(Map<Skill, Integer> levels, Map<Skill, Integer> rawXp)
	{
		Map<Skill, Integer> validated = new EnumMap<>(Skill.class);
		for (Map.Entry<Skill, Integer> entry : rawXp.entrySet())
		{
			Skill skill = entry.getKey();
			Integer value = entry.getValue();
			Integer level = levels.get(skill);
			if (value == null || level == null || value < 0)
			{
				continue;
			}
			// The whole validation: XP below the threshold for the reported
			// level cannot happen on a loaded account.
			if (value < XpTable.xpFor(level))
			{
				continue;
			}
			validated.put(skill, value);
		}
		return new SkillXp(validated);
	}

	/** Whether this skill's experience is known. */
	public boolean known(Skill skill)
	{
		return xp.containsKey(skill);
	}

	/** This skill's experience, or -1 when unknown. */
	public int get(Skill skill)
	{
		Integer value = xp.get(skill);
		return value == null ? -1 : value;
	}

	/**
	 * The experience still needed to reach {@code targetLevel} in this skill,
	 * or -1 when the skill's XP is unknown.
	 *
	 * Zero when the target is already reached — a cost of nothing is a real
	 * answer, unlike an unknown one.
	 */
	public long toLevel(Skill skill, int targetLevel)
	{
		if (!known(skill))
		{
			return -1;
		}
		return Math.max(0, XpTable.xpFor(targetLevel) - get(skill));
	}
}
