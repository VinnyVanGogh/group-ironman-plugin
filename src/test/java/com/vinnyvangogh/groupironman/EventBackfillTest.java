package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.all;
import static com.vinnyvangogh.groupironman.EventFixtures.items;
import static com.vinnyvangogh.groupironman.EventFixtures.settings;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Board 2026-11-17: events recorded while not synced reach everyone once, in order, with no toast or post. */
public class EventBackfillTest
{
	/** The Log's feed ages entries against the real clock, so these events are made relative to it. */
	private static final long NOW = System.currentTimeMillis();
	private static final long HOUR = TimeUnit.HOURS.toMillis(1);
	private final Gson gson = new Gson();
	private final FakeItems lookup = new FakeItems();

	private static GroupEvent run(GroupEvent.Type type, long at, String member, String text, long number)
	{
		return GroupEvent.of(type, at, member).text(text).number(number).build();
	}

	/** Ark's client: the events he recorded while not synced are in his own Log. */
	private GroupRoster arkOffline()
	{
		GroupRoster ark = new GroupRoster();
		ark.feed().add(run(GroupEvent.Type.ACTIVITY_START, NOW - 7 * HOUR, "GroupArk", "Fight Caves", 1));
		ark.feed().add(run(GroupEvent.Type.ACTIVITY_DEATH, NOW - 6 * HOUR, "GroupArk", "Fight Caves", 38));
		ark.feed().add(run(GroupEvent.Type.SLAYER, NOW - 5 * HOUR, "GroupArk", "Abyssal demons", 130));
		ark.feed().add(run(GroupEvent.Type.LEVEL_UP, NOW - 4 * HOUR, "GroupArk", "Slayer", 70));
		ark.events().add(GroupEvent.drop(NOW - 3 * HOUR, "GroupArk", "Zulrah", items(FakeItems.WHIP, 1)));
		return ark;
	}

	private List<GroupEvent> wire(List<GroupEvent> sent)
	{
		List<GroupEvent> out = new ArrayList<>();
		for (String payload : GroupCodec.encodeEvents(gson, sent))
		{
			out.addAll(GroupCodec.decode(gson, payload).events);
		}
		return out;
	}

	private static final class Receiver
	{
		final GroupRoster roster = new GroupRoster();
		final List<GroupEvent> toasts = new ArrayList<>();
		final List<GroupEvent> discord = new ArrayList<>();
		final List<GroupEvent> logged = new ArrayList<>();
		final EventRouter router;
		final ItemLookup items;

		Receiver(ItemLookup items)
		{
			this.items = items;
			router = new EventRouter(discord::add, toasts::add, e ->
			{
			}, e -> roster.feed().add(e));
		}

		/** What GroupService.receiveEvents does with one member's event, minus the client plumbing. */
		void receive(GroupEvent wire, long now)
		{
			receive(wire, now, 1);
		}

		/** One party message as GroupService.receiveEvents takes it: each event, knowing how many came together. */
		void message(String payload, long now)
		{
			GroupCodec.Decoded d = GroupCodec.decode(new Gson(), payload);
			if (d.kind != GroupCodec.Kind.EVENTS)
			{
				// Banks, carried items, storage and extras fill the cards; GroupService never routes them as events.
				return;
			}
			d.events.forEach(e -> receive(e, now, d.events.size()));
		}

		void receive(GroupEvent wire, long now, int batch)
		{
			// The wire carries no member: the receiver files it under the party member's name.
			GroupEvent e = wire.member.isEmpty() ? wire.withMember("GroupArk") : wire;
			EventBackfill.Arrival a = EventBackfill.classify(roster.feed(), e, now, batch);
			if (a == EventBackfill.Arrival.DUPLICATE)
			{
				return;
			}
			if (a == EventBackfill.Arrival.BACKFILL)
			{
				router.backfill(e, settings(all(), all()), items);
			}
			else
			{
				router.route(e, false, settings(all(), all()), items);
			}
		}
	}

