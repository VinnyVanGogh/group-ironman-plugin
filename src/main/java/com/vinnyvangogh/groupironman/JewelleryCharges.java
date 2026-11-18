package com.vinnyvangogh.groupironman;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Charges on the jewellery a member carries or wears, as RuneLite's Item Charges plugin shows them.
 *
 * <p>Most pieces carry their charges in the item name ("Ring of dueling(6)", "Amulet of glory(4)"), so they are
 * read from the names. A few (binding necklace and the like) keep a count RuneLite's Item Charges plugin stores
 * in its config group; that is read through the config manager, never by reaching into the other plugin.
 *
 * <p>Pure: names and a config reader in, label to charges out. When a piece is carried twice the fuller one
 * is shown.
 */
final class JewelleryCharges
{
	/** The config group Item Charges keeps its counts in. */
	static final String CONFIG_GROUP = "itemCharge";
	static final int MAX_LABELS = 12;
	static final int MAX_LABEL = 32;
	static final int MAX_CHARGES = 10_000;

	/** Jewellery whose name ends in its charges: "Ring of dueling(8)", "Ring of wealth (i5)", "Amulet of glory (t4)". */
	private static final Pattern DOSED = Pattern.compile("^(.+?)\\s*\\((?:[a-z])?(\\d+)\\)$", Pattern.CASE_INSENSITIVE);

	private static final String[] DOSED_KINDS = {
		"ring of dueling", "games necklace", "amulet of glory", "ring of wealth", "slayer ring", "skills necklace",
		"combat bracelet", "necklace of passage", "digsite pendant", "burning amulet", "ring of the gods",
		"amulet of the damned", "bracelet of clay", "expeditious bracelet", "bracelet of slaughter",
	};

	/** Item name (lower case) to the Item Charges config key holding its count. */
	private static final Map<String, String> CONFIG_KEYS = new TreeMap<>();

	static
	{
		CONFIG_KEYS.put("binding necklace", "bindingNecklace");
		CONFIG_KEYS.put("dodgy necklace", "dodgyNecklace");
		CONFIG_KEYS.put("ring of recoil", "ringOfRecoil");
		CONFIG_KEYS.put("ring of forging", "ringOfForging");
	}

	private JewelleryCharges()
	{
	}

	/**
	 * @param names the names of every item carried or worn
	 * @param config a stored Item Charges count by config key, or null when it has none
	 * @return display label to charges, sorted by label
	 */
	static Map<String, Integer> of(Iterable<String> names, Function<String, Integer> config)
	{
		Map<String, Integer> out = new TreeMap<>();
		for (String name : names)
		{
			if (name == null)
			{
				continue;
			}
			String lower = name.toLowerCase(Locale.ROOT).trim();
			Matcher m = DOSED.matcher(lower);
			if (m.matches() && isDosed(m.group(1)))
			{
				keep(out, label(m.group(1)), Integer.parseInt(m.group(2)));
				continue;
			}
			String key = CONFIG_KEYS.get(lower);
			Integer stored = key == null ? null : config.apply(key);
			if (stored != null)
			{
				keep(out, label(lower), stored);
			}
		}
		return out.isEmpty() ? Collections.emptyMap() : out;
	}

	/** Whether a decoded label and charge count may be shown: bounded, so a bad message cannot fill a card. */
	static boolean plausible(String label, long charges)
	{
		return label != null && !label.trim().isEmpty() && label.length() <= MAX_LABEL && charges >= 0
			&& charges <= MAX_CHARGES;
	}

	/** "Ring of dueling 6, Games necklace 3". */
	static String text(Map<String, Integer> charges)
	{
		StringBuilder out = new StringBuilder();
		for (Map.Entry<String, Integer> e : charges.entrySet())
		{
			out.append(out.length() == 0 ? "" : ", ").append(e.getKey()).append(' ').append(e.getValue());
		}
		return out.toString();
	}

	private static boolean isDosed(String base)
	{
		for (String kind : DOSED_KINDS)
		{
			if (kind.equals(base))
			{
				return true;
			}
		}
		return false;
	}

	private static void keep(Map<String, Integer> out, String label, int charges)
	{
		if (charges >= 0 && charges <= MAX_CHARGES && out.size() < MAX_LABELS || out.containsKey(label))
		{
			out.merge(label, charges, Math::max);
		}
	}

	private static String label(String lower)
	{
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}
}
