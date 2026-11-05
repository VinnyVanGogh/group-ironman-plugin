package com.vinnyvangogh.groupironman;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GroupSyncTest
{
	private static final String KEY = "00112233445566778899aabbccddeeff";
	private static final String GROUP = "GroupShip";

	/** Stands in for PartyService: remembers every change. */
	private static final class FakeParty implements GroupSync.Party
	{
		String current;
		final List<String> changes = new ArrayList<>();

		@Override
		public String passphrase()
		{
			return current;
		}

		@Override
		public void changeParty(String passphrase)
		{
			changes.add(passphrase);
			current = passphrase;
		}
	}

	@Test
	public void statesFollowTheSettingsTheKeyAndTheParty()
	{
		FakeParty party = new FakeParty();
		GroupSync sync = new GroupSync(party);
		assertEquals(GroupSync.State.OFF, sync.state(false, GROUP, KEY));
		assertEquals(GroupSync.State.NO_KEY, sync.state(true, GROUP, ""));
		assertEquals(GroupSync.State.NO_GROUP, sync.state(true, "", KEY));
		assertEquals(GroupSync.State.READY, sync.state(true, GROUP, KEY));
		party.current = "raids-with-friends";
		assertEquals(GroupSync.State.OTHER_PARTY, sync.state(true, GROUP, KEY));
		party.current = GroupKey.passphrase(GROUP, KEY);
		assertEquals(GroupSync.State.CONNECTED, sync.state(true, GROUP, KEY));
		party.current = GroupKey.passphrase(GROUP, "ffeeddccbbaa99887766554433221100");
		assertEquals("our own party with an old key", GroupSync.State.STALE, sync.state(true, GROUP, KEY));
	}

	@Test
	public void nothingJoinsWithoutTheClick()
	{
		FakeParty party = new FakeParty();
		GroupSync sync = new GroupSync(party);
		sync.state(true, GROUP, KEY);
		assertTrue(party.changes.isEmpty());
		assertTrue(sync.join(true, GROUP, KEY));
		assertEquals(GroupKey.passphrase(GROUP, KEY), party.current);
	}

	@Test
	public void joinNeverReplacesAPartyThePlayerJoined()
	{
		FakeParty party = new FakeParty();
		party.current = "raids-with-friends";
		GroupSync sync = new GroupSync(party);
		assertFalse(sync.join(true, GROUP, KEY));
		assertEquals("raids-with-friends", party.current);
		assertTrue(party.changes.isEmpty());

		// Only the explicit Switch button moves them.
		assertTrue(sync.switchParty(true, GROUP, KEY));
		assertEquals(GroupKey.passphrase(GROUP, KEY), party.current);
	}

	@Test
	public void switchDoesNothingUnlessInAnotherParty()
	{
		FakeParty party = new FakeParty();
		GroupSync sync = new GroupSync(party);
		assertFalse(sync.switchParty(true, GROUP, KEY));
		assertTrue(party.changes.isEmpty());
	}

	@Test
	public void leavesOnlyThePartyItJoined()
	{
		FakeParty party = new FakeParty();
		GroupSync sync = new GroupSync(party);
		assertFalse("never joined: nothing to leave", sync.leaveOwn());

		party.current = "raids-with-friends";
		assertFalse(sync.leaveOwn());
		assertEquals("raids-with-friends", party.current);

		party.current = null;
		sync.join(true, GROUP, KEY);
		assertTrue(sync.inOwnParty());
		assertTrue(sync.leaveOwn());
		assertNull(party.current);
	}

	@Test
	public void ifThePlayerMovedToAnotherPartyShutdownLeavesThemThere()
	{
		FakeParty party = new FakeParty();
		GroupSync sync = new GroupSync(party);
		sync.join(true, GROUP, KEY);
		// They joined a raid party from the Party panel afterwards.
		party.current = "raids-with-friends";
		assertFalse(sync.leaveOwn());
		assertEquals("raids-with-friends", party.current);
	}

	@Test
	public void aNewKeyRejoinsOnTheClickFromOurOwnOldParty()
	{
		FakeParty party = new FakeParty();
		GroupSync sync = new GroupSync(party);
		sync.join(true, GROUP, KEY);
		String newKey = "ffeeddccbbaa99887766554433221100";
		assertEquals(GroupSync.State.STALE, sync.state(true, GROUP, newKey));
		assertTrue(sync.join(true, GROUP, newKey));
		assertEquals(GroupKey.passphrase(GROUP, newKey), party.current);
	}

	@Test
	public void theKeyIsNeverLogged()
	{
		Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		root.addAppender(appender);
		try
		{
			FakeParty party = new FakeParty();
			GroupSync sync = new GroupSync(party);
			sync.state(true, GROUP, KEY);
			sync.join(true, GROUP, KEY);
			sync.leaveOwn();
			party.current = "x";
			sync.switchParty(true, GROUP, KEY);
			GroupKey.keyFrom(net.runelite.api.ChatMessageType.CLAN_GIM_CHAT, GroupKey.shareLine(KEY));
			GroupKey.passphrase(GROUP, KEY);
		}
		finally
		{
			root.detachAppender(appender);
		}
		for (ILoggingEvent e : appender.list)
		{
			assertFalse(e.getFormattedMessage(), e.getFormattedMessage().contains(KEY));
		}
	}
}
