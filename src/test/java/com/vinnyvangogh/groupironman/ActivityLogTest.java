package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.items;
import static com.vinnyvangogh.groupironman.FakeItems.SHARK;
import static com.vinnyvangogh.groupironman.FakeItems.TROUT;
import static com.vinnyvangogh.groupironman.FakeItems.WHIP;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The sidebar Log: every event type lands in it, search, filters, sections, the bound and a restart. */
public class ActivityLogTest
{
	private static final long HOUR = TimeUnit.HOURS.toMillis(1);
	private static final long DAY = TimeUnit.DAYS.toMillis(1);
	private static final ZoneId UTC = ZoneOffset.UTC;
	/** Monday 10 Nov 2025, noon UTC, so "today" has room either side. */
	private static final long NOW = 1_762_776_000_000L;
	private static final Gson GSON = new Gson();

	private final FakeItems items = new FakeItems();

	private static GroupEvent drop(long at, String member, String npc, int id)
	{
		return GroupEvent.drop(at, member, npc, items(id, 1));
	}

	private static GroupEvent death(long at, String member, String place)
	{
		return GroupEvent.of(GroupEvent.Type.DEATH, at, member).location(place).build();
	}

	private static GroupEvent level(long at, String member, String skill, int lvl)
	{
		return GroupEvent.of(GroupEvent.Type.LEVEL_UP, at, member).text(skill).number(lvl).build();
	}

	/** Newest first, as the feed keeps them. */
	private List<GroupEvent> sample()
	{
		return Arrays.asList(
			level(NOW - HOUR, "Sam", "Attack", 80),
			death(NOW - 2 * HOUR, "Alex", "Lumbridge"),
			drop(NOW - 3 * HOUR, "Alex", "Vorkath", WHIP),
			GroupEvent.of(GroupEvent.Type.STORAGE_SUMMARY, NOW - 2 * DAY, "Sam").items(items(SHARK, 5))
				.taken(items(TROUT, 15)).build(),
			drop(NOW - 10 * DAY, "Sam", "Zulrah", SHARK));
	}

	// ---- Every type lands in the log, whatever the outputs ----

	@Test
	public void everyTypeIsLoggedWithEveryOutputOff()
	{
		List<GroupEvent> logged = new ArrayList<>();
		EventRouter router = new EventRouter(e ->
		{
		}, e ->
		{
		}, e ->
		{
		}, logged::add);
		EventSettings off = EventFixtures.settings(EventFixtures.none(), EventFixtures.none());
		for (GroupEvent.Type t : GroupEvent.Type.values())
		{
			router.route(EventFixtures.sample(t), true, off, items);
			router.route(EventFixtures.sample(t).withMember("Sam"), false, off, items);
		}
		EnumSet<GroupEvent.Type> seen = EnumSet.noneOf(GroupEvent.Type.class);
		for (GroupEvent e : logged)
		{
			seen.add(e.type);
		}
		assertEquals(EnumSet.allOf(GroupEvent.Type.class), seen);
		// Yours and members' alike.
		assertEquals(2 * GroupEvent.Type.values().length, logged.size());
	}

	@Test
	public void theLogGetsWhatTheThresholdsLeave()
	{
		List<GroupEvent> logged = new ArrayList<>();
		EventRouter router = new EventRouter(e ->
		{
		}, e ->
		{
		}, e ->
		{
		}, logged::add);
		EventSettings s = EventFixtures.settings(EventFixtures.none(), EventFixtures.none());
		// 3 sharks is 2,400 gp, under the 1M drop minimum: no valuable drop, so nothing logged.
		router.route(GroupEvent.drop(NOW, "Alex", "Zulrah", items(SHARK, 3)), true, s, items);
		assertTrue(logged.isEmpty());
	}

	@Test
	public void everyKindIsDetectedForTheLogButNotWhenOff()
	{
		EventSettings s = EventFixtures.settings(EventFixtures.none(), EventFixtures.none());
		for (GroupEvent.Type t : GroupEvent.Type.values())
		{
			assertFalse(s.wants(t));
			assertTrue(s.detects(t));
			assertFalse(EventSettings.OFF.detects(t));
		}
	}

	// ---- Search ----

	@Test
	public void searchMatchesMemberItemPlaceAndText()
	{
		List<GroupEvent> all = sample();
		assertEquals(3, filter(all, "sam").size());
		assertEquals(1, filter(all, "abyssal WHIP").size());
		assertEquals(1, filter(all, "lumbridge").size());
		assertEquals(1, filter(all, "attack level").size());
		// A storage summary's withdrawals are searchable too.
		assertEquals(1, filter(all, "trout").size());
		assertTrue(filter(all, "nothing like this").isEmpty());
		assertEquals(all.size(), filter(all, "  ").size());
	}

	private List<GroupEvent> filter(List<GroupEvent> all, String query)
	{
		return ActivityLog.filter(all, new ActivityLog.Filter(query, EnumSet.noneOf(GroupEvent.Category.class), "",
			ActivityLog.Range.ALL), items, NOW, UTC);
	}

	// ---- Filters ----

