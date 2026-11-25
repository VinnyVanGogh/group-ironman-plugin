package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** "Bring this": Equip vs Missing, who in the group has it, and region entry. */
public class GearChecksTest
{
	private static final int SALVE_EI = 1;
	private static final int SALVE_E = 2;
	private static final int SHARK = 3;
	private static final int PRAYER_4 = 4;
	private static final int PRAYER_2 = 5;

	private static final ItemLookup ITEMS = new ItemLookup()
	{
		private final Map<Integer, String> names = new HashMap<>();

		{
			names.put(SALVE_EI, "Salve amulet(ei)");
			names.put(SALVE_E, "Salve amulet(e)");
			names.put(SHARK, "Shark");
			names.put(PRAYER_4, "Prayer potion(4)");
			names.put(PRAYER_2, "Prayer potion(2)");
		}

		@Override
		public String name(int id)
		{
			return names.getOrDefault(id, "item " + id);
		}

		@Override
		public long price(int id)
		{
			return 0;
		}
	};

	private static Map<Integer, Integer> items(int... ids)
	{
		Map<Integer, Integer> out = new HashMap<>();
		for (int id : ids)
		{
			out.put(id, 1);
		}
		return out;
	}

	private static GearChecks.Line line(List<GearChecks.Line> lines, String label)
	{
		for (GearChecks.Line l : lines)
		{
			if (l.req.label.equals(label))
			{
				return l;
			}
		}
		throw new AssertionError("no line " + label);
	}

	private static List<GearChecks.Line> revs(Map<Integer, Integer> inv, Map<Integer, Integer> worn,
		List<GroupSearch.Source> sources)
	{
		return GearChecks.check(GearChecks.byId("revs"), inv, worn, sources, "Me", ITEMS);
	}

	@Test
	public void wornWhenWornIsOkAndInInventoryIsEquip()
	{
		List<GearChecks.Line> worn = revs(items(), items(SALVE_EI), Collections.emptyList());
		assertEquals(GearChecks.State.OK, line(worn, "Salve amulet").state);
		List<GearChecks.Line> bag = revs(items(SALVE_EI), items(), Collections.emptyList());
		assertEquals(GearChecks.State.EQUIP, line(bag, "Salve amulet").state);
		assertFalse(line(bag, "Salve amulet").requestable());
	}

	@Test
	public void notCarriedIsMissingAndTeammatesAreListed()
	{
		List<GearChecks.Line> lines = revs(items(), items(), Arrays.asList(
			new GroupSearch.Source("Alex", "bank", items(SALVE_EI), 5),
			new GroupSearch.Source("Me", "bank", items(SALVE_E), 5)));
		GearChecks.Line salve = line(lines, "Salve amulet");
		assertEquals(GearChecks.State.MISSING, salve.state);
		assertEquals(Collections.singletonList("Alex (bank)"), salve.holders);
		assertEquals("Salve amulet(ei)", salve.itemName);
		assertTrue(salve.requestable());
		assertFalse(salve.nobody());
	}

	@Test
	public void nobodyHavingItIsFlaggedForShopping()
	{
		GearChecks.Line salve = line(revs(items(), items(), Collections.emptyList()), "Salve amulet");
		assertTrue(salve.nobody());
		assertEquals("Salve amulet(ei)", salve.itemName);
	}

	@Test
	public void ownBankDoesNotCountAsATeammate()
	{
		GearChecks.Line salve = line(revs(items(), items(), Collections.singletonList(
			new GroupSearch.Source("me", "bank", items(SALVE_EI), 5))), "Salve amulet");
		assertTrue(salve.nobody());
	}

