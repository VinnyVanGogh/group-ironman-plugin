package com.vinnyvangogh.groupironman;

import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PresenceDebounceTest
{
	private static final long MIN = TimeUnit.MINUTES.toMillis(1);
	private final PresenceDebounce p = new PresenceDebounce();

	@Test
	public void loginIsOnlineWithTheWorld()
	{
		GroupEvent e = p.login(NOW, 302, "Alex");
		assertEquals(GroupEvent.Type.ONLINE, e.type);
		assertEquals("online", e.text);
		assertEquals(302, e.world);
		assertEquals("Alex: is online on world 302", EventText.sentence(e, new FakeItems()));
	}

	@Test
	public void aHopOrQuickRelogSaysNothing()
	{
		p.login(NOW, 302, "Alex");
		p.logout(NOW + MIN);
		assertNull(p.poll(NOW + 2 * MIN, "Alex"));
		assertNull("back within 10 minutes", p.login(NOW + 9 * MIN, 330, "Alex"));
		assertNull("the logout was cancelled", p.poll(NOW + 60 * MIN, "Alex"));
	}

	@Test
	public void offlineOnceTheLogoutSettles()
	{
		p.login(NOW, 302, "Alex");
		p.logout(NOW + MIN);
		assertNull(p.poll(NOW + MIN + PresenceDebounce.SETTLE_MS - 1, "Alex"));
		GroupEvent off = p.poll(NOW + MIN + PresenceDebounce.SETTLE_MS, "Alex");
		assertEquals("offline", off.text);
		assertEquals("stamped with the logout, not the poll", NOW + MIN, off.at);
		assertNull("only once", p.poll(NOW + 120 * MIN, "Alex"));
		assertEquals("online", p.login(NOW + 130 * MIN, 302, "Alex").text);
	}

	@Test
	public void backAfterDays()
	{
		long day = TimeUnit.DAYS.toMillis(1);
		assertNull(PresenceDebounce.back(0, NOW, 7, "Alex"));
		assertNull(PresenceDebounce.back(NOW - 6 * day, NOW, 7, "Alex"));
		GroupEvent back = PresenceDebounce.back(NOW - 9 * day - 5, NOW, 7, "Alex");
		assertEquals(GroupEvent.Type.BACK, back.type);
		assertEquals(9, back.number);
	}
}
