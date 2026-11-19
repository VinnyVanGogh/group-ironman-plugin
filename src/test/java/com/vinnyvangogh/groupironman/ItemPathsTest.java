package com.vinnyvangogh.groupironman;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** "Path to": the nearest place to get each item, from where the player stands. */
public class ItemPathsTest
{
	private static final int NET = 305;
	private static final int ROPE = 954;
	private static final int SHARK = 385;

	/** Harry's is in Catherby, the Port Sarim shop in the south; the guild needs 68 Fishing. */
	private static final String DATA = "{"
		+ "\"quests\":[{\"id\":1,\"name\":\"Q\",\"items\":["
		+ "{\"name\":\"Big fishing net\",\"item_id\":305,\"sources\":[],"
		+ "\"shop_names\":[\"Fishing Guild Shop\",\"Harry's Fishing Shop\",\"Port Sarim shop\"]},"
		+ "{\"name\":\"Rope\",\"item_id\":954,\"sources\":[],\"shop_names\":[\"Port Sarim shop\",\"No tile shop\"]}]}],"
		+ "\"shops\":["
		+ "{\"name\":\"Fishing Guild Shop\",\"x\":2611,\"y\":3393,\"reachability\":{\"required_skills\":"
		+ "[{\"skill\":\"fishing\",\"level\":68}]}},"
		+ "{\"name\":\"Harry's Fishing Shop\",\"x\":2833,\"y\":3443},"
		+ "{\"name\":\"Port Sarim shop\",\"x\":3014,\"y\":3224},"
		+ "{\"name\":\"No tile shop\"}]}";

	private static final List<ItemPaths.Stop> BANKS = ItemPaths.parseBanks(Arrays.asList(
		"# comment",
		"3185 3438 0\tVarrock West",
		"2807 3441 0\tCatherby",
		"not a line",
		"3208 3220 2\tLumbridge Castle"));

	private static final WorldPoint CATHERBY = new WorldPoint(2810, 3440, 0);
	private static final WorldPoint PORT_SARIM = new WorldPoint(3010, 3210, 0);

	private static QuestData data() throws IOException
	{
		return ShopSourcesTest.data(DATA);
	}

	private static ShopSources.Viewer viewer()
	{
		return new ShopSources.Viewer(new HashMap<>(), QuestProgress.UNKNOWN);
	}

	private static ShopAllocation.Holdings holdings(int itemId, long alice, long storage)
	{
		Map<String, Map<Integer, Long>> held = new LinkedHashMap<>();
		held.put("Alice", Collections.singletonMap(itemId, alice));
		return new ShopAllocation.Holdings(held, Collections.singletonMap(itemId, storage));
	}

	private static ItemPaths.Planner planner(QuestData d, ShopAllocation.Holdings h, WorldPoint here)
	{
		return new ItemPaths.Planner(d, viewer(), h, BANKS, here);
	}

	private static ItemPaths.Planner withdrawing(QuestData d, ShopAllocation.Holdings h, WorldPoint here)
	{
		return new ItemPaths.Planner(d, viewer(), h, BANKS, here, true);
	}

	/** One shopping row: Alice needs {@code need} of the item, holding {@code alice}, with {@code storage} in storage. */
	private static ShoppingBoard.Row row(QuestData d, int itemId, long need, long alice, long storage)
	{
		Map<String, Map<Integer, ShopNeeds.Need>> needs = new LinkedHashMap<>();
		needs.put("Alice", Collections.singletonMap(itemId,
			new ShopNeeds.Need(itemId, "Item", need, true, false, Collections.singleton("Q"))));
		ShopAllocation.Line line = ShopAllocation.of(needs, holdings(itemId, alice, storage), id -> true).get(0);
		return new ShoppingBoard.Row(line, ShopSources.forViewer(d.item(itemId), viewer(), d));
	}

	private static List<String> names(List<ItemPaths.Stop> stops)
	{
		List<String> out = new ArrayList<>();
		stops.forEach(s -> out.add(s.name));
		return out;
	}

	@Test
	public void theNearestShopComesFirstFromWhereThePlayerStands() throws IOException
	{
		QuestData d = data();
		ShoppingBoard.Row r = row(d, NET, 1, 0, 0);

		List<ItemPaths.Stop> fromCatherby = planner(d, holdings(NET, 0, 0), CATHERBY).shopping(r);
		assertEquals(Arrays.asList("Harry's Fishing Shop", "Port Sarim shop"), names(fromCatherby));
		assertEquals("Path to: Harry's Fishing Shop", fromCatherby.get(0).label());
		assertEquals(new WorldPoint(2833, 3443, 0), fromCatherby.get(0).at);

		List<ItemPaths.Stop> fromSarim = planner(d, holdings(NET, 0, 0), PORT_SARIM).shopping(r);
		assertEquals("Port Sarim shop", fromSarim.get(0).name);
	}