	@Test
	public void dosesCountButNamesDoNotMatchLoosely()
	{
		assertTrue(GearChecks.matches("Prayer potion(2)", "Prayer potion"));
		assertFalse(GearChecks.matches("Prayer potion(2)", "Prayer potion(4)"));
		assertFalse(GearChecks.matches("Prayer potion flask", "Prayer potion"));
		assertTrue(GearChecks.matches("SALVE AMULET(EI)", "Salve amulet(ei)"));
		Map<Integer, Integer> inv = new HashMap<>();
		inv.put(PRAYER_2, 1);
		inv.put(PRAYER_4, 1);
		inv.put(SHARK, 6);
		List<GearChecks.Line> lines = revs(inv, items(), Collections.emptyList());
		assertEquals(GearChecks.State.OK, line(lines, "Prayer restore").state);
		assertEquals(GearChecks.State.OK, line(lines, "Food").state);
	}

	@Test
	public void fewerThanTheCountIsShortAndListsTheDifference()
	{
		List<GearChecks.Line> lines = revs(items(PRAYER_2), items(), Collections.emptyList());
		GearChecks.Line prayer = line(lines, "Prayer restore");
		assertEquals(GearChecks.State.SHORT, prayer.state);
		assertEquals(1, prayer.have);
		assertEquals(1, prayer.short_());
		assertTrue(prayer.nobody());
	}

	@Test
	public void carriedItemCanBeInventoryOrWorn()
	{
		List<GearChecks.Line> lines = revs(items(), items(SHARK), Collections.emptyList());
		assertEquals(1, line(lines, "Food").have);
		assertEquals(GearChecks.State.SHORT, line(lines, "Food").state);
	}

	@Test
	public void zeroQuantityDoesNotCount()
	{
		Map<Integer, Integer> inv = new HashMap<>();
		inv.put(SHARK, 0);
		assertEquals(GearChecks.State.MISSING, line(revs(inv, items(), Collections.emptyList()), "Food").state);
	}

	@Test
	public void problemsCountsEveryRequiredLineNotOk()
	{
		List<GearChecks.Line> lines = revs(items(SALVE_EI), items(), Collections.emptyList());
		int missing = 0;
		int optional = 0;
		for (GearChecks.Line l : lines)
		{
			optional += l.req.optional ? 1 : 0;
			missing += l.state == GearChecks.State.OK || l.req.optional ? 0 : 1;
		}
		assertTrue(optional > 0);
		assertEquals(missing, GearChecks.problems(lines));
		assertEquals(lines.size() - optional, GearChecks.problems(lines));
	}

	// ---- Any-of kinds, sub-fight skips, the shopping plan ----

	private static final int WHIP = 10;
	private static final int STAFF = 11;
	private static final int CROSSBOW = 12;
	private static final int SPADE = 13;
	private static final int AMULET = 14;

	private static final ItemLookup GEAR = new ItemLookup()
	{
		private final Map<Integer, String> names = new HashMap<>();

		{
			names.put(WHIP, "Abyssal whip");
			names.put(STAFF, "Staff of fire");
			names.put(CROSSBOW, "Rune crossbow");
			names.put(SPADE, "Spade");
			names.put(AMULET, "Amulet of strength");
			names.put(SHARK, "Shark");
		}

		@Override
		public String name(int id)
		{
			return names.getOrDefault(id, "item " + id);
		}

		@Override
		public long price(int id)
		{
			return 0;
		}

		@Override
		public int slot(int id)
		{
			return id == WHIP || id == STAFF || id == CROSSBOW ? GearChecks.WEAPON_SLOT : id == AMULET ? 2 : -1;
		}

		@Override
		public int combatStyles(int id)
		{
			switch (id)
			{
				case WHIP:
					return ItemStyles.MELEE | ItemStyles.COMBAT;
				case STAFF:
					return ItemStyles.MELEE | ItemStyles.MAGIC | ItemStyles.COMBAT;
				case CROSSBOW:
					return ItemStyles.RANGED | ItemStyles.COMBAT;
				case AMULET:
					return ItemStyles.MELEE | ItemStyles.COMBAT;
				default:
					return 0;
			}
		}
	};

	private static List<GearChecks.Line> barrows(Set<String> skipped, Map<Integer, Integer> inv,
		Map<Integer, Integer> worn, List<GroupSearch.Source> sources)
	{
		return GearChecks.check(GearChecks.byId("barrows"), skipped, inv, worn, sources, "Me", GEAR);
	}

