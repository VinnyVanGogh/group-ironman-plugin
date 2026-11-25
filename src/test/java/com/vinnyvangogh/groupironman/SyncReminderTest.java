package com.vinnyvangogh.groupironman;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SyncReminderTest
{
	@Test
	public void remindsOnlyWhenTheMemberCouldJoinButHasNot()
	{
		for (GroupSync.State s : GroupSync.State.values())
		{
			boolean expected = s == GroupSync.State.READY || s == GroupSync.State.STALE;
			assertEquals(s.name(), expected, SyncReminder.due(s));
		}
	}

	@Test
	public void neverNagsASoloPlayerOrAnotherParty()
	{
		// Sync switched off is the player's choice; another party is theirs too (Switch is in the panel).
		assertFalse(SyncReminder.due(GroupSync.State.OFF));
		assertFalse(SyncReminder.due(GroupSync.State.OTHER_PARTY));
		assertFalse(SyncReminder.due(GroupSync.State.CONNECTED));
	}

	@Test
	public void bannerIsHiddenUntilShownAndOffersTheJoinClick()
	{
		SyncReminder r = new SyncReminder();
		assertFalse(r.shown());
		assertTrue(r.getMenuEntries().stream().anyMatch(e -> SyncReminder.JOIN.equals(e.getOption())));
		r.show(true);
		assertTrue(r.shown());
	}
}
