package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The group payloads on the wire: banks split to fit the size budget,
 * carried items, storage visits and stats, read back by a build that may be
 * older or newer than the sender.
 */
public class GroupCodecTest
{
	private static final Gson GSON = new Gson();

	private static Map<Integer, Integer> items(int... pairs)
	{
		Map<Integer, Integer> out = new TreeMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			out.put(pairs[i], pairs[i + 1]);
		}
		return out;
	}

	/** A bank as big as the game allows, every stack at max cash so every number is as long as it gets. */
	private static BankSnapshot hugeBank()
	{
		Map<Integer, Integer> items = new TreeMap<>();
		for (int i = 0; i < 1410; i++)
		{
			items.put(30000 + i * 7, Integer.MAX_VALUE);
		}
		return new BankSnapshot(1_762_000_000_000L, items);
	}

	private static BankSnapshot reassemble(List<String> payloads)
	{
		BankAssembler assembler = new BankAssembler();
		BankSnapshot out = null;
		for (String p : payloads)
		{
			GroupCodec.Decoded d = GroupCodec.decode(GSON, p);
			assertNotNull(p, d);
			assertEquals(GroupCodec.Kind.BANK, d.kind);
			BankSnapshot done = assembler.accept(7L, d.bank);
			if (done != null)
			{
				out = done;
			}
		}
		return out;
	}

	@Test
	public void everyBankPartFitsTheSizeBudget()
	{
		List<String> parts = GroupCodec.encodeBank(GSON, hugeBank());
		assertTrue("a full bank needs splitting", parts.size() > 1);
		assertTrue(parts.size() <= GroupCodec.MAX_BANK_PARTS);
		for (String p : parts)
		{
			assertTrue("part is " + p.length() + " chars", p.length() <= GroupCodec.MAX_PAYLOAD_CHARS);
		}
	}

	@Test
	public void aSplitBankReassemblesExactly()
	{
		BankSnapshot bank = hugeBank();
		assertEquals(bank, reassemble(GroupCodec.encodeBank(GSON, bank)));
	}

	@Test
	public void aSmallBankIsOneMessage()
	{
		BankSnapshot bank = new BankSnapshot(5L, items(995, 1_000_000, 385, 300));
		List<String> parts = GroupCodec.encodeBank(GSON, bank);
		assertEquals(1, parts.size());
		assertEquals(bank, reassemble(parts));
	}

	@Test
	public void anEmptyBankStillSendsOnePart()
	{
		BankSnapshot bank = new BankSnapshot(5L, items());
		List<String> parts = GroupCodec.encodeBank(GSON, bank);
		assertEquals(1, parts.size());
		assertEquals(bank, reassemble(parts));
	}

	@Test
	public void storageVisitRoundTrips()
	{
		StorageChange change = new StorageChange(42L, "ignored", items(385, 5), items(453, 100), items(453, 100, 385, 15));
		GroupCodec.Decoded d = GroupCodec.decode(GSON, GroupCodec.encodeStorage(GSON, change));
		assertEquals(GroupCodec.Kind.STORAGE, d.kind);
		assertEquals(42L, d.storage.at());
		assertEquals(change.took(), d.storage.took());
		assertEquals(change.deposited(), d.storage.deposited());
		assertEquals(change.after(), d.storage.after());
		assertEquals("the sender never names who made the change", "", d.storage.member());
	}

	@Test
	public void statsRoundTrip()
	{
		Map<String, MemberStats.Level> skills = new LinkedHashMap<>();
		skills.put("Attack", new MemberStats.Level(90, 98));
		skills.put("Defence", new MemberStats.Level(1, 1));
		skills.put("Hitpoints", new MemberStats.Level(99, 71));
		MemberStats stats = new MemberStats(skills, 87, 50, 302, 12850);
		GroupCodec.Decoded d = GroupCodec.decode(GSON, GroupCodec.encodeStats(GSON, stats));
		assertEquals(GroupCodec.Kind.STATS, d.kind);
		assertEquals(stats, d.stats);
	}

	@Test
	public void carriedRoundTrips()
	{
		GroupCodec.Decoded d = GroupCodec.decode(GSON, GroupCodec.encodeCarried(GSON, items(385, 20), items(4151, 1)));
		assertEquals(GroupCodec.Kind.CARRIED, d.kind);
		assertEquals(items(385, 20), d.carried.inventory);
		assertEquals(items(4151, 1), d.carried.worn);
	}

	private static List<GroupCodec.Slot> slots(int... pairs)
	{
		List<GroupCodec.Slot> out = new java.util.ArrayList<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			out.add(GroupCodec.Slot.of(pairs[i], pairs[i + 1]));
		}
		return out;
	}

	@Test
	public void inventorySlotsTravelInOrderWithEmptiesAndRawIds()
	{
		// Coins, a gap, two potions, a noted shark stack (386): the counts say shark, the slot says noted shark.
		List<GroupCodec.Slot> sent = slots(995, 5000, 0, 0, 2434, 1, 2434, 1, 386, 10);
		String payload = GroupCodec.encodeCarried(GSON, items(995, 5000, 2434, 2, 385, 10), items(4151, 1), sent);
		GroupCodec.Decoded d = GroupCodec.decode(GSON, payload);
		assertEquals(GroupCodec.INVENTORY_SLOTS, d.carried.slots.size());
		assertEquals(995, d.carried.slots.get(0).id);
		assertEquals(5000, d.carried.slots.get(0).quantity);
		assertTrue(d.carried.slots.get(1).empty());
		assertEquals(2434, d.carried.slots.get(2).id);
		assertEquals(2434, d.carried.slots.get(3).id);
		assertEquals(386, d.carried.slots.get(4).id);
		assertEquals(10, d.carried.slots.get(4).quantity);
		for (int i = 5; i < GroupCodec.INVENTORY_SLOTS; i++)
		{
			assertTrue("slot " + i, d.carried.slots.get(i).empty());
		}
		assertEquals("counts stay canonical for Find and totals", items(995, 5000, 2434, 2, 385, 10), d.carried.inventory);
	}

	/** A build from before slots (gim-test-2) reads only inv and eq, so those must be exactly what it always got. */
	@Test
	public void theSlotMessageKeepsTheOldFieldsForAnOldReader()
	{
		Map<Integer, Integer> inv = items(2434, 14);
		Map<Integer, Integer> worn = items(4151, 1);
		int[] fourteen = new int[28];
		for (int i = 0; i < 28; i += 2)
		{
			fourteen[i] = 2434;
			fourteen[i + 1] = 1;
		}
		com.google.gson.JsonObject withSlots = GSON.fromJson(
			GroupCodec.encodeCarried(GSON, inv, worn, slots(fourteen)), com.google.gson.JsonObject.class);
		assertTrue(withSlots.has("is"));
		withSlots.remove("is");
		assertEquals("old fields and version unchanged", GroupCodec.encodeCarried(GSON, inv, worn), GSON.toJson(withSlots));

		GroupCodec.Decoded d = GroupCodec.decode(GSON, GroupCodec.encodeCarried(GSON, inv, worn, slots(fourteen)));
		assertEquals(inv, d.carried.inventory);
		assertEquals(worn, d.carried.worn);
	}

	@Test
	public void anOldCarriedMessageHasNoSlots()
	{
		GroupCodec.Decoded d = GroupCodec.decode(GSON, "{\"v\":1,\"k\":\"carried\",\"inv\":[2434,14],\"eq\":[]}");
		assertTrue(d.carried.slots.isEmpty());
		assertEquals(items(2434, 14), d.carried.inventory);
		assertTrue("no slots sent, none encoded", !GroupCodec.encodeCarried(GSON, items(1, 1), items()).contains("\"is\""));
	}

	@Test
	public void badSlotsBecomeEmptyInPlaceAndAPeerCannotSendMoreThan28()
	{
		StringBuilder sb = new StringBuilder("{\"v\":1,\"k\":\"carried\",\"inv\":[],\"eq\":[],\"is\":[-1,5,385,0,\"x\",1,995,7");
		for (int i = 0; i < 100; i++)
		{
			sb.append(",385,1");
		}
		sb.append("]}");
		List<GroupCodec.Slot> got = GroupCodec.decode(GSON, sb.toString()).carried.slots;
		assertEquals(GroupCodec.INVENTORY_SLOTS, got.size());
		assertTrue(got.get(0).empty());
		assertTrue(got.get(1).empty());
		assertTrue(got.get(2).empty());
		assertEquals(995, got.get(3).id);
		assertEquals(385, got.get(27).id);
		assertTrue("not an array: no slots", GroupCodec.decode(GSON,
			"{\"v\":1,\"k\":\"carried\",\"inv\":[],\"is\":{}}").carried.slots.isEmpty());
	}

	@Test
	public void aNewerSenderWithExtraFieldsIsStillRead()
	{
		String json = "{\"v\":7,\"k\":\"carried\",\"inv\":[385,3],\"eq\":[],\"future\":{\"x\":1},\"note\":\"hi\"}";
		GroupCodec.Decoded d = GroupCodec.decode(GSON, json);
		assertNotNull(d);
		assertEquals(items(385, 3), d.carried.inventory);
	}

	@Test
	public void anOlderSenderMissingFieldsDecodesToEmpty()
	{
		GroupCodec.Decoded d = GroupCodec.decode(GSON, "{\"v\":1,\"k\":\"stats\"}");
		assertNotNull(d);
		assertTrue(d.stats.skills().isEmpty());
		assertEquals(0, d.stats.world());
	}

	@Test
	public void aKindFromANewerBuildIsIgnored()
	{
		assertNull(GroupCodec.decode(GSON, "{\"v\":2,\"k\":\"pets\",\"i\":[1,1]}"));
	}

	@Test
	public void unversionedOrBrokenPayloadsAreIgnored()
	{
		assertNull(GroupCodec.decode(GSON, null));
		assertNull(GroupCodec.decode(GSON, ""));
		assertNull(GroupCodec.decode(GSON, "not json"));
		assertNull(GroupCodec.decode(GSON, "[1,2]"));
		assertNull(GroupCodec.decode(GSON, "{\"k\":\"carried\"}"));
		assertNull(GroupCodec.decode(GSON, "{\"v\":0,\"k\":\"carried\"}"));
		assertNull(GroupCodec.decode(GSON, "{\"v\":1}"));
	}

	@Test
	public void badItemPairsAreDroppedNotFatal()
	{
		String json = "{\"v\":1,\"k\":\"carried\",\"inv\":[385,3,-1,5,0,2,99,0,1.5,2,\"x\",4,9999999,1,995,10,7],\"eq\":{}}";
		GroupCodec.Decoded d = GroupCodec.decode(GSON, json);
		assertEquals(items(385, 3, 995, 10), d.carried.inventory);
		assertTrue(d.carried.worn.isEmpty());
	}

	@Test
	public void impossibleLevelsAreDropped()
	{
		String json = "{\"v\":1,\"k\":\"stats\",\"s\":{\"Attack\":[120,120],\"Magic\":[99,250],\"Prayer\":[70,43],\"X\":\"y\"},\"run\":500,\"spec\":-4}";
		GroupCodec.Decoded d = GroupCodec.decode(GSON, json);
		assertEquals(1, d.stats.skills().size());
		assertEquals(new MemberStats.Level(70, 43), d.stats.skills().get("Prayer"));
		assertEquals(100, d.stats.runEnergy());
		assertEquals(0, d.stats.specialAttack());
	}

	@Test
	public void badBankPartHeadersAreIgnored()
	{
		assertNull(GroupCodec.decode(GSON, "{\"v\":1,\"k\":\"bank\",\"ts\":1,\"p\":3,\"n\":3,\"i\":[]}"));
		assertNull(GroupCodec.decode(GSON, "{\"v\":1,\"k\":\"bank\",\"ts\":1,\"p\":0,\"n\":99,\"i\":[]}"));
		assertNull(GroupCodec.decode(GSON, "{\"v\":1,\"k\":\"bank\",\"p\":0,\"n\":1,\"i\":[]}"));
	}

	@Test
	public void aPeerCannotFloodTheCarriedList()
	{
		StringBuilder sb = new StringBuilder("{\"v\":1,\"k\":\"carried\",\"inv\":[");
		for (int i = 1; i <= 500; i++)
		{
			sb.append(i == 1 ? "" : ",").append(i).append(",1");
		}
		sb.append("]}");
		assertEquals(GroupCodec.MAX_CARRIED_ITEMS, GroupCodec.decode(GSON, sb.toString()).carried.inventory.size());
	}
}
