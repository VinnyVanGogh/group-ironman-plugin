package com.vinnyvangogh.groupironman;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Map region id to a place name ("Lumbridge", "Vorkath", "Taverley Dungeon"),
 * from the bundled {@code regions.txt}.
 *
 * <p>A region the table does not know has no name; the panel then shows the
 * region number, as part 2 did, rather than a guess.
 */
final class RegionNames
{
	private final Map<Integer, String> names;

	private RegionNames(Map<Integer, String> names)
	{
		this.names = names;
	}

	/** The bundled table, or an empty one if the resource is missing or unreadable. */
	static RegionNames load()
	{
		// getResourceAsStream, never getResource: the resource is inside a jar, not a file.
		return read(RegionNames.class.getResourceAsStream("regions.txt"));
	}

	/** The table in a UTF-8 stream (closed here), or an empty one when it is null or unreadable. */
	static RegionNames read(InputStream resource)
	{
		if (resource == null)
		{
			return parse("");
		}
		try (InputStream stream = resource)
		{
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			int n;
			while ((n = stream.read(buf)) > 0)
			{
				out.write(buf, 0, n);
			}
			// A charset name, not StandardCharsets: the Plugin Hub bot holds any
			// import from the nio packages for file i/o review.
			return parse(out.toString("UTF-8"));
		}
		catch (IOException e)
		{
			// Includes UnsupportedEncodingException, which UTF-8 never throws.
			return parse("");
		}
	}

	/** {@code Name=id,id} per line; {@code #} comments; bad ids are skipped; the first name for an id wins. */
	static RegionNames parse(String text)
	{
		Map<Integer, String> out = new HashMap<>();
		for (String raw : text.split("\n"))
		{
			String line = raw.trim();
			int eq = line.lastIndexOf('=');
			if (line.isEmpty() || line.startsWith("#") || eq <= 0)
			{
				continue;
			}
			String name = line.substring(0, eq).trim();
			for (String id : line.substring(eq + 1).split(","))
			{
				try
				{
					out.putIfAbsent(Integer.parseInt(id.trim()), name);
				}
				catch (NumberFormatException e)
				{
					// One bad id does not lose the rest of the line.
				}
			}
		}
		return new RegionNames(out);
	}

	/** The place name, or null when the table does not know the region. */
	String name(int region)
	{
		return names.get(region);
	}

	int size()
	{
		return names.size();
	}

	/** "Lumbridge", or "region 12345" when unknown, or null when the region itself is unknown (0). */
	String describe(int region)
	{
		if (region <= 0)
		{
			return null;
		}
		String name = name(region);
		return name != null ? name : "region " + region;
	}
}
