package com.vinnyvangogh.groupironman;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.Map;
import org.junit.Test;

/**
 * A storage post's Value is what the visit left in storage: deposited minus taken. The Board's post (took 10 trout,
 * deposited 100 yew logs) read 9,480 gp, the two sides added, where 9,120 was right.
 */
public class StorageValueTest
{
	private final ItemLookup words = Amounts.BOTH.words(new FakeItems());

	private static DiscordMerge.Post storage(Map<Integer, Integer> deposited, Map<Integer, Integer> taken)
	{
		return new DiscordMerge.Post(null, "Group storage visit", "", Collections.emptyMap(),
			Collections.emptyList(), false, deposited, taken);
	}

	@Test
	public void bothSidesShowTheNetAndEachSide()
	{
		// 12 sharks in (12 x 800 = 9,600), 6 trout out (6 x 30 = 180): 9,420 left, never 9,780.
		String v = DiscordMerge.valueText(storage(Collections.singletonMap(FakeItems.SHARK, 12),
			Collections.singletonMap(FakeItems.TROUT, 6)), words);
		assertEquals(words.amount(9_420) + " gp (deposited " + words.amount(9_600) + ", took " + words.amount(180)
			+ ")", v);
	}

	@Test
	public void takingMoreThanDepositingIsNegative()
	{
		String v = DiscordMerge.valueText(storage(Collections.singletonMap(FakeItems.TROUT, 1),
			Collections.singletonMap(FakeItems.SHARK, 2)), words);
		assertEquals("-" + words.amount(1_570) + " gp (deposited " + words.amount(30) + ", took "
			+ words.amount(1_600) + ")", v);
	}

	@Test
	public void oneSideOnly()
	{
		assertEquals(words.amount(180) + " gp taken", DiscordMerge.valueText(storage(Collections.emptyMap(),
			Collections.singletonMap(FakeItems.TROUT, 6)), words));
		assertEquals(words.amount(9_600) + " gp", DiscordMerge.valueText(storage(
			Collections.singletonMap(FakeItems.SHARK, 12), Collections.emptyMap()), words));
	}

	@Test
	public void otherPostsStillSumTheirItems()
	{
		DiscordMerge.Post drop = new DiscordMerge.Post(null, "Drop", "", Collections.singletonMap(FakeItems.SHARK, 2),
			Collections.emptyList(), false);
		assertEquals(words.amount(1_600) + " gp", DiscordMerge.valueText(drop, words));
	}
}
