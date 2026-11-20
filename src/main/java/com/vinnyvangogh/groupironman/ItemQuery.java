package com.vinnyvangogh.groupironman;

import java.util.*;
import java.util.List;

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
	/** Shorthand players type for items, each to the name text it stands for, from {@code item-aliases.txt}. */
	private static final Map<String, List<String>> ALIASES = new HashMap<>();

	static
	{
		for (String line : RegionNames.resource("item-aliases.txt").split("\n"))
		{
			int eq = line.indexOf('=');
			if (!line.startsWith("#") && eq > 0)
			{
				alias(line.substring(0, eq).trim(), line.substring(eq + 1).trim().split(","));
			}
		}
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
