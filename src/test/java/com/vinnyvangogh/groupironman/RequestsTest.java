package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
 * Requests: each member's own named lists the group fills. They travel on the
 * Shopping tab's sync ({@link ShoppingSync}), are edited only by their owner,
 * claimed by anyone, and close when the group holds what was asked.
 */
public class RequestsTest
{
	private static final int SHARK = 385;
	private static final int PRAYER = 2434;
	private final Gson gson = new Gson();

	/** One simulated client, as in {@code ShoppingSyncTest}. */
	private final class Client extends ShoppingClient
	{
		Client(String name)
		{
			super(gson, name);
		}
	}

	private static Map<Integer, Integer> fireCape()
	{
		Map<Integer, Integer> items = new TreeMap<>();
		items.put(SHARK, 500);
		items.put(PRAYER, 20);
		return items;
	}

	private static ShopAllocation.Holdings holdings(String member, int id, long qty, long inStorage)
	{
		Map<String, Map<Integer, Long>> members = new LinkedHashMap<>();
		Map<Integer, Long> m = new TreeMap<>();
		m.put(id, qty);
		members.put(member, m);
		Map<Integer, Long> storage = new TreeMap<>();
		storage.put(id, inStorage);
		return new ShopAllocation.Holdings(members, storage);
	}

	private static ShopAllocation.Holdings nothing()
	{
		return new ShopAllocation.Holdings(Collections.emptyMap(), Collections.emptyMap());
	}

	private static RequestBoard.Row row(RequestBoard.Card c, int id)
	{
		for (RequestBoard.Row r : c.rows)
		{
			if (r.itemId == id)
			{
				return r;
			}
		}
		throw new AssertionError("no row for " + id);
	}

	// ---- Named and unnamed lists ----

	@Test
	public void aNamedRequestKeepsItsName()
	{
		RequestList r = RequestList.create("Fire cape", fireCape(), "Alice", 1000);
		assertEquals("Fire cape", r.name);
		assertEquals("Alice", r.owner);
		assertEquals(Integer.valueOf(500), r.items.get(SHARK));
		assertFalse(r.closed);
	}

	@Test
	public void anUnnamedRequestIsNamedForItsOwner()
	{
		assertEquals("Alice's requests", RequestList.create("  ", fireCape(), "Alice", 1000).name);
		assertEquals("Alice's requests", RequestList.create(null, fireCape(), "Alice", 1000).name);
	}

	@Test
	public void eachMemberHasTheirOwnListsAndTheyReachTheGroup()
	{
		Client alice = new Client("Alice");
		Client bob = new Client("Bob");
		assertTrue(alice.sync.addRequest(RequestList.create("Fire cape", fireCape(), "Alice", 1000)));
		assertTrue(bob.sync.addRequest(RequestList.create("", Collections.singletonMap(SHARK, 100), "Bob", 1100)));
		alice.tick(bob);
		bob.tick(alice);

		for (Client c : new Client[]{alice, bob})
		{
			List<RequestList> live = c.sync.requests();
			assertEquals(2, live.size());
			assertEquals("Fire cape", live.get(0).name);
			assertEquals("Alice", live.get(0).owner);
			assertEquals("Bob's requests", live.get(1).name);
			assertEquals("Bob", live.get(1).owner);
		}
		assertEquals("Bob asked for 'Bob's requests'", alice.sync.history().get(0).text);
	}

	@Test
	public void aRequestWithNoItemsIsRefused()
	{
		Client alice = new Client("Alice");
		assertFalse(alice.sync.addRequest(RequestList.create("Empty", Collections.emptyMap(), "Alice", 1000)));
		assertTrue(alice.sync.requests().isEmpty());
	}

	// ---- Counting ----