	@Test
	public void offlineEventsUploadOnceInTimeOrderWithTheirOwnTimes()
	{
		List<GroupEvent> up = wire(EventBackfill.upload(arkOffline(), "GroupArk", NOW));
		assertEquals(5, up.size());

		Receiver ship = new Receiver(lookup);
		up.forEach(e -> ship.receive(e.withMember("GroupArk"), NOW));

		List<GroupEvent> feed = ship.roster.feed().entries();
		assertEquals(5, feed.size());
		for (int i = 1; i < feed.size(); i++)
		{
			assertTrue("newest first", feed.get(i - 1).at > feed.get(i).at);
		}
		assertEquals(NOW - 3 * HOUR, feed.get(0).at);
		assertEquals(NOW - 7 * HOUR, feed.get(4).at);
	}

	@Test
	public void backfilledEventsNeverToastOrPostToDiscord()
	{
		Receiver ship = new Receiver(lookup);
		wire(EventBackfill.upload(arkOffline(), "GroupArk", NOW)).forEach(e -> ship.receive(e, NOW));
		assertEquals(5, ship.roster.feed().entries().size());
		assertTrue(ship.toasts.isEmpty());
		assertTrue(ship.discord.isEmpty());
	}

	@Test
	public void aLiveEventStillToasts()
	{
		Receiver ship = new Receiver(lookup);
		ship.receive(run(GroupEvent.Type.LEVEL_UP, NOW - 5_000, "GroupArk", "Fishing", 60), NOW);
		assertEquals(1, ship.toasts.size());
		assertEquals(1, ship.roster.feed().entries().size());
	}

	@Test
	public void aDuplicateUploadIsIgnored()
	{
		Receiver ship = new Receiver(lookup);
		List<GroupEvent> up = wire(EventBackfill.upload(arkOffline(), "GroupArk", NOW));
		up.forEach(e -> ship.receive(e, NOW));
		up.forEach(e -> ship.receive(e, NOW + 60_000));
		up.forEach(e -> ship.receive(e, NOW + 120_000));
		assertEquals(5, ship.roster.feed().entries().size());
	}

	@Test
	public void aLiveEventThenItsResendDoesNotDouble()
	{
		Receiver ship = new Receiver(lookup);
		GroupEvent e = run(GroupEvent.Type.LEVEL_UP, NOW, "GroupArk", "Fishing", 60);
		ship.receive(e, NOW + 1_000);
		ship.receive(e, NOW + 10 * 60_000);
		assertEquals(1, ship.roster.feed().entries().size());
		assertEquals(1, ship.toasts.size());
	}

	// ---- RUNELITE-510: only real events raise banners ----

	/** Ark's client with events from the last minute: fresh enough that age alone would call them live. */
	private GroupRoster arkJustNow()
	{
		GroupRoster ark = new GroupRoster();
		ark.feed().add(run(GroupEvent.Type.LEVEL_UP, NOW - 50_000, "GroupArk", "Slayer", 71));
		ark.feed().add(run(GroupEvent.Type.SLAYER, NOW - 40_000, "GroupArk", "Abyssal demons", 130));
		ark.feed().add(run(GroupEvent.Type.LEVEL_UP, NOW - 30_000, "GroupArk", "Mining", 52));
		ark.events().add(GroupEvent.drop(NOW - 20_000, "GroupArk", "Zulrah", items(FakeItems.WHIP, 1)));
		ark.events().add(GroupEvent.drop(NOW - 10_000, "GroupArk", "Vorkath", items(FakeItems.WHIP, 1)));
		return ark;
	}

	@Test
	public void joiningSyncRaisesNoBannersEvenForFreshEvents()
	{
		Receiver ship = new Receiver(lookup);
		List<GroupEvent> mine = EventBackfill.upload(arkJustNow(), "GroupArk", NOW);
		GroupCodec.encodeEvents(gson, mine).forEach(p -> ship.message(p, NOW));
		assertEquals(5, ship.roster.feed().entries().size());
		assertEquals(0, ship.toasts.size());
		assertEquals(0, ship.discord.size());
	}

