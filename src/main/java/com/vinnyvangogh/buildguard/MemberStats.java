package com.vinnyvangogh.buildguard;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A member's live state: real and boosted level per skill, run energy,
 * special attack, world and a coarse location.
 *
 * <p>Hitpoints and Prayer are not separate fields: current HP is the boosted
 * Hitpoints level and current prayer the boosted Prayer level, which is how
 * the game itself stores them.
 */
final class MemberStats
{
	/** Real and boosted level. */
	static final class Level
	{
		final int real;
		final int boosted;

		Level(int real, int boosted)
		{
			this.real = real;
			this.boosted = boosted;
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof Level && ((Level) o).real == real && ((Level) o).boosted == boosted;
		}

		@Override
		public int hashCode()
		{
			return real * 1000 + boosted;
		}
	}

	private final Map<String, Level> skills;
	private final int runEnergy;
	private final int specialAttack;
	private final int world;
	private final int region;

	/**
	 * @param runEnergy percent, 0 to 100
	 * @param specialAttack percent, 0 to 100
	 * @param region the 64x64 map region id, or 0 when unknown
	 */
	MemberStats(Map<String, Level> skills, int runEnergy, int specialAttack, int world, int region)
	{
		this.skills = Collections.unmodifiableMap(new LinkedHashMap<>(skills));
		this.runEnergy = runEnergy;
		this.specialAttack = specialAttack;
		this.world = world;
		this.region = region;
	}

	Map<String, Level> skills()
	{
		return skills;
	}

	int runEnergy()
	{
		return runEnergy;
	}

	int specialAttack()
	{
		return specialAttack;
	}

	int world()
	{
		return world;
	}

	int region()
	{
		return region;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof MemberStats))
		{
			return false;
		}
		MemberStats m = (MemberStats) o;
		return skills.equals(m.skills) && runEnergy == m.runEnergy && specialAttack == m.specialAttack
			&& world == m.world && region == m.region;
	}

	@Override
	public int hashCode()
	{
		return skills.hashCode() * 31 + runEnergy * 7 + specialAttack * 13 + world * 17 + region;
	}
}