	@Test
	public void anyOfKindMatchesByStatsNotName()
	{
		assertTrue(GearChecks.Kind.RANGED_WEAPON.accepts(GearChecks.WEAPON_SLOT, ItemStyles.RANGED));
		assertFalse(GearChecks.Kind.RANGED_WEAPON.accepts(2, ItemStyles.RANGED));
		assertTrue(GearChecks.Kind.NON_MAGIC_WEAPON.accepts(GearChecks.WEAPON_SLOT, ItemStyles.MELEE));
		assertFalse(GearChecks.Kind.NON_MAGIC_WEAPON.accepts(GearChecks.WEAPON_SLOT,
			ItemStyles.MELEE | ItemStyles.MAGIC));
		assertFalse(GearChecks.Kind.ANY_WEAPON.accepts(GearChecks.WEAPON_SLOT, 0));
		assertFalse(GearChecks.Kind.NONE.accepts(GearChecks.WEAPON_SLOT, ItemStyles.MELEE));

		// A staff, named nowhere in the table, is a weapon of any style but not "not magic" for Ahrim.
		List<GearChecks.Line> staff = barrows(Collections.emptySet(), items(), items(STAFF), Collections.emptyList());
		assertEquals(GearChecks.State.OK, line(staff, "Weapon (any style)").state);
		assertEquals(GearChecks.State.MISSING, line(staff, "Not magic, for Ahrim").state);
		// A crossbow in the bag satisfies Ahrim's line; an amulet with melee stats is no weapon.
		List<GearChecks.Line> both = barrows(Collections.emptySet(), items(CROSSBOW, AMULET), items(STAFF),
			Collections.emptyList());
		assertEquals(GearChecks.State.OK, line(both, "Not magic, for Ahrim").state);
		List<GearChecks.Line> amulet = barrows(Collections.emptySet(), items(), items(AMULET), Collections.emptyList());
		assertEquals(GearChecks.State.MISSING, line(amulet, "Weapon (any style)").state);
	}

	@Test
	public void skippingAFightRemovesItsLine()
	{
		List<GearChecks.Line> all = barrows(Collections.emptySet(), items(), items(), Collections.emptyList());
		line(all, "Not magic, for Ahrim");
		List<GearChecks.Line> skipped = barrows(Collections.singleton("ahrim"), items(), items(),
			Collections.emptyList());
		assertEquals(all.size() - 1, skipped.size());
		for (GearChecks.Line l : skipped)
		{
			assertFalse(l.req.label, l.req.label.contains("Ahrim"));
		}
		assertEquals(Collections.singletonList("Ahrim"), GearChecks.byId("barrows").fights());
	}

	@Test
	public void aKindOnlyLineRequestsWhatTheTeammateActuallyHolds()
	{
		GearChecks.Rule rule = new GearChecks.Rule("custom-1", "Mine", new int[0], Collections.singletonList(
			new GearChecks.Req("Any melee weapon", Collections.emptyList(), GearChecks.Need.CARRIED, 1,
				GearChecks.Kind.MELEE_WEAPON, false, "")), true);
		GearChecks.Line nobody = GearChecks.check(rule, items(), items(), Collections.emptyList(), "Me", GEAR).get(0);
		assertEquals("", nobody.itemName);
		assertEquals(Collections.singletonList("Any melee weapon"),
			GearChecks.plan(Collections.singletonList(nobody)).unnamed);
		GearChecks.Line held = GearChecks.check(rule, items(), items(), Collections.singletonList(
			new GroupSearch.Source("Alex", "bank", items(WHIP), 5)), "Me", GEAR).get(0);
		assertTrue(held.requestable());
		assertEquals("Abyssal whip", held.itemName);
		assertEquals(WHIP, held.itemId);
	}

