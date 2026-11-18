package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/** The live socket's rules, adversarial first: refusals, quota, stale callbacks, then the minimal-traffic rules. */
public class ServerLinkTest
{
	private static final String KEY = "0123456789abcdef0123456789abcdef";
	private final Gson gson = new Gson();

	private final List<FakeSocket> dialed = new ArrayList<>();
	private final List<String> received = new ArrayList<>();
	private final List<ServerLink.State> states = new ArrayList<>();
	private int deleted;
	private int joined;
	private long now = 1_000_000;
	private final List<Timer> timers = new ArrayList<>();
	private ServerLink link;

	private static final class Timer
	{
		final long at;
		final Runnable task;

		Timer(long at, Runnable task)
		{
			this.at = at;
			this.task = task;
		}
	}

	private final class FakeSocket implements ServerLink.Socket
	{
		final String url;
		final String auth;
		final String name;
		final ServerLink.Events events;
		final List<String> sent = new ArrayList<>();
		int closedWith = -1;

		FakeSocket(String url, String auth, String name, ServerLink.Events events)
		{
			this.url = url;
			this.auth = auth;
			this.name = name;
			this.events = events;
		}

		@Override
		public boolean send(String text)
		{
			sent.add(text);
			return true;
		}

		@Override
		public void close(int code, String reason)
		{
			closedWith = code;
		}

		void hello(String... online)
		{
			events.opened(this);
			JsonObject o = new JsonObject();
			o.addProperty("t", "hi");
			JsonArray n = new JsonArray();
			for (String s : online)
			{
				n.add(s);
			}
			o.add("n", n);
			events.text(this, gson.toJson(o));
		}
	}

	@Before
	public void setUp()
	{
		link = new ServerLink(gson, (url, auth, name, events) ->
		{
			FakeSocket s = new FakeSocket(url, auth, name, events);
			dialed.add(s);
			return s;
		}, new ServerLink.Clock()
		{
			@Override
			public long now()
			{
				return now;
			}

			@Override
			public void schedule(Runnable task, long delayMs)
			{
				timers.add(new Timer(now + delayMs, task));
			}
		}, new ServerLink.Listener()
		{
			@Override
			public void payload(String from, String payload)
			{
				received.add(from + ":" + payload);
			}

			@Override
			public void connected(List<String> online)
			{
			}

			@Override
			public void joined(String name)
			{
				joined++;
			}

			@Override
			public void left(String name)
			{
			}

			@Override
			public void deleted()
			{
				deleted++;
			}

			@Override
			public void stateChanged(ServerLink.State state)
			{
				states.add(state);
			}
		});
	}

	/** Runs every timer due by {@code now + ms}, in order, advancing the clock. */
	private void advance(long ms)
	{
		long end = now + ms;
		while (true)
		{
			Timer next = timers.stream().filter(t -> t.at <= end).min(Comparator.comparingLong(t -> t.at)).orElse(null);
			if (next == null)
			{
				break;
			}
			timers.remove(next);
			now = Math.max(now, next.at);
			next.task.run();
		}
		now = end;
	}

	private FakeSocket connect()
	{
		link.start(KEY, "Alice");
		FakeSocket s = dialed.get(dialed.size() - 1);
		s.hello("Alice", "Bob");
		return s;
	}

	private static String stats(int hp)
	{
		return "{\"v\":1,\"k\":\"stats\",\"hp\":" + hp + ",\"ts\":1}";
	}

	// --- Adversarial and failure cases ---

	@Test
	public void neverDialsWithoutAValidKeyOrName()
	{
		link.start("", "Alice");
		link.start("not a key", "Alice");
		link.start(KEY, "  ");
		link.start(KEY, null);
		assertTrue(dialed.isEmpty());
		assertEquals(ServerLink.State.OFF, link.state());
	}

	@Test
	public void theKeyItselfNeverLeavesTheClient()
	{
		link.start(KEY, "Alice");
		FakeSocket s = dialed.get(0);
		ServerKeys keys = ServerKeys.of(KEY);
		assertFalse(s.url.contains(KEY));
		assertFalse(s.auth.contains(KEY));
		assertEquals(ServerLink.BASE_URL + "/v1/groups/" + keys.groupId + "/live", s.url);
		assertEquals(keys.groupAuth, s.auth);
	}

