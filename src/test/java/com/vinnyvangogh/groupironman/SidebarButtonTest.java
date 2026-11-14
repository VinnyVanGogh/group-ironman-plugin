package com.vinnyvangogh.groupironman;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.plugins.config.ConfigPlugin;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The sidebar button is there for as long as the plugin is on, whatever the
 * switches say (Board 2026-11-13: a friend with every switch off saw no panel
 * and thought the plugin was broken).
 */
public class SidebarButtonTest
{
	private final List<String> calls = new ArrayList<>();
	private final SidebarButton button = new SidebarButton(() -> calls.add("add"), () -> calls.add("remove"));

	@Test
	public void startUpAddsTheButtonWithEverySwitchOff()
	{
		button.startUp();
		assertTrue(button.shown());
		assertEquals(List.of("add"), calls);
	}

	@Test
	public void startingTwiceAddsItOnce()
	{
		button.startUp();
		button.startUp();
		assertEquals(List.of("add"), calls);
	}

	@Test
	public void shutDownRemovesIt()
	{
		button.startUp();
		button.shutDown();
		assertFalse(button.shown());
		assertEquals(List.of("add", "remove"), calls);
	}

	@Test
	public void shutDownWithoutStartUpRemovesNothing()
	{
		button.shutDown();
		assertTrue(calls.isEmpty());
	}

	@Test
	public void offNoticeOnlyWhenEverythingIsOff()
	{
		assertTrue(SidebarButton.offNotice(false));
		assertFalse(SidebarButton.offNotice(true));
	}

	@Test
	public void offNoticeWording()
	{
		assertEquals("Everything is off. Turn on what you want to share.", SidebarButton.OFF_TEXT);
	}

	/** Toggling switches arrives as ConfigChanged; the plugin's handler must never touch the toolbar. */
	@Test
	public void configChangesNeverTouchTheToolbar() throws IOException
	{
		String plugin = new String(Files.readAllBytes(
			Paths.get("src/main/java/com/vinnyvangogh/groupironman/GroupIronmanPlugin.java")), StandardCharsets.UTF_8);
		assertFalse("the plugin adds or removes the button only through SidebarButton",
			plugin.contains("addNavigation(") && !plugin.contains("new SidebarButton("));
		int handler = plugin.indexOf("public void onConfigChanged(");
		assertTrue(handler > 0);
		int end = plugin.indexOf("\n\t}\n", handler);
		String body = plugin.substring(handler, end);
		assertFalse(body, body.contains("Navigation"));
		assertFalse(body, body.contains("sidebar."));
		assertFalse(plugin, plugin.contains("updateNavButton"));
	}

	/**
	 * "Open settings" posts the same event an overlay's "Configure" entry does; RuneLite's
	 * ConfigPlugin handles it by opening that plugin's settings. Guard that the receiver
	 * still exists in the client we build against.
	 */
	@Test
	public void configPluginStillOpensSettingsFromOverlayMenuClicked() throws Exception
	{
		Method m = ConfigPlugin.class.getDeclaredMethod("onOverlayMenuClicked", OverlayMenuClicked.class);
		assertEquals(void.class, m.getReturnType());
	}
}
