package com.vinnyvangogh.groupironman;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.SpriteID;

/**
 * The game's own empty-slot silhouettes from the Worn Equipment tab, drawn in
 * a member card's worn grid where nothing is worn, so it reads as the
 * equipment screen (Board 2026-11-14). Loaded at run time through RuneLite's
 * {@code SpriteManager} from the game cache; no game art is bundled.
 *
 * <p>Sprite ids are RuneLite's {@link SpriteID} {@code EQUIPMENT_SLOT_*}
 * constants (runelite-api), keyed by {@link EquipmentInventorySlot} index.
 */
final class WornSlotIcons
{
	/** Equipment slot index to its silhouette's sprite id, in the worn tab's order. */
	static final Map<Integer, Integer> SPRITES;

	static
	{
		Map<Integer, Integer> m = new LinkedHashMap<>();
		m.put(EquipmentInventorySlot.HEAD.getSlotIdx(), SpriteID.EQUIPMENT_SLOT_HEAD);
		m.put(EquipmentInventorySlot.CAPE.getSlotIdx(), SpriteID.EQUIPMENT_SLOT_CAPE);
		m.put(EquipmentInventorySlot.AMULET.getSlotIdx(), SpriteID.EQUIPMENT_SLOT_NECK);
		m.put(EquipmentInventorySlot.AMMO.getSlotIdx(), SpriteID.EQUIPMENT_SLOT_AMMUNITION);
		m.put(EquipmentInventorySlot.WEAPON.getSlotIdx(), SpriteID.EQUIPMENT_SLOT_WEAPON);
		m.put(EquipmentInventorySlot.BODY.getSlotIdx(), SpriteID.EQUIPMENT_SLOT_TORSO);
		m.put(EquipmentInventorySlot.SHIELD.getSlotIdx(), SpriteID.EQUIPMENT_SLOT_SHIELD);
		m.put(EquipmentInventorySlot.LEGS.getSlotIdx(), SpriteID.EQUIPMENT_SLOT_LEGS);
		m.put(EquipmentInventorySlot.GLOVES.getSlotIdx(), SpriteID.EQUIPMENT_SLOT_HANDS);
		m.put(EquipmentInventorySlot.BOOTS.getSlotIdx(), SpriteID.EQUIPMENT_SLOT_FEET);
		m.put(EquipmentInventorySlot.RING.getSlotIdx(), SpriteID.EQUIPMENT_SLOT_RING);
		SPRITES = Collections.unmodifiableMap(m);
	}

	private WornSlotIcons()
	{
	}

	/** The silhouette sprite for an equipment slot, or -1 for none. */
	static int sprite(int slot)
	{
		return SPRITES.getOrDefault(slot, -1);
	}

	/** The grey a silhouette pixel of no brightness at all is lifted to. */
	private static final int LIFT = 96;

	/**
	 * {@code sprite} as a flat light grey with its own shape and alpha (Board 2026-11-14): the game draws these
	 * dark for its brown stone tab, and on the sidebar's dark grey they all but vanished. Grey and not
	 * white, so an empty slot still reads as empty beside a coloured item. Null stays null.
	 */
	static BufferedImage lighten(BufferedImage sprite)
	{
		if (sprite == null)
		{
			return null;
		}
		int w = sprite.getWidth();
		int h = sprite.getHeight();
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < h; y++)
		{
			for (int x = 0; x < w; x++)
			{
				int argb = sprite.getRGB(x, y);
				int alpha = argb >>> 24;
				if (alpha == 0)
				{
					continue;
				}
				int r = (argb >> 16) & 0xFF;
				int g = (argb >> 8) & 0xFF;
				int b = argb & 0xFF;
				int lum = (299 * r + 587 * g + 114 * b) / 1000;
				int v = Math.min(255, LIFT + lum / 2);
				out.setRGB(x, y, (alpha << 24) | (v << 16) | (v << 8) | v);
			}
		}
		return out;
	}
}
