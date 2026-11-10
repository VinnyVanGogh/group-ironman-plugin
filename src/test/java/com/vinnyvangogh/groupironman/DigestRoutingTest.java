package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static com.vinnyvangogh.groupironman.EventFixtures.items;
import static com.vinnyvangogh.groupironman.EventFixtures.none;
import static com.vinnyvangogh.groupironman.EventFixtures.sample;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The daily digest is in addition to instant posts: it never holds one back (Board 2026-11-10). */
public class DigestRoutingTest
{
	private final FakeItems items = new FakeItems();
	private final List<GroupEvent> discord = new ArrayList<>();
	private final List<GroupEvent> overlay = new ArrayList<>();
	private final List<GroupEvent> digest = new ArrayList<>();
	private final EventRouter router = new EventRouter(discord::add, overlay::add, digest::add);

	private static EventSettings settings(Set<GroupEvent.Type> instant, Set<GroupEvent.Category> digest)
	{
		return new EventSettings(instant, none(), 1_000_000, 1_000_000, 50, 7, false, 0, 100, 5, digest, 20);
	}

	@Test
	public void anInstantPostIsSentWhileItsDigestCategoryIsTicked()
	{
		EventSettings s = settings(EnumSet.of(GroupEvent.Type.DEATH), EnumSet.allOf(GroupEvent.Category.class));
		router.route(sample(GroupEvent.Type.DEATH), true, s, items);
		assertEquals("posted now", 1, discord.size());
		assertEquals("and in tonight's digest", 1, digest.size());
	}

	@Test
	public void theDigestHasTickedAndUntickedEventsOfItsCategory()
	{
		EventSettings s = settings(EnumSet.of(GroupEvent.Type.PET), EnumSet.of(GroupEvent.Category.DROPS));
		router.route(sample(GroupEvent.Type.PET), true, s, items);
		router.route(sample(GroupEvent.Type.CLOG), true, s, items);
		assertEquals("only the ticked pet posts now", 1, discord.size());
		assertEquals(GroupEvent.Type.PET, discord.get(0).type);
		assertEquals("the digest lists both", 2, digest.size());
	}

	@Test
	public void aDigestOnlyCategoryStillLandsInTheDigest()
	{
		EventSettings s = settings(none(), EnumSet.of(GroupEvent.Category.DEATHS));
		assertTrue("detected though no instant post wants it", s.wants(GroupEvent.Type.DEATH));
		assertFalse("other categories stay undetected", s.wants(GroupEvent.Type.PET));
		router.route(sample(GroupEvent.Type.DEATH), true, s, items);
		assertTrue(discord.isEmpty());
		assertEquals(1, digest.size());
	}

	@Test
	public void theDigestKeepsTheThresholdsMembersEventsAndTheRecapOut()
	{
		EventSettings s = settings(none(), EnumSet.allOf(GroupEvent.Category.class));
		router.route(GroupEvent.drop(NOW, "Alex", "Goblin", items(FakeItems.SHARK, 100)), true, s, items);
		router.route(sample(GroupEvent.Type.DEATH), false, s, items);
		router.route(sample(GroupEvent.Type.WEEKLY_RECAP), true, s, items);
		assertFalse("the recap is its own post", s.wants(GroupEvent.Type.WEEKLY_RECAP));
		assertTrue(digest.isEmpty());
	}

	@Test
	public void noDigestTickedMeansNoDigestLines()
	{
		EventSettings s = settings(EnumSet.of(GroupEvent.Type.DEATH), EnumSet.noneOf(GroupEvent.Category.class));
		router.route(sample(GroupEvent.Type.DEATH), true, s, items);
		assertEquals(1, discord.size());
		assertTrue(digest.isEmpty());
	}
}
