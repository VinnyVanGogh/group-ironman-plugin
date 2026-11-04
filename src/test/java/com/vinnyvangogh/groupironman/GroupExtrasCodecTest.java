package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static com.vinnyvangogh.groupironman.FakeItems.*;

/**
 * The part 3 payloads: rune pouch, quiver and seed vault ("extras"), the
 * drops and collection log events, and the position added to stats.
 */
public class GroupExtrasCodecTest
{
	private static final Gson GSON = new Gson();
	private static final long NOW = 1_762_000_000_000L;

	private static Map<Integer, Integer> items(int... pairs)
	{
		Map<Integer, Integer> out = new TreeMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			out.put(pairs[i], pairs[i + 1]);
		}
		return out;
	}

	@Test
	public void extrasRoundTrip()
	{
		MemberExtras extras = new MemberExtras(items(563, 1000, 560, 250), items(21326, 500), items(5295, 12), NOW);
		GroupCodec.Decoded d = GroupCodec.decode(GSON, GroupCodec.encodeExtras(GSON, extras));
		assertNotNull(d);
		assertEquals(GroupCodec.Kind.EXTRAS, d.kind);
		assertEquals(extras, d.extras);
	}

	@Test
	public void anUnknownSeedVaultStaysUnknown()
	{
		MemberExtras extras = new MemberExtras(items(563, 1), items(), items(), -1);
		GroupCodec.Decoded d = GroupCodec.decode(GSON, GroupCodec.encodeExtras(GSON, extras));
		assertEquals(-1, d.extras.seedVaultAt);
		assertTrue(d.extras.seedVault.isEmpty());
	}

	@Test
	public void theFullestExtrasFitTheSizeBudget()
	{
		Map<Integer, Integer> pouch = new TreeMap<>();
		for (int i = 0; i < 6; i++)
		{
			pouch.put(1_000_000 + i, Integer.MAX_VALUE);
		}
		Map<Integer, Integer> vault = new TreeMap<>();
		for (int i = 0; i < 1000; i++)
		{
			vault.put(1_000_000 + i, Integer.MAX_VALUE);
		}
		MemberExtras extras = new MemberExtras(pouch, items(1_048_000, Integer.MAX_VALUE), vault, Long.MAX_VALUE / 2);
		String payload = GroupCodec.encodeExtras(GSON, extras);
		assertTrue("payload is " + payload.length() + " chars", payload.length() <= GroupCodec.MAX_PAYLOAD_CHARS);
		assertEquals(GroupCodec.MAX_SEED_VAULT_ITEMS, GroupCodec.decode(GSON, payload).extras.seedVault.size());
	}

	@Test
	public void aPeerCannotFloodThePouch()
	{
		StringBuilder sb = new StringBuilder("{\"v\":1,\"k\":\"extras\",\"rp\":[");
		for (int i = 1; i <= 100; i++)
		{
			sb.append(i == 1 ? "" : ",").append(i).append(",1");
		}
		sb.append("],\"svts\":-1}");
		assertEquals(GroupCodec.MAX_POUCH_ITEMS, GroupCodec.decode(GSON, sb.toString()).extras.runePouch.size());
	}

	@Test
	public void eventsRoundTripWithoutTheSendersName()
	{
		List<GroupEvent> events = new ArrayList<>();
		events.add(GroupEvent.drop(NOW, "Me", "Vorkath", items(WHIP, 1, SHARK, 2)));
		events.add(GroupEvent.clog(NOW + 1, "Me", "Draconic visage"));
		List<String> payloads = GroupCodec.encodeEvents(GSON, events);
		assertEquals(1, payloads.size());
		GroupCodec.Decoded d = GroupCodec.decode(GSON, payloads.get(0));
		assertEquals(GroupCodec.Kind.EVENTS, d.kind);
		assertEquals(2, d.events.size());
		GroupEvent drop = d.events.get(0);
		assertEquals(GroupEvent.Type.DROP, drop.type);
		assertEquals(NOW, drop.at);
		assertEquals("Vorkath", drop.text);
		assertEquals(items(WHIP, 1, SHARK, 2), drop.items);
		assertEquals("the receiver files it under the party member it came from", "", drop.member);
		GroupEvent clog = d.events.get(1);
		assertEquals(GroupEvent.Type.CLOG, clog.type);
		assertEquals("Draconic visage", clog.text);
	}

	@Test
	public void manyEventsSplitAndEveryPartFits()
	{
		List<GroupEvent> events = new ArrayList<>();
		Map<Integer, Integer> loot = new TreeMap<>();
		for (int i = 0; i < GroupEvent.MAX_DROP_ITEMS; i++)
		{
			loot.put(1_000_000 + i, Integer.MAX_VALUE);
		}
		StringBuilder name = new StringBuilder();
		for (int i = 0; i < GroupEvent.MAX_TEXT; i++)
		{
			// Gson escapes '<' as six characters; the split must count what is actually sent.
			name.append('<');
		}
		for (int i = 0; i < DropLog.MAX_RESEND; i++)
		{
			events.add(GroupEvent.drop(NOW + i, "Me", name.toString(), loot));
		}
		List<String> payloads = GroupCodec.encodeEvents(GSON, events);
		assertTrue(payloads.size() > 1);
		int seen = 0;
		for (String p : payloads)
		{
			assertTrue("part is " + p.length() + " chars", p.length() <= GroupCodec.MAX_PAYLOAD_CHARS);
			seen += GroupCodec.decode(GSON, p).events.size();
		}
		assertEquals(events.size(), seen);
	}

	@Test
	public void noEventsMeansNoMessage()
	{
		assertTrue(GroupCodec.encodeEvents(GSON, Collections.emptyList()).isEmpty());
	}

	@Test
	public void badEventsAreDroppedNotFatal()
	{
		String json = "{\"v\":1,\"k\":\"events\",\"e\":[{\"t\":\"d\",\"ts\":5,\"s\":\"Goblin\",\"i\":[385,1]},"
			+ "{\"t\":\"x\",\"ts\":5,\"s\":\"?\"},{\"t\":\"c\",\"s\":\"no time\"},{\"t\":\"c\",\"ts\":6,\"s\":\"\"},7,"
			+ "{\"t\":\"d\",\"ts\":8,\"s\":\"Empty\",\"i\":[]}]}";
		GroupCodec.Decoded d = GroupCodec.decode(GSON, json);
		assertEquals(1, d.events.size());
		assertEquals("Goblin", d.events.get(0).text);
	}

	@Test
	public void statsCarryAPositionAndOldReadersIgnoreIt()
	{
		Map<String, MemberStats.Level> skills = new LinkedHashMap<>();
		skills.put("Attack", new MemberStats.Level(60, 60));
		MemberStats stats = new MemberStats(skills, 100, 100, 330, 12850, 3222, 3218, 0);
		GroupCodec.Decoded d = GroupCodec.decode(GSON, GroupCodec.encodeStats(GSON, stats));
		assertEquals(stats, d.stats);
		assertEquals(3222, d.stats.x());
		assertEquals(3218, d.stats.y());
	}

	@Test
	public void statsFromAPart2SenderHaveNoPosition()
	{
		GroupCodec.Decoded d = GroupCodec.decode(GSON, "{\"v\":1,\"k\":\"stats\",\"w\":301,\"r\":12850}");
		assertEquals(0, d.stats.x());
		assertEquals(0, d.stats.y());
		assertEquals(12850, d.stats.region());
	}

	@Test
	public void impossiblePositionsAreClamped()
	{
		GroupCodec.Decoded d = GroupCodec.decode(GSON, "{\"v\":1,\"k\":\"stats\",\"x\":-5,\"y\":99999999,\"pl\":9}");
		assertEquals(0, d.stats.x());
		assertEquals(GroupCodec.MAX_COORD, d.stats.y());
		assertEquals(3, d.stats.plane());
	}

	@Test
	public void aPart2ReaderIgnoresThePart3Kinds()
	{
		// What a part 2 build does with these: GroupCodec's default branch. Pinned so
		// nobody repurposes the names for something an old build would misread.
		assertNull(GroupCodec.decode(GSON, "{\"v\":1,\"k\":\"pets\"}"));
		assertNotNull(GroupCodec.decode(GSON, "{\"v\":1,\"k\":\"extras\"}"));
		assertNotNull(GroupCodec.decode(GSON, "{\"v\":1,\"k\":\"events\"}"));
	}
}
