package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.vinnyvangogh.testkit.ui.RuneLiteLook;
import com.vinnyvangogh.testkit.ui.SwingDriver;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.Before;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.GimPanelFixture.GROUP;
import static com.vinnyvangogh.groupironman.GimPanelFixture.REGIONS;
import static com.vinnyvangogh.groupironman.GimPanelFixture.SELF;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Member card items (Board 2026-11-14): a carried log basket shows how many
 * logs are inside when its owner's client knows, nothing when it does not;
 * the contents sync without breaking older builds and are findable; the worn
 * grid draws the game's empty-slot silhouette wherever nothing is worn. All of
 * it on the player's own card before joining a group too.
 */
public class MemberCardContainersTest
{
	private static final Gson GSON = new Gson();
	/** EquipmentInventorySlot.WEAPON. */
	private static final int WEAPON_SLOT = 3;

	@Before
	public void lookLikeRuneLite()
	{
		SwingDriver.onEdt(RuneLiteLook::install);
	}

	/** FakeItems, with the whip worn in the weapon slot as ItemManager's stats say. */
	private static final class Items implements ItemLookup
	{
		private final FakeItems fake = new FakeItems();

		@Override
		public String name(int id)
		{
			return fake.name(id);
		}

		@Override
		public long price(int id)
		{
			return fake.price(id);
		}

		@Override
		public int slot(int id)
		{
			return id == FakeItems.WHIP ? WEAPON_SLOT : -1;
		}
	}

	private static Map<Integer, Integer> items(int... idQty)
	{
		Map<Integer, Integer> out = new TreeMap<>();
		for (int i = 0; i < idQty.length; i += 2)
		{
			out.put(idQty[i], idQty[i + 1]);
		}
		return out;
	}

	private static List<GroupCodec.Slot> slots(int... idQty)
	{
		List<GroupCodec.Slot> out = new ArrayList<>();
		for (int i = 0; i < idQty.length; i += 2)
		{
			out.add(GroupCodec.Slot.of(idQty[i], idQty[i + 1]));
		}
		return out;
	}

	private static Map<SkillingContainer, GroupCodec.Held> basketOf(int count, Map<Integer, Integer> logs)
	{
		Map<SkillingContainer, GroupCodec.Held> out = new EnumMap<>(SkillingContainer.class);
		out.put(SkillingContainer.LOG_BASKET, new GroupCodec.Held(count, logs));
		return out;
	}

	/** The Board's own card: a basket in slot 1, logs after it, a whip worn. */
	private static GroupCodec.Carried carried(Map<SkillingContainer, GroupCodec.Held> contents)
	{
		return new GroupCodec.Carried(items(FakeItems.LOG_BASKET, 1, FakeItems.YEW_LOGS, 2),
			items(FakeItems.WHIP, 1), slots(FakeItems.LOG_BASKET, 1, FakeItems.YEW_LOGS, 1, FakeItems.YEW_LOGS, 1),
			contents);
	}

	// ---- Inventory grid: the count, or nothing ----

	@Test
	public void basketCountShownWhenKnown()
	{
		List<PanelModel.ItemCell> cells = PanelModel.slotCells(carried(basketOf(19, Collections.emptyMap())), new Items());
		assertEquals(19, cells.get(0).inside);
		assertEquals("Log basket (19 inside)", cells.get(0).tooltip());
		assertEquals("the logs beside it are not containers", -1, cells.get(1).inside);
		assertEquals("Yew logs", cells.get(1).tooltip());
	}

	@Test
	public void basketCountHiddenWhenUnknown()
	{
		List<PanelModel.ItemCell> cells = PanelModel.slotCells(carried(Collections.emptyMap()), new Items());
		assertEquals(-1, cells.get(0).inside);
		assertEquals("Log basket", cells.get(0).tooltip());
	}

	@Test
	public void anEmptyBasketIsZeroNotUnknown()
	{
		List<PanelModel.ItemCell> cells = PanelModel.slotCells(carried(basketOf(0, Collections.emptyMap())), new Items());
		assertEquals(0, cells.get(0).inside);
	}

	// ---- Group sync, old and new builds ----

