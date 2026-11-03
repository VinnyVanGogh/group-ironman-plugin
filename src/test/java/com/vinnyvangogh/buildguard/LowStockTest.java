package com.vinnyvangogh.buildguard;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static com.vinnyvangogh.buildguard.FakeItems.*;

public class LowStockTest
{
	private static final FakeItems ITEMS = new FakeItems();

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
	public void belowTheMinimumAlerts()
	{
		LowStock low = LowStock.parse("Shark: 100");
		List<LowStock.Alert> alerts = low.check(items(SHARK, 40), ITEMS);
		assertEquals(1, alerts.size());
		assertEquals("Group storage is low on Shark: 40 left, minimum 100", alerts.get(0).message());
	}

	@Test
	public void exactlyTheMinimumIsNotLow()
	{
		assertTrue(LowStock.parse("Shark: 100").check(items(SHARK, 100), ITEMS).isEmpty());
	}

	@Test
	public void missingFromStorageCountsAsZero()
	{
		List<LowStock.Alert> alerts = LowStock.parse("Coal: 1").check(items(SHARK, 5), ITEMS);
		assertEquals(0, alerts.get(0).have);
	}

	@Test
	public void alertsOnceUntilRestocked()
	{
		LowStock low = LowStock.parse("shark: 100");
		assertEquals(1, low.check(items(SHARK, 40), ITEMS).size());
		assertTrue("still low, already told", low.check(items(SHARK, 30), ITEMS).isEmpty());
		assertTrue(low.check(items(SHARK, 150), ITEMS).isEmpty());
		assertEquals("low again after a restock alerts again", 1, low.check(items(SHARK, 99), ITEMS).size());
	}

	@Test
	public void matchesByNameCaseInsensitively()
	{
		LowStock low = LowStock.parse("PRAYER POTION(4): 20");
		assertTrue(low.check(items(PRAYER_POT_4, 20), ITEMS).isEmpty());
	}

	@Test
	public void badLinesAreIgnored()
	{
		LowStock low = LowStock.parse("Shark 100\nCoal: lots\n: 5\nRune scimitar: 0\nRune scimitar: -2\n\n");
		assertTrue(low.isEmpty());
		assertTrue(LowStock.parse(null).isEmpty());
	}

	@Test
	public void commaSeparatedRulesAndNamesWithColonsUseTheLastColon()
	{
		LowStock low = LowStock.parse("Shark: 10, Coal: 50");
		assertEquals(2, low.check(items(), ITEMS).size());
	}
}