	@Test
	public void countsFollowWhatTheGroupHolds()
	{
		RequestList r = RequestList.create("Fire cape", fireCape(), "Alice", 1000);
		RequestBoard.Card before = RequestBoard.card(r, Collections.emptyList(), nothing(), id -> "item " + id);
		assertEquals(0, row(before, SHARK).have);
		assertEquals(500, row(before, SHARK).wanted);
		assertFalse(row(before, SHARK).done());

		// Bob's bank and inventory (one source total) plus group storage, canonical ids as the shopping list counts.
		RequestBoard.Card after = RequestBoard.card(r, Collections.emptyList(), holdings("Bob", SHARK, 300, 150),
			id -> "item " + id);
		assertEquals(450, row(after, SHARK).have);
		assertFalse(row(after, SHARK).done());
		assertEquals(0, after.done());
	}

	@Test
	public void anItemClosesByItselfOnceTheGroupHoldsTheAmount()
	{
		RequestList r = RequestList.create("Fire cape", fireCape(), "Alice", 1000);
		RequestBoard.Card c = RequestBoard.card(r, Collections.emptyList(), holdings("Bob", SHARK, 400, 100),
			id -> "item " + id);
		assertTrue(row(c, SHARK).done());
		assertFalse(row(c, PRAYER).done());
		assertFalse("one item still open", c.done() == c.rows.size());
		assertEquals(Collections.singleton(SHARK), RequestBoard.reached(c));
	}

	@Test
	public void theOwnerSealsReachedItemsAndTheListClosesWhenAllAreDone()
	{
		Client alice = new Client("Alice");
		RequestList r = RequestList.create("Fire cape", Collections.singletonMap(SHARK, 500), "Alice", 1000);
		alice.sync.addRequest(r);
		RequestBoard.Card c = RequestBoard.card(alice.sync.request(r.id), Collections.emptyList(),
			holdings("Bob", SHARK, 500, 0), id -> "Shark");
		assertTrue(c.allDone());

		RequestList sealed = alice.sync.closeItems(r.id, RequestBoard.reached(c), "Alice", 2000);
		assertNotNull(sealed);
		assertTrue("every item done closes the whole list", sealed.closed);
		assertTrue(sealed.closedItems.contains(SHARK));

		// Sealed: it stays done when the sharks are eaten.
		RequestBoard.Card later = RequestBoard.card(alice.sync.request(r.id), Collections.emptyList(), nothing(),
			id -> "Shark");
		assertTrue(row(later, SHARK).done());
		assertTrue(later.allDone());
	}

	@Test
	public void theOwnerCanCloseAnItemTheGroupDoesNotHold()
	{
		Client alice = new Client("Alice");
		RequestList r = RequestList.create("Fire cape", fireCape(), "Alice", 1000);
		alice.sync.addRequest(r);
		RequestList next = alice.sync.closeItems(r.id, Collections.singleton(PRAYER), "Alice", 2000);
		assertNotNull(next);
		assertFalse(next.closed);
		RequestBoard.Card c = RequestBoard.card(next, Collections.emptyList(), nothing(), id -> "x");
		assertTrue(row(c, PRAYER).done());
		assertFalse(row(c, SHARK).done());
	}

	// ---- Claims ----

	@Test
	public void anyoneCanClaimAndUnclaim()
	{
		Client alice = new Client("Alice");
		Client bob = new Client("Bob");
		RequestList r = RequestList.create("Fire cape", fireCape(), "Alice", 1000);
		alice.sync.addRequest(r);
		alice.tick(bob);

		assertTrue(bob.sync.claim(r.id, SHARK, "Bob", true, 2000));
		bob.tick(alice);
		RequestBoard.Card c = RequestBoard.card(alice.sync.request(r.id), alice.sync.claims(r.id), nothing(),
			id -> "Shark");
		assertEquals(Collections.singletonList("Bob"), row(c, SHARK).claimedBy);
		assertTrue(row(c, PRAYER).claimedBy.isEmpty());

		assertTrue(bob.sync.claim(r.id, SHARK, "Bob", false, 3000));
		bob.tick(alice);
		c = RequestBoard.card(alice.sync.request(r.id), alice.sync.claims(r.id), nothing(), id -> "Shark");
		assertTrue(row(c, SHARK).claimedBy.isEmpty());
	}

