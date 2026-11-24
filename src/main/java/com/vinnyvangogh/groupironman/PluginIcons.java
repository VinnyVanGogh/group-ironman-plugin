package com.vinnyvangogh.groupironman;

import java.awt.image.BufferedImage;
import java.util.Locale;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;

/** The Group box's pictures from RuneLite's own managers; all of them cache, so asking every frame is cheap. */
final class PluginIcons implements BoxIcons.Source
{
	private final SkillIconManager skills;
	private final ItemManager items;
	private final SpriteManager sprites;

	PluginIcons(SkillIconManager skills, ItemManager items, SpriteManager sprites)
	{
		this.skills = skills;
		this.items = items;
		this.sprites = sprites;
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

	/** Render thread (the client thread), as SpriteManager#getSprite needs; null until the sprite cache is up. */
	@Override
	public BufferedImage glyph(BoxText.Glyph glyph)
	{
		int item = itemOf(glyph);
		if (item >= 0)
		{
			return items.getImage(item);
		}
		int sprite = spriteOf(glyph);
		return sprite < 0 ? null : sprites.getSprite(sprite, 0);
	}

	/** The item whose picture stands for a glyph: the collection log book; -1 for none. */
	static int itemOf(BoxText.Glyph glyph)
	{
		return glyph == BoxText.Glyph.CLOG ? ItemID.COLLECTION_LOG : -1;
	}

	/** The game sprite for a glyph; -1 for the glyphs drawn by hand (online dot, death cross). */
	static int spriteOf(BoxText.Glyph glyph)
	{
		switch (glyph)
		{
			case QUEST:
				return SpriteID.SideiconsInterface.QUESTS;
			case DIARY:
				return SpriteID.SideiconsInterface.ACHIEVEMENT_DIARIES;
			case COMBAT:
				return SpriteID.SideiconsInterface.COMBAT;
			case KILLS:
				return SpriteID.ICON_SKULL;
			case BEST:
				// The game has no stopwatch sprite; the crown reads as "best".
				return SpriteID.ICON_CROWN;
			case PET:
				return SpriteID.ICON_CAT;
			default:
				return -1;
		}
	}
}
