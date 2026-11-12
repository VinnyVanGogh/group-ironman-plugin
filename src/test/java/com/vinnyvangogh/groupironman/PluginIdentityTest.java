package com.vinnyvangogh.groupironman;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.plugins.PluginDescriptor;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * GIM Utilities split out of Build Guard and both may run in the same client,
 * so nothing this plugin stores or sends may collide with Build Guard's. These
 * pin the names Build Guard uses as literals: they are its frozen identity and
 * this repo no longer has its classes.
 */
public class PluginIdentityTest
{
	private static final String BUILD_GUARD_CONFIG_GROUP = "build-guard";
	private static final String BUILD_GUARD_CACHE_DIR = "build-guard";
	private static final String[] BUILD_GUARD_MESSAGE_TYPES = {"BuildGuardPartyMessage", "BuildGuardGroupMessage"};
	/** The Hub plugin "Group Iron Man" (Spedwards, slug group-ironman) already owns this group. */
	private static final String HUB_GROUP_IRON_MAN_CONFIG_GROUP = "groupironman";

	/** Every setting at its default, the way a fresh install sees them. */
	private static final GroupIronmanConfig DEFAULTS = new GroupIronmanConfig()
	{
	};

	@Test
	public void configGroupIsItsOwnAndNotBuildGuards()
	{
		assertEquals("gimutilities", GroupIronmanConfig.GROUP);
		assertEquals(GroupIronmanConfig.GROUP, GroupIronmanConfig.class.getAnnotation(ConfigGroup.class).value());
		assertNotEquals(BUILD_GUARD_CONFIG_GROUP, GroupIronmanConfig.GROUP);
		assertNotEquals(HUB_GROUP_IRON_MAN_CONFIG_GROUP, GroupIronmanConfig.GROUP);
	}

	@Test
	public void partyMessageTypeIsDistinctFromBuildGuards()
	{
		// The party relay routes by simple class name, so the name is the type id.
		String type = VvgGroupIronmanMessage.class.getSimpleName();
		assertEquals("VvgGroupIronmanMessage", type);
		for (String theirs : BUILD_GUARD_MESSAGE_TYPES)
		{
			assertNotEquals(theirs, type);
		}
	}

	@Test
	public void cacheFolderIsNotBuildGuards()
	{
		assertEquals("gim-utilities", GroupService.CACHE_DIR);
		assertNotEquals(BUILD_GUARD_CACHE_DIR, GroupService.CACHE_DIR);
	}

	@Test
	public void pluginIsNamedGimUtilities()
	{
		assertEquals("GIM Utilities", GroupIronmanPlugin.class.getAnnotation(PluginDescriptor.class).name());
	}

	@Test
	public void everySwitchDefaultsToOff() throws Exception
	{
		List<String> on = new ArrayList<>();
		int switches = 0;
		for (Method m : GroupIronmanConfig.class.getDeclaredMethods())
		{
			if (m.getAnnotation(ConfigItem.class) == null || m.getReturnType() != boolean.class)
			{
				continue;
			}
			switches++;
			if ((Boolean) m.invoke(DEFAULTS))
			{
				on.add(m.getName());
			}
		}
		// Six share switches, the world map, ::find, "Sync with my group" and "Colour item names" (a ::find
		// look, off): a new switch must be added here on purpose. Sync is the one switch on by default (Board,
		// round 2): it does nothing without a group key, and even with one nothing is sent until the player
		// presses Join. Part A of the group events adds 44 more, every one off: "Share group events", "Post to
		// Discord", "Attach a screenshot", "Group box", and a Discord and an overlay switch per event kind.
		// Part C adds 15 kinds (a Discord and an overlay switch each) and one digest switch per category.
		// "Easter eggs" (Discord section) adds one more, off. "Show update notes" (Advanced) is the second switch
		// on by default (Board 2026-11-11): it only writes the welcome or what's new in the game chat, once per
		// version, and sends nothing anywhere.
		assertEquals(10 + 4 + 2 * GroupEvent.Type.values().length + GroupEvent.Category.values().length + 1 + 1,
			switches);
		assertEquals(92, switches);
		List<String> expectedOn = Arrays.asList("groupSync", "showUpdateNotes");
		Collections.sort(on);
		assertEquals(expectedOn, on);
		assertEquals("", DEFAULTS.groupLowStock());
		assertEquals("no key on a fresh install", "", DEFAULTS.groupKey());
	}

	@Test
	public void pluginStartsWithAllFeaturesOff()
	{
		// What GroupService.switches() and anyOn() build from the config on startUp.
		GroupOutbox.Switches s = new GroupOutbox.Switches(DEFAULTS.groupShareBank(), DEFAULTS.groupShareStorage(),
			DEFAULTS.groupShareStats(), DEFAULTS.groupShareCarried(), DEFAULTS.groupShareExtras(),
			DEFAULTS.groupShareDrops());
		assertFalse(s.any());
		assertTrue(LowStock.parse(DEFAULTS.groupLowStock()).isEmpty());
		assertFalse(DEFAULTS.groupWorldMap());
		assertFalse(DEFAULTS.groupFindCommand());
		// Sync on but no key: the panel says "No group key yet" and nothing joins or sends.
		assertEquals(GroupSync.State.NO_KEY, new GroupSync(new GroupSync.Party()
		{
			@Override
			public String passphrase()
			{
				return null;
			}

			@Override
			public void changeParty(String passphrase)
			{
				throw new AssertionError("joined on a fresh install");
			}
		}).state(DEFAULTS.groupSync(), "GroupShip", DEFAULTS.groupKey()));
	}
}
