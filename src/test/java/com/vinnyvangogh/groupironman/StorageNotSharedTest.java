package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static com.vinnyvangogh.groupironman.FakeItems.*;

/**
 * An own group storage visit made with "Share group storage changes" off is kept on this client, marked not
 * shared, and nothing is sent (Board 2026-11-13: a friend's withdrawal before ticking the switch looked lost).
 */
public class StorageNotSharedTest
{
	private static final Gson GSON = new Gson();
	private static final ZoneId UTC = ZoneOffset.UTC;
	private static final long NOW = 1_762_776_000_000L;
	private static final GroupOutbox.Switches STORAGE_OFF = new GroupOutbox.Switches(true, false, true, true, true,
		true);
	private static final GroupOutbox.Switches STORAGE_ON = new GroupOutbox.Switches(false, true, false, false);

	private final FakeItems items = new FakeItems();

	private static Map<Integer, Integer> items(int... pairs)
	{
		Map<Integer, Integer> out = new TreeMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			out.put(pairs[i], pairs[i + 1]);
		}
		return out;
	}

	private static StorageChange tookPots(long at)
	{
		return new StorageChange(at, "Friend", items(PRAYER_POT_4, 4), items(), items(PRAYER_POT_4, 6));
	}

	@Test
	public void withTheSwitchOffTheVisitIsLoggedLocallyAndNothingIsSent()
	{
		GroupRoster roster = new GroupRoster();
		String payload = GroupOutbox.ownStorage(GSON, STORAGE_OFF, tookPots(NOW), roster);

		assertNull("nothing goes to the outbox", payload);
		assertEquals(1, roster.log().entries().size());
		StorageChange kept = roster.log().entries().get(0);
		assertFalse(kept.shared());
		assertEquals(items(PRAYER_POT_4, 4), kept.took());
		assertEquals("the own Storage tab shows what this client saw", NOW, roster.storageAt());
		assertTrue(roster.log().notShared(NOW, "friend"));
	}

	@Test
	public void withTheSwitchOnBehaviourIsUnchanged()
	{
		GroupRoster roster = new GroupRoster();
		StorageChange change = tookPots(NOW);
		String payload = GroupOutbox.ownStorage(GSON, STORAGE_ON, change, roster);

		assertEquals(GroupOutbox.storage(GSON, STORAGE_ON, change), payload);
		assertNotNull(payload);
		assertTrue(roster.log().entries().get(0).shared());
		assertFalse(roster.log().notShared(NOW, "Friend"));
		assertEquals("Friend took 4 Prayer potion(4)", PanelModel.change(roster.log().entries().get(0), items, ""));
	}

	@Test
	public void theStorageTabLineSaysWhyItWasNotShared()
	{
		GroupRoster roster = new GroupRoster();
		GroupOutbox.ownStorage(GSON, STORAGE_OFF, tookPots(NOW), roster);
		assertEquals("You took 4 Prayer potion(4) (not shared: Share group storage changes is off)",
			PanelModel.change(roster.log().entries().get(0), items, "Friend"));
	}

	@Test
	public void theLogTabRowSaysWhyItWasNotShared()
	{
		GroupRoster roster = new GroupRoster();
		GroupOutbox.ownStorage(GSON, STORAGE_OFF, tookPots(NOW), roster);
		GroupEvent summary = GroupEvent.of(GroupEvent.Type.STORAGE_SUMMARY, NOW, "Friend")
			.taken(items(PRAYER_POT_4, 4)).build();

		ActivityLog.Row row = ActivityLog.row(summary, items, NOW, UTC, roster.log());
		assertTrue(row.text, row.text.endsWith("(" + StorageChange.NOT_SHARED + ")"));
		assertTrue(row.full, row.full.contains(StorageChange.NOT_SHARED));
		assertTrue(row.copy, row.copy.contains(StorageChange.NOT_SHARED));
	}

	@Test
	public void otherRowsAreNotMarked()
	{
		GroupRoster roster = new GroupRoster();
		GroupOutbox.ownStorage(GSON, STORAGE_OFF, tookPots(NOW), roster);
		GroupEvent otherVisit = GroupEvent.of(GroupEvent.Type.STORAGE_SUMMARY, NOW - 1, "Friend")
			.taken(items(SHARK, 1)).build();
		GroupEvent drop = GroupEvent.drop(NOW, "Friend", "Goblin", items(COINS, 5));

		assertFalse(ActivityLog.row(otherVisit, items, NOW, UTC, roster.log()).text.contains(StorageChange.NOT_SHARED));
		assertFalse("only storage rows", ActivityLog.row(drop, items, NOW, UTC, roster.log()).text
			.contains(StorageChange.NOT_SHARED));
		assertEquals(ActivityLog.row(drop, items, NOW, UTC).text, ActivityLog.row(drop, items, NOW, UTC,
			roster.log()).text);
	}

	@Test
	public void theMarkSurvivesARestart()
	{
		GroupRoster before = new GroupRoster();
		GroupOutbox.ownStorage(GSON, STORAGE_OFF, tookPots(NOW), before);
		before.putStorage(new StorageChange(NOW + 5, "Sam", items(SHARK, 1), items(), items()));

		GroupRoster after = new GroupRoster();
		assertTrue(GroupCache.read(GSON, GroupCache.write(GSON, before), after));
		assertEquals(2, after.log().entries().size());
		assertTrue(after.log().notShared(NOW, "Friend"));
		assertFalse(after.log().notShared(NOW + 5, "Sam"));
	}

	@Test
	public void anOlderFileWithoutTheMarkReadsAsShared()
	{
		GroupRoster before = new GroupRoster();
		before.putStorage(tookPots(NOW));
		String json = GroupCache.write(GSON, before);
		assertFalse("only written when set", json.contains("\"ns\""));

		GroupRoster after = new GroupRoster();
		assertTrue(GroupCache.read(GSON, json, after));
		assertTrue(after.log().entries().get(0).shared());
	}

	@Test
	public void aMemberRenameKeepsTheMark()
	{
		assertFalse(tookPots(NOW).notShared().withMember("Other").shared());
	}
}
