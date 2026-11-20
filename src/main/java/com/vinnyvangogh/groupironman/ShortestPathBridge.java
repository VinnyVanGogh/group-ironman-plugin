package com.vinnyvangogh.groupironman;

import java.util.*;
import java.util.function.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.events.PluginMessage;

/**
 * Hands world points to the Shortest Path plugin's own pathfinder.
 *
 * <p>Copied from Quest Path's {@code ShortestPathBridge}
 * (questpath-plugin {@code 80cdafb},
 * {@code src/main/java/com/vinnyvangogh/questpath/ShortestPathBridge.java}), with
 * its tests ({@code ShortestPathBridgeTest}). Only the package and the
 * player-facing words changed; every finding below was made there and is
 * carried over, not re-derived.
 *
 * <p>This class touches nothing in the plugin it talks to: no reflection, no
 * member access, no classloading. Shortest Path ({@code Skretzo/shortest-path})
 * listens for RuneLite's own {@code net.runelite.client.events.PluginMessage},
 * a public class shipped inside {@code runelite-client} itself, and documents
 * the messages for other plugins in its {@code docs/Cross-plugin-communication.md}
 * (read 2026-11-14): namespace {@code shortestpath}; {@code path} "shows a
 * path, replacing the current one" and takes a {@code target} that is one
 * {@code WorldPoint} or a collection of them; {@code clear} "removes the shown
 * path". Posting one is two calls against RuneLite's public API ({@code new
 * PluginMessage(namespace, name, data)} and {@code EventBus#post(Object)}).
 * Quest Path confirmed the constants and the {@code Set<WorldPoint>} branch
 * with {@code javap -c -p} against the installed jar's
 * {@code shortestpath/ShortestPathPlugin.class}.
 *
 * <p><b>What {@link RouteOutcome#SENT} proves.</b> The message went out built
 * exactly as Shortest Path's handler reads it. Not that a line is drawn on
 * screen: {@code EventBus#post} is fire-and-forget, and seeing the path still
 * needs a human at a running client.
 *
 * <p><b>Why {@link #availability()} degrades three ways.</b> The running
 * instance is found through a {@code Supplier<Collection<?>>} of loaded plugins
 * (production: {@code PluginManager#getPlugins}) matched by class name, the
 * same "is this plugin loaded" check RuneLite's own {@code PluginManager}
 * makes, with no reflective member access. A missing or disabled Shortest Path
 * must be visible and distinct: a silent no-op reads as "nothing needed".
 *
 * <p><b>{@link #eventPoster} must land the {@code EventBus#post} call on the
 * client thread.</b> Shortest Path's {@code onPluginMessage} asserts it is on
 * the client thread and does nothing to get there, and {@code EventBus#post}
 * calls subscribers on the caller's thread. Quest Path first wired a bare
 * {@code eventBus::post} from a Swing button and every click threw {@code
 * AssertionError}; build the poster with {@link #clientThreadEventPoster}.
 * That {@code AssertionError} is also why the catches below take {@code
 * RuntimeException | AssertionError}: {@code EventBus#post}'s per-subscriber
 * catch covers only {@code Exception}, never an {@code Error}.
 *
 * <p><b>Shortest Path clears its own path on arrival</b>, within its {@code
 * reachedDistance} (5 tiles by default), with no event to react to. This
 * plugin's {@code PathSession} clears or moves on only when the item itself is
 * done.
 */
@Slf4j
class ShortestPathBridge
{
	/** Shortest Path's own plugin class, matched by name. Confirmed against the installed jar. */
	static final String PLUGIN_CLASS_NAME = "shortestpath.ShortestPathPlugin";

