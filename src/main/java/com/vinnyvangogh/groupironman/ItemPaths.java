package com.vinnyvangogh.groupironman;

import com.google.common.base.Charsets;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;

/**
 * Where "Path to" sends the player for a shopping line or a requested item:
 * the shops this player can be served at that have a tile in the quest data
 * (the same {@link QuestData#shop} table the shop alert walks), and, when the
 * group holds some in group storage, the banks (group storage opens from the
 * regular bank interface: OSRS Wiki, "Group storage"). Nearest first from
 * where the player stands. Pure: the plugin captures the inputs.
 *
 * <p>Drops are never a target: the quest data names a monster's region, not a
 * tile. "Nearest" is the straight-line (Chebyshev) distance, as the game counts
 * steps; Shortest Path then finds the real way there, transports included.
 */
final class ItemPaths
{
	static final String BANKS_RESOURCE = "banks.tsv";
	/** Another floor costs this many tiles more: the stairs are rarely on the way. */
	static final int PLANE_COST = 50;

	private ItemPaths()
	{
	}

	/** One place to path to. */
	static final class Stop
	{
		final String name;
		final WorldPoint at;
		/** A bank, for an item in group storage. */
		final boolean bank;

		/** The town or area the quest data puts it in, tidied for display; empty when unknown. */
		final String town;

		Stop(String name, WorldPoint at, boolean bank)
		{
			this(name, at, bank, "");
		}

		Stop(String name, WorldPoint at, boolean bank, String town)
		{
			this.name = name;
			this.at = at;
			this.bank = bank;
			this.town = tidyTown(town);
		}

		/** The wiki's "Shilo Village (location)" is just "Shilo Village". */
		private static String tidyTown(String town)
		{
			String t = town == null ? "" : town.trim();
			return t.endsWith(" (location)") ? t.substring(0, t.length() - " (location)".length()) : t;
		}

		/** "Obli's General Store (Shilo Village)": the name as a player says it, and where it is. */
		String where()
		{
			String shown = name.endsWith(".") ? name.substring(0, name.length() - 1) : name;
			return town.isEmpty() ? shown : shown + " (" + town + ")";
		}

		/** "Path to: Varrock West bank". */
		String label()
		{
			return "Path to: " + where();
		}
	}

	/** What a path was asked for: one list line, a list's next stop, or one requested item. */
	static final class Target
	{
		enum Kind
		{
			LIST_ITEM, LIST_NEXT, REQUEST_ITEM
		}

		final Kind kind;
		final String id;
		final int itemId;

		private Target(Kind kind, String id, int itemId)
		{
			this.kind = kind;
			this.id = id;
			this.itemId = itemId;
		}

		static Target listItem(String listId, int itemId)
		{
			return new Target(Kind.LIST_ITEM, listId, itemId);
		}

		static Target next(String listId)
		{
			return new Target(Kind.LIST_NEXT, listId, 0);
		}

