package com.vinnyvangogh.groupironman;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class ItemCountsPlaceholderTest
{
	private static final int GLORY = 1704;
	private static final int GLORY_PLACEHOLDER = 14_500;
	private static final int LAVA_STAFF = 3053;
	private static final int LAVA_STAFF_PLACEHOLDER = 14_501;

	/** Placeholder ids map back to the real item, exactly as ItemManager.canonicalize does. */
	private static int canonical(int id)
	{
		if (id == GLORY_PLACEHOLDER)
		{
			return GLORY;
		}
		if (id == LAVA_STAFF_PLACEHOLDER)
		{
			return LAVA_STAFF;
		}
		return id;
	}

	private static boolean isPlaceholder(int id)
	{
		return id == GLORY_PLACEHOLDER || id == LAVA_STAFF_PLACEHOLDER;
	}

	private static void slot(Map<Integer, Integer> into, int id, int qty)
	{
		ItemCounts.addOwned(into, id, qty, ItemCountsPlaceholderTest::canonical, ItemCountsPlaceholderTest::isPlaceholder);
	}

	@Test
	public void placeholderOnlyBankIsEmpty()
	{
		Map<Integer, Integer> out = new HashMap<>();
		slot(out, GLORY_PLACEHOLDER, 0);
		assertTrue(out.isEmpty());
	}

	@Test
	public void placeholderWithNonZeroQuantityIsStillSkipped()
	{
		Map<Integer, Integer> out = new HashMap<>();
		slot(out, LAVA_STAFF_PLACEHOLDER, 1);
		assertTrue(out.isEmpty());
	}

	@Test
	public void placeholderBesideRealItemCountsOnlyReal()
	{
		Map<Integer, Integer> out = new HashMap<>();
		slot(out, GLORY_PLACEHOLDER, 0);
		slot(out, LAVA_STAFF, 1);
		assertEquals(1, out.size());
		assertEquals(Integer.valueOf(1), out.get(LAVA_STAFF));
	}

	@Test
	public void realGloryPlusItsPlaceholderCountsOnce()
	{
		Map<Integer, Integer> out = new HashMap<>();
		slot(out, GLORY, 1);
		slot(out, GLORY_PLACEHOLDER, 0);
		assertEquals(Integer.valueOf(1), out.get(GLORY));
	}

	@Test
	public void quantityChangesAreUnaffected()
	{
		Map<Integer, Integer> out = new HashMap<>();
		slot(out, LAVA_STAFF, 3);
		slot(out, LAVA_STAFF, 2);
		assertEquals(Integer.valueOf(5), out.get(LAVA_STAFF));
		Map<Integer, Integer> none = new HashMap<>();
		slot(none, LAVA_STAFF, 0);
		assertTrue(none.isEmpty());
	}
}