	@Test
	public void shoppingPlanRequestsFirstSkipsOptionalAndNeverListsTwice()
	{
		GearChecks.Rule rule = new GearChecks.Rule("custom-1", "Mine", new int[0], Arrays.asList(
			new GearChecks.Req("Food", Arrays.asList("Shark"), GearChecks.Need.CARRIED, 6, GearChecks.Kind.NONE,
				false, ""),
			new GearChecks.Req("More food", Arrays.asList("shark"), GearChecks.Need.CARRIED, 10,
				GearChecks.Kind.NONE, false, ""),
			new GearChecks.Req("Spade", GearChecks.Need.CARRIED, "Spade"),
			new GearChecks.Req("Teleport", GearChecks.Need.CARRIED, "Barrows teleport").optional(true),
			new GearChecks.Req("Spade again", GearChecks.Need.CARRIED, "Spade")), true);
		Map<Integer, Integer> inv = new HashMap<>();
		inv.put(SHARK, 2);
		List<GearChecks.Line> lines = GearChecks.check(rule, inv, items(), Collections.singletonList(
			new GroupSearch.Source("Alex", "bank", items(SPADE), 5)), "Me", GEAR);
		GearChecks.Plan plan = GearChecks.plan(lines);
		assertEquals(1, plan.request.size());
		assertEquals("Spade", plan.request.get(0).name);
		assertEquals(1, plan.request.get(0).quantity);
		assertEquals(1, plan.shop.size());
		assertEquals("Shark", plan.shop.get(0).name);
		assertEquals(8, plan.shop.get(0).quantity);
		assertTrue(plan.unnamed.isEmpty());
	}

	@Test
	public void anItemBothRequestableAndNotIsOnlyRequested()
	{
		GearChecks.Line shop = new GearChecks.Line(new GearChecks.Req("A", GearChecks.Need.CARRIED, "Shark").count(5),
			GearChecks.State.MISSING, 0, Collections.emptyList(), "Shark", -1);
		GearChecks.Line ask = new GearChecks.Line(new GearChecks.Req("B", GearChecks.Need.CARRIED, "Shark").count(3),
			GearChecks.State.MISSING, 0, Collections.singletonList("Alex (bank)"), "Shark", -1);
		for (List<GearChecks.Line> order : Arrays.asList(Arrays.asList(shop, ask), Arrays.asList(ask, shop)))
		{
			GearChecks.Plan plan = GearChecks.plan(order);
			assertTrue(plan.shop.isEmpty());
			assertEquals(1, plan.request.size());
			assertEquals(5, plan.request.get(0).quantity);
		}
	}

	@Test
	public void optionalLinesAreNeverProblems()
	{
		GearChecks.Rule rule = new GearChecks.Rule("custom-1", "Mine", new int[0], Collections.singletonList(
			new GearChecks.Req("Teleport", GearChecks.Need.CARRIED, "Barrows teleport").optional(true)), true);
		List<GearChecks.Line> lines = GearChecks.check(rule, items(), items(), Collections.emptyList(), "Me", GEAR);
		assertEquals(GearChecks.State.MISSING, lines.get(0).state);
		assertEquals(0, GearChecks.problems(lines));
		assertTrue(GearChecks.plan(lines).empty());
	}

	@Test
	public void regionEntryPicksTheRule()
	{
		assertEquals("vorkath", GearChecks.forRegion(9023).id);
		assertEquals("zulrah", GearChecks.forRegion(9007).id);
		assertEquals("revs", GearChecks.forRegion(12702).id);
		assertNull(GearChecks.forRegion(12850));
	}

	@Test
	public void ruleIdsAreUniqueAndRegionsDoNotOverlap()
	{
		List<String> ids = new ArrayList<>();
		Map<Integer, String> seen = new HashMap<>();
		for (GearChecks.Rule r : GearChecks.RULES)
		{
			assertFalse(r.id, ids.contains(r.id));
			ids.add(r.id);
			assertFalse(r.reqs.isEmpty());
			for (int region : r.regions)
			{
				assertNull("region " + region + " in " + r.id + " and " + seen.get(region), seen.put(region, r.id));
			}
			assertNotNull(GearChecks.byId(r.id));
		}
	}

