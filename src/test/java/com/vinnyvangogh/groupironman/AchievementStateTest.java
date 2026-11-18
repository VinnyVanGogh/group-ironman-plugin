package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Quests, quest points and diaries shared with the group. */
public class AchievementStateTest
{
	@Test
	public void hostileInputIsClamped()
	{
		Map<String, Integer> d = new HashMap<>();
		d.put("Ardougne", 0b11111111);
		d.put("", 3);
		AchievementState a = AchievementState.of(-9, Arrays.asList("A", "B"), Arrays.asList("B", "C"), d);
		assertEquals(0, a.questPoints());
		assertTrue(a.finished("B"));
		assertFalse("finished wins over started", a.inProgress().contains("B"));
		assertEquals(0b1111, a.diaryTiers("Ardougne"));
		assertEquals(1, a.diaries().size());
	}

	@Test
	public void diaryTierCountAndNames()
	{
		AchievementState a = AchievementState.of(100, Collections.emptyList(), Collections.emptyList(),
			Collections.singletonMap("Varrock", 0b0111));
		assertEquals(3, a.diaryTiersDone());
		assertTrue(AchievementState.tierDone(a.diaryTiers("Varrock"), AchievementState.Tier.MEDIUM));
		assertFalse(AchievementState.tierDone(a.diaryTiers("Varrock"), AchievementState.Tier.ELITE));
		assertEquals(0, a.diaryTiers("Nowhere"));
	}

	@Test
	public void wireRoundTrip()
	{
		Gson gson = new Gson();
		AchievementState a = AchievementState.of(150, Arrays.asList("Cook's Assistant"),
			Arrays.asList("Dragon Slayer II"), Collections.singletonMap("Falador", 0b0011));
		assertEquals(a, MemberProgressCodec.decodeAchievements(gson, MemberProgressCodec.encode(gson, a)));
		assertNull(MemberProgressCodec.decodeAchievements(gson, "{\"v\":1,\"k\":\"ach\",\"f\":7}"));
	}
}
