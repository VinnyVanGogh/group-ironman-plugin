package com.vinnyvangogh.buildguard;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the verdict engine needs to know about one account.
 *
 * <p>Assembled from three places, all of them local: levels and experience from
 * the client, completed quests from the client's own quest varbits, and build
 * rules from the player's own stored rules. The client knows what you have
 * done; only you know what you have decided not to do, and in Build Guard that
 * second half never leaves the machine.
 *
 * <p>Skill names are matched case-insensitively against whatever the graph and
 * the rules use, because three sources spell them three ways and none of them
 * is wrong.
 */
class PlayerState
{
	private final Map<String, Integer> levels;
	private final Map<String, Long> experience;
	private final Set<String> completed;
	private final Set<String> inProgress;
	private final Map<String, BuildRule> rules;

	/**
	 * Without in-progress information.
	 *
	 * <p>An empty in-progress set is not a claim that nothing is started; it
	 * is the absence of a claim. A started-only prerequisite is then judged on
	 * completion alone, which errs towards blocking rather than towards a
	 * false "eligible". The golden vectors are built this way, because the
	 * exported account carries completed quests and nothing else.
	 */
	PlayerState(Map<String, Integer> levels, Map<String, Long> experience,
		Set<String> completedQuestNames, List<BuildRule> rules)
	{
		this(levels, experience, completedQuestNames, Collections.emptySet(), rules);
	}

	PlayerState(Map<String, Integer> levels, Map<String, Long> experience,
		Set<String> completedQuestNames, Set<String> inProgressQuestNames, List<BuildRule> rules)
	{
		this.levels = lowerKeys(levels);
		this.experience = lowerKeys(experience);

		// Quest completion is matched by name, not by id.
		//
		// The client's Quest enum and the bundle's quest table are two id
		// spaces that have nothing to do with each other -- RuneLite's are the
		// game's, the bundle's are questpath's own SQLite rowids. Comparing
		// them produced a completed set that was pure coincidence: quests the
		// account had never touched came back COMPLETE, and quests it had
		// finished were judged as if it had not. The name is the one identity
		// both sides genuinely share, which is why the generated vectors
		// resolve their ids through the graph to a name before reaching here.
		this.completed = new HashSet<>();
		if (completedQuestNames != null)
		{
			for (String name : completedQuestNames)
			{
				if (name != null)
				{
					this.completed.add(name.toLowerCase());
				}
			}
		}

		// Matched by name for exactly the same reason as completed above.
		this.inProgress = new HashSet<>();
		if (inProgressQuestNames != null)
		{
			for (String name : inProgressQuestNames)
			{
				if (name != null)
				{
					this.inProgress.add(name.toLowerCase());
				}
			}
		}

		Map<String, BuildRule> bySkill = new HashMap<>();
		if (rules != null)
		{
			for (BuildRule rule : rules)
			{
				bySkill.put(rule.skillName().toLowerCase(), rule);
			}
		}
		this.rules = bySkill;
	}

	private static <T> Map<String, T> lowerKeys(Map<String, T> source)
	{
		Map<String, T> out = new HashMap<>();
		if (source != null)
		{
			for (Map.Entry<String, T> entry : source.entrySet())
			{
				out.put(entry.getKey().toLowerCase(), entry.getValue());
			}
		}
		return out;
	}

	/**
	 * The account's level in a skill.
	 *
	 * A skill the account has said nothing about is at its start level, not
	 * zero: zero is not a level any skill can hold, and treating it as one
	 * would make every requirement look unmet.
	 */
	int levelIn(String skill)
	{
		Integer level = levels.get(skill.toLowerCase());
		if (level != null)
		{
			return level;
		}
		return "hitpoints".equalsIgnoreCase(skill) ? 10 : 1;
	}

	long experienceIn(String skill)
	{
		Long xp = experience.get(skill.toLowerCase());
		return xp == null ? 0L : xp;
	}

	boolean isComplete(String questName)
	{
		return questName != null && completed.contains(questName.toLowerCase());
	}

	/**
	 * Whether the account has at least begun this quest.
	 *
	 * <p>Complete counts as started, which is what makes this the right test
	 * for a prerequisite the wiki says need only be *started* -- Curse of the
	 * Empty Lord asks that Desert Treasure I be begun, and someone who has
	 * finished it has certainly begun it.
	 */
	boolean hasStarted(String questName)
	{
		if (questName == null)
		{
			return false;
		}
		String key = questName.toLowerCase();
		return completed.contains(key) || inProgress.contains(key);
	}

	/** The account's rule for a skill, or null when it has declared none. */
	BuildRule ruleFor(String skill)
	{
		return rules.get(skill.toLowerCase());
	}

	/**
	 * Whether any build rules are known.
	 *
	 * With no rules, the engine cannot report BUILD_BLOCKED for anything, so
	 * every verdict it gives is "as far as I know" rather than an answer. Saying
	 * so beats quietly rendering a pure's quest list as if it were a main's.
	 *
	 * <p>In questpath this flag also distinguished "not synced yet" from "you
	 * have written no rules". Build Guard has no sync, so only the second
	 * sentence is available and it is the one the UI must say. What exactly it
	 * says is still open -- see {@code docs/SPEC.md}.
	 */
	boolean hasRules()
	{
		return !rules.isEmpty();
	}
}
