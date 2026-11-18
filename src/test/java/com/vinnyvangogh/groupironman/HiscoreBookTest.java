package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.Skill;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Hiscore levels for members without the plugin: the 6 h throttle, backoff, precedence and what the card shows. */
public class HiscoreBookTest
{
	private static final long NOW = TimeUnit.DAYS.toMillis(100);
	private static final long MIN = TimeUnit.MINUTES.toMillis(1);
	private static final long HOUR = TimeUnit.HOURS.toMillis(1);
	private static final RegionNames REGIONS = RegionNames.parse("");
	private static final FakeItems ITEMS = new FakeItems();

	private static Map<String, Integer> levels()
	{
		Map<String, Integer> out = new LinkedHashMap<>();
		out.put("Attack", 60);
		out.put("Hitpoints", 70);
		out.put("Prayer", 52);
		return out;
	}

	/** Bob is in the Group channel on W330; Alex is on the roster but offline. */
	private static GamePresence bobOnline()
	{
		GroupMembers roster = GroupMembers.of("Ship", Arrays.asList("Alex", "Bob", "Me"));
		Map<String, Integer> channel = new HashMap<>();
		channel.put("Bob", 330);
		channel.put("Me", 330);
		return GamePresence.read(roster, channel, null);
	}

	private static PanelModel.MemberCard card(GroupRoster roster, GamePresence game, String name)
	{
		List<PanelModel.MemberCard> cards = PanelModel.members(roster, Arrays.asList("Alex", "Bob", "Me"), REGIONS,
			ITEMS, "Me", game, NOW);
		for (PanelModel.MemberCard c : cards)
		{
			if (c.name.equals(name))
			{
				return c;
			}
		}
		throw new AssertionError("no card for " + name);
	}

	// ---- The throttle, with a fake clock ----

	@Test
	public void looksUpOnlyWhenAskedAndAtMostOnceEverySixHours()
	{
		HiscoreBook book = new HiscoreBook();
		assertFalse("no login or coming online: nothing starts", book.begin("Bob", NOW, false));
		assertTrue(book.begin("Bob", NOW, true));
		assertFalse("already in flight", book.begin("Bob", NOW, true));
		book.succeeded("Bob", levels(), NOW);

		assertFalse(book.begin("Bob", NOW + MIN, true));
		assertFalse("came online again 5h59m later", book.begin("bob", NOW + 6 * HOUR - MIN, true));
		assertTrue("6 h later, asked again", book.begin("Bob", NOW + 6 * HOUR, true));
	}

	@Test
	public void sixHoursPassingAloneStartsNothing()
	{
		HiscoreBook book = new HiscoreBook();
		book.begin("Bob", NOW, true);
		book.succeeded("Bob", levels(), NOW);
		assertFalse(book.begin("Bob", NOW + 7 * HOUR, false));
	}

	@Test
	public void namesMatchAsTheGameMatchesThem()
	{
		HiscoreBook book = new HiscoreBook();
		book.begin("Iron Bob", NOW, true);
		book.succeeded("Iron Bob", levels(), NOW);
		assertNotNull(book.entry("iron_bob"));
		assertFalse(book.begin("IRON-BOB", NOW + MIN, true));
	}

	@Test
	public void aClockThatWentBackDoesNotBlockLookupsForGood()
	{
		HiscoreBook book = new HiscoreBook();
		book.restore("Bob", NOW + 30 * 24 * HOUR, levels());
		assertTrue(book.begin("Bob", NOW, true));
	}

	// ---- Failures back off, never storm ----

	@Test
	public void aFailureRetriesOnItsOwnAfterABackoffThatDoublesUpToSixHours()
	{
		assertEquals(15 * MIN, HiscoreBook.backoff(1));
		assertEquals(30 * MIN, HiscoreBook.backoff(2));
		assertEquals(60 * MIN, HiscoreBook.backoff(3));
		assertEquals(6 * HOUR, HiscoreBook.backoff(6));
		assertEquals(6 * HOUR, HiscoreBook.backoff(1000));

		HiscoreBook book = new HiscoreBook();
		assertTrue(book.begin("Bob", NOW, true));
		book.failed("Bob", NOW);
		assertTrue(book.unavailable("Bob"));
		assertFalse("asking again does not skip the backoff", book.begin("Bob", NOW + 14 * MIN, true));
		assertTrue("retried without being asked", book.begin("Bob", NOW + 15 * MIN, false));
		book.failed("Bob", NOW + 15 * MIN);
		assertFalse(book.begin("Bob", NOW + 44 * MIN, false));
		assertTrue(book.begin("Bob", NOW + 45 * MIN, false));
		book.succeeded("Bob", levels(), NOW + 45 * MIN);
		assertFalse(book.unavailable("Bob"));
		assertFalse("back on the 6 h throttle", book.begin("Bob", NOW + 2 * HOUR, false));
	}

