package com.vinnyvangogh.buildguard;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Decides what this client is willing to send the party, and is the only
 * place that decides it.
 *
 * <p>Every group feature is opt-in and off by default. With build sharing off
 * the answer is null and the plugin sends no message at all; gear and the
 * group risk line each need their own switch on top of it. Levels are sent
 * only for skills the member has a rule on.
 */
final class PartyOutbox
{
	/** The three config switches, as plain values so the decision is testable. */
	static final class Switches
	{
		final boolean shareBuild;
		final boolean shareGear;
		final boolean shareRisk;

		Switches(boolean shareBuild, boolean shareGear, boolean shareRisk)
		{
			this.shareBuild = shareBuild;
			this.shareGear = shareGear;
			this.shareRisk = shareRisk;
		}
	}

	private PartyOutbox()
	{
	}

	/** The payload to send, or null to send nothing. */
	static String payload(Gson gson, Switches switches, List<BuildRule> rules, Map<String, Integer> realLevels,
		List<String> gear, List<String> atRisk, String riskReason)
	{
		if (!switches.shareBuild)
		{
			return null;
		}

		Map<String, Integer> ruled = new LinkedHashMap<>();
		for (BuildRule rule : rules)
		{
			for (Map.Entry<String, Integer> e : realLevels.entrySet())
			{
				if (e.getKey().toLowerCase(Locale.ROOT).equals(rule.skillName().toLowerCase(Locale.ROOT)))
				{
					ruled.put(rule.skillName(), e.getValue());
				}
			}
		}

		List<String> noStrings = Collections.emptyList();
		PartyBuild build = new PartyBuild(rules, ruled,
			switches.shareGear ? gear : noStrings,
			switches.shareRisk ? atRisk : noStrings,
			switches.shareRisk ? riskReason : "");
		return PartyBuildCodec.encode(gson, build);
	}
}
