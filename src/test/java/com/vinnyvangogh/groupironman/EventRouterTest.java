package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static com.vinnyvangogh.groupironman.EventFixtures.all;
import static com.vinnyvangogh.groupironman.EventFixtures.items;
import static com.vinnyvangogh.groupironman.EventFixtures.none;
import static com.vinnyvangogh.groupironman.EventFixtures.sample;
import static com.vinnyvangogh.groupironman.EventFixtures.settings;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class EventRouterTest
{
	private final FakeItems items = new FakeItems();
	private final List<GroupEvent> discord = new ArrayList<>();
	private final List<GroupEvent> overlay = new ArrayList<>();
	private final EventRouter router = new EventRouter(discord::add, overlay::add);

	@Test
	public void everyKindReachesEachOutputOnceWhenItsSwitchIsOn()
	{
		for (GroupEvent.Type t : GroupEvent.Type.values())
		{
			discord.clear();
			overlay.clear();
			GroupEvent e = sample(t);
			router.route(e, true, settings(EnumSet.of(t), EnumSet.of(t)), items);
			assertEquals(t + " to Discord", 1, discord.size());
			assertEquals(t + " to the overlay", 1, overlay.size());
			assertEquals(t, discord.get(0).type);
			assertEquals("Alex", overlay.get(0).member);
		}
	}

	@Test
	public void noKindReachesAnOutputWithItsSwitchOff()
	{
		for (GroupEvent.Type t : GroupEvent.Type.values())
		{
			EnumSet<GroupEvent.Type> others = EnumSet.complementOf(EnumSet.of(t));
			router.route(sample(t), true, settings(others, others), items);
			router.route(sample(t), true, EventSettings.OFF, items);
		}
		assertTrue(discord.isEmpty());
		assertTrue(overlay.isEmpty());
	}

	@Test
	public void onlyOwnEventsArePostedSoEachIsPostedOnce()
	{
		// Alex's client posts Alex's drop; Sam's client sees the same drop from the party and does not.
		GroupEvent drop = sample(GroupEvent.Type.DROP);
		router.route(drop, true, settings(all(), all()), items);
		router.route(drop, false, settings(all(), all()), items);
		assertEquals(1, discord.size());
		assertEquals("both clients show it", 2, overlay.size());
	}

	@Test
	public void aDropKeepsOnlyItsValuableStacks()
	{
		GroupEvent drop = GroupEvent.drop(NOW, "Alex", "Vorkath", items(FakeItems.WHIP, 1, FakeItems.SHARK, 5));
		router.route(drop, true, settings(all(), none()), items);
		assertEquals(items(FakeItems.WHIP, 1), discord.get(0).items);

		discord.clear();
		router.route(GroupEvent.drop(NOW, "Alex", "Goblin", items(FakeItems.SHARK, 100)), true,
			settings(all(), all()), items);
		assertTrue("80K of sharks is under 1M", discord.isEmpty() && overlay.isEmpty());
	}

	@Test
	public void levelAndStorageThresholds()
	{
		EventSettings s = settings(all(), none());
		router.route(GroupEvent.of(GroupEvent.Type.LEVEL_UP, NOW, "Alex").text("Attack").number(49).build(), true, s,
			items);
		router.route(GroupEvent.of(GroupEvent.Type.STORAGE_DEPOSIT, NOW, "Alex").items(items(FakeItems.SHARK, 1))
			.number(999_999).build(), true, s, items);
		assertTrue(discord.isEmpty());
		router.route(GroupEvent.of(GroupEvent.Type.LEVEL_UP, NOW, "Alex").text("Attack").number(50).build(), true, s,
			items);
		router.route(GroupEvent.of(GroupEvent.Type.STORAGE_WITHDRAWAL, NOW, "Alex").items(items(FakeItems.WHIP, 1))
			.number(1_000_000).build(), true, s, items);
		assertEquals(2, discord.size());
	}

	@Test
	public void unthresholdedKindsPassUnchanged()
	{
		GroupEvent pet = sample(GroupEvent.Type.PET);
		assertSame(pet, EventRouter.threshold(pet, settings(all(), all()), items));
	}

	@Test
	public void anEventWithNoMemberIsDropped()
	{
		router.route(GroupEvent.of(GroupEvent.Type.PET, NOW, "").build(), true, settings(all(), all()), items);
		assertTrue(discord.isEmpty() && overlay.isEmpty());
	}
}
