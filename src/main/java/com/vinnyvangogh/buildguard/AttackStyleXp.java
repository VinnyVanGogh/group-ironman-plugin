package com.vinnyvangogh.buildguard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;

/**
 * Which skills the selected attack style trains, and which of a member's
 * rules that puts at risk.
 *
 * <p>The base client's {@code attackstyles} plugin answers the first half,
 * but its {@code AttackStyle} enum is package-private, so the mapping is
 * restated here. The resolution order is the same as
 * {@code AttackStylesPlugin#getWeaponTypeStyles} at runelite-client 1.12.39:
 * the weapon type's enum ({@code EnumID.WEAPON_STYLES}) lists struct ids,
 * each struct names its style ({@code ParamID.ATTACK_STYLE_NAME}), an
 * "Other" entry leaves its slot empty, and a "Defensive" in the sixth slot is
 * defensive casting. Pure: the plugin reads the names off the client and
 * passes them in.
 */
final class AttackStyleXp
{
	/** The style index the game uses for the casting slot; defensive casting is this plus the toggle. */
	static final int CASTING_INDEX = 4;

	private AttackStyleXp()
	{
	}

	/**
	 * Turns the struct style names for one weapon into style keys, slot by
	 * slot. Unknown names become null, which trains nothing: a style the game
	 * adds later is reported as "no known skill" rather than crashing.
	 */
	static List<String> resolveStyles(List<String> structNames)
	{
		List<String> out = new ArrayList<>();
		if (structNames == null)
		{
			return out;
		}
		for (String raw : structNames)
		{
			String style = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
			if ("OTHER".equals(style))
			{
				out.add(null);
				continue;
			}
			if (out.size() == CASTING_INDEX + 1 && "DEFENSIVE".equals(style))
			{
				style = "DEFENSIVE CASTING";
			}
			out.add(skillsFor(style).isEmpty() ? null : style);
		}
		return out;
	}

	/**
	 * The styles for the two weapon types the enum has no entry for, exactly
	 * as the base client hard-codes them. Anything else unknown has none.
	 */
	static List<String> fallbackStyles(int weaponType)
	{
		switch (weaponType)
		{
			case 22:
				return Arrays.asList("ACCURATE", "AGGRESSIVE", null, "DEFENSIVE", "CASTING", "DEFENSIVE CASTING");
			case 30:
				return Arrays.asList("ACCURATE", "AGGRESSIVE", "AGGRESSIVE", "DEFENSIVE");
			default:
				return Collections.emptyList();
		}
	}

	/**
	 * The skills trained by the style at {@code comMode} (varp
	 * {@code COM_MODE}), with {@code defensiveCasting} the
	 * {@code AUTOCAST_DEFMODE} varbit.
	 */
	static Set<Skill> skillsTrained(List<String> styles, int comMode, int defensiveCasting)
	{
		int index = comMode == CASTING_INDEX ? comMode + defensiveCasting : comMode;
		if (styles == null || index < 0 || index >= styles.size() || styles.get(index) == null)
		{
			return EnumSet.noneOf(Skill.class);
		}
		return skillsFor(styles.get(index));
	}

	/** The human name for a style key, for a warning line. */
	static String styleName(List<String> styles, int comMode, int defensiveCasting)
	{
		int index = comMode == CASTING_INDEX ? comMode + defensiveCasting : comMode;
		if (styles == null || index < 0 || index >= styles.size() || styles.get(index) == null)
		{
			return "";
		}
		String key = styles.get(index).toLowerCase(Locale.ROOT);
		return Character.toUpperCase(key.charAt(0)) + key.substring(1);
	}

	private static Set<Skill> skillsFor(String style)
	{
		switch (style)
		{
			case "ACCURATE":
				return EnumSet.of(Skill.ATTACK);
			case "AGGRESSIVE":
				return EnumSet.of(Skill.STRENGTH);
			case "DEFENSIVE":
				return EnumSet.of(Skill.DEFENCE);
			case "CONTROLLED":
				return EnumSet.of(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE);
			case "RANGING":
				return EnumSet.of(Skill.RANGED);
			case "LONGRANGE":
				return EnumSet.of(Skill.RANGED, Skill.DEFENCE);
			case "CASTING":
				return EnumSet.of(Skill.MAGIC);
			case "DEFENSIVE CASTING":
				return EnumSet.of(Skill.MAGIC, Skill.DEFENCE);
			default:
				return EnumSet.noneOf(Skill.class);
		}
	}

	/**
	 * The blocking rules on skills this style trains that the next XP drop
	 * would eat into.
	 *
	 * <p>NO_XP is always at risk. A HARD_CAP is at risk once the real level has
	 * reached the cap: below it, training toward the cap is the plan. A level
	 * the client has not reported is treated as at risk, because a wrong
	 * "safe" is paid for in experience that does not come back. SOFT_CAP and
	 * GOAL never are, as everywhere else in this plugin.
	 */
	static List<BuildRule> rulesAtRisk(Set<Skill> trained, List<BuildRule> rules, Map<Skill, Integer> realLevels)
	{
		List<BuildRule> out = new ArrayList<>();
		for (BuildRule rule : rules)
		{
			Skill skill = rule.skill();
			if (skill == null || !trained.contains(skill) || !rule.blocking())
			{
				continue;
			}
			if (BuildRule.HARD_CAP.equals(rule.type()) && rule.hasValue())
			{
				Integer level = realLevels.get(skill);
				if (level != null && level < rule.value())
				{
					continue;
				}
			}
			out.add(rule);
		}
		return out;
	}
}
