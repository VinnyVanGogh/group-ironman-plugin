package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static com.vinnyvangogh.groupironman.EventFixtures.all;
import static com.vinnyvangogh.groupironman.EventFixtures.settings;
import static org.junit.Assert.assertEquals;

/** "Post every N levels": a group setting, for Discord and the overlay alike. */
public class LevelEveryTest
{
	private final FakeItems items = new FakeItems();
	private final List<Long> discord = new ArrayList<>();
	private final List<Long> overlay = new ArrayList<>();
	private final EventRouter router = new EventRouter(e -> discord.add(e.number), e -> overlay.add(e.number));

	private void levels(EventSettings s, int from, int to)
	{
		for (int level = from; level <= to; level++)
		{
			router.route(GroupEvent.of(GroupEvent.Type.LEVEL_UP, NOW, "Alex").text("Attack").number(level).build(), true,
				s, items);
		}
	}

	@Test
	public void everyLevelWhenNIsOne()
	{
		levels(settings(all(), all()).levels(60, 1), 58, 64);
		assertEquals(java.util.Arrays.asList(60L, 61L, 62L, 63L, 64L), discord);
		assertEquals(discord, overlay);
	}

	@Test
	public void everyFifthLevelFromTheMinimum()
	{
		levels(settings(all(), all()).levels(60, 5), 59, 66);
		assertEquals(java.util.Arrays.asList(60L, 65L), discord);
		assertEquals(discord, overlay);
	}

	@Test
	public void ninetyNineAlwaysPosts()
	{
		levels(settings(all(), all()).levels(60, 5), 97, 99);
		assertEquals(java.util.Collections.singletonList(99L), discord);
		assertEquals(discord, overlay);
	}

	@Test
	public void otherProgressKindsIgnoreN()
	{
		EventSettings s = settings(all(), all()).levels(60, 7);
		for (GroupEvent.Type t : new GroupEvent.Type[]{GroupEvent.Type.MAXED, GroupEvent.Type.COMBAT_LEVEL,
			GroupEvent.Type.TOTAL_LEVEL})
		{
			router.route(EventFixtures.sample(t), true, s, items);
		}
		assertEquals(3, discord.size());
	}

	@Test
	public void theDefaultIsEveryLevel()
	{
		GroupIronmanConfig defaults = new GroupIronmanConfig()
		{
		};
		assertEquals(1, defaults.eventLevelEvery());
		assertEquals(1, EventSettings.from(defaults).levelEvery);
		assertEquals(1, settings(all(), all()).levelEvery);
	}
}
