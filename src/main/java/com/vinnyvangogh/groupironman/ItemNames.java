package com.vinnyvangogh.groupironman;

import java.util.function.*;

/**
 * A real name for any item id, noted and placeholder ids included. The game's own name for a noted or
 * placeholder id is sometimes missing or "null"; the item it stands for (ItemManager#canonicalize) always has one.
 */
final class ItemNames
{
	private ItemNames()
	{
	}

	/**
	 * @param nameOf the game's name for an id, or null
	 * @param canonicalize the id of the item a noted or placeholder id stands for, the same id otherwise
	 * @return a usable name, or null when neither the id nor its canonical id has one
	 */
	static String resolve(int id, IntFunction<String> nameOf, IntUnaryOperator canonicalize)
	{
		String name = nameOf.apply(id);
		if (usable(name))
		{
			return name;
		}
		int canonical = canonicalize.applyAsInt(id);
		if (canonical != id)
		{
			String real = nameOf.apply(canonical);
			if (usable(real))
			{
				return real;
			}
		}
		return null;
	}

	private static boolean usable(String name)
	{
		return name != null && !name.isEmpty() && !"null".equalsIgnoreCase(name);
	}
}
