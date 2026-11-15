package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static com.vinnyvangogh.groupironman.FakeItems.*;

/** Members' last banks, the storage log and storage survive a restart. */
public class GroupCacheTest
{
	private static final Gson GSON = new Gson();

	private static Map<Integer, Integer> items(int... pairs)
	{
		Map<Integer, Integer> out = new TreeMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			out.put(pairs[i], pairs[i + 1]);
		}
		return out;
	}

	@Test
	public void banksLogAndStorageRoundTrip()
	{
		GroupRoster before = new GroupRoster();
		before.putBank("Alex", new BankSnapshot(100, items(SHARK, 120, COAL, 5)));
		before.putBank("Sam", new BankSnapshot(200, items(WHIP, 1)));
		before.putStorage(new StorageChange(300, "Sam", items(SHARK, 1), items(COAL, 9), items(SHARK, 40, COAL, 9)));
		before.putCarried("Sam", new GroupCodec.Carried(items(SHARK, 1), items()));

		GroupRoster after = new GroupRoster();
		assertTrue(GroupCache.read(GSON, GroupCache.write(GSON, before), after));

		assertEquals(before.banks(), after.banks());
		assertEquals(1, after.log().entries().size());
		assertEquals("Sam", after.log().entries().get(0).member());
		assertEquals(items(SHARK, 1), after.log().entries().get(0).took());
		assertEquals(items(SHARK, 40, COAL, 9), after.storageItems());
		assertEquals(300, after.storageAt());
		// Round 2 keeps carried items for offline cards: two banks, Sam's inventory and
		// equipment, and storage come back as search sources.
		assertEquals(5, after.searchSources().size());
		assertEquals(items(SHARK, 1), after.allCarried().get("Sam").inventory);
	}

	@Test
	public void anUnreadableFileLeavesTheRosterEmpty()
	{
		GroupRoster roster = new GroupRoster();
		assertFalse(GroupCache.read(GSON, "{{{", roster));
		assertFalse(GroupCache.read(GSON, "{\"banks\":{}}", roster));
		assertFalse(GroupCache.read(GSON, null, roster));
		assertFalse(roster.hasAnything());
	}

	@Test
	public void atMostFiftyMembersAreLookedAtPerSectionAndABadEntryCountsAsOne()
	{
		StringBuilder json = new StringBuilder("{\"v\":1,\"banks\":{\"Bad\":7");
		for (int i = 0; i < 60; i++)
		{
			json.append(",\"M").append(i).append("\":{\"ts\":1,\"i\":[385,1]}");
		}
		json.append("},\"heard\":{\"Alex\":5,\"Sam\":-1,\"Kim\":\"x\"},\"played\":9}");
		GroupRoster roster = new GroupRoster();
		assertTrue(GroupCache.read(GSON, json.toString(), roster));
		// "Bad" is not a bank, but it is still one of the fifty looked at.
		assertEquals(GroupCache.MAX_MEMBERS - 1, roster.banks().size());
		assertTrue(roster.banks().containsKey("M48"));
		assertFalse(roster.banks().containsKey("M49"));
		assertEquals(Long.valueOf(5), roster.lastHeard().get("Alex"));
		assertEquals(1, roster.lastHeard().size());
		assertEquals(9, roster.lastPlayed());
	}

	@Test
	public void extrasSeenHeardStatsAndHiscoresRoundTrip()
	{
		GroupRoster before = new GroupRoster();
		before.putExtras("Sam", new MemberExtras(items(SHARK, 2), items(), items(COAL, 3), 77));
		before.heard("Sam", 500);
		before.seen("Alex", new LastSeen(600, 302, true));
		before.markPlayed(700);
		GroupRoster after = new GroupRoster();
		String json = GroupCache.write(GSON, before, "hooks");
		assertTrue(GroupCache.read(GSON, json, after));
		assertEquals("hooks", GroupCache.webhooks(GSON, json));
		assertEquals(null, GroupCache.shop(GSON, json));
		assertEquals(items(COAL, 3), after.extras().get("Sam").seedVault);
		assertEquals(77, after.extras().get("Sam").seedVaultAt);
		assertEquals(Long.valueOf(500), after.lastHeard().get("Sam"));
		assertEquals(302, after.lastSeen().get("Alex").world);
		assertTrue(after.lastSeen().get("Alex").plugin);
		assertEquals(700, after.lastPlayed());
		// A second write of what was read is the same document.
		assertEquals(json, GroupCache.write(GSON, after, "hooks"));
	}

	@Test
	public void aNewerCacheWithExtraFieldsIsStillRead()
	{
		GroupRoster roster = new GroupRoster();
		assertTrue(GroupCache.read(GSON,
			"{\"v\":3,\"banks\":{\"Alex\":{\"ts\":5,\"i\":[385,2],\"tabs\":[1]}},\"pets\":[]}", roster));
		assertEquals(items(SHARK, 2), roster.banks().get("Alex").items());
	}
}
