package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.events.PluginMessage;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins {@link ShortestPathBridge}: it finds the plugin by class name, degrades
 * to the right availability when it is missing or off, and posts exactly one
 * {@code PluginMessage("shortestpath", "path", {"target": set})}, or the right
 * outcome when it cannot.
 *
 * <p>Copied from Quest Path's {@code ShortestPathBridgeTest} (questpath-plugin
 * {@code 80cdafb}, {@code src/test/java/com/vinnyvangogh/questpath/ShortestPathBridgeTest.java}).
 * Shortest Path is not on the classpath; {@link FakePlugin} stands in for it,
 * so nothing here runs its real {@code onPluginMessage}.
 */
public class ShortestPathBridgeTest
{
	/** Stand-in for {@code shortestpath.ShortestPathPlugin}: nothing is ever called on it. */
	private static class FakePlugin
	{
	}

	private static ShortestPathBridge bridge(Supplier<Collection<?>> plugins, Predicate<Object> enabled,
		Consumer<Object> eventPoster)
	{
		return new ShortestPathBridge(plugins, enabled, FakePlugin.class.getName(), eventPoster);
	}

	private static ShortestPathBridge bridge(Supplier<Collection<?>> plugins, Predicate<Object> enabled)
	{
		return bridge(plugins, enabled, event ->
		{
		});
	}

	private static Set<WorldPoint> oneDestination()
	{
		return Set.of(new WorldPoint(3200, 3200, 0));
	}

	private static ShortestPathBridge.RouteOutcome routeOf(ShortestPathBridge bridge, Set<WorldPoint> destinations)
	{
		AtomicReference<ShortestPathBridge.RouteOutcome> result = new AtomicReference<>();
		bridge.route(destinations, result::set);
		return result.get();
	}

	@Test
	public void notInstalledWhenNoMatchingPluginIsLoaded()
	{
		ShortestPathBridge bridge = bridge(java.util.Collections::emptyList, plugin -> true);
		assertEquals(ShortestPathBridge.Availability.NOT_INSTALLED, bridge.availability());
		assertEquals(ShortestPathBridge.RouteOutcome.UNAVAILABLE, routeOf(bridge, oneDestination()));
	}

	@Test
	public void notEnabledWhenThePluginSaysSo()
	{
		FakePlugin plugin = new FakePlugin();
		ShortestPathBridge bridge = bridge(() -> List.of(plugin), p -> false);
		assertEquals(ShortestPathBridge.Availability.NOT_ENABLED, bridge.availability());
		assertEquals(ShortestPathBridge.RouteOutcome.UNAVAILABLE, routeOf(bridge, oneDestination()));
	}

	/** A thrown enabled-check is "no": fail closed. */
	@Test
	public void notEnabledWhenTheEnabledCheckItselfThrows()
	{
		FakePlugin plugin = new FakePlugin();
		ShortestPathBridge bridge = bridge(() -> List.of(plugin), p ->
		{
			throw new RuntimeException("cannot read plugin state");
		});
		assertEquals(ShortestPathBridge.Availability.NOT_ENABLED, bridge.availability());
	}

	/** A thrown plugin listing is the same as none found. */
	@Test
	public void notInstalledWhenListingPluginsThrows()
	{
		ShortestPathBridge bridge = bridge(() ->
		{
			throw new RuntimeException("plugin manager not ready");
		}, p -> true);
		assertEquals(ShortestPathBridge.Availability.NOT_INSTALLED, bridge.availability());
	}

	@Test
	public void availableWhenTheFakePluginIsLoadedAndEnabled()
	{
		ShortestPathBridge bridge = bridge(() -> List.of(new FakePlugin()), p -> true);
		assertEquals(ShortestPathBridge.Availability.AVAILABLE, bridge.availability());
	}

	@Test
	public void noDestinationsWhenTheSetIsEmpty()
	{
		List<Object> posted = new ArrayList<>();
		ShortestPathBridge bridge = bridge(() -> List.of(new FakePlugin()), p -> true, posted::add);

		assertEquals(ShortestPathBridge.RouteOutcome.NO_DESTINATIONS, routeOf(bridge, Set.of()));
		assertEquals(ShortestPathBridge.RouteOutcome.NO_DESTINATIONS, routeOf(bridge, null));
		assertTrue("nothing should have been posted for an empty route", posted.isEmpty());
	}