	/**
	 * The {@code PluginMessage} name that clears Shortest Path's route.
	 *
	 * <p>A separate message from {@link #MESSAGE_NAME}: a {@code path} message
	 * with an empty target set does not clear, it re-sends the previous
	 * targets ({@code onPluginMessage} calls {@code restartPathfinding} with
	 * {@code pathfinder.getTargets()} when the set is empty). Quest Path shipped
	 * that bug once; the player's words were that "stop the route" made it
	 * "flick to the last one, then back, and stay".
	 */
	static final String CLEAR_MESSAGE_NAME = "clear";

	/** The {@code PluginMessage} namespace Shortest Path listens on ({@code ShortestPathPlugin.CONFIG_GROUP}). */
	static final String CONFIG_GROUP = "shortestpath";

	/** The {@code PluginMessage} name Shortest Path listens for ({@code ShortestPathPlugin.PLUGIN_MESSAGE_PATH}). */
	static final String MESSAGE_NAME = "path";

	/**
	 * The data-map key Shortest Path reads its destination set from
	 * ({@code ShortestPathPlugin.PLUGIN_MESSAGE_TARGET}). This class only ever
	 * sends a {@code Set<WorldPoint>}.
	 */
	static final String TARGET_KEY = "target";

	/** Whether a route can be handed to Shortest Path right now, and why not when it cannot. */
	enum Availability
	{
		/** Reachable. */
		AVAILABLE("Path to with Shortest Path"),
		NOT_INSTALLED("Shortest Path is not installed"),
		NOT_ENABLED("Shortest Path is not enabled"),
		/** Present and enabled, but something this class depends on did not resolve. */
		UNAVAILABLE("Shortest Path integration unavailable");

		private final String menuLabel;

		Availability(String menuLabel)
		{
			this.menuLabel = menuLabel;
		}

		/** The exact text to put in front of the player; doubles as the reason. */
		String menuLabel()
		{
			return menuLabel;
		}
	}

	/** What became of a {@link #route} call. */
	enum RouteOutcome
	{
		/** {@link #availability()} was not {@link Availability#AVAILABLE} when the call landed. */
		UNAVAILABLE,
		/** Called with an empty or null destination set: never posted, an empty set is not a route. */
		NO_DESTINATIONS,
		/**
		 * {@link #eventPoster} threw while this method could still see it. Off the
		 * client thread (the panel's buttons) the post is only enqueued, so this
		 * means the hand-off itself failed.
		 */
		CALL_FAILED,
		/**
		 * Handed to {@link #eventPoster} without throwing. <b>Not</b>
		 * confirmation that Shortest Path acted on it: there is no read-back.
		 */
		SENT,
	}

	private final Supplier<Collection<?>> plugins;
	private final Predicate<Object> pluginEnabled;
	private final String pluginClassName;

	/** Posts an event; in production built with {@link #clientThreadEventPoster}, never a bare {@code eventBus::post}. */
	private final Consumer<Object> eventPoster;

	/**
	 * Production wiring: the real plugin list, the real event bus.
	 *
	 * @param plugins every plugin RuneLite currently knows about, e.g. {@code pluginManager::getPlugins}
	 * @param pluginEnabled whether a given plugin instance is switched on
	 * @param eventPoster built with {@link #clientThreadEventPoster}
	 */
	ShortestPathBridge(Supplier<Collection<?>> plugins, Predicate<Object> pluginEnabled, Consumer<Object> eventPoster)
	{
		this(plugins, pluginEnabled, PLUGIN_CLASS_NAME, eventPoster);
	}

	/**
	 * Test seam: the plugin class name is overridable so a test can point
	 * {@link #findPlugin} at a fixture instead of a real Shortest Path, which is
	 * not on this project's classpath.
	 */
	ShortestPathBridge(Supplier<Collection<?>> plugins, Predicate<Object> pluginEnabled, String pluginClassName,
		Consumer<Object> eventPoster)
	{
		this.plugins = plugins;
		this.pluginEnabled = pluginEnabled;
		this.pluginClassName = pluginClassName;
		this.eventPoster = eventPoster;
	}

