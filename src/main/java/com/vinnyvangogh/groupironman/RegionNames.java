package com.vinnyvangogh.groupironman;

import com.google.common.base.Charsets;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Map region id to a place name ("Lumbridge", "Vorkath", "Taverley Dungeon"),
 * from the bundled {@code regions.txt} and {@code regions-generated.txt}.
 *
 * <p>A region neither table names is described from where it is, never by its
 * number (Board 2026-11-14): see {@link #describe(int)}.
 */
final class RegionNames
{
	private final Map<Integer, String> names;

	private RegionNames(Map<Integer, String> names)
	{
		this.names = names;
	}

	/**
	 * The bundled tables: the hand-kept {@code regions.txt} first, so its names win, then
	 * {@code regions-generated.txt} (tools/regions/gen_regions.py). A missing or unreadable one counts as empty.
	 */
	static RegionNames load()
	{
		return parse(resource("regions.txt") + "\n" + resource("regions-generated.txt"));
	}

	/** A bundled text resource next to this class, or "" when missing or unreadable. */
	static String resource(String name)
	{
		// getResourceAsStream, never getResource: the resource is inside a jar, not a file.
		try (InputStream stream = RegionNames.class.getResourceAsStream(name))
		{
			if (stream == null)
			{
				return "";
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			int n;
			while ((n = stream.read(buf)) > 0)
			{
				out.write(buf, 0, n);
			}
			return new String(out.toByteArray(), Charsets.UTF_8);
		}
		catch (IOException e)
		{
			return "";
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

	/** Every region id the table names. */
	Set<Integer> ids()
	{
		return Collections.unmodifiableSet(names.keySet());
	}

	/**
	 * "Lumbridge"; for a region the table does not name, never its number: "Instance", "Wilderness lvl 17-24",
	 * "near Lumbridge", "under Lumbridge", "Underground" or "Unmapped area". Null when the region itself is
	 * unknown (0).
	 */
	String describe(int region)
	{
		if (region <= 0)
		{
			return null;
		}
		String name = name(region);
		if (name != null)
		{
			return name;
		}
		int rx = region >> 8;
		int ry = region & 0xFF;
		if (rx >= INSTANCE_REGION_X)
		{
			return "Instance";
		}
		String wild = wildernessLevels(rx, ry);
		if (wild != null)
		{
			return wild;
		}
		String near = nearest(rx, ry);
		if (near != null)
		{
			return "near " + near;
		}
		return ry >= CAVE_REGION_Y ? underground(rx, ry) : "Unmapped area";
	}

	/** A cave region: the place right above it, else the nearest named place above it. */
	private String underground(int rx, int ry)
	{
		int above = rx << 8 | (ry - SURFACE_TO_CAVE);
		String name = name(above);
		if (name != null)
		{
			return "under " + name;
		}
		String near = nearest(rx, ry - SURFACE_TO_CAVE);
		return near != null ? "underground near " + near : "Underground";
	}

	/**
	 * The nearest named region within {@link #NEAR_RADIUS} regions on the same map layer, never across the
	 * Wilderness border: straight-line distance, then the lower id, so the answer does not depend on map order.
	 */
	private String nearest(int rx, int ry)
	{
		int layer = layer(ry);
		boolean wild = wilderness(rx, ry) != null;
		int best = -1;
		int bestDistance = Integer.MAX_VALUE;
		for (int dx = -NEAR_RADIUS; dx <= NEAR_RADIUS; dx++)
		{
			for (int dy = -NEAR_RADIUS; dy <= NEAR_RADIUS; dy++)
			{
				int x = rx + dx;
				int y = ry + dy;
				int distance = dx * dx + dy * dy;
				boolean closer = distance < bestDistance || distance == bestDistance && (x << 8 | y) < best;
				if (closer && namedAlike(x, y, layer, wild))
				{
					best = x << 8 | y;
					bestDistance = distance;
				}
			}
		}
		return best < 0 ? null : names.get(best);
	}

	/** A named real-map region on the given layer and the given side of the Wilderness border. */
	private boolean namedAlike(int x, int y, int layer, boolean wild)
	{
		boolean onMap = x >= 0 && x < INSTANCE_REGION_X && y >= 0 && y <= 0xFF;
		return onMap && layer(y) == layer && (wilderness(x, y) != null) == wild && names.containsKey(x << 8 | y);
	}

	/** "Wilderness lvl 17-24" for a region inside the Wilderness, with " (underground)" below ground. */
	private static String wildernessLevels(int rx, int ry)
	{
		Integer base = wilderness(rx, ry);
		if (base == null)
		{
			return null;
		}
		int low = (ry * REGION_SIZE - base) / TILES_PER_LEVEL + 1;
		int high = (ry * REGION_SIZE + REGION_SIZE - 1 - base) / TILES_PER_LEVEL + 1;
		return "Wilderness lvl " + low + "-" + high + (base == WILDERNESS_CAVE_Y ? " (underground)" : "");
	}

	/** The southern edge (level 1) of the Wilderness layer this region is in, or null outside it. */
	private static Integer wilderness(int rx, int ry)
	{
		int x = rx * REGION_SIZE;
		if (x < WILDERNESS_WEST || x > WILDERNESS_EAST)
		{
			return null;
		}
		int y = ry * REGION_SIZE;
		for (int base : new int[]{WILDERNESS_SURFACE_Y, WILDERNESS_CAVE_Y})
		{
			if (y >= base && y < base + WILDERNESS_HEIGHT)
			{
				return base;
			}
		}
		return null;
	}

	/** 0 for the surface, 1 for the rows of special areas above it, 2 for the cave layer. */
	private static int layer(int ry)
	{
		return ry >= CAVE_REGION_Y ? 2 : ry >= SURFACE_TOP_REGION_Y ? 1 : 0;
	}

	private static final int REGION_SIZE = 64;
	/** Instanced maps are built from x 6400 east, past the edge of the real map. */
	private static final int INSTANCE_REGION_X = 100;
	/** The surface ends near y 4224; caves are the surface plus 6400 tiles (100 regions) north. */
	private static final int SURFACE_TOP_REGION_Y = 66;
	private static final int CAVE_REGION_Y = 140;
	private static final int SURFACE_TO_CAVE = 100;
	private static final int NEAR_RADIUS = 3;
	/** Wilderness: x 2944-3391, level 1 at y 3520 on the surface and y 9920 below it, 8 tiles a level. */
	private static final int WILDERNESS_WEST = 2944;
	private static final int WILDERNESS_EAST = 3391;
	private static final int WILDERNESS_SURFACE_Y = 3520;
	private static final int WILDERNESS_CAVE_Y = 9920;
	private static final int WILDERNESS_HEIGHT = 448;
	private static final int TILES_PER_LEVEL = 8;
}