	@Test
	public void twoMembersCanClaimTheSameItemWithoutLosingEitherClaim()
	{
		Client alice = new Client("Alice");
		Client bob = new Client("Bob");
		Client cat = new Client("Cat");
		RequestList r = RequestList.create("Fire cape", fireCape(), "Alice", 1000);
		alice.sync.addRequest(r);
		alice.tick(bob, cat);
		bob.sync.claim(r.id, SHARK, "Bob", true, 2000);
		cat.sync.claim(r.id, SHARK, "Cat", true, 2000);
		bob.tick(alice, cat);
		cat.tick(alice, bob);
		for (Client c : new Client[]{alice, bob, cat})
		{
			RequestBoard.Card card = RequestBoard.card(c.sync.request(r.id), c.sync.claims(r.id), nothing(), id -> "x");
			assertEquals(c.name, 2, row(card, SHARK).claimedBy.size());
		}
	}

	@Test
	public void anOlderClaimNeverUndoesANewerOne()
	{
		Client alice = new Client("Alice");
		RequestList r = RequestList.create("Fire cape", fireCape(), "Alice", 1000);
		alice.sync.addRequest(r);
		assertTrue(alice.sync.receive("Bob", ShoppingCodec.encode(gson, new RequestClaim(r.id, SHARK, "Bob", false,
			3000))));
		alice.sync.receive("Bob", ShoppingCodec.encode(gson, new RequestClaim(r.id, SHARK, "Bob", true, 2000)));
		assertTrue(alice.sync.claims(r.id).isEmpty() || !alice.sync.claims(r.id).get(0).claimed);
	}

	@Test
	public void claimingAClosedOrMissingRequestIsRefused()
	{
		Client alice = new Client("Alice");
		assertFalse(alice.sync.claim("nosuch", SHARK, "Alice", true, 1000));
		RequestList r = RequestList.create("Fire cape", Collections.singletonMap(SHARK, 1), "Alice", 1000);
		alice.sync.addRequest(r);
		assertFalse("not an item on the list", alice.sync.claim(r.id, PRAYER, "Alice", true, 1500));
		alice.sync.closeRequest(r.id, "Alice", 2000);
		assertFalse(alice.sync.claim(r.id, SHARK, "Alice", true, 3000));
	}

	// ---- Owner-only edits ----

	@Test
	public void onlyTheOwnerEditsTheirList()
	{
		Client alice = new Client("Alice");
		Client bob = new Client("Bob");
		RequestList r = RequestList.create("Fire cape", fireCape(), "Alice", 1000);
		alice.sync.addRequest(r);
		alice.tick(bob);

		assertFalse(bob.sync.renameRequest(r.id, "Bob's now", "Bob", 2000));
		assertFalse(bob.sync.removeRequest(r.id, "Bob", 2000));
		assertFalse(bob.sync.closeRequest(r.id, "Bob", 2000));
		assertNull(bob.sync.closeItems(r.id, Collections.singleton(SHARK), "Bob", 2000));
		assertEquals("Fire cape", bob.sync.request(r.id).name);

		assertTrue(alice.sync.renameRequest(r.id, "Inferno cape", "Alice", 2000));
		alice.tick(bob);
		assertEquals("Inferno cape", bob.sync.request(r.id).name);
		assertTrue(alice.sync.removeRequest(r.id, "Alice", 3000));
		alice.tick(bob);
		assertTrue(bob.sync.requests().isEmpty());
	}

	@Test
	public void aRevisionNamingAnotherOwnerIsIgnored()
	{
		Client alice = new Client("Alice");
		RequestList r = RequestList.create("Fire cape", fireCape(), "Alice", 1000);
		alice.sync.addRequest(r);
		// A forged revision: same id, higher rev, a different owner.
		RequestList forged = new RequestList(r.id, "Mallory", "Mine now", r.items, Collections.emptySet(), false,
			false, 5, 9000, r.created, RequestList.RENAMED);
		alice.sync.receive("Mallory", ShoppingCodec.encode(gson, forged));
		assertEquals("Fire cape", alice.sync.request(r.id).name);
		assertEquals("Alice", alice.sync.request(r.id).owner);
	}

