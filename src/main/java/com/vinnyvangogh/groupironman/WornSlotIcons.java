package com.vinnyvangogh.groupironman;

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
}
