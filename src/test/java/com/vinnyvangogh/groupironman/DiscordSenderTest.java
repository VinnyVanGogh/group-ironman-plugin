package com.vinnyvangogh.groupironman;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import static com.vinnyvangogh.groupironman.DiscordWebhookTest.URL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The queue, rate limits, back-off and quiet hours, with a fake transport and clock: no network at all. */
public class DiscordSenderTest
{
	private static final long SEC = TimeUnit.SECONDS.toMillis(1);

	private long now = 1_000_000;
	private int hour = 12;
	private final List<DiscordQueue.Post> sent = new ArrayList<>();
	private final List<DiscordSender.Done> pending = new ArrayList<>();
	private final DiscordSender sender = new DiscordSender((post, done) ->
	{
		sent.add(post);
		pending.add(done);
	}, () -> now, () -> hour);

	private ListAppender<ILoggingEvent> logs;
	private Logger logger;

	@Before
	public void captureLogs()
	{
		logger = (Logger) LoggerFactory.getLogger(DiscordSender.class);
		logger.setLevel(Level.DEBUG);
		logs = new ListAppender<>();
		logs.start();
		logger.addAppender(logs);
	}

	@After
	public void releaseLogs()
	{
		logger.detachAppender(logs);
	}

	private void reply(int status, String retryAfter)
	{
		pending.remove(0).done(status, retryAfter);
	}

	@Test
	public void noUrlMeansNoNetwork()
	{
		sender.post(null, "{}", null);
		sender.post(Webhooks.of(new GroupIronmanConfig()
		{
		}).url(Webhook.DROPS), "{}", null);
		sender.pump();
		assertTrue(sent.isEmpty());
		assertEquals(0, sender.queued());
	}

	private static final String OTHER = "https://discord.com/api/webhooks/9/other-token";

	@Test
	public void eachWebhookHasItsOwnQueueSoOneBusyWebhookNeverHoldsAnother()
	{
		sender.post(URL, "a1", null);
		sender.post(URL, "a2", null);
		sender.post(OTHER, "b1", null);
		sender.pump();
		assertEquals("one in flight per webhook, both at once", 2, sent.size());
		assertEquals(3, sender.queued());
	}

	@Test
	public void a429OnOneWebhookBacksOffThatWebhookOnly()
	{
		sender.post(URL, "a1", null);
		sender.pump();
		reply(429, "30");
		sender.post(OTHER, "b1", null);
		sender.post(URL, "a2", null);
		now += SEC;
		sender.pump();
		assertEquals(2, sent.size());
		assertEquals("the other webhook still posts", "b1", sent.get(1).json);
		reply(204, null);
		sender.post(OTHER, "b2", null);
		now += SEC;
		sender.pump();
		assertEquals("b2", sent.get(2).json);
	}

	@Test
	public void theMinuteCapIsPerWebhook()
	{
		for (int i = 0; i < DiscordQueue.PER_MINUTE + 1; i++)
		{
			sender.post(URL, "a" + i, null);
			sender.post(OTHER, "b" + i, null);
		}
		for (int i = 0; i < 2 * DiscordQueue.PER_MINUTE; i++)
		{
			sender.pump();
			while (!pending.isEmpty())
			{
				reply(204, null);
			}
			now += SEC;
		}
		assertEquals("twenty a minute on each", 2 * DiscordQueue.PER_MINUTE, sent.size());
	}

	@Test
	public void postsGoOneAtATimeInOrder()
	{
		sender.post(URL, "a", null);
		sender.post(URL, "b", null);
		sender.pump();
		sender.pump();
		assertEquals("b waits for a", 1, sent.size());
		reply(204, null);
		sender.pump();
		assertEquals("b", sent.get(1).json);
	}

