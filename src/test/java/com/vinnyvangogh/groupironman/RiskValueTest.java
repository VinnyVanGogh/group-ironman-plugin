package com.vinnyvangogh.groupironman;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class RiskValueTest
{
	private static final int WHIP = 1;
	private static final int HELM = 2;
	private static final int BOOTS = 3;
	private static final int FOOD = 4;
	private static final int RUNES = 5;
	private static final int COINS_ITEM = 6;
	private static final int BAG_LOOT = 7;
	private static final Gson GSON = new Gson();

	private static final ItemLookup ITEMS = new ItemLookup()
	{
		@Override
		public String name(int id)
		{
			return "item" + id;
		}

		@Override
		public long price(int id)
		{
			switch (id)
			{
				case WHIP:
					return 1_000_000;
				case HELM:
					return 500_000;
				case BOOTS:
					return 100_000;
				case FOOD:
					return 1_000;
				case RUNES:
					return 200;
				case BAG_LOOT:
					return 50_000;
				default:
					return 0;
			}
		}

		@Override
		public long highAlch(int id)
		{
			return price(id) / 2;
		}
	};

	private static Map<Integer, Integer> map(int... pairs)
	{
		Map<Integer, Integer> m = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			m.put(pairs[i], pairs[i + 1]);
		}
		return m;
	}

	private static GroupCodec.Carried carried(Map<Integer, Integer> inv, Map<Integer, Integer> worn,
		Map<Integer, Integer> bag)
	{
		Map<SkillingContainer, GroupCodec.Held> contents = new EnumMap<>(SkillingContainer.class);
		if (bag != null)
		{
			contents.put(SkillingContainer.LOOTING_BAG, new GroupCodec.Held(bag.size(), bag));
		}
		return new GroupCodec.Carried(inv, worn, Collections.emptyList(), contents);
	}

	@Test
	public void unskulledKeepsThreeMostValuable()
	{
		RiskValue r = RiskValue.of(carried(map(BOOTS, 1, FOOD, 10), map(WHIP, 1, HELM, 1), null), ITEMS, false);
		// Keeps whip, helm, boots: loses 10 food.
		assertEquals(10_000, r.outcome(false, false).lostValue);
	}

	@Test
	public void skulledKeepsNothing()
	{
		RiskValue r = RiskValue.of(carried(map(BOOTS, 1, FOOD, 10), map(WHIP, 1, HELM, 1), null), ITEMS, false);
		assertEquals(1_000_000 + 500_000 + 100_000 + 10_000, r.outcome(true, false).lostValue);
	}

	@Test
	public void protectItemKeepsOneMore()
	{
		RiskValue r = RiskValue.of(carried(map(BOOTS, 1, FOOD, 10), map(WHIP, 1, HELM, 1), null), ITEMS, false);
		// Unskulled + protect keeps 4: the whip, helm, boots and one food.
		assertEquals(9_000, r.outcome(false, true).lostValue);
		// Skulled + protect keeps 1: the whip.
		assertEquals(500_000 + 100_000 + 10_000, r.outcome(true, true).lostValue);
	}

	@Test
	public void stackIsKeptOneUnitAtATime()
	{
		RiskValue r = RiskValue.of(carried(map(RUNES, 1000), map(), null), ITEMS, false);
		assertEquals(997 * 200, r.outcome(false, false).lostValue);
	}

	@Test
	public void lootingBagIsAlwaysLostAndNeverKept()
	{
		RiskValue r = RiskValue.of(carried(map(), map(WHIP, 1), map(BAG_LOOT, 4)), ITEMS, false);
		assertTrue(r.bagKnown());
		assertEquals(200_000, r.bagValue());
		assertEquals(200_000, r.outcome(false, false).lostValue);
		assertEquals(1_200_000, r.outcome(true, false).lostValue);
	}

	@Test
	public void unknownBagIsSaidSoNotGuessed()
	{
		RiskValue r = RiskValue.of(carried(map(FOOD, 1), map(), null), ITEMS, false);
		assertFalse(r.bagKnown());
		assertEquals(0, r.bagValue());
	}

	@Test
	public void rankingUsesGuidePriceEvenWhenShowingAlchemy()
	{
		RiskValue r = RiskValue.of(carried(map(BOOTS, 1), map(WHIP, 1, HELM, 1), null), ITEMS, true);
		// Skulled loses all three, shown at half.
		assertEquals(800_000, r.outcome(true, false).lostValue);
		assertEquals(0, r.outcome(false, false).lostValue);
	}

	@Test
	public void unpricedItemsRankLastAndCountZero()
	{
		RiskValue r = RiskValue.of(carried(map(COINS_ITEM, 1, WHIP, 1), map(), null), ITEMS, false);
		assertEquals(0, r.outcome(false, false).lostValue);
		assertEquals(1_000_000, r.outcome(true, false).lostValue);
	}

	@Test
	public void keptAndLostLinesShowTheSplit()
	{
		RiskValue r = RiskValue.of(carried(map(FOOD, 3), map(WHIP, 1, HELM, 1), null), ITEMS, false);
		RiskValue.Outcome o = r.outcome(false, false);
		assertEquals(java.util.Arrays.asList("1 x item1", "1 x item2", "1 x item4"), o.keptLines);
		assertEquals(Collections.singletonList("2 x item4"), o.lostLines);
	}

	@Test
	public void cardShowsBothFiguresAndHighlightsInWilderness()
	{
		RiskValue r = RiskValue.of(carried(map(FOOD, 10), map(WHIP, 1, HELM, 1, BOOTS, 1), null), ITEMS, false);
		PanelModel.MemberCard card = new PanelModel.MemberCard("A", false, true, "x", Collections.emptyList(),
			Collections.emptyList(), Collections.emptyList(), null, null);
		PanelModel.applyRisk(card, r, false, true, true);
		assertTrue(card.risk, card.risk.startsWith("Risk 10K"));
		assertTrue(card.risk, card.risk.contains("skulled 1."));
		assertTrue(card.riskHot);
		assertTrue(card.riskTooltip.contains("Protect Item"));
		assertTrue(card.riskTooltip.contains("In the Wilderness, skulled"));
	}

	@Test
	public void nothingCarriedShowsNoRiskLine()
	{
		RiskValue r = RiskValue.of(carried(map(), map(), null), ITEMS, false);
		PanelModel.MemberCard card = new PanelModel.MemberCard("A", false, true, "x", Collections.emptyList(),
			Collections.emptyList(), Collections.emptyList(), null, null);
		PanelModel.applyRisk(card, r, false, false, false);
		assertNull(card.risk);
	}

	@Test
	public void wildernessAndSkullSurviveTheWire()
	{
		MemberStatus s = MemberStatus.of(false, 0, false, false, true, true);
		Map<String, MemberStats.Level> skills = new LinkedHashMap<>();
		skills.put("Prayer", new MemberStats.Level(70, 43));
		MemberStats stats = new MemberStats(skills, 100, 100, 302, 12850, 0, 0, 0, s);
		MemberStatus back = GroupCodec.decode(GSON, GroupCodec.encodeStats(GSON, stats)).stats.status();
		assertTrue(back.inWilderness());
		assertTrue(back.skulled());
		assertEquals(s, back);
	}

	@Test
	public void olderSenderWithoutFlagsIsNotInWilderness()
	{
		Map<String, MemberStats.Level> skills = new LinkedHashMap<>();
		skills.put("Prayer", new MemberStats.Level(70, 43));
		MemberStats stats = new MemberStats(skills, 100, 100, 302, 12850, 0, 0, 0, MemberStatus.NONE);
		String json = GroupCodec.encodeStats(GSON, stats).replace(",\"wd\":0", "").replace(",\"sk\":0", "");
		MemberStatus back = GroupCodec.decode(GSON, json).stats.status();
		assertFalse(back.inWilderness());
		assertFalse(back.skulled());
	}
}
