package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Barrows set progress: which pieces of each brother's set this player has
 * had from the chest since the plugin started counting, kept by piece name
 * (a chest piece is always undegraded, so the name is the piece).
 *
 * <p>Saved as plain text in the player's RuneLite profile; it is a count of
 * what this client saw, not the collection log, and the event says "from the
 * chest" so nobody reads it as more.
 */
final class BarrowsProgress
{
	static final String[] BROTHERS = {"Ahrim's", "Dharok's", "Guthan's", "Karil's", "Torag's", "Verac's"};
	static final int PIECES = 4;

	private final Set<String> seen = new TreeSet<>();

	static BarrowsProgress parse(String saved)
	{
		BarrowsProgress out = new BarrowsProgress();
		for (String s : (saved == null ? "" : saved).split("\\|"))
		{
			if (!s.trim().isEmpty())
			{
				out.seen.add(s.trim().toLowerCase(Locale.ROOT));
			}
		}
		return out;
	}

	String save()
	{
		return String.join("|", seen);
	}

	/** Records the chest's pieces; "Dharok's 3/4" for each brother with a piece in it, empty when none. */
	String add(Map<Integer, Integer> chest, ItemLookup lookup)
	{
		Set<String> brothers = new LinkedHashSet<>();
		for (int id : chest.keySet())
		{
			String name = lookup.name(id);
			String brother = brother(name);
			if (brother != null)
			{
				seen.add(name.toLowerCase(Locale.ROOT));
				brothers.add(brother);
			}
		}
		List<String> parts = new ArrayList<>();
		for (String b : brothers)
		{
			parts.add(b + " " + count(b) + "/" + PIECES);
		}
		return String.join(", ", parts);
	}

	private int count(String brother)
	{
		String prefix = brother.toLowerCase(Locale.ROOT);
		int n = 0;
		for (String s : seen)
		{
			if (s.startsWith(prefix))
			{
				n++;
			}
		}
		return Math.min(n, PIECES);
	}

	private static String brother(String name)
	{
		for (String b : BROTHERS)
		{
			if (name.startsWith(b))
			{
				return b;
			}
		}
		return null;
	}
}
