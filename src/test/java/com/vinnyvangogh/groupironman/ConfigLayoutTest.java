package com.vinnyvangogh.groupironman;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The settings panel is eight sections in a fixed order, and moving a setting
 * between them never touches what it is saved under: the config group and
 * every keyName stay exactly as they were published.
 */
public class ConfigLayoutTest
{
	/** Every keyName published before the eight-section layout. Never remove one: that resets it for every user. */
	private static final Set<String> PUBLISHED_KEYS = new HashSet<>(Arrays.asList(
		"groupSync", "groupKey", "groupShareBank", "groupShareStorage", "groupShareStats", "groupShareCarried",
		"groupLowStock", "groupShareExtras", "groupShareDrops", "groupDropMinValue", "groupWorldMap",
		"groupFindCommand", "groupShareEvents", "findQueryColour", "findColourItemNames", "findItemColour",
		"findYourBankColour", "findMemberBankColour", "findStorageColour", "findCarriedColour", "eventDropMinValue",
		"eventStorageMinValue", "eventLevelMin", "eventBackAfterDays", "eventUntradeableIgnore", "discordEnabled",
		"discordWebhookUrl", "discordDropsUrl", "discordDeathsUrl", "discordStorageUrl", "discordProgressUrl",
		"discordGroupUrl", "discordActivitiesUrl", "discordStyle", "discordScreenshot", "discordGravestoneScreenshot",
		"discordMention", "discordQuietStart", "discordQuietEnd", "discordEasterEggs", "discordLegendAt",
		"discordFreeloaderAt", "discordCombineLevels", "discordPosterName", "discordNameMain", "discordNameDrops",
		"discordNameDeaths", "discordNameStorage", "discordNameProgress", "discordNameGroup", "discordNameActivities",
		"discordValuableDrop", "discordPet", "discordCollectionLog", "discordUntradeableUnique", "discordDeath",
		"discordStorageDeposit", "discordStorageWithdrawal", "discordStorageSummary", "discordStorageMinimum",
		"discordStorageLast", "discordLevelUp", "discordMaxed", "discordXpMilestone", "discordCombatLevel",
		"discordTotalLevel", "discordQuest", "discordQuestPoints", "discordOnline", "discordOnlineStatus",
		"discordOfflineGrace", "discordBack", "discordBarrows", "overlayDurationSeconds", "overlayPosition",
		"overlayMaxStacked", "overlayGroupBox", "overlayGroupBoxEvents", "overlayValuableDrop", "overlayPet",
		"overlayCollectionLog", "overlayUntradeableUnique", "overlayDeath", "overlayStorageDeposit",
		"overlayStorageWithdrawal", "overlayStorageSummary", "overlayStorageMinimum", "overlayStorageLast",
		"overlayLevelUp", "overlayMaxed", "overlayXpMilestone", "overlayCombatLevel", "overlayTotalLevel",
		"overlayQuest", "overlayQuestPoints", "overlayOnline", "overlayBack", "overlayBarrows", "eventClueMinValue",
		"eventKcEvery", "eventGravestoneWarnMinutes", "discordRaidUnique", "discordClue", "discordDryStreak",
		"discordDeathValue", "discordGravestone", "discordHardcoreLife", "discordWilderness", "discordDiary",
		"discordCombatTask", "discordKcMilestone", "discordPersonalBest", "discordWeeklyRecap", "discordSlayer",
		"discordFarming", "discordBirdhouse", "overlayRaidUnique", "overlayClue", "overlayDryStreak",
		"overlayDeathValue", "overlayGravestone", "overlayHardcoreLife", "overlayWilderness", "overlayDiary",
		"overlayCombatTask", "overlayKcMilestone", "overlayPersonalBest", "overlayWeeklyRecap", "overlaySlayer",
		"overlayFarming", "overlayBirdhouse", "discordDigestHour", "digestDrops", "digestDeaths", "digestStorage",
		"digestProgress", "digestGroup", "digestActivities"));

	private static final List<String> SECTION_ORDER = Arrays.asList("Group", "Discord: connection",
		"Discord: what to post", "Discord: how to post", "Discord: daily digest", "In-game", "Thresholds", "Advanced");

	private static final Set<String> CLOSED = new HashSet<>(Arrays.asList("Thresholds", "Advanced"));

