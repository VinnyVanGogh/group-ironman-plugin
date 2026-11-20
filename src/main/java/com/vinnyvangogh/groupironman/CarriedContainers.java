package com.vinnyvangogh.groupironman;

import java.util.*;
import java.util.regex.*;
import javax.inject.Inject;
import net.runelite.api.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

/**
 * Client thread only. Feeds {@link ContainerContents} from the game the way
 * Combat Vitals' plugin does ({@code CombatVitalsPlugin#readContainerDialog},
 * {@code #keepContainerCounts}, {@code #scanContainers}): chat lines, the Check
 * dialog's Objectbox text, Fill / Empty clicks and the inventory change after
 * them. Only reads: no click or message is changed or sent.
 *
 * <p>Known counts are kept per account (RuneScape profile config,
 * {@code containerCount.<KIND>}), so a login starts from the last known count
 * instead of no number.
 */
final class CarriedContainers
{
	static final String KEY_PREFIX = "containerCount.";
	/** "You catch some shrimps.", "You catch a shark!": one fish, which an open barrel takes. */
	private static final Pattern CATCH = Pattern.compile("^You catch (?:a|an|some) (.+?)[.!]?$");
	/** Caught fish that stack in the inventory; the barrel takes neither. */
	private static final Pattern STACKING_CATCH = Pattern.compile("^(?:minnows|karambwanji)$");
	private static final String COAL_BAG_CAPE = "smithing cape";

	private final Client client;
	private final ItemManager itemManager;
	private final ConfigManager configManager;
	private final ContainerContents contents = new ContainerContents();

	/** Containers carried in the inventory, from the last scan. */
	private final Set<SkillingContainer> carried = EnumSet.noneOf(SkillingContainer.class);
	/** The looting bag as last viewed this session, by canonical id; null until the game sends it. */
	private Map<Integer, Integer> lootingItems;
	private int lootingSlots;
	/** The inventory's filling slots at the last scan, -1 until read. */
	private int filling = -1;
	/** The Check dialog text already read, so one dialog is read once. */
	private String dialog;
	/** Counts as last written to the profile; null until restored after a login. */
	private Map<SkillingContainer, Integer> saved;
	/** Whether an item takes its own slot that a gathered item could have used; definitions never change. */
	private final Map<Integer, Boolean> fillers = new HashMap<>();
	/** Item name to id, for a container's contents in Find; -1 for a name with no tradeable item. */
	private final Map<String, Integer> ids = new HashMap<>();

