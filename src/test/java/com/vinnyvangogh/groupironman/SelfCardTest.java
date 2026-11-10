package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static com.vinnyvangogh.groupironman.FakeItems.*;

/**
 * Your own card is built from your own client while you are logged in, with
 * or without group sync and whatever the cache holds; only the sends wait
 * for sync.
 */
public class SelfCardTest
{
	private static final FakeItems ITEMS = new FakeItems();
	private static final long NOW = TimeUnit.DAYS.toMillis(100);
	private static final long MIN = TimeUnit.MINUTES.toMillis(1);
	private static final RegionNames REGIONS = RegionNames.parse("Lumbridge=12850\n");
	private static final GroupOutbox.Switches ALL = new GroupOutbox.Switches(true, true, true, true, true, true);
	private static final GroupOutbox.Switches NONE = new GroupOutbox.Switches(false, false, false, false, false, false);
	private static final String ME = "GroupShip";

	private final GroupRoster roster = new GroupRoster();
	private final List<String> sent = new ArrayList<>();
	private int redraws;
	private final SelfCard card = new SelfCard(roster, new Gson(), sent::add, () -> redraws++);

	private static Map<Integer, Integer> items(int... pairs)
	{
		Map<Integer, Integer> out = new TreeMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			out.put(pairs[i], pairs[i + 1]);
		}
		return out;
	}

	private static MemberStats stats(int hp)
	{
		Map<String, MemberStats.Level> skills = new LinkedHashMap<>();
		skills.put("Attack", new MemberStats.Level(90, 90));
		skills.put("Hitpoints", new MemberStats.Level(99, hp));
		skills.put("Prayer", new MemberStats.Level(70, 70));
		return new MemberStats(skills, 87, 100, 302, 12850, 3222, 3218, 0);
	}

	@Test
	public void loggedInWithAnEmptyCacheAndNoSyncYouShowLiveData()
	{
		card.seen(ME, NOW);
		card.stats(ME, stats(99), ALL, false);
		card.carried(ME, items(SHARK, 20), items(WHIP, 1), ALL, false);
		card.extras(ME, new MemberExtras(items(COAL, 100), items(), items(), -1), ALL, false);

		List<PanelModel.MemberCard> cards = PanelModel.members(roster, Arrays.asList("Alex", ME), REGIONS, ITEMS, ME, NOW);
		PanelModel.MemberCard you = cards.get(0);
		assertTrue(you.you);
		assertTrue(you.online);
		assertEquals("Online - W302, Lumbridge", you.status);
		assertEquals(3, you.skills.size());
		assertEquals(4, you.bars.size());
		assertEquals(Arrays.asList("Worn", "Inventory", "Rune pouch"), titles(you));

		PanelModel.MemberCard alex = cards.get(1);
		assertFalse(alex.online);
		assertEquals("Not seen yet", alex.status);
		assertTrue("nothing goes to a party without sync", sent.isEmpty());
	}

	@Test
	public void sharingOffStillFillsYourOwnCardButSendsNothing()
	{
		card.seen(ME, NOW);
		card.stats(ME, stats(99), NONE, true);
		card.carried(ME, items(SHARK, 20), items(), NONE, true);
		card.extras(ME, new MemberExtras(items(COAL, 100), items(), items(), -1), NONE, true);

		assertNotNull(roster.allStats().get(ME));
		assertNotNull(roster.allCarried().get(ME));
		assertNotNull(roster.extras().get(ME));
		assertTrue(sent.isEmpty());
	}

	@Test
	public void whileSyncingEachChangeIsSentOnceAndRedrawnOnce()
	{
		assertTrue(card.stats(ME, stats(99), ALL, true));
		assertEquals(1, sent.size());
		assertEquals(1, redraws);

		assertFalse("the same stats again are neither sent nor redrawn", card.stats(ME, stats(99), ALL, true));
		assertEquals(1, sent.size());
		assertEquals(1, redraws);

		card.resendStats();
		assertTrue("a heartbeat resends what has not changed", card.stats(ME, stats(99), ALL, true));
		assertEquals(2, sent.size());
		assertEquals("but the card did not change", 1, redraws);

		assertTrue(card.stats(ME, stats(80), ALL, true));
		assertEquals(3, sent.size());
		assertEquals(2, redraws);
	}

	@Test
	public void carriedAndExtrasAreSentOnlyWhenTheyChange()
	{
		assertTrue(card.carried(ME, items(SHARK, 20), items(WHIP, 1), ALL, true));
		assertFalse(card.carried(ME, items(SHARK, 20), items(WHIP, 1), ALL, true));
		assertTrue(card.carried(ME, items(SHARK, 19), items(WHIP, 1), ALL, true));

		MemberExtras pouch = new MemberExtras(items(COAL, 100), items(), items(), -1);
		assertTrue(card.extras(ME, pouch, ALL, true));
		assertFalse(card.extras(ME, pouch, ALL, true));
		card.resend();
		assertTrue(card.carried(ME, items(SHARK, 19), items(WHIP, 1), ALL, true));
		assertTrue(card.extras(ME, pouch, ALL, true));
		assertEquals(5, sent.size());
		assertEquals("first carried, changed carried, first extras", 3, redraws);
	}

	@Test
	public void comingOnlineRedrawsOnceAndLaterTicksDoNot()
	{
		card.seen(ME, NOW);
		card.seen(ME, NOW + 600);
		card.seen(ME, NOW + 1200);
		assertEquals(1, redraws);
		assertTrue(Presence.online(roster.lastHeard().get(ME), NOW + 1200));

		// Back after a logout long enough to read as offline: redraw again.
		card.seen(ME, NOW + 10 * MIN);
		assertEquals(2, redraws);
	}

	@Test
	public void aCardClearedByASwitchIsRefilledOnTheNextCapture()
	{
		card.stats(ME, stats(99), NONE, false);
		roster.clearStats();
		card.stats(ME, stats(99), NONE, false);
		assertNotNull(roster.allStats().get(ME));
		assertEquals(2, redraws);
	}

	private static List<String> titles(PanelModel.MemberCard c)
	{
		List<String> out = new ArrayList<>();
		for (PanelModel.ItemGroup g : c.items)
		{
			out.add(g.title);
		}
		return out;
	}
}
