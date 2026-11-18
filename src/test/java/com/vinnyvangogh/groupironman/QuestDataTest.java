package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The bundled quest data and the 1 Defence presets built on it. The Defence check is the one that produced the
 * preset list (Board 2026-11-14): no quest a preset reaches, prerequisites included, may reward Defence experience
 * or need Defence above 1.
 */
public class QuestDataTest
{
	private static final Gson GSON = new Gson();
	private static final QuestData DATA = QuestData.load(GSON);
	private static final ShopPresets PRESETS = ShopPresets.load(GSON);

	private static final String COOKS_ASSISTANT = "Cook's Assistant";
	private static final String RFD = "Recipe for Disaster/";
	private static final int BUCKET = 1925;
	private static final int MILK = 1927;
	private static final int EGG = 1944;
	private static final int FLOUR = 1933;

	/** Every quest {@code roots} reach, walked here independently of {@link ShopNeeds}. */
	private static Set<String> walk(List<String> roots)
	{
		Set<String> out = new HashSet<>();
		Deque<QuestData.Quest> todo = new ArrayDeque<>();
		for (String r : roots)
		{
			QuestData.Quest q = DATA.quest(r);
			assertNotNull("bundled data lacks " + r, q);
			todo.add(q);
		}
		while (!todo.isEmpty())
		{
			QuestData.Quest q = todo.pop();
			if (out.add(q.name()))
			{
				for (long pre : q.prerequisites())
				{
					QuestData.Quest p = DATA.quest(pre);
					assertNotNull(q.name() + " names prerequisite " + pre + ", which the data lacks", p);
					todo.add(p);
				}
			}
		}
		return out;
	}

	/** A preset's quests and every option it can be picked with. */
	private static List<String> everyRoot(ShopPresets.Preset p)
	{
		List<String> roots = new ArrayList<>(p.quests());
		if (p.choice() != null)
		{
			roots.addAll(p.choice().from());
		}
		return roots;
	}

	private static ShopPresets.Preset mithrilGloves()
	{
		ShopPresets.Preset p = PRESETS.preset("mithril-gloves");
		assertNotNull(p);
		return p;
	}

	@Test
	public void theDataSaysWhereItCameFrom()
	{
		assertEquals("VinnyVanGogh/questpath-plugin", DATA.source().get("repo"));
		assertEquals("ee638ff", DATA.source().get("commit"));
		assertEquals("src/main/resources/com/vinnyvangogh/questpath/quests.json", DATA.source().get("path"));
		assertTrue(DATA.questNames().size() >= DATA.quests().size());
	}

	/** Every quest in the closure of {@code roots} that rewards Defence xp or needs Defence above 1. */
	private static List<String> defenceProblems(QuestData data, List<String> roots)
	{
		List<String> problems = new ArrayList<>();
		for (QuestData.Quest q : ShopNeeds.closure(data, roots))
		{
			q.rewards().stream().filter(r -> "defence".equalsIgnoreCase(r.skill()) && r.xp() > 0)
				.forEach(r -> problems.add(q.name() + " rewards " + r.xp() + " Defence xp"));
			q.requirements().stream().filter(r -> "defence".equalsIgnoreCase(r.skill()) && r.level() > 1)
				.forEach(r -> problems.add(q.name() + " needs " + r.level() + " Defence"));
		}
		return problems;
	}

	@Test
	public void noPresetQuestRewardsDefenceOrNeedsIt()
	{
		assertFalse(PRESETS.presets().isEmpty());
		List<String> problems = new ArrayList<>();
		for (ShopPresets.Preset p : PRESETS.presets())
		{
			// The independent walk first, so a missing prerequisite fails loudly rather than shrinking the closure.
			assertEquals(walk(everyRoot(p)).size(), ShopNeeds.closure(DATA, everyRoot(p)).size());
			defenceProblems(DATA, everyRoot(p)).forEach(s -> problems.add(p.name() + ": " + s));
		}
		assertEquals(Collections.emptyList(), problems);
	}

	@Test
	public void theDefenceCheckCatchesAPrerequisiteThatRewardsDefence() throws Exception
	{
		// Sir Amik Varze's shape: the subquest itself is clean, a prerequisite (Nature Spirit) is not.
		QuestData d = ShopSourcesTest.data("{\"quests\":["
			+ "{\"id\":157,\"name\":\"RFD/Sir Amik\",\"prerequisites\":[55]},"
			+ "{\"id\":55,\"name\":\"Nature Spirit\",\"rewards\":[{\"skill\":\"defence\",\"xp\":2000}]},"
			+ "{\"id\":9,\"name\":\"Needs Defence\",\"requirements\":[{\"skill\":\"defence\",\"level\":30}]}]}");
		assertEquals(Collections.singletonList("Nature Spirit rewards 2000.0 Defence xp"),
			defenceProblems(d, Collections.singletonList("RFD/Sir Amik")));
		assertEquals(1, defenceProblems(d, Collections.singletonList("Needs Defence")).size());
	}

	@Test
	public void theNinePresetsAreThe1DefenceMilestones()
	{
		List<String> ids = new ArrayList<>();
		PRESETS.presets().forEach(p -> ids.add(p.id()));
		assertEquals(Arrays.asList("mithril-gloves", "avas-accumulator", "ancient-magicks", "dragon-scimitar",
			"dragon-dagger", "climbing-boots", "ibans-staff", "god-books", "waterfall-quest"), ids);
		for (ShopPresets.Preset p : PRESETS.presets())
		{
			for (String quest : everyRoot(p))
			{
				assertNotNull(p.name() + ": " + quest + " is not in the bundled data", DATA.quest(quest));
				assertNotNull(p.name() + ": the game has no quest for " + quest, QuestStates.quest(quest));
			}
		}
	}

