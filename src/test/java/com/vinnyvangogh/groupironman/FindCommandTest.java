package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static com.vinnyvangogh.groupironman.FakeItems.*;

public class FindCommandTest
{
	private static final long NOW = TimeUnit.DAYS.toMillis(100);
	private static final FakeItems ITEMS = new FakeItems();
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

	private static long daysAgo(int days)
	{
		return NOW - TimeUnit.DAYS.toMillis(days);
	}

	private static StorageChange took(long at, String member, Map<Integer, Integer> took)
	{
		return new StorageChange(at, member, took, items(), items());
	}

	@Test
	public void answersCurrentHoldersThenWhoLastTookItFromStorage()
	{
		GroupRoster roster = new GroupRoster();
		roster.putBank("Sam", new BankSnapshot(NOW - TimeUnit.HOURS.toMillis(2), items(WHIP, 1)));
		roster.log().add(took(daysAgo(3), "Alex", items(WHIP, 1)));

		assertEquals(Arrays.asList(
			"Abyssal whip: Sam bank 1 (2h ago)",
			"Alex took 1 x Abyssal whip from storage (3d ago)"),
			FindCommand.answer("abyssal whip", roster.searchSources(), roster.log().entries(), ITEMS, NOW));
	}

	@Test
	public void matchesCaseInsensitivelyOnAnyPartOfTheName()
	{
		GroupRoster roster = new GroupRoster();
		roster.log().add(took(daysAgo(1), "Alex", items(WHIP, 1, SHARK, 5)));

		assertEquals(Collections.singletonList("Alex took 1 x Abyssal whip from storage (1d ago)"),
			FindCommand.answer("  WHIP ", roster.searchSources(), roster.log().entries(), ITEMS, NOW));
	}

	@Test
	public void historyIsNewestFirstMatchingOnlyAndLimited()
	{
		StorageLog log = new StorageLog();
		log.add(took(daysAgo(5), "Alex", items(SHARK, 1)));
		log.add(new StorageChange(daysAgo(4), "Sam", items(), items(SHARK, 20), items()));
		log.add(took(daysAgo(3), "Kim", items(COAL, 9)));
		log.add(took(daysAgo(2), "Alex", items(SHARK, 2, COAL, 1)));
		log.add(took(daysAgo(1), "Sam", items(SHARK, 3)));

		assertEquals(Arrays.asList(
			"Sam took 3 x Shark from storage (1d ago)",
			"Alex took 2 x Shark from storage (2d ago)",
			"Sam deposited 20 x Shark into storage (4d ago)"),
			GroupSearch.history("shark", log.entries(), ITEMS, NOW, 3));
		assertEquals(Collections.singletonList("Sam took 3 x Shark from storage (1d ago)"),
			GroupSearch.history("shark", log.entries(), ITEMS, NOW, 1));
	}

	@Test
	public void exactlyFiveLinesAreNotCut()
	{
		GroupRoster roster = new GroupRoster();
		roster.putBank("Alex", new BankSnapshot(daysAgo(1), items(PRAYER_POT_4, 10, PRAYER_POT_3, 2)));
		for (int i = 1; i <= 6; i++)
		{
			roster.log().add(took(daysAgo(i + 1), "Sam", items(PRAYER_POT_4, i)));
		}

		// 2 holder lines + HISTORY_LIMIT (3) history lines.
		List<String> out = FindCommand.answer("prayer", roster.searchSources(), roster.log().entries(), ITEMS, NOW);
		assertEquals(Arrays.asList(
			"Prayer potion(3): Alex bank 2 (1d ago)",
			"Prayer potion(4): Alex bank 10 (1d ago)",
			"Sam took 1 x Prayer potion(4) from storage (2d ago)",
			"Sam took 2 x Prayer potion(4) from storage (3d ago)",
			"Sam took 3 x Prayer potion(4) from storage (4d ago)"), out);
	}

	@Test
	public void moreThanFiveLinesAreCutAndPointAtThePanel()
	{
		// Unnamed ids read "item 90001".. so "item" matches all eight.
		Map<Integer, Integer> many = new TreeMap<>();
		for (int id = 1; id <= 8; id++)
		{
			many.put(90_000 + id, 1);
		}
		StorageLog log = new StorageLog();
		log.add(took(daysAgo(1), "Sam", items(90_001, 1)));

		List<String> out = FindCommand.answer("item",
			Collections.singletonList(new GroupSearch.Source("Alex", "bank", many, -1)), log.entries(), ITEMS, NOW);
		assertEquals(FindCommand.MAX_LINES + 1, out.size());
		assertEquals("item 90001: Alex bank 1", out.get(0));
		assertEquals("4 more, see the GIM Utilities panel", out.get(FindCommand.MAX_LINES));
	}

	@Test
	public void noMatchSaysSoAndNamesTheBlindSpots()
	{
		GroupRoster roster = new GroupRoster();
		roster.putBank("Alex", new BankSnapshot(daysAgo(1), items(SHARK, 1)));
		roster.log().add(took(daysAgo(1), "Alex", items(COAL, 1)));

		List<String> out = FindCommand.answer("dragon hunter wand", roster.searchSources(), roster.log().entries(),
			ITEMS, NOW);
		assertEquals(1, out.size());
		assertTrue(out.get(0).startsWith("Nothing found for 'dragon hunter wand'."));
		assertTrue(out.get(0).contains("Only members running GIM Utilities with sharing on are tracked"));
		assertTrue(out.get(0).contains("trades and drops between members are not"));
	}

	@Test
	public void theQueryIsEchoedWithoutChatTags()
	{
		List<String> out = FindCommand.answer("<col=ff0000>zz", Collections.<GroupSearch.Source>emptyList(),
			Collections.<StorageChange>emptyList(), ITEMS, NOW);
		assertTrue(out.get(0), out.get(0).startsWith("Nothing found for 'col=ff0000zz'."));
	}

	@Test
	public void aTooShortQueryAsksForMoreLetters()
	{
		GroupRoster roster = new GroupRoster();
		roster.putBank("Alex", new BankSnapshot(daysAgo(1), items(SHARK, 1)));
		roster.log().add(took(daysAgo(1), "Alex", items(SHARK, 1)));
		for (String q : Arrays.asList("s", " ", "", null))
		{
			assertEquals(Collections.singletonList(
				"Type at least 2 letters of an item name, e.g. ::find dragon hunter wand"),
				FindCommand.answer(q, roster.searchSources(), roster.log().entries(), ITEMS, NOW));
		}
		assertTrue(GroupSearch.history("s", roster.log().entries(), ITEMS, NOW, 3).isEmpty());
	}

	@Test
	public void theStorageLogSurvivesARestartStillCappedAt200()
	{
		GroupRoster before = new GroupRoster();
		for (int i = 0; i < StorageLog.MAX_ENTRIES + 25; i++)
		{
			before.log().add(took(daysAgo(30) +TimeUnit.MINUTES.toMillis(i), "Alex", items(SHARK, 1)));
		}
		before.log().add(took(daysAgo(3), "Alex", items(WHIP, 1)));
		assertEquals(StorageLog.MAX_ENTRIES, before.log().entries().size());

		GroupRoster after = new GroupRoster();
		assertTrue(GroupCache.read(GSON, GroupCache.write(GSON, before), after));
		assertEquals(StorageLog.MAX_ENTRIES, after.log().entries().size());
		assertEquals(Collections.singletonList("Alex took 1 x Abyssal whip from storage (3d ago)"),
			FindCommand.answer("whip", after.searchSources(), after.log().entries(), ITEMS, NOW));
	}
}
