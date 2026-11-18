package com.vinnyvangogh.groupironman;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class AchievementCaptureTest
{
	@Test
	public void diaryTiersBecomeAreaMasks()
	{
		Map<Integer, String> labels = new HashMap<>();
		labels.put(10, "Ardougne easy");
		labels.put(11, "Ardougne medium");
		labels.put(12, "Ardougne hard");
		labels.put(13, "Ardougne elite");
		labels.put(20, "Varrock easy");
		Map<Integer, Integer> values = new HashMap<>();
		values.put(10, 1);
		values.put(11, 1);
		values.put(12, 0);
		values.put(20, 1);

		Map<String, Integer> masks = AchievementCapture.diaries(labels, v -> values.getOrDefault(v, 0));

		assertEquals(0b0011, (int) masks.get("Ardougne"));
		assertEquals(0b0001, (int) masks.get("Varrock"));
	}

	@Test
	public void anAreaWithNoTiersDoneStillAppearsWithZero()
	{
		Map<Integer, String> labels = new HashMap<>();
		labels.put(1, "Desert easy");
		Map<String, Integer> masks = AchievementCapture.diaries(labels, v -> 0);
		assertTrue(masks.containsKey("Desert"));
		assertEquals(0, (int) masks.get("Desert"));
	}

	@Test
	public void unknownTierWordIsIgnored()
	{
		Map<Integer, String> labels = new HashMap<>();
		labels.put(1, "Desert legendary");
		labels.put(2, "NoSpaceLabel");
		assertTrue(AchievementCapture.diaries(labels, v -> 1).isEmpty());
	}

	@Test
	public void stateClampsQuestPointsAndKeepsDiaries()
	{
		Map<Integer, String> labels = new HashMap<>();
		labels.put(1, "Desert easy");
		AchievementState s = AchievementCapture.state(5000, java.util.Arrays.asList("Cook's Assistant"),
			java.util.Collections.emptyList(), labels, v -> 1);
		assertEquals(AchievementState.MAX_QP, s.questPoints());
		assertTrue(s.finished("Cook's Assistant"));
		assertEquals(1, s.diaryTiers("Desert"));
	}
}
