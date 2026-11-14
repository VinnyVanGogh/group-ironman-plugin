package com.vinnyvangogh.scenarios;

import java.util.List;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Node;

/**
 * An {@link ItemContainer} holding a fixed list of items, for an {@code ItemContainerChanged} with no game behind
 * it, answered the way the game's container answers.
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

	// ItemContainer is also a Node of the game's hash table. Not in any table.

	@Override
	public Node getNext()
	{
		return null;
	}

	@Override
	public Node getPrevious()
	{
		return null;
	}

	@Override
	public long getHash()
	{
		return id;
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
