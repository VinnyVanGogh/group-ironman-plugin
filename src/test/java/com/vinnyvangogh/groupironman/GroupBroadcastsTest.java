package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static com.vinnyvangogh.groupironman.EventFixtures.all;
import static com.vinnyvangogh.groupironman.EventFixtures.none;
import static com.vinnyvangogh.groupironman.EventFixtures.settings;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The game's Group channel broadcasts, posted for members without the plugin (Board 2026-11-14). */
public class GroupBroadcastsTest
{
	private static final Function<String, Integer> ITEMS = name ->
	{
		if ("Abyssal whip".equalsIgnoreCase(name))
		{
			return FakeItems.WHIP;
		}
		return "Shark".equalsIgnoreCase(name) ? Integer.valueOf(FakeItems.SHARK) : null;
	};
	private static final Predicate<String> NOBODY = n -> false;

	private static GroupEvent parse(String line)
	{
		return GroupBroadcasts.parse(line, NOW, ITEMS);
	}

	private static void is(String line, GroupEvent.Type type, String member, String text, long number)
	{
		GroupEvent e = parse(line);
		assertNotNull(line, e);
		assertEquals(line, type, e.type);
		assertEquals(line, member, e.member);
		assertEquals(line, text, e.text);
		assertEquals(line, number, e.number);
		assertTrue(line, e.broadcast);
	}

	// ---- Each format parses ----

	@Test
	public void theBoardsOwnCaptures()
	{
		is("GroupOtt has reached Herblore level 21.", GroupEvent.Type.LEVEL_UP, "GroupOtt", "Herblore", 21);
		is("GroupOtt has completed a quest: Black Knights' Fortress", GroupEvent.Type.QUEST, "GroupOtt",
			"Black Knights' Fortress", 0);
	}

	@Test
	public void theChatboxGroupTagTagsAndNonBreakingSpacesAreIgnored()
	{
		is("[GroupPures] GroupOtt has reached Herblore level 21.", GroupEvent.Type.LEVEL_UP, "GroupOtt", "Herblore",
			21);
		is("<col=ef1020>Iron Ott has reached Herblore level 21.</col>", GroupEvent.Type.LEVEL_UP, "Iron Ott",
			"Herblore", 21);
	}

	@Test
	public void levelKinds()
	{
		is("Alex has reached combat level 100.", GroupEvent.Type.COMBAT_LEVEL, "Alex", "", 100);
		is("Alex has reached a total level of 1,750.", GroupEvent.Type.TOTAL_LEVEL, "Alex", "", 1750);
		is("Alex has reached the highest possible Herblore level of 99.", GroupEvent.Type.MAXED, "Alex", "Herblore",
			99);
		is("Alex has reached Herblore level 99.", GroupEvent.Type.MAXED, "Alex", "Herblore", 99);
		is("Alex has reached 50,000,000 XP in Herblore.", GroupEvent.Type.XP_MILESTONE, "Alex", "Herblore",
			50_000_000);
	}

	@Test
	public void achievements()
	{
		GroupEvent task = parse("Alex has completed an Elite combat task: Perfect Zulrah (5 points).");
		assertEquals(GroupEvent.Type.COMBAT_TASK, task.type);
		assertEquals("Perfect Zulrah", task.text);
		assertEquals("Elite", task.detail);
		is("Alex has completed the Hard Ardougne diary.", GroupEvent.Type.DIARY, "Alex", "Hard Ardougne", 0);
		is("Alex has achieved a new Vorkath personal best: 1:23.40", GroupEvent.Type.PERSONAL_BEST, "Alex",
			"Vorkath 1:23.40", 0);
	}

	@Test
	public void dropsLogSlotsAndPets()
	{
		GroupEvent drop = parse("Alex received a drop: Abyssal whip (1,500,000 coins).");
		assertEquals(GroupEvent.Type.DROP, drop.type);
		assertEquals(Collections.singletonMap(FakeItems.WHIP, 1), drop.items);
		assertTrue("a broadcast drop names no NPC and is still a drop", drop.isValid());
		assertEquals(Collections.singletonMap(FakeItems.SHARK, 200),
			parse("Alex received a drop: 200 x Shark (160,000 coins).").items);
		assertNull("an item the client cannot name is no drop", parse("Alex received a drop: Mystery box"));

		is("Alex received a new collection log item: Dragon pickaxe (123/1477)", GroupEvent.Type.CLOG, "Alex",
			"Dragon pickaxe", 0);
		is("Alex has a funny feeling like he's being followed: Pet snakeling at 50 kill count.", GroupEvent.Type.PET,
			"Alex", "Pet snakeling", 0);
		is("Alex feels something weird sneaking into her backpack: Heron at 5,000,000 XP.", GroupEvent.Type.PET,
			"Alex", "Heron", 0);
		is("Alex has a funny feeling like he would have been followed: Baby mole at 120 kill count.",
			GroupEvent.Type.PET, "Alex", "Baby mole", 0);
	}