	@Test
	public void aWrongKeyIsRefusedAndNotRetried()
	{
		link.start(KEY, "Alice");
		dialed.get(0).events.failed(dialed.get(0), 401, 0);
		assertEquals(ServerLink.State.REFUSED, link.state());
		advance(60 * 60_000);
		assertEquals(1, dialed.size());
		// Same key and name again: the refusal stands until something changes.
		link.start(KEY, "Alice");
		assertEquals(1, dialed.size());
		assertFalse(link.send(stats(1)));
	}

	@Test
	public void signedInElsewhereIsRefused()
	{
		FakeSocket s = connect();
		s.events.closed(s, ServerLink.CLOSE_REPLACED);
		assertEquals(ServerLink.State.REFUSED, link.state());
		advance(60 * 60_000);
		assertEquals(1, dialed.size());
	}

	@Test
	public void serverErrorsBackOffDoublingToFiveMinutesAndHonourRetryAfter()
	{
		link.start(KEY, "Alice");
		dialed.get(0).events.failed(dialed.get(0), 503, 30_000);
		assertEquals(ServerLink.State.WAITING, link.state());
		advance(29_000);
		assertEquals(1, dialed.size());
		advance(1_000);
		assertEquals(2, dialed.size());
		long[] expected = {2_000, 4_000, 8_000, 16_000, 32_000, 64_000, 128_000, 256_000, 300_000, 300_000};
		for (long wait : expected)
		{
			FakeSocket last = dialed.get(dialed.size() - 1);
			last.events.failed(last, 0, 0);
			int before = dialed.size();
			advance(wait - 1);
			assertEquals("redialed early, wait " + wait, before, dialed.size());
			advance(1);
			assertEquals("did not redial, wait " + wait, before + 1, dialed.size());
		}
	}

	@Test
	public void aQuotaCloseWaitsUntilUtcMidnight()
	{
		now = 24L * 60 * 60 * 1000 * 20_000 + 23L * 60 * 60 * 1000; // 23:00 UTC
		FakeSocket s = connect();
		s.events.closed(s, ServerLink.CLOSE_QUOTA);
		assertEquals(ServerLink.State.WAITING, link.state());
		advance(59 * 60_000);
		assertEquals(1, dialed.size());
		advance(2 * 60_000);
		assertEquals(2, dialed.size());
	}

	@Test
	public void aStaleSocketsCallbacksDoNothing()
	{
		link.start(KEY, "Alice");
		FakeSocket old = dialed.get(0);
		link.stop(false);
		link.start(KEY, "Bob");
		FakeSocket current = dialed.get(1);
		old.hello("Alice");
		assertEquals(ServerLink.State.CONNECTING, link.state());
		old.events.failed(old, 401, 0);
		assertEquals(ServerLink.State.CONNECTING, link.state());
		current.hello("Bob");
		assertTrue(link.connected());
	}

	@Test
	public void groupDeletedTurnsOffAndTellsTheListener()
	{
		FakeSocket s = connect();
		s.events.closed(s, ServerLink.CLOSE_DELETED);
		assertEquals(1, deleted);
		assertEquals(ServerLink.State.OFF, link.state());
		advance(60 * 60_000);
		assertEquals(1, dialed.size());
	}

	@Test
	public void malformedFramesAreIgnored()
	{
		FakeSocket s = connect();
		s.events.text(s, "not json");
		s.events.text(s, "{}");
		s.events.text(s, "{\"t\":\"b\",\"m\":\"x\"}");
		s.events.text(s, "{\"t\":\"b\",\"m\":[1,{\"f\":[1],\"d\":\"x\"},{\"f\":\"Bob\"}]}");
		s.events.text(s, "{\"t\":\"j\",\"n\":{}}");
		assertTrue(received.isEmpty());
		assertTrue(link.connected());
	}

	@Test
	public void nothingIsSentUntilConnected()
	{
		link.start(KEY, "Alice");
		assertFalse(link.send(stats(1)));
		dialed.get(0).hello("Alice");
		assertTrue(dialed.get(0).sent.isEmpty());
	}

	// --- Minimal traffic (Board 2026-11-18) ---

