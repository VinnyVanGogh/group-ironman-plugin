package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The level-up picture (Board 2026-11-11: Smithing 1 → 4 showed the chat of level 3). Driven the way
 * GroupEventEngine drives it: a moment's post is held when the batch flushes it, the chat and every game tick ask
 * for the shots due, and each due shot is one frame for every post waiting on it. The "frame" here is the time it
 * was taken, so a test can tell which one a post got.
 */
public class LevelShotTest
{
	private static final String CONGRATS = "Congratulations, you've just advanced your Smithing level. You are now level ";
	private static final long TICK = 600;

	private final LevelShot shot = new LevelShot();
	private final List<long[]> sent = new ArrayList<>();

	/** A post that records the frame it was sent with, and its own tag. */
	private Consumer<byte[]> post(long tag)
	{
		return png -> sent.add(new long[]{tag, png == null ? -1 : png[0]});
	}

	/** One frame per due shot, stamped with the time it was taken. */
	private int shoot(long now)
	{
		List<LevelShot.Held> due = shot.due(now);
		for (LevelShot.Held h : due)
		{
			byte[] frame = {(byte) ((now - NOW) / 100)};
			h.sends.forEach(s -> s.accept(frame));
		}
		return due.size();
	}

	@Test
	public void waitsForTheChatLineAndShootsOnIt()
	{
		// The level registers at NOW; the batch flushes the moment 1.2 s later, before the chat line is in.
		shot.hold("Smithing", 4, NOW, post(1));
		assertEquals(0, shoot(NOW + 1_200));
		assertTrue(shot.chat(CONGRATS + "4.", NOW + 1_500));
		assertEquals(1, shoot(NOW + 1_500));
		assertEquals(1, sent.size());
		assertEquals(15, sent.get(0)[1]);
	}

	@Test
	public void aChatLineAlreadyInShootsAtOnce()
	{
		// The game's line usually lands on the same tick the level registers, ahead of the batch's flush.
		shot.chat("<col=ff0000>" + CONGRATS + "4.</col>", NOW + 100);
		shot.hold("Smithing", 4, NOW, post(1));
		assertEquals(1, shoot(NOW + 1_200));
	}

	@Test
	public void noChatLineShootsAtTwoSeconds()
	{
		shot.hold("Smithing", 4, NOW, post(1));
		for (long t = NOW; t < NOW + LevelShot.WAIT; t += TICK)
		{
			assertEquals("before " + LevelShot.WAIT + " ms at +" + (t - NOW), 0, shoot(t));
		}
		assertEquals(1, shoot(NOW + LevelShot.WAIT));
		assertEquals(20, sent.get(0)[1]);
	}

	@Test
	public void anOlderLevelsLineDoesNotCount()
	{
		shot.chat(CONGRATS + "3.", NOW - 500);
		shot.hold("Smithing", 4, NOW, post(1));
		assertEquals(0, shoot(NOW + 1_200));
	}

	@Test
	public void anotherSkillsLineDoesNotCount()
	{
		shot.hold("Smithing", 4, NOW, post(1));
		shot.chat("Congratulations, you've just advanced your Mining level. You are now level 4.", NOW + 300);
		assertEquals(0, shoot(NOW + 300));
	}

	@Test
	public void aSecondLevelWithinTheWaitGetsTheNewerFrameForBoth()
	{
		shot.hold("Smithing", 3, NOW, post(3));
		// Level 4 a second later: its moment starts and flushes the level-3 one (the batch's repeat rule).
		shot.hold("Smithing", 4, NOW + 1_000, post(4));
		shot.chat(CONGRATS + "3.", NOW + 1_100);
		assertEquals("level 3's line is not level 4's", 0, shoot(NOW + 1_100));
		shot.chat(CONGRATS + "4.", NOW + 1_700);
		assertEquals(1, shoot(NOW + 1_700));
		assertEquals(2, sent.size());
		assertArrayEquals(new long[]{3, 17}, sent.get(0));
		assertArrayEquals(new long[]{4, 17}, sent.get(1));
	}

	@Test
	public void theWaitIsCappedAtThreeSecondsFromTheFirstLevel()
	{
		shot.hold("Smithing", 3, NOW, post(3));
		shot.hold("Smithing", 4, NOW + 1_800, post(4));
		assertEquals(0, shoot(NOW + 2_900));
		assertEquals(1, shoot(NOW + LevelShot.MAX_WAIT));
		assertEquals(2, sent.size());
	}

	@Test
	public void runecraftMatchesTheChatsRunecrafting()
	{
		shot.hold("Runecraft", 10, NOW, post(1));
		shot.chat("Congratulations, you've just advanced your Runecrafting level. You are now level 10.", NOW + 200);
		assertEquals(1, shoot(NOW + 200));
	}

	@Test
	public void otherChatIsNotALevelLine()
	{
		assertFalse(shot.chat("Your Vorkath kill count is: 120.", NOW));
	}

	@Test
	public void aHopOrLogoutShootsEverythingWaiting()
	{
		shot.hold("Smithing", 4, NOW, post(1));
		shot.hold("Mining", 9, NOW, post(2));
		assertEquals(2, shot.drain().size());
		assertEquals(0, shoot(NOW + 5_000));
	}

	@Test
	public void theCombinedPostsEditCarriesTheNewerFrame()
	{
		// Wired as the engine's combineLevels does: each held send is a level into its streak.
		LevelThreads threads = new LevelThreads();
		GroupEvent three = GroupEvent.of(GroupEvent.Type.LEVEL_UP, NOW, "Me").text("Smithing").number(3).previous(2)
			.build();
		GroupEvent four = GroupEvent.of(GroupEvent.Type.LEVEL_UP, NOW + 1_000, "Me").text("Smithing").number(4)
			.previous(3).build();
		List<LevelThreads.Streak> fresh = new ArrayList<>();
		long window = 600_000;
		shot.hold("Smithing", 3, NOW, png -> fresh.add(threads.level(three, "url", NOW + 1_700, window, png)));
		shot.hold("Smithing", 4, NOW + 1_000, png -> assertNull(threads.level(four, "url", NOW + 1_700, window, png)));
		shot.chat(CONGRATS + "4.", NOW + 1_700);
		shoot(NOW + 1_700);
		threads.posted(fresh.get(0), 200, "msg", NOW + 2_000);
		List<LevelThreads.Edit> edits = threads.due(NOW + 2_000 + LevelThreads.EDIT_GAP);
		assertEquals(1, edits.size());
		assertEquals(17, edits.get(0).png[0]);
		assertEquals(17, fresh.get(0).png[0]);
	}
}