		static Target requestItem(String requestId, int itemId)
		{
			return new Target(Kind.REQUEST_ITEM, requestId, itemId);
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof Target))
			{
				return false;
			}
			Target t = (Target) o;
			return kind == t.kind && itemId == t.itemId && id.equals(t.id);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(kind, id, itemId);
		}
	}

	/** Everything a path is worked out from, captured once per redraw. */
	static final class Planner
	{
		final QuestData data;
		final ShopSources.Viewer viewer;
		final ShopAllocation.Holdings holdings;
		final List<Stop> banks;
		/** Where the player stands; null when logged out, and then the data's order stands. */
		final WorldPoint here;

		Planner(QuestData data, ShopSources.Viewer viewer, ShopAllocation.Holdings holdings, List<Stop> banks,
			WorldPoint here)
		{
			this.data = data;
			this.viewer = viewer;
			this.holdings = holdings;
			this.banks = banks;
			this.here = here;
		}

		static Planner of(ShoppingBoard.Inputs in, List<Stop> banks, WorldPoint here)
		{
			return new Planner(in.data, in.viewer, in.holdings, banks, here);
		}

		/** A list line: its shops while some is still to get, the banks while storage covers some. */
		List<Stop> shopping(ShoppingBoard.Row r)
		{
			List<Stop> out = new ArrayList<>();
			if (r.line.stillNeeded() > 0)
			{
				out.addAll(shops(r.sources));
			}
			if (r.line.storageUsed > 0)
			{
				out.addAll(banks);
			}
			return nearestFirst(out, here);
		}

		/** A requested item: nowhere once it is done or the request closed. */
		List<Stop> request(RequestList req, RequestBoard.Row r)
		{
			if (!req.open() || r.done())
			{
				return Collections.emptyList();
			}
			List<Stop> out = new ArrayList<>(shops(ShopSources.forViewer(data.item(r.itemId), viewer, data)));
			if (holdings.storage.getOrDefault(r.itemId, 0L) > 0)
			{
				out.addAll(banks);
			}
			return nearestFirst(out, here);
		}

		/** "Path to next": every line's stops, walked nearest-next from here. */
		List<Stop> next(ShoppingBoard.Card c)
		{
			List<List<Stop>> open = new ArrayList<>();
			for (ShoppingBoard.Row r : c.rows)
			{
				List<Stop> stops = shopping(r);
				if (!stops.isEmpty())
				{
					open.add(stops);
				}
			}
			return tour(open, here);
		}

		private List<Stop> shops(ShopSources.Options o)
		{
			List<Stop> out = new ArrayList<>();
			for (ShopSources.Source s : o.sources)
			{
				QuestData.Shop shop = s.shop ? data.shop(s.name) : null;
				// A shop the data gives no tile (0, 0) cannot be walked to.
				if (shop != null && (shop.x != 0 || shop.y != 0))
				{
					out.add(new Stop(s.name, new WorldPoint(shop.x, shop.y, shop.plane), false, shop.town()));
				}
			}
			return out;
		}
	}

	/** Where a target leads now; empty once its list, request or item is gone or done. */
	static List<Stop> forTarget(Target t, List<ShoppingBoard.Card> lists, List<RequestBoard.Card> requests,
		Planner p)
	{
		if (t.kind == Target.Kind.REQUEST_ITEM)
		{
			return forRequest(t, requests, p);
		}
		for (ShoppingBoard.Card c : lists)
		{
			if (c.list.id.equals(t.id))
			{
				return t.kind == Target.Kind.LIST_NEXT ? p.next(c) : forLine(t, c, p);
			}
		}
		return Collections.emptyList();
	}

	private static List<Stop> forLine(Target t, ShoppingBoard.Card c, Planner p)
	{
		for (ShoppingBoard.Row r : c.rows)
		{
			if (r.line.itemId == t.itemId)
			{
				return p.shopping(r);
			}
		}
		return Collections.emptyList();
	}

	private static List<Stop> forRequest(Target t, List<RequestBoard.Card> requests, Planner p)
	{
		for (RequestBoard.Card c : requests)
		{
			if (!c.request.id.equals(t.id))
			{
				continue;
			}
			for (RequestBoard.Row r : c.rows)
			{
				if (r.itemId == t.itemId)
				{
					return p.request(c.request, r);
				}
			}
		}
		return Collections.emptyList();
	}

	/** Steps from {@code from} to {@code to}, another floor costing {@link #PLANE_COST} more; 0 when not known. */
	static int cost(WorldPoint from, WorldPoint to)
	{
		if (from == null)
		{
			return 0;
		}
		int steps = Math.max(Math.abs(from.getX() - to.getX()), Math.abs(from.getY() - to.getY()));
		return steps + (from.getPlane() == to.getPlane() ? 0 : PLANE_COST);
	}

	/** Tiles the player walks before the "nearest stop" is worked out again. */
	static final int REDRAW_TILES = 8;

	/** True when the position last drawn from is unknown, or the player is on another floor or {@link #REDRAW_TILES} away. */
	static boolean movedFar(WorldPoint drawn, WorldPoint now)
	{
		if (now == null)
		{
			return false;
		}
		return drawn == null || drawn.getPlane() != now.getPlane()
			|| Math.max(Math.abs(drawn.getX() - now.getX()), Math.abs(drawn.getY() - now.getY())) >= REDRAW_TILES;
	}

	/** Nearest first, each name once; the data's order breaks ties (and is all there is without a position). */
	static List<Stop> nearestFirst(List<Stop> stops, WorldPoint here)
	{
		Map<String, Stop> once = new LinkedHashMap<>();
		stops.forEach(s -> once.putIfAbsent(s.name, s));
		List<Stop> out = new ArrayList<>(once.values());
		out.sort(Comparator.comparingInt(s -> cost(here, s.at)));
		return out;
	}

	/**
	 * An efficient order through every line: the nearest stop any line can use,
	 * then the nearest from there, until each line has a stop. A stop serves
	 * every line it is a stop for. Greedy nearest-neighbour, not the shortest
	 * tour: good enough for a handful of shops, and it never sends the player
	 * past one stop to reach a farther one first.
	 */
	static List<Stop> tour(List<List<Stop>> lines, WorldPoint start)
	{
		List<List<Stop>> left = new ArrayList<>(lines);
		List<Stop> order = new ArrayList<>();
		WorldPoint from = start;
		while (!left.isEmpty())
		{
			Stop best = closest(left, from);
			order.add(best);
			left.removeIf(stops -> named(stops, best.name));
			from = best.at;
		}
		return order;
	}

	private static Stop closest(List<List<Stop>> lines, WorldPoint from)
	{
		Stop best = null;
		int bestCost = Integer.MAX_VALUE;
		for (List<Stop> stops : lines)
		{
			for (Stop s : stops)
			{
				int c = cost(from, s.at);
				if (c < bestCost)
				{
					best = s;
					bestCost = c;
				}
			}
		}
		return best;
	}

	static boolean named(List<Stop> stops, String name)
	{
		for (Stop s : stops)
		{
			if (s.name.equals(name))
			{
				return true;
			}
		}
		return false;
	}

	/** The bundled bank tiles; empty, never a failure, when the resource is missing. */
	static List<Stop> loadBanks()
	{
		try (InputStream in = ItemPaths.class.getResourceAsStream(BANKS_RESOURCE))
		{
			if (in == null)
			{
				return Collections.emptyList();
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, Charsets.UTF_8));
			return parseBanks(reader.lines().collect(Collectors.toList()));
		}
		catch (IOException | UncheckedIOException e)
		{
			return Collections.emptyList();
		}
	}

	/** "x y plane&lt;TAB&gt;name" lines, '#' comments; a line that does not read is skipped. */
	static List<Stop> parseBanks(List<String> lines)
	{
		List<Stop> out = new ArrayList<>();
		for (String line : lines)
		{
			Stop s = bankLine(line);
			if (s != null)
			{
				out.add(s);
			}
		}
		return Collections.unmodifiableList(out);
	}

	private static Stop bankLine(String line)
	{
		String[] cols = line.split("\t");
		if (line.startsWith("#") || cols.length < 2)
		{
			return null;
		}
		String[] xyz = cols[0].trim().split(" ");
		try
		{
			return xyz.length != 3 ? null : new Stop(cols[1].trim() + " bank",
				new WorldPoint(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])), true);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}
}
