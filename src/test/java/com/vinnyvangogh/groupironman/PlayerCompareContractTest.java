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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.runelite.client.events.PluginMessage;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The Player Compare group contract, v1 (docs/contracts/player-compare-group-v1.md): every message GIM sends matches
 * its shared fixture in src/test/resources/contracts/player-compare-group-v1, and GIM reads Player Compare's
 * {@code ready}, {@code request} and {@code bye} fixtures. Player Compare runs the same fixtures from its side.
 */
public class PlayerCompareContractTest
{
	private static final Gson GSON = new GsonBuilder().serializeNulls().create();

	private final List<PluginMessage> sent = new ArrayList<>();
	private final PlayerCompare compare = new PlayerCompare(o -> sent.add((PluginMessage) o));

	private static JsonObject fixture(String name)
	{
		String path = "/contracts/player-compare-group-v1/" + name + ".json";
		try (InputStream in = PlayerCompareContractTest.class.getResourceAsStream(path))
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
		compare.send(name, data);
		PluginMessage m = sent.remove(sent.size() - 1);
		JsonObject want = fixture(fixture);
		assertEquals(want.get("namespace").getAsString(), m.getNamespace());
		assertEquals(want.get("name").getAsString(), m.getName());
		JsonElement got = GSON.toJsonTree(m.getData());
		assertEquals("fixture " + fixture + " differs; GIM sends:\n" + GSON.toJson(got), want.get("data"), got);
	}

	/** Player Compare's message as it would arrive on the bus. */
	private static PluginMessage incoming(String fixture)
	{
		JsonObject f = fixture(fixture);
		Map<String, Object> data = GSON.fromJson(f.get("data"), new TypeToken<Map<String, Object>>()
		{
		}.getType());
		return new PluginMessage(f.get("namespace").getAsString(), f.get("name").getAsString(), data);
	}

	private static <V> Map<String, V> map(String k1, V v1, String k2, V v2)
	{
		Map<String, V> m = new TreeMap<>();
		m.put(k1, v1);
		if (k2 != null)
		{
			m.put(k2, v2);
		}
		return m;
	}

	private static Map<Integer, Integer> items(int... idQty)
	{
		Map<Integer, Integer> out = new TreeMap<>();
		for (int i = 0; i < idQty.length; i += 2)
		{
			out.put(idQty[i], idQty[i + 1]);
		}
		return out;
	}

	@Test
	public void hello()
	{
		matches("hello", PlayerCompare.HELLO, PlayerCompare.hello("1.0.0"));
	}

	@Test
	public void bye()
	{
		matches("bye", PlayerCompare.BYE, PlayerCompare.bye());
	}

	@Test
	public void open()
	{
		matches("open", PlayerCompare.OPEN, PlayerCompare.open());
	}

	@Test
	public void snapshot()
	{
		PlayerCompare.Member alice = new PlayerCompare.Member("Alice", map("Woodcutting", 61, "Attack", 70), 112,
			map("Zulrah", 75.6, null, null), map("Zulrah", 140, null, null), items(4151, 1), items(1511, 27, 385, 12),
			items(1511, 300));
		PlayerCompare.Member bob = new PlayerCompare.Member("Bob", map("Attack", 65, null, null), -1,
			Collections.emptyMap(), null, null, null, null);
		matches("snapshot", PlayerCompare.SNAPSHOT,
			PlayerCompare.snapshot("Alice", Arrays.asList(alice, bob), items(1513, 50)));
	}

	@Test
	public void readyAndRequestAskForASnapshot()
	{
		assertFalse(compare.installed());
		assertTrue(compare.receive(incoming("ready")));
		assertTrue(compare.installed());
		assertEquals("1.0.0", compare.version());
		assertTrue(compare.receive(incoming("request")));
	}

	@Test
	public void byeFromPlayerCompareUninstalls()
	{
		compare.receive(incoming("ready"));
		assertFalse(compare.receive(incoming("bye-from-player-compare")));
		assertFalse(compare.installed());
	}

	@Test
	public void ownMessagesAreIgnored()
	{
		compare.send(PlayerCompare.HELLO, PlayerCompare.hello("1.0.0"));
		assertFalse(compare.receive(sent.get(0)));
		assertFalse(compare.installed());
	}

	@Test
	public void selfIsAddedOnce()
	{
		assertEquals(Arrays.asList("Bob", "alice"), PlayerCompare.names(Arrays.asList("Bob", "alice"), "Alice"));
		assertEquals(Arrays.asList("Bob", "Alice"), PlayerCompare.names(Collections.singletonList("Bob"), "Alice"));
	}
}
