package com.vinnyvangogh.buildguard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.runelite.api.Skill;

/**
 * Reads the plain-text rule box in the config panel.
 *
 * <p>One {@code Skill: limit} per line or per comma:
 * <pre>
 * Defence: 1          hard cap at level 1
 * Prayer: none        no XP at all (also "no xp")
 * Strength: goal 99   goal, informs only
 * Ranged: soft        soft cap, informs only
 * </pre>
 *
 * <p>This is the manual per-skill entry questpath-plugin has, in the one input
 * RuneLite's config panel offers without a custom editor. Whether presets come
 * later is still open in {@code docs/SPEC.md}. A line that does not parse is
 * reported by {@link #problems} and never guessed at: a rule silently read as
 * something else is a rule that does not protect the account.
 */
final class RuleText
{
	private RuleText()
	{
	}

	static List<BuildRule> parse(String text)
	{
		List<BuildRule> rules = new ArrayList<>();
		for (String entry : entries(text))
		{
			Parsed p = parseEntry(entry);
			if (p.problem == null)
			{
				rules = BuildRuleCodec.set(rules, p.skill, p.type, p.value, "");
			}
		}
		return rules;
	}

	/** One readable sentence per entry that did not parse. */
	static List<String> problems(String text)
	{
		List<String> out = new ArrayList<>();
		for (String entry : entries(text))
		{
			Parsed p = parseEntry(entry);
			if (p.problem != null)
			{
				out.add("\"" + entry + "\": " + p.problem);
			}
		}
		return out;
	}

	private static List<String> entries(String text)
	{
		if (text == null)
		{
			return Collections.emptyList();
		}
		List<String> out = new ArrayList<>();
		for (String piece : text.split("[,\\n\\r]"))
		{
			String t = piece.trim();
			if (!t.isEmpty())
			{
				out.add(t);
			}
		}
		return out;
	}

	private static final class Parsed
	{
		String skill;
		String type;
		int value;
		String problem;
	}

	private static Parsed parseEntry(String entry)
	{
		Parsed p = new Parsed();
		int colon = entry.indexOf(':');
		if (colon < 0)
		{
			p.problem = "expected \"Skill: limit\"";
			return p;
		}
		String skillText = entry.substring(0, colon).trim();
		String limit = entry.substring(colon + 1).trim().toLowerCase(Locale.ROOT);

		for (Skill s : Skill.values())
		{
			if (s.getName().equalsIgnoreCase(skillText))
			{
				p.skill = s.getName();
			}
		}
		if (p.skill == null)
		{
			p.problem = "unknown skill";
			return p;
		}

		if (limit.equals("none") || limit.equals("no xp") || limit.equals("noxp"))
		{
			p.type = BuildRule.NO_XP;
		}
		else if (limit.equals("soft"))
		{
			p.type = BuildRule.SOFT_CAP;
		}
		else if (limit.startsWith("goal"))
		{
			p.type = BuildRule.GOAL;
			p.value = level(limit.substring(4).trim());
		}
		else
		{
			p.type = BuildRule.HARD_CAP;
			p.value = level(limit.startsWith("cap") ? limit.substring(3).trim() : limit);
		}

		if ((BuildRule.HARD_CAP.equals(p.type) || BuildRule.GOAL.equals(p.type)) && p.value < 1)
		{
			p.problem = "expected a level from 1 to 99, \"none\", \"soft\" or \"goal N\"";
		}
		return p;
	}

	private static int level(String s)
	{
		try
		{
			int v = Integer.parseInt(s);
			return v >= 1 && v <= 99 ? v : -1;
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}
}
