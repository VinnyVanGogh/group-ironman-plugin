package com.vinnyvangogh.groupironman;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Who benefits most, from a typed item name to a ranked list. */
public class BenefitFinderTest
{
	private static final int WHIP = 4151;
	private static final int SCIMITAR = 1321;
	private static final int DEFENDER = 12954;

	/** Two weapons (slot 3) and a shield-slot item; a whip is +82 melee attack, a scimitar +8. */
	private static final BenefitFinder.Source SRC = new BenefitFinder.Source()
	{
		@Override
		public int find(String q)
		{
			return q.equalsIgnoreCase("abyssal whip") ? WHIP : q.equalsIgnoreCase("dragon defender") ? DEFENDER : -1;
		}

		@Override
		public String name(int id)
		{
			return id == WHIP ? "Abyssal whip" : id == DEFENDER ? "Dragon defender" : "Bronze scimitar";
		}

		@Override
		public int slot(int id)
		{
			return id == DEFENDER ? 5 : 3;
		}

		@Override
		public Map<String, Integer> stats(int id)
		{
			Map<String, Integer> m = new HashMap<>();
			m.put("melee_attack", id == WHIP ? 82 : id == SCIMITAR ? 8 : 25);
			return m;
		}

		@Override
		public Map<String, Integer> requirements(int id)
		{
			return id == WHIP ? Collections.singletonMap("Attack", 70) : Collections.emptyMap();
		}
	};

	private static BenefitFinder.Member member(String name, int attack, Integer worn, Integer carried)
	{
		return new BenefitFinder.Member(name, Collections.singletonMap("Attack", attack),
			worn == null ? Collections.<Integer>emptyList() : Collections.singletonList(worn),
			carried == null ? Collections.<Integer>emptyList() : Collections.singletonList(carried));
	}

	@Test
	public void ranksByUpgradeThenByHowCloseTheOthersAre()
	{
		BenefitFinder.Outcome o = BenefitFinder.run("abyssal whip", SRC, Arrays.asList(
			member("Low", 60, SCIMITAR, null), member("High", 80, SCIMITAR, null),
			member("Naked", 99, null, null)), ItemBenefit.Settings.all());
		assertEquals("Abyssal whip", o.item);
		assertEquals("Naked", o.ranked.get(0).name);
		assertEquals(82, o.ranked.get(0).upgrade);
		assertEquals("High", o.ranked.get(1).name);
		assertEquals(74, o.ranked.get(1).upgrade);
		assertEquals("Low", o.ranked.get(2).name);
		assertFalse(o.ranked.get(2).usableNow);
		assertEquals(10, o.ranked.get(2).levelsShort);
		assertFalse(o.requirementsUnknown);
	}

	@Test
	public void aMemberWhoOwnsItIsLastWithNoGain()
	{
		BenefitFinder.Outcome o = BenefitFinder.run("abyssal whip", SRC, Arrays.asList(
			member("Owner", 99, null, WHIP), member("Other", 99, null, null)), ItemBenefit.Settings.all());
		assertEquals("Other", o.ranked.get(0).name);
		assertTrue(o.ranked.get(1).alreadyOwned);
		assertEquals(0, o.ranked.get(1).upgrade);
	}

	@Test
	public void unknownItemAndEmptyQueryExplainThemselves()
	{
		assertTrue(BenefitFinder.run("zzz", SRC, Collections.<BenefitFinder.Member>emptyList(),
			ItemBenefit.Settings.all()).message.contains("No item found"));
		assertEquals("Type an item name.", BenefitFinder.run(" ", SRC,
			Collections.<BenefitFinder.Member>emptyList(), ItemBenefit.Settings.all()).message);
	}

	@Test
	public void aSwitchedOffCategoryIsNamedNotRanked()
	{
		ItemBenefit.Settings noShields = new ItemBenefit.Settings(ItemBenefit.ALL_STATS,
			Collections.singleton("weapon"));
		BenefitFinder.Outcome o = BenefitFinder.run("dragon defender", SRC,
			Collections.singletonList(member("A", 99, null, null)), noShields);
		assertTrue(o.ranked.isEmpty());
		assertTrue(o.message.contains("shield"));
	}

	@Test
	public void noKnownRequirementIsSaidSoTheListIsNotOverTrusted()
	{
		BenefitFinder.Outcome o = BenefitFinder.run("dragon defender", SRC,
			Collections.singletonList(member("A", 1, null, null)), ItemBenefit.Settings.all());
		assertTrue(o.requirementsUnknown);
		List<ItemBenefit.Result> r = o.ranked;
		assertEquals(1, r.size());
	}
}
