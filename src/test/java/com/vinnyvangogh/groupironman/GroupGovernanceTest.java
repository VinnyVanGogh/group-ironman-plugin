package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Before;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Roles and group settings between simulated members. Every document crosses
 * the "wire" through {@link GovernanceCodec}, as it does over the party.
 */
public class GroupGovernanceTest
{
	private static final String GROUP = "GroupShip";
	private static final GroupIronmanConfig DEFAULTS = new GroupIronmanConfig()
	{
	};
	private final Gson gson = new Gson();

	private GroupSigner.Identity alexId;
	private GroupSigner.Identity samId;
	private GroupSigner.Identity kimId;
	private GroupGovernance alex;
	private GroupGovernance sam;
	private GroupGovernance kim;

	@Before
	public void setUp()
	{
		alexId = GroupSigner.Identity.create();
		samId = GroupSigner.Identity.create();
		kimId = GroupSigner.Identity.create();
		alex = member(alexId);
		sam = member(samId);
		kim = member(kimId);
		// Each member's announce reaches the others, as on joining the party.
		for (GroupGovernance g : new GroupGovernance[]{alex, sam, kim})
		{
			g.receiveMember("Alex", alexId.publicKey(), MemberPrefs.NONE);
			g.receiveMember("Sam", samId.publicKey(), MemberPrefs.NONE);
			g.receiveMember("Kim", kimId.publicKey(), MemberPrefs.NONE);
		}
	}

	private static GroupGovernance member(GroupSigner.Identity id)
	{
		GroupGovernance g = new GroupGovernance(id);
		g.group(GROUP);
		return g;
	}

	private GroupRoles wire(GroupRoles roles)
	{
		return GovernanceCodec.decode(gson, GovernanceCodec.encode(gson, roles)).roles;
	}

	private GroupSettings wire(GroupSettings settings)
	{
		return GovernanceCodec.decode(gson, GovernanceCodec.encode(gson, settings)).settings;
	}

	private static Map<String, Long> values(long levelMin)
	{
		Map<String, Long> v = GroupSettingsSchema.seed(DEFAULTS);
		v.put("eventLevelMin", levelMin);
		return v;
	}

	// ---- Roles ----

	@Test
	public void theClaimantIsLeaderOnEveryClient()
	{
		GroupRoles roles = alex.claim("Alex", NOW);
		assertNotNull(roles);
		assertEquals(GroupGovernance.Role.LEADER, alex.myRole("Alex"));
		assertEquals(GroupGovernance.Verdict.ACCEPTED, sam.receiveRoles(wire(roles)));
		assertEquals(GroupGovernance.Role.LEADER, sam.role("Alex"));
		assertEquals(GroupGovernance.Role.MEMBER, sam.myRole("Sam"));
	}

	@Test
	public void aClaimedGroupCannotBeClaimedAgain()
	{
		sam.receiveRoles(wire(alex.claim("Alex", NOW)));
		assertNull("Sam's client refuses a second claim", sam.claim("Sam", NOW + 1));
		assertNull("and so does Alex's", alex.claim("Alex", NOW + 1));
	}

	@Test
	public void aMemberCannotSelfPromote()
	{
		sam.receiveRoles(wire(alex.claim("Alex", NOW)));
		kim.receiveRoles(wire(alex.roles()));

		// Sam signs a claim of his own with his own key: the pinned leader key does not match.
		GroupGovernance rogue = member(samId);
		rogue.receiveMember("Sam", samId.publicKey(), MemberPrefs.NONE);
		GroupRoles forged = rogue.claim("Sam", NOW + 5);
		assertEquals(GroupGovernance.Verdict.REFUSED, kim.receiveRoles(wire(forged)));
		assertEquals(GroupGovernance.Role.LEADER, kim.role("Alex"));

		// Sam edits Alex's roles to add himself as editor, without Alex's key to re-sign.
		JsonObject json = GovernanceCodec.json(alex.roles());
		json.getAsJsonObject("ed").addProperty("Sam", samId.publicKey());
		json.addProperty("ver", 9);
		GroupRoles tampered = GovernanceCodec.decode(gson, json.toString()).roles;
		assertEquals(GroupGovernance.Verdict.REFUSED, kim.receiveRoles(tampered));
		assertEquals(GroupGovernance.Role.MEMBER, kim.role("Sam"));

		// And his own client will not let him save.
		assertNull(sam.save("Sam", values(70), NOW + 6));
	}

	@Test
	public void aClaimNamingSomeoneElseWithTheWrongKeyIsRefused()
	{
		// Sam signs "Alex is leader" with Sam's key, hoping members pin a key he holds.
		GroupGovernance rogue = member(samId);
		rogue.receiveMember("Alex", samId.publicKey(), MemberPrefs.NONE);
		GroupRoles forged = rogue.claim("Alex", NOW);
		assertEquals(GroupGovernance.Verdict.REFUSED, kim.receiveRoles(wire(forged)));
		assertNull(kim.roles());
	}

