package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** A posted drop's pickup: the detection window and place, and the edit that adds the second screenshot. */
public class PickupWatchTest
{
	private static final int ITEM = 4151;
	private static final long MIN = TimeUnit.MINUTES.toMillis(1);
	private static final String ID = "1305379712849158164";
	private final Gson gson = new Gson();
	private final List<String[]> seen = new CopyOnWriteArrayList<>();
	private final OkHttpClient http = new OkHttpClient();
	private HttpServer server;
	private String url;

	private static PickupWatch.Watch watch(long expires)
	{
		return new PickupWatch.Watch("u", ID, "{}", new byte[] {1}, Collections.singletonMap(ITEM, 1),
			Collections.emptyMap(), 3200, 3200, 0, expires);
	}

	private static Map<Integer, Integer> inv(int id, int n)
	{
		Map<Integer, Integer> m = new HashMap<>();
		m.put(id, n);
		return m;
	}

	@Test
	public void pickupInsideTheWindowAndAreaCounts()
	{
		PickupWatch w = new PickupWatch();
		w.add(watch(5 * MIN));
		assertEquals(0, w.inventory(inv(995, 100), 3200, 3200, 0, MIN).size());
		assertEquals(0, w.inventory(inv(ITEM, 1), 3200 + PickupWatch.NEAR + 1, 3200, 0, MIN).size());
		assertEquals(0, w.inventory(inv(ITEM, 1), 3200, 3200, 1, MIN).size());
		assertEquals(1, w.inventory(inv(ITEM, 1), 3205, 3198, 0, 2 * MIN).size());
		assertEquals("used up", 0, w.size());
	}

	@Test
	public void pickupAfterTheWindowIsLeftAlone()
	{
		PickupWatch w = new PickupWatch();
		w.add(watch(5 * MIN));
		assertEquals(0, w.inventory(inv(ITEM, 1), 3200, 3200, 0, 5 * MIN).size());
		assertEquals("expired watch dropped", 0, w.size());
	}

	@Test
	public void itemAlreadyCarriedIsNotAPickup()
	{
		PickupWatch w = new PickupWatch();
		w.add(new PickupWatch.Watch("u", ID, "{}", null, Collections.singletonMap(ITEM, 1), inv(ITEM, 1), 0, 0, 0,
			5 * MIN));
		assertEquals(0, w.inventory(inv(ITEM, 1), 0, 0, 0, MIN).size());
		assertEquals(1, w.inventory(inv(ITEM, 2), 0, 0, 0, MIN).size());
	}

	@Before
	public void start() throws IOException
	{
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/api/webhooks/123/token", this::discord);
		server.start();
		url = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/webhooks/123/token";
	}

	@After
	public void stop()
	{
		server.stop(0);
		http.dispatcher().executorService().shutdown();
	}

	private void discord(HttpExchange x) throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		InputStream in = x.getRequestBody();
		byte[] buf = new byte[8192];
		for (int n; (n = in.read(buf)) > 0; )
		{
			out.write(buf, 0, n);
		}
		String query = x.getRequestURI().getQuery();
		seen.add(new String[] {x.getRequestMethod(), x.getRequestURI().getPath(), query,
			new String(out.toByteArray(), StandardCharsets.ISO_8859_1)});
		byte[] reply = ("{\"id\":\"" + ID + "\"}").getBytes(StandardCharsets.UTF_8);
		x.getResponseHeaders().add("Content-Type", "application/json");
		x.sendResponseHeaders(200, reply.length);
		try (OutputStream o = x.getResponseBody())
		{
			o.write(reply);
		}
	}

	@Test
	public void theEditAddsTheSecondScreenshotToTheSameMessage() throws Exception
	{
		DiscordSender sender = new DiscordSender(DiscordSender.okHttp(http, gson), System::currentTimeMillis, () -> 12);
		String body = "{\"embeds\":[{\"title\":\"Drop\",\"image\":{\"url\":\"attachment://screenshot.png\"}}]}";
		String[] id = new String[1];
		sender.post(url, body, new byte[] {1, 2}, (status, messageId) -> id[0] = messageId);
		pumpUntilIdle(sender);
		assertEquals(ID, id[0]);
		assertEquals("POST", seen.get(0)[0]);
		assertEquals("wait=true", seen.get(0)[2]);

		String edit = DiscordWebhook.pickupEdit(gson, body);
		assertNotNull(edit);
		sender.edit(url, id[0], edit, new byte[] {1, 2}, new byte[] {3, 4}, null);
		pumpUntilIdle(sender);
		assertEquals(2, seen.size());
		String[] patch = seen.get(1);
		assertEquals("PATCH", patch[0]);
		assertEquals("/api/webhooks/123/token/messages/" + ID, patch[1]);
		assertTrue(patch[3], patch[3].contains("name=\"files[0]\"") && patch[3].contains("name=\"files[1]\""));
		assertTrue(patch[3], patch[3].contains("attachment://pickup.png"));
		assertTrue(patch[3], patch[3].contains("\"Picked up\""));
	}

	@Test
	public void pickupEditKeepsTheOriginalEmbedAndNamesBothUploads()
	{
		String body = "{\"username\":\"x\",\"embeds\":[{\"title\":\"Drop\"}]}";
		JsonObject o = gson.fromJson(DiscordWebhook.pickupEdit(gson, body), JsonObject.class);
		assertEquals(2, o.getAsJsonArray("embeds").size());
		assertEquals("Drop", o.getAsJsonArray("embeds").get(0).getAsJsonObject().get("title").getAsString());
		assertEquals(2, o.getAsJsonArray("attachments").size());
		assertTrue("an edit takes no username", !o.has("username"));
	}

	private static void pumpUntilIdle(DiscordSender sender) throws InterruptedException
	{
		sender.pump();
		long until = System.currentTimeMillis() + 5_000;
		while (sender.queued() > 0 && System.currentTimeMillis() < until)
		{
			Thread.sleep(10);
		}
	}
}
