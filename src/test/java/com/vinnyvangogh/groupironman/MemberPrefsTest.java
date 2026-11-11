package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.sample;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Per-member opt-outs: personal, applied on top of the group's settings, and shown read-only in the grid. */
public class MemberPrefsTest
{
	private static final GroupIronmanConfig DEFAULTS = new GroupIronmanConfig()
	{
	};
	private final FakeItems items = new FakeItems();
	private final List<GroupEvent> discord = new ArrayList<>();
	private final List<GroupEvent> overlay = new ArrayList<>();
	private final List<GroupEvent> digest = new ArrayList<>();
	private final EventRouter router = new EventRouter(discord::add, overlay::add, digest::add);

	/** The group posts and shows every kind, and digests every category. */
	private static GroupSettingsSchema.Resolved groupAllOn()
	{
		Map<String, Long> values = GroupSettingsSchema.seed(DEFAULTS);
		for (GroupSettingsSchema.Setting s : GroupSettingsSchema.ALL)
		{
			if (s.flag)
			{
				values.put(s.key, 1L);
			}
		}
		values.put("eventLevelMin", 1L);
		// The samples' clue is worth 80 gp; thresholds are not what these tests are about.
		values.put("eventClueMinValue", 0L);
		return new GroupSettingsSchema.Resolved(DEFAULTS,
			GroupSettings.next(null, values, "Alex", GroupSigner.Identity.create(), EventFixtures.NOW));
	}

	private EventSettings settings(MemberPrefs prefs)
	{
		return EventSettings.from(DEFAULTS, groupAllOn(), prefs);
	}

	@Test
	public void dontPostMyEventsStopsEveryOwnPostButNotTheOverlay()
	{
		EventSettings s = settings(new MemberPrefs(true, EnumSet.noneOf(GroupEvent.Type.class),
			EnumSet.noneOf(GroupEvent.Type.class)));
		for (GroupEvent.Type t : GroupEvent.Type.values())
		{
			router.route(sample(t), true, s, items);
		}
		assertTrue(discord.isEmpty());
		assertTrue(digest.isEmpty());
		assertEquals(GroupEvent.Type.values().length, overlay.size());
	}

	@Test
	public void aPerKindOptOutIsRespected()
	{
		EventSettings s = settings(new MemberPrefs(false, EnumSet.of(GroupEvent.Type.DEATH),
			EnumSet.noneOf(GroupEvent.Type.class)));
		router.route(sample(GroupEvent.Type.DEATH), true, s, items);
		router.route(sample(GroupEvent.Type.PET), true, s, items);
		assertEquals(1, discord.size());
		assertEquals(GroupEvent.Type.PET, discord.get(0).type);
		assertEquals("the death stays out of the digest too", 1, digest.size());
		assertEquals("both still show in this member's overlay", 2, overlay.size());
	}

	@Test
	public void anOverlayMuteHidesOnlyTheBanner()
	{
		EventSettings s = settings(new MemberPrefs(false, EnumSet.noneOf(GroupEvent.Type.class),
			EnumSet.of(GroupEvent.Type.LEVEL_UP)));
		router.route(sample(GroupEvent.Type.LEVEL_UP), false, s, items);
		router.route(sample(GroupEvent.Type.LEVEL_UP), true, s, items);
		assertTrue(overlay.isEmpty());
		assertEquals(1, discord.size());
	}

	@Test
	public void preferencesRoundTripAsKindCodes()
	{
		String codes = MemberPrefs.format(EnumSet.of(GroupEvent.Type.PET, GroupEvent.Type.DEATH));
		assertEquals(EnumSet.of(GroupEvent.Type.PET, GroupEvent.Type.DEATH), MemberPrefs.parse(codes));
		assertEquals(EnumSet.of(GroupEvent.Type.PET), MemberPrefs.parse("pt, zz,,"));
		assertTrue(MemberPrefs.parse(null).isEmpty());
		MemberPrefs fresh = MemberPrefs.of(DEFAULTS);
		for (GroupEvent.Type t : GroupEvent.Type.values())
		{
			assertTrue("a fresh install opts out of nothing", fresh.posts(t));
		}
	}

	@Test
	public void theGridShowsEachMembersChoiceReadOnly()
	{
		Map<String, MemberPrefs> prefs = new TreeMap<>();
		prefs.put("Sam", new MemberPrefs(false, EnumSet.of(GroupEvent.Type.DEATH), EnumSet.noneOf(GroupEvent.Type.class)));
		prefs.put("Kim", new MemberPrefs(true, EnumSet.noneOf(GroupEvent.Type.class),
			EnumSet.noneOf(GroupEvent.Type.class)));
		SettingsModel.Grid grid = SettingsModel.grid(Arrays.asList("Alex", "Sam", "Kim"), prefs);
		assertEquals(Arrays.asList("Alex", "Sam", "Kim"), grid.members);
		assertEquals(GroupEvent.Type.values().length, grid.rows.size());
		SettingsModel.GridRow death = grid.rows.stream().filter(r -> r.type == GroupEvent.Type.DEATH).findFirst().get();
		assertEquals(Arrays.asList(SettingsModel.Cell.UNKNOWN, SettingsModel.Cell.OUT, SettingsModel.Cell.OUT),
			death.cells);
		SettingsModel.GridRow pet = grid.rows.stream().filter(r -> r.type == GroupEvent.Type.PET).findFirst().get();
		assertEquals(Arrays.asList(SettingsModel.Cell.UNKNOWN, SettingsModel.Cell.IN, SettingsModel.Cell.OUT),
			pet.cells);
	}
}
