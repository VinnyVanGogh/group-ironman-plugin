package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Each medium kind: detected from a game line or variable change, fires once, silent when its switch is off. */
public class EventWatchersTest
{
	private final List<GroupEvent> out = new ArrayList<>();
	private final Map<Integer, Integer> vars = new HashMap<>();
	private EnumSet<GroupEvent.Type> on;
	private EventWatchers watchers;

	@Before
	public void setUp()
	{
		on = EnumSet.allOf(GroupEvent.Type.class);
		watchers = new EventWatchers(out::add, t -> on.contains(t));
	}

	private void chat(String line)
	{
		watchers.chat(line, NOW, "Alex", Collections.singleton("Sam"), Arrays.asList("Alex", "Sam", "Stranger"));
	}

	private GroupEvent only()
	{
		assertEquals(1, out.size());
		return out.get(0);
	}

	private void silentWhenOff(GroupEvent.Type type, String line)
	{
		on.remove(type);
		out.clear();
		chat(line);
		assertTrue(type + " off is silent", out.isEmpty());
	}

	private void poll()
	{
		watchers.poll(id -> vars.getOrDefault(id, 0), NOW, "Alex");
	}

	@Test
	public void raidUniqueNamesTheTeamMembersInTheGroupAndCountsTheRest()
	{
		watchers.raidCount("Theatre of Blood", 12);
		chat("<col=ef20ff>Alex found something special: Scythe of vitur (uncharged)</col>");
		GroupEvent e = only();
		assertEquals(GroupEvent.Type.RAID_UNIQUE, e.type);
		assertEquals("Scythe of vitur (uncharged)", e.text);
		assertEquals(12, e.number);
		assertEquals("Theatre of Blood; with Sam +1 other", e.detail);
		assertTrue(e.isBig());
		silentWhenOff(GroupEvent.Type.RAID_UNIQUE, "Alex found something special: Scythe of vitur (uncharged)");
	}

	@Test
	public void aTeammatesPurpleIsTheirsToPost()
	{
		chat("Sam found something special: Scythe of vitur (uncharged)");
		assertTrue(out.isEmpty());
	}

	@Test
	public void chambersLootNeedsItsHeaderLine()
	{
		chat("Alex - Twisted bow");
		assertTrue("a bare dash line is not loot", out.isEmpty());
		chat("Special loot:");
		chat("Alex - Twisted bow");
		assertEquals("Twisted bow", only().text);
	}

	@Test
	public void clueCountsAreKeptByTierAndValuedByTheReward()
	{
		chat("You have completed <col=ff0000>42</col> medium Treasure Trails.");
		assertTrue("waits for the reward", out.isEmpty());
		watchers.clueReward(EventFixtures.items(FakeItems.WHIP, 1), 1_500_000, NOW, "Alex");
		GroupEvent e = only();
		assertEquals(GroupEvent.Type.CLUE, e.type);
		assertEquals("medium", e.text);
		assertEquals(1_500_000, e.number);
		assertEquals("medium 42", e.detail);
		out.clear();
		watchers.clueReward(EventFixtures.items(FakeItems.WHIP, 1), 1_500_000, NOW, "Alex");
		assertTrue("one reward per completed line", out.isEmpty());
		chat("You have completed 3 master Treasure Trails.");
		watchers.clueReward(EventFixtures.items(FakeItems.WHIP, 1), 5, NOW, "Alex");
		assertEquals("medium 42, master 3", only().detail);
	}

	@Test
	public void aClueBelowTheThresholdIsDroppedByTheRouter()
	{
		GroupEvent clue = GroupEvent.of(GroupEvent.Type.CLUE, NOW, "Alex").text("easy").number(10).build();
		EventSettings s = new EventSettings(EventFixtures.all(), EventFixtures.none(), 0, 0, 1, 7, false, 1_000,
			100, 5, EnumSet.noneOf(GroupEvent.Category.class), 20);
		assertNull(EventRouter.threshold(clue, s, new FakeItems()));
	}

	@Test
	public void kcMilestonesFireEveryNOnceEach()
	{
		watchers.kill("Vorkath", 99, 100, NOW, "Alex");
		assertTrue(out.isEmpty());
		watchers.kill("Vorkath", 100, 100, NOW, "Alex");
		GroupEvent e = only();
		assertEquals(GroupEvent.Type.KC_MILESTONE, e.type);
		assertEquals("Vorkath", e.text);
		assertEquals(100, e.number);
		out.clear();
		watchers.kill("Vorkath", 100, 100, NOW, "Alex");
		assertTrue("the same line twice is one milestone", out.isEmpty());
		on.remove(GroupEvent.Type.KC_MILESTONE);
		watchers.kill("Vorkath", 200, 100, NOW, "Alex");
		assertTrue(out.isEmpty());
	}