	@Test
	public void categoryChipsKeepTheirCategories()
	{
		List<GroupEvent> all = sample();
		assertEquals(2, byCategory(all, GroupEvent.Category.DROPS).size());
		assertEquals(1, byCategory(all, GroupEvent.Category.DEATHS).size());
		assertEquals(1, byCategory(all, GroupEvent.Category.STORAGE).size());
		assertEquals(1, byCategory(all, GroupEvent.Category.PROGRESS).size());
		assertTrue(byCategory(all, GroupEvent.Category.GROUP).isEmpty());
		assertTrue(byCategory(all, GroupEvent.Category.ACTIVITIES).isEmpty());
		assertEquals(3, byCategory(all, GroupEvent.Category.DROPS, GroupEvent.Category.DEATHS).size());
	}

	private List<GroupEvent> byCategory(List<GroupEvent> all, GroupEvent.Category first, GroupEvent.Category... rest)
	{
		return ActivityLog.filter(all, new ActivityLog.Filter("", EnumSet.of(first, rest), "", ActivityLog.Range.ALL),
			items, NOW, UTC);
	}

	@Test
	public void everyCategoryHasAChipLabel()
	{
		assertEquals("Drops", ActivityLog.label(GroupEvent.Category.DROPS));
		assertEquals("Activities", ActivityLog.label(GroupEvent.Category.ACTIVITIES));
	}

	@Test
	public void memberPickerKeepsOneMember()
	{
		List<GroupEvent> all = sample();
		List<GroupEvent> alex = ActivityLog.filter(all, new ActivityLog.Filter("", EnumSet.noneOf(
			GroupEvent.Category.class), "ALEX", ActivityLog.Range.ALL), items, NOW, UTC);
		assertEquals(2, alex.size());
		assertEquals(Arrays.asList("Alex", "Sam"), ActivityLog.members(all));
	}

	@Test
	public void timeRangeKeepsTodayOrTheLastWeek()
	{
		List<GroupEvent> all = sample();
		assertEquals(3, range(all, ActivityLog.Range.TODAY).size());
		assertEquals(4, range(all, ActivityLog.Range.WEEK).size());
		assertEquals(5, range(all, ActivityLog.Range.ALL).size());
	}

	private List<GroupEvent> range(List<GroupEvent> all, ActivityLog.Range r)
	{
		return ActivityLog.filter(all, new ActivityLog.Filter("", EnumSet.noneOf(GroupEvent.Category.class), "", r),
			items, NOW, UTC);
	}

	// ---- Sections and rows ----

	@Test
	public void sinceYouLastPlayedIsTheTopSectionThenDays()
	{
		List<ActivityLog.Section> s = ActivityLog.sections(sample(), NOW - 150 * 60_000L, "Sam", NOW, UTC);
		assertTrue(s.get(0).title.startsWith("Since you last played"));
		// Alex's death after it; Sam's own level up is not "new" to Sam.
		assertEquals(1, s.get(0).events.size());
		assertEquals(GroupEvent.Type.DEATH, s.get(0).events.get(0).type);
		assertEquals("Today", s.get(1).title);
		assertEquals(2, s.get(1).events.size());
		assertEquals("Sat 8 Nov", s.get(2).title);
		assertEquals("Fri 31 Oct", s.get(3).title);
	}

	@Test
	public void withoutALastPlayedTimeThereAreOnlyDays()
	{
		List<ActivityLog.Section> s = ActivityLog.sections(sample(), 0, "Sam", NOW, UTC);
		assertEquals("Today", s.get(0).title);
		assertEquals(3, s.get(0).events.size());
		assertEquals("Yesterday", ActivityLog.dayTitle(LocalDate.of(2025, 11, 9), LocalDate.of(2025, 11, 10)));
	}

	@Test
	public void rowHasGameAmountsShortAndFullOnHover()
	{
		GroupEvent e = GroupEvent.drop(NOW - HOUR, "Alex", "Vorkath", items(WHIP, 1, SHARK, 2));
		ActivityLog.Row r = ActivityLog.row(e, items, NOW, UTC);
		assertEquals(WHIP, r.itemId);
		assertNull(r.skill);
		assertEquals("1h ago", r.ago);
		assertTrue(r.text, r.text.contains("Abyssal whip"));
		assertTrue(r.full, r.full.startsWith("Valuable drop\nAlex: Vorkath dropped"));
		assertTrue(r.full, r.full.contains("\nValue: ") && r.full.contains("(1,501,600) gp"));
		assertTrue(r.full, r.full.endsWith("\n10 Nov 2025, 11:00"));
		assertTrue(r.copy, r.copy.startsWith("[10 Nov 2025, 11:00] Alex: Vorkath dropped"));
	}

	@Test
	public void levelRowsDrawTheirSkill()
	{
		assertEquals("Attack", ActivityLog.row(level(NOW, "Sam", "Attack", 80), items, NOW, UTC).skill);
		GroupEvent maxed = GroupEvent.of(GroupEvent.Type.MAXED, NOW, "Sam").text("total").number(2277).build();
		assertNull(ActivityLog.skillOf(maxed));
		assertEquals(-1, ActivityLog.row(maxed, items, NOW, UTC).itemId);
	}

