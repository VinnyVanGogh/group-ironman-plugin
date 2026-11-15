package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where the player reading the list can get an item: the shops they can be
 * served at, with a price where the wiki gave one, and the monsters that drop
 * it. Group ironmen have no Grand Exchange, so it is never offered.
 *
 * <p>Ported from Quest Path's {@code groupshop.ItemSources} and {@code
 * ShopReachability} (questpath-plugin ee638ff). A shop behind a skill level or
 * a quest this player lacks is never listed, only counted ({@link
 * Options#unreachableShops}), so "no shop" and "no shop you can use" stay
 * different answers. A gate that cannot be judged (an unknown quest) counts as
 * closed, and a boostable gate is judged on the real level: walking to a shop
 * hoping for a boost is the wasted trip this exists to prevent.
 */
final class ShopSources
{
	/** "Shop: Tynan's Fishing Supplies (2 Coins)". */
	private static final Pattern SHOP_PRICE = Pattern.compile("^Shop: (.+) \\(([\\d,]+) Coins\\)$");
	private static final Pattern DROP = Pattern.compile("^Drop: (.+?)(?: \\((.+)\\))?$");

	private ShopSources()
	{
	}

	/** The player reading the list: real levels by lowercase skill name, and quest progress. */
	static final class Viewer
	{
		final Map<String, Integer> levels;
		final QuestProgress quests;

		Viewer(Map<String, Integer> levels, QuestProgress quests)
		{
			Map<String, Integer> lower = new HashMap<>();
			levels.forEach((k, v) -> lower.put(k.toLowerCase(Locale.ROOT), v));
			this.levels = Collections.unmodifiableMap(lower);
			this.quests = quests;
		}

		int level(String skill)
		{
			return levels.getOrDefault(skill.toLowerCase(Locale.ROOT), 1);
		}
	}

	/** One place to get an item. */
	static final class Source
	{
		final boolean shop;
		final String name;
		/** Coins, or -1 when the wiki line gave none. */
		final long price;
		/** Drop rate words ("Always", "1/128") or the region ("wilderness"); empty for none. */
		final String detail;

		Source(boolean shop, String name, long price, String detail)
		{
			this.shop = shop;
			this.name = name;
			this.price = price;
			this.detail = detail;
		}

		/** "Shop: Tynan's Fishing Supplies (2 gp)", "Drop: Goblin (Always)". */
		String describe()
		{
			String head = (shop ? "Shop: " : "Drop: ") + name;
			if (shop)
			{
				return price >= 0 ? head + " (" + Amounts.GAME.of(price) + " gp)" : head;
			}
			return detail.isEmpty() ? head : head + " (" + detail + ")";
		}
	}

	/** Every usable source, shops first, and how many shops were left out. */
	static final class Options
	{
		final List<Source> sources;
		final int unreachableShops;

		Options(List<Source> sources, int unreachableShops)
		{
			this.sources = Collections.unmodifiableList(sources);
			this.unreachableShops = unreachableShops;
		}
	}

	/** Why a shop will not serve the viewer, in the shop's own terms; empty when it will. */
	static String blockedBy(QuestData.Shop shop, Viewer viewer, QuestData data)
	{
		QuestData.Reachability gates = shop == null ? null : shop.reachability();
		if (gates == null || viewer == null)
		{
			return "";
		}
		List<String> blocked = new ArrayList<>();
		if (gates.unknown())
		{
			blocked.add("requirements Quest Path could not read");
		}
		for (QuestData.Level gate : gates.requiredSkills())
		{
			if (viewer.level(gate.skill()) < gate.level())
			{
				blocked.add(gate.level() + " " + gate.skill());
			}
		}
		for (Long id : gates.requiredQuestIds())
		{
			String missing = missingQuest(id, gates, viewer, data);
			if (missing != null)
			{
				blocked.add(missing);
			}
		}
		return String.join(", ", blocked);
	}

	/** The quest gate's name when the viewer has not met it, or null when they have. */
	private static String missingQuest(Long id, QuestData.Reachability gates, Viewer viewer, QuestData data)
	{
		String quest = id == null ? null : data.questName(id);
		if (quest == null)
		{
			return "an unknown quest";
		}
		boolean met = gates.startedOnly(id) ? viewer.quests.started(quest) : viewer.quests.finished(quest);
		return met ? null : quest;
	}

	/** Where {@code viewer} can get {@code item}, as the quest data has it. */
	static Options forViewer(QuestData.Item item, Viewer viewer, QuestData data)
	{
		List<Source> out = new ArrayList<>();
		if (item == null)
		{
			return new Options(out, 0);
		}
		Map<String, Long> prices = prices(item.sources());
		int unreachable = 0;
		// The data repeats some shop names; each shop is judged once.
		for (String name : new java.util.LinkedHashSet<>(item.shopNames()))
		{
			QuestData.Shop shop = data.shop(name);
			if (shop != null && blockedBy(shop, viewer, data).isEmpty())
			{
				out.add(new Source(true, name, prices.getOrDefault(name, -1L), ""));
			}
			else
			{
				unreachable++;
			}
		}
		out.addAll(drops(item, data));
		return new Options(dedupe(out), unreachable);
	}

	/** The wiki's prices, by shop name, from its "Shop: X (N Coins)" lines. */
	static Map<String, Long> prices(List<String> sources)
	{
		Map<String, Long> out = new HashMap<>();
		for (String s : sources)
		{
			Matcher m = SHOP_PRICE.matcher(s);
			if (m.matches())
			{
				out.putIfAbsent(m.group(1), Long.parseLong(m.group(2).replace(",", "")));
			}
		}
		return out;
	}

	/** The wiki's own drop lines first (with their rate), then every other dropper it names. */
	private static List<Source> drops(QuestData.Item item, QuestData data)
	{
		Map<String, Source> out = new LinkedHashMap<>();
		for (String s : item.sources())
		{
			Matcher m = DROP.matcher(s);
			if (m.matches())
			{
				out.putIfAbsent(m.group(1), new Source(false, m.group(1), -1, m.group(2) == null ? "" : m.group(2)));
			}
		}
		for (String name : item.dropSourceNames())
		{
			out.putIfAbsent(name, new Source(false, name, -1, region(name, data)));
		}
		return new ArrayList<>(out.values());
	}

	/** "Wilderness" when the only known place for it is there: worth saying before anyone walks in. */
	private static String region(String monster, QuestData data)
	{
		boolean any = false;
		for (QuestData.DropLocation at : data.dropLocations())
		{
			if (at.sourceName().equals(monster))
			{
				if (!"wilderness".equalsIgnoreCase(at.region()))
				{
					return "";
				}
				any = true;
			}
		}
		return any ? "Wilderness" : "";
	}

	private static List<Source> dedupe(List<Source> sources)
	{
		Map<String, Source> out = new LinkedHashMap<>();
		for (Source s : sources)
		{
			out.putIfAbsent((s.shop ? "s:" : "d:") + s.name, s);
		}
		return new ArrayList<>(out.values());
	}
}
