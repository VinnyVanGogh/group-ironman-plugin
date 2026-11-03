package com.vinnyvangogh.buildguard;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** A member who has a feature off sends nothing of it, not an empty payload. */
public class GroupOutboxTest
{
	private static final Gson GSON = new Gson();
	private static final GroupOutbox.Switches ALL_OFF = new GroupOutbox.Switches(false, false, false, false);
	private static final GroupOutbox.Switches ALL_ON = new GroupOutbox.Switches(true, true, true, true);

	private static Map<Integer, Integer> one()
	{
		Map<Integer, Integer> m = new TreeMap<>();
		m.put(385, 3);
		return m;
	}

	private static MemberStats stats()
	{
		Map<String, MemberStats.Level> skills = new LinkedHashMap<>();
		skills.put("Attack", new MemberStats.Level(60, 60));
		return new MemberStats(skills, 100, 100, 301, 12850);
	}

	@Test
	public void theDefaultIsEverythingOff()
	{
		assertFalse(ALL_OFF.any());
	}

	@Test
	public void optedOutSendsNothing()
	{
		assertTrue(GroupOutbox.bank(GSON, ALL_OFF, new BankSnapshot(1, one())).isEmpty());
		assertNull(GroupOutbox.storage(GSON, ALL_OFF, new StorageChange(1, "", one(), one(), one())));
		assertNull(GroupOutbox.stats(GSON, ALL_OFF, stats()));
		assertNull(GroupOutbox.carried(GSON, ALL_OFF, one(), one()));
	}

	@Test
	public void eachSwitchOnlyOpensItsOwnFeature()
	{
		GroupOutbox.Switches bankOnly = new GroupOutbox.Switches(true, false, false, false);
		assertEquals(1, GroupOutbox.bank(GSON, bankOnly, new BankSnapshot(1, one())).size());
		assertNull(GroupOutbox.storage(GSON, bankOnly, new StorageChange(1, "", one(), one(), one())));
		assertNull(GroupOutbox.stats(GSON, bankOnly, stats()));
		assertNull(GroupOutbox.carried(GSON, bankOnly, one(), one()));
	}

	@Test
	public void optedInSendsEachKind()
	{
		assertEquals(1, GroupOutbox.bank(GSON, ALL_ON, new BankSnapshot(1, one())).size());
		assertNotNull(GroupOutbox.storage(GSON, ALL_ON, new StorageChange(1, "", one(), one(), one())));
		assertNotNull(GroupOutbox.stats(GSON, ALL_ON, stats()));
		assertNotNull(GroupOutbox.carried(GSON, ALL_ON, one(), one()));
	}

	@Test
	public void aVisitThatChangedNothingIsNotSent()
	{
		Map<Integer, Integer> none = Collections.emptyMap();
		assertNull(GroupOutbox.storage(GSON, ALL_ON, new StorageChange(1, "", none, none, one())));
	}
}