	@Test
	public void contentsRoundTrip()
	{
		GroupCodec.Carried sent = carried(basketOf(19, items(FakeItems.YEW_LOGS, 14, FakeItems.LOGS, 5)));
		GroupCodec.Decoded d = GroupCodec.decode(GSON, GroupCodec.encodeCarried(GSON, sent));
		assertEquals(GroupCodec.Kind.CARRIED, d.kind);
		assertEquals(19, d.carried.inside(SkillingContainer.LOG_BASKET));
		assertEquals(items(FakeItems.YEW_LOGS, 14, FakeItems.LOGS, 5),
			d.carried.contents.get(SkillingContainer.LOG_BASKET).items);
		assertEquals(-1, d.carried.inside(SkillingContainer.FISH_BARREL));
		assertEquals(sent.slots.size(), d.carried.slots.size());
	}

	@Test
	public void anOldBuildSeesExactlyWhatItAlwaysDid()
	{
		GroupCodec.Carried sent = carried(basketOf(19, items(FakeItems.YEW_LOGS, 19)));
		JsonObject now = GSON.fromJson(GroupCodec.encodeCarried(GSON, sent), JsonObject.class);
		JsonObject before = GSON.fromJson(GroupCodec.encodeCarried(GSON, sent.inventory, sent.worn, sent.slots),
			JsonObject.class);
		assertTrue(now.has("cc"));
		now.remove("cc");
		// Only a new field: v, k, inv, eq and is are byte for byte the old message.
		assertEquals(before, now);
	}

	@Test
	public void aMessageFromAnOldBuildHasNoNumbers()
	{
		String old = GroupCodec.encodeCarried(GSON, items(FakeItems.LOG_BASKET, 1), items(),
			slots(FakeItems.LOG_BASKET, 1));
		GroupCodec.Decoded d = GroupCodec.decode(GSON, old);
		assertTrue(d.carried.contents.isEmpty());
		assertEquals(-1, PanelModel.slotCells(d.carried, new Items()).get(0).inside);
	}

	@Test
	public void unknownContainersAndImpossibleCountsAreSkippedOneAtATime()
	{
		String json = "{\"v\":1,\"k\":\"carried\",\"inv\":[],\"eq\":[],\"cc\":{"
			+ "\"GEM_BAG\":{\"n\":60},"
			+ "\"FISH_BARREL\":{\"n\":-3},"
			+ "\"COAL_BAG\":{\"n\":100000},"
			+ "\"LOG_BASKET\":{\"n\":7,\"i\":[1515,7]}}}";
		GroupCodec.Decoded d = GroupCodec.decode(GSON, json);
		assertEquals(Collections.singleton(SkillingContainer.LOG_BASKET), d.carried.contents.keySet());
		assertEquals(7, d.carried.inside(SkillingContainer.LOG_BASKET));
		assertEquals("a garbled cc is no container known", 0,
			GroupCodec.decode(GSON, "{\"v\":1,\"k\":\"carried\",\"cc\":[1,2]}").carried.contents.size());
	}

	@Test
	public void aCountWithoutKnownItemsSendsNoItemList()
	{
		String json = GroupCodec.encodeCarried(GSON, carried(basketOf(19, Collections.emptyMap())));
		JsonObject cc = GSON.fromJson(json, JsonObject.class).getAsJsonObject("cc");
		assertEquals(19, cc.getAsJsonObject("LOG_BASKET").get("n").getAsInt());
		assertFalse(cc.getAsJsonObject("LOG_BASKET").has("i"));
	}

	// ---- Find ----

	@Test
	public void findCountsLogsInAMembersBasketWhereKnown()
	{
		GroupRoster roster = new GroupRoster();
		roster.putCarried(SELF, carried(basketOf(14, items(FakeItems.YEW_LOGS, 14))));
		List<GroupSearch.Hit> hits = GroupSearch.hits("yew", roster.searchSources(), new Items());
		boolean inBasket = false;
		for (GroupSearch.Hit h : hits)
		{
			inBasket |= "log basket".equals(h.where) && h.quantity == 14 && SELF.equals(h.member);
		}
		assertTrue(hits.toString(), inBasket);
	}

	@Test
	public void findSaysNothingOfABasketWhoseLogsAreUnknown()
	{
		GroupRoster roster = new GroupRoster();
		roster.putCarried(SELF, carried(basketOf(14, Collections.emptyMap())));
		for (GroupSearch.Hit h : GroupSearch.hits("yew", roster.searchSources(), new Items()))
		{
			assertFalse("only the 2 loose logs: " + h.where, "log basket".equals(h.where));
		}
	}

	// ---- The card itself, before joining a group ----