	@Test
	public void aFullQueueDropsTheOldest()
	{
		for (int i = 0; i < DiscordQueue.MAX_QUEUED + 5; i++)
		{
			sender.post(URL, "p" + i, null);
		}
		assertEquals(DiscordQueue.MAX_QUEUED, sender.queued());
		sender.pump();
		assertEquals("p5", sent.get(0).json);
	}

	@Test
	public void twentyAMinuteAtMost()
	{
		for (int i = 0; i < 30; i++)
		{
			sender.post(URL, "p" + i, null);
		}
		for (int i = 0; i < 30; i++)
		{
			sender.pump();
			if (!pending.isEmpty())
			{
				reply(204, null);
			}
			now += SEC;
		}
		assertEquals(DiscordQueue.PER_MINUTE, sent.size());
		now += 60 * SEC;
		sender.pump();
		assertEquals("the next minute sends again", DiscordQueue.PER_MINUTE + 1, sent.size());
	}

	@Test
	public void a429WaitsItsRetryAfterAndRetriesTheSamePost()
	{
		sender.post(URL, "a", null);
		sender.pump();
		reply(429, "2.5");
		sender.pump();
		now += 2 * SEC;
		sender.pump();
		assertEquals("still backing off", 1, sent.size());
		now += SEC;
		sender.pump();
		assertEquals(2, sent.size());
		assertEquals("a", sent.get(1).json);
	}

	@Test
	public void aServerErrorRetriesAFewTimesThenGivesUp()
	{
		sender.post(URL, "a", null);
		for (int i = 0; i < DiscordQueue.MAX_ATTEMPTS + 2; i++)
		{
			sender.pump();
			if (!pending.isEmpty())
			{
				reply(502, null);
			}
			now += DiscordQueue.RETRY_MS;
		}
		assertEquals(DiscordQueue.MAX_ATTEMPTS, sent.size());
		assertEquals(0, sender.queued());
	}

	@Test
	public void aDeletedWebhookIsNotRetried()
	{
		sender.post(URL, "a", null);
		sender.pump();
		reply(404, null);
		now += 10 * SEC;
		sender.pump();
		assertEquals(1, sent.size());
	}

	@Test
	public void quietHoursHoldPostsAndSendThemAfter()
	{
		sender.quietHours(22, 7);
		hour = 23;
		sender.post(URL, "a", null);
		sender.pump();
		hour = 3;
		sender.pump();
		assertTrue("held overnight", sent.isEmpty());
		assertEquals(1, sender.queued());
		hour = 7;
		sender.pump();
		assertEquals(1, sent.size());
	}

	@Test
	public void quietHoursAcrossMidnightAndOff()
	{
		assertTrue(DiscordQueue.quiet(23, 22, 7));
		assertTrue(DiscordQueue.quiet(0, 22, 7));
		assertFalse(DiscordQueue.quiet(7, 22, 7));
		assertTrue(DiscordQueue.quiet(13, 13, 14));
		assertFalse(DiscordQueue.quiet(14, 13, 14));
		assertFalse("equal hours: off", DiscordQueue.quiet(5, 0, 0));
	}

	@Test
	public void retryAfterIsSecondsPossiblyFractional()
	{
		assertEquals(2500, DiscordQueue.retryAfterMs("2.5"));
		assertEquals(1000, DiscordQueue.retryAfterMs("1"));
		assertEquals(-1, DiscordQueue.retryAfterMs(null));
		assertEquals(-1, DiscordQueue.retryAfterMs("soon"));
	}

	@Test
	public void theUrlIsNeverLogged()
	{
		sender.post(URL, "a", null);
		sender.pump();
		reply(0, null);
		now += DiscordQueue.RETRY_MS;
		sender.pump();
		reply(401, null);
		assertFalse("failures were logged", logs.list.isEmpty());
		for (ILoggingEvent e : logs.list)
		{
			assertFalse(e.getFormattedMessage().contains("webhooks"));
			assertFalse(e.getFormattedMessage().contains("AbC-def_GHI123"));
		}
	}
}
