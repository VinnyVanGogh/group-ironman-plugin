package com.vinnyvangogh.buildguard;

import net.runelite.api.Skill;

/**
 * The seven levels that feed the combat level formula.
 *
 * A direct port of {@code osrs.Stats} in the questpath Go app. Immutable, so
 * {@link #with(Skill, int)} answers "what if this one skill were higher"
 * without the caller having to defend a shared instance across the client
 * thread and the Swing EDT.
 */
public final class CombatStats
{
	/** The combat seven, in the order the in-game skill panel shows them. */
	public static final Skill[] COMBAT_SKILLS = {
		Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.HITPOINTS,
		Skill.RANGED, Skill.MAGIC, Skill.PRAYER
	};

	/** The lowest level any skill can hold. Hitpoints starts at 10. */
	public static final int MIN_LEVEL = 1;

	/** The highest real (non-virtual) level in any skill. */
	public static final int MAX_LEVEL = 99;

	private final int attack;
	private final int strength;
	private final int defence;
	private final int hitpoints;
	private final int ranged;
	private final int magic;
	private final int prayer;

	public CombatStats(int attack, int strength, int defence, int hitpoints, int ranged, int magic, int prayer)
	{
		this.attack = attack;
		this.strength = strength;
		this.defence = defence;
		this.hitpoints = hitpoints;
		this.ranged = ranged;
		this.magic = magic;
		this.prayer = prayer;
	}

	/**
	 * Returns the level for one of the seven combat skills, or -1 for any
	 * other skill.
	 *
	 * -1 rather than 0: zero is not a level any skill can hold, but it is a
	 * plausible-looking number to accidentally do arithmetic on.
	 */
	public int get(Skill skill)
	{
		switch (skill)
		{
			case ATTACK:
				return attack;
			case STRENGTH:
				return strength;
			case DEFENCE:
				return defence;
			case HITPOINTS:
				return hitpoints;
			case RANGED:
				return ranged;
			case MAGIC:
				return magic;
			case PRAYER:
				return prayer;
			default:
				return -1;
		}
	}

	/**
	 * Returns a copy with one combat skill set to {@code level}. A skill
	 * outside the combat seven returns an identical copy rather than throwing:
	 * callers sweep every skill and ask.
	 */
	public CombatStats with(Skill skill, int level)
	{
		switch (skill)
		{
			case ATTACK:
				return new CombatStats(level, strength, defence, hitpoints, ranged, magic, prayer);
			case STRENGTH:
				return new CombatStats(attack, level, defence, hitpoints, ranged, magic, prayer);
			case DEFENCE:
				return new CombatStats(attack, strength, level, hitpoints, ranged, magic, prayer);
			case HITPOINTS:
				return new CombatStats(attack, strength, defence, level, ranged, magic, prayer);
			case RANGED:
				return new CombatStats(attack, strength, defence, hitpoints, level, magic, prayer);
			case MAGIC:
				return new CombatStats(attack, strength, defence, hitpoints, ranged, level, prayer);
			case PRAYER:
				return new CombatStats(attack, strength, defence, hitpoints, ranged, magic, level);
			default:
				return this;
		}
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof CombatStats))
		{
			return false;
		}
		CombatStats other = (CombatStats) o;
		return attack == other.attack
			&& strength == other.strength
			&& defence == other.defence
			&& hitpoints == other.hitpoints
			&& ranged == other.ranged
			&& magic == other.magic
			&& prayer == other.prayer;
	}

	@Override
	public int hashCode()
	{
		int result = attack;
		result = 31 * result + strength;
		result = 31 * result + defence;
		result = 31 * result + hitpoints;
		result = 31 * result + ranged;
		result = 31 * result + magic;
		result = 31 * result + prayer;
		return result;
	}

	@Override
	public String toString()
	{
		return "CombatStats{att=" + attack + " str=" + strength + " def=" + defence
			+ " hp=" + hitpoints + " rng=" + ranged + " mag=" + magic + " pray=" + prayer + "}";
	}
}
