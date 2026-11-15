package com.vinnyvangogh.groupironman;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Board 2026-11-15: an account not in the group the client is set up for gets nothing done for it. */
public class GroupBindingTest
{
	private static final GroupMembers OURS = GroupMembers.of("Iron Crew", Arrays.asList("Vinny", "Bea", "Cal"));
	private static final GroupMembers THEIRS = GroupMembers.of("Wreckers", Arrays.asList("Ship Wrek", "Mate"));
	private static final List<String> HEARD = Arrays.asList("Vinny", "Bea", "Cal");

	@Test
	public void anAccountInAnotherGroupIsNotAMember()
	{
		GroupBinding.Result r = GroupBinding.check("iron crew", THEIRS, "Ship Wrek", "", HEARD);
		assertEquals(GroupBinding.Verdict.NOT_MEMBER, r.verdict);
		assertFalse(r.member());
		assertNull(r.bind);
	}

	@Test
	public void anAccountWithNoGroupWaitsForeverAndIsNeverAMember()
	{
		GroupBinding.Result r = GroupBinding.check("iron crew", GroupMembers.UNKNOWN, "Ship Wrek", "", HEARD);
		assertEquals(GroupBinding.Verdict.WAIT, r.verdict);
		assertFalse(r.member());
	}

	@Test
	public void aMemberOfTheBoundGroupIsAMember()
	{
		GroupBinding.Result r = GroupBinding.check("Iron Crew", OURS, "bea", "", HEARD);
		assertTrue(r.member());
		assertNull(r.bind);
	}

	@Test
	public void nameNotOnItsOwnRosterIsNotAMember()
	{
		assertFalse(GroupBinding.check("iron crew", OURS, "Ship Wrek", "", HEARD).member());
	}

	@Test
	public void noNameYetWaits()
	{
		assertEquals(GroupBinding.Verdict.WAIT, GroupBinding.check("iron crew", OURS, "", "", HEARD).verdict);
	}

	@Test
	public void firstLoginOnAFreshClientBindsItsGroup()
	{
		GroupBinding.Result r = GroupBinding.check("", OURS, "Vinny", "", Collections.emptyList());
		assertTrue(r.member());
		assertEquals("iron crew", r.bind);
	}

	@Test
	public void savedRolesDecideTheFirstBindingNotTheAccountLoggedIn()
	{
		// Upgrade: the first login is the other group's account; the signed roles say which group is ours.
		GroupBinding.Result r = GroupBinding.check("", THEIRS, "Ship Wrek", "Iron Crew", HEARD);
		assertFalse(r.member());
		assertEquals("iron crew", r.bind);
	}

	@Test
	public void heardMembersDecideTheFirstBindingWithoutRoles()
	{
		// The cache already holds the other account's own card from before the fix: only it overlaps.
		List<String> polluted = Arrays.asList("Vinny", "Bea", "Ship Wrek");
		GroupBinding.Result wrong = GroupBinding.check("", THEIRS, "Ship Wrek", "", polluted);
		assertFalse(wrong.member());
		assertNull(wrong.bind);

		GroupBinding.Result right = GroupBinding.check("", OURS, "Cal", "", polluted);
		assertTrue(right.member());
		assertEquals("iron crew", right.bind);
	}

	@Test
	public void onlyOwnDataHeldBindsTheFirstGroup()
	{
		GroupBinding.Result r = GroupBinding.check("", OURS, "Vinny", "", Collections.singletonList("vinny"));
		assertTrue(r.member());
		assertEquals("iron crew", r.bind);
	}

	@Test
	public void switchingBackToAMemberIsAMemberAgain()
	{
		assertFalse(GroupBinding.check("iron crew", THEIRS, "Ship Wrek", "", HEARD).member());
		assertTrue(GroupBinding.check("iron crew", OURS, "Vinny", "", HEARD).member());
	}
}
