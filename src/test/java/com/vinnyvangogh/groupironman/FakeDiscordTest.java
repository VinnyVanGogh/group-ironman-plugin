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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The level-up streak through the real OkHttp transport against a local server that answers as Discord does: a
 * JSON or multipart post in, and for {@code ?wait=true} the created message as JSON (Discord "Execute Webhook";
 * the reply's shape is the documented Message object, {@link #MESSAGE}). The other tests fake the reply; this one
 * proves the id is read off the wire and the next level edits that message.
 */
public class FakeDiscordTest
{
	private static final long SEC = TimeUnit.SECONDS.toMillis(1);
	private static final long WINDOW = TimeUnit.MINUTES.toMillis(30);
	private static final String MESSAGE_ID = "1305379712849158164";
	/**
	 * A {@code ?wait=true} reply as Discord sends it for a webhook post with one embed and a screenshot: the
	 * message id first, and nested objects (author, attachments) that carry ids of their own.
	 */
	static final String MESSAGE = "{\"type\":0,\"content\":\"\",\"mentions\":[],\"mention_roles\":[],"
		+ "\"attachments\":[{\"id\":\"1305379713214185522\",\"filename\":\"screenshot.png\",\"size\":48213,"
		+ "\"url\":\"https://cdn.discordapp.com/attachments/1/2/screenshot.png\",\"proxy_url\":\"https://media"
		+ ".discordapp.net/attachments/1/2/screenshot.png\",\"width\":765,\"height\":503,"
		+ "\"content_type\":\"image/png\"}],"
		+ "\"embeds\":[{\"type\":\"rich\",\"title\":\"Alex: Level up\",\"description\":\"Prayer level 24\","
		+ "\"color\":5763719,\"timestamp\":\"2026-11-10T20:22:00+00:00\","
		+ "\"image\":{\"url\":\"attachment://screenshot.png\",\"width\":765,\"height\":503}}],"
		+ "\"timestamp\":\"2026-11-11T04:22:01.123000+00:00\",\"edited_timestamp\":null,\"flags\":0,"
		+ "\"components\":[],\"id\":\"" + MESSAGE_ID + "\",\"channel_id\":\"1290000000000000000\","
		+ "\"author\":{\"id\":\"1290000000000000001\",\"username\":\"GIM Utilities\",\"avatar\":null,"
		+ "\"discriminator\":\"0000\",\"public_flags\":0,\"flags\":0,\"bot\":true,\"global_name\":null},"
		+ "\"pinned\":false,\"mention_everyone\":false,\"tts\":false,\"webhook_id\":\"1290000000000000001\"}";

	/** One request as the server saw it. */
	private static final class Seen
	{
		final String method;
		final String path;
		final String query;
		final String contentType;
		final String body;

		Seen(HttpExchange x, String body)
		{
			this.method = x.getRequestMethod();
			this.path = x.getRequestURI().getPath();
			this.query = x.getRequestURI().getQuery();
			this.contentType = x.getRequestHeaders().getFirst("Content-Type");
			this.body = body;
		}
	}

	private final Gson gson = new Gson();
	private final List<Seen> seen = new CopyOnWriteArrayList<>();
	private final OkHttpClient http = new OkHttpClient();
	private HttpServer server;
	private String url;
	private long now = 1_000_000;
	private DiscordSender sender;
	private LevelPosts levels;

	@Before
	public void start() throws IOException
	{
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/api/webhooks/123/token", this::discord);
		server.start();
		url = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/webhooks/123/token";
		sender = new DiscordSender(DiscordSender.okHttp(http, gson), () -> now, () -> 12);
		levels = new LevelPosts(sender, gson, () -> now, () -> DiscordStyle.EMBED, FakeItems::new);
	}

	@After
	public void stop()
	{
		server.stop(0);
		http.dispatcher().executorService().shutdown();
	}

	/** Discord: a post without wait is 204 and no body; with wait, or an edit, 200 and the message. */
	private void discord(HttpExchange x) throws IOException
	{
		seen.add(new Seen(x, read(x.getRequestBody())));
		boolean edit = "PATCH".equals(x.getRequestMethod());
		boolean wait = x.getRequestURI().getQuery() != null && x.getRequestURI().getQuery().contains("wait=true");
		if (edit && !x.getRequestURI().getPath().endsWith("/messages/" + MESSAGE_ID))
		{
			respond(x, 404, "{\"message\":\"Unknown Message\",\"code\":10008}");
		}
		else if (edit || wait)
		{
			respond(x, 200, MESSAGE);
		}
		else
		{
			x.sendResponseHeaders(204, -1);
			x.close();
		}
	}

	private static void respond(HttpExchange x, int status, String json) throws IOException
	{
		byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
		x.getResponseHeaders().add("Content-Type", "application/json");
		x.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = x.getResponseBody())
		{
			out.write(bytes);
		}
	}

	private static String read(InputStream in) throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		for (int n; (n = in.read(buf)) > 0; )
		{
			out.write(buf, 0, n);
		}
		return new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
	}

	private static GroupEvent prayer(int n)
	{
		return GroupEvent.of(GroupEvent.Type.LEVEL_UP, 0, "Alex").text("Prayer").number(n).build();
	}

	private void gain(int n, byte[] png)
	{
		String json = DiscordWebhook.levels(gson, prayer(n), "Prayer level " + n, new FakeItems(), DiscordStyle.EMBED,
			png != null);
		levels.level(url, prayer(n), WINDOW, png, json);
	}

	/** The engine's tick, then wait for the request it sent (if any) to be answered and its outcome handled. */
	private void tick() throws InterruptedException
	{
		int before = seen.size();
		levels.tick();
		sender.pump();
		long until = System.currentTimeMillis() + 5_000;
		while (sender.queued() > 0 && System.currentTimeMillis() < until)
		{
			Thread.sleep(10);
		}
		assertTrue("the fake Discord answered", sender.queued() == 0 || seen.size() > before);
	}

	private void streak(byte[] png) throws InterruptedException
	{
		gain(24, png);
		tick();
		assertEquals(1, seen.size());
		Seen first = seen.get(0);
		assertEquals("POST", first.method);
		assertEquals("wait=true", first.query);

		// A minute later: Prayer 25 and 26 join, and become one edit of the first message.
		now += 60 * SEC;
		gain(25, png);
		now += 60 * SEC;
		gain(26, png);
		tick();
		assertEquals("no second post: the streak's message is edited", 2, seen.size());
		Seen edit = seen.get(1);
		assertEquals("PATCH", edit.method);
		assertEquals("/api/webhooks/123/token/messages/" + MESSAGE_ID, edit.path);
		assertNull(edit.query);
		assertTrue(edit.body, edit.body.contains("(+3) in 2 min"));

		// Nothing else owed.
		now += 60 * SEC;
		tick();
		assertEquals(2, seen.size());
	}

	@Test
	public void aJsonStreakIsOnePostThenAnEdit() throws InterruptedException
	{
		streak(null);
		assertTrue(seen.get(0).contentType.startsWith("application/json"));
		JsonObject patch = gson.fromJson(seen.get(1).body, JsonObject.class);
		assertFalse("no screenshot, no attachments list", patch.has("attachments"));
		assertFalse("the edit endpoint takes no username", patch.has("username"));
	}

	@Test
	public void aScreenshotStreakIsOneMultipartPostThenAMultipartEdit() throws InterruptedException
	{
		byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
		streak(png);
		Seen post = seen.get(0);
		assertTrue(post.contentType, post.contentType.startsWith("multipart/form-data"));
		assertTrue(post.body.contains("name=\"payload_json\""));
		assertTrue(post.body.contains("name=\"files[0]\"; filename=\"screenshot.png\""));

		Seen edit = seen.get(1);
		assertTrue(edit.contentType, edit.contentType.startsWith("multipart/form-data"));
		assertTrue(edit.body.contains("name=\"files[0]\"; filename=\"screenshot.png\""));
		assertTrue(edit.body, edit.body.contains("\"attachments\":[{\"id\":0,\"filename\":\"screenshot.png\"}]"));
	}

	@Test
	public void aWebhookQueryIsKeptAndWaitJoinsIt()
	{
		DiscordQueue.Result r = (status, id) -> { };
		String hook = "https://discord.com/api/webhooks/1/t";
		assertEquals(hook + "?wait=true", DiscordSender.target(new DiscordQueue.Post(hook, "{}", null, null, r)));
		assertEquals(hook + "?thread_id=9&wait=true",
			DiscordSender.target(new DiscordQueue.Post(hook + "/?thread_id=9", "{}", null, null, r)));
		assertEquals(hook + "/messages/7?thread_id=9",
			DiscordSender.target(new DiscordQueue.Post(hook + "?thread_id=9", "{}", null, "7", r)));
		assertEquals(hook + "/messages/7", DiscordSender.target(new DiscordQueue.Post(hook + "/", "{}", null, "7", r)));
		// A post nobody waits on goes to the URL as it is.
		assertEquals(hook + "/", DiscordSender.target(new DiscordQueue.Post(hook + "/", "{}", null)));
	}

	@Test
	public void theRecordedReplysMessageIdIsTheTopLevelId()
	{
		assertEquals(MESSAGE_ID, DiscordSender.messageId(gson, MESSAGE));
	}
}
