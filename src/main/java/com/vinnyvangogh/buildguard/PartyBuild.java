package com.vinnyvangogh.buildguard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What one party member chose to share: their build rules, their real levels
 * in the skills those rules cover, the gear they opted to list, and whether
 * their current attack style puts a rule at risk.
 *
 * <p>Immutable, and the same type whether it was built locally or decoded off
 * the wire, for the same reason {@link BuildRule#local} gives: one type, one
 * code path through the panel.
 */
final class PartyBuild
{
	private final List<BuildRule> rules;
	private final Map<String, Integer> levels;
	private final List<String> gear;
	private final List<String> atRisk;
	private final String riskReason;

	PartyBuild(List<BuildRule> rules, Map<String, Integer> levels, List<String> gear,
		List<String> atRisk, String riskReason)
	{
		this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
		this.levels = Collections.unmodifiableMap(new LinkedHashMap<>(levels));
		this.gear = Collections.unmodifiableList(new ArrayList<>(gear));
		this.atRisk = Collections.unmodifiableList(new ArrayList<>(atRisk));
		this.riskReason = riskReason == null ? "" : riskReason;
	}

	List<BuildRule> rules()
	{
		return rules;
	}

	/** Real level by skill name, only for skills the member has a rule on. */
	Map<String, Integer> levels()
	{
		return levels;
	}

	List<String> gear()
	{
		return gear;
	}

	/** Skill names whose blocking rule the member's current style is eating into. */
	List<String> atRisk()
	{
		return atRisk;
	}

	/** Why, in a few words: "Controlled attack style". Empty when nothing is at risk. */
	String riskReason()
	{
		return riskReason;
	}
}