	@Test
	public void atMostOneFrameEveryFiveSecondsAndTheLatestStateWins()
	{
		FakeSocket s = connect();
		link.send(stats(1));
		assertEquals(1, s.sent.size());
		link.send(stats(2));
		link.send(stats(3));
		link.send("{\"v\":1,\"k\":\"events\",\"e\":[1],\"ts\":2}");
		assertEquals(1, s.sent.size());
		advance(ServerLink.MIN_GAP_MS - 1);
		assertEquals(1, s.sent.size());
		advance(1);
		assertEquals(2, s.sent.size());
		JsonObject frame = gson.fromJson(s.sent.get(1), JsonObject.class);
		JsonArray d = frame.getAsJsonArray("d");
		// stats(2) was replaced by stats(3); the event is kept.
		assertEquals(2, d.size());
		assertEquals(stats(3), d.get(0).getAsString());
		assertTrue(d.get(1).getAsString().contains("events"));
	}

	@Test
	public void anUnchangedStateIsNeverSentTwice()
	{
		FakeSocket s = connect();
		link.send(stats(1));
		advance(60_000);
		link.send(stats(1));
		advance(60_000);
		assertEquals(1, s.sent.size());
		// Back to an older state after a change goes out again.
		link.send(stats(2));
		advance(60_000);
		link.send(stats(1));
		advance(60_000);
		assertEquals(3, s.sent.size());
	}

	@Test
	public void bankPartsAreSlottedByPart()
	{
		assertEquals("bank:0", link.slot("{\"v\":1,\"k\":\"bank\",\"p\":0,\"ts\":1}"));
		assertEquals("bank:1", link.slot("{\"v\":1,\"k\":\"bank\",\"p\":1,\"ts\":1}"));
		assertEquals("stats", link.slot(stats(1)));
		assertEquals(null, link.slot("{\"v\":1,\"k\":\"events\",\"ts\":1}"));
		assertEquals(null, link.slot("garbage"));
	}

	@Test
	public void queuedEventsAreCapped()
	{
		FakeSocket s = connect();
		link.send(stats(0));
		for (int i = 0; i < ServerLink.MAX_QUEUED + 50; i++)
		{
			link.send("{\"v\":1,\"k\":\"events\",\"i\":" + i + ",\"ts\":1}");
		}
		int total = 0;
		for (int i = 0; i < 20; i++)
		{
			advance(ServerLink.MIN_GAP_MS);
		}
		for (int i = 1; i < s.sent.size(); i++)
		{
			JsonArray d = gson.fromJson(s.sent.get(i), JsonObject.class).getAsJsonArray("d");
			assertTrue(d.size() <= ServerLink.MAX_BATCH);
			total += d.size();
		}
		assertEquals(ServerLink.MAX_QUEUED, total);
		// The oldest were dropped: the first one left is number 50.
		String first = gson.fromJson(s.sent.get(1), JsonObject.class).getAsJsonArray("d").get(0).getAsString();
		assertTrue(first, first.contains("\"i\":50,"));
	}

	@Test
	public void logoutSendsTheLastLiveStateFlaggedToKeep()
	{
		FakeSocket s = connect();
		link.send(stats(7));
		link.stop(true);
		assertEquals(1000, s.closedWith);
		JsonObject last = gson.fromJson(s.sent.get(s.sent.size() - 1), JsonObject.class);
		assertEquals(1, last.get("p").getAsInt());
		assertEquals(Collections.singletonList(stats(7)),
			Collections.singletonList(last.getAsJsonArray("d").get(0).getAsString()));
		assertEquals(ServerLink.State.OFF, link.state());
	}

	@Test
	public void relaysAndPresenceReachTheListener()
	{
		FakeSocket s = connect();
		assertTrue(link.online("bob"));
		s.events.text(s, "{\"t\":\"b\",\"m\":[{\"f\":\"Bob\",\"d\":\"" + stats(5).replace("\"", "\\\"") + "\"}]}");
		assertEquals(Collections.singletonList("Bob:" + stats(5)), received);
		s.events.text(s, "{\"t\":\"j\",\"n\":\"Carol\"}");
		assertEquals(1, joined);
		assertTrue(link.online("Carol"));
		s.events.text(s, "{\"t\":\"l\",\"n\":\"Carol\"}");
		assertFalse(link.online("Carol"));
	}

	@Test
	public void startIsIdempotentForTheSameKeyAndName()
	{
		connect();
		link.start(KEY, "Alice");
		link.start(KEY, "Alice");
		assertEquals(1, dialed.size());
		assertTrue(link.connected());
		link.start(KEY, "Bob");
		assertEquals(2, dialed.size());
		assertEquals(1000, dialed.get(0).closedWith);
	}
}