	@Test
	public void personalBestsCombatTasksAndSlayer()
	{
		chat("Fight duration: <col=ff0000>1:23</col> (new personal best)");
		assertEquals("1:23", only().text);
		assertEquals(GroupEvent.Type.PERSONAL_BEST, out.get(0).type);
		silentWhenOff(GroupEvent.Type.PERSONAL_BEST, "Fight duration: <col=ff0000>1:23</col> (new personal best)");
		on.add(GroupEvent.Type.PERSONAL_BEST);
		out.clear();
		chat("Fight duration: <col=ff0000>1:40</col>. Personal best: 1:23");
		assertTrue("not a new best", out.isEmpty());

		chat("Congratulations, you've completed an elite combat task: <col=06600c>Peach Conjurer</col> (4 points).");
		GroupEvent ca = only();
		assertEquals(GroupEvent.Type.COMBAT_TASK, ca.type);
		assertEquals("Peach Conjurer", ca.text);
		assertEquals("elite", ca.detail);
		silentWhenOff(GroupEvent.Type.COMBAT_TASK,
			"Congratulations, you've completed an elite combat task: <col=06600c>Peach Conjurer</col> (4 points).");

		on.add(GroupEvent.Type.COMBAT_TASK);
		out.clear();
		chat("You have completed your task! You killed 19 Icefiends. You gained 1,520 xp.");
		assertTrue("waits for its streak line", out.isEmpty());
		chat("You've completed 123 tasks and received 15 points, giving you a total of 240; return to a Slayer master.");
		GroupEvent done = only();
		assertEquals(GroupEvent.Type.SLAYER, done.type);
		assertEquals("Slayer task done: Icefiends (streak 123, 15 points)", EventText.describe(done, new FakeItems()));
		out.clear();
		chat("You have received a new Slayer assignment from Konar: Hellhounds (150)");
		assertEquals("Hellhounds", only().text);
		assertEquals(150, out.get(0).number);
		out.clear();
		chat("You're assigned to kill hellhounds; only 150 more to go.");
		assertTrue("the gem repeats the task it was given", out.isEmpty());
		silentWhenOff(GroupEvent.Type.SLAYER, "You have received a new Slayer assignment from Konar: Hellhounds (150)");
	}

	@Test
	public void firstPollIsABaselineNotAnEvent()
	{
		vars.put(EventWatchers.SKULL, 1);
		vars.put(net.runelite.api.gameval.VarbitID.INSIDE_WILDERNESS, 1);
		vars.put(net.runelite.api.gameval.VarbitID.VARROCK_DIARY_EASY_COMPLETE, 1);
		poll();
		assertTrue(out.isEmpty());
		poll();
		assertTrue("unchanged stays silent", out.isEmpty());
	}

	@Test
	public void wildernessAndSkullFireOnEntryOnly()
	{
		poll();
		vars.put(net.runelite.api.gameval.VarbitID.INSIDE_WILDERNESS, 1);
		poll();
		GroupEvent e = only();
		assertEquals(GroupEvent.Type.WILDERNESS, e.type);
		assertEquals("entered the Wilderness", e.text);
		out.clear();
		poll();
		assertTrue("fires once", out.isEmpty());
		vars.put(EventWatchers.SKULL, 1);
		poll();
		assertEquals("got skulled", only().text);
		out.clear();
		on.remove(GroupEvent.Type.WILDERNESS);
		vars.put(EventWatchers.SKULL, 0);
		poll();
		vars.put(EventWatchers.SKULL, 1);
		poll();
		assertTrue(out.isEmpty());
	}

	@Test
	public void aDiaryTierFiresOnceAndNamesAreaAndTier()
	{
		poll();
		vars.put(net.runelite.api.gameval.VarbitID.VARROCK_DIARY_HARD_COMPLETE, 1);
		poll();
		GroupEvent e = only();
		assertEquals(GroupEvent.Type.DIARY, e.type);
		assertEquals("Varrock hard", e.text);
		out.clear();
		poll();
		assertTrue(out.isEmpty());
		on.remove(GroupEvent.Type.DIARY);
		vars.put(net.runelite.api.gameval.VarbitID.VARROCK_DIARY_ELITE_COMPLETE, 1);
		poll();
		assertTrue(out.isEmpty());
	}

	@Test
	public void aHardcoreLifeLostSaysHowManyAreLeft()
	{
		vars.put(net.runelite.api.gameval.VarbitID.GIM_GROUPSIZE, 3);
		poll();
		vars.put(net.runelite.api.gameval.VarbitID.GIM_HARDCORE_DEATHS, 1);
		poll();
		GroupEvent e = only();
		assertEquals(GroupEvent.Type.HC_LIFE_LOST, e.type);
		assertEquals(2, e.number);
		assertTrue(e.isBig());
		out.clear();
		poll();
		assertTrue(out.isEmpty());
	}

