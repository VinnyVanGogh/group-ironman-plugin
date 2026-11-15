package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.events.PluginMessage;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** What "Path to" says and sends, and that the path goes away when the item is done. */
public class PathSessionTest
{
	private static class FakePlugin
	{
	}

	private static final ItemPaths.Stop HARRY = new ItemPaths.Stop("Harry's Fishing Shop",
		new WorldPoint(2833, 3443, 0), false);
	private static final ItemPaths.Stop SARIM = new ItemPaths.Stop("Port Sarim shop", new WorldPoint(3014, 3224, 0),
		false);
	private static final ItemPaths.Target NET = ItemPaths.Target.listItem("list", 305);

	private final List<Object> posted = new ArrayList<>();

	private PathSession session(boolean installed, boolean enabled)
	{
		List<Object> plugins = installed ? Collections.singletonList(new FakePlugin()) : Collections.emptyList();
		return new PathSession(new ShortestPathBridge(() -> plugins, p -> enabled, FakePlugin.class.getName(),
			posted::add));
	}

	private PluginMessage last()
	{
		return (PluginMessage) posted.get(posted.size() - 1);
	}

	@Test
	public void sendsTheStopsTileAndNamesIt()
	{
		PathSession s = session(true, true);
		assertEquals("Path to: Harry's Fishing Shop.", s.go(NET, HARRY, true));
		assertEquals(1, posted.size());
		assertEquals("path", last().getName());
		assertEquals(Set.of(HARRY.at), last().getData().get("target"));
		assertEquals("Harry's Fishing Shop", s.active());
	}

	@Test
	public void noShortestPathSaysSoAndSendsNothing()
	{
		PathSession s = session(false, true);
		assertEquals("Shortest Path is not installed: get it from the Plugin Hub to path to items.",
			s.go(NET, HARRY, true));
		assertTrue(posted.isEmpty());
		assertEquals("", s.active());

		PathSession off = session(true, false);
		assertEquals("Shortest Path is not enabled: turn it on in the plugin list to path to items.",
			off.go(NET, HARRY, true));
		assertTrue(posted.isEmpty());
	}

	@Test
	public void noShortestPathNeverThrowsOnClearOrRecheck()
	{
		PathSession s = session(false, true);
		assertEquals("", s.stop());
		assertFalse(s.recheck(t -> Collections.emptyList()));
		assertTrue(posted.isEmpty());
	}

	@Test
	public void loggedOutHasNowhereToStartFrom()
	{
		PathSession s = session(true, true);
		assertEquals("Log in first: the path starts where you stand.", s.go(NET, HARRY, false));
		assertTrue(posted.isEmpty());
	}

	@Test
	public void aShortestPathThatThrowsIsReportedNotRaised()
	{
		PathSession s = new PathSession(new ShortestPathBridge(() -> Collections.singletonList(new FakePlugin()),
			p -> true, FakePlugin.class.getName(), e ->
			{
				throw new AssertionError("not on the client thread");
			}));
		assertEquals("Shortest Path did not take the path: update it, or try again.", s.go(NET, HARRY, true));
		assertEquals("", s.active());
	}

	@Test
	public void doneItemClearsThePath()
	{
		PathSession s = session(true, true);
		s.go(NET, HARRY, true);
		assertTrue(s.recheck(t -> Collections.emptyList()));
		assertEquals("clear", last().getName());
		assertEquals("", s.active());
		// Nothing active: a second recheck sends nothing.
		int before = posted.size();
		assertFalse(s.recheck(t -> Collections.emptyList()));
		assertEquals(before, posted.size());
	}

	/** Still needed and the shop still sells it: the path stays as it is, even if another shop is now nearer. */
	@Test
	public void stillNeededLeavesThePathAlone()
	{
		PathSession s = session(true, true);
		s.go(NET, HARRY, true);
		assertFalse(s.recheck(t -> Arrays.asList(SARIM, HARRY)));
		assertEquals(1, posted.size());
		assertEquals("Harry's Fishing Shop", s.active());
	}

	/** "Path to next": once the stop has nothing left to get, on to the nearest remaining one. */
	@Test
	public void stopWithNothingLeftMovesOnToTheNext()
	{
		PathSession s = session(true, true);
		s.go(ItemPaths.Target.next("list"), HARRY, true);
		assertTrue(s.recheck(t -> Collections.singletonList(SARIM)));
		assertEquals("path", last().getName());
		assertEquals(Set.of(SARIM.at), last().getData().get("target"));
		assertEquals("Port Sarim shop", s.active());
	}

	@Test
	public void stopClearsOnce()
	{
		PathSession s = session(true, true);
		s.go(NET, HARRY, true);
		assertEquals("Path cleared.", s.stop());
		assertEquals("clear", last().getName());
		assertEquals("", s.stop());
		assertEquals(2, posted.size());
	}
}
