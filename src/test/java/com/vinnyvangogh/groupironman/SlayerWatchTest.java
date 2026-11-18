package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Slayer posts from the game's own lines. The lines are the Board's live test (2026-11-10) and RuneLite's
 * SlayerPluginTest at runelite/runelite 1f6ae893^ (:85-126), tags as the game sends them.
 */
public class SlayerWatchTest
{
	private static final FakeItems ITEMS = new FakeItems();

	private final List<GroupEvent> out = new ArrayList<>();
	private final SlayerWatch watch = new SlayerWatch(out::add);

	private void chat(String line)
	{
		watch.chat(line, NOW, "Alex");
	}

	private void dialog(String text, String npc)
	{
		watch.dialog(text, npc, NOW, "Alex");
	}

	/** The one post made since the last call, as Discord words it. */
	private String said()
	{
		assertEquals(out.toString(), 1, out.size());
		String s = EventText.describe(out.get(0), ITEMS);
		out.clear();
		return s;
	}

	private void silent(String why)
	{
		assertTrue(why, out.isEmpty());
	}

	@Test
	public void theBoardsGemCheckPostsNothing()
	{
		chat("You're assigned to kill icefiends; only 19 more to go.");
		chat("You've completed 0 tasks in a row and currently have a total of 0 points.");
		silent("a gem check is neither a new task nor a finished one");
	}

	@Test
	public void theMastersDialogueIsTheNewTaskAndTheGemNeverRepostsIt()
	{
		dialog("Your new task is to kill 19 Icefiends.", "Turael");
		assertEquals("New Slayer task: 19 x Icefiends (Turael)", said());
		dialog("Your new task is to kill 19 Icefiends.", "Turael");
		silent("the same dialogue still open");
		dialog(null, "");
		chat("You're assigned to kill icefiends; only 19 more to go.");
		chat("You've completed 0 tasks in a row and currently have a total of 0 points.");
		chat("You're assigned to kill icefiends; only 12 more to go.");
		silent("the gem repeats the task");
	}

	@Test
	public void everyWayAMasterHandsOutATask()
	{
		dialog("We'll start you off hunting goblins, you'll need to kill 17 of them.", "Turael");
		assertEquals("New Slayer task: 17 x Goblins (Turael)", said());
		dialog("Excellent, you're doing great. Your new task is to kill<br>211 Suqahs.", "<col=ffff00>Duradel</col>");
		assertEquals("New Slayer task: 211 x Suqahs (Duradel)", said());
		dialog("You are to bring balance to 147 Wyrms in the Karuulm Slayer Dungeon.", "Konar quo Maten");
		assertEquals("New Slayer task: 147 x Wyrms (Konar quo Maten)", said());
		dialog("You are to bring balance to 135 Trolls south of Mount Quidamortem.", "Konar quo Maten");
		assertEquals("New Slayer task: 135 x Trolls (Konar quo Maten)", said());
		dialog("Excellent. You're now assigned to kill the Chaos <br>Elemental 3 times. Your reward point tally is 914.",
			"Duradel");
		assertEquals("New Slayer task: 3 x Chaos Elemental (Duradel)", said());
		chat("You have received a new Slayer assignment from breaklulz: Dust Devils (377)");
		assertEquals("New Slayer task: 377 x Dust Devils (breaklulz)", said());
	}

	@Test
	public void theMasterRepeatingTheTaskIsNotNew()
	{
		dialog("You're still hunting suqahs; you have 222 to go. Come<br>back when you've finished your task.", "Duradel");
		chat("You're assigned to kill Suqahs; only 211 more to go.");
		silent("held tasks, never new ones");
	}

	@Test
	public void theGemsCheckDialogueIsABaselineToo()
	{
		dialog("You're currently assigned to kill fossil island wyverns; only 23 more to go. Your reward point tally is 46.",
			"");
		chat("You're assigned to kill fossil island wyverns; only 22 more to go.");
		silent("the gem's own dialogue repeats the task");
	}

	@Test
	public void theFirstGemCheckIsABaselineAndAChangedTaskPosts()
	{
		chat("You're assigned to kill Suqahs in the Wilderness; only 211 more to go.");
		silent("the first check after a login only learns the task");
		chat("You're assigned to kill Blue dragons in the Ogre Enclave; only 122 more to go.");
		assertEquals("New Slayer task: 122 x Blue dragons", said());
	}

