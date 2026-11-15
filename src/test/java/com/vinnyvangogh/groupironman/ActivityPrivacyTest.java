package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Who sees a live activity and where it may post (docs/plans/activities.md §5.4, §7 items 2 and 4): nothing leaves
 * this client without sync and "Share group events", a member's opt-outs beat the leader's settings, and the
 * activity events go to the Progress and Deaths webhooks.
 */
public class ActivityPrivacyTest
{
	private static final Gson GSON = new Gson();
	private static final ActivityState RUN = ActivityCodecTest.inferno(10);

	@Test
	public void nothingIsSentWithoutSyncOrShareGroupEvents()
	{
		assertNull(GroupOutbox.activity(GSON, false, true, RUN, false));
		assertNull(GroupOutbox.activity(GSON, true, false, RUN, false));
		assertNull(GroupOutbox.activity(GSON, false, false, RUN, true));
		assertNotNull(GroupOutbox.activity(GSON, true, true, RUN, false));
	}

	@Test
	public void turningSharingOffMidRunSendsOnlyAClear()
	{
		String clear = GroupOutbox.activity(GSON, true, false, RUN, true);
		assertNotNull(clear);
		ActivityState back = ActivityCodec.decode(GSON, clear);
		assertFalse("no run details in it", back.active());
		assertFalse(clear.contains("Inferno"));
	}

	@Test
	public void noActivityEventTravelsWithoutShareGroupEvents()
	{
		GroupEvent wave = GroupEvent.of(GroupEvent.Type.ACTIVITY_WAVE, 1, "Alex").text("The Inferno").number(60).build();
		assertTrue(GroupOutbox.groupEvents(GSON, false, Collections.singletonList(wave)).isEmpty());
		assertEquals(1, GroupOutbox.groupEvents(GSON, true, Collections.singletonList(wave)).size());
	}

	@Test
	public void activityEventsGoToProgressAndDeaths()
	{
		assertEquals(Webhook.PROGRESS, Webhook.of(GroupEvent.Type.ACTIVITY_START));
		assertEquals(Webhook.PROGRESS, Webhook.of(GroupEvent.Type.ACTIVITY_WAVE));
		assertEquals(Webhook.PROGRESS, Webhook.of(GroupEvent.Type.ACTIVITY_COMPLETE));
		assertEquals(Webhook.DEATHS, Webhook.of(GroupEvent.Type.ACTIVITY_DEATH));
		assertEquals(GroupEvent.Category.ACTIVITIES, GroupEvent.Type.ACTIVITY_COMPLETE.category);
		assertEquals(GroupEvent.Category.DEATHS, GroupEvent.Type.ACTIVITY_DEATH.category);
	}

	@Test
	public void everyActivityToggleIsOffByDefaultAndWaveEveryIsTen()
	{
		GroupIronmanConfig defaults = new GroupIronmanConfig()
		{
		};
		EventSettings s = EventSettings.from(defaults);
		for (GroupEvent.Type t : EnumSet.of(GroupEvent.Type.ACTIVITY_START, GroupEvent.Type.ACTIVITY_WAVE,
			GroupEvent.Type.ACTIVITY_COMPLETE, GroupEvent.Type.ACTIVITY_DEATH))
		{
			assertFalse(t + " to Discord", s.discord.contains(t));
			assertFalse(t + " as a banner", s.overlay.contains(t));
		}
		assertEquals(10, defaults.eventWaveEvery());
		assertEquals(10, new GroupSettingsSchema.Resolved(defaults, null).intValue(GroupSettingsSchema.WAVE_EVERY));
	}

