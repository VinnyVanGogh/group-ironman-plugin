package com.vinnyvangogh.groupironman;

import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static com.vinnyvangogh.groupironman.GamePresenceTest.ROSTER;
import static com.vinnyvangogh.groupironman.GamePresenceTest.worlds;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Online and offline posts for members without the plugin, as the game's Group channel sees them (Board
 * 2026-11-11), with the same grace and pair limit as a member's own ({@link PresenceDebounce}), and only one
 * plugin user posting each.
 */
public class ChannelPostsTest
{
	private static final long MIN = TimeUnit.MINUTES.toMillis(1);
	private static final int GRACE = 2;
	/** GroupShip runs the plugin; the rest do not. */
	private static final Predicate<String> PLUGIN = n -> GroupMembers.key(n).equals("groupship");

	private final ChannelPosts posts = new ChannelPosts(ZoneOffset.UTC);

	private static GamePresence channel(Object... nameWorld)
	{
		return GamePresence.read(ROSTER, worlds(nameWorld), null);
	}

	@Test
	public void theFirstChannelIsTheBaselineAndPostsNothing()
	{
		assertTrue(posts.update(GamePresenceTest.twoOfFive(), PLUGIN, NOW, GRACE).isEmpty());
		assertTrue(posts.poll(NOW + 30 * MIN, GRACE).isEmpty());
	}

	@Test
	public void aLoginPostsOnlineWithTheWorldAndAHopPostsNothing()
	{
		posts.update(channel("GroupShip", 303), PLUGIN, NOW, GRACE);
		List<GroupEvent> on = posts.update(channel("GroupShip", 303, "GroupOtt", 615), PLUGIN, NOW + MIN, GRACE);
		assertEquals(1, on.size());
		assertEquals("GroupOtt", on.get(0).member);
		assertEquals("online", on.get(0).text);
		assertEquals(GroupEvent.Type.ONLINE, on.get(0).type);
		assertTrue(posts.update(channel("GroupShip", 303, "GroupOtt", 302), PLUGIN, NOW + 2 * MIN, GRACE).isEmpty());
	}

	@Test
	public void aLogoutPostsOfflineOnceTheGraceAndPairLimitPass()
	{
		posts.update(channel("GroupShip", 303), PLUGIN, NOW, GRACE);
		posts.update(channel("GroupShip", 303, "GroupOtt", 615), PLUGIN, NOW, GRACE);
		long out = NOW + 20 * MIN;
		assertTrue(posts.update(channel("GroupShip", 303), PLUGIN, out, GRACE).isEmpty());
		assertTrue(posts.poll(out + GRACE * MIN - 1, GRACE).isEmpty());
		List<GroupEvent> off = posts.poll(out + GRACE * MIN, GRACE);
		assertEquals(1, off.size());
		assertEquals("offline", off.get(0).text);
		assertEquals("GroupOtt", off.get(0).member);
		assertEquals(out, off.get(0).at);
	}

	@Test
	public void aRelogInsideTheGracePostsNothing()
	{
		posts.update(channel("GroupShip", 303), PLUGIN, NOW, GRACE);
		posts.update(channel("GroupShip", 303, "GroupOtt", 615), PLUGIN, NOW, GRACE);
		posts.update(channel("GroupShip", 303), PLUGIN, NOW + 20 * MIN, GRACE);
		assertTrue(posts.update(channel("GroupShip", 303, "GroupOtt", 615), PLUGIN, NOW + 21 * MIN, GRACE).isEmpty());
		assertTrue(posts.poll(NOW + 40 * MIN, GRACE).isEmpty());
	}

	@Test
	public void pluginUsersPostTheirOwn()
	{
		posts.update(channel("GroupOtt", 615), PLUGIN, NOW, GRACE);
		assertTrue(posts.update(channel("GroupOtt", 615, "GroupShip", 303), PLUGIN, NOW + MIN, GRACE).isEmpty());
	}

	@Test
	public void losingTheChannelIsNotEveryoneLoggingOut()
	{
		posts.update(GamePresenceTest.twoOfFive(), PLUGIN, NOW, GRACE);
		assertTrue(posts.update(GamePresence.UNKNOWN, PLUGIN, NOW + MIN, GRACE).isEmpty());
		assertTrue(posts.update(GamePresenceTest.twoOfFive(), PLUGIN, NOW + 2 * MIN, GRACE).isEmpty());
		assertTrue(posts.poll(NOW + 30 * MIN, GRACE).isEmpty());
	}

	@Test
	public void afterAResetTheNextChannelIsABaselineAgain()
	{
		posts.update(channel("GroupShip", 303), PLUGIN, NOW, GRACE);
		posts.reset();
		assertTrue(posts.update(GamePresenceTest.twoOfFive(), PLUGIN, NOW + MIN, GRACE).isEmpty());
	}

	/** Two plugin users online: exactly one of them posts for the rest, the same one on both clients. */
	@Test
	public void onlyOnePluginUserPostsEachChange()
	{
		assertTrue(ChannelPosts.leads("GroupArk", Collections.singleton("GroupShip")));
		assertFalse(ChannelPosts.leads("GroupShip", Collections.singleton("GroupArk")));
		assertTrue(ChannelPosts.leads("groupark", Collections.singleton("GroupShip")));
		assertTrue(ChannelPosts.leads("GroupShip", Collections.emptySet()));
		// Yourself in the list changes nothing.
		Set<String> both = new HashSet<>(Arrays.asList("GroupArk", "GroupShip"));
		assertTrue(ChannelPosts.leads("GroupArk", both));
		assertFalse(ChannelPosts.leads("GroupShip", both));
		assertFalse(ChannelPosts.leads("", both));
	}

	/** Out of the party, the others cannot see you: post only when you hear no one either. */
	@Test
	public void aClientOutOfThePartyPostsOnlyAlone()
	{
		assertFalse(ChannelPosts.leads("GroupArk", false, Collections.singleton("GroupShip")));
		assertTrue(ChannelPosts.leads("GroupShip", false, Collections.emptySet()));
		assertTrue(ChannelPosts.leads("GroupArk", true, Collections.singleton("GroupShip")));
	}
}
