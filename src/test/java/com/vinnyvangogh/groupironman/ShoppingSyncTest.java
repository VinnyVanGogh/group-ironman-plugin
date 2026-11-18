package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Lists reach a second client over the party, and removals and renames follow them. */
public class ShoppingSyncTest
{
	private final Gson gson = new Gson();

	/** One simulated client ({@link ShoppingClient}), named. */
	private final class Client
	{
		final ShoppingClient c;
		final String name;
		final List<String> sent;
		final ShoppingSync sync;

		Client(String name)
		{
			this.c = new ShoppingClient(gson, name);
			this.name = name;
			this.sent = c.sent;
			this.sync = c.sync;
		}

		void tick(Client... to)
		{
			ShoppingClient[] out = new ShoppingClient[to.length];
			for (int i = 0; i < to.length; i++)
			{
				out[i] = to[i].c;
			}
			c.tick(out);
		}
	}

	private static Map<Integer, Integer> ropes()
	{
		Map<Integer, Integer> items = new TreeMap<>();
		items.put(954, 2);
		return items;
	}

	private ShoppingList custom(String by, long at)
	{
		return ShoppingList.create("Ropes", "", Collections.emptyList(), Arrays.asList("Alice", "Bob"), ropes(), by, at);
	}

	@Test
	public void aListReachesASecondClient()
	{
		Client alice = new Client("Alice");
		Client bob = new Client("Bob");
		assertTrue(alice.sync.add(custom("Alice", 1000)));
		alice.tick(bob);

		List<ShoppingList> lists = bob.sync.active();
		assertEquals(1, lists.size());
		assertEquals("Ropes", lists.get(0).name);
		assertEquals(Integer.valueOf(2), lists.get(0).items.get(954));
		assertEquals("Alice added 'Ropes' for Alice, Bob", bob.sync.history().get(0).text);
	}

	@Test
	public void renameAndRemovalFollowTheList()
	{
		Client alice = new Client("Alice");
		Client bob = new Client("Bob");
		ShoppingList list = custom("Alice", 1000);
		alice.sync.add(list);
		alice.tick(bob);

		assertTrue(bob.sync.rename(list.id, "Ropes for Lost City", "Bob", 2000));
		bob.tick(alice);
		assertEquals("Ropes for Lost City", alice.sync.active().get(0).name);
		assertEquals("Bob renamed 'Ropes' to 'Ropes for Lost City'", alice.sync.history().get(0).text);

		assertTrue(alice.sync.remove(list.id, "Alice", 3000));
		alice.tick(bob);
		assertTrue(bob.sync.active().isEmpty());
		assertEquals("Alice removed 'Ropes for Lost City'", bob.sync.history().get(0).text);
	}

	@Test
	public void aStaleCopyNeverBringsARemovedListBack()
	{
		Client alice = new Client("Alice");
		Client bob = new Client("Bob");
		ShoppingList list = custom("Alice", 1000);
		alice.sync.add(list);
		alice.tick(bob);
		alice.sync.remove(list.id, "Alice", 2000);
		alice.tick();

		// Bob was offline for the removal and resends his older copy when Alice joins.
		bob.sync.resendAll();
		bob.tick(alice);
		assertTrue(alice.sync.active().isEmpty());

		// And Alice's resend to him carries the removal.
		alice.sync.resendAll();
		alice.tick(bob);
		assertTrue(bob.sync.active().isEmpty());
	}

	@Test
	public void aJoiningMemberGetsEveryList()
	{
		Client alice = new Client("Alice");
		Client cara = new Client("Cara");
		alice.sync.add(custom("Alice", 1000));
		alice.sync.add(custom("Alice", 1001));
		alice.tick();

		alice.sync.resendAll();
		alice.tick(cara);
		assertEquals(2, cara.sync.active().size());
	}

	@Test
	public void presetsCannotBeRenamed()
	{
		Client alice = new Client("Alice");
		ShoppingList preset = ShoppingList.create("Mithril gloves", "mithril-gloves", Collections.emptyList(),
			Collections.singletonList("Alice"), Collections.emptyMap(), "Alice", 1000);
		alice.sync.add(preset);
		assertFalse(alice.sync.rename(preset.id, "Gloves", "Alice", 2000));
	}

	@Test
	public void questProgressGoesOnlyWhileAListIsForThatMember()
	{
		Client alice = new Client("Alice");
		Client bob = new Client("Bob");
		alice.sync.ownProgress("Alice", QuestProgress.of(Collections.singleton("Cook's Assistant"),
			Collections.emptySet()));
		alice.sync.flush("Alice");
		assertTrue("a fresh install with no list for this member sends nothing", alice.sent.isEmpty());

		alice.sync.add(ShoppingList.create("Mithril gloves", "mithril-gloves", Collections.emptyList(),
			Collections.singletonList("Alice"), Collections.emptyMap(), "Alice", 1000));
		alice.tick(bob);
		assertTrue(bob.sync.progress("Alice").known());
		assertTrue(bob.sync.progress("alice").finished("Cook's Assistant"));
		assertFalse(bob.sync.progress("Cara").known());
	}

	@Test
	public void theCacheKeepsListsHistoryAndProgress()
	{
		Client alice = new Client("Alice");
		ShoppingList list = custom("Alice", 1000);
		alice.sync.add(list);
		alice.sync.ownProgress("Alice", QuestProgress.of(Collections.singleton("Lost City"), Collections.emptySet()));
		JsonObject cache = alice.sync.toCache();
		String file = GroupCache.write(gson, new GroupRoster(), null, cache);

		Client again = new Client("Alice");
		again.sync.fromCache(GroupCache.shop(gson, file), 2000);
		assertEquals(list.id, again.sync.active().get(0).id);
		assertEquals(1, again.sync.history().size());
		assertTrue(again.sync.progress("Alice").finished("Lost City"));
	}

	@Test
	public void aCacheFromBeforeShoppingHasNoEntry()
	{
		assertNull(GroupCache.shop(gson, GroupCache.write(gson, new GroupRoster())));
	}

	@Test
	public void removedListsAreForgottenAfterAMonth()
	{
		Client alice = new Client("Alice");
		ShoppingList list = custom("Alice", 1000);
		alice.sync.add(list);
		alice.sync.remove(list.id, "Alice", 2000);
		JsonObject cache = alice.sync.toCache();

		Client later = new Client("Alice");
		later.sync.fromCache(cache, 2000 + ShoppingSync.TOMBSTONE_MS + 1);
		assertNull(later.sync.list(list.id));
	}
}