	@Test
	public void deaths()
	{
		is("Alex has been defeated by Vorkath in The Wilderness and lost (1,234 coins) worth of loot.",
			GroupEvent.Type.DEATH, "Alex", "Vorkath", 0);
		is("Alex has died and lost a group ironman life!", GroupEvent.Type.DEATH, "Alex", "", 0);
	}

	@Test
	public void otherLinesAreNothing()
	{
		for (String line : Arrays.asList(null, "", "anyone got guams?", "Alex has logged in.",
			"A name far too long for the game has reached Herblore level 21.", "[GIMU] 0123"))
		{
			assertNull(String.valueOf(line), parse(line));
		}
	}

	// ---- Plugin users, the one poster, dedupe ----

	@Test
	public void aPluginUsersBroadcastIsSkippedTheirClientPostsIt()
	{
		GroupEvent e = parse("Alex has reached Herblore level 21.");
		assertEquals(GroupBroadcasts.Action.SKIP, GroupBroadcasts.decide(e, n -> n.equalsIgnoreCase("alex"), true,
			new GroupBroadcasts.Seen(), NOW));
		assertEquals(GroupBroadcasts.Action.SKIP, GroupBroadcasts.decide(null, NOBODY, true,
			new GroupBroadcasts.Seen(), NOW));
	}

	@Test
	public void onlyTheChosenPosterPostsTheRestShowIt()
	{
		GroupEvent e = parse("Ott has reached Herblore level 21.");
		assertEquals(GroupBroadcasts.Action.POST, GroupBroadcasts.decide(e, NOBODY, true, new GroupBroadcasts.Seen(),
			NOW));
		assertEquals(GroupBroadcasts.Action.SHOW, GroupBroadcasts.decide(e, NOBODY, false, new GroupBroadcasts.Seen(),
			NOW));
	}

	@Test
	public void theLeaderPostsWhileOnlineElseTheFirstByName()
	{
		List<String> others = Arrays.asList("Alex", "Sam");
		assertTrue("the leader posts though Alex is first by name", ChannelPosts.leads("Zed", true, others, "Zed"));
		assertFalse("the leader is online: Alex does not post", ChannelPosts.leads("Alex", true,
			Arrays.asList("Zed", "Sam"), "Zed"));
		assertTrue("leader offline: first by name", ChannelPosts.leads("Alex", true, others.subList(1, 2), "Zed"));
		assertFalse(ChannelPosts.leads("Sam", true, Collections.singletonList("Alex"), "Zed"));
		assertTrue("no roles known: first by name", ChannelPosts.leads("Alex", true, others.subList(1, 2), ""));
		assertFalse("not in the party: posts only when it hears nobody", ChannelPosts.leads("Zed", false, others,
			"Zed"));
	}

	@Test
	public void aBroadcastIsPostedOnceAcrossARestart()
	{
		GroupEvent e = parse("Ott has completed a quest: Black Knights' Fortress");
		GroupBroadcasts.Seen seen = new GroupBroadcasts.Seen();
		assertEquals(GroupBroadcasts.Action.POST, GroupBroadcasts.decide(e, NOBODY, true, seen, NOW));
		assertEquals(GroupBroadcasts.Action.SHOW, GroupBroadcasts.decide(e, NOBODY, true, seen, NOW + 1000));

		GroupBroadcasts.Seen restarted = GroupBroadcasts.Seen.load(seen.save(), NOW + 60_000);
		assertEquals(GroupBroadcasts.Action.SHOW, GroupBroadcasts.decide(e, NOBODY, true, restarted, NOW + 60_000));
		GroupBroadcasts.Seen dayLater = GroupBroadcasts.Seen.load(seen.save(), NOW + GroupBroadcasts.REMEMBER_MS + 1);
		assertEquals("forgotten after a day", GroupBroadcasts.Action.POST,
			GroupBroadcasts.decide(e, NOBODY, true, dayLater, NOW + GroupBroadcasts.REMEMBER_MS + 1));
		assertEquals(0, GroupBroadcasts.Seen.load("junk\nno tab\n", NOW).save().length());
	}

	@Test
	public void anotherPostersClaimOverTheGroupSyncStopsASecondPost()
	{
		GroupEvent e = parse("Ott has reached Herblore level 21.");
		Gson gson = new Gson();
		String payload = GroupCodec.encodePosted(gson, Collections.singletonList(GroupBroadcasts.key(e)));
		assertNull("an older build's decoder reads it as an unknown kind", GroupCodec.decode(gson, payload));
		List<String> claim = GroupCodec.posted(gson, payload);
		assertEquals(Collections.singletonList(GroupBroadcasts.key(e)), claim);

		// Two clients both think they post (their views of who is online differ): the late one hears the claim.
		GroupBroadcasts.Seen late = new GroupBroadcasts.Seen();
		claim.forEach(k -> late.add(k, NOW));
		assertEquals(GroupBroadcasts.Action.SHOW, GroupBroadcasts.decide(e, NOBODY, true, late, NOW + 2000));
	}

