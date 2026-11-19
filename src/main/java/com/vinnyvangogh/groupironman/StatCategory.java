package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;

/**
 * The categories of the Leaderboard and Compare views, as the Hiscores plugin groups them: Skilling, Bossing,
 * Activities (clues, collections logged and the like) and Minigames. The counts behind the last three are the public
 * hiscores' own, named exactly as {@link HiscoreSkill#getName()} spells them.
 */
enum StatCategory
{
	SKILLING("Skilling"), BOSSING("Bossing"), ACTIVITIES("Activities"), MINIGAMES("Minigames");

	/** The minigames among the hiscores' activities and bosses. */
	private static final Set<HiscoreSkill> MINIGAME_SKILLS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		HiscoreSkill.TEMPOROSS, HiscoreSkill.WINTERTODT, HiscoreSkill.ZALCANO, HiscoreSkill.SOUL_WARS_ZEAL,
		HiscoreSkill.RIFTS_CLOSED, HiscoreSkill.LAST_MAN_STANDING, HiscoreSkill.PVP_ARENA_RANK)));

	private static final Map<StatCategory, List<String>> NAMES = new EnumMap<>(StatCategory.class);

	static
	{
		for (StatCategory c : values())
		{
			NAMES.put(c, new ArrayList<>());
		}
		for (HiscoreSkill s : HiscoreSkill.values())
		{
			StatCategory c = of(s);
			if (c != null)
			{
				NAMES.get(c).add(s.getName());
			}
		}
	}

	final String label;

	StatCategory(String label)
	{
		this.label = label;
	}

	/** The category a hiscore entry belongs to; null for skills (which are not counts) and the overall row. */
	static StatCategory of(HiscoreSkill s)
	{
		if (s == null || s.getType() == HiscoreSkillType.SKILL || s.getType() == HiscoreSkillType.OVERALL)
		{
			return null;
		}
		if (MINIGAME_SKILLS.contains(s))
		{
			return MINIGAMES;
		}
		return s.getType() == HiscoreSkillType.BOSS ? BOSSING : ACTIVITIES;
	}

	/** Every count this category can show, in the hiscores' own order. Empty for Skilling. */
	List<String> names()
	{
		return Collections.unmodifiableList(NAMES.get(this));
	}
}