	/**
	 * A poster that always lands {@code post} on the client thread, whichever
	 * thread calls it. Takes the hand-off as a plain {@code Consumer<Runnable>}
	 * so a test can stand in for {@code ClientThread}; production passes
	 * {@code clientThread::invoke}.
	 */
	static Consumer<Object> clientThreadEventPoster(Consumer<Runnable> onClientThread, Consumer<Object> post)
	{
		return object -> onClientThread.accept(() -> post.accept(object));
	}

	/** Whether a route can be handed to Shortest Path right now, and why not when it cannot. */
	Availability availability()
	{
		Object plugin = findPlugin();
		if (plugin == null)
		{
			return Availability.NOT_INSTALLED;
		}
		if (!isEnabledSafely(plugin))
		{
			return Availability.NOT_ENABLED;
		}
		return Availability.AVAILABLE;
	}

	/**
	 * Clears whatever path Shortest Path is drawing, with its own {@code clear}
	 * message. Deliberately not {@code route(emptySet())}: see
	 * {@link #CLEAR_MESSAGE_NAME}.
	 *
	 * @return whether the message went out
	 */
	boolean clear()
	{
		if (availability() != Availability.AVAILABLE)
		{
			log.debug("Not clearing the Shortest Path route: {}", availability());
			return false;
		}
		try
		{
			eventPoster.accept(new PluginMessage(CONFIG_GROUP, CLEAR_MESSAGE_NAME, Map.of()));
			log.debug("Cleared the Shortest Path route");
			return true;
		}
		catch (RuntimeException | AssertionError e)
		{
			log.warn("Clearing the Shortest Path route threw", e);
			return false;
		}
	}

	/**
	 * Posts destinations to Shortest Path as one target set.
	 *
	 * @param destinations handed to Shortest Path as they are
	 * @param onOutcome called exactly once, before this method returns
	 */
	void route(Set<WorldPoint> destinations, Consumer<RouteOutcome> onOutcome)
	{
		if (availability() != Availability.AVAILABLE)
		{
			log.debug("Not sending a route to Shortest Path: {}", availability());
			onOutcome.accept(RouteOutcome.UNAVAILABLE);
			return;
		}
		if (destinations == null || destinations.isEmpty())
		{
			onOutcome.accept(RouteOutcome.NO_DESTINATIONS);
			return;
		}
		try
		{
			Map<String, Object> data = Map.of(TARGET_KEY, destinations);
			eventPoster.accept(new PluginMessage(CONFIG_GROUP, MESSAGE_NAME, data));
		}
		// Not Throwable: an OutOfMemoryError here is not "Shortest Path didn't get the route".
		catch (RuntimeException | AssertionError e)
		{
			log.warn("Posting PluginMessage(\"{}\", \"{}\", ...) with {} destination(s) failed"
				+ ": Shortest Path may be out of date", CONFIG_GROUP, MESSAGE_NAME, destinations.size(), e);
			onOutcome.accept(RouteOutcome.CALL_FAILED);
			return;
		}
		// The one place a "the button does nothing" report can be checked from the client log.
		log.info("Sent {} destination(s) to Shortest Path", destinations.size());
		onOutcome.accept(RouteOutcome.SENT);
	}

	private boolean isEnabledSafely(Object plugin)
	{
		try
		{
			return pluginEnabled.test(plugin);
		}
		catch (RuntimeException e)
		{
			log.warn("Could not read Shortest Path's enabled state", e);
			return false;
		}
	}

	private Object findPlugin()
	{
		Collection<?> loaded;
		try
		{
			loaded = plugins.get();
		}
		catch (RuntimeException e)
		{
			log.warn("Could not list loaded plugins while looking for Shortest Path", e);
			return null;
		}
		if (loaded == null)
		{
			return null;
		}
		for (Object candidate : loaded)
		{
			if (candidate != null && pluginClassName.equals(candidate.getClass().getName()))
			{
				return candidate;
			}
		}
		return null;
	}
}
