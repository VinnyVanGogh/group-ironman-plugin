package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** "Who benefits most": usable now, stat upgrade over best in slot, already owned, and the settings that narrow it. */
public class ItemBenefitTest
{
	private static Map<String, Integer> m(Object... kv)
	{
		Map<String, Integer> out = new HashMap<>();
		for (int i = 0; i < kv.length; i += 2)
		{
			out.put((String) kv[i], (Integer) kv[i + 1]);
		}
		return out;
	}

	private static final ItemBenefit.Item DEFENDER = new ItemBenefit.Item("Dragon defender", "shield",
		m("Defence", 60), m("melee_attack", 25, "melee_strength", 6, "defence", 25));

	private static ItemBenefit.Candidate who(String name, int defence, Map<String, Integer> current, boolean owns)
	{
		return new ItemBenefit.Candidate(name, m("Defence", defence), current, owns);
	}

	@Test
	public void ranksUsableBiggestUpgradeFirstThenClosest()
	{
		ItemBenefit.Candidate bare = who("GIM1", 70, m(), false);
		ItemBenefit.Candidate rune = who("GIM2", 70, m("melee_attack", 10, "melee_strength", 0, "defence", 20), false);
		ItemBenefit.Candidate far = who("GIM3", 30, m(), false);
		ItemBenefit.Candidate near = who("GIM4", 55, m(), false);
		List<ItemBenefit.Result> r = ItemBenefit.rank(DEFENDER, Arrays.asList(far, rune, near, bare),
			ItemBenefit.Settings.all());
		assertEquals(Arrays.asList("GIM1", "GIM2", "GIM4", "GIM3"), names(r));
		assertTrue(r.get(0).usableNow);
		assertFalse(r.get(2).usableNow);
		assertEquals(5, r.get(2).levelsShort);
		assertEquals(30, r.get(3).levelsShort);
		assertTrue(r.get(0).upgrade > r.get(1).upgrade);
	}

	@Test
	public void ownedEqualOrBetterIsLastWithNoGain()
	{
		ItemBenefit.Candidate has = who("GIM1", 99, m(), true);
		ItemBenefit.Candidate better = who("GIM2", 99, m("melee_attack", 40, "melee_strength", 10, "defence", 40),
			false);
		ItemBenefit.Candidate none = who("GIM3", 60, m(), false);
		List<ItemBenefit.Result> r = ItemBenefit.rank(DEFENDER, Arrays.asList(has, better, none),
			ItemBenefit.Settings.all());
		assertEquals("GIM3", r.get(0).name);
		assertEquals(Arrays.asList("GIM1", "GIM2"), names(r.subList(1, 3)));
		assertEquals(0, r.get(1).upgrade);
		assertTrue(r.get(1).alreadyOwned);
		assertTrue("a better item in the slot counts as owned", r.get(2).alreadyOwned);
	}

	@Test
	public void settingsChooseWhichStatsCount()
	{
		ItemBenefit.Settings defenceOnly = new ItemBenefit.Settings(Collections.singleton("defence"),
			Collections.singleton("shield"));
		ItemBenefit.Candidate a = who("GIM1", 99, m("defence", 25), false);
		assertEquals(0, ItemBenefit.rank(DEFENDER, Collections.singletonList(a), defenceOnly).get(0).upgrade);
		ItemBenefit.Candidate b = who("GIM2", 99, m(), false);
		assertEquals(25, ItemBenefit.rank(DEFENDER, Collections.singletonList(b), defenceOnly).get(0).upgrade);
	}

	@Test
	public void categoryOffMeansNoRanking()
	{
		ItemBenefit.Settings weaponsOnly = new ItemBenefit.Settings(ItemBenefit.Settings.all().stats(),
			Collections.singleton("weapon"));
		assertTrue(ItemBenefit.rank(DEFENDER, Collections.singletonList(who("GIM1", 99, m(), false)), weaponsOnly)
			.isEmpty());
	}

	private static List<String> names(List<ItemBenefit.Result> r)
	{
		List<String> out = new ArrayList<>();
		r.forEach(x -> out.add(x.name));
		return out;
	}
}