	@Test
	public void aFailedLookupShowsHiscoresUnavailableAndNoNumbers()
	{
		GroupRoster roster = new GroupRoster();
		roster.hiscores().begin("Bob", NOW, true);
		roster.hiscores().failed("Bob", NOW);

		PanelModel.MemberCard bob = card(roster, bobOnline(), "Bob");
		assertEquals("Hiscores unavailable", bob.source);
		assertTrue(bob.bars.isEmpty());
		assertTrue(bob.skills.isEmpty());
	}

	@Test
	public void aFailedRefreshKeepsTheLevelsAlreadyRead()
	{
		GroupRoster roster = new GroupRoster();
		roster.hiscores().restore("Bob", NOW - 7 * HOUR, levels());
		roster.hiscores().begin("Bob", NOW, true);
		roster.hiscores().failed("Bob", NOW);

		PanelModel.MemberCard bob = card(roster, bobOnline(), "Bob");
		assertEquals("From hiscores, 7h ago (hiscores unavailable)", bob.source);
		assertEquals(3, bob.skills.size());
	}

	// ---- What the card shows ----

	@Test
	public void anOnlineMemberWithoutThePluginShowsHiscoreLevelsAndDimmedAssumedRunAndSpec()
	{
		GroupRoster roster = new GroupRoster();
		roster.hiscores().restore("Bob", NOW - 2 * HOUR, levels());

		PanelModel.MemberCard bob = card(roster, bobOnline(), "Bob");
		assertTrue(bob.online);
		assertEquals("From hiscores, 2h ago", bob.source);
		assertEquals(4, bob.bars.size());

		PanelModel.Bar hp = bob.bars.get(0);
		assertEquals("70/70", hp.text());
		assertFalse(hp.assumed);
		assertEquals("HP 70/70 (from hiscores)", hp.tooltip());
		assertEquals("52/52", bob.bars.get(1).text());

		PanelModel.Bar run = bob.bars.get(2);
		PanelModel.Bar spec = bob.bars.get(3);
		assertEquals("Run", run.label);
		assertEquals("100/100", run.text());
		assertTrue(run.assumed);
		assertEquals("Run 100/100 (assumed, not shared)", run.tooltip());
		assertEquals("Spec", spec.label);
		assertTrue(spec.assumed);
		assertEquals("Spec 100/100 (assumed, not shared)", spec.tooltip());

		assertEquals("Attack", bob.skills.get(0).skill);
		assertEquals("60", bob.skills.get(0).text());
	}

	@Test
	public void anOfflineOrNeverSeenMemberShowsLevelsButNoOrbs()
	{
		GroupRoster roster = new GroupRoster();
		roster.hiscores().restore("Alex", NOW - HOUR, levels());

		PanelModel.MemberCard alex = card(roster, bobOnline(), "Alex");
		assertFalse(alex.online);
		assertTrue(alex.bars.isEmpty());
		assertEquals(3, alex.skills.size());
		assertEquals("From hiscores, 1h ago", alex.source);
	}

	@Test
	public void sharedPluginDataAlwaysWinsOverHiscores()
	{
		GroupRoster roster = new GroupRoster();
		Map<String, MemberStats.Level> shared = new LinkedHashMap<>();
		shared.put("Attack", new MemberStats.Level(99, 99));
		shared.put("Hitpoints", new MemberStats.Level(99, 80));
		roster.putStats("Bob", new MemberStats(shared, 40, 25, 330, 0));
		roster.heard("Bob", NOW - MIN);
		roster.hiscores().restore("Bob", NOW - HOUR, levels());

		PanelModel.MemberCard bob = card(roster, bobOnline(), "Bob");
		assertNull("no hiscore line when the member shares", bob.source);
		assertEquals("80/99", bob.bars.get(0).text());
		assertEquals("40/100", bob.bars.get(1).text());
		assertFalse(bob.bars.get(1).assumed);
		assertNull(bob.bars.get(1).note);
		assertEquals("99", bob.skills.get(0).text());
	}

	@Test
	public void aMemberWhoNeverSharedItemsHasNoItemsNotMadeUpOnes()
	{
		GroupRoster roster = new GroupRoster();
		roster.hiscores().restore("Bob", NOW - HOUR, levels());
		assertTrue(card(roster, bobOnline(), "Bob").items.isEmpty());
		assertTrue(card(roster, bobOnline(), "Alex").items.isEmpty());
	}