	@Test
	public void ownerNamesMatchTheWayTheRosterDoes()
	{
		Client alice = new Client("Iron Alice");
		RequestList r = RequestList.create("Fire cape", fireCape(), "Iron Alice", 1000);
		alice.sync.addRequest(r);
		assertTrue(alice.sync.renameRequest(r.id, "Cape", "Iron_Alice", 2000));
	}

	// ---- The wire and older clients ----

	@Test
	public void requestsAndClaimsRoundTrip()
	{
		RequestList r = RequestList.create("Fire cape", fireCape(), "Alice", 1000)
			.withClosedItems(Collections.singleton(PRAYER), 2000);
		ShoppingCodec.Message m = ShoppingCodec.decode(gson, ShoppingCodec.encode(gson, r));
		assertNotNull(m);
		assertNotNull(m.request);
		assertEquals(r.id, m.request.id);
		assertEquals("Alice", m.request.owner);
		assertEquals(r.items, m.request.items);
		assertEquals(Collections.singleton(PRAYER), m.request.closedItems);
		assertEquals(2, m.request.rev);

		RequestClaim claim = new RequestClaim(r.id, SHARK, "Bob", true, 3000);
		ShoppingCodec.Message cm = ShoppingCodec.decode(gson, ShoppingCodec.encode(gson, claim));
		assertNotNull(cm.claim);
		assertEquals("Bob", cm.claim.member);
		assertEquals(SHARK, cm.claim.itemId);
		assertTrue(cm.claim.claimed);
	}

	@Test
	public void olderClientsIgnoreRequestsSafely()
	{
		RequestList r = RequestList.create("Fire cape", fireCape(), "Alice", 1000);
		for (String payload : new String[]{ShoppingCodec.encode(gson, r),
			ShoppingCodec.encode(gson, new RequestClaim(r.id, SHARK, "Bob", true, 2000))})
		{
			// gim-test-3 and older: no Shopping tab at all.
			assertNull(GroupCodec.decode(gson, payload));
			assertNull(GroupCodec.posted(gson, payload));
			assertFalse(GovernanceSync.isGovernance(payload));
			assertNull(GovernanceCodec.decode(gson, payload));
			// The first Shopping tab build looked for these two kinds only, then dropped anything else.
			assertFalse(payload.contains("\"k\":\"shop\"") || payload.contains("\"k\":\"qstate\""));
			assertTrue(ShoppingCodec.isShopping(payload));
		}
	}

	@Test
	public void anOlderShoppingListStillReadsAndRequestsNeverBecomeLists()
	{
		Client alice = new Client("Alice");
		ShoppingList old = ShoppingList.create("Ropes", "", Collections.emptyList(), Collections.singletonList("Alice"),
			Collections.singletonMap(954, 2), "Bob", 1000);
		assertTrue(alice.sync.receive("Bob", ShoppingCodec.encode(gson, old)));
		alice.sync.receive("Bob", ShoppingCodec.encode(gson, RequestList.create("Cape", fireCape(), "Bob", 1000)));
		assertEquals(1, alice.sync.active().size());
		assertEquals(1, alice.sync.requests().size());
	}

	@Test
	public void badRequestPayloadsAreDropped()
	{
		assertNull(ShoppingCodec.decode(gson, "{\"v\":1,\"k\":\"req\",\"id\":\"BAD ID\",\"o\":\"A\",\"r\":1,\"ts\":1}"));
		assertNull(ShoppingCodec.decode(gson, "{\"v\":1,\"k\":\"req\",\"id\":\"abc\",\"o\":\"\",\"r\":1,\"ts\":1}"));
		assertNull(ShoppingCodec.decode(gson, "{\"v\":1,\"k\":\"rqc\",\"id\":\"abc\",\"i\":0,\"m\":\"Bob\",\"ts\":1}"));
		assertNull(ShoppingCodec.decode(gson, "{\"v\":1,\"k\":\"rqc\",\"id\":\"abc\",\"i\":\"x\",\"m\":\"Bob\"}"));
	}

