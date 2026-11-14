package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The two Shopping kinds on the wire, and that they leave older clients alone: a gim-test-3 build reads every
 * payload through {@link GroupCodec} and then {@link GovernanceCodec}, and both must drop these as unknown.
 */
public class ShoppingCodecTest
{
	private final Gson gson = new Gson();

	private ShoppingList list()
	{
		Map<Integer, Integer> items = new TreeMap<>();
		items.put(954, 2);
		items.put(1925, 1);
		return ShoppingList.create("Ropes & buckets", "", Collections.emptyList(), Arrays.asList("Alice", "Bob"),
			items, "Alice", 1_700_000_000_000L);
	}

	@Test
	public void aListRoundTrips()
	{
		ShoppingList l = list().removedBy("Bob", 1_700_000_000_500L);
		ShoppingList back = ShoppingCodec.decode(gson, ShoppingCodec.encode(gson, l)).list;
		assertEquals(l.id, back.id);
		assertEquals(l.name, back.name);
		assertEquals(l.members, back.members);
		assertEquals(l.items, back.items);
		assertEquals(2, back.rev);
		assertEquals("Bob", back.by);
		assertEquals(ShoppingList.REMOVED, back.action);
		assertTrue(back.removed);
		assertEquals("Alice", back.createdBy);
	}

	@Test
	public void aPresetListCarriesItsPicks()
	{
		ShoppingList l = ShoppingList.create("Mithril gloves", "mithril-gloves", Arrays.asList("a", "b"),
			Collections.singletonList("Alice"), Collections.emptyMap(), "Alice", 5);
		ShoppingList back = ShoppingCodec.decode(gson, ShoppingCodec.encode(gson, l)).list;
		assertEquals("mithril-gloves", back.preset);
		assertEquals(Arrays.asList("a", "b"), back.picks);
		assertFalse(back.custom());
	}

	@Test
	public void questProgressRoundTrips()
	{
		QuestProgress p = QuestProgress.of(Arrays.asList("Cook's Assistant", "Lost City"),
			Collections.singletonList("Waterfall Quest"));
		QuestProgress back = ShoppingCodec.decode(gson, ShoppingCodec.encode(gson, p)).quests;
		assertEquals(p, back);
		assertTrue(back.started("Cook's Assistant"));
		assertTrue(back.started("Waterfall Quest"));
		assertFalse(back.finished("Waterfall Quest"));
	}

	@Test
	public void olderClientsIgnoreBothKinds()
	{
		for (String payload : Arrays.asList(ShoppingCodec.encode(gson, list()),
			ShoppingCodec.encode(gson, QuestProgress.of(Collections.singleton("Lost City"), Collections.emptySet()))))
		{
			assertNull("GroupCodec drops an unknown kind", GroupCodec.decode(gson, payload));
			assertFalse(GovernanceSync.isGovernance(payload));
			assertNull("GovernanceCodec drops an unknown kind", GovernanceCodec.decode(gson, payload));
			assertTrue(ShoppingCodec.isShopping(payload));
		}
	}

	@Test
	public void otherKindsAndBadPayloadsAreNotShopping()
	{
		String stats = GroupCodec.encodeStats(gson, new MemberStats(Collections.emptyMap(), 0, 0, 0, 0, 0, 0, 0));
		assertFalse(ShoppingCodec.isShopping(stats));
		assertNull(ShoppingCodec.decode(gson, stats));
		assertNull(ShoppingCodec.decode(gson, "{\"v\":1,\"k\":\"shop2\"}"));
		assertNull(ShoppingCodec.decode(gson, "{\"v\":1,\"k\":\"shop\",\"id\":\"BAD ID\",\"r\":1,\"ts\":1}"));
		assertNull(ShoppingCodec.decode(gson, "{\"v\":1,\"k\":\"shop\",\"id\":\"abc\",\"r\":0,\"ts\":1}"));
		assertNull(ShoppingCodec.decode(gson, "not json"));
	}

	@Test
	public void everyFieldIsCapped()
	{
		StringBuilder longName = new StringBuilder();
		for (int i = 0; i < 100; i++)
		{
			longName.append('x');
		}
		ShoppingList l = ShoppingList.create(longName.toString(), "", Collections.emptyList(),
			Collections.nCopies(1, "Alice"), Collections.emptyMap(), "Alice", 1);
		assertEquals(ShoppingList.MAX_NAME, l.name.length());
		assertTrue(ShoppingCodec.encode(gson, l).length() < GroupCodec.MAX_PAYLOAD_CHARS);
	}
}
