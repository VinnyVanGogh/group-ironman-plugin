package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Monster drops for items the quest data does not name: the bundled {@code
 * item-drops.txt}, from the Old School RuneScape Wiki (see that file and
 * THIRD_PARTY_NOTICES.md). Without it a custom list's Dragon warhammer read "no
 * known shop or drop" though Lizardman shamans drop it (Board 2026-11-14).
 */
final class ItemDrops
{
	static final String RESOURCE = "item-drops.txt";
	private static final ItemDrops BUNDLED = parse(RegionNames.resource(RESOURCE));

	private final Map<Integer, List<ShopSources.Source>> drops;

	private ItemDrops(Map<Integer, List<ShopSources.Source>> drops)
	{
		this.drops = drops;
	}

	static ItemDrops bundled()
	{
		return BUNDLED;
	}

	/** The monsters that drop {@code itemId}, best rate first; empty when the table has none. */
	List<ShopSources.Source> of(int itemId)
	{
		return drops.getOrDefault(itemId, Collections.emptyList());
	}

	int size()
	{
		return drops.size();
	}

	/** "id|name|monster=rate|...", one item a line; "#" lines and anything malformed are skipped. */
	static ItemDrops parse(String text)
	{
		Map<Integer, List<ShopSources.Source>> out = new HashMap<>();
		for (String line : text.split("\n"))
		{
			String[] parts = line.trim().split("\\|");
			Integer id = line.trim().startsWith("#") || parts.length < 3 ? null : id(parts[0]);
			List<ShopSources.Source> sources = id == null ? Collections.emptyList() : sources(parts);
			if (!sources.isEmpty())
			{
				out.put(id, Collections.unmodifiableList(sources));
			}
		}
		return new ItemDrops(out);
	}

	private static Integer id(String text)
	{
		try
		{
			return Integer.parseInt(text.trim());
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/** Every "monster=rate" after the id and name. */
	private static List<ShopSources.Source> sources(String[] parts)
	{
		List<ShopSources.Source> out = new ArrayList<>();
		for (int i = 2; i < parts.length; i++)
		{
			int eq = parts[i].indexOf('=');
			String monster = (eq < 0 ? parts[i] : parts[i].substring(0, eq)).trim();
			String rate = eq < 0 ? "" : parts[i].substring(eq + 1).trim();
			if (!monster.isEmpty())
			{
				out.add(new ShopSources.Source(false, monster, -1, rate));
			}
		}
		return out;
	}
}
