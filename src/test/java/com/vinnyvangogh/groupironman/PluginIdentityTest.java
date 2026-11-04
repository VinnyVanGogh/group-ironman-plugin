package com.vinnyvangogh.groupironman;

import java.lang.reflect.Method;
import java.util.ArrayList;
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
 * Group Ironman split out of Build Guard and both may run in the same client,
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
		assertEquals("vvg-group-ironman", GroupIronmanConfig.GROUP);
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
		assertEquals("group-ironman", GroupService.CACHE_DIR);
		assertNotEquals(BUILD_GUARD_CACHE_DIR, GroupService.CACHE_DIR);
	}

	@Test
	public void pluginIsNamedGroupIronman()
	{
		assertEquals("Group Ironman", GroupIronmanPlugin.class.getAnnotation(PluginDescriptor.class).name());
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
		// Six share switches, the world map and ::find: a new switch must be added here on purpose.
		assertEquals(8, switches);
		assertTrue("on by default: " + on, on.isEmpty());
		assertEquals("", DEFAULTS.groupLowStock());
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
	}
}