	// ---- The bound ----

	@Test
	public void theFeedKeepsTheNewestTwoThousand()
	{
		DropLog feed = new DropLog(ActivityLog.MAX_ENTRIES, ActivityLog.MAX_AGE_MS, () -> NOW);
		for (int i = 0; i < ActivityLog.MAX_ENTRIES + 50; i++)
		{
			assertTrue(feed.add(level(NOW - (ActivityLog.MAX_ENTRIES + 50 - i) * 1000L, "Sam", "Attack", i)));
		}
		List<GroupEvent> kept = feed.entries();
		assertEquals(ActivityLog.MAX_ENTRIES, kept.size());
		assertEquals(NOW - 1000L, kept.get(0).at);
		assertEquals(NOW - ActivityLog.MAX_ENTRIES * 1000L, kept.get(kept.size() - 1).at);
	}

	@Test
	public void theFeedKeepsThirtyDays()
	{
		AtomicLong clock = new AtomicLong(NOW);
		DropLog feed = new DropLog(ActivityLog.MAX_ENTRIES, ActivityLog.MAX_AGE_MS, clock::get);
		assertFalse(feed.add(death(NOW - 31 * DAY, "Alex", "Lumbridge")));
		assertTrue(feed.add(death(NOW - 29 * DAY, "Alex", "Lumbridge")));
		assertTrue(feed.add(death(NOW - DAY, "Alex", "Varrock")));
		// Two days on, the oldest is past 30 days and goes with the next add.
		clock.set(NOW + 2 * DAY);
		assertTrue(feed.add(death(NOW, "Alex", "Falador")));
		assertEquals(2, feed.entries().size());
		assertEquals("Varrock", feed.entries().get(1).location);
	}

	@Test
	public void theFeedDropsAResend()
	{
		DropLog feed = ActivityLog.newFeed();
		long at = System.currentTimeMillis();
		assertTrue(feed.add(drop(at, "Alex", "Vorkath", WHIP)));
		assertFalse(feed.add(drop(at, "alex", "Vorkath", WHIP)));
	}

	// ---- Restart ----

	@Test
	public void aRestartKeepsTheLog()
	{
		long now = System.currentTimeMillis();
		GroupRoster before = new GroupRoster();
		GroupEvent summary = GroupEvent.of(GroupEvent.Type.STORAGE_SUMMARY, now - HOUR, "Sam").items(items(SHARK, 5))
			.taken(items(TROUT, 15)).build();
		GroupEvent offline = GroupEvent.of(GroupEvent.Type.ONLINE, now - 2 * HOUR, "Alex").text("offline")
			.number(3_600_000).world(302).detail("1:35 PM").build();
		GroupEvent dead = death(now - 3 * HOUR, "Alex", "Lumbridge").toBuilder().text("Vorkath").build();
		before.feed().add(summary);
		before.feed().add(offline);
		before.feed().add(dead);

		GroupRoster after = new GroupRoster();
		assertTrue(GroupCache.read(GSON, GroupCache.write(GSON, before), after));
		List<GroupEvent> kept = after.feed().entries();
		assertEquals(3, kept.size());
		assertTrue(kept.get(0).sameAs(summary));
		assertEquals(summary.taken, kept.get(0).taken);
		assertEquals(offline.detail, kept.get(1).detail);
		assertEquals(302, kept.get(1).world);
		assertEquals("Lumbridge", kept.get(2).location);
		assertEquals("Vorkath", kept.get(2).text);
	}

	@Test
	public void anOlderCacheStartsTheFeedFromItsDropsAndStorageLogs()
	{
		long now = System.currentTimeMillis();
		GroupRoster before = new GroupRoster();
		before.events().add(drop(now - HOUR, "Alex", "Vorkath", WHIP));
		before.putStorage(new StorageChange(now - 2 * HOUR, "Sam", items(TROUT, 15), items(SHARK, 5),
			Collections.emptyMap()));
		// What a build before the feed wrote: everything but the "feed" key.
		com.google.gson.JsonObject old = GSON.fromJson(GroupCache.write(GSON, before), com.google.gson.JsonObject.class);
		old.remove("feed");

		GroupRoster after = new GroupRoster();
		assertTrue(GroupCache.read(GSON, GSON.toJson(old), after));
		List<GroupEvent> feed = after.feed().entries();
		assertEquals(2, feed.size());
		assertEquals(GroupEvent.Type.DROP, feed.get(0).type);
		assertEquals(GroupEvent.Type.STORAGE_SUMMARY, feed.get(1).type);
		assertEquals(items(TROUT, 15), feed.get(1).taken);
	}

	@Test
	public void clearingTheGroupClearsTheFeed()
	{
		GroupRoster roster = new GroupRoster();
		roster.feed().add(death(System.currentTimeMillis(), "Alex", "Lumbridge"));
		assertTrue(roster.hasAnything());
		roster.clearAll();
		assertTrue(roster.feed().entries().isEmpty());
	}
}
