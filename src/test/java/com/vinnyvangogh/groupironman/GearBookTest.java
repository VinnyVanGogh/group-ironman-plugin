package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The Gear tab's saved lists: edits survive a restart, a blank or broken key is the starting table. */
public class GearBookTest
{
	private static final Gson GSON = new Gson();

	/** What a restart does: write the key, read it back into a fresh book. */
	private static GearBook restart(GearBook book)
	{
		return GearBook.parse(GSON, book.toJson(GSON));
	}

	@Test
	public void oldConfigWithoutTheKeyIsTheStartingTable()
	{
		for (String json : new String[] {null, "", "  ", "not json {", "[1,2]", "{\"edits\":5}", "{\"v\":99}"})
		{
			GearBook book = GearBook.parse(GSON, json);
			assertTrue(String.valueOf(json), book.isEmpty());
			List<GearChecks.Rule> rules = book.rules();
			assertEquals(GearChecks.RULES.size(), rules.size());
			for (int i = 0; i < rules.size(); i++)
			{
				assertSame(GearChecks.RULES.get(i), rules.get(i));
			}
			assertEquals("vorkath", book.forRegion(9023).id);
		}
	}

	@Test
	public void anEditedRulePersistsAcrossRestartAndResets()
	{
		GearBook book = GearBook.empty();
		GearChecks.Rule vork = book.byId("vorkath");
		List<GearChecks.Req> next = new ArrayList<>(vork.reqs);
		next.add(new GearChecks.Req("Rune pouch", GearChecks.Need.CARRIED, "Rune pouch").count(2).optional(true));
		next.set(0, next.get(0).need(GearChecks.Need.WORN));
		assertTrue(book.put(vork.withReqs(next)));

		GearBook again = restart(book);
		assertTrue(again.edited("vorkath"));
		GearChecks.Rule read = again.byId("vorkath");
		assertEquals(vork.title, read.title);
		assertEquals(next.size(), read.reqs.size());
		GearChecks.Req pouch = read.reqs.get(read.reqs.size() - 1);
		assertEquals("Rune pouch", pouch.label);
		assertEquals(2, pouch.count);
		assertTrue(pouch.optional);
		assertEquals(GearChecks.Need.WORN, read.reqs.get(0).need);
		assertEquals("vorkath", again.forRegion(9023).id);
		assertEquals(next.size(), again.forRegion(9023).reqs.size());

		again.reset("vorkath");
		assertFalse(again.edited("vorkath"));
		assertSame(GearChecks.byId("vorkath"), again.byId("vorkath"));
		assertTrue(restart(again).isEmpty());
	}

	@Test
	public void kindsAndFightsSurviveARestart()
	{
		GearBook book = restart(GearBook.empty());
		GearChecks.Rule barrows = book.byId("barrows");
		assertTrue(book.put(barrows.withReqs(new ArrayList<>(barrows.reqs))));
		GearChecks.Rule read = restart(book).byId("barrows");
		for (int i = 0; i < barrows.reqs.size(); i++)
		{
			GearChecks.Req a = barrows.reqs.get(i);
			GearChecks.Req b = read.reqs.get(i);
			assertEquals(a.label, b.label);
			assertEquals(a.names, b.names);
			assertEquals(a.kind, b.kind);
			assertEquals(a.need, b.need);
			assertEquals(a.count, b.count);
			assertEquals(a.optional, b.optional);
			assertEquals(a.fight, b.fight);
		}
	}

	@Test
	public void skipsPersistAndAreCaseInsensitive()
	{
		GearBook book = GearBook.empty();
		book.skip("barrows", "Ahrim", true);
		GearBook again = restart(book);
		assertEquals(Collections.singleton("Ahrim"), again.skipped("barrows"));
		again.skip("barrows", "AHRIM", false);
		assertTrue(again.skipped("barrows").isEmpty());
		assertTrue(again.isEmpty());
	}

	@Test
	public void ownPlacesPersistAndTheirRegionComesFirst()
	{
		GearBook book = GearBook.empty();
		GearChecks.Rule mine = book.addCustom("  My Vorkath  ", 9023);
		assertNotNull(mine);
		assertTrue(mine.id.startsWith(GearBook.CUSTOM_PREFIX));
		assertTrue(book.put(mine.withReqs(Collections.singletonList(new GearChecks.Req("Any ranged weapon",
			Collections.emptyList(), GearChecks.Need.WORN, 1, GearChecks.Kind.RANGED_WEAPON, false, "")))));
		GearChecks.Rule hand = book.addCustom("", 0);
		assertEquals("My list", hand.title);
		assertEquals(0, hand.regions.length);
		assertFalse(mine.id.equals(hand.id));

		GearBook again = restart(book);
		GearChecks.Rule read = again.forRegion(9023);
		assertEquals(mine.id, read.id);
		assertEquals("My Vorkath", read.title);
		assertTrue(read.custom);
		assertEquals(GearChecks.Kind.RANGED_WEAPON, read.reqs.get(0).kind);
		assertEquals(GearChecks.RULES.size() + 2, again.rules().size());

		assertTrue(again.remove(mine.id));
		assertEquals("vorkath", again.forRegion(9023).id);
		assertFalse(again.remove("vorkath"));
		assertNotNull(again.byId("vorkath"));
	}

	@Test
	public void handEditedJsonIsCleanedNotTrusted()
	{
		String json = "{\"edits\":{\"nope\":{\"lines\":[{\"label\":\"x\",\"names\":[\"y\"]}]},"
			+ "\"zulrah\":{\"title\":\"Renamed\",\"regions\":[1],\"lines\":[{\"label\":\"Food\",\"names\":[\"Shark\"],"
			+ "\"count\":99999,\"need\":\"sideways\",\"kind\":\"laser\"},{\"label\":\"\",\"names\":[\"Shark\"]},"
			+ "{\"label\":\"Empty\",\"names\":[]}]}},"
			+ "\"custom\":[{\"id\":\"vorkath\",\"title\":\"Sneaky\"},{\"id\":\"custom-7\",\"title\":\"Mine\"},"
			+ "{\"id\":\"custom-7\",\"title\":\"Twice\"}]}";
		GearBook book = GearBook.parse(GSON, json);
		assertNull(book.byId("nope"));
		GearChecks.Rule zulrah = book.byId("zulrah");
		assertEquals("Zulrah", zulrah.title);
		assertEquals(9007, zulrah.regions[0]);
		assertEquals(1, zulrah.reqs.size());
		GearChecks.Req food = zulrah.reqs.get(0);
		assertEquals(GearChecks.MAX_COUNT, food.count);
		assertEquals(GearChecks.Need.CARRIED, food.need);
		assertEquals(GearChecks.Kind.NONE, food.kind);
		assertEquals(GearChecks.RULES.size() + 1, book.rules().size());
		assertEquals("Mine", book.byId("custom-7").title);
		assertFalse(book.byId("vorkath").custom);
	}
}
