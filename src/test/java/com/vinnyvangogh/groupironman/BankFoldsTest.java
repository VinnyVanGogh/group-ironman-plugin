package com.vinnyvangogh.groupironman;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Which bank cards in the Banks tab are open (Board 2026-11-13). */
public class BankFoldsTest
{
	private static final List<String> BANKS = Arrays.asList("Alex", "Sam", "Zed");

	@Test
	public void everyBankStartsOpen()
	{
		BankFolds f = new BankFolds();
		f.filter("");
		for (String b : BANKS)
		{
			assertTrue(b, f.open(b, true));
		}
		assertTrue(f.anyOpen(BANKS, name -> true));
	}

	@Test
	public void togglingOneBankLeavesTheOthers()
	{
		BankFolds f = new BankFolds();
		f.filter("");
		f.toggle("Sam", true);
		assertTrue(f.open("Alex", true));
		assertFalse(f.open("Sam", true));
		assertTrue(f.open("Zed", true));

		f.toggle("Sam", true);
		assertTrue(f.open("Sam", true));
	}

	@Test
	public void namesMatchWhateverTheCase()
	{
		BankFolds f = new BankFolds();
		f.filter("");
		f.toggle("Sam", true);
		assertFalse(f.open("sam", true));
	}

	@Test
	public void filterOpensMatchesFoldsTheRestAndClearingRestoresThePlayersChoice()
	{
		BankFolds f = new BankFolds();
		f.filter("");
		f.toggle("Alex", true); // the player folds Alex; Sam and Zed stay open

		f.filter("shark");
		assertTrue("a folded bank with a match opens", f.open("Alex", true));
		assertFalse("an open bank with no match folds", f.open("Sam", false));
		assertTrue(f.open("Zed", true));

		f.filter("");
		assertFalse("the player's fold is back", f.open("Alex", true));
		assertTrue(f.open("Sam", true));
		assertTrue(f.open("Zed", true));
	}

	@Test
	public void foldingWhileFilteringLastsOnlyForThatFilter()
	{
		BankFolds f = new BankFolds();
		f.filter("shark");
		f.toggle("Alex", true);
		assertFalse(f.open("Alex", true));

		f.filter("whip");
		assertTrue("a new filter starts from the matches again", f.open("Alex", true));

		f.filter("");
		assertTrue("the player's own choice was never touched", f.open("Alex", true));
	}

	@Test
	public void filterTooShortToSearchKeepsThePlayersChoice()
	{
		BankFolds f = new BankFolds();
		f.filter("");
		f.toggle("Alex", true);
		f.filter("s");
		assertFalse(f.open("Alex", true));
	}

	@Test
	public void collapseAllThenExpandAll()
	{
		BankFolds f = new BankFolds();
		f.filter("");
		f.setAll(BANKS, false);
		for (String b : BANKS)
		{
			assertFalse(b, f.open(b, true));
		}
		assertFalse(f.anyOpen(BANKS, name -> true));

		f.setAll(BANKS, true);
		for (String b : BANKS)
		{
			assertTrue(b, f.open(b, true));
		}
	}

	@Test
	public void collapseAllWhileFilteringIsUndoneByClearingTheFilter()
	{
		BankFolds f = new BankFolds();
		f.filter("shark");
		f.setAll(BANKS, false);
		assertFalse(f.open("Alex", true));

		f.filter("");
		assertTrue(f.open("Alex", true));
	}
}
