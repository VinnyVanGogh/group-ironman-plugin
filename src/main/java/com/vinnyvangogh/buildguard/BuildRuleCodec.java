package com.vinnyvangogh.buildguard;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads, writes and edits the player's own build rules as a JSON string.
 *
 * <p>Pure: {@code String} in, {@code String} out, with no {@code
 * ConfigManager} and no client. The per-account store that puts the result
 * somewhere is a thin adapter over this, and is deliberately a separate class:
 * {@code ConfigManager} has only a private constructor and cannot be faked in a
 * test without Mockito, which this project does not depend on. All the logic
 * worth testing therefore lives here, where {@code BuildRuleCodecTest} can
 * reach it.
 *
 * <p><b>One rule per skill.</b> Two rules on one skill is not a richer
 * expression of intent, it is an ambiguity — "no XP at all" and "cap at 40"
 * cannot both be obeyed — so {@link #set} replaces rather than appends, and
 * decoding collapses a duplicate to the last one seen rather than letting the
 * advisor pick whichever it happened to iterate first.
 */
final class BuildRuleCodec
{
	/**
	 * An array type rather than questpath's {@code TypeToken<List<BuildRule>>}.
	 *
	 * <p>The two are equivalent to Gson and the token is the more idiomatic of
	 * them, but obtaining one means importing {@code java.lang.reflect.Type}.
	 * That is a type token, not reflection this plugin performs — Gson does its
	 * own introspection inside its own library, which {@code AGENTS.md} allows —
	 * yet it matches a grep for {@code java.lang.reflect} in a codebase where
	 * reflection is an automatic Plugin Hub rejection. {@code
	 * scripts/preflight.sh} flags it and so would a reviewer.
	 *
	 * <p>An array has no such import and costs one {@code Arrays.asList}, so the
	 * argument is not worth having.
	 */
	private BuildRuleCodec()
	{
	}

	/**
	 * Parses stored rules, or an empty list.
	 *
	 * <p>Unparseable stored text returns empty rather than throwing. A
	 * corrupt config value is not worth taking the plugin down for, and the
	 * consequence of returning empty is well-defined and visible: the editor
	 * shows no rules, which the player can see and fix. The alternative —
	 * throwing from a config read on startup — is a plugin that will not
	 * load and says nothing about why.
	 */
	static List<BuildRule> decode(Gson gson, String json)
	{
		if (json == null || json.trim().isEmpty())
		{
			return Collections.emptyList();
		}
		List<BuildRule> parsed;
		try
		{
			BuildRule[] array = gson.fromJson(json, BuildRule[].class);
			parsed = array == null ? null : Arrays.asList(array);
		}
		catch (JsonSyntaxException e)
		{
			return Collections.emptyList();
		}
		if (parsed == null)
		{
			return Collections.emptyList();
		}

		// Collapse duplicates and drop the unusable, keeping insertion order
		// so the editor's list does not reshuffle itself on every save.
		Map<String, BuildRule> bySkill = new LinkedHashMap<>();
		for (BuildRule rule : parsed)
		{
			if (rule == null || rule.skillName().isEmpty() || rule.type().isEmpty())
			{
				continue;
			}
			if (!known(rule.type()))
			{
				// A type this build does not understand. Dropped rather than
				// kept, because an unknown type is silently non-blocking
				// everywhere downstream -- which turns a rule the player
				// believes is protecting them into one that does nothing.
				continue;
			}
			bySkill.put(key(rule.skillName()), rule);
		}
		return new ArrayList<>(bySkill.values());
	}

	/** Serialises rules for storage. */
	static String encode(Gson gson, List<BuildRule> rules)
	{
		List<BuildRule> out = rules == null ? Collections.emptyList() : rules;
		return gson.toJson(out.toArray(new BuildRule[0]), BuildRule[].class);
	}

	/**
	 * Sets or replaces the rule on one skill.
	 *
	 * <p>A value is stored only for the types that carry one. NO_XP is
	 * self-describing and SOFT_CAP deliberately carries no number, so a level
	 * typed against either is dropped rather than stored — a stored-but-
	 * ignored number is a setting the player believes they have made.
	 */
	static List<BuildRule> set(List<BuildRule> rules, String skill, String type, int value, String note)
	{
		List<BuildRule> out = new ArrayList<>();
		boolean replaced = false;
		BuildRule fresh = BuildRule.local(skill, type, takesValue(type) ? value : 0, note);

		for (BuildRule rule : rules)
		{
			if (key(rule.skillName()).equals(key(skill)))
			{
				out.add(fresh);
				replaced = true;
			}
			else
			{
				out.add(rule);
			}
		}
		if (!replaced)
		{
			out.add(fresh);
		}
		return out;
	}

	/** Removes the rule on one skill, if there is one. */
	static List<BuildRule> clear(List<BuildRule> rules, String skill)
	{
		List<BuildRule> out = new ArrayList<>();
		for (BuildRule rule : rules)
		{
			if (!key(rule.skillName()).equals(key(skill)))
			{
				out.add(rule);
			}
		}
		return out;
	}

	// questpath's merge(pushed, local) is deliberately not ported. It reconciled
	// rules arriving over a push with rules the player typed, and resolved a
	// conflict in favour of the typed one. Build Guard has no push and no second
	// origin to reconcile against: the player is the only author, so a merge
	// would be a function with one meaningful argument and a second that is
	// always empty. That is not a simplification of the original, it is the
	// absence of the thing the original existed for.

	/** Whether this rule type carries a level alongside it. */
	static boolean takesValue(String type)
	{
		return BuildRule.HARD_CAP.equals(type) || BuildRule.GOAL.equals(type);
	}

	private static boolean known(String type)
	{
		return BuildRule.NO_XP.equals(type) || BuildRule.HARD_CAP.equals(type)
			|| BuildRule.SOFT_CAP.equals(type) || BuildRule.GOAL.equals(type);
	}

	private static String key(String skill)
	{
		return skill == null ? "" : skill.toLowerCase(Locale.ROOT);
	}
}