	@Test
	public void aClaimWaitsUntilTheClaimantsKeyIsKnown()
	{
		GroupGovernance newcomer = member(GroupSigner.Identity.create());
		assertEquals(GroupGovernance.Verdict.PENDING, newcomer.receiveRoles(wire(alex.claim("Alex", NOW))));
		assertNull(newcomer.roles());
		newcomer.receiveMember("Alex", alexId.publicKey(), MemberPrefs.NONE);
		assertNotNull("applied once Alex's own announce arrives", newcomer.roles());
	}

	@Test
	public void rolesForAnotherGroupAreRefused()
	{
		GroupGovernance other = member(GroupSigner.Identity.create());
		other.group("Other group");
		other.receiveMember("Alex", alexId.publicKey(), MemberPrefs.NONE);
		assertEquals(GroupGovernance.Verdict.REFUSED, other.receiveRoles(wire(alex.claim("Alex", NOW))));
	}

	@Test
	public void onlyTheLeaderGrantsAndRevokesEditors()
	{
		sam.receiveRoles(wire(alex.claim("Alex", NOW)));
		assertNull("Sam is not leader", sam.setEditor("Sam", "Sam", true, NOW + 1));
		GroupRoles granted = alex.setEditor("Alex", "Sam", true, NOW + 1);
		assertNotNull(granted);
		assertEquals(GroupGovernance.Verdict.ACCEPTED, sam.receiveRoles(wire(granted)));
		assertEquals(GroupGovernance.Role.EDITOR, sam.myRole("Sam"));
		assertEquals(GroupGovernance.Verdict.STALE, sam.receiveRoles(wire(granted)));

		GroupRoles revoked = alex.setEditor("Alex", "Sam", false, NOW + 2);
		assertEquals(GroupGovernance.Verdict.ACCEPTED, sam.receiveRoles(wire(revoked)));
		assertEquals(GroupGovernance.Role.MEMBER, sam.myRole("Sam"));
	}

	@Test
	public void anEditorWhoseKeyIsUnknownCannotBeGranted()
	{
		alex.claim("Alex", NOW);
		assertNull(alex.setEditor("Alex", "Stranger", true, NOW + 1));
	}

	// ---- Settings ----

	@Test
	public void aNonEditorChangeIsRefused()
	{
		GroupRoles roles = alex.claim("Alex", NOW);
		sam.receiveRoles(wire(roles));
		kim.receiveRoles(wire(roles));
		assertNull("refused on Sam's own client", sam.save("Sam", values(70), NOW + 1));

		// A document Sam signs himself (a modified client) is refused by everyone else.
		GroupSettings forged = GroupSettings.next(null, values(70), "Sam", samId, NOW + 1);
		assertEquals(GroupGovernance.Verdict.REFUSED, kim.receiveSettings(wire(forged)));
		assertNull(kim.settings());
	}

	@Test
	public void aChangeSyncsToASecondMember()
	{
		GroupRoles roles = alex.claim("Alex", NOW);
		GroupSettings saved = alex.save("Alex", values(70), NOW + 1);
		assertNotNull(saved);
		assertEquals(1, saved.version);

		assertEquals(GroupGovernance.Verdict.ACCEPTED, sam.receiveRoles(wire(roles)));
		assertEquals(GroupGovernance.Verdict.ACCEPTED, sam.receiveSettings(wire(saved)));
		assertEquals(70, new GroupSettingsSchema.Resolved(DEFAULTS, sam.settings()).value("eventLevelMin"));
		assertEquals("Alex", sam.settings().by);
		assertEquals(NOW + 1, sam.settings().at);
	}

	@Test
	public void historySaysWhoChangedWhatAndWhen()
	{
		alex.claim("Alex", NOW);
		alex.save("Alex", values(70), NOW + 1);
		GroupSettings second = alex.save("Alex", values(80), NOW + 2);
		assertEquals(2, second.version);
		GroupSettings.Change last = second.history.get(0);
		assertEquals("Alex", last.by);
		assertEquals(NOW + 2, last.at);
		assertEquals("eventLevelMin", last.key);
		assertEquals(70, last.from);
		assertEquals(80, last.to);
	}

	@Test
	public void savingNothingNewKeepsTheVersion()
	{
		alex.claim("Alex", NOW);
		GroupSettings first = alex.save("Alex", values(70), NOW + 1);
		assertSame(first, alex.save("Alex", values(70), NOW + 2));
	}

	@Test
	public void anOfflineMemberPicksUpTheLatestAndIgnoresOlder()
	{
		GroupRoles roles = alex.claim("Alex", NOW);
		GroupSettings v1 = alex.save("Alex", values(70), NOW + 1);
		GroupSettings v2 = alex.save("Alex", values(80), NOW + 2);
		// Sam was offline for both; on joining, a member resends roles then the latest settings.
		sam.receiveRoles(wire(roles));
		assertEquals(GroupGovernance.Verdict.ACCEPTED, sam.receiveSettings(wire(v2)));
		assertEquals(GroupGovernance.Verdict.STALE, sam.receiveSettings(wire(v1)));
		assertEquals(2, sam.settings().version);
	}

