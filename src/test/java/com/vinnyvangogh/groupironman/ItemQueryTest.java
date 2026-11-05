package com.vinnyvangogh.groupironman;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static com.vinnyvangogh.groupironman.FakeItems.*;

/** One search engine for the panel and ::find, and ::find never opens anything. */
public class ItemQueryTest
{
	private static final FakeItems ITEMS = new FakeItems();
	private static final long NOW = 1_000_000_000L;

	private static Map<Integer, Integer> items(int... pairs)
	{
		Map<Integer, Integer> out = new TreeMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			out.put(pairs[i], pairs[i + 1]);
		}
		return out;
	}

	@Test
	public void guamFindsEveryGuamItem()
	{
		ItemQuery q = ItemQuery.parse("guam");
		assertTrue(q.matches("Grimy guam leaf"));
		assertTrue(q.matches("Guam leaf"));
		assertTrue(q.matches("Guam potion (unf)"));
		assertFalse(q.matches("Ranarr weed"));
	}

	@Test
	public void caseWordsInAnyOrderAndAliases()
	{
		assertTrue(ItemQuery.parse("GUAM").matches("Grimy guam leaf"));
		assertTrue(ItemQuery.parse("leaf grimy").matches("Grimy guam leaf"));
		assertTrue(ItemQuery.parse("  grimy   guam ").matches("Grimy guam leaf"));
		assertTrue(ItemQuery.parse("ppot").matches("Prayer potion(4)"));
		assertTrue(ItemQuery.parse("dwh").matches("Dragon warhammer"));
		assertFalse("one word must still match", ItemQuery.parse("leaf ranarr").matches("Grimy guam leaf"));
		assertNull(ItemQuery.parse("g"));
		assertNull(ItemQuery.parse(null));
	}

	@Test
	public void findCommandAndThePanelGiveTheSameAnswer()
	{
		GroupRoster roster = new GroupRoster();
		roster.putBank("GroupShip", new BankSnapshot(NOW - 60_000, items(GRIMY_GUAM, 15, GUAM_UNF, 3, SHARK, 9)));

		List<PanelModel.FindRow> panel = PanelModel.find("guam", roster, ITEMS, NOW);
		assertEquals(2, panel.size());
		assertEquals("Grimy guam leaf - 15 in GroupShip's bank", panel.get(0).text);
		assertEquals(GRIMY_GUAM, panel.get(0).itemId);
		assertEquals("Guam potion (unf) - 3 in GroupShip's bank", panel.get(1).text);

		List<String> chat = FindCommand.answer("guam", roster.searchSources(), roster.log().entries(), ITEMS, NOW);
		assertEquals(2, chat.size());
		assertEquals("Grimy guam leaf: GroupShip bank 15 (1m ago)", chat.get(0));
		assertEquals("Guam potion (unf): GroupShip bank 3 (1m ago)", chat.get(1));
	}

	@Test
	public void findCommandOnlyReturnsText()
	{
		// The ::find answer is computed from data alone: no client, nothing to open.
		List<String> out = FindCommand.answer("guam", Collections.emptyList(), Collections.emptyList(), ITEMS, NOW);
		assertEquals(1, out.size());
		assertTrue(out.get(0), out.get(0).startsWith("Nothing found for 'guam'"));
	}

	@Test
	public void nothingButTheMapMarkersTouchesTheWorldMap() throws Exception
	{
		// ::find and the panel search must never open the world map. Only the markers
		// class may use world map APIs, and it only adds points (opened by the player's click).
		Path dir = Paths.get("src/main/java/com/vinnyvangogh/groupironman");
		List<String> offenders = new ArrayList<>();
		try (Stream<Path> files = Files.list(dir))
		{
			for (Path p : files.collect(Collectors.toList()))
			{
				String name = p.getFileName().toString();
				String code = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
				// World map APIs, not the 'groupWorldMap' setting's name.
				boolean map = code.replace("groupWorldMap", "").matches("(?s).*(WorldMap|WORLDMAP|Worldmap).*");
				if (map && !name.equals("MemberMapMarkers.java"))
				{
					offenders.add(name);
				}
			}
		}
		assertEquals(Collections.emptyList(), offenders);
		String markers = new String(Files.readAllBytes(dir.resolve("MemberMapMarkers.java")), StandardCharsets.UTF_8);
		assertFalse(markers.contains("setWorldMap") || markers.contains("openWorldMap") || markers.contains("runScript"));
	}
}