	@Test
	public void aMemberWhoSharedItemsBeforeKeepsThemWithHiscoreLevels()
	{
		GroupRoster roster = new GroupRoster();
		Map<Integer, Integer> inv = new LinkedHashMap<>();
		inv.put(FakeItems.SHARK, 5);
		roster.putCarried("Alex", new GroupCodec.Carried(inv, Collections.emptyMap()));
		roster.hiscores().restore("Alex", NOW - HOUR, levels());

		PanelModel.MemberCard alex = card(roster, bobOnline(), "Alex");
		assertEquals("Inventory", alex.items.get(0).title);
		assertEquals(3, alex.skills.size());
	}

	@Test
	public void nothingLookedUpShowsNoSourceLine()
	{
		assertNull(card(new GroupRoster(), bobOnline(), "Bob").source);
	}

	// ---- Reading a hiscore result, and the cache ----

	@Test
	public void readsSkillLevelsNamedAsSharedStatsAndSkipsUnrankedAndActivities()
	{
		Map<HiscoreSkill, Skill> skills = new EnumMap<>(HiscoreSkill.class);
		skills.put(HiscoreSkill.OVERALL, new Skill(1, 1500, 1L));
		skills.put(HiscoreSkill.ATTACK, new Skill(10, 60, 273_742L));
		skills.put(HiscoreSkill.RUNECRAFT, new Skill(-1, -1, -1L));
		skills.put(HiscoreSkill.HITPOINTS, new Skill(10, 70, 737_627L));
		skills.put(HiscoreSkill.ZULRAH, new Skill(5, 120, -1L));
		Map<String, Integer> out = HiscoreBook.levels(new HiscoreResult("Bob", skills));

		assertEquals(Integer.valueOf(60), out.get("Attack"));
		assertEquals(Integer.valueOf(70), out.get("Hitpoints"));
		assertEquals(2, out.size());
		assertTrue(HiscoreBook.levels(null).isEmpty());
	}

	@Test
	public void lookupsSurviveARestartWithWhenTheyWereRead()
	{
		GroupRoster before = new GroupRoster();
		before.hiscores().restore("Bob", NOW - 2 * HOUR, levels());
		before.hiscores().begin("Alex", NOW, true);
		before.hiscores().failed("Alex", NOW);
		Gson gson = new Gson();

		GroupRoster after = new GroupRoster();
		assertTrue(GroupCache.read(gson, GroupCache.write(gson, before), after));
		HiscoreBook.Entry bob = after.hiscores().entry("Bob");
		assertEquals(NOW - 2 * HOUR, bob.at);
		assertEquals(levels(), bob.levels);
		assertFalse("failures are not kept", after.hiscores().unavailable("Alex"));
		assertFalse("and still throttled after the restart", after.hiscores().begin("Bob", NOW, true));
	}

	@Test
	public void aHostileCacheEntryIsCleaned()
	{
		Gson gson = new Gson();
		String json = "{\"v\":1,\"hiscores\":{\"bob\":{\"ts\":5,\"l\":{\"Attack\":99,\"Magic\":9000,\"Prayer\":-3,"
			+ "\"Mining\":\"x\"}},\"alex\":{\"l\":{}},\"cat\":7}}";
		GroupRoster roster = new GroupRoster();
		assertTrue(GroupCache.read(gson, json, roster));
		assertEquals(Collections.singletonMap("Attack", 99), roster.hiscores().entry("Bob").levels);
		assertNull(roster.hiscores().entry("alex"));
	}

	@Test
	public void cameOnlineIsOnlyMembersWhoWereNotOnlineBefore()
	{
		GroupMembers roster = GroupMembers.of("Ship", Arrays.asList("Alex", "Bob"));
		Map<String, Integer> first = new HashMap<>();
		first.put("Alex", 301);
		Map<String, Integer> second = new HashMap<>(first);
		second.put("Bob", 302);
		GamePresence a = GamePresence.read(roster, first, null);
		GamePresence b = GamePresence.read(roster, second, null);

		assertEquals(Arrays.asList("Alex"), GamePresence.cameOnline(roster.names(), GamePresence.UNKNOWN, a));
		assertEquals(Arrays.asList("Bob"), GamePresence.cameOnline(roster.names(), a, b));
		assertTrue(GamePresence.cameOnline(roster.names(), b, a).isEmpty());
	}
}
