package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Board in-game report 2026-11-18: standing in Al Kharid with blood runes, bronze bars and buckets on the list,
 * "Path to next" said Obli's General Store (Shilo Village). Run against the bundled quest data, not a fixture.
 */
public class ItemPathsAlKharidTest
{
	private static final int BLOOD_RUNE = 565;
	private static final int BRONZE_BAR = 2349;
	private static final int BUCKET = 1925;
	private static final WorldPoint AL_KHARID = new WorldPoint(3293, 3180, 0);

	private static final QuestData DATA = QuestData.load(new Gson());

	private static ShoppingBoard.Row row(int itemId, String name)
	{
		Map<String, Map<Integer, ShopNeeds.Need>> needs = new LinkedHashMap<>();
		needs.put("Alice", Collections.singletonMap(itemId,
			new ShopNeeds.Need(itemId, name, 5, true, false, Collections.singleton("Q"))));
		ShopAllocation.Holdings held = new ShopAllocation.Holdings(
			Collections.singletonMap("Alice", Collections.singletonMap(itemId, 0L)), Collections.emptyMap());
		ShopAllocation.Line line = ShopAllocation.of(needs, held, id -> true).get(0);
		ShopSources.Viewer viewer = viewer();
		return new ShoppingBoard.Row(line, ShopSources.forViewer(DATA.item(itemId), viewer, DATA));
	}

	private static ShopSources.Viewer viewer()
	{
		return new ShopSources.Viewer(new HashMap<>(), QuestProgress.UNKNOWN);
	}

	private static ItemPaths.Planner planner(WorldPoint here)
	{
		ShopAllocation.Holdings none = new ShopAllocation.Holdings(Collections.emptyMap(), Collections.emptyMap());
		return new ItemPaths.Planner(DATA, viewer(), none, Collections.emptyList(), here);
	}

	private static List<ItemPaths.Stop> tour(WorldPoint here)
	{
		ItemPaths.Planner p = planner(here);
		List<List<ItemPaths.Stop>> open = new ArrayList<>();
		for (ShoppingBoard.Row r : rows())
		{
			open.add(p.shopping(r));
		}
		return ItemPaths.tour(open, here);
	}

	private static List<ShoppingBoard.Row> rows()
	{
		List<ShoppingBoard.Row> rows = new ArrayList<>();
		rows.add(row(BLOOD_RUNE, "Blood rune"));
		rows.add(row(BRONZE_BAR, "Bronze bar"));
		rows.add(row(BUCKET, "Bucket"));
		return rows;
	}

	@Test
	public void inAlKharidTheNextStopIsAnAlKharidShop()
	{
		List<ItemPaths.Stop> tour = tour(AL_KHARID);
		assertFalse(tour.isEmpty());
		assertTrue("next stop was " + tour.get(0).name + " (" + tour.get(0).town + ")",
			"Al Kharid".equals(tour.get(0).town));
	}

	/** The cause in game: the panel drew from a stale or missing position and never redrew as the player walked. */
	@Test
	public void walkingFarOrChangingFloorRedrawsButStandingStillDoesNot()
	{
		assertTrue(ItemPaths.movedFar(null, AL_KHARID));
		assertFalse(ItemPaths.movedFar(AL_KHARID, null));
		assertFalse(ItemPaths.movedFar(AL_KHARID, new WorldPoint(3296, 3183, 0)));
		assertTrue(ItemPaths.movedFar(AL_KHARID, new WorldPoint(3293, 3188, 0)));
		assertTrue(ItemPaths.movedFar(AL_KHARID, new WorldPoint(3293, 3180, 1)));
	}

	@Test
	public void aStopSaysWhichListItemsItSupplies()
	{
		ShoppingBoard.Card card = new ShoppingBoard.Card(null, null, rows(), Collections.emptyList(),
			Collections.emptyList());
		ItemPaths.Stop first = tour(AL_KHARID).get(0);
		List<String> items = PathStopPlan.supplies(card, first, planner(AL_KHARID));
		assertTrue(items.toString(), items.contains("Blood rune") && items.contains("Bucket"));
		assertFalse(items.toString(), items.contains("Bronze bar"));
	}

	@Test
	public void everyStopNamesItsTown()
	{
		for (ItemPaths.Stop s : tour(AL_KHARID))
		{
			assertFalse(s.name + " has no town", s.town.isEmpty());
		}
		ItemPaths.Stop obli = null;
		for (ItemPaths.Stop s : tour(AL_KHARID))
		{
			if (s.name.startsWith("Obli"))
			{
				obli = s;
			}
		}
		assertEquals("Obli's General Store (Shilo Village)", obli.where());
	}
}
