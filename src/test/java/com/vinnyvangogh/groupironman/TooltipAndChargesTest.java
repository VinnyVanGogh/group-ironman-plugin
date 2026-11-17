package com.vinnyvangogh.groupironman;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;

/** Log basket, looting bag and noted-item tooltips, and jewellery charges (placeholder names only). */
public class TooltipAndChargesTest
{
	private static final Gson GSON = new Gson();
	private static final int LOOTING_BAG = 11941;
	private static final int RUNE_ARROW = 892;

	private static Map<Integer, Integer> counts(int... pairs)
	{
		Map<Integer, Integer> out = new TreeMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			out.put(pairs[i], pairs[i + 1]);
		}
		return out;
	}

	private static List<GroupCodec.Slot> slot(int id)
	{
		return Collections.singletonList(GroupCodec.Slot.of(id, 1));
	}

	private static ItemLookup items()
	{
		FakeItems fake = new FakeItems();
		return new ItemLookup()
		{
			@Override
			public String name(int id)
			{
				return id == LOOTING_BAG ? "Looting bag" : fake.name(id);
			}

			@Override
			public long price(int id)
			{
				return fake.price(id);
			}
		};
	}

	private static GroupCodec.Carried carriedWith(int id, SkillingContainer kind, GroupCodec.Held held)
	{
		Map<SkillingContainer, GroupCodec.Held> contents = new EnumMap<>(SkillingContainer.class);
		contents.put(kind, held);
		return new GroupCodec.Carried(counts(id, 1), counts(), slot(id), contents);
	}

	// ---- (a) log basket says which logs ----

	@Test
	public void openLogBasketNamesItsLogs()
	{
		GroupCodec.Carried c = carriedWith(FakeItems.OPEN_LOG_BASKET, SkillingContainer.LOG_BASKET,
			new GroupCodec.Held(12, counts(FakeItems.YEW_LOGS, 12)));
		assertEquals("Open log basket (12 inside: 12 x Yew logs)", PanelModel.slotCells(c, items()).get(0).tooltip());
	}

	@Test
	public void mixedLogsAreAllListed()
	{
		GroupCodec.Carried c = carriedWith(FakeItems.LOG_BASKET, SkillingContainer.LOG_BASKET,
			new GroupCodec.Held(19, counts(FakeItems.LOGS, 5, FakeItems.YEW_LOGS, 14)));
		assertEquals("Log basket (19 inside: 5 x Logs, 14 x Yew logs)",
			PanelModel.slotCells(c, items()).get(0).tooltip());
	}

	// ---- (b) looting bag shows contents and value ----

	@Test
	public void lootingBagShowsContentsAndValue()
	{
		GroupCodec.Carried c = carriedWith(LOOTING_BAG, SkillingContainer.LOOTING_BAG,
			new GroupCodec.Held(2, counts(FakeItems.COINS, 1000, FakeItems.SHARK, 3)));
		assertEquals("Looting bag (2 inside: 3 x Shark, 1000 x Coins, worth 3,400 gp)",
			PanelModel.slotCells(c, items()).get(0).tooltip());
	}

	@Test
	public void lootingBagSurvivesTheWire()
	{
		GroupCodec.Carried sent = carriedWith(LOOTING_BAG, SkillingContainer.LOOTING_BAG,
			new GroupCodec.Held(2, counts(FakeItems.COINS, 1000, FakeItems.SHARK, 3)));
		GroupCodec.Decoded d = GroupCodec.decode(GSON, GroupCodec.encodeCarried(GSON, sent));
		assertEquals(2, d.carried.inside(SkillingContainer.LOOTING_BAG));
		assertEquals(counts(FakeItems.COINS, 1000, FakeItems.SHARK, 3),
			d.carried.contents.get(SkillingContainer.LOOTING_BAG).items);
	}

	@Test
	public void openAndClosedLootingBagAreTheSameContainer()
	{
		assertEquals(SkillingContainer.LOOTING_BAG, SkillingContainer.fromItemName("Open looting bag"));
		assertEquals(SkillingContainer.LOOTING_BAG, SkillingContainer.fromItemName("Looting bag"));
	}

	// ---- (c) noted ids resolve to real names ----

	@Test
	public void aNotedIdWithoutANameTakesItsItemsName()
	{
		String name = ItemNames.resolve(FakeItems.NOTED_YEW_LOGS,
			id -> id == FakeItems.YEW_LOGS ? "Yew logs" : "null", id -> FakeItems.YEW_LOGS);
		assertEquals("Yew logs", name);
	}

	@Test
	public void aKnownNameIsKept()
	{
		assertEquals("Shark", ItemNames.resolve(386, id -> "Shark", id -> 385));
	}

	@Test
	public void noNameAnywhereIsNull()
	{
		assertNull(ItemNames.resolve(1, id -> null, id -> id));
		assertNull(ItemNames.resolve(1, id -> "", id -> 2));
	}

	// ---- jewellery charges ----

	private static Map<String, Integer> charges(String... names)
	{
		return JewelleryCharges.of(Arrays.asList(names), key -> null);
	}

	@Test
	public void dosedJewelleryReadsItsCharges()
	{
		Map<String, Integer> c = charges("Ring of dueling(6)", "Games necklace(3)", "Amulet of glory(4)",
			"Ring of wealth (5)", "Slayer ring (8)", "Shark");
		assertEquals(Integer.valueOf(6), c.get("Ring of dueling"));
		assertEquals(Integer.valueOf(3), c.get("Games necklace"));
		assertEquals(Integer.valueOf(4), c.get("Amulet of glory"));
		assertEquals(Integer.valueOf(5), c.get("Ring of wealth"));
		assertEquals(Integer.valueOf(8), c.get("Slayer ring"));
		assertEquals(5, c.size());
	}

	@Test
	public void imbuedAndTrimmedVariantsStillCount()
	{
		assertEquals(Integer.valueOf(5), charges("Ring of wealth (i5)").get("Ring of wealth"));
		assertEquals(Integer.valueOf(4), charges("Amulet of glory (t4)").get("Amulet of glory"));
	}

	@Test
	public void theFullerOfTwoIsShown()
	{
		assertEquals(Integer.valueOf(7), charges("Ring of dueling(2)", "Ring of dueling(7)").get("Ring of dueling"));
	}

	@Test
	public void potionsAndOtherDosesAreNotJewellery()
	{
		assertTrue(charges("Prayer potion(4)", "Super restore(4)", "Guam potion (unf)").isEmpty());
	}

	@Test
	public void bindingNecklaceComesFromItemChargesConfig()
	{
		Map<String, Integer> c = JewelleryCharges.of(Collections.singletonList("Binding necklace"),
			key -> "bindingNecklace".equals(key) ? 12 : null);
		assertEquals(Integer.valueOf(12), c.get("Binding necklace"));
		assertTrue(charges("Binding necklace").isEmpty());
	}

	@Test
	public void chargesRoundTripAndShowOnTheCard()
	{
		GroupCodec.Carried sent = new GroupCodec.Carried(counts(), counts(), Collections.emptyList(),
			Collections.emptyMap(), charges("Ring of dueling(6)", "Games necklace(3)"));
		GroupCodec.Decoded d = GroupCodec.decode(GSON, GroupCodec.encodeCarried(GSON, sent));
		assertEquals(sent.charges, d.carried.charges);
		assertEquals("Games necklace 3, Ring of dueling 6", JewelleryCharges.text(d.carried.charges));
	}

	@Test
	public void noChargesAddsNothingToTheMessage()
	{
		GroupCodec.Carried sent = new GroupCodec.Carried(counts(), counts());
		assertTrue(!GroupCodec.encodeCarried(GSON, sent).contains("jc"));
	}

	@Test
	public void badChargesAreSkippedOneAtATime()
	{
		String json = "{\"v\":1,\"k\":\"carried\",\"inv\":[],\"eq\":[],\"jc\":{\"Ring of dueling\":6,"
			+ "\"Neg\":-1,\"Huge\":99999999,\"Text\":\"x\"}}";
		GroupCodec.Decoded d = GroupCodec.decode(GSON, json);
		assertEquals(Collections.singletonMap("Ring of dueling", 6), d.carried.charges);
	}
}
