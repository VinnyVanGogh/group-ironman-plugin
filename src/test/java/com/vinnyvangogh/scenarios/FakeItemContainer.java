package com.vinnyvangogh.scenarios;

import java.util.List;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;

/**
 * An {@link ItemContainer} holding a fixed list of items, for an {@code ItemContainerChanged} with no game behind
 * it. The interface's eight methods are all here, answered the way the game's container answers them.
 */
public final class FakeItemContainer implements ItemContainer
{
	private final int id;
	private final Item[] items;

	public FakeItemContainer(int id, List<Item> items)
	{
		this.id = id;
		this.items = items.toArray(new Item[0]);
	}

	@Override
	public int getId()
	{
		return id;
	}

	@Override
	public Item[] getItems()
	{
		return items.clone();
	}

	@Override
	public Item getItem(int slot)
	{
		return slot >= 0 && slot < items.length ? items[slot] : null;
	}

	@Override
	public boolean contains(int itemId)
	{
		return find(itemId) >= 0;
	}

	@Override
	public int count(int itemId)
	{
		int n = 0;
		for (Item item : items)
		{
			if (item.getId() == itemId)
			{
				n += item.getQuantity();
			}
		}
		return n;
	}

	@Override
	public int size()
	{
		return items.length;
	}

	@Override
	public int count()
	{
		int n = 0;
		for (Item item : items)
		{
			if (item.getId() >= 0)
			{
				n++;
			}
		}
		return n;
	}

	@Override
	public int find(int itemId)
	{
		for (int i = 0; i < items.length; i++)
		{
			if (items[i].getId() == itemId)
			{
				return i;
			}
		}
		return -1;
	}
}