	@Test
	public void aResetBaselinesTheNextAccount()
	{
		poll();
		watchers.reset();
		vars.put(net.runelite.api.gameval.VarbitID.VARROCK_DIARY_EASY_COMPLETE, 1);
		poll();
		assertTrue(out.isEmpty());
	}

	@Test
	public void deathValueIsWhatLeftTheCarriedItems()
	{
		FakeItems items = new FakeItems();
		watchers.died(EventFixtures.items(FakeItems.WHIP, 1, FakeItems.SHARK, 10), "Lumbridge", 302);
		for (int i = 0; i < EventWatchers.DEATH_SETTLE_TICKS - 1; i++)
		{
			watchers.deathTick(EventFixtures.items(FakeItems.SHARK, 10), items, NOW, "Alex");
			assertTrue(out.isEmpty());
		}
		watchers.deathTick(EventFixtures.items(FakeItems.SHARK, 10), items, NOW, "Alex");
		GroupEvent e = only();
		assertEquals(GroupEvent.Type.DEATH_VALUE, e.type);
		assertEquals(EventFixtures.items(FakeItems.WHIP, 1), e.items);
		assertEquals(1_500_000, e.number);
		assertEquals("Lumbridge", e.location);
		assertEquals(302, e.world);
		out.clear();
		watchers.deathTick(EventFixtures.items(), items, NOW, "Alex");
		assertTrue("fires once", out.isEmpty());
	}

	@Test
	public void theGravestoneWarnsOnceBeforeItExpires()
	{
		watchers.died(EventFixtures.items(), "Lumbridge", 302);
		int warnAt = EventWatchers.GRAVE_TICKS - 5 * EventWatchers.TICKS_PER_MINUTE;
		for (int i = 1; i < warnAt; i++)
		{
			watchers.graveTick(1, 5, NOW, "Alex");
			assertTrue("tick " + i, out.isEmpty());
		}
		watchers.graveTick(1, 5, NOW, "Alex");
		GroupEvent e = only();
		assertEquals(GroupEvent.Type.GRAVESTONE_EXPIRING, e.type);
		assertEquals(5, e.number);
		assertEquals("Lumbridge", e.location);
		out.clear();
		watchers.graveTick(1, 5, NOW, "Alex");
		assertTrue(out.isEmpty());
	}

	@Test
	public void aCollectedGraveNeverWarns()
	{
		watchers.died(EventFixtures.items(), "Lumbridge", 302);
		watchers.graveTick(1, 5, NOW, "Alex");
		watchers.graveTick(0, 5, NOW, "Alex");
		for (int i = 0; i < EventWatchers.GRAVE_TICKS; i++)
		{
			watchers.graveTick(0, 5, NOW, "Alex");
		}
		assertTrue(out.isEmpty());
	}

	@Test
	public void aDeathWithNothingLostNeverWarns()
	{
		// Nothing to lose, or a safe death (minigame): everything is still carried once the death settles.
		FakeItems items = new FakeItems();
		watchers.died(EventFixtures.items(FakeItems.SHARK, 10), "Castle Wars", 302);
		for (int i = 0; i < EventWatchers.DEATH_SETTLE_TICKS; i++)
		{
			watchers.deathTick(EventFixtures.items(FakeItems.SHARK, 10), items, NOW, "Alex");
		}
		assertFalse("no grave spawned", watchers.graveOut());
		out.clear();
		for (int i = 0; i < EventWatchers.GRAVE_TICKS; i++)
		{
			watchers.graveTick(1, 5, NOW, "Alex");
		}
		assertTrue(out.isEmpty());
	}

	@Test
	public void aGraveNeverSeenNeverWarns()
	{
		// Items lost but the grave never showed (an instanced death, items to a reclaim NPC): no warning.
		watchers.died(EventFixtures.items(FakeItems.WHIP, 1), "Zul-Andra", 302);
		for (int i = 0; i < EventWatchers.GRAVE_TICKS; i++)
		{
			watchers.graveTick(0, 5, NOW, "Alex");
		}
		assertTrue(out.isEmpty());
		assertFalse(watchers.graveOut());
	}

	@Test
	public void theGravestoneIsSilentWhenOff()
	{
		on.remove(GroupEvent.Type.GRAVESTONE_EXPIRING);
		watchers.died(EventFixtures.items(), "Lumbridge", 302);
		for (int i = 0; i < EventWatchers.GRAVE_TICKS; i++)
		{
			watchers.graveTick(1, 5, NOW, "Alex");
		}
		assertTrue(out.isEmpty());
		assertFalse(on.contains(GroupEvent.Type.GRAVESTONE_EXPIRING));
	}
}
