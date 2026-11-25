package com.vinnyvangogh.groupironman;

import java.util.*;
import java.util.List;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.VarbitID;

/**
 * The Fortis Colosseum modifiers the player has already chosen, with their tier, read from the game's own level
 * varbits: "Mantimayhem II, Myopia I". Only what the player picked at the pillar; never a spawn, timer or hazard
 * (docs/plans/activities.md §1.2 row 3).
 *
 * <p>Verified against runelite-api 1.13.6 with {@code javap -constants net.runelite.api.gameval.VarbitID}: each
 * {@code COLOSSEUM_MODIFIER_*_STACKS_CLIENT} constant below exists. The mapping (and that Bees! is the game's
 * "toxicity" varbit) follows the Hub plugin LlemonDuck/fortis-colosseum, {@code util/Modifier.java}, which reads the
 * same ids (9791, 9790, 10681, 9796, 4588, 9795, 9792, 9798, 9797, 9799). A level of 0 is not chosen.
 *
 * <p>Not here: Dynamic Duo, Quartet, Red Flag and Totemic have no level varbit (one tier only); the Hub plugin reads
 * them from a script argument, a raw script id with no gameval constant, so this slice leaves them out.
 */
final class ColosseumModifiers
{
	private static final String[] NAMES = {"Bees!", "Blasphemy", "Doom", "Frailty", "Mantimayhem", "Myopia",
		"Reentry", "Relentless", "Solarflare", "Volatility"};
	private static final int[] LEVEL_VARBITS = {
		VarbitID.COLOSSEUM_MODIFIER_TOXICITY_STACKS_CLIENT,
		VarbitID.COLOSSEUM_MODIFIER_BLASPHEMY_STACKS_CLIENT,
		VarbitID.COLOSSEUM_MODIFIER_DOOM_STACKS_CLIENT,
		VarbitID.COLOSSEUM_MODIFIER_FRAILTY_STACKS_CLIENT,
		VarbitID.COLOSSEUM_MODIFIER_MANTIMAYHEM_STACKS_CLIENT,
		VarbitID.COLOSSEUM_MODIFIER_MYOPIA_STACKS_CLIENT,
		VarbitID.COLOSSEUM_MODIFIER_REENTRY_STACKS_CLIENT,
		VarbitID.COLOSSEUM_MODIFIER_RELENTLESS_STACKS_CLIENT,
		VarbitID.COLOSSEUM_MODIFIER_SOLARFLARE_STACKS_CLIENT,
		VarbitID.COLOSSEUM_MODIFIER_VOLATILITY_STACKS_CLIENT,
	};
	private static final String[] TIERS = {"", "I", "II", "III"};

	private ColosseumModifiers()
	{
	}

	/** Every varbit read, for the engine's change check. */
	static int[] varbits()
	{
		return LEVEL_VARBITS.clone();
	}

	/** "Mantimayhem II, Myopia I", in name order; empty when none chosen. */
	static String describe(IntUnaryOperator varbit)
	{
		List<String> out = new ArrayList<>();
		for (int i = 0; i < NAMES.length; i++)
		{
			int level = varbit.applyAsInt(LEVEL_VARBITS[i]);
			if (level > 0)
			{
				out.add(NAMES[i] + " " + TIERS[Math.min(level, TIERS.length - 1)]);
			}
		}
		return String.join(", ", out);
	}
}