	@Test
	public void aJoiningMemberGetsRequestsAndClaims()
	{
		Client alice = new Client("Alice");
		Client bob = new Client("Bob");
		RequestList r = RequestList.create("Fire cape", fireCape(), "Alice", 1000);
		alice.sync.addRequest(r);
		alice.sync.claim(r.id, SHARK, "Alice", true, 1500);
		alice.sync.flush("Alice");
		alice.sent.clear();

		alice.sync.resendAll();
		alice.tick(bob);
		assertEquals(1, bob.sync.requests().size());
		assertEquals(1, bob.sync.claims(r.id).size());
	}

	@Test
	public void theCacheKeepsRequestsAndClaims()
	{
		Client alice = new Client("Alice");
		RequestList r = RequestList.create("Fire cape", fireCape(), "Alice", 1000);
		alice.sync.addRequest(r);
		alice.sync.claim(r.id, SHARK, "Bob", true, 1500);
		JsonObject cache = alice.sync.toCache();

		Client again = new Client("Alice");
		again.sync.fromCache(cache, 2000);
		assertEquals("Fire cape", again.sync.request(r.id).name);
		assertEquals(1, again.sync.claims(r.id).size());
	}

	// ---- The panel's words ----

	@Test
	public void theCardSaysWhatIsLeftAndWhoIsGettingIt()
	{
		RequestList r = RequestList.create("Fire cape", fireCape(), "Alice", 1000);
		List<RequestClaim> claims = new ArrayList<>();
		claims.add(new RequestClaim(r.id, SHARK, "Bob", true, 2000));
		claims.add(new RequestClaim(r.id, SHARK, "Cat", true, 2001));
		RequestBoard.Card c = RequestBoard.card(r, claims, holdings("Bob", SHARK, 300, 150),
			id -> id == SHARK ? "Shark" : "Prayer potion(4)");
		assertEquals("Shark: want 500, group has 450, 50 to go", RequestCards.rowText(row(c, SHARK)));
		assertEquals("Bob and Cat will get it", RequestCards.claimText(row(c, SHARK)));
		assertEquals("", RequestCards.claimText(row(c, PRAYER)));
		assertTrue(RequestCards.claimedBy(row(c, SHARK), "cat"));
		assertEquals("Asked by Alice, " + BoostText.ago(1000, 1000) + ". 0 of 2 done.", RequestCards.byline(c, 1000));
	}

	// ---- Discord ----

	@Test
	public void theDiscordPostIsOffByDefault()
	{
		GroupIronmanConfig defaults = new GroupIronmanConfig()
		{
		};
		assertFalse(defaults.discordRequest());
		assertFalse(defaults.overlayRequest());
		assertEquals(Webhook.MAIN, Webhook.of(GroupEvent.Type.REQUEST));
	}

	@Test
	public void eachRequestEventReadsAsASentence()
	{
		RequestList r = RequestList.create("Fire cape", fireCape(), "Alice", 1000);
		GroupEvent made = RequestBoard.made(r, id -> id == SHARK ? "Shark" : "Prayer potion(4)", 1000);
		assertEquals("Alice", made.member);
		assertTrue(made.isValid());
		assertEquals("made a request, 'Fire cape': 500 Shark, 20 Prayer potion(4)", EventText.describe(made, null));

		GroupEvent claimed = RequestBoard.claimed(r, SHARK, "Shark", "Bob", 2000);
		assertEquals("Bob", claimed.member);
		assertEquals("will get Shark for Alice's 'Fire cape'", EventText.describe(claimed, null));

		GroupEvent done = RequestBoard.done(r, 3000);
		assertEquals("Alice", done.member);
		assertEquals("'Fire cape' is done: the group has it all", EventText.describe(done, null));
	}
}
