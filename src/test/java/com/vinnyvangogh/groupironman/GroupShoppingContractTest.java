package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.events.PluginMessage;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The Group Shopping contract, v1 (docs/contracts/group-shopping-v1.md): every message GIM sends matches its shared
 * fixture in src/test/resources/contracts/group-shopping-v1, and GIM reads Group Shopping's {@code ready},
 * {@code request}, {@code send}, {@code store}, {@code event} and {@code bye} fixtures. Group Shopping runs the same
 * fixtures from its side.
 */
public class GroupShoppingContractTest
{
	private static final Gson GSON = new GsonBuilder().serializeNulls().create();
	private static final String CACHE = "{\"lists\":[],\"reqs\":[],\"claims\":[],\"hist\":[[1764000000000,"
		+ "\"Moss added 'cox' for Ember, Moss\"]],\"qp\":{}}";

	private final List<PluginMessage> sent = new ArrayList<>();
	private final GroupShopping shop = new GroupShopping(o -> sent.add((PluginMessage) o));

	private static JsonObject fixture(String name)
	{
		String path = "/contracts/group-shopping-v1/" + name + ".json";
		try (InputStream in = GroupShoppingContractTest.class.getResourceAsStream(path))
		{
			assertNotNull("missing fixture " + path, in);
			try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				return new JsonParser().parse(r).getAsJsonObject();
			}
		}
		catch (java.io.IOException e)
		{
			throw new AssertionError(e);
		}
	}

	private void matches(String fixture, String name, Map<String, Object> data)
	{
		shop.send(name, data);
		PluginMessage m = sent.remove(sent.size() - 1);
		JsonObject want = fixture(fixture);
		assertEquals(want.get("namespace").getAsString(), m.getNamespace());
		assertEquals(want.get("name").getAsString(), m.getName());
		JsonElement got = GSON.toJsonTree(m.getData());
		assertEquals("fixture " + fixture + " differs; GIM sends:\n" + GSON.toJson(got), want.get("data"), got);
	}

	/** Group Shopping's message as it would arrive on the bus. */
	private static PluginMessage incoming(String fixture)
	{
		JsonObject f = fixture(fixture);
		Map<String, Object> data = GSON.fromJson(f.get("data"), new TypeToken<Map<String, Object>>()
		{
		}.getType());
		return new PluginMessage(f.get("namespace").getAsString(), f.get("name").getAsString(), data);
	}

	private static Map<Integer, Integer> items(int... idQty)
	{
		Map<Integer, Integer> m = new LinkedHashMap<>();
		for (int i = 0; i < idQty.length; i += 2)
		{
			m.put(idQty[i], idQty[i + 1]);
		}
		return m;
	}

	@Test
	public void gimSendsEachMessageAsItsFixture()
	{
		matches("hello", GroupShopping.HELLO, GroupShopping.hello("1.4.0"));
		matches("bye", GroupShopping.BYE, GroupShopping.bye());
		matches("open", GroupShopping.OPEN, GroupShopping.open());
		matches("joined", GroupShopping.JOINED, GroupShopping.joined());
		matches("restore", GroupShopping.RESTORE, GroupShopping.restore(CACHE));
		matches("restore-empty", GroupShopping.RESTORE, GroupShopping.restore(null));
		matches("add-list", GroupShopping.ADD, GroupShopping.add(true, 4151, "Abyssal whip"));
		matches("add-request", GroupShopping.ADD, GroupShopping.add(false, 385, "Shark"));
	}

	@Test
	public void aPartyPayloadIsPassedOnUnread()
	{
		String payload = fixture("party").getAsJsonObject("data").get("payload").getAsString();
		assertTrue(GroupShopping.isShopping(payload));
		matches("party", GroupShopping.PARTY, GroupShopping.party("Moss", payload));
	}

	@Test
	public void theSnapshotCarriesMembersAndWhatTheyHold()
	{
		List<GroupSearch.Source> sources = Arrays.asList(
			new GroupSearch.Source("Ember", "bank", items(453, 5, 385, 12), 1L),
			new GroupSearch.Source("Moss", "inventory", items(2434, 10), -1L),
			new GroupSearch.Source("Group", "storage", items(995, 250_000), 2L));
		matches("snapshot", GroupShopping.SNAPSHOT,
			GroupShopping.snapshot("Ember", Arrays.asList("Ember", "Moss"), true, sources));
	}

	@Test
	public void anUnchangedSnapshotIsNotSentAgainUnlessAsked()
	{
		Map<String, Object> s = GroupShopping.snapshot("Ember", Collections.singletonList("Ember"), true,
			Collections.emptyList());
		shop.sendSnapshot(s, false);
		shop.sendSnapshot(GroupShopping.snapshot("Ember", Collections.singletonList("Ember"), true,
			Collections.emptyList()), false);
		assertEquals(1, sent.size());
		shop.sendSnapshot(s, true);
		assertEquals(2, sent.size());
	}

	@Test
	public void readyAndRequestAskForASnapshotAndByeForgetsIt()
	{
		assertFalse(shop.installed());
		assertEquals(GroupShopping.Inbound.SNAPSHOT, shop.receive(incoming("ready")));
		assertTrue(shop.installed());
		assertEquals("1.0.0", shop.version());
		assertEquals(GroupShopping.Inbound.SNAPSHOT, shop.receive(incoming("request")));
		assertEquals(GroupShopping.Inbound.BYE, shop.receive(incoming("bye-from-group-shopping")));
		assertFalse(shop.installed());
	}

	@Test
	public void gimNeverReadsItsOwnMessagesBack()
	{
		assertEquals(GroupShopping.Inbound.IGNORE, shop.receive(incoming("hello")));
		assertEquals(GroupShopping.Inbound.IGNORE, shop.receive(incoming("bye")));
		assertFalse(shop.installed());
	}

	@Test
	public void sendQueuesThePayloadForTheParty()
	{
		PluginMessage m = incoming("send");
		assertEquals(GroupShopping.Inbound.SEND, shop.receive(m));
		shop.queue(GroupShopping.payload(m.getData()));
		assertEquals(Collections.singletonList(m.getData().get("payload")), shop.drain());
		assertTrue(shop.drain().isEmpty());
	}

	@Test
	public void sendRefusesAPayloadThatIsNotShopping()
	{
		Map<String, Object> data = new LinkedHashMap<>(incoming("send").getData());
		data.put("payload", "{\"v\":1,\"k\":\"stats\"}");
		assertEquals(GroupShopping.Inbound.IGNORE, shop.receive(new PluginMessage(GroupShopping.NAMESPACE,
			GroupShopping.SEND, data)));
		assertNull(GroupShopping.payload(data));
		shop.queue("{\"v\":1,\"k\":\"stats\"}");
		assertTrue(shop.drain().isEmpty());
	}

	@Test
	public void aFullQueueDropsTheOldest()
	{
		for (int i = 0; i < GroupShopping.MAX_QUEUED + 3; i++)
		{
			shop.queue("{\"v\":1,\"k\":\"shop\",\"n\":" + i + "}");
		}
		List<String> out = shop.drain();
		assertEquals(GroupShopping.MAX_QUEUED, out.size());
		assertTrue(out.get(0).contains("\"n\":3"));
	}

	@Test
	public void storeIsKeptAsText()
	{
		PluginMessage m = incoming("store");
		assertEquals(GroupShopping.Inbound.STORE, shop.receive(m));
		assertTrue(m.getData().get("cache") instanceof String);
	}

	@Test
	public void aRequestEventBecomesTheSameGroupEvent()
	{
		assertEquals(GroupShopping.Inbound.EVENT, shop.receive(incoming("event-made")));
		GroupEvent made = GroupShopping.event(incoming("event-made").getData(), "Ember", 5L);
		assertNotNull(made);
		assertEquals(GroupEvent.Type.REQUEST, made.type);
		assertEquals("Ember", made.member);
		assertEquals("made a request, 'Fire cape': 500 Shark, 20 Prayer potion(4)", RequestBoard.describe(made));

		GroupEvent claimed = GroupShopping.event(incoming("event-claimed").getData(), "ember", 5L);
		assertNotNull(claimed);
		assertEquals("will get Shark for Moss's 'Fire cape'", RequestBoard.describe(claimed));
		assertEquals(Collections.singletonMap(385, 1), claimed.items);
	}

	@Test
	public void groupShoppingCannotSpeakForAnotherMember()
	{
		assertNull(GroupShopping.event(incoming("event-made").getData(), "Moss", 5L));
		assertNull(GroupShopping.event(incoming("event-made").getData(), "", 5L));
		Map<String, Object> odd = new LinkedHashMap<>(incoming("event-made").getData());
		odd.put("kind", 9);
		assertNull(GroupShopping.event(odd, "Ember", 5L));
		odd.put("kind", 1);
		odd.put("type", "DEATH");
		assertNull(GroupShopping.event(odd, "Ember", 5L));
	}

	@Test
	public void theShoppingKindsAreTheFourPartyKinds()
	{
		assertTrue(GroupShopping.isShopping("{\"v\":1,\"k\":\"qstate\"}"));
		assertTrue(GroupShopping.isShopping("{\"v\":1,\"k\":\"req\"}"));
		assertFalse(GroupShopping.isShopping("{\"v\":1,\"k\":\"bank\"}"));
		assertFalse(GroupShopping.isShopping(null));
	}
}