	@Test
	public void aMembersOptOutBeatsTheLeadersWaveSetting()
	{
		GroupIronmanConfig defaults = new GroupIronmanConfig()
		{
		};
		Map<String, Long> group = GroupSettingsSchema.seed(defaults);
		group.put(GroupSettingsSchema.discordKey(GroupEvent.Type.ACTIVITY_WAVE), 1L);
		GroupSettings doc = GroupSettings.next(null, group, "Leader", GroupSigner.Identity.create(), 1_000L);
		GroupSettingsSchema.Resolved leader = new GroupSettingsSchema.Resolved(defaults, doc);
		assertTrue(EventSettings.from(defaults, leader, MemberPrefs.NONE).discord
			.contains(GroupEvent.Type.ACTIVITY_WAVE));
		MemberPrefs noWaves = new MemberPrefs(false, EnumSet.of(GroupEvent.Type.ACTIVITY_WAVE),
			EnumSet.noneOf(GroupEvent.Type.class));
		assertFalse(EventSettings.from(defaults, leader, noWaves).discord.contains(GroupEvent.Type.ACTIVITY_WAVE));
		MemberPrefs nothing = new MemberPrefs(true, EnumSet.noneOf(GroupEvent.Type.class),
			EnumSet.noneOf(GroupEvent.Type.class));
		assertFalse(EventSettings.from(defaults, leader, nothing).discord.contains(GroupEvent.Type.ACTIVITY_WAVE));
	}

	@Test
	public void theLiveBoardLeavesOutMembersWhoOptedOut()
	{
		Map<String, ActivityState> live = new TreeMap<>();
		live.put("Alex", RUN);
		live.put("Sam", RUN);
		live.put("Pat", RUN);
		Map<String, MemberPrefs> prefs = new HashMap<>();
		prefs.put("Sam", new MemberPrefs(true, EnumSet.noneOf(GroupEvent.Type.class),
			EnumSet.noneOf(GroupEvent.Type.class)));
		prefs.put("Pat", new MemberPrefs(false, EnumSet.of(GroupEvent.Type.ACTIVITY_START),
			EnumSet.noneOf(GroupEvent.Type.class)));
		Map<String, String> out = StatusBoard.activitySuffixes(live, prefs, "Me", MemberPrefs.NONE, true);
		assertEquals(" - in The Inferno, wave 40", out.get(GroupMembers.key("Alex")));
		assertFalse("don't post my events", out.containsKey(GroupMembers.key("Sam")));
		assertFalse("activity posts off", out.containsKey(GroupMembers.key("Pat")));
	}

	@Test
	public void ownBoardClauseNeedsShareGroupEvents()
	{
		Map<String, ActivityState> live = Collections.singletonMap("Me", RUN);
		assertTrue(StatusBoard.activitySuffixes(live, Collections.emptyMap(), "Me", MemberPrefs.NONE, false)
			.isEmpty());
		assertEquals(1, StatusBoard.activitySuffixes(live, Collections.emptyMap(), "Me", MemberPrefs.NONE, true)
			.size());
	}

	@Test
	public void theBoardShowsTheClauseOnOnlineLinesOnly()
	{
		GroupMembers roster = GroupMembers.of("Iron Teacups", java.util.Arrays.asList("Alex", "Sam"));
		Map<String, Long> heard = new TreeMap<>();
		long now = 1_000_000_000L;
		heard.put("Alex", now);
		heard.put("Sam", now - 24 * 3_600_000L);
		Map<String, String> clauses = new HashMap<>();
		clauses.put(GroupMembers.key("Alex"), " - in The Inferno, wave 40");
		clauses.put(GroupMembers.key("Sam"), " - in Fight Caves, wave 10");
		String lines = StatusBoard.lines(roster, heard, Collections.emptyMap(), Collections.emptyMap(), null,
			GamePresence.UNKNOWN, clauses, now);
		assertTrue(lines, lines.contains("**Alex** online - in The Inferno, wave 40"));
		assertFalse(lines, lines.contains("Fight Caves"));
	}

	@Test
	public void staleActivitiesAgeOutOfTheRoster()
	{
		GroupRoster roster = new GroupRoster();
		roster.putActivity("Alex", RUN);
		assertEquals(1, roster.activities(RUN.at + 60_000).size());
		assertTrue(roster.activities(RUN.at + GroupRoster.ACTIVITY_STALE_MS + 1).isEmpty());
		roster.putActivity("Alex", ActivityState.NONE);
		assertTrue(roster.activities(RUN.at).isEmpty());
	}
}