	/** A shop the player cannot be served at, or one with no tile, is never a target. */
	@Test
	public void gatedShopsAndShopsWithoutATileAreNeverTargets() throws IOException
	{
		QuestData d = data();
		WorldPoint atTheGuild = new WorldPoint(2611, 3393, 0);
		assertEquals(Arrays.asList("Harry's Fishing Shop", "Port Sarim shop"),
			names(planner(d, holdings(NET, 0, 0), atTheGuild).shopping(row(d, NET, 1, 0, 0))));
		assertEquals(Collections.singletonList("Port Sarim shop"),
			names(planner(d, holdings(ROPE, 0, 0), CATHERBY).shopping(row(d, ROPE, 1, 0, 0))));
	}

	/** Gathering: an item held in group storage counts as had, so there is no withdraw stop. */
	@Test
	public void whileGatheringStorageIsHadAndHasNoStop() throws IOException
	{
		QuestData d = data();
		ShoppingBoard.Row covered = row(d, SHARK, 5, 0, 5);
		assertTrue(planner(d, holdings(SHARK, 0, 5), new WorldPoint(3200, 3430, 0)).shopping(covered).isEmpty());
		// Part in storage, part to buy: only the shop is a stop.
		ShoppingBoard.Row partly = row(d, NET, 3, 0, 1);
		for (ItemPaths.Stop s : planner(d, holdings(NET, 0, 1), CATHERBY).shopping(partly))
		{
			assertTrue(s.name, !s.bank);
		}
	}

	/** Quest started: withdraw from the nearest bank, since group storage opens from any bank. */
	@Test
	public void withdrawingGoesToTheNearestBank() throws IOException
	{
		QuestData d = data();
		ShoppingBoard.Row covered = row(d, SHARK, 5, 0, 5);
		List<ItemPaths.Stop> stops = withdrawing(d, holdings(SHARK, 0, 5), new WorldPoint(3200, 3430, 0)).shopping(covered);
		assertEquals("Varrock West bank", stops.get(0).name);
		assertTrue(stops.get(0).bank);

		// Part from storage, part to buy: banks and shops compete, nearest wins.
		ShoppingBoard.Row partly = row(d, NET, 3, 0, 1);
		assertEquals("Catherby bank", withdrawing(d, holdings(NET, 0, 1), CATHERBY).shopping(partly).get(0).name);
	}

	/** An item that moved from group storage into a member's inventory is still had: no stop either way. */
	@Test
	public void anItemInAnInventoryStillCounts() throws IOException
	{
		QuestData d = data();
		assertTrue(withdrawing(d, holdings(SHARK, 5, 0), CATHERBY).shopping(row(d, SHARK, 5, 5, 0)).isEmpty());
	}

	@Test
	public void aDoneLineWithNothingInStorageHasNowhereToGo() throws IOException
	{
		QuestData d = data();
		assertTrue(planner(d, holdings(NET, 1, 0), CATHERBY).shopping(row(d, NET, 1, 1, 0)).isEmpty());
	}

	/** Logged out: no position, so the data's order stands and nothing is dropped. */
	@Test
	public void withoutAPositionTheDataOrderStands() throws IOException
	{
		QuestData d = data();
		assertEquals(Arrays.asList("Harry's Fishing Shop", "Port Sarim shop"),
			names(planner(d, holdings(NET, 0, 0), null).shopping(row(d, NET, 1, 0, 0))));
	}

	@Test
	public void anotherPlaneCostsExtra()
	{
		List<ItemPaths.Stop> stops = ItemPaths.nearestFirst(BANKS, new WorldPoint(3208, 3220, 0));
		// Lumbridge Castle's bank is right above, on plane 2; Varrock West is 218 tiles away.
		assertEquals("Lumbridge Castle bank", stops.get(0).name);
		assertTrue(ItemPaths.cost(new WorldPoint(3208, 3220, 0), stops.get(0).at) > 0);
	}

	private static RequestList request(boolean closed, int itemId)
	{
		return new RequestList("r1", "Alice", "Mine", Collections.singletonMap(itemId, 5), Collections.emptySet(),
			closed, false, 1, 1L, 1L, RequestList.ADDED);
	}

	private static RequestBoard.Row requestRow(int itemId, long have)
	{
		return new RequestBoard.Row(itemId, "Item", 5, have, false, Collections.emptyList());
	}