	/** A stand-in for the tradeable item search: substring hits, in the order given. */
	private static java.util.function.Function<String, List<ShoppingTab.Hit>> search(ShoppingTab.Hit... all)
	{
		return q ->
		{
			List<ShoppingTab.Hit> out = new ArrayList<>();
			for (ShoppingTab.Hit h : all)
			{
				if (h.name.toLowerCase().contains(q.toLowerCase()))
				{
					out.add(h);
				}
			}
			return out;
		};
	}

	@Test
	public void resolveWantsTakesTheFirstExactOrDoseMatchAndMergesQuantities()
	{
		java.util.function.Function<String, List<ShoppingTab.Hit>> search = search(
			new ShoppingTab.Hit(10, "Super restore mix(2)"), new ShoppingTab.Hit(11, "Prayer potion(4)"),
			new ShoppingTab.Hit(12, "Prayer potion(3)"), new ShoppingTab.Hit(20, "Shark"),
			new ShoppingTab.Hit(21, "Sharks fin"));
		List<String> notFound = new ArrayList<>();
		Map<Integer, Integer> ids = GearChecks.resolveWants(Arrays.asList(new GearChecks.Want("Prayer potion", 2),
			new GearChecks.Want("Shark", 6), new GearChecks.Want("prayer potion", 4),
			new GearChecks.Want("Dragonfire shield", 1)), search, Collections.emptyMap(), notFound);
		// Prayer potion(4) is the first hit that is the name with a dose; "Super restore mix" never matches.
		assertEquals(Integer.valueOf(4), ids.get(11));
		assertEquals(Integer.valueOf(6), ids.get(20));
		assertEquals(2, ids.size());
		assertEquals(Collections.singletonList("Dragonfire shield"), notFound);
	}

	@Test
	public void resolveWantsFindsAnUntradeableAMemberHolds()
	{
		Map<Integer, String> known = new HashMap<>();
		known.put(SALVE_EI + 100, "Salve amulet(ei)");
		known.put(SALVE_EI, "Salve amulet(ei)");
		known.put(SHARK, "Shark");
		List<String> notFound = new ArrayList<>();
		Map<Integer, Integer> ids = GearChecks.resolveWants(Arrays.asList(new GearChecks.Want("Salve amulet(ei)", 1),
			new GearChecks.Want("Fire cape", 1)), search(), known, notFound);
		assertEquals(Collections.singletonMap(SALVE_EI, 1), ids);
		assertEquals(Collections.singletonList("Fire cape"), notFound);
		// The tradeable search wins when it knows the item.
		assertEquals(Integer.valueOf(99), GearChecks.resolveId("Shark", search(new ShoppingTab.Hit(99, "Shark")), known));
		// Names match whole, or with a variant suffix: "Salve amulet" is the (ei), "Salve" is nothing.
		assertEquals(Integer.valueOf(SALVE_EI), GearChecks.resolveId("Salve amulet", search(), known));
		assertNull(GearChecks.resolveId("Salve", search(), known));
	}

	@Test
	public void makeShoppingListTwiceAddsNothingTwice()
	{
		Map<Integer, Integer> want = new HashMap<>();
		want.put(SHARK, 6);
		want.put(PRAYER_4, 4);
		want.put(SALVE_EI, 1);
		Map<Integer, Integer> listed = new HashMap<>();
		listed.put(SHARK, 8);
		listed.put(PRAYER_4, 2);
		assertEquals(1, GearChecks.dropListed(want, listed));
		// Shark is listed in full; prayer potions only 2 of 4, so they stay; salve was never listed.
		assertFalse(want.containsKey(SHARK));
		assertEquals(Integer.valueOf(4), want.get(PRAYER_4));
		assertEquals(Integer.valueOf(1), want.get(SALVE_EI));
		listed.putAll(want);
		assertEquals(2, GearChecks.dropListed(want, listed));
		assertTrue(want.isEmpty());
	}
}