	private static GroupIronmanPanel.View notConnected(GroupRoster roster)
	{
		GimPanelFixture.Scene scene = new GimPanelFixture.Scene();
		return new GroupIronmanPanel.View(GroupSync.State.READY, roster, GroupMembers.of(GROUP, Arrays.asList(SELF)),
			new Items(), scene.switches, REGIONS, 0, SELF, GamePresence.UNKNOWN, null, true, scene.sharing);
	}

	private static List<GroupIronmanPanel.SmallItem> smallItems(Component root)
	{
		List<GroupIronmanPanel.SmallItem> out = new ArrayList<>();
		if (root instanceof GroupIronmanPanel.SmallItem)
		{
			out.add((GroupIronmanPanel.SmallItem) root);
		}
		if (root instanceof Container)
		{
			for (Component child : ((Container) root).getComponents())
			{
				out.addAll(smallItems(child));
			}
		}
		return out;
	}

	private static List<GroupIronmanPanel.SmallItem> render(GroupRoster roster)
	{
		GimPanelFixture f = new GimPanelFixture();
		f.panel.render(notConnected(roster));
		f.ui.layout(GimPanelFixture.PANEL_WIDTH);
		f.ui.assertShows("Not connected");
		return smallItems(f.panel);
	}

	@Test
	public void wornGridDrawsASilhouetteForEachEmptySlotAndTheItemForFilledOnes()
	{
		SwingDriver.onEdt(() ->
		{
			GroupRoster roster = new GroupRoster();
			roster.putCarried(SELF, carried(Collections.emptyMap()));
			Set<Integer> silhouettes = new TreeSet<>();
			int worn = 0;
			for (GroupIronmanPanel.SmallItem cell : render(roster))
			{
				if (cell.emptySlot >= 0)
				{
					assertTrue("one silhouette per slot", silhouettes.add(cell.emptySlot));
					assertEquals(-1, cell.inside);
				}
				else if (cell.getToolTipText() != null && cell.getToolTipText().equals("Abyssal whip"))
				{
					worn++;
				}
			}
			Set<Integer> expected = new TreeSet<>(WornSlotIcons.SPRITES.keySet());
			expected.remove(WEAPON_SLOT);
			assertEquals("every empty worn slot, not the weapon", expected, silhouettes);
			assertEquals(1, worn);
			assertEquals(11, WornSlotIcons.SPRITES.size());
		});
	}

	@Test
	public void nothingWornIsElevenSilhouettes()
	{
		SwingDriver.onEdt(() ->
		{
			GroupRoster roster = new GroupRoster();
			roster.putCarried(SELF, new GroupCodec.Carried(items(FakeItems.SHARK, 1), items(), slots(FakeItems.SHARK, 1)));
			int silhouettes = 0;
			for (GroupIronmanPanel.SmallItem cell : render(roster))
			{
				silhouettes += cell.emptySlot >= 0 ? 1 : 0;
			}
			assertEquals(11, silhouettes);
		});
	}

	@Test
	public void ownCardShowsTheBasketCountBeforeJoining()
	{
		SwingDriver.onEdt(() ->
		{
			GroupRoster roster = new GroupRoster();
			roster.putCarried(SELF, carried(basketOf(19, Collections.emptyMap())));
			int counted = 0;
			for (GroupIronmanPanel.SmallItem cell : render(roster))
			{
				if (cell.inside >= 0)
				{
					assertEquals(19, cell.inside);
					assertEquals("Log basket (19 inside)", cell.getToolTipText());
					counted++;
				}
			}
			assertEquals(1, counted);
		});
	}

	@Test
	public void unknownBasketDrawsNoNumberOnTheCard()
	{
		SwingDriver.onEdt(() ->
		{
			GroupRoster roster = new GroupRoster();
			roster.putCarried(SELF, carried(Collections.emptyMap()));
			for (GroupIronmanPanel.SmallItem cell : render(roster))
			{
				assertEquals(-1, cell.inside);
			}
		});
	}

	@Test
	public void silhouetteSpritesAreTheGamesEquipmentSlotSprites()
	{
		assertEquals(net.runelite.api.SpriteID.EQUIPMENT_SLOT_HEAD, WornSlotIcons.sprite(0));
		assertEquals(net.runelite.api.SpriteID.EQUIPMENT_SLOT_WEAPON, WornSlotIcons.sprite(WEAPON_SLOT));
		assertEquals(-1, WornSlotIcons.sprite(6));
	}
}
