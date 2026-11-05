package com.vinnyvangogh.groupironman;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static com.vinnyvangogh.groupironman.FakeItems.*;

/** What each card, grid and header line of the panel shows. */
public class PanelModelTest
{
	private static final FakeItems ITEMS = new FakeItems();
	private static final long NOW = TimeUnit.DAYS.toMillis(100);
	private static final long MIN = TimeUnit.MINUTES.toMillis(1);
	private static final RegionNames REGIONS = RegionNames.parse("Lumbridge=12850\nDraynor=12338\n");

	private static Map<Integer, Integer> items(int... pairs)
	{
		Map<Integer, Integer> out = new TreeMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			out.put(pairs[i], pairs[i + 1]);
		}
		return out;
	}

	private static MemberStats stats(int world, int region)
	{
		Map<String, MemberStats.Level> skills = new LinkedHashMap<>();
		skills.put("Defence", new MemberStats.Level(1, 1));
		skills.put("Attack", new MemberStats.Level(90, 98));
		skills.put("Hitpoints", new MemberStats.Level(99, 72));
		skills.put("Prayer", new MemberStats.Level(70, 67));
		skills.put("Mining", new MemberStats.Level(50, 50));
		return new MemberStats(skills, 87, 50, world, region, 3222, 3218, 0);
	}

	@Test
	public void onlineCardHasStatusBarsSkillsInTabOrderAndItemGrids()
	{
		GroupRoster roster = new GroupRoster();
		roster.heard("Alex", NOW - MIN);
		roster.putStats("Alex", stats(302, 12850));
		roster.putCarried("Alex", new GroupCodec.Carried(items(SHARK, 20), items(WHIP, 1)));
		roster.putExtras("Alex", new MemberExtras(items(COAL, 100), items(), items(SHARK, 4), NOW - 120 * MIN));

		PanelModel.MemberCard c = PanelModel.members(roster, Collections.emptyList(), REGIONS, ITEMS, "Me", NOW).get(0);
		assertEquals("Alex", c.name);
		assertTrue(c.online);
		assertEquals("Online - W302, Lumbridge", c.status);

		assertEquals(4, c.bars.size());
		assertEquals("HP", c.bars.get(0).label);
		assertEquals("72/99", c.bars.get(0).text());
		assertEquals("67/70", c.bars.get(1).text());
		assertEquals("87/100", c.bars.get(2).text());
		assertEquals("50/100", c.bars.get(3).text());

		// The skills tab reads Attack, Hitpoints, Mining across the first row, then Strength, ... Defence.
		assertEquals(Arrays.asList("Attack", "Hitpoints", "Mining", "Defence", "Prayer"),
			Arrays.asList(c.skills.get(0).skill, c.skills.get(1).skill, c.skills.get(2).skill, c.skills.get(3).skill,
				c.skills.get(4).skill));
		assertEquals("98", c.skills.get(0).text());
		assertEquals("Attack 90 (+8)", c.skills.get(0).tooltip());

		assertEquals("Worn", c.items.get(0).title);
		assertEquals(WHIP, c.items.get(0).items.get(0).id);
		assertEquals("Inventory", c.items.get(1).title);
		assertEquals("20 x Shark", c.items.get(1).items.get(0).tooltip());
		assertEquals("Rune pouch", c.items.get(2).title);
		assertEquals(3, c.items.size());
		assertEquals("Seed vault: 1 kinds, 4 seeds (seen 2h ago)", c.seedVault);
	}

	@Test
	public void offlineCardSaysWhereTheyWereAndWhen()
	{
		GroupRoster roster = new GroupRoster();
		roster.heard("Sam", NOW - 2 * 60 * MIN);
		roster.putStats("Sam", stats(303, 12338));
		PanelModel.MemberCard c = PanelModel.members(roster, Collections.emptyList(), REGIONS, ITEMS, "Me", NOW).get(0);
		assertFalse(c.online);
		assertEquals("Offline - last seen W303, Draynor, 2h ago", c.status);
		assertFalse("the last stats are kept", c.skills.isEmpty());
	}

	@Test
	public void rosterMembersWithNothingYetStillGetACardAndYouComeFirst()
	{
		GroupRoster roster = new GroupRoster();
		roster.heard("Me", NOW);
		List<PanelModel.MemberCard> cards = PanelModel.members(roster, Arrays.asList("Alex", "Me", "Zed"), REGIONS,
			ITEMS, "me", NOW);
		assertEquals(3, cards.size());
		assertEquals("Me", cards.get(0).name);
		assertTrue(cards.get(0).you);
		assertEquals("Not seen yet", cards.get(1).status);
		assertTrue(cards.get(1).bars.isEmpty());
		assertTrue(cards.get(1).skills.isEmpty());
	}

	@Test
	public void bankCardsAreIconGridsMostValuableFirstAndFilterable()
	{
		GroupRoster roster = new GroupRoster();
		roster.putBank("Alex", new BankSnapshot(NOW - 2 * 60 * MIN, items(SHARK, 100, WHIP, 2, COAL, 5)));
		PanelModel.BankCard b = PanelModel.banks(roster, ITEMS, "", NOW).get(0);
		// 100 x 800 + 2 x 1,500,000 + 5 x 150 = 3,080,750
		assertEquals("107 items, 3.1M gp, seen 2h ago", b.summary);
		assertEquals(Arrays.asList(WHIP, SHARK, COAL),
			Arrays.asList(b.items.get(0).id, b.items.get(1).id, b.items.get(2).id));

		PanelModel.BankCard filtered = PanelModel.banks(roster, ITEMS, "shark", NOW).get(0);
		assertEquals(1, filtered.items.size());
		assertEquals(SHARK, filtered.items.get(0).id);
		assertEquals("the summary stays the whole bank", b.summary, filtered.summary);
	}

	@Test
	public void storageNeverSeenSaysSo()
	{
		PanelModel.StorageView v = PanelModel.storage(new GroupRoster(), ITEMS, "Me", null, NOW);
		assertNull(v.snapshot);
		assertTrue(v.items.isEmpty());
		assertTrue(v.log.isEmpty());
	}

	@Test
	public void findRowsNameTheItemCountAndPlace()
	{
		GroupRoster roster = new GroupRoster();
		roster.putCarried("Sam", new GroupCodec.Carried(items(SHARK, 5), items()));
		roster.putStorage(new StorageChange(NOW - 3 * MIN, "Sam", items(), items(SHARK, 1), items(SHARK, 40)));
		List<PanelModel.FindRow> rows = PanelModel.find("shark", roster, ITEMS, NOW);
		assertEquals("Shark 5 - Sam inventory", rows.get(0).text);
		assertEquals("", rows.get(0).ago);
		assertEquals("Shark 40 - group storage", rows.get(1).text);
		assertEquals("3m ago", rows.get(1).ago);
	}

	@Test
	public void headerSaysWhatToDoNextInEveryState()
	{
		GroupMembers group = GroupMembers.of("GroupShip", Arrays.asList("Alex", "Me"));
		assertEquals("No group key yet", SyncHeader.of(GroupSync.State.NO_KEY, 0, group).title);
		assertEquals(Arrays.asList(SyncHeader.Button.CREATE_KEY, SyncHeader.Button.PASTE_KEY),
			SyncHeader.of(GroupSync.State.NO_KEY, 0, group).buttons);
		assertEquals("Sync off", SyncHeader.of(GroupSync.State.OFF, 0, group).title);
		assertTrue(SyncHeader.of(GroupSync.State.OFF, 0, group).buttons.isEmpty());
		assertTrue(SyncHeader.of(GroupSync.State.READY, 0, group).buttons.contains(SyncHeader.Button.JOIN));
		assertEquals("In another party; GIM sync paused", SyncHeader.of(GroupSync.State.OTHER_PARTY, 0, group).title);
		assertEquals(Collections.singletonList(SyncHeader.Button.SWITCH),
			SyncHeader.of(GroupSync.State.OTHER_PARTY, 0, group).buttons);
		SyncHeader connected = SyncHeader.of(GroupSync.State.CONNECTED, 2, group);
		// The count includes you: two others plus you.
		assertEquals("Connected, 3 online", connected.title);
		assertEquals("Connected, 1 online (you)", SyncHeader.of(GroupSync.State.CONNECTED, 0, group).title);
		assertTrue(connected.ok);
		assertEquals("Connected, 2 online", SyncHeader.of(GroupSync.State.CONNECTED, 1, group).title);
		for (GroupSync.State s : GroupSync.State.values())
		{
			assertTrue(s.name(), SyncHeader.of(s, 0, GroupMembers.UNKNOWN).title.length() > 0);
		}
	}

	@Test
	public void theKeyIsCopiedAndPastedOutsideTheGame()
	{
		// Board 2026-11-04: the game chatbox takes no paste, and filling it in was dropped;
		// the key goes out by copy (e.g. to Discord) and comes in by Paste.
		GroupMembers group = GroupMembers.of("GroupShip", Arrays.asList("Alex", "Me"));
		for (GroupSync.State s : new GroupSync.State[]{GroupSync.State.READY, GroupSync.State.STALE,
			GroupSync.State.CONNECTED, GroupSync.State.NO_GROUP})
		{
			assertTrue(s.name(), SyncHeader.of(s, 0, group).buttons.contains(SyncHeader.Button.COPY_KEY));
		}
		assertTrue(SyncHeader.of(GroupSync.State.NO_KEY, 0, group).buttons.contains(SyncHeader.Button.PASTE_KEY));
		// Joining is automatic now; the header says so rather than asking for a click.
		assertEquals("Connecting", SyncHeader.of(GroupSync.State.READY, 0, group).title);
	}

	@Test
	public void aPastedKeyIsReadBareOrAsAShareLine()
	{
		String key = "0123456789abcdef0123456789abcdef";
		assertEquals(key, GroupKey.fromPasted(key));
		assertEquals(key, GroupKey.fromPasted("  " + key.toUpperCase() + "\n"));
		assertEquals(key, GroupKey.fromPasted("[GIMU] " + key));
		assertEquals("", GroupKey.fromPasted("hello"));
		assertEquals("", GroupKey.fromPasted(key + "0"));
		assertEquals("", GroupKey.fromPasted(null));
	}

	@Test
	public void wornItemsGoToTheirEquipmentTabSlots()
	{
		// 3 x 5 like the worn-equipment tab: head top middle, weapon/body/shield across the middle.
		assertEquals(1, GroupIronmanPanel.wornCell(0));   // head
		assertEquals(6, GroupIronmanPanel.wornCell(3));   // weapon
		assertEquals(7, GroupIronmanPanel.wornCell(4));   // body
		assertEquals(8, GroupIronmanPanel.wornCell(5));   // shield
		assertEquals(14, GroupIronmanPanel.wornCell(12)); // ring
		assertEquals(5, GroupIronmanPanel.wornCell(13));  // ammo
		assertEquals(-1, GroupIronmanPanel.wornCell(-1)); // not equipment
		assertEquals(-1, GroupIronmanPanel.wornCell(6));  // unused slot id
	}

	@Test
	public void columnsLeftAlignEverything()
	{
		// Mixed alignments in a BoxLayout pushed text past the panel's right edge.
		GroupIronmanPanel.Column column = new GroupIronmanPanel.Column();
		javax.swing.JPanel box = new javax.swing.JPanel();
		javax.swing.JLabel label = new javax.swing.JLabel("x");
		column.add(box);
		column.add(label);
		assertEquals(java.awt.Component.LEFT_ALIGNMENT, box.getAlignmentX(), 0f);
		assertEquals(java.awt.Component.LEFT_ALIGNMENT, label.getAlignmentX(), 0f);
	}

	@Test
	public void panelTextFromPeersIsEscaped()
	{
		assertEquals("&lt;b&gt;Alex &amp; Sam&lt;/b&gt;", GroupIronmanPanel.escape("<b>Alex & Sam</b>"));
	}
}