	@Inject
	CarriedContainers(Client client, ItemManager itemManager, ConfigManager configManager)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.configManager = configManager;
	}

	/** A game or spam chat line; @return whether a count changed. */
	boolean chat(String message)
	{
		String line = Text.removeTags(message);
		int tick = client.getTickCount();
		boolean changed = contents.message(line, tick);
		Matcher fish = CATCH.matcher(line);
		if (!changed && fish.matches() && !STACKING_CATCH.matcher(fish.group(1).toLowerCase(Locale.ROOT)).matches())
		{
			changed = contents.fishCaught(tick);
		}
		return changed;
	}

	/** Fill, Empty or Empty to bank on a container, in the inventory or the bank. */
	void clicked(String option, String target)
	{
		SkillingContainer kind = target == null ? null : SkillingContainer.fromItemName(Text.removeTags(target));
		if (kind != null)
		{
			contents.clicked(kind, option, client.getTickCount());
		}
	}

	/** The inventory or equipment changed; @return whether a count changed. */
	boolean itemsChanged()
	{
		int before = filling;
		scan();
		return contents.inventoryChanged(before, filling, client.getTickCount());
	}

	/** Once a tick: the Check dialog, then keeping counts per account; @return whether a count changed. */
	boolean tick()
	{
		boolean changed = readDialog();
		keepCounts();
		if (filling < 0)
		{
			scan();
		}
		return changed;
	}

	void loggedOut()
	{
		contents.clear();
		carried.clear();
		filling = -1;
		dialog = null;
		saved = null;
		lootingItems = null;
		lootingSlots = 0;
	}

	/** The looting bag's container changed (the game sends it while the bag is viewed): keep what it holds. */
	void lootingBag(ItemContainer bag)
	{
		if (bag == null)
		{
			return;
		}
		Map<Integer, Integer> items = new TreeMap<>();
		int slots = 0;
		for (Item item : bag.getItems())
		{
			if (item.getId() > 0 && item.getQuantity() > 0)
			{
				slots++;
				ItemCounts.add(items, item.getId(), item.getQuantity(), itemManager::canonicalize);
			}
		}
		lootingSlots = slots;
		lootingItems = items;
	}

	/** What each carried container is known to hold; one not carried, or unknown, is left out. */
	Map<SkillingContainer, GroupCodec.Held> held()
	{
		Map<SkillingContainer, GroupCodec.Held> out = new EnumMap<>(SkillingContainer.class);
		if (lootingItems != null && carried.contains(SkillingContainer.LOOTING_BAG))
		{
			out.put(SkillingContainer.LOOTING_BAG, new GroupCodec.Held(lootingSlots, lootingItems));
		}
		for (SkillingContainer kind : carried)
		{
			int n = contents.contents(kind);
			if (n >= 0)
			{
				out.put(kind, new GroupCodec.Held(n, itemIds(contents.named(kind))));
			}
		}
		return out;
	}

	/** Names to ids, all or nothing: one name without an item leaves the contents unfindable, not half right. */
	private Map<Integer, Integer> itemIds(Map<String, Integer> named)
	{
		if (named == null)
		{
			return Collections.emptyMap();
		}
		Map<Integer, Integer> out = new TreeMap<>();
		for (Map.Entry<String, Integer> e : named.entrySet())
		{
			int id = ids.computeIfAbsent(e.getKey().toLowerCase(Locale.ROOT), this::lookUp);
			if (id < 0)
			{
				return Collections.emptyMap();
			}
			out.merge(id, e.getValue(), Integer::sum);
		}
		return out;
	}

	/** The tradeable item named exactly {@code lowerName}, or -1. */
	private int lookUp(String lowerName)
	{
		// var: ItemManager#search's element type has moved packages between RuneLite releases.
		for (var price : itemManager.search(lowerName))
		{
			if (price.getName() != null && price.getName().equalsIgnoreCase(lowerName))
			{
				return price.getId();
			}
		}
		return -1;
	}

	/**
	 * A container's Check answers in the item dialog, not in chat: "The basket contains:<br>14 x Yew logs".
	 * Read once per dialog, with line breaks as spaces.
	 */
	private boolean readDialog()
	{
		Widget text = client.getWidget(InterfaceID.Objectbox.TEXT);
		if (text == null || text.isHidden() || text.getText() == null)
		{
			dialog = null;
			return false;
		}
		String message = Text.removeTags(text.getText().replace("<br>", " ")).trim();
		if (message.equals(dialog))
		{
			return false;
		}
		dialog = message;
		return contents.message(message, client.getTickCount());
	}

	/** Restores this account's counts once its profile is known, then writes any count that changed. */
	private void keepCounts()
	{
		if (client.getGameState() != GameState.LOGGED_IN || configManager.getRSProfileKey() == null)
		{
			return;
		}
		if (saved == null)
		{
			saved = new EnumMap<>(SkillingContainer.class);
			for (SkillingContainer kind : SkillingContainer.values())
			{
				Integer count = configManager.getRSProfileConfiguration(GroupIronmanConfig.GROUP, KEY_PREFIX + kind.name(),
					Integer.class);
				if (count != null)
				{
					contents.restore(kind, count);
					saved.put(kind, count);
				}
			}
		}
		for (Map.Entry<SkillingContainer, Integer> e : contents.known().entrySet())
		{
			if (!e.getValue().equals(saved.get(e.getKey())))
			{
				configManager.setRSProfileConfiguration(GroupIronmanConfig.GROUP, KEY_PREFIX + e.getKey().name(),
					e.getValue());
				saved.put(e.getKey(), e.getValue());
			}
		}
	}

	/** Which containers are carried and open, their capacities, and the inventory's filling slots. */
	private void scan()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return;
		}
		carried.clear();
		Set<SkillingContainer> open = EnumSet.noneOf(SkillingContainer.class);
		int slots = 0;
		for (Item item : inventory.getItems())
		{
			if (item.getId() <= 0)
			{
				continue;
			}
			String name = itemManager.getItemComposition(item.getId()).getName();
			SkillingContainer kind = SkillingContainer.fromItemName(name);
			if (kind != null)
			{
				carried.add(kind);
				if (SkillingContainer.isOpen(name))
				{
					open.add(kind);
				}
			}
			else if (fillers.computeIfAbsent(item.getId(), this::fills))
			{
				slots++;
			}
		}
		filling = slots;
		contents.carried(open, capacities());
	}

	/** A slot item: not stackable and not noted, so a gathered item could not share its slot. */
	private boolean fills(int id)
	{
		ItemComposition item = itemManager.getItemComposition(id);
		return !item.isStackable() && item.getNote() == -1;
	}

	/** The coal bag takes 36 with a Smithing cape worn. */
	private Map<SkillingContainer, Integer> capacities()
	{
		Map<SkillingContainer, Integer> out = new EnumMap<>(SkillingContainer.class);
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn == null)
		{
			return out;
		}
		for (Item item : worn.getItems())
		{
			if (item.getId() > 0 && itemManager.getItemComposition(item.getId()).getName().toLowerCase(Locale.ROOT)
				.startsWith(COAL_BAG_CAPE))
			{
				out.put(SkillingContainer.COAL_BAG, SkillingContainer.COAL_BAG_CAPE_CAPACITY);
			}
		}
		return out;
	}
}