	@Test
	public void theConfigGroupIsUnchanged()
	{
		assertEquals("gimutilities", GroupIronmanConfig.GROUP);
		assertEquals("gimutilities", GroupIronmanConfig.class.getAnnotation(ConfigGroup.class).value());
	}

	@Test
	public void everyPublishedKeyIsStillThere()
	{
		Set<String> keys = new HashSet<>();
		for (Method m : items())
		{
			keys.add(m.getAnnotation(ConfigItem.class).keyName());
		}
		Set<String> lost = new HashSet<>(PUBLISHED_KEYS);
		lost.removeAll(keys);
		assertTrue("published keys renamed or removed: " + lost, lost.isEmpty());
	}

	@Test
	public void exactlyEightSectionsInOrderWithOnlyThresholdsAndAdvancedClosed() throws Exception
	{
		List<ConfigSection> sections = new ArrayList<>(sections().values());
		sections.sort(Comparator.comparingInt(ConfigSection::position));
		List<String> names = new ArrayList<>();
		Set<Integer> positions = new HashSet<>();
		for (ConfigSection s : sections)
		{
			names.add(s.name());
			assertTrue("duplicate section position " + s.position(), positions.add(s.position()));
			assertEquals(s.name() + " closed by default", CLOSED.contains(s.name()), s.closedByDefault());
			assertFalse(s.name() + " has no description", s.description().trim().isEmpty());
		}
		assertEquals(SECTION_ORDER, names);
	}

	@Test
	public void everySettingIsInOneOfTheSections() throws Exception
	{
		Set<String> sectionKeys = sections().keySet();
		for (Method m : items())
		{
			String section = m.getAnnotation(ConfigItem.class).section();
			assertTrue(m.getName() + " has no section, or an unknown one: '" + section + "'",
				sectionKeys.contains(section));
		}
	}

	@Test
	public void positionsAreUniqueWithinEachSection()
	{
		Map<String, Set<Integer>> seen = new HashMap<>();
		for (Method m : items())
		{
			ConfigItem item = m.getAnnotation(ConfigItem.class);
			assertTrue(m.getName() + " shares position " + item.position() + " in " + item.section(),
				seen.computeIfAbsent(item.section(), k -> new HashSet<>()).add(item.position()));
		}
	}

	@Test
	public void onlineStatusIsAboutHowToPostNotWhichEvents()
	{
		assertEquals(GroupIronmanConfig.discordHowSection, sectionOf("discordOnlineStatus"));
		assertEquals(GroupIronmanConfig.discordHowSection, sectionOf("discordCombineLevels"));
		assertEquals(GroupIronmanConfig.discordHowSection, sectionOf("discordGravestoneScreenshot"));
		assertEquals(GroupIronmanConfig.discordConnectionSection, sectionOf("discordPosterName"));
	}

	@Test
	public void everySettingHasAPlainNameAndDescription()
	{
		for (Method m : items())
		{
			ConfigItem item = m.getAnnotation(ConfigItem.class);
			assertFalse(m.getName() + " has no name", item.name().trim().isEmpty());
			assertFalse(m.getName() + " has no description", item.description().trim().isEmpty());
		}
	}

	private static String sectionOf(String method)
	{
		return items().stream().filter(m -> m.getName().equals(method)).findFirst()
			.orElseThrow(AssertionError::new).getAnnotation(ConfigItem.class).section();
	}

	/** Section key (the field's value) to its annotation. */
	private static Map<String, ConfigSection> sections() throws IllegalAccessException
	{
		Map<String, ConfigSection> out = new HashMap<>();
		for (Field f : GroupIronmanConfig.class.getFields())
		{
			ConfigSection s = f.getAnnotation(ConfigSection.class);
			if (s != null)
			{
				assertTrue("duplicate section key in " + f.getName(), out.put((String) f.get(null), s) == null);
			}
		}
		return out;
	}

	/** Every setting method, also used by ConfigFitTest. */
	static List<Method> items()
	{
		List<Method> out = new ArrayList<>();
		for (Method m : GroupIronmanConfig.class.getMethods())
		{
			if (m.isAnnotationPresent(ConfigItem.class))
			{
				out.add(m);
			}
		}
		return out;
	}
}