	@Test
	public void aSyncBurstOfFullInventoriesAndBanksRaisesNoBanners()
	{
		Receiver ship = new Receiver(lookup);
		for (int i = 0; i < 50; i++)
		{
			ship.message(GroupCodec.encodeCarried(gson, items(FakeItems.SHARK, 20 + i, FakeItems.COAL, 5),
				items(FakeItems.WHIP, 1)), NOW + i * 600);
		}
		GroupCodec.encodeBank(gson, new BankSnapshot(NOW, items(FakeItems.SHARK, 500, FakeItems.WHIP, 3)))
			.forEach(p -> ship.message(p, NOW));
		// A reconnect resends the recent events as one batch on top.
		GroupCodec.encodeEvents(gson, EventBackfill.upload(arkJustNow(), "GroupArk", NOW))
			.forEach(p -> ship.message(p, NOW));
		assertEquals(0, ship.toasts.size());
	}

	@Test
	public void aRealDropRaisesExactlyOneBanner()
	{
		Receiver ship = new Receiver(lookup);
		GroupEvent drop = GroupEvent.drop(NOW - 1_000, "GroupArk", "Zulrah", items(FakeItems.WHIP, 1));
		GroupCodec.encodeEvents(gson, Collections.singletonList(drop)).forEach(p -> ship.message(p, NOW));
		assertEquals(1, ship.toasts.size());
	}

	@Test
	public void aRepeatedMessageRaisesOneBanner()
	{
		Receiver ship = new Receiver(lookup);
		GroupEvent drop = GroupEvent.drop(NOW - 1_000, "GroupArk", "Zulrah", items(FakeItems.WHIP, 1));
		List<String> payloads = GroupCodec.encodeEvents(gson, Collections.singletonList(drop));
		payloads.forEach(p -> ship.message(p, NOW));
		payloads.forEach(p -> ship.message(p, NOW + 500));
		payloads.forEach(p -> ship.message(p, NOW + 5_000));
		assertEquals(1, ship.toasts.size());
		assertEquals(1, ship.roster.feed().entries().size());
	}

	@Test
	public void aBatchIsCatchUpAndALoneFreshEventIsLive()
	{
		DropLog feed = new GroupRoster().feed();
		GroupEvent fresh = run(GroupEvent.Type.LEVEL_UP, NOW - 1_000, "GroupArk", "Fishing", 60);
		assertEquals(EventBackfill.Arrival.LIVE, EventBackfill.classify(feed, fresh, NOW, 1));
		assertEquals(EventBackfill.Arrival.BACKFILL, EventBackfill.classify(feed, fresh, NOW, 2));
		assertEquals(EventBackfill.Arrival.BACKFILL,
			EventBackfill.classify(feed, run(GroupEvent.Type.LEVEL_UP, NOW - 3 * 60_000, "GroupArk", "Fishing", 61),
				NOW, 1));
	}

	@Test
	public void aLateStorageVisitIsHistory()
	{
		assertFalse(EventBackfill.late(NOW - 30_000, NOW));
		assertTrue(EventBackfill.late(NOW - EventBackfill.LIVE_MS - 1, NOW));
	}

	@Test
	public void uploadHoldsOnlyOwnShareableEvents()
	{
		GroupRoster ark = arkOffline();
		ark.feed().add(run(GroupEvent.Type.LEVEL_UP, NOW - HOUR, "GroupShip", "Mining", 40));
		ark.feed().add(run(GroupEvent.Type.STORAGE_SUMMARY, NOW - HOUR, "GroupArk", "", 0));
		List<GroupEvent> up = EventBackfill.upload(ark, "GroupArk", NOW);
		for (GroupEvent e : up)
		{
			assertEquals("GroupArk", e.member);
		}
		assertFalse(up.stream().anyMatch(e -> e.type == GroupEvent.Type.STORAGE_SUMMARY));
	}

	@Test
	public void uploadSkipsWhatIsOlderThanTheResendWindow()
	{
		GroupRoster ark = new GroupRoster();
		ark.feed().add(run(GroupEvent.Type.LEVEL_UP, NOW - TimeUnit.DAYS.toMillis(9), "GroupArk", "Mining", 40));
		assertTrue(EventBackfill.upload(ark, "GroupArk", NOW).isEmpty());
	}

	// ---- The roster check ----

	private static final GroupMembers TEAM = GroupMembers.of("Iron Pals",
		Arrays.asList("GroupArk", "GroupShip", "GroupTrashy"));

