package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The one item-name matcher behind both the panel's Find tab and
 * {@code ::find}, so the two can never disagree.
 *
 * <p>Case-insensitive. A name matches when it contains the whole query
 * ("guam" in "Grimy guam leaf"), or every word of it in any order ("leaf
 * grimy"), or what a common shorthand stands for ("ppot" for "Prayer
 * potion").
 */
final class ItemQuery
{
	/** Shorthand players type for items, each to the name text it stands for. */
	private static final Map<String, List<String>> ALIASES = new HashMap<>();

	static
	{
		alias("ppot", "prayer potion");
		alias("ppots", "prayer potion");
		alias("pray pot", "prayer potion");
		alias("brew", "saradomin brew");
		alias("brews", "saradomin brew");
		alias("sara brew", "saradomin brew");
		alias("restore", "super restore");
		alias("restores", "super restore");
		alias("scb", "super combat potion");
		alias("super combat", "super combat potion");
		alias("stam", "stamina potion");
		alias("stams", "stamina potion");
		alias("antifire", "antifire potion", "extended antifire", "super antifire");
		alias("ags", "armadyl godsword");
		alias("bgs", "bandos godsword");
		alias("sgs", "saradomin godsword");
		alias("zgs", "zamorak godsword");
		alias("dwh", "dragon warhammer");
		alias("dhl", "dragon hunter lance");
		alias("dhcb", "dragon hunter crossbow");
		alias("dhw", "dragon hunter wand");
		alias("bp", "toxic blowpipe");
		alias("blowpipe", "toxic blowpipe");
		alias("tbow", "twisted bow");
		alias("fury", "amulet of fury");
		alias("torture", "amulet of torture");
		alias("anguish", "necklace of anguish");
		alias("tent", "abyssal tentacle");
		alias("rcb", "rune crossbow");
		alias("acb", "armadyl crossbow");
		alias("dds", "dragon dagger");
		alias("glory", "amulet of glory");
		alias("ess", "essence");
		alias("pure ess", "pure essence");
		alias("karam", "karambwan");
		alias("dbones", "dragon bones");
		alias("dbone", "dragon bones");
		alias("law", "law rune");
		alias("laws", "law rune");
		alias("nats", "nature rune");
		alias("nat", "nature rune");
		alias("bloods", "blood rune");
		alias("deaths", "death rune");
		alias("souls", "soul rune");
		alias("wraths", "wrath rune");
		alias("cannonballs", "cannonball");
	}

	private final String whole;
	private final List<String> words;
	private final List<String> expansions;

	private ItemQuery(String whole)
	{
		this.whole = whole;
		List<String> w = new ArrayList<>();
		for (String part : whole.split("\\s+"))
		{
			if (!part.isEmpty())
			{
				w.add(part);
			}
		}
		this.words = Collections.unmodifiableList(w);
		List<String> x = ALIASES.get(whole);
		this.expansions = x == null ? Collections.emptyList() : x;
	}

	private static void alias(String shorthand, String... means)
	{
		List<String> list = new ArrayList<>();
		for (String m : means)
		{
			list.add(m.toLowerCase(Locale.ROOT));
		}
		ALIASES.put(shorthand, Collections.unmodifiableList(list));
	}

	/** Null when {@code text} is shorter than {@link GroupSearch#MIN_QUERY} once trimmed. */
	static ItemQuery parse(String text)
	{
		String q = normalize(text);
		return q.length() < GroupSearch.MIN_QUERY ? null : new ItemQuery(q);
	}

	static String normalize(String text)
	{
		return text == null ? "" : text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	boolean matches(String itemName)
	{
		if (itemName == null)
		{
			return false;
		}
		String name = itemName.toLowerCase(Locale.ROOT).replace(' ', ' ');
		if (name.contains(whole))
		{
			return true;
		}
		for (String e : expansions)
		{
			if (name.contains(e))
			{
				return true;
			}
		}
		if (words.size() < 2)
		{
			return false;
		}
		for (String w : words)
		{
			if (!name.contains(w))
			{
				return false;
			}
		}
		return true;
	}

	/** The query as typed, normalized. */
	String text()
	{
		return whole;
	}
}