	@Test
	public void theCompletionLinesNameTheTaskAndTheStreak()
	{
		dialog("Your new task is to kill 19 Icefiends.", "Turael");
		out.clear();
		chat("You have completed your task! You killed 19 Icefiends. You gained 1,520 xp.");
		silent("waits for the streak line");
		chat("<col=ef1020>You've completed </col>1 task<col=ef1020> and received </col>2 points<col=ef1020>, giving you a"
			+ " total of </col>2<col=ef1020>; return to a Slayer master.");
		assertEquals("Slayer task done: Icefiends (streak 1, 2 points)", said());
		chat("You're assigned to kill bears; only 30 more to go.");
		assertEquals("after a finished task, any task the gem names is new", "New Slayer task: 30 x Bears", said());
	}

	@Test
	public void runeLitesCompletionLines()
	{
		String[][] lines = {
			{"<col=ef1020>You've completed </col>1 task<col=ef1020> and will need</col> 4 more <col=ef1020>before you"
				+ " start receiving Slayer points; return to a Slayer master.</col>", "streak 1"},
			{"<col=ef1020>You've completed </col>9 tasks <col=ef1020>and received </col>10 points<col=ef1020>, giving you"
				+ " a total of </col>18,000<col=ef1020>; return to a Slayer master.", "streak 9, 10 points"},
			{"<col=ef1020>You've completed </col>2,465 tasks <col=ef1020>and received </col>15 points<col=ef1020>, giving"
				+ " you a total of </col>131,071<col=ef1020>; return to a Slayer master.", "streak 2465, 15 points"},
			{"<col=ef1020>You've completed </col>104 tasks <col=ef1020>. You'll be eligible to earn reward points if you"
				+ " complete tasks from a more advanced Slayer Master.", "streak 104"},
			{"<col=ef1020>You've completed at least </col>16,000 tasks <col=ef1020>and received </col>15 points"
				+ "<col=ef1020>, giving you a total of </col>131,071<col=ef1020>; return to a Slayer master.",
				"streak 16000, 15 points"},
			{"<col=ef1020>You've completed </col>9 tasks <col=ef1020>and reached the maximum amount of Slayer points"
				+ " </col>(131,071)<col=ef1020>; return to a Slayer master.", "streak 9"},
			{"<col=ef1020>You've completed </col>9 Wilderness tasks <col=ef1020>and received </col>10 points"
				+ "<col=ef1020>, giving you a total of </col>18,000<col=ef1020>; return to a Slayer master.",
				"streak 9, 10 points"},
		};
		for (String[] line : lines)
		{
			chat(line[0]);
			assertEquals(line[0], 1, out.size());
			assertEquals(line[0], line[1], out.get(0).detail);
			assertEquals("a finished task carries no amount", 0, out.get(0).number);
			out.clear();
		}
	}

	@Test
	public void aCompletionWithoutItsStreakLinePostsAfterItsWait()
	{
		chat("You have completed your task! You killed 245 Cave Kraken. You gained 62,475 xp.");
		watch.tick(NOW, "Alex");
		silent("one tick in");
		watch.tick(NOW, "Alex");
		assertEquals("Slayer task done: Cave Kraken", said());
		watch.tick(NOW, "Alex");
		silent("once");
	}

	@Test
	public void aCancelledTaskMeansTheNextOneIsNew()
	{
		chat("You're assigned to kill Suqahs; only 211 more to go.");
		chat("Your task has been cancelled.");
		chat("You're assigned to kill Suqahs; only 180 more to go.");
		assertEquals("New Slayer task: 180 x Suqahs", said());
	}

	@Test
	public void aLoginStartsTheBaselineOver()
	{
		chat("You're assigned to kill Suqahs; only 211 more to go.");
		watch.reset();
		chat("You're assigned to kill Bears; only 30 more to go.");
		silent("another account, or the same after a logout: a baseline again");
	}

	@Test
	public void otherLinesAreNotSlayer()
	{
		assertNull(EventChat.slayerDone("You've completed 0 tasks in a row and currently have a total of 0 points."));
		assertNull(EventChat.slayerChat("You have completed 42 medium Treasure Trails."));
		assertNull(EventChat.slayerDialog("Hello, adventurer.", "Turael"));
	}

	@Test
	public void anOlderMembersSlayerLinesReadAsTheyDid()
	{
		GroupEvent done = GroupEvent.of(GroupEvent.Type.SLAYER, NOW, "Sam").text("task done").number(123).build();
		assertEquals("slayer task done (123)", EventText.describe(done, ITEMS));
		GroupEvent task = GroupEvent.of(GroupEvent.Type.SLAYER, NOW, "Sam").text("new task: Hellhounds").number(150).build();
		assertEquals("slayer new task: Hellhounds (150)", EventText.describe(task, ITEMS));
	}
}
