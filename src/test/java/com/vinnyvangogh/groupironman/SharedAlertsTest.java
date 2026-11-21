package com.vinnyvangogh.groupironman;

import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SharedAlertsTest
{
	private static final long T0 = 1_700_000_000_000L;

	@Test
	public void cooldownBlocksAnUnchangedRepeatThenLetsItThrough()
	{
		SharedAlerts.Cooldown c = new SharedAlerts.Cooldown();
		assertTrue(c.allow("Alex", "Fortunato", "10 more Shark", T0));
		assertFalse(c.allow("Alex", "Fortunato", "10 more Shark", T0 + 60_000));
		assertFalse(c.allow("alex", "FORTUNATO", "10 more Shark", T0 + SharedAlerts.COOLDOWN_MS - 1));
		assertTrue(c.allow("Alex", "Fortunato", "10 more Shark", T0 + SharedAlerts.COOLDOWN_MS));
	}

	@Test
	public void changedItemsFireInsideTheCooldown()
	{
		SharedAlerts.Cooldown c = new SharedAlerts.Cooldown();
		assertTrue(c.allow("Alex", "Fortunato", "10 more Shark", T0));
		assertTrue(c.allow("Alex", "Fortunato", "10 more Shark, 5 more Wine", T0 + 1_000));
		assertFalse(c.allow("Alex", "Fortunato", "10 more Shark, 5 more Wine", T0 + 2_000));
	}

	@Test
	public void cooldownIsPerMemberAndPerShop()
	{
		SharedAlerts.Cooldown c = new SharedAlerts.Cooldown();
		assertTrue(c.allow("Alex", "Fortunato", "x", T0));
		assertTrue(c.allow("Sam", "Fortunato", "x", T0));
		assertTrue(c.allow("Alex", "Zeke's", "x", T0));
	}

	@Test
	public void cooldownIsBounded()
	{
		SharedAlerts.Cooldown c = new SharedAlerts.Cooldown();
		for (int i = 0; i < SharedAlerts.MAX_KEPT * 3; i++)
		{
			assertTrue(c.allow("Alex", "shop" + i, "x", T0 + i));
		}
	}

	@Test
	public void whichEventsAreShared()
	{
		assertTrue(SharedAlerts.isShared(SharedAlerts.shopNear("Alex", "Fortunato", "10 more Shark", T0)));
		assertTrue(SharedAlerts.isShared(GroupEvent.of(GroupEvent.Type.STOCK_SHORT, T0, "Alex").text("Shark").build()));
		assertTrue(SharedAlerts.isShared(GroupEvent.of(GroupEvent.Type.REQUEST, T0, "Alex").text("Cape")
			.number(RequestBoard.KIND_MADE).build()));
		assertTrue(SharedAlerts.isShared(GroupEvent.of(GroupEvent.Type.REQUEST, T0, "Alex").text("Cape")
			.number(RequestBoard.KIND_DONE).build()));
		assertFalse(SharedAlerts.isShared(GroupEvent.of(GroupEvent.Type.REQUEST, T0, "Alex").text("Cape")
			.number(RequestBoard.KIND_CLAIMED).items(Collections.singletonMap(1, 1)).build()));
		assertFalse(SharedAlerts.isShared(GroupEvent.clog(T0, "Alex", "Visage")));
	}

	@Test
	public void staleEventsGetNoChatLine()
	{
		GroupEvent e = SharedAlerts.shopNear("Alex", "Fortunato", "x", T0);
		assertTrue(SharedAlerts.recent(e, T0 + SharedAlerts.MAX_AGE_MS));
		assertFalse(SharedAlerts.recent(e, T0 + SharedAlerts.MAX_AGE_MS + 1));
		assertFalse(SharedAlerts.recent(e, T0 - SharedAlerts.MAX_AGE_MS - 1));
	}

	@Test
	public void theChatLinesRead()
	{
		GroupEvent shop = SharedAlerts.shopNear("Alex", "Fortunato's Fine Wine", "10 more Shark", T0);
		assertEquals("Alex: is at Fortunato's Fine Wine: your group needs 10 more Shark",
			SharedAlerts.chatLine(shop, null));
		GroupEvent low = SharedAlerts.stockShort("Alex", new LowStock.Alert("Shark", 12, 100), T0);
		assertEquals("Alex: group storage is low on Shark: 12 left, minimum 100", SharedAlerts.chatLine(low, null));
	}

	@Test
	public void theNewKindsTravelAndAreValid()
	{
		GroupEvent shop = SharedAlerts.shopNear("Alex", "Fortunato", "10 more Shark", T0);
		assertTrue(shop.isValid());
		assertTrue(shop.type.travels());
		assertTrue(GroupEvent.Type.STOCK_SHORT.travels());
		assertTrue(EventText.covers(GroupEvent.Type.SHOP_NEAR));
		assertTrue(EventText.covers(GroupEvent.Type.STOCK_SHORT));
	}
}
