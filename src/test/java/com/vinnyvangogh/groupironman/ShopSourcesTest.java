package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** An ironman sees only the shops they can be served at, with the wiki's price, and the monsters that drop it. */
public class ShopSourcesTest
{
	private static final String DATA = "{"
		+ "\"quests\":[{\"id\":1,\"name\":\"Fishing quest\",\"items\":[{\"name\":\"Big fishing net\",\"item_id\":305,"
		+ "\"sources\":[\"Shop: Fishing Guild Shop (20 Coins)\",\"Shop: Harry's Fishing Shop (25 Coins)\","
		+ "\"Drop: Goblin (1/128)\"],"
		+ "\"shop_names\":[\"Fishing Guild Shop\",\"Harry's Fishing Shop\",\"Zoo shop\",\"Nowhere shop\"],"
		+ "\"drop_source_names\":[\"Goblin\",\"Revenant imp\"]}]}],"
		+ "\"quest_names\":{\"1\":\"Fishing quest\",\"2\":\"Zogre Flesh Eaters\"},"
		+ "\"shops\":["
		+ "{\"name\":\"Fishing Guild Shop\",\"x\":2611,\"y\":3393,\"reachability\":{\"required_skills\":"
		+ "[{\"skill\":\"fishing\",\"level\":68,\"boostable\":true}]}},"
		+ "{\"name\":\"Harry's Fishing Shop\",\"x\":2833,\"y\":3443},"
		+ "{\"name\":\"Zoo shop\",\"x\":1,\"y\":1,\"reachability\":{\"required_quest_ids\":[2],"
		+ "\"required_quest_ids_started_only\":[2]}}],"
		+ "\"drop_locations\":[{\"source_name\":\"Revenant imp\",\"region\":\"wilderness\"},"
		+ "{\"source_name\":\"Goblin\",\"region\":\"general\"}]}";

	static QuestData data(String json) throws IOException
	{
		return QuestData.parse(new Gson(), new StringReader(json));
	}

	private static ShopSources.Viewer viewer(int fishing, QuestProgress quests)
	{
		Map<String, Integer> levels = new HashMap<>();
		levels.put("Fishing", fishing);
		return new ShopSources.Viewer(levels, quests);
	}

	private static String describe(List<ShopSources.Source> sources)
	{
		StringBuilder s = new StringBuilder();
		for (ShopSources.Source src : sources)
		{
			s.append(src.describe()).append('|');
		}
		return s.toString();
	}

	@Test
	public void shopsBehindLevelsOrQuestsAreLeftOutAndCounted() throws IOException
	{
		QuestData d = data(DATA);
		ShopSources.Options o = ShopSources.forViewer(d.item(305), viewer(59, QuestProgress.UNKNOWN), d);
		assertEquals("Shop: Harry's Fishing Shop (25 gp)|Drop: Goblin (1/128)|Drop: Revenant imp (Wilderness)|",
			describe(o.sources));
		// Guild (68 Fishing), the zoo (Zogre Flesh Eaters not started) and one with no tile.
		assertEquals(3, o.unreachableShops);
	}

	@Test
	public void meetingTheGatesOpensTheShops() throws IOException
	{
		QuestData d = data(DATA);
		QuestProgress started = QuestProgress.of(Collections.emptySet(), Collections.singleton("Zogre Flesh Eaters"));
		ShopSources.Options o = ShopSources.forViewer(d.item(305), viewer(68, started), d);
		assertTrue(describe(o.sources).startsWith("Shop: Fishing Guild Shop (20 gp)|Shop: Harry's Fishing Shop (25 gp)|"
			+ "Shop: Zoo shop|"));
		assertEquals(1, o.unreachableShops);
	}

	@Test
	public void whyAShopWillNotServe() throws IOException
	{
		QuestData d = data(DATA);
		assertEquals("68 fishing", ShopSources.blockedBy(d.shop("Fishing Guild Shop"),
			viewer(59, QuestProgress.UNKNOWN), d));
		assertEquals("Zogre Flesh Eaters", ShopSources.blockedBy(d.shop("Zoo shop"),
			viewer(59, QuestProgress.UNKNOWN), d));
		assertEquals("", ShopSources.blockedBy(d.shop("Harry's Fishing Shop"), viewer(1, QuestProgress.UNKNOWN), d));
	}

	@Test
	public void theShopAlertSaysItOncePerVisit() throws IOException
	{
		QuestData d = data(DATA);
		ShopVisits visits = new ShopVisits();
		List<String> harry = Collections.singletonList("Harry's Fishing Shop");
		assertTrue(visits.arrived(2800, 3443, 0, harry, d).isEmpty());
		assertEquals(harry, visits.arrived(2830, 3440, 0, harry, d));
		assertTrue("still there", visits.arrived(2833, 3443, 0, harry, d).isEmpty());
		assertTrue("upstairs is not there", new ShopVisits().arrived(2833, 3443, 1, harry, d).isEmpty());
		visits.arrived(2700, 3443, 0, harry, d);
		assertEquals("back again", harry, visits.arrived(2833, 3443, 0, harry, d));
	}
}
