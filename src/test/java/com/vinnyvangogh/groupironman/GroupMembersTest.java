package com.vinnyvangogh.groupironman;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GroupMembersTest
{
	private static final GroupMembers GROUP = GroupMembers.of("GroupShip",
		Arrays.asList("Iron Alex", "Sam Two", "<img=3>Kim"));

	@Test
	public void rosterMembersAreAcceptedWhateverTheSpacingOrCase()
	{
		assertEquals(GroupMembers.Verdict.ACCEPT, GROUP.check("iron alex"));
		assertEquals(GroupMembers.Verdict.ACCEPT, GROUP.check("Iron_Alex"));
		assertEquals(GroupMembers.Verdict.ACCEPT, GROUP.check("Sam Two"));
		assertEquals(GroupMembers.Verdict.ACCEPT, GROUP.check("Kim"));
	}

	@Test
	public void aStrangerIsFlaggedAndIgnored()
	{
		assertEquals(GroupMembers.Verdict.STRANGER, GROUP.check("Mallory"));
		assertFalse(GROUP.allows("Mallory"));
	}

	@Test
	public void beforeTheRosterArrivesNobodyIsAcceptedOrFlagged()
	{
		assertEquals(GroupMembers.Verdict.WAIT, GroupMembers.UNKNOWN.check("Iron Alex"));
		assertEquals(GroupMembers.Verdict.WAIT, GroupMembers.of("GroupShip", Collections.emptyList()).check("x"));
	}

	@Test
	public void namesAreShownAsTheGameSpellsThem()
	{
		assertEquals("GroupShip", GROUP.group());
		assertTrue(GROUP.names().contains("Iron Alex"));
		assertTrue(GROUP.names().contains("Sam Two"));
		assertTrue(GROUP.names().contains("Kim"));
		assertEquals(3, GROUP.names().size());
	}
}
