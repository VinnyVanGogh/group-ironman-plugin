package com.vinnyvangogh.groupironman;

import java.lang.reflect.Proxy;
import java.util.EnumSet;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EventSettingsTest
{
	private static final GroupIronmanConfig DEFAULTS = new GroupIronmanConfig()
	{
	};

	@Test
	public void everyKindHasADiscordAndAnOverlaySwitch()
	{
		for (GroupEvent.Type t : GroupEvent.Type.values())
		{
			assertTrue(t + " Discord switch", EventSettings.DISCORD.containsKey(t));
			assertTrue(t + " overlay switch", EventSettings.OVERLAY.containsKey(t));
		}
	}

	@Test
	public void aFreshInstallWantsNothing()
	{
		EventSettings s = EventSettings.from(DEFAULTS);
		assertTrue(s.discord.isEmpty());
		assertTrue(s.overlay.isEmpty());
		assertFalse(s.share);
		for (GroupEvent.Type t : GroupEvent.Type.values())
		{
			assertFalse(s.wants(t));
		}
		assertEquals(1_000_000, s.dropMin);
		assertEquals(7, s.backAfterDays);
	}

	@Test
	public void eachSwitchTurnsOnItsOwnKindOnly()
	{
		// Through a config proxy, the way RuneLite reads settings: only discordPet and overlayDeath are on.
		GroupIronmanConfig config = (GroupIronmanConfig) Proxy.newProxyInstance(
			GroupIronmanConfig.class.getClassLoader(), new Class<?>[]{GroupIronmanConfig.class},
			(p, m, args) ->
			{
				if (m.getReturnType() == boolean.class)
				{
					return m.getName().equals("discordPet") || m.getName().equals("overlayDeath");
				}
				return m.getReturnType() == int.class ? 3 : null;
			});
		EventSettings s = EventSettings.from(config);
		assertEquals(EnumSet.of(GroupEvent.Type.PET), s.discord);
		assertEquals(EnumSet.of(GroupEvent.Type.DEATH), s.overlay);
		assertEquals(3, s.levelMin);
	}

	@Test
	public void eventTextCoversEveryKind()
	{
		FakeItems items = new FakeItems();
		for (GroupEvent.Type t : GroupEvent.Type.values())
		{
			assertTrue(t + " has words", EventText.covers(t));
			assertFalse(EventText.describe(EventFixtures.sample(t), items).isEmpty());
		}
	}
}
