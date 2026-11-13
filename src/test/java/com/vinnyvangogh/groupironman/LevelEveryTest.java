package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static com.vinnyvangogh.groupironman.EventFixtures.all;
import static com.vinnyvangogh.groupironman.EventFixtures.settings;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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

	private void jump(EventSettings s, int previous, int level)
	{
		router.route(GroupEvent.of(GroupEvent.Type.LEVEL_UP, NOW, "Alex").text("Prayer").number(level)
			.previous(previous).build(), true, s, items);
	}

	@Test
	public void aJumpOverAMultipleCounts()
	{
		EventSettings s = settings(all(), all()).levels(1, 5);
		jump(s, 3, 7);
		jump(s, 7, 9);
		jump(s, 9, 10);
		jump(s, 10, 14);
		jump(s, 14, 21);
		assertEquals("3 → 7 crossed 5, 9 → 10 lands on 10, 14 → 21 crossed 15 and 20",
			java.util.Arrays.asList(7L, 10L, 21L), discord);
		assertEquals(discord, overlay);
	}

	@Test
	public void aMembersEventWithoutThePreviousLevelUsesOneBelow()
	{
		EventSettings s = settings(all(), all()).levels(1, 5);
		jump(s, 0, 10);
		jump(s, 0, 11);
		assertEquals(java.util.Collections.singletonList(10L), discord);
	}

	/**
	 * Decision for "Combine level-ups": every-N runs first, so a streak's post
	 * only ever sees the levels that pass it (one post for 60, edited at 65).
	 * The streak reads its start from the first level that passed.
	 */
	@Test
	public void combineLevelUpsOnlySeesLevelsThatPass()
	{
		List<GroupEvent> posted = new ArrayList<>();
		EventRouter levels = new EventRouter(posted::add, e -> { });
		EventSettings s = settings(all(), all()).levels(60, 5);
		for (int level = 59; level <= 66; level++)
		{
			levels.route(GroupEvent.of(GroupEvent.Type.LEVEL_UP, NOW, "Alex").text("Attack").number(level)
				.previous(level - 1).build(), true, s, items);
		}
		assertEquals(2, posted.size());
		LevelThreads threads = new LevelThreads();
		long window = 30 * 60_000L;
		LevelThreads.Streak streak = threads.level(posted.get(0), "u", NOW, window, null);
		assertNotNull("60 starts the post", streak);
		assertNull("65 joins it", threads.level(posted.get(1), "u", NOW + 60_000, window, null));
		assertEquals(59, streak.from);
		assertEquals(65, streak.to);
		assertEquals("Attack 59 → 65 (+6) in 1 min", LevelThreads.text(streak.skill, streak.from, streak.to, 60_000));
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
