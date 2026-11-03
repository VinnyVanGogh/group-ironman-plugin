package com.vinnyvangogh.buildguard;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntUnaryOperator;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Group storage open versus close. Slots are given as id/quantity pairs in
 * slot order, the way the game's container hands them over.
 */
public class StorageDiffTest
{
	private static final int SHARK = 385;
	private static final int SHARK_NOTED = 386;
	private static final int COAL = 453;
	private static final int COAL_NOTED = 454;
	private static final int RUNE_SCIM = 1333;

	/** Stands in for ItemManager.canonicalize: noted ids map to the unnoted one. */
	private static final IntUnaryOperator UNNOTE = id -> id == SHARK_NOTED ? SHARK : id == COAL_NOTED ? COAL : id;

	private static Map<Integer, Integer> slots(int... pairs)
	{
		Map<Integer, Integer> out = new TreeMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			ItemCounts.add(out, pairs[i], pairs[i + 1], UNNOTE);
		}
		return out;
	}

	private static Map<Integer, Integer> counts(int... pairs)
	{
		Map<Integer, Integer> out = new TreeMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			out.put(pairs[i], pairs[i + 1]);
		}
		return out;
	}

	@Test
	public void movingAStackToAnotherSlotIsNoChange()
	{
		StorageChange c = StorageDiff.diff(1, slots(SHARK, 10, -1, 0, COAL, 5), slots(COAL, 5, SHARK, 10, -1, 0));
		assertTrue(c.isEmpty());
	}

	@Test
	public void takingPartOfAStackIsTookThatMany()
	{
		StorageChange c = StorageDiff.diff(1, slots(SHARK, 10), slots(SHARK, 7));
		assertEquals(counts(SHARK, 3), c.took());
		assertTrue(c.deposited().isEmpty());
	}

	@Test
	public void addingToAStackIsDepositedThatMany()
	{
		StorageChange c = StorageDiff.diff(1, slots(COAL, 100), slots(COAL, 250));
		assertEquals(counts(COAL, 150), c.deposited());
	}

	@Test
	public void takingAWholeStackAndAddingANewOne()
	{
		StorageChange c = StorageDiff.diff(1, slots(RUNE_SCIM, 1, SHARK, 4), slots(SHARK, 4, COAL, 20));
		assertEquals(counts(RUNE_SCIM, 1), c.took());
		assertEquals(counts(COAL, 20), c.deposited());
		assertEquals(counts(SHARK, 4, COAL, 20), c.after());
	}

	@Test
	public void notedAndUnnotedAreTheSameItem()
	{
		// Before: 5 sharks unnoted. After the deposit of 20 noted, the game shows 25 sharks.
		StorageChange c = StorageDiff.diff(1, slots(SHARK, 5), slots(SHARK, 5, SHARK_NOTED, 20));
		assertEquals(counts(SHARK, 20), c.deposited());
		assertTrue(c.took().isEmpty());
	}

	@Test
	public void aNotedStackSwappedForTheSameUnnotedCountIsNoChange()
	{
		StorageChange c = StorageDiff.diff(1, slots(COAL_NOTED, 30), slots(COAL, 30));
		assertTrue(c.isEmpty());
	}

	@Test
	public void splitStacksAreSummed()
	{
		// Two partial stacks of the same item (unstackables) count together.
		StorageChange c = StorageDiff.diff(1, slots(RUNE_SCIM, 1, RUNE_SCIM, 1, RUNE_SCIM, 1), slots(RUNE_SCIM, 1));
		assertEquals(counts(RUNE_SCIM, 2), c.took());
	}

	@Test
	public void emptySlotsAndPlaceholdersAreIgnored()
	{
		assertEquals(counts(SHARK, 2), slots(-1, 1, 0, 5, SHARK, 2, COAL, 0));
	}
}
