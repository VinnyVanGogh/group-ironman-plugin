package com.vinnyvangogh.groupironman;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static com.vinnyvangogh.groupironman.FakeItems.*;

public class StorageLogTest
{
	private static Map<Integer, Integer> items(int... pairs)
	{
		Map<Integer, Integer> out = new TreeMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			out.put(pairs[i], pairs[i + 1]);
		}
		return out;
	}

	private static StorageChange took(long at, String who)
	{
		return new StorageChange(at, who, items(SHARK, 1), items(), items());
	}

	@Test
	public void keepsOnlyTheNewest200()
	{
		StorageLog log = new StorageLog();
		for (int i = 1; i <= 250; i++)
		{
			log.add(took(i, "Alex"));
		}
		List<StorageChange> entries = log.entries();
		assertEquals(StorageLog.MAX_ENTRIES, entries.size());
		assertEquals(250, entries.get(0).at());
		assertEquals(51, entries.get(entries.size() - 1).at());
	}

	@Test
	public void newestFirstEvenWhenDeliveredOutOfOrder()
	{
		StorageLog log = new StorageLog();
		log.add(took(10, "Alex"));
		log.add(took(30, "Sam"));
		log.add(took(20, "Jo"));
		assertEquals(30, log.entries().get(0).at());
		assertEquals(20, log.entries().get(1).at());
		assertEquals(10, log.entries().get(2).at());
	}

	@Test
	public void aDuplicateDeliveryIsDropped()
	{
		StorageLog log = new StorageLog();
		log.add(took(10, "Alex"));
		assertFalse(log.add(took(10, "alex")));
		assertEquals(1, log.entries().size());
	}

	@Test
	public void aVisitThatChangedNothingIsNotLogged()
	{
		StorageLog log = new StorageLog();
		assertFalse(log.add(new StorageChange(1, "Alex", items(), items(), items(SHARK, 4))));
	}

	@Test
	public void linesSayWhoTookAndDeposited()
	{
		StorageLog log = new StorageLog();
		long now = TimeUnit.DAYS.toMillis(5);
		log.add(new StorageChange(now - TimeUnit.MINUTES.toMillis(3), "Alex", items(SHARK, 5),
			items(COAL, 100, RUNE_SCIM, 1), items()));
		assertEquals(Collections.singletonList(
			"Alex took 5 x Shark, deposited 100 x Coal, 1 x Rune scimitar (3m ago)"),
			log.lines(new FakeItems(), now));
	}
}
