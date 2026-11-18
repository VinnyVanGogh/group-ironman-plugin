package com.vinnyvangogh.groupironman;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The on-screen "Path to" card: the stop, what to get there and for whom, and what comes next. */
public class PathStopPlanTest
{
	static final int NET = 305;
	static final int ROPE = 954;

	private static final String DATA = "{"
		+ "\"quests\":[{\"id\":1,\"name\":\"Q\",\"items\":["
		+ "{\"name\":\"Big fishing net\",\"item_id\":305,\"sources\":[\"Shop: Harry's Fishing Shop (14 Coins)\"],"
		+ "\"shop_names\":[\"Harry's Fishing Shop\"]},"
		+ "{\"name\":\"Rope\",\"item_id\":954,\"sources\":[],\"shop_names\":[\"Port Sarim shop\"]}]}],"
		+ "\"shops\":["
		+ "{\"name\":\"Harry's Fishing Shop\",\"x\":2833,\"y\":3443},"
		+ "{\"name\":\"Port Sarim shop\",\"x\":3014,\"y\":3224}]}";

	private static final WorldPoint CATHERBY = new WorldPoint(2810, 3440, 0);

	private static ItemPaths.Planner planner(QuestData d, ShopAllocation.Holdings h)
	{
		return new ItemPaths.Planner(d, new ShopSources.Viewer(new HashMap<>(), QuestProgress.UNKNOWN), h,
			Collections.emptyList(), CATHERBY);
	}

	/** Alice and Bob each need {@code need} of {@code itemId}; nobody holds any. */
	private static ShoppingBoard.Row row(QuestData d, int itemId, long need)
	{
		Map<String, Map<Integer, ShopNeeds.Need>> needs = new LinkedHashMap<>();
		for (String m : Arrays.asList("Alice", "Bob"))
		{
			needs.put(m, Collections.singletonMap(itemId,
				new ShopNeeds.Need(itemId, "Item " + itemId, need, true, false, Collections.singleton("Q"))));
		}
		Map<String, Map<Integer, Long>> held = new LinkedHashMap<>();
		held.put("Alice", Collections.singletonMap(itemId, 0L));
		held.put("Bob", Collections.singletonMap(itemId, 0L));
		ShopAllocation.Holdings h = new ShopAllocation.Holdings(held, Collections.emptyMap());
		ShopAllocation.Line line = ShopAllocation.of(needs, h, id -> true).get(0);
		return new ShoppingBoard.Row(line, ShopSources.forViewer(d.item(itemId),
			new ShopSources.Viewer(new HashMap<>(), QuestProgress.UNKNOWN), d));
	}

	private static ShoppingBoard.Card card(ShoppingList list, ShoppingBoard.Row... rows)
	{
		return new ShoppingBoard.Card(list, null, Arrays.asList(rows), Collections.emptyList(),
			Collections.emptyList());
	}

	private static ShoppingList list()
	{
		return ShoppingList.create("Fishing", "", Collections.emptyList(), Arrays.asList("Alice", "Bob"),
			new LinkedHashMap<>(), "Alice", 1L);
	}

	private static ItemPaths.Stop harry()
	{
		return new ItemPaths.Stop("Harry's Fishing Shop", new WorldPoint(2833, 3443, 0), false);
	}

	/** "Path to" one line: that line only, with who it is for and the shop's price. */
	@Test
	public void aLineTargetNamesTheItemItsAmountItsPriceAndWho() throws IOException
	{
		QuestData d = ShopSourcesTest.data(DATA);
		ShoppingBoard.Row net = row(d, NET, 2);
		ShoppingBoard.Row rope = row(d, ROPE, 1);
		ShoppingList l = list();
		PathStopPlan p = PathStopPlan.of(ItemPaths.Target.listItem(l.id, NET), harry(),
			Collections.singletonList(card(l, net, rope)), Collections.emptyList(),
			planner(d, new ShopAllocation.Holdings(new LinkedHashMap<>(), Collections.emptyMap())));

		assertEquals("Harry's Fishing Shop", p.stop);
		assertEquals("Buy", p.verb());
		assertEquals(1, p.entries.size());
		PathStopPlan.Entry e = p.entries.get(0);
		assertEquals(4, e.amount);
		assertEquals("Alice, Bob", e.forWho);
		assertTrue(e.price, e.price.endsWith("gp each"));
		assertEquals("", p.then);
	}

	/** "Path to next": everything the stop serves, and the stop after it. */
	@Test
	public void aNextTargetListsEverythingTheStopServesAndTheStopAfter() throws IOException
	{
		QuestData d = ShopSourcesTest.data(DATA);
		ShoppingBoard.Row net = row(d, NET, 1);
		ShoppingBoard.Row rope = row(d, ROPE, 1);
		ShoppingList l = list();
		ShoppingBoard.Card c = card(l, net, rope);
		ItemPaths.Planner planner = planner(d, new ShopAllocation.Holdings(new LinkedHashMap<>(), Collections.emptyMap()));
		PathStopPlan p = PathStopPlan.of(ItemPaths.Target.next(l.id), harry(), Collections.singletonList(c),
			Collections.emptyList(), planner);

		assertEquals(1, p.entries.size());
		assertEquals("Port Sarim shop", p.then);
	}

	@Test
	public void aRequestTargetShowsWhatIsLeftAndWhoClaimedIt()
	{
		RequestList req = new RequestList("r1", "Alice", "Mine", Collections.singletonMap(NET, 5),
			Collections.emptySet(), false, false, 1, 1L, 1L, RequestList.ADDED);
		RequestBoard.Row row = new RequestBoard.Row(NET, "Big fishing net", 5, 2, false,
			Collections.singletonList("Bob"));
		PathStopPlan p = PathStopPlan.of(ItemPaths.Target.requestItem("r1", NET), harry(),
			Collections.emptyList(), Collections.singletonList(new RequestBoard.Card(req, Collections.singletonList(row))),
			null);

		assertEquals(1, p.entries.size());
		assertEquals(3, p.entries.get(0).amount);
		assertEquals("Bob", p.entries.get(0).forWho);
	}

	@Test
	public void noPathOrAGoneTargetIsNoCard()
	{
		assertNull(PathStopPlan.of(null, null, Collections.<ShoppingBoard.Card>emptyList(),
			Collections.<RequestBoard.Card>emptyList(), null));
		assertNull(PathStopPlan.of(ItemPaths.Target.listItem("gone", NET), harry(),
			Collections.<ShoppingBoard.Card>emptyList(), Collections.<RequestBoard.Card>emptyList(), null));
	}

	@Test
	public void aBankStopWithdrawsInsteadOfBuying()
	{
		ItemPaths.Stop bank = new ItemPaths.Stop("Catherby bank", CATHERBY, true);
		List<PathStopPlan.Entry> none = Collections.emptyList();
		assertEquals("Withdraw", new PathStopPlan(bank.name, true, none, "").verb());
	}
}
