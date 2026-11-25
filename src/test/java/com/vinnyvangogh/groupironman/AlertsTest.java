package com.vinnyvangogh.groupironman;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Group announcements and alerts: defaults, one alert per event, "everyone online" and dismissal. */
public class AlertsTest
{
	private static final GroupIronmanConfig DEFAULTS = new GroupIronmanConfig()
	{
	};

	private static GroupEvent announcement(String member, String text, long at)
	{
		return GroupEvent.of(GroupEvent.Type.ANNOUNCEMENT, at, member).text(text).build();
	}

	private static GroupEvent login(String member, long at)
	{
		return GroupEvent.of(GroupEvent.Type.ONLINE, at, member).text("online").world(301).build();
	}

	@Test
	public void announcementsAreOnAndLoginAlertsAreOffByDefault()
	{
		assertTrue(DEFAULTS.overlayAnnouncement());
		assertFalse(DEFAULTS.overlayOnline());
		assertFalse(DEFAULTS.overlayEveryoneOnline());
		assertFalse("nothing goes to Discord unasked", DEFAULTS.discordAnnouncement());
		EventSettings s = EventSettings.from(DEFAULTS);
		assertTrue(s.overlay.contains(GroupEvent.Type.ANNOUNCEMENT));
		assertFalse(s.overlay.contains(GroupEvent.Type.ONLINE));
		assertFalse(s.overlay.contains(GroupEvent.Type.EVERYONE_ONLINE));
	}

	@Test
	public void noQuietHoursByDefault()
	{
		assertFalse(ToastQueue.quiet(3, DEFAULTS.overlayQuietStart(), DEFAULTS.overlayQuietEnd()));
	}

	@Test
	public void anAnnouncementResentOnAPartyJoinIsOneAlert()
	{
		AlertDedup d = new AlertDedup();
		assertTrue(d.fresh(announcement("Ann", "Raid at 8", NOW), NOW + 5));
		assertFalse("the party resends it", d.fresh(announcement("Ann", "Raid at 8", NOW), NOW + 600_000));
		assertTrue("a new note with the same words is a new one", d.fresh(announcement("Ann", "Raid at 8", NOW + 9_000),
			NOW + 10_000));
		assertTrue("another member's", d.fresh(announcement("Bob", "Raid at 8", NOW), NOW + 10_000));
	}

	@Test
	public void twoDropsInTheSameMillisecondBothShow()
	{
		AlertDedup d = new AlertDedup();
		assertTrue(d.fresh(GroupEvent.drop(NOW, "Ann", "Dust devil", Collections.singletonMap(995, 5_000)), NOW));
		assertTrue("a second kill in the same barrage", d.fresh(GroupEvent.drop(NOW, "Ann", "Dust devil",
			Collections.singletonMap(1_319, 1)), NOW));
		assertFalse("the party resends the first", d.fresh(GroupEvent.drop(NOW, "Ann", "Dust devil",
			Collections.singletonMap(995, 5_000)), NOW + 60_000));
	}

	@Test
	public void aLoginTwoSourcesReportIsOneAlert()
	{
		AlertDedup d = new AlertDedup();
		assertTrue(d.fresh(login("Ann", NOW), NOW));
		assertFalse("the Group channel saw it a moment later", d.fresh(login("ann", NOW + 2_000), NOW + 2_000));
		assertTrue("a login the next hour is a new one", d.fresh(login("Ann", NOW + 3_600_000),
			NOW + 3_600_000));
	}

	@Test
	public void everyoneOnlineFiresOnTheChangeNotTheFirstLook()
	{
		EveryoneOnline e = new EveryoneOnline();
		List<String> members = Arrays.asList("Ann", "Bob", "Cy");
		assertEquals("starting with everyone here says nothing", 0, e.update(members, set("Ann", "Bob", "Cy")));
		assertEquals(0, e.update(members, set("Ann", "Bob", "Cy")));
		assertEquals(0, e.update(members, set("Ann", "Bob")));
		assertEquals("the last one arrives", 3, e.update(members, set("Ann", "Bob", "Cy")));
		assertEquals("and only once", 0, e.update(members, set("Ann", "Bob", "Cy")));
	}

	@Test
	public void aGroupOfOneIsNeverEveryone()
	{
		EveryoneOnline e = new EveryoneOnline();
		assertEquals(0, e.update(Collections.singletonList("Ann"), set()));
		assertEquals(0, e.update(Collections.singletonList("Ann"), set("Ann")));
	}

	@Test
	public void everyoneOnlineNeverTravelsButAnAnnouncementDoes()
	{
		assertFalse(GroupEvent.Type.EVERYONE_ONLINE.travels());
		assertTrue(GroupEvent.Type.ANNOUNCEMENT.travels());
	}

	@Test
	public void dismissClearsBannersButNotTheGroupBox()
	{
		ToastQueue q = new ToastQueue();
		q.add(announcement("Ann", "one", NOW), NOW);
		q.add(announcement("Bob", "two", NOW + 1), NOW + 1);
		assertTrue(q.dismissNewest());
		List<ToastQueue.Toast> left = q.visible(NOW + 2, 8_000, 3, e -> true);
		assertEquals(1, left.size());
		assertEquals("one", left.get(0).event.text);
		assertEquals(1, q.dismissAll());
		assertTrue(q.visible(NOW + 3, 8_000, 3, e -> true).isEmpty());
		assertFalse("nothing left to dismiss", q.dismissNewest());
		assertEquals(2, q.recent(5).size());
	}

	@Test
	public void anAnnouncementReadsAsItsText()
	{
		assertEquals("Raid at 8", EventText.describe(announcement("Ann", "Raid at 8", NOW), new FakeItems()));
	}

	private static Set<String> set(String... names)
	{
		return new HashSet<>(Arrays.asList(names));
	}
}
