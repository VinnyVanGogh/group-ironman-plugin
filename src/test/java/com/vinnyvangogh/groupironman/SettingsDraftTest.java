package com.vinnyvangogh.groupironman;

import java.util.Map;
import java.util.TreeMap;
import org.junit.Before;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The Settings tab's draft: what survives a redraw and what replaces it
 * (Board 2026-11-13: the leader's Save sent the old values while another
 * member had the tab open, because any redraw threw the draft away).
 */
public class SettingsDraftTest
{
	private static final GroupIronmanConfig DEFAULTS = new GroupIronmanConfig()
	{
	};

	private GroupSigner.Identity alexId;
	private GroupSigner.Identity samId;
	private GroupGovernance alex;
	private GroupGovernance sam;
	private final SettingsDraft draft = new SettingsDraft();

	@Before
	public void setUp()
	{
		alexId = GroupSigner.Identity.create();
		samId = GroupSigner.Identity.create();
		alex = new GroupGovernance(alexId);
		sam = new GroupGovernance(samId);
		for (GroupGovernance g : new GroupGovernance[]{alex, sam})
		{
			g.group("GroupShip");
			g.receiveMember("Alex", alexId.publicKey(), MemberPrefs.NONE);
			g.receiveMember("Sam", samId.publicKey(), MemberPrefs.NONE);
		}
		alex.claim("Alex", NOW);
		sam.receiveRoles(alex.roles());
	}

	private static Map<String, Long> values(long levelMin)
	{
		Map<String, Long> v = GroupSettingsSchema.seed(DEFAULTS);
		v.put("eventLevelMin", levelMin);
		return v;
	}

	private static Map<String, Long> current(GroupGovernance g)
	{
		GroupSettingsSchema.Resolved r = new GroupSettingsSchema.Resolved(DEFAULTS, g.settings());
		Map<String, Long> out = new TreeMap<>();
		for (GroupSettingsSchema.Setting s : GroupSettingsSchema.ALL)
		{
			out.put(s.key, r.value(s.key));
		}
		return out;
	}

	/** One redraw of Alex's tab, with whatever Alex's client holds now. */
	private void redrawAlex()
	{
		draft.update(alex.myRole("Alex"), alex.roles(), alex.settings(), current(alex));
	}

	@Test
	public void anotherMembersPrefsOrPresenceDoNotTouchTheDraft()
	{
		alex.save("Alex", values(50), NOW + 1);
		redrawAlex();
		draft.put("eventLevelMin", 70);

		// Sam opens the tab, toggles an opt-out and comes online: his announce arrives, the panel redraws.
		MemberPrefs out = new MemberPrefs(true, java.util.EnumSet.noneOf(GroupEvent.Type.class),
			java.util.EnumSet.noneOf(GroupEvent.Type.class));
		alex.receiveMember("Sam", samId.publicKey(), out);
		redrawAlex();
		redrawAlex();

		assertEquals(70L, (long) draft.values().get("eventLevelMin"));
		assertNull(draft.notice());
		GroupSettings saved = alex.save("Alex", draft.values(), NOW + 2);
		assertEquals(70L, (long) saved.values.get("eventLevelMin"));
	}

	@Test
	public void anUneditedDraftFollowsTheGroup()
	{
		alex.save("Alex", values(50), NOW + 1);
		redrawAlex();
		alex.save("Alex", values(60), NOW + 2);
		redrawAlex();
		assertEquals(60L, (long) draft.values().get("eventLevelMin"));
		assertFalse(draft.edited());
		assertNull(draft.notice());
	}

	@Test
	public void aNewVersionUnderADraftKeepsItAndSaysSo()
	{
		alex.setEditor("Alex", "Sam", true, NOW + 1);
		sam.receiveRoles(alex.roles());
		alex.save("Alex", values(50), NOW + 2);
		sam.receiveSettings(alex.settings());
		redrawAlex();
		draft.put("eventLevelMin", 70);

		alex.receiveSettings(sam.save("Sam", values(80), NOW + 3));
		redrawAlex();

		assertEquals("the draft is kept", 70L, (long) draft.values().get("eventLevelMin"));
		assertEquals("Your group's settings changed (version 2 by Sam). Save will replace them, or Discard to"
			+ " see theirs.", draft.notice());

		draft.discard(current(alex));
		assertEquals(80L, (long) draft.values().get("eventLevelMin"));
		assertNull(draft.notice());
	}

	@Test
	public void savingClearsTheNoticeAndOwnSaveDoesNotRaiseOne()
	{
		alex.save("Alex", values(50), NOW + 1);
		redrawAlex();
		draft.put("eventLevelMin", 70);
		assertTrue(draft.edited());
		alex.save("Alex", draft.values(), NOW + 2);
		// The redraw from the save can come before the tab hears the result.
		redrawAlex();
		assertNull("our own save is not someone else's change", draft.notice());
		draft.saved();
		redrawAlex();
		assertFalse(draft.edited());
		assertNull(draft.notice());
	}

	@Test
	public void losingTheRoleResetsTheDraft()
	{
		alex.setEditor("Alex", "Sam", true, NOW + 1);
		sam.receiveRoles(alex.roles());
		draft.update(sam.myRole("Sam"), sam.roles(), sam.settings(), current(sam));
		draft.put("eventLevelMin", 70);

		alex.setEditor("Alex", "Sam", false, NOW + 2);
		sam.receiveRoles(alex.roles());
		draft.update(sam.myRole("Sam"), sam.roles(), sam.settings(), current(sam));

		assertEquals(GroupGovernance.Role.MEMBER, sam.myRole("Sam"));
		assertFalse(draft.edited());
		assertEquals(current(sam).get("eventLevelMin"), draft.values().get("eventLevelMin"));
	}
}
