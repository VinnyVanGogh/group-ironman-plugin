package com.vinnyvangogh.groupironman;

import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The bundled wiki drop table, and that it reaches a list line no quest names. */
public class ItemDropsTest
{
	private static final int DRAGON_WARHAMMER = 13576;

	@Test
	public void dragonWarhammerIsALizardmanShamanDrop()
	{
		List<ShopSources.Source> drops = ItemDrops.bundled().of(DRAGON_WARHAMMER);
		assertEquals(1, drops.size());
		assertEquals("Drop: Lizardman shaman (1/3,000)", drops.get(0).describe());
	}

	@Test
	public void everyLineOfTheBundledTableParses()
	{
		String text = RegionNames.resource(ItemDrops.RESOURCE);
		long lines = text.lines().filter(l -> !l.trim().isEmpty() && !l.trim().startsWith("#")).count();
		assertTrue("the table is bundled", lines > 20);
		assertEquals("no line was skipped as malformed", lines, ItemDrops.bundled().size());
	}

	@Test
	public void aCustomItemNoQuestNamesGetsItsDropsNotNoKnownShop()
	{
		ShopSources.Viewer viewer = new ShopSources.Viewer(Collections.emptyMap(), QuestProgress.UNKNOWN);
		ShopSources.Options o = ShopSources.forViewer(DRAGON_WARHAMMER, null, viewer, QuestData.empty(),
			ItemDrops.bundled());
		assertEquals("Get: Drop: Lizardman shaman (1/3,000)", ShoppingTab.sourceText(o));
	}

	@Test
	public void anItemInNeitherSaysSoPlainly()
	{
		ShopSources.Viewer viewer = new ShopSources.Viewer(Collections.emptyMap(), QuestProgress.UNKNOWN);
		ShopSources.Options o = ShopSources.forViewer(1, null, viewer, QuestData.empty(), ItemDrops.bundled());
		assertTrue(o.sources.isEmpty());
		assertEquals("Not sold in any shop, and no drop on file: check the wiki.", ShoppingTab.sourceText(o));
	}

	@Test
	public void malformedLinesAreSkipped()
	{
		ItemDrops d = ItemDrops.parse("# note\nx|Bad|Goblin=1/2\n5|No drops\n7|Ok|Goblin=1/2|Imp\n");
		assertEquals(1, d.size());
		assertEquals(2, d.of(7).size());
		assertFalse(d.of(7).get(1).describe().contains("("));
	}
}
