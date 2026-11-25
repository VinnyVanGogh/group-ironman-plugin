package com.vinnyvangogh.groupironman;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * GIM must never open the world map by itself (RUNELITE-520). Source-level
 * guards, one per way a plugin can open or toggle the map: if one of these
 * fails, a new call path needs a control that says it opens the map.
 */
public class NeverOpensWorldMapTest
{
	private static final Path MAIN = Paths.get("src/main/java/com/vinnyvangogh/groupironman");

	private static List<Path> sources() throws IOException
	{
		try (Stream<Path> s = Files.walk(MAIN))
		{
			return s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
		}
	}

	private static List<String> filesContaining(String needle) throws IOException
	{
		List<String> hits = new ArrayList<>();
		for (Path p : sources())
		{
			if (new String(Files.readAllBytes(p), StandardCharsets.UTF_8).contains(needle))
			{
				hits.add(p.getFileName().toString());
			}
		}
		return hits;
	}

	@Test
	public void noWorldMapWidgetOrInterfaceIsTouched()
	{
		for (String needle : new String[] {"InterfaceID.Worldmap", "InterfaceID.WORLDMAP", "WorldMapPlugin",
			"WorldMapOverlay", "openWorldMap"})
		{
			try
			{
				assertEquals(needle, new ArrayList<String>(), filesContaining(needle));
			}
			catch (IOException e)
			{
				throw new AssertionError(e);
			}
		}
	}

	@Test
	public void noScriptsAreRunAndNoMenuActionIsInvoked() throws IOException
	{
		assertEquals(new ArrayList<String>(), filesContaining("client.runScript"));
		assertEquals(new ArrayList<String>(), filesContaining("client.menuAction"));
		assertEquals(new ArrayList<String>(), filesContaining("invokeMenuAction"));
	}

	@Test
	public void onlyTheMarkersTouchTheWorldMapAndOnlyOnAClick() throws IOException
	{
		// jumpOnClick acts only when the player clicks a marker on a map they opened.
		assertEquals(java.util.Collections.singletonList("MemberMapMarkers.java"), filesContaining("jumpOnClick"));
		assertEquals(java.util.Collections.singletonList("MemberMapMarkers.java"), filesContaining("WorldMapPointManager"));
		String markers = new String(Files.readAllBytes(MAIN.resolve("MemberMapMarkers.java")), StandardCharsets.UTF_8);
		assertTrue(markers.contains(".jumpOnClick(true)"));
		assertTrue("markers must not move the map on their own", !markers.contains("setWorldMapPositionTarget"));
	}

	@Test
	public void theFindCommandOnlyWritesToTheChatbox() throws IOException
	{
		String find = new String(Files.readAllBytes(MAIN.resolve("FindCommand.java")), StandardCharsets.UTF_8);
		assertTrue(!find.contains("PluginMessage") && !find.contains("eventBus") && !find.contains("import net.runelite"));
	}

	@Test
	public void onlyShortestPathBridgeSendsRoutes() throws IOException
	{
		// A "path" PluginMessage is the one hand-off to another plugin; it must stay in one place.
		assertEquals(java.util.Collections.singletonList("ShortestPathBridge.java"), filesContaining("new PluginMessage("));
	}
}
