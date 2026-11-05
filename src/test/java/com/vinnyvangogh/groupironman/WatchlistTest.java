package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Personal and group watchlists: what a member adds, what another member's
 * list turns into (a suggestion, never an addition), who has each item, and
 * the two plain events part A will route ("nobody has it", "someone got it").
 */
public class WatchlistTest
{
	private static final Gson GSON = new Gson();
	private static final List<GroupSearch.Source> NOTHING_SEEN = Collections.emptyList();

	private static Map<Integer, Integer> items(int... pairs)
	{
		Map<Integer, Integer> out = new TreeMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			out.put(pairs[i], pairs[i + 1]);
		}
		return out;
	}

	private static GroupSearch.Source bank(String member, int... pairs)
	{
		return new GroupSearch.Source(member, "bank", items(pairs), 1_000L);
	}

	private static GroupSearch.Source inventory(String member, int... pairs)
	{
		return new GroupSearch.Source(member, "inventory", items(pairs), -1);
	}

	private static GroupSearch.Source storage(int... pairs)
	{
		return new GroupSearch.Source("Group", "storage", items(pairs), 2_000L);
	}

	private static List<Integer> ids(List<Watchlist.Entry> entries)
	{
		List<Integer> out = new ArrayList<>();
		for (Watchlist.Entry e : entries)
		{
			out.add(e.itemId);
		}
		return out;
	}

	private static Watchlist.Entry entry(int id, int qty, String by, long at)
	{
		return new Watchlist.Entry(id, qty, by, at);
	}

	// Adding and removing.

	@Test
	public void addAndRemovePersonal()
	{
		Watchlist w = new Watchlist();
		assertTrue(w.addPersonal(FakeItems.SHARK, 100, 10, NOTHING_SEEN));
		assertTrue(w.addPersonal(FakeItems.WHIP, 1, 11, NOTHING_SEEN));
		assertEquals(Arrays.asList(FakeItems.SHARK, FakeItems.WHIP), ids(w.personal()));
		assertEquals(100, w.personal().get(0).quantity);
		assertTrue(w.group().isEmpty());

		assertTrue(w.removePersonal(FakeItems.SHARK));
		assertFalse(w.removePersonal(FakeItems.SHARK));
		assertEquals(Collections.singletonList(FakeItems.WHIP), ids(w.personal()));
	}

	@Test
	public void addingAgainChangesTheQuantityOnly()
	{
		Watchlist w = new Watchlist();
		w.addGroup(FakeItems.SHARK, 100, "Alex", 10, NOTHING_SEEN);
		assertFalse("same quantity is no change", w.addGroup(FakeItems.SHARK, 100, "Alex", 20, NOTHING_SEEN));
		assertTrue(w.addGroup(FakeItems.SHARK, 50, "Alex", 30, NOTHING_SEEN));
		Watchlist.Entry e = w.group().get(0);
		assertEquals(50, e.quantity);
		assertEquals("first add's time is kept, so members do not see it as new", 10, e.at);
	}

	@Test
	public void badItemsAndQuantitiesAreRefused()
	{
		Watchlist w = new Watchlist();
		assertFalse(w.addPersonal(0, 1, 10, NOTHING_SEEN));
		assertFalse(w.addPersonal(-5, 1, 10, NOTHING_SEEN));
		assertFalse(w.addPersonal(ItemCounts.MAX_ITEM_ID, 1, 10, NOTHING_SEEN));
		assertTrue("quantity under 1 means 1", w.addPersonal(FakeItems.SHARK, 0, 10, NOTHING_SEEN));
		assertEquals(1, w.personal().get(0).quantity);
	}

	@Test
	public void eachListIsCapped()
	{
		Watchlist w = new Watchlist();
		for (int i = 1; i <= Watchlist.MAX_ITEMS; i++)
		{
			assertTrue(w.addPersonal(i, 1, i, NOTHING_SEEN));
		}
		assertFalse(w.addPersonal(Watchlist.MAX_ITEMS + 1, 1, 0, NOTHING_SEEN));
		assertEquals(Watchlist.MAX_ITEMS, w.personal().size());
	}

	// Another member's list: suggested, never added.

	@Test
	public void anotherMembersItemArrivesAsSuggestedNotAdded()
	{
		Watchlist w = new Watchlist();
		assertTrue(w.receive("Sam", Collections.singletonList(entry(FakeItems.WHIP, 1, "Sam", 50)), 100));
		assertTrue("never added on its own", w.group().isEmpty());
		assertEquals(Collections.singletonList(FakeItems.WHIP), ids(w.suggested()));
		Watchlist.Entry s = w.suggested().get(0);
		assertEquals("Sam", s.by);
		assertEquals(50, s.at);
	}

	@Test
	public void anEmptyAdderIsTheSender()
	{
		Watchlist w = new Watchlist();
		w.receive("Sam", Collections.singletonList(entry(FakeItems.WHIP, 1, "", 50)), 100);
		assertEquals("Sam", w.suggested().get(0).by);
	}

	@Test
	public void aTimeFromTheFutureIsHeldToNow()
	{
		Watchlist w = new Watchlist();
		w.receive("Sam", Collections.singletonList(entry(FakeItems.WHIP, 1, "Sam", 9_999)), 100);
		assertEquals(100, w.suggested().get(0).at);
	}

	@Test
	public void anItemAlreadyOnMyGroupListIsNotSuggested()
	{
		Watchlist w = new Watchlist();
		w.addGroup(FakeItems.WHIP, 1, "Alex", 10, NOTHING_SEEN);
		assertFalse(w.receive("Sam", Collections.singletonList(entry(FakeItems.WHIP, 1, "Sam", 50)), 100));
		assertTrue(w.suggested().isEmpty());
	}

	@Test
	public void acceptMovesASuggestionToTheGroupListKeepingWhoAddedIt()
	{
		Watchlist w = new Watchlist();
		w.receive("Sam", Collections.singletonList(entry(FakeItems.WHIP, 2, "Sam", 50)), 100);
		assertTrue(w.accept(FakeItems.WHIP, NOTHING_SEEN));
		assertTrue(w.suggested().isEmpty());
		Watchlist.Entry e = w.group().get(0);
		assertEquals(FakeItems.WHIP, e.itemId);
		assertEquals(2, e.quantity);
		assertEquals("Sam", e.by);
		assertEquals(50, e.at);
		assertFalse("nothing left to accept", w.accept(FakeItems.WHIP, NOTHING_SEEN));
	}

	@Test
	public void dismissDropsItAndTheSameSuggestionDoesNotComeBack()
	{
		Watchlist w = new Watchlist();
		List<Watchlist.Entry> sams = Collections.singletonList(entry(FakeItems.WHIP, 1, "Sam", 50));
		w.receive("Sam", sams, 100);
		assertTrue(w.dismiss(FakeItems.WHIP));
		assertTrue(w.suggested().isEmpty());
		assertTrue(w.group().isEmpty());

		// Sam resends the same list (a rejoin), and Kim, who accepted Sam's, sends it too.
		assertFalse(w.receive("Sam", sams, 200));
		assertFalse(w.receive("Kim", Collections.singletonList(entry(FakeItems.WHIP, 1, "Sam", 50)), 200));
		assertTrue(w.suggested().isEmpty());

		// Someone adds it again later: that is a new suggestion.
		assertTrue(w.receive("Kim", Collections.singletonList(entry(FakeItems.WHIP, 1, "Kim", 300)), 400));
		assertEquals(Collections.singletonList(FakeItems.WHIP), ids(w.suggested()));
	}

	@Test
	public void acceptAllTakesEverySuggestion()
	{
		Watchlist w = new Watchlist();
		w.receive("Sam", Arrays.asList(entry(FakeItems.WHIP, 1, "Sam", 50), entry(FakeItems.SHARK, 200, "Sam", 51)), 100);
		w.receive("Kim", Collections.singletonList(entry(FakeItems.COAL, 1000, "Kim", 52)), 100);
		assertEquals(3, w.acceptAll(NOTHING_SEEN));
		assertTrue(w.suggested().isEmpty());
		assertEquals(Arrays.asList(FakeItems.WHIP, FakeItems.SHARK, FakeItems.COAL), ids(w.group()));
		assertEquals(0, w.acceptAll(NOTHING_SEEN));
	}

	@Test
	public void aSuggestionGoesWhenEveryMemberWhoListedItDropsIt()
	{
		Watchlist w = new Watchlist();
		w.receive("Sam", Collections.singletonList(entry(FakeItems.WHIP, 1, "Sam", 50)), 100);
		w.receive("Kim", Collections.singletonList(entry(FakeItems.WHIP, 1, "Sam", 50)), 100);
		w.receive("Sam", Collections.emptyList(), 200);
		assertEquals("Kim still lists it", 1, w.suggested().size());
		assertTrue(w.receive("Kim", Collections.emptyList(), 200));
		assertTrue(w.suggested().isEmpty());
	}

	@Test
	public void removingFromMyGroupListDoesNotBounceBackAsASuggestion()
	{
		Watchlist w = new Watchlist();
		w.addGroup(FakeItems.WHIP, 1, "Alex", 10, NOTHING_SEEN);
		assertTrue(w.removeGroup(FakeItems.WHIP));
		// Sam accepted it earlier and still has it.
		assertFalse(w.receive("Sam", Collections.singletonList(entry(FakeItems.WHIP, 1, "Alex", 10)), 100));
		assertTrue(w.suggested().isEmpty());
	}

	@Test
	public void addingASuggestedItemByHandAcceptsIt()
	{
		Watchlist w = new Watchlist();
		w.receive("Sam", Collections.singletonList(entry(FakeItems.WHIP, 1, "Sam", 50)), 100);
		w.addGroup(FakeItems.WHIP, 3, "Alex", 500, NOTHING_SEEN);
		assertTrue(w.suggested().isEmpty());
		Watchlist.Entry e = w.group().get(0);
		assertEquals(3, e.quantity);
		assertEquals("keeps Sam's add, so it does not look new to anyone", "Sam", e.by);
		assertEquals(50, e.at);
	}

	@Test
	public void suggestionsAreCappedLikeTheLists()
	{
		Watchlist w = new Watchlist();
		List<Watchlist.Entry> lots = new ArrayList<>();
		for (int i = 1; i <= Watchlist.MAX_ITEMS + 20; i++)
		{
			lots.add(entry(i, 1, "Sam", i));
		}
		w.receive("Sam", lots, 1_000);
		assertEquals(Watchlist.MAX_ITEMS, w.suggested().size());
	}

	@Test
	public void personalItemsAreNeverPartOfWhatIsShared()
	{
		Watchlist w = new Watchlist();
		w.addPersonal(FakeItems.SHARK, 1, 10, NOTHING_SEEN);
		w.addGroup(FakeItems.WHIP, 1, "Alex", 10, NOTHING_SEEN);
		assertEquals(Collections.singletonList(FakeItems.WHIP), ids(w.shared()));
	}

	// Who has it.

	@Test
	public void holdersListEveryPlaceWithTheItem()
	{
		List<GroupSearch.Source> seen = Arrays.asList(
			bank("Alex", FakeItems.SHARK, 120),
			inventory("Sam", FakeItems.SHARK, 5, FakeItems.COAL, 1),
			storage(FakeItems.COAL, 300));
		List<Watchlist.Holder> holders = Watchlist.holders(FakeItems.SHARK, seen);
		assertEquals(2, holders.size());
		assertEquals("Alex", holders.get(0).member);
		assertEquals("bank", holders.get(0).where);
		assertEquals(120, holders.get(0).quantity);
		assertEquals("Sam", holders.get(1).member);
		assertEquals(125, Watchlist.total(FakeItems.SHARK, seen));
		assertTrue(Watchlist.holders(FakeItems.WHIP, seen).isEmpty());
	}

	// Event 19: an important item nobody in the group has.

	@Test
	public void missingLooksAcrossBanksCarriedAndStorage()
	{
		Watchlist w = new Watchlist();
		w.addGroup(FakeItems.SHARK, 1, "Alex", 1, NOTHING_SEEN);
		w.addGroup(FakeItems.COAL, 1, "Alex", 2, NOTHING_SEEN);
		w.addGroup(FakeItems.TROUT, 1, "Alex", 3, NOTHING_SEEN);
		w.addPersonal(FakeItems.WHIP, 1, 4, NOTHING_SEEN);
		w.addPersonal(FakeItems.RUNE_SCIM, 1, 5, NOTHING_SEEN);
		List<GroupSearch.Source> seen = Arrays.asList(
			bank("Alex", FakeItems.SHARK, 1),
			inventory("Sam", FakeItems.RUNE_SCIM, 1),
			storage(FakeItems.COAL, 300));
		assertEquals(Arrays.asList(FakeItems.TROUT, FakeItems.WHIP), ids(w.missingFromGroup(seen)));
	}

	@Test
	public void nothingIsMissingBeforeAnythingHasBeenSeen()
	{
		Watchlist w = new Watchlist();
		w.addGroup(FakeItems.SHARK, 1, "Alex", 1, NOTHING_SEEN);
		assertTrue("no data is not 'nobody has it'", w.missingFromGroup(NOTHING_SEEN).isEmpty());
	}

	@Test
	public void anItemOnBothListsIsMissingOnce()
	{
		Watchlist w = new Watchlist();
		w.addGroup(FakeItems.SHARK, 1, "Alex", 1, NOTHING_SEEN);
		w.addPersonal(FakeItems.SHARK, 1, 1, NOTHING_SEEN);
		assertEquals(1, w.missingFromGroup(Collections.singletonList(bank("Alex", FakeItems.COAL, 1))).size());
	}

	// Event 22: someone got a watchlist item.

	@Test
	public void gotFiresOnceWhenEnoughTurnsUp()
	{
		Watchlist w = new Watchlist();
		w.addGroup(FakeItems.SHARK, 100, "Alex", 1, NOTHING_SEEN);
		assertTrue(w.checkGot(Collections.singletonList(bank("Sam", FakeItems.SHARK, 40))).isEmpty());

		List<GroupSearch.Source> enough = Arrays.asList(bank("Sam", FakeItems.SHARK, 40),
			inventory("Kim", FakeItems.SHARK, 60));
		List<Watchlist.Got> got = w.checkGot(enough);
		assertEquals(1, got.size());
		assertEquals(FakeItems.SHARK, got.get(0).entry.itemId);
		assertEquals(2, got.get(0).holders.size());
		assertTrue(w.isGot(FakeItems.SHARK));

		assertTrue("fires once", w.checkGot(enough).isEmpty());
		assertTrue("and not again after it dips and comes back",
			w.checkGot(NOTHING_SEEN).isEmpty() && w.checkGot(enough).isEmpty());
	}

	@Test
	public void addingAnItemSomeoneAlreadyHasDoesNotFire()
	{
		Watchlist w = new Watchlist();
		List<GroupSearch.Source> seen = Collections.singletonList(bank("Sam", FakeItems.WHIP, 1));
		w.addPersonal(FakeItems.WHIP, 1, 1, seen);
		w.receive("Sam", Collections.singletonList(entry(FakeItems.SHARK, 1, "Sam", 5)), 10);
		w.acceptAll(Collections.singletonList(bank("Sam", FakeItems.SHARK, 1)));
		assertTrue(w.checkGot(Arrays.asList(bank("Sam", FakeItems.WHIP, 1, FakeItems.SHARK, 1))).isEmpty());
	}

	@Test
	public void removingAndAddingAgainCanFireAgain()
	{
		Watchlist w = new Watchlist();
		w.addPersonal(FakeItems.WHIP, 1, 1, NOTHING_SEEN);
		List<GroupSearch.Source> seen = Collections.singletonList(bank("Sam", FakeItems.WHIP, 1));
		assertEquals(1, w.checkGot(seen).size());
		w.removePersonal(FakeItems.WHIP);
		assertFalse(w.isGot(FakeItems.WHIP));
		w.addPersonal(FakeItems.WHIP, 1, 2, NOTHING_SEEN);
		assertEquals(1, w.checkGot(seen).size());
	}

	@Test
	public void gotNeedsTheLargerQuantityWhenOnBothLists()
	{
		Watchlist w = new Watchlist();
		w.addPersonal(FakeItems.SHARK, 10, 1, NOTHING_SEEN);
		w.addGroup(FakeItems.SHARK, 100, "Alex", 1, NOTHING_SEEN);
		assertTrue(w.checkGot(Collections.singletonList(bank("Sam", FakeItems.SHARK, 50))).isEmpty());
		assertEquals(1, w.checkGot(Collections.singletonList(bank("Sam", FakeItems.SHARK, 100))).size());
	}

	// Persistence.

	@Test
	public void everythingSurvivesARestart()
	{
		Watchlist w = new Watchlist();
		w.addPersonal(FakeItems.SHARK, 100, 10, NOTHING_SEEN);
		w.addGroup(FakeItems.WHIP, 1, "Alex", 20, NOTHING_SEEN);
		w.receive("Sam", Arrays.asList(entry(FakeItems.COAL, 500, "Sam", 30), entry(FakeItems.TROUT, 1, "Sam", 31)), 100);
		w.receive("Kim", Collections.singletonList(entry(FakeItems.COAL, 500, "Sam", 30)), 100);
		w.dismiss(FakeItems.TROUT);
		w.checkGot(Collections.singletonList(bank("Kim", FakeItems.WHIP, 1)));

		Watchlist back = new Watchlist();
		assertTrue(back.load(GSON, w.toJson(GSON)));
		assertEquals(ids(w.personal()), ids(back.personal()));
		assertEquals(100, back.personal().get(0).quantity);
		assertEquals(ids(w.group()), ids(back.group()));
		assertEquals("Alex", back.group().get(0).by);
		assertEquals(20, back.group().get(0).at);
		assertEquals(Collections.singletonList(FakeItems.COAL), ids(back.suggested()));
		assertTrue("got is remembered, so it does not fire again after a restart", back.isGot(FakeItems.WHIP));
		assertTrue(back.checkGot(Collections.singletonList(bank("Kim", FakeItems.WHIP, 1))).isEmpty());

		// Dismissed stays dismissed.
		assertFalse(back.receive("Sam", Collections.singletonList(entry(FakeItems.TROUT, 1, "Sam", 31)), 200));
		// Who listed a suggestion is remembered: it goes only when both drop it.
		back.receive("Sam", Collections.emptyList(), 200);
		assertEquals(1, back.suggested().size());
		back.receive("Kim", Collections.emptyList(), 200);
		assertTrue(back.suggested().isEmpty());
	}

	@Test
	public void unreadableSavedTextLeavesTheListAlone()
	{
		Watchlist w = new Watchlist();
		w.addPersonal(FakeItems.SHARK, 1, 1, NOTHING_SEEN);
		assertFalse(w.load(GSON, "not json"));
		assertFalse(w.load(GSON, ""));
		assertFalse(w.load(GSON, null));
		assertFalse(w.load(GSON, "{\"v\":0}"));
		assertEquals(1, w.personal().size());
	}

	@Test
	public void badSavedEntriesAreSkippedOneByOne()
	{
		Watchlist w = new Watchlist();
		assertTrue(w.load(GSON, "{\"v\":1,\"p\":[[385,5,1,\"\"],\"x\",[0,1,1,\"\"],[453],[4151,-3,2,\"Al\"]]}"));
		assertEquals(Arrays.asList(385, 4151), ids(w.personal()));
		assertEquals("a bad quantity reads as 1", 1, w.personal().get(1).quantity);
	}

	// The add-item search.

	@Test
	public void searchMatchesLikeFindAndPutsShortNamesFirst()
	{
		Map<Integer, String> names = new HashMap<>();
		names.put(1, "Shark lure");
		names.put(2, "Shark");
		names.put(3, "Prayer potion(4)");
		names.put(4, "Coal");
		assertEquals(Arrays.asList(2, 1), Watchlist.search("shark", names, 10));
		assertEquals("aliases work as in ::find", Collections.singletonList(3), Watchlist.search("ppot", names, 10));
		assertEquals(Collections.singletonList(2), Watchlist.search("shark", names, 1));
		assertTrue("too short", Watchlist.search("s", names, 10).isEmpty());
	}

	// The wire.

	@Test
	public void theSharedListRoundTripsOverTheParty()
	{
		List<Watchlist.Entry> list = Arrays.asList(entry(FakeItems.WHIP, 1, "Alex", 20), entry(FakeItems.SHARK, 300, "Sam", 30));
		String payload = GroupOutbox.watch(GSON, true, list);
		assertNotNull(payload);
		GroupCodec.Decoded d = GroupCodec.decode(GSON, payload);
		assertNotNull(d);
		assertEquals(GroupCodec.Kind.WATCH, d.kind);
		assertEquals(ids(list), ids(d.watch));
		assertEquals(300, d.watch.get(1).quantity);
		assertEquals("Sam", d.watch.get(1).by);
		assertEquals(30, d.watch.get(1).at);
	}

	@Test
	public void nothingIsSentWithTheSwitchOff()
	{
		assertNull(GroupOutbox.watch(GSON, false,
			Collections.singletonList(entry(FakeItems.WHIP, 1, "Alex", 20))));
	}

	@Test
	public void anEmptyListIsStillSentSoMembersDropOldSuggestions()
	{
		String payload = GroupOutbox.watch(GSON, true, Collections.emptyList());
		assertNotNull(payload);
		assertTrue(GroupCodec.decode(GSON, payload).watch.isEmpty());
	}

	@Test
	public void theFullestListFitsOneMessage()
	{
		List<Watchlist.Entry> list = new ArrayList<>();
		StringBuilder longName = new StringBuilder();
		for (int i = 0; i < Watchlist.MAX_NAME; i++)
		{
			longName.append(' ');
		}
		for (int i = 0; i < Watchlist.MAX_ITEMS; i++)
		{
			list.add(entry(ItemCounts.MAX_ITEM_ID - 1 - i, Integer.MAX_VALUE, longName.toString(), Long.MAX_VALUE / 2));
		}
		String payload = GroupOutbox.watch(GSON, true, list);
		assertTrue(payload.length() + " chars", payload.length() <= GroupCodec.MAX_PAYLOAD_CHARS);
		assertFalse("cut to fit, not dropped", GroupCodec.decode(GSON, payload).watch.isEmpty());
	}

	@Test
	public void badWireEntriesAreSkipped()
	{
		GroupCodec.Decoded d = GroupCodec.decode(GSON,
			"{\"v\":1,\"k\":\"watch\",\"w\":[[4151,1,5,\"Sam\"],[0,1,1,\"x\"],\"junk\",[385,2,-1,\"x\"],[453,2,3,7]]}");
		assertNotNull(d);
		assertEquals(Arrays.asList(4151, 453), ids(d.watch));
		assertEquals("a non-text adder reads as unknown", "", d.watch.get(1).by);
	}
}
