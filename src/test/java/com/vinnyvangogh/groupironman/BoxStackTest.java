package com.vinnyvangogh.groupironman;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** The Group box stacks consecutive level-ups of one skill by one member into one line. */
public class BoxStackTest
{
	private static final long NOW = EventFixtures.NOW;
	private final FakeItems items = new FakeItems();

	private static GroupEvent level(String member, String skill, long level, long at)
	{
		return GroupEvent.of(GroupEvent.Type.LEVEL_UP, at, member).text(skill).number(level).build();
	}

	private static GroupEvent death(String member, long at)
	{
		return GroupEvent.of(GroupEvent.Type.DEATH, at, member).build();
	}

	@Test
	public void threeLevelsAreOneLine()
	{
		List<GroupEvent> out = BoxText.stack(Arrays.asList(level("Alex", "Thieving", 4, NOW - 1_000),
			level("Alex", "Thieving", 3, NOW - 2_000), level("Alex", "Thieving", 2, NOW - 3_000)));
		assertEquals(1, out.size());
		assertEquals(NOW - 1_000, out.get(0).at);
		BoxText.Line wide = BoxText.format(out.get(0), items, BoxSize.WIDE, true, BoxTimes.SHORT, NOW);
		assertEquals("2 → 4", wide.detail);
		BoxText.Line compact = BoxText.format(out.get(0), items, BoxSize.COMPACT, true, BoxTimes.SHORT, NOW);
		assertEquals("+3", compact.detail);
		BoxText.Line plain = BoxText.format(out.get(0), items, BoxSize.WIDE, false, BoxTimes.SHORT, NOW);
		assertEquals("Thieving 2 → 4", plain.detail);
	}

	@Test
	public void aSingleLevelIsUnchanged()
	{
		GroupEvent one = level("Alex", "Thieving", 44, NOW);
		List<GroupEvent> out = BoxText.stack(Arrays.asList(one));
		assertEquals(1, out.size());
		assertEquals("44", BoxText.format(out.get(0), items, BoxSize.WIDE, true, BoxTimes.SHORT, NOW).detail);
	}

	@Test
	public void otherSkillsMembersAndInterruptionsDoNotMerge()
	{
		assertEquals(2, BoxText.stack(Arrays.asList(level("Alex", "Thieving", 4, NOW),
			level("Alex", "Mining", 3, NOW))).size());
		assertEquals(2, BoxText.stack(Arrays.asList(level("Alex", "Thieving", 4, NOW),
			level("Sam", "Thieving", 3, NOW))).size());
		assertEquals(3, BoxText.stack(Arrays.asList(level("Alex", "Thieving", 4, NOW), death("Alex", NOW),
			level("Alex", "Thieving", 3, NOW))).size());
	}
}