	@Test
	public void settingsBeforeRolesWaitForThem()
	{
		GroupRoles roles = alex.claim("Alex", NOW);
		GroupSettings v1 = alex.save("Alex", values(70), NOW + 1);
		assertEquals(GroupGovernance.Verdict.PENDING, sam.receiveSettings(wire(v1)));
		assertNull(sam.settings());
		sam.receiveRoles(wire(roles));
		assertNotNull(sam.settings());
		assertEquals(1, sam.settings().version);
	}

	@Test
	public void aTamperedSettingsDocumentIsRefused()
	{
		GroupRoles roles = alex.claim("Alex", NOW);
		sam.receiveRoles(wire(roles));
		JsonObject json = GovernanceCodec.json(alex.save("Alex", values(70), NOW + 1));
		json.getAsJsonObject("val").addProperty("eventLevelMin", 1);
		assertEquals(GroupGovernance.Verdict.REFUSED,
			sam.receiveSettings(GovernanceCodec.decode(gson, json.toString()).settings));
	}

	@Test
	public void aVersionConflictConvergesOnOneWinnerInAnyOrder()
	{
		GroupRoles roles = alex.claim("Alex", NOW);
		roles = alex.setEditor("Alex", "Sam", true, NOW + 1);
		roles = alex.setEditor("Alex", "Kim", true, NOW + 2);
		GroupSettings base = alex.save("Alex", values(60), NOW + 3);
		for (GroupGovernance g : new GroupGovernance[]{sam, kim})
		{
			g.receiveRoles(wire(roles));
			g.receiveSettings(wire(base));
		}

		// Sam and Kim both edit version 1 at the same moment: two different version 2s.
		GroupSettings bySam = sam.save("Sam", values(70), NOW + 10);
		GroupSettings byKim = kim.save("Kim", values(80), NOW + 10);
		assertEquals(2, bySam.version);
		assertEquals(2, byKim.version);

		GroupGovernance lee = member(GroupSigner.Identity.create());
		GroupGovernance max = member(GroupSigner.Identity.create());
		for (GroupGovernance g : new GroupGovernance[]{lee, max})
		{
			g.receiveMember("Alex", alexId.publicKey(), MemberPrefs.NONE);
			g.receiveRoles(wire(roles));
		}
		lee.receiveSettings(wire(bySam));
		lee.receiveSettings(wire(byKim));
		max.receiveSettings(wire(byKim));
		max.receiveSettings(wire(bySam));
		alex.receiveSettings(wire(bySam));
		alex.receiveSettings(wire(byKim));
		sam.receiveSettings(wire(byKim));
		kim.receiveSettings(wire(bySam));

		String winner = lee.settings().signature;
		for (GroupGovernance g : new GroupGovernance[]{max, alex, sam, kim})
		{
			assertEquals("every client keeps the same version 2", winner, g.settings().signature);
		}
		// The next save builds on the winner, so nothing is lost silently: it is in the history.
		GroupSettings next = alex.save("Alex", values(90), NOW + 20);
		assertEquals(3, next.version);
	}

	@Test
	public void revokingAnEditorReSignsTheirSettingsSoLateJoinersAgree()
	{
		GroupRoles roles = alex.claim("Alex", NOW);
		roles = alex.setEditor("Alex", "Sam", true, NOW + 1);
		sam.receiveRoles(wire(roles));
		GroupSettings bySam = sam.save("Sam", values(70), NOW + 2);
		alex.receiveSettings(wire(bySam));

		GroupRoles revoked = alex.setEditor("Alex", "Sam", false, NOW + 3);
		GroupSettings resigned = alex.settings();
		assertEquals("Alex", resigned.by);
		assertEquals(bySam.version + 1, resigned.version);
		assertEquals(bySam.values, resigned.values);

		GroupGovernance late = member(GroupSigner.Identity.create());
		late.receiveMember("Alex", alexId.publicKey(), MemberPrefs.NONE);
		late.receiveRoles(wire(revoked));
		assertEquals(GroupGovernance.Verdict.REFUSED, late.receiveSettings(wire(bySam)));
		assertEquals(GroupGovernance.Verdict.ACCEPTED, late.receiveSettings(wire(resigned)));
		assertEquals(70, new GroupSettingsSchema.Resolved(DEFAULTS, late.settings()).value("eventLevelMin"));
	}

	@Test
	public void forgettingTheLeaderAllowsANewClaimOnThisClientOnly()
	{
		sam.receiveRoles(wire(alex.claim("Alex", NOW)));
		sam.forgetLeader();
		assertNull(sam.roles());
		assertTrue(sam.unclaimed());
		assertFalse(alex.unclaimed());
	}

	@Test
	public void membersPreferencesAreKeptForTheGrid()
	{
		MemberPrefs prefs = new MemberPrefs(false, java.util.EnumSet.of(GroupEvent.Type.DEATH),
			java.util.EnumSet.noneOf(GroupEvent.Type.class));
		alex.receiveMember("Sam", samId.publicKey(), prefs);
		Map<String, MemberPrefs> seen = new TreeMap<>(alex.prefs());
		assertFalse(seen.get("Sam").posts(GroupEvent.Type.DEATH));
		assertTrue(seen.get("Sam").posts(GroupEvent.Type.PET));
	}
}