	@Test
	public void mithrilGlovesPicksFiveOfSixWithAwowogeiToSwapIn()
	{
		ShopPresets.Choice c = mithrilGloves().choice();
		assertEquals(5, c.count());
		assertEquals(6, c.from().size());
		assertEquals(5, c.defaults().size());
		assertFalse(c.defaults().contains(RFD + "Freeing King Awowogei"));
		for (String banned : Arrays.asList("Sir Amik", "Lumbridge Guide", "Culinaromancer"))
		{
			c.from().forEach(option -> assertFalse(option, option.contains(banned)));
		}
		assertTrue(mithrilGloves().roots(null).containsAll(c.defaults()));
		assertTrue("four picks fall back to the defaults",
			mithrilGloves().roots(c.defaults().subList(0, 4)).containsAll(c.defaults()));
	}

	/** Item lines for {@code roots} with nothing done, counted here straight from the data. */
	private static Map<Integer, Long> expected(List<String> roots)
	{
		Map<Integer, Long> out = new TreeMap<>();
		for (String name : walk(roots))
		{
			for (QuestData.Item i : DATA.quest(name).items())
			{
				if (!i.optional() && !i.unresolved() && !i.grouped() && i.itemId() > 0)
				{
					out.merge(i.itemId(), (long) Math.max(1, i.quantity()), Long::sum);
				}
			}
		}
		return out;
	}

	private static Map<Integer, Long> counts(Map<Integer, ShopNeeds.Need> needs)
	{
		Map<Integer, Long> out = new TreeMap<>();
		needs.forEach((id, n) -> out.put(id, n.count));
		return out;
	}

	@Test
	public void mithrilGlovesLinesMatchTheDataForAMemberWithNothingDone()
	{
		List<String> roots = mithrilGloves().roots(null);
		Map<Integer, ShopNeeds.Need> needs = ShopNeeds.forMember(DATA, roots, QuestProgress.UNKNOWN);

		assertEquals(expected(roots), counts(needs));
		// Cook's Assistant's milk, egg and flour are there; its bucket is optional and is not.
		assertTrue(needs.containsKey(MILK));
		assertTrue(needs.containsKey(EGG));
		assertTrue(needs.containsKey(FLOUR));
		assertFalse(needs.containsKey(BUCKET));
		assertTrue(needs.get(MILK).quests.contains(COOKS_ASSISTANT));
	}

	@Test
	public void theListShrinksAsQuestsAreDone()
	{
		List<String> roots = mithrilGloves().roots(null);
		Map<Integer, Long> none = counts(ShopNeeds.forMember(DATA, roots, QuestProgress.UNKNOWN));
		Map<Integer, Long> afterCooks = counts(ShopNeeds.forMember(DATA, roots,
			QuestProgress.of(Collections.singleton(COOKS_ASSISTANT), Collections.emptySet())));

		assertEquals(none.get(MILK) - 1, (long) afterCooks.get(MILK));
		assertEquals(none.get(EGG) - 1, (long) afterCooks.getOrDefault(EGG, 0L));
		long totalNone = none.values().stream().mapToLong(Long::longValue).sum();
		long totalAfter = afterCooks.values().stream().mapToLong(Long::longValue).sum();
		assertTrue(totalAfter < totalNone);

		QuestProgress all = QuestProgress.of(walk(roots), Collections.emptySet());
		assertTrue("everything done leaves nothing to get", ShopNeeds.forMember(DATA, roots, all).isEmpty());
	}

	@Test
	public void swappingInAwowogeiBringsMonkeyMadness()
	{
		List<String> picks = new ArrayList<>(mithrilGloves().choice().defaults());
		picks.remove(RFD + "Freeing Skrach Uglogwee");
		picks.add(RFD + "Freeing King Awowogei");
		List<String> roots = mithrilGloves().roots(picks);
		Set<String> quests = new HashSet<>();
		ShopNeeds.questsToDo(DATA, roots, QuestProgress.UNKNOWN).forEach(q -> quests.add(q.name()));
		assertTrue(quests.contains("Monkey Madness I"));
		assertFalse(quests.contains("Big Chompy Bird Hunting"));
		assertEquals(expected(roots), counts(ShopNeeds.forMember(DATA, roots, QuestProgress.UNKNOWN)));
	}

	@Test
	public void prerequisitesComeBeforeTheQuestsThatNeedThem()
	{
		List<QuestData.Quest> order = ShopNeeds.questsToDo(DATA, Collections.singletonList("Desert Treasure I"),
			QuestProgress.UNKNOWN);
		Map<String, Integer> at = new LinkedHashMap<>();
		for (int i = 0; i < order.size(); i++)
		{
			at.put(order.get(i).name(), i);
		}
		assertTrue(at.get("Death Plateau") < at.get("Troll Stronghold"));
		assertTrue(at.get("Troll Stronghold") < at.get("Desert Treasure I"));
	}

	@Test
	public void aCarriedShopNamesAQuestTheDataKnows()
	{
		for (QuestData.Shop s : DATA.shops())
		{
			if (s.reachability() != null)
			{
				for (Long id : s.reachability().requiredQuestIds())
				{
					assertNotNull(s.name() + " is behind quest " + id, DATA.questName(id));
				}
			}
		}
	}
}
