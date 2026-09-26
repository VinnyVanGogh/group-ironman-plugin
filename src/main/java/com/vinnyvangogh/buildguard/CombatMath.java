package com.vinnyvangogh.buildguard;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Skill;

/**
 * The combat level formula, and the two questions a pure account actually asks
 * of it.
 *
 * A direct port of {@code internal/osrs/combat.go} in the questpath Go app,
 * carrying the same test vectors. It is pure arithmetic with no client
 * dependency, which is why it is the first thing this plugin does: it needs no
 * quest data, no server, and no network.
 *
 * All integer division here is deliberate and matches the game: {@code
 * Prayer / 2} and {@code Ranged / 2} floor, they do not round.
 */
public final class CombatMath
{
	private CombatMath()
	{
	}

	/**
	 * The unfloored combat level.
	 *
	 * <pre>
	 * base   = 0.25  * (Defence + Hitpoints + floor(Prayer / 2))
	 * melee  = 0.325 * (Attack + Strength)
	 * ranged = 0.325 * (floor(Ranged / 2) + Ranged)
	 * magic  = 0.325 * (floor(Magic / 2) + Magic)
	 * combat = base + max(melee, ranged, magic)
	 * </pre>
	 *
	 * Exposed unfloored so a progress readout can show how far through the
	 * current level the account is, rather than only the integer the game
	 * displays.
	 */
	public static double combatLevelExact(CombatStats s)
	{
		double base = 0.25 * (s.get(Skill.DEFENCE) + s.get(Skill.HITPOINTS) + s.get(Skill.PRAYER) / 2);
		double melee = 0.325 * (s.get(Skill.ATTACK) + s.get(Skill.STRENGTH));
		double ranged = 0.325 * (s.get(Skill.RANGED) / 2 + s.get(Skill.RANGED));
		double magic = 0.325 * (s.get(Skill.MAGIC) / 2 + s.get(Skill.MAGIC));
		return base + Math.max(melee, Math.max(ranged, magic));
	}

	/** The combat level as the game displays it. */
	public static int combatLevel(CombatStats s)
	{
		return (int) Math.floor(combatLevelExact(s));
	}

	/** How far through the current combat level the stats are, in [0, 1). */
	public static double combatProgress(CombatStats s)
	{
		double exact = combatLevelExact(s);
		return exact - Math.floor(exact);
	}

	/**
	 * The highest Ranged level at which the ranged term does not exceed the
	 * melee term, and so does not raise the combat level. The maths is
	 * identical for Magic.
	 *
	 * <pre>
	 * meleeTerm = attack + strength
	 * max R such that floor(R/2) + R &lt;= meleeTerm
	 * </pre>
	 *
	 * This is the whole point of a pure build: every level below the ceiling
	 * is free, and the ceiling drifts upward every couple of Strength levels.
	 * Hardcoding it guarantees leaving levels on the table.
	 */
	public static int freeRangedCeiling(int attack, int strength)
	{
		int meleeTerm = attack + strength;
		int ceiling = CombatStats.MIN_LEVEL;
		for (int r = CombatStats.MIN_LEVEL; r <= CombatStats.MAX_LEVEL; r++)
		{
			if (r / 2 + r <= meleeTerm)
			{
				ceiling = r;
				continue;
			}
			break;
		}
		return ceiling;
	}

	/**
	 * For each of the seven combat skills, how many levels in that skill alone
	 * would raise the combat level by one.
	 *
	 * A skill is absent from the map when no level at or below 99 raises
	 * combat — for a pure, Ranged and Magic sit far enough below the melee
	 * term to be free for many levels. The value is the number of levels to
	 * add, never the resulting level; see {@link #nextCombatLevelAt} for that.
	 */
	public static Map<Skill, Integer> levelsToNextCombat(CombatStats s)
	{
		int target = combatLevel(s) + 1;
		Map<Skill, Integer> out = new EnumMap<>(Skill.class);

		for (Skill skill : CombatStats.COMBAT_SKILLS)
		{
			int current = s.get(skill);
			if (current < 0)
			{
				continue;
			}
			for (int level = current + 1; level <= CombatStats.MAX_LEVEL; level++)
			{
				if (combatLevel(s.with(skill, level)) >= target)
				{
					out.put(skill, level - current);
					break;
				}
			}
		}
		return out;
	}

	/**
	 * The level this skill must reach to raise the combat level by one, or -1
	 * when no level at or below 99 does.
	 */
	public static int nextCombatLevelAt(CombatStats s, Skill skill)
	{
		Integer delta = levelsToNextCombat(s).get(skill);
		if (delta == null)
		{
			return -1;
		}
		return s.get(skill) + delta;
	}

	/**
	 * The next Strength level that raises the free Ranged / Magic ceiling, and
	 * what it raises it to.
	 *
	 * The ceiling moves every two or three Strength levels, and knowing where
	 * the next step is turns "train Strength" into "two more Strength buys two
	 * more free Ranged levels". Null when no level at or below 99 moves it.
	 *
	 * Strength rather than Attack because the melee term is their sum and a
	 * pure is training one of them; pass the Attack you actually have.
	 */
	public static CeilingStep nextCeilingStep(int attack, int strength)
	{
		int current = freeRangedCeiling(attack, strength);
		for (int s = strength + 1; s <= CombatStats.MAX_LEVEL; s++)
		{
			int ceiling = freeRangedCeiling(attack, s);
			if (ceiling > current)
			{
				return new CeilingStep(s, ceiling, ceiling - current);
			}
		}
		return null;
	}

	/** Where the free ceiling next moves, and by how much. */
	public static final class CeilingStep
	{
		/** The Strength level that moves it. */
		public final int atStrength;
		/** The ceiling once it has moved. */
		public final int ceiling;
		/** How many free levels that step is worth. */
		public final int gained;

		CeilingStep(int atStrength, int ceiling, int gained)
		{
			this.atStrength = atStrength;
			this.ceiling = ceiling;
			this.gained = gained;
		}
	}
}
