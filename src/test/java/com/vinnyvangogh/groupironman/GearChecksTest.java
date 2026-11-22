package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
		List<GearChecks.Line> lines = revs(items(PRAYER_2, SHARK), items(), Collections.emptyList());
		assertEquals(GearChecks.State.OK, line(lines, "Prayer restore").state);
		assertEquals(GearChecks.State.OK, line(lines, "Food").state);
	}

	@Test
	public void carriedItemCanBeInventoryOrWorn()
	{
		List<GearChecks.Line> lines = revs(items(), items(SHARK), Collections.emptyList());
		assertEquals(GearChecks.State.OK, line(lines, "Food").state);
	}

	@Test
	public void zeroQuantityDoesNotCount()
	{
		Map<Integer, Integer> inv = new HashMap<>();
		inv.put(SHARK, 0);
		assertEquals(GearChecks.State.MISSING, line(revs(inv, items(), Collections.emptyList()), "Food").state);
	}

	@Test
	public void problemsCountsEveryLineNotOk()
	{
		List<GearChecks.Line> lines = revs(items(SALVE_EI), items(), Collections.emptyList());
		int missing = 0;
		for (GearChecks.Line l : lines)
		{
			missing += l.state == GearChecks.State.OK ? 0 : 1;
		}
		assertEquals(missing, GearChecks.problems(lines));
		assertEquals(lines.size(), GearChecks.problems(lines));
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
}
