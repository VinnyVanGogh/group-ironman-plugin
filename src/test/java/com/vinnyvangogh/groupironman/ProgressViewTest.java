package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** What the Group, Compare and Personal bests views read, and the text built from it. */
public class ProgressViewTest
{
	private static PersonalBests pbs(Object... kv)
	{
		Map<String, Double> m = new HashMap<>();
		for (int i = 0; i < kv.length; i += 2)
		{
			m.put((String) kv[i], (Double) kv[i + 1]);
		}
		return PersonalBests.of(m);
	}

	private static ProgressView view()
	{
		MemberProgressBook book = new MemberProgressBook(new Gson(), s -> { });
		Gson gson = new Gson();
		book.receive("GIM2", MemberProgressCodec.encode(gson, pbs("Zulrah", 65.0, "Vorkath", 90.0)));
		book.receive("GIM2", MemberProgressCodec.encode(gson,
			AchievementState.of(200, Arrays.asList("Cook's Assistant", "Dragon Slayer II"),
				Collections.singletonList("Monkey Madness II"), Collections.singletonMap("Ardougne", 0b0011))));
		return ProgressView.of(book, "GIM1", pbs("Zulrah", 70.0), null);
	}

	@Test
	public void ownFiguresJoinTheBookAndNobodyElseIsInvented()
	{
		ProgressView v = view();
		assertEquals(Arrays.asList("GIM1", "GIM2"), new ArrayList<>(v.members()));
		assertEquals(70.0, v.bests("GIM1").get("Zulrah"), 0.0);
		assertTrue(v.bests("Nobody").isEmpty());
		assertNull(v.achievements("GIM1"));
	}

	@Test
	public void cardLinesShowQuestsDiariesAndBests()
	{
		List<String> lines = view().cardLines("GIM2", 5);
		assertEquals("Quest points 200 · 2 quests done, 1 started", lines.get(0));
		assertEquals("Achievement diaries 2/48", lines.get(1));
		assertTrue(lines.contains("Zulrah best 1:05.0"));
		assertTrue(lines.contains("Vorkath best 1:30.0"));
	}

	@Test
	public void cardLinesCapTheBossesAndSayHowManyMoreAreElsewhere()
	{
		List<String> lines = view().cardLines("GIM2", 1);
		assertEquals(3 + 1, lines.size());
		assertTrue(lines.get(lines.size() - 1).startsWith("+1 more"));
	}

	@Test
	public void aMemberWhoSharedNothingHasNoLines()
	{
		assertTrue(view().cardLines("Stranger", 5).isEmpty());
		assertTrue(ProgressView.EMPTY.cardLines("GIM1", 5).isEmpty());
	}

	@Test
	public void bossRowsRankFastestFirstAndNameTheHolder()
	{
		List<ProgressView.BossRow> rows = view().bossRows();
		assertEquals("Vorkath", rows.get(0).boss);
		ProgressView.BossRow zulrah = rows.get(1);
		assertEquals("GIM2", zulrah.holder);
		assertEquals(65.0, zulrah.best, 0.0);
		assertEquals(Arrays.asList("GIM2", "GIM1"), new ArrayList<>(zulrah.times.keySet()));
		assertEquals("Zulrah: 1:05.0 GIM2 (best), 1:10.0 GIM1", zulrah.line());
	}

	@Test
	public void historyIsNewestFirstAndOnlyPersonalBests()
	{
		List<GroupEvent> feed = Arrays.asList(
			GroupEvent.of(GroupEvent.Type.PERSONAL_BEST, 100, "GIM1").text("Zulrah 1:10").build(),
			GroupEvent.of(GroupEvent.Type.CLOG, 150, "GIM1").text("Abyssal whip").build(),
			GroupEvent.of(GroupEvent.Type.PERSONAL_BEST, 200, "GIM2").text("Zulrah 1:05").build());
		assertEquals(Arrays.asList("GIM2: Zulrah 1:05", "GIM1: Zulrah 1:10"), ProgressView.history(feed, 10));
		assertEquals(1, ProgressView.history(feed, 1).size());
	}
}