	/** The case the class exists for: one {@code PluginMessage("shortestpath", "path", {"target": set})}. */
	@Test
	public void sentPostsExactlyOnePluginMessageWithTheDestinationSet()
	{
		Set<WorldPoint> destinations = oneDestination();
		List<Object> posted = new ArrayList<>();
		ShortestPathBridge bridge = bridge(() -> List.of(new FakePlugin()), p -> true, posted::add);

		assertEquals(ShortestPathBridge.RouteOutcome.SENT, routeOf(bridge, destinations));
		assertEquals(1, posted.size());
		PluginMessage message = (PluginMessage) posted.get(0);
		assertEquals("shortestpath", message.getNamespace());
		assertEquals("path", message.getName());
		assertEquals(destinations, message.getData().get("target"));
	}

	@Test
	public void callFailedWhenThePosterThrows()
	{
		ShortestPathBridge bridge = bridge(() -> List.of(new FakePlugin()), p -> true, event ->
		{
			throw new RuntimeException("event bus rejected the message");
		});
		assertEquals(ShortestPathBridge.RouteOutcome.CALL_FAILED, routeOf(bridge, oneDestination()));
	}

	/** Shortest Path's client-thread assertion is an Error; it must be caught, not escape to the panel. */
	@Test
	public void callFailedWhenThePosterThrowsAnAssertionError()
	{
		ShortestPathBridge bridge = bridge(() -> List.of(new FakePlugin()), p -> true, event ->
		{
			throw new AssertionError("not on the client thread");
		});
		assertEquals(ShortestPathBridge.RouteOutcome.CALL_FAILED, routeOf(bridge, oneDestination()));
	}

	@Test
	public void unavailableAtCallTimeIsNotPosted()
	{
		List<Object> posted = new ArrayList<>();
		ShortestPathBridge bridge = bridge(() -> List.of(new FakePlugin()), p -> false, posted::add);

		assertEquals(ShortestPathBridge.RouteOutcome.UNAVAILABLE, routeOf(bridge, oneDestination()));
		assertTrue("an unavailable bridge must never post", posted.isEmpty());
	}

	/** The post happens only through the client-thread hand-off, never before or instead of it. */
	@Test
	public void clientThreadEventPosterRunsThePostOnlyThroughTheHandoff()
	{
		List<String> order = new ArrayList<>();
		Consumer<Runnable> clientThreadStandIn = task ->
		{
			order.add("handed off to the client thread");
			task.run();
		};

		Consumer<Object> poster = ShortestPathBridge.clientThreadEventPoster(clientThreadStandIn,
			event -> order.add("posted"));
		poster.accept(new PluginMessage("shortestpath", "path", Map.of()));

		assertEquals(List.of("handed off to the client thread", "posted"), order);
	}

	@Test
	public void clientThreadEventPosterNeverPostsWithoutTheHandoffRunningItsTask()
	{
		List<Runnable> capturedTasks = new ArrayList<>();
		List<Object> posted = new ArrayList<>();

		Consumer<Object> poster = ShortestPathBridge.clientThreadEventPoster(capturedTasks::add, posted::add);
		poster.accept(new PluginMessage("shortestpath", "path", Map.of()));

		assertEquals(1, capturedTasks.size());
		assertTrue("nothing is posted until the hand-off runs its task", posted.isEmpty());
		capturedTasks.get(0).run();
		assertEquals(1, posted.size());
	}

	/** Clearing sends Shortest Path's own "clear", never a "path" with no targets (which re-sends the old route). */
	@Test
	public void clearSendsTheClearMessageAndNeverAnEmptyPath()
	{
		List<Object> posted = new ArrayList<>();
		ShortestPathBridge bridge = bridge(() -> List.of(new FakePlugin()), plugin -> true, posted::add);

		assertTrue(bridge.clear());
		assertEquals(1, posted.size());
		PluginMessage message = (PluginMessage) posted.get(0);
		assertEquals("shortestpath", message.getNamespace());
		assertEquals(ShortestPathBridge.CLEAR_MESSAGE_NAME, message.getName());
		assertNotEquals(ShortestPathBridge.MESSAGE_NAME, message.getName());
	}

	@Test
	public void clearSendsNothingWhenShortestPathIsUnavailable()
	{
		List<Object> posted = new ArrayList<>();
		ShortestPathBridge bridge = bridge(List::of, plugin -> true, posted::add);

		assertFalse(bridge.clear());
		assertTrue(posted.isEmpty());
	}
}
