package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.lang.reflect.Field;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.party.PartyService;
import org.junit.Test;
import org.mockito.Mockito;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** With group sync off, your own card still gets your levels and your online time from this client. */
public class OwnCardWithoutSyncTest
{
	private static void set(Object target, String name, Object value) throws Exception
	{
		Field f = GroupService.class.getDeclaredField(name);
		f.setAccessible(true);
		f.set(target, value);
	}

	@Test
	public void syncOffKeepsYouOnlineWhileIdle() throws Exception
	{
		GroupService service = service();
		service.onGameTick(new GameTick());
		long first = service.roster().lastHeard().get("Alice");
		// Nothing about you changed for a minute, and you were last heard long ago.
		service.roster().heard("Alice", 1L);
		set(service, "ticksSinceStats", GroupService.STATS_HEARTBEAT_TICKS);
		service.onGameTick(new GameTick());
		assertTrue("idle with sync off still refreshes you", service.roster().lastHeard().get("Alice") >= first);
	}

	@Test
	public void syncOffStillFillsYourOwnCard() throws Exception
	{
		GroupService service = service();
		assertFalse("sync is off", service.syncing());
		service.onGameTick(new GameTick());

		MemberStats mine = service.roster().allStats().get("Alice");
		assertNotNull("own card has stats with sync off", mine);
		assertFalse(mine.skills().isEmpty());
		assertEquals(301, mine.world());
		assertTrue("own card is online", service.roster().lastHeard().containsKey("Alice"));
	}

	private static GroupService service() throws Exception
	{
		Client client = Mockito.mock(Client.class);
		Player player = Mockito.mock(Player.class);
		Mockito.when(player.getName()).thenReturn("Alice");
		Mockito.when(client.getLocalPlayer()).thenReturn(player);
		Mockito.when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		Mockito.when(client.getRealSkillLevel(Mockito.any())).thenReturn(50);
		Mockito.when(client.getBoostedSkillLevel(Mockito.any())).thenReturn(50);
		Mockito.when(client.getWorld()).thenReturn(301);
		GroupIronmanConfig config = new GroupIronmanConfig()
		{
		};

		GroupService service = new GroupService();
		set(service, "client", client);
		set(service, "config", config);
		set(service, "gson", new Gson());
		set(service, "configManager", Mockito.mock(ConfigManager.class));
		set(service, "partyService", Mockito.mock(PartyService.class));
		set(service, "itemManager", Mockito.mock(ItemManager.class));
		set(service, "containers", Mockito.mock(CarriedContainers.class));
		set(service, "memberName", GroupMembers.key("Alice"));
		set(service, "cacheLoaded", true);
		// The mock client has no group roster, so a roster read would put the account back to waiting: hold the verdict above.
		set(service, "ticksSinceRoster", 0);
		return service;
	}
}
