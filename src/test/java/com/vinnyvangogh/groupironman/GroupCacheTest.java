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
	public void aNewerCacheWithExtraFieldsIsStillRead()
	{
		GroupRoster roster = new GroupRoster();
		assertTrue(GroupCache.read(GSON,
			"{\"v\":3,\"banks\":{\"Alex\":{\"ts\":5,\"i\":[385,2],\"tabs\":[1]}},\"pets\":[]}", roster));
		assertEquals(items(SHARK, 2), roster.banks().get("Alex").items());
	}
}
