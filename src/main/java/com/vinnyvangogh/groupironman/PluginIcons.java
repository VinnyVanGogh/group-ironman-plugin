package com.vinnyvangogh.groupironman;

import java.awt.image.BufferedImage;
import java.util.Locale;
import net.runelite.api.Skill;
import net.runelite.client.game.*;

/** The Group box's pictures from RuneLite's own managers; both cache, so asking every frame is cheap. */
final class PluginIcons implements BoxIcons.Source
{
	private final SkillIconManager skills;
	private final ItemManager items;

	PluginIcons(SkillIconManager skills, ItemManager items)
	{
		this.skills = skills;
		this.items = items;
	}

	@Override
	public BufferedImage skill(String name)
	{
		try
		{
			return skills.getSkillImage(Skill.valueOf(name.trim().toUpperCase(Locale.ROOT)), true);
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}

	@Override
	public BufferedImage item(int id)
	{
		return items.getImage(id);
	}
}