	@Test
	public void theSameLineTheSameMinuteOnTwoClientsIsOneKeyANewMinuteIsAnother()
	{
		GroupEvent a = GroupBroadcasts.parse("Ott has reached Herblore level 21.", 120_000, ITEMS);
		GroupEvent b = GroupBroadcasts.parse("[GroupPures] OTT has reached Herblore level 21.", 121_500, ITEMS);
		GroupEvent c = GroupBroadcasts.parse("Ott has reached Herblore level 21.", 185_000, ITEMS);
		assertEquals(GroupBroadcasts.key(a), GroupBroadcasts.key(b));
		assertFalse(GroupBroadcasts.key(a).equals(GroupBroadcasts.key(c)));
	}

	@Test
	public void aClaimIsCappedAndReadOnlyAsStrings()
	{
		Gson gson = new Gson();
		List<String> many = new ArrayList<>();
		for (int i = 0; i < 50; i++)
		{
			many.add("k" + i);
		}
		assertEquals(GroupCodec.MAX_POSTED, GroupCodec.posted(gson, GroupCodec.encodePosted(gson, many)).size());
		assertEquals(Collections.singletonList("ok"), GroupCodec.posted(gson,
			"{\"v\":1,\"k\":\"posted\",\"p\":[1,{\"a\":1},\"ok\",\"\"]}"));
		assertNull("not a claim", GroupCodec.posted(gson, "{\"v\":1,\"k\":\"events\",\"e\":[]}"));
		assertNull(GroupCodec.posted(gson, "not json {"));
	}

	// ---- Toggles, N levels, the footer ----

	@Test
	public void theEventSwitchesAndPostEveryNLevelsApply()
	{
		List<GroupEvent> discord = new ArrayList<>();
		List<GroupEvent> overlay = new ArrayList<>();
		EventRouter router = new EventRouter(discord::add, overlay::add);
		FakeItems items = new FakeItems();
		EventSettings every5 = settings(all(), all()).levels(1, 5);
		router.post(parse("Ott has reached Herblore level 21."), every5, items);
		assertTrue("21 is not a multiple of 5", discord.isEmpty());
		router.post(parse("Ott has reached Herblore level 25."), every5, items);
		assertEquals(1, discord.size());
		assertTrue("posting never shows it a second time", overlay.isEmpty());

		discord.clear();
		router.post(parse("Ott has completed a quest: Cook's Assistant"), settings(EnumSet.complementOf(
			EnumSet.of(GroupEvent.Type.QUEST)), none()), items);
		assertTrue("Quest switched off", discord.isEmpty());
		router.post(parse("Ott received a drop: 2 x Shark (1,600 coins)."), settings(all(), none()), items);
		assertTrue("under the valuable drop minimum", discord.isEmpty());
		router.post(parse("Ott received a drop: Abyssal whip (1,500,000 coins)."), settings(all(), none()), items);
		assertEquals(1, discord.size());
	}

	@Test
	public void theFooterSaysWhereThePostCameFrom()
	{
		Gson gson = new Gson();
		FakeItems items = new FakeItems();
		GroupEvent e = parse("Ott has completed a quest: Cook's Assistant");
		// Gson writes the apostrophe as ', so the check is on the words after it.
		String marker = "group broadcast";
		String embed = DiscordWebhook.payload(gson, DiscordMerge.single(e, items), items, DiscordStyle.EMBED, "",
			false, null);
		assertTrue(embed, embed.contains(marker));
		String plain = DiscordWebhook.payload(gson, DiscordMerge.single(e, items), items, DiscordStyle.PLAIN, "",
			false, null);
		assertTrue(plain, plain.contains(marker));

		GroupEvent own = GroupEvent.of(GroupEvent.Type.QUEST, NOW, "Alex").text("Cook's Assistant").build();
		assertFalse(DiscordWebhook.payload(gson, DiscordMerge.single(own, items), items, DiscordStyle.EMBED, "",
			false, null).contains(marker));
		String drop = EventText.describe(parse("Ott received a drop: Abyssal whip (1,500,000 coins)."), items);
		assertTrue(drop, drop.startsWith("received a drop: 1 x Abyssal whip"));
	}

	@Test
	public void theGroupSettingIsOnByDefault()
	{
		GroupSettingsSchema.Setting s = GroupSettingsSchema.get(GroupSettingsSchema.NO_PLUGIN_EVENTS);
		assertNotNull(s);
		assertTrue(s.flag);
		GroupIronmanConfig defaults = new GroupIronmanConfig()
		{
		};
		assertTrue(new GroupSettingsSchema.Resolved(defaults, null).on(GroupSettingsSchema.NO_PLUGIN_EVENTS));
	}
}
