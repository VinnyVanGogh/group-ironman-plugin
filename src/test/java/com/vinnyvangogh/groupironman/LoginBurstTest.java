package com.vinnyvangogh.groupironman;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The net under {@link LoginGate}: a login's first minute posts a handful, never a burst. */
public class LoginBurstTest
{
	private LoginBurst burst;

	@Before
	public void login()
	{
		burst = new LoginBurst(LoginBurst.ACHIEVEMENTS::contains);
		burst.open(NOW);
	}

	private static GroupEvent diary(String text)
	{
		return GroupEvent.of(GroupEvent.Type.DIARY, NOW, "Ship Wrek").text(text).build();
	}

	@Test
	public void aFiveAchievementLoginPostsNothing()
	{
		for (String d : new String[]{"Karamja medium", "Lumbridge easy", "Varrock easy", "Ardougne easy"})
		{
			assertTrue(burst.hold(diary(d), NOW + 1_000));
		}
		assertTrue(burst.hold(GroupEvent.of(GroupEvent.Type.TOTAL_LEVEL, NOW, "Ship Wrek").number(700).build(),
			NOW + 2_000));
		assertTrue("still inside the minute", burst.due(NOW + 30_000).isEmpty());
		assertTrue("a burst is dropped", burst.due(NOW + LoginBurst.WINDOW_MS).isEmpty());
		assertEquals(5, burst.takeDropped());
		assertEquals(0, burst.takeDropped());
	}

	@Test
	public void aHandfulPostsOnceTheMinuteIsOver()
	{
		for (int i = 0; i < LoginBurst.MAX_POSTS; i++)
		{
			burst.hold(diary("Varrock easy"), NOW + i);
		}
		List<GroupEvent> out = burst.due(NOW + LoginBurst.WINDOW_MS);
		assertEquals(LoginBurst.MAX_POSTS, out.size());
		assertEquals(0, burst.takeDropped());
		assertTrue("posted once", burst.due(NOW + LoginBurst.WINDOW_MS + 1).isEmpty());
	}

	@Test
	public void afterTheMinuteAndForOtherKindsNothingIsHeld()
	{
		assertFalse("an online post is no achievement",
			burst.hold(GroupEvent.of(GroupEvent.Type.ONLINE, NOW, "Ship Wrek").text("online").build(), NOW + 1));
		assertFalse("a mid-session diary posts at once", burst.hold(diary("Falador easy"), NOW + LoginBurst.WINDOW_MS));
	}

	@Test
	public void aLogoutInsideTheMinuteSettlesItThen()
	{
		burst.hold(diary("Varrock easy"), NOW + 1);
		assertEquals(1, burst.end().size());
		assertFalse("the window is closed", burst.hold(diary("Falador easy"), NOW + 2));
	}

	@Test
	public void memberBroadcastsCountEveryKind()
	{
		LoginBurst members = new LoginBurst(t -> true);
		members.open(NOW);
		assertTrue(members.hold(GroupEvent.of(GroupEvent.Type.DROP, NOW, "GroupOtt").broadcast(true).build(), NOW));
	}
}