	@Test
	public void requestItemsPathToShopsOrToStorage() throws IOException
	{
		QuestData d = data();
		// A shop item, nothing held: the shop.
		assertEquals("Harry's Fishing Shop",
			planner(d, holdings(NET, 0, 0), CATHERBY).request(request(false, NET), requestRow(NET, 0)).get(0).name);
		// No shop in the data, some in storage: a bank, but only when withdrawing.
		assertEquals("Catherby bank",
			withdrawing(d, holdings(SHARK, 0, 2), CATHERBY).request(request(false, SHARK), requestRow(SHARK, 2)).get(0).name);
		assertTrue(planner(d, holdings(SHARK, 0, 2), CATHERBY).request(request(false, SHARK), requestRow(SHARK, 2))
			.isEmpty());
		// No shop, nothing in storage: nowhere.
		assertTrue(planner(d, holdings(SHARK, 2, 0), CATHERBY).request(request(false, SHARK), requestRow(SHARK, 2))
			.isEmpty());
		// Done, or the request is closed: nowhere.
		assertTrue(planner(d, holdings(NET, 5, 0), CATHERBY).request(request(false, NET), requestRow(NET, 5)).isEmpty());
		assertTrue(planner(d, holdings(NET, 0, 0), CATHERBY).request(request(true, NET), requestRow(NET, 0)).isEmpty());
	}

	/** "Path to next": nearest stop first, then nearest from there; one stop serves every item it sells. */
	@Test
	public void theTourWalksNearestNextAndSharesStops()
	{
		ItemPaths.Stop harry = new ItemPaths.Stop("Harry's", new WorldPoint(2833, 3443, 0), false);
		ItemPaths.Stop sarim = new ItemPaths.Stop("Sarim", new WorldPoint(3014, 3224, 0), false);
		ItemPaths.Stop varrock = new ItemPaths.Stop("Varrock", new WorldPoint(3185, 3438, 0), false);
		List<List<ItemPaths.Stop>> needs = Arrays.asList(
			Arrays.asList(varrock),
			Arrays.asList(sarim, harry),
			Arrays.asList(sarim));

		assertEquals(Arrays.asList("Harry's", "Sarim", "Varrock"), names(ItemPaths.tour(needs, CATHERBY)));
		assertEquals(Arrays.asList("Varrock", "Sarim"), names(ItemPaths.tour(needs, new WorldPoint(3190, 3440, 0))));
		assertTrue(ItemPaths.tour(Collections.emptyList(), CATHERBY).isEmpty());
	}

	@Test
	public void aTargetWhoseListIsGoneHasNowhereToGo() throws IOException
	{
		QuestData d = data();
		ItemPaths.Planner p = planner(d, holdings(NET, 0, 0), CATHERBY);
		assertTrue(ItemPaths.forTarget(ItemPaths.Target.listItem("gone", NET), Collections.emptyList(),
			Collections.emptyList(), p).isEmpty());
		assertTrue(ItemPaths.forTarget(ItemPaths.Target.next("gone"), Collections.emptyList(),
			Collections.emptyList(), p).isEmpty());
		assertTrue(ItemPaths.forTarget(ItemPaths.Target.requestItem("gone", NET), Collections.emptyList(),
			Collections.emptyList(), p).isEmpty());
	}

	@Test
	public void aRequestTargetResolvesThroughItsCard() throws IOException
	{
		QuestData d = data();
		ItemPaths.Planner p = planner(d, holdings(NET, 0, 0), CATHERBY);
		RequestBoard.Card open = new RequestBoard.Card(request(false, NET), Collections.singletonList(requestRow(NET, 0)));
		assertEquals("Harry's Fishing Shop", ItemPaths.forTarget(ItemPaths.Target.requestItem("r1", NET),
			Collections.emptyList(), Collections.singletonList(open), p).get(0).name);
	}

	@Test
	public void theBundledBankTableLoads()
	{
		List<ItemPaths.Stop> banks = ItemPaths.loadBanks();
		assertTrue("expected the bundled banks, got " + banks.size(), banks.size() >= 20);
		assertTrue(names(banks).contains("Lumbridge Castle bank"));
		for (ItemPaths.Stop b : banks)
		{
			assertTrue(b.name, b.bank);
		}
	}

	@Test
	public void targetsAreEqualByWhatTheyPointAt()
	{
		assertEquals(ItemPaths.Target.listItem("a", 1), ItemPaths.Target.listItem("a", 1));
		assertTrue(!ItemPaths.Target.listItem("a", 1).equals(ItemPaths.Target.requestItem("a", 1)));
		assertEquals(ItemPaths.Target.next("a").hashCode(), ItemPaths.Target.next("a").hashCode());
	}
}
