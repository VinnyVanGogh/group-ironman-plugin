package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.runelite.client.events.PluginMessage;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The Discord Relay contract, v1 (docs/contracts/discord-relay-v1.md): every message GIM sends matches its shared
 * fixture in src/test/resources/contracts/discord-relay-v1 (namespace, name, and each key's type and value), and
 * GIM reads Discord Relay's {@code ready} and {@code bye} fixtures. Discord Relay runs the same fixtures from its
 * side, so a change to either plugin that breaks the other fails here first.
 */
public class DiscordRelayContractTest
{
	private static final long AT = 1767225600000L;
	private static final Gson GSON = new GsonBuilder().serializeNulls().create();

	private static final ItemLookup LOOKUP = new ItemLookup()
	{
		@Override
		public String name(int id)
		{
			return id == 4151 ? "Abyssal whip" : "Bones";
		}

		@Override
		public long price(int id)
		{
			return id == 4151 ? 1_500_000L : 100L;
		}
	};

	private final List<PluginMessage> sent = new ArrayList<>();
	private final DiscordRelay relay = new DiscordRelay(o -> sent.add((PluginMessage) o));

	private static JsonObject fixture(String name)
	{
		String path = "/contracts/discord-relay-v1/" + name + ".json";
		try (InputStream in = DiscordRelayContractTest.class.getResourceAsStream(path))
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

	/** {@code name}'s message as GIM sends it equals the fixture: namespace, name and data. */
	private void matches(String fixture, String name, Map<String, Object> data)
	{
		relay.send(name, data);
		PluginMessage m = sent.remove(sent.size() - 1);
		JsonObject want = fixture(fixture);
		assertEquals(want.get("namespace").getAsString(), m.getNamespace());
		assertEquals(want.get("name").getAsString(), m.getName());
		JsonElement got = GSON.toJsonTree(m.getData());
		assertEquals("fixture " + fixture + " differs; GIM sends:\n" + GSON.toJson(got), want.get("data"), got);
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
		matches("hello", DiscordRelay.HELLO, DiscordRelay.hello("1.0.0"));
	}

	@Test
	public void bye()
	{
		matches("bye", DiscordRelay.BYE, DiscordRelay.bye());
	}

	@Test
	public void captureAndRelease()
	{
		matches("capture", DiscordRelay.CAPTURE, DiscordRelay.capture(DiscordRelay.GRAVE));
		matches("release", DiscordRelay.RELEASE, DiscordRelay.release(DiscordRelay.GRAVE));
	}

	@Test
	public void legacy()
	{
		matches("legacy", DiscordRelay.LEGACY, DiscordRelay.legacy("w111\t1234567890\t20454\t", null, null));
	}

	@Test
	public void board()
	{
		String lines = "🟢 **Alice** online (world 302)\n🔴 **Bob** last seen <t:1767225000:R> on world 330";
		matches("board", DiscordRelay.BOARD, DiscordRelay.board(true, true, lines));
	}

	@Test
	public void boardNotKeptSendsNoLines()
	{
		Map<String, Object> m = DiscordRelay.board(false, true, "anything");
		assertEquals(false, m.get("keep"));
		assertEquals("", m.get("lines"));
	}

	@Test
	public void eventDrop()
	{
		GroupEvent e = GroupEvent.of(GroupEvent.Type.DROP, AT, "Alice").text("Abyssal demon")
			.items(items(4151, 1, 526, 2)).number(120).build();
		matches("event-drop", DiscordRelay.EVENT, DiscordRelay.event(e, LOOKUP, true, false, false));
	}

	@Test
	public void eventLevelForTheDigest()
	{
		GroupEvent e = GroupEvent.of(GroupEvent.Type.LEVEL_UP, AT, "Alice").text("Attack").number(67).previous(66)
			.world(302).build();
		matches("event-level-digest", DiscordRelay.EVENT, DiscordRelay.event(e, LOOKUP, false, true, false));
	}

	@Test
	public void eventStorageSummaryAttaches()
	{
		GroupEvent e = GroupEvent.of(GroupEvent.Type.STORAGE_SUMMARY, AT, "Alice").items(items(526, 2))
			.taken(items(4151, 1)).build();
		matches("event-storage-attach", DiscordRelay.EVENT, DiscordRelay.event(e, LOOKUP, false, false, true));
	}

	@Test
	public void eventGravestoneTakesTheGraveFrame()
	{
		GroupEvent e = GroupEvent.of(GroupEvent.Type.GRAVESTONE_EXPIRING, AT, "Alice").number(3).world(302)
			.location("Lumbridge").build();
		matches("event-gravestone", DiscordRelay.EVENT, DiscordRelay.event(e, LOOKUP, true, false, false));
	}

	@Test
	public void noScreenshotForLoginsOrOtherMembersBroadcasts()
	{
		assertEquals(DiscordRelay.SHOT_NONE, DiscordRelay.screenshot(
			GroupEvent.of(GroupEvent.Type.ONLINE, AT, "Alice").text("online").build()));
		assertEquals(DiscordRelay.SHOT_NONE, DiscordRelay.screenshot(
			GroupEvent.of(GroupEvent.Type.QUEST, AT, "Bob").text("Cook's Assistant").broadcast(true).build()));
	}

	@Test
	public void settingsWithTheLeadersSharedWebhooks()
	{
		EventSettings s = new EventSettings(EnumSet.of(GroupEvent.Type.DROP, GroupEvent.Type.LEVEL_UP,
			GroupEvent.Type.GRAVESTONE_EXPIRING), EnumSet.noneOf(GroupEvent.Type.class), 100_000, 50_000, 1, 7, true,
			0, 100, 5, EnumSet.of(GroupEvent.Category.DROPS, GroupEvent.Category.PROGRESS), 20);
		Map<Webhook, Webhooks.Setting> hooks = new EnumMap<>(Webhook.class);
		for (Webhook w : Webhook.values())
		{
			hooks.put(w, new Webhooks.Setting("", DiscordPosterName.WEBHOOK, ""));
		}
		hooks.put(Webhook.MAIN, new Webhooks.Setting("https://discord.com/api/webhooks/111/maintoken",
			DiscordPosterName.GIM_UTILITIES, ""));
		hooks.put(Webhook.DEATHS, new Webhooks.Setting("", DiscordPosterName.CUSTOM, "Grim"));
		GroupWebhooks shared = new GroupWebhooks("group", 1, AT, "Alice", "key", true, hooks, "sig");
		matches("settings", DiscordRelay.SETTINGS, DiscordRelay.settings(s, 23, 7, 2,
			new EasterEggs.Settings(true, 1_000_000, 1_000_000), OnlineStatus.LIVE_BOARD, shared));
	}

	@Test
	public void sendIfChangedSendsOnceUntilReady()
	{
		Map<String, Object> board = DiscordRelay.board(true, true, "x");
		relay.sendIfChanged(DiscordRelay.BOARD, board);
		relay.sendIfChanged(DiscordRelay.BOARD, DiscordRelay.board(true, true, "x"));
		assertEquals(1, sent.size());
		relay.receive(inbound("ready"));
		relay.sendIfChanged(DiscordRelay.BOARD, board);
		assertEquals("a new ready forgets what was sent", 2, sent.size());
	}

	@SuppressWarnings("unchecked")
	private static PluginMessage inbound(String fixture)
	{
		JsonObject f = fixture(fixture);
		Map<String, Object> data = GSON.fromJson(f.get("data"), Map.class);
		return new PluginMessage(f.get("namespace").getAsString(), f.get("name").getAsString(), data);
	}

	@Test
	public void readyMarksRelayInstalledAndReadsItsWebhooks()
	{
		assertFalse(relay.installed());
		Webhooks own = relay.receive(inbound("ready"));
		assertTrue(relay.installed());
		assertTrue(relay.posting());
		assertEquals("1.0.0", relay.relayVersion());
		assertNotNull(own);
		assertEquals("https://discord.com/api/webhooks/333/onlinetoken", own.url(Webhook.ONLINE));
		assertEquals("Drops falls back to Main", "https://discord.com/api/webhooks/111/maintoken",
			own.url(Webhook.DROPS));
		assertEquals(DiscordPosterName.CUSTOM, own.settings().get(Webhook.ONLINE).mode);
		assertEquals("Lumbridge Guide", own.settings().get(Webhook.ONLINE).name);
	}

	@Test
	public void byeMarksRelayGone()
	{
		relay.receive(inbound("ready"));
		Map<String, Object> bye = new java.util.HashMap<>();
		bye.put("v", 1);
		bye.put("source", DiscordRelay.RELAY);
		Webhooks own = relay.receive(new PluginMessage(DiscordRelay.NAMESPACE, DiscordRelay.BYE, bye));
		assertFalse(relay.installed());
		assertFalse(own.any());
	}

	@Test
	public void ignoresItsOwnMessagesAndOtherNamespaces()
	{
		assertNull(relay.receive(new PluginMessage(DiscordRelay.NAMESPACE, DiscordRelay.READY,
			DiscordRelay.hello("1.0.0"))));
		JsonObject f = fixture("ready");
		assertNull(relay.receive(new PluginMessage("shortestpath", "ready",
			GSON.fromJson(f.get("data"), Map.class))));
		assertFalse(relay.installed());
	}

	@Test
	public void installHintIsTheBoardsLine()
	{
		assertEquals("Install Discord Relay to post to Discord", DiscordRelay.INSTALL_HINT);
	}
}