	@Test
	public void aNonRosterEntryIsPurgedFromEveryLog()
	{
		GroupRoster r = new GroupRoster();
		r.feed().add(run(GroupEvent.Type.LEVEL_UP, NOW - HOUR, "Stranger", "Mining", 40));
		r.feed().add(run(GroupEvent.Type.LEVEL_UP, NOW - HOUR, "GroupArk", "Mining", 40));
		r.events().add(GroupEvent.drop(NOW - HOUR, "Stranger", "Zulrah", items(FakeItems.WHIP, 1)));
		r.putStorage(new StorageChange(NOW - HOUR, "Stranger", items(FakeItems.COAL, 1), items(), items()));
		r.heard("Stranger", NOW);

		Set<String> hit = r.purge(n -> EventBackfill.offRoster(TEAM, "GroupShip", n));
		assertEquals(1, hit.size());
		assertTrue(hit.contains("Stranger"));
		assertEquals(1, r.feed().entries().size());
		assertEquals("GroupArk", r.feed().entries().get(0).member);
		assertTrue(r.events().entries().isEmpty());
		assertTrue(r.log().entries().isEmpty());
	}

	@Test
	public void aRosterMemberIsNeverPurged()
	{
		GroupRoster r = new GroupRoster();
		r.feed().add(run(GroupEvent.Type.LEVEL_UP, NOW, "GroupTrashy", "Mining", 40));
		r.feed().add(run(GroupEvent.Type.LEVEL_UP, NOW - 1, "grouptrashy", "Mining", 41));
		r.purge(n -> EventBackfill.offRoster(TEAM, "GroupShip", n));
		assertEquals(2, r.feed().entries().size());
	}

	@Test
	public void theLocalPlayerAndTheGroupsOwnEntriesAreNeverPurged()
	{
		GroupRoster r = new GroupRoster();
		r.feed().add(run(GroupEvent.Type.LEVEL_UP, NOW, "Me", "Mining", 40));
		r.feed().add(run(GroupEvent.Type.EVERYONE_ONLINE, NOW - 1, "Iron Pals", "", 0));
		r.feed().add(run(GroupEvent.Type.STORAGE_MINIMUM, NOW - 2, "Group storage", "Shark", 0));
		r.purge(n -> EventBackfill.offRoster(TEAM, "Me", n));
		assertEquals(3, r.feed().entries().size());
	}

	@Test
	public void nothingIsPurgedWhileTheRosterIsUnknown()
	{
		GroupRoster r = new GroupRoster();
		r.feed().add(run(GroupEvent.Type.LEVEL_UP, NOW, "Anyone", "Mining", 40));
		assertTrue(r.purge(n -> EventBackfill.offRoster(GroupMembers.UNKNOWN, "Me", n)).isEmpty());
		assertEquals(1, r.feed().entries().size());
	}

	@Test
	public void theCheckRemovesItFromTheClientThatHasIt()
	{
		// Ark's own client holds an entry filed under a non-member: it purges its own copy too.
		GroupRoster ark = arkOffline();
		ark.feed().add(run(GroupEvent.Type.LEVEL_UP, NOW - HOUR, "Stranger", "Mining", 40));
		ark.purge(n -> EventBackfill.offRoster(TEAM, "GroupArk", n));
		assertTrue(ark.feed().entries().stream().noneMatch(e -> e.member.equals("Stranger")));
		assertEquals(4, ark.feed().entries().size());
	}

	@Test
	public void aMemberNotSyncingShowsWhenTheyLastSynced()
	{
		GroupRoster r = new GroupRoster();
		// The game says GroupOtt is online on W615, but their client last sent anything 7 hours ago.
		r.heard("GroupOtt", NOW - 7 * HOUR);
		List<PanelModel.MemberCard> cards = PanelModel.members(r, GamePresenceTest.ROSTER.names(), RegionNames.parse(""),
			lookup, "GroupShip", GamePresenceTest.twoOfFive(), NOW);
		String status = cards.stream().filter(c -> c.name.equals("GroupOtt")).findFirst().get().status;
		assertEquals("Online - W615 (not syncing, last synced 7h ago)", status);
	}
}
