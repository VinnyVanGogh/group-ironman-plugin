package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static com.vinnyvangogh.groupironman.EventFixtures.items;
import static com.vinnyvangogh.groupironman.EventFixtures.none;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * One moment, one Discord post: the events the router lets through for Discord
 * are held by {@link DiscordBatch} and posted as one by {@link DiscordMerge},
 * the way {@link GroupEventEngine} wires them.
 */
public class DiscordMergeTest
{
	private static final long STORAGE_MIN = 10_000;
	private static final EnumSet<GroupEvent.Type> STORAGE_ALL = EnumSet.of(GroupEvent.Type.STORAGE_DEPOSIT,
		GroupEvent.Type.STORAGE_WITHDRAWAL, GroupEvent.Type.STORAGE_SUMMARY, GroupEvent.Type.STORAGE_MINIMUM,
		GroupEvent.Type.STORAGE_LAST);

	private final FakeItems items = new FakeItems();
	/** The engine builds Discord posts with every digit kept: "40K (40,000)". */
	private final ItemLookup words = Amounts.BOTH.words(items);
	private final Gson gson = new Gson();
	private final DiscordBatch batch = new DiscordBatch();
	private final List<DiscordBatch.Pending> done = new ArrayList<>();
	private EventSettings settings;
	private long now = NOW;
	private final EventRouter router = new EventRouter(e -> batch.add(e, now,
		DiscordMerge.window(DiscordMerge.family(e.type), settings), done), e ->
	{
	});

	private static EventSettings discord(EnumSet<GroupEvent.Type> on)
	{
		return new EventSettings(on, none(), 1_000_000, STORAGE_MIN, 50, 7, false);
	}

	/** What the engine does with a storage visit: route each event, then lend the batch the whole visit. */
	private void visit(StorageChange change)
	{
		for (GroupEvent e : EventDetect.storage(change, items))
		{
			router.route(e, true, settings, items);
			if (e.type == GroupEvent.Type.STORAGE_SUMMARY)
			{
				batch.attach(e);
			}
		}
	}

	private List<DiscordMerge.Post> posts()
	{
		List<DiscordBatch.Pending> all = new ArrayList<>(done);
		all.addAll(batch.drain());
		List<DiscordMerge.Post> out = new ArrayList<>();
		for (DiscordBatch.Pending p : all)
		{
			DiscordMerge.Post post = DiscordMerge.merge(p.events, settings, words);
			if (post != null)
			{
				out.add(post);
			}
		}
		return out;
	}

	private JsonObject embed(DiscordMerge.Post post)
	{
		String json = DiscordWebhook.payload(gson, post, words, DiscordStyle.EMBED, "", true);
		return new JsonParser().parse(json).getAsJsonObject().getAsJsonArray("embeds").get(0).getAsJsonObject();
	}

	private static List<String> fieldNames(JsonObject embed)
	{
		List<String> out = new ArrayList<>();
		JsonArray fields = embed.getAsJsonArray("fields");
		for (int i = 0; fields != null && i < fields.size(); i++)
		{
			out.add(fields.get(i).getAsJsonObject().get("name").getAsString());
		}
		return out;
	}

	private static StorageChange change(Map<Integer, Integer> took, Map<Integer, Integer> deposited,
		Map<Integer, Integer> after)
	{
		return new StorageChange(NOW, "Alex", took, deposited, after);
	}

	// ---- Storage ----

	@Test
	public void aFortyThousandCoinWithdrawalPostsOnce()
	{
		// The Board's 2026-11-10 test: it posted "Big storage withdrawal" and "Group storage visit".
		settings = discord(STORAGE_ALL);
		visit(change(items(FakeItems.COINS, 40_000), items(), items(FakeItems.COINS, 5)));
		List<DiscordMerge.Post> posts = posts();
		assertEquals(1, posts.size());
		JsonObject embed = embed(posts.get(0));
		assertEquals("Alex: Big storage withdrawal", embed.get("title").getAsString());
		assertTrue(embed.get("description").getAsString(), embed.get("description").getAsString()
			.contains("took 40K (40,000) x Coins (big, 40K gp)"));
		assertEquals("attachment://screenshot.png", embed.getAsJsonObject("image").get("url").getAsString());
	}

	@Test
	public void aVisitWithBigAndSmallMovesPostsOnceListingBoth()
	{
		// The summary switch is off: the post still lists the small deposit.
		settings = discord(EnumSet.of(GroupEvent.Type.STORAGE_DEPOSIT, GroupEvent.Type.STORAGE_WITHDRAWAL));
		visit(change(items(FakeItems.COINS, 40_000), items(FakeItems.LOBSTER, 5), items(FakeItems.COINS, 5)));
		List<DiscordMerge.Post> posts = posts();
		assertEquals(1, posts.size());
		DiscordMerge.Post post = posts.get(0);
		assertEquals("Big storage withdrawal", post.title);
		assertTrue(post.description, post.description.contains("40K (40,000) x Coins (big"));
		assertTrue(post.description, post.description.contains("deposited 5 x Lobster"));
		assertFalse("a small move is not marked", post.description.contains("Lobster (big"));
		assertTrue(fieldNames(embed(post)).contains("Value"));
		assertEquals("the visit's total", "40.5K (40,500) gp", embed(post).getAsJsonArray("fields").get(0).getAsJsonObject()
			.get("value").getAsString());
	}

	@Test
	public void bigBothWaysIsABigStorageVisit()
	{
		settings = discord(STORAGE_ALL);
		visit(change(items(FakeItems.COINS, 40_000), items(FakeItems.WHIP, 1), items(FakeItems.COINS, 5)));
		List<DiscordMerge.Post> posts = posts();
		assertEquals(1, posts.size());
		assertEquals("Big storage visit", posts.get(0).title);
	}

	@Test
	public void aVisitWithNoBigMovesPostsTheSummaryWhenItIsOn()
	{
		settings = discord(STORAGE_ALL);
		visit(change(items(FakeItems.LOBSTER, 5), items(), items(FakeItems.LOBSTER, 5)));
		List<DiscordMerge.Post> posts = posts();
		assertEquals(1, posts.size());
		assertEquals("Group storage visit", posts.get(0).title);
		assertTrue(posts.get(0).description.contains("took 5 x Lobster"));
		assertFalse(posts.get(0).description.contains("(big"));
	}

	@Test
	public void aVisitWithNoBigMovesAndTheSummaryOffPostsNothing()
	{
		settings = discord(EnumSet.of(GroupEvent.Type.STORAGE_DEPOSIT, GroupEvent.Type.STORAGE_WITHDRAWAL));
		visit(change(items(FakeItems.LOBSTER, 5), items(), items(FakeItems.LOBSTER, 5)));
		assertTrue(posts().isEmpty());
	}

	@Test
	public void theMinimumAndTheLastOneFoldIntoTheVisitPost()
	{
		settings = discord(STORAGE_ALL);
		// The engine hears the minimum first (GroupService.commitStorage), then the visit.
		router.route(EventDetect.minimum(NOW, "Alex", new LowStock.Alert("Coins", 0, 100)), true, settings, items);
		visit(change(items(FakeItems.COINS, 40_000), items(), items()));
		List<DiscordMerge.Post> posts = posts();
		assertEquals(1, posts.size());
		List<String> names = fieldNames(embed(posts.get(0)));
		assertTrue(names.toString(), names.contains("Storage minimum hit"));
		assertTrue(names.toString(), names.contains("Took the last one"));
	}

	@Test
	public void aMinimumWithNoPostWorthyVisitStillPostsOnce()
	{
		settings = discord(EnumSet.of(GroupEvent.Type.STORAGE_MINIMUM, GroupEvent.Type.STORAGE_WITHDRAWAL));
		router.route(EventDetect.minimum(NOW, "Alex", new LowStock.Alert("Lobster", 1, 10)), true, settings, items);
		visit(change(items(FakeItems.LOBSTER, 5), items(), items(FakeItems.LOBSTER, 1)));
		List<DiscordMerge.Post> posts = posts();
		assertEquals(1, posts.size());
		assertEquals("Storage minimum hit", posts.get(0).title);
	}

	// ---- Levels ----

	@Test
	public void aLevelNinetyNinePostsOnce()
	{
		settings = discord(EnumSet.of(GroupEvent.Type.LEVEL_UP, GroupEvent.Type.MAXED, GroupEvent.Type.XP_MILESTONE,
			GroupEvent.Type.TOTAL_LEVEL));
		ProgressTracker progress = new ProgressTracker(1);
		progress.skill("Attack", 98, 12_000_000, NOW, "Alex");
		for (GroupEvent e : progress.skill("Attack", 99, 13_034_431, NOW, "Alex"))
		{
			router.route(e, true, settings, items);
		}
		List<DiscordMerge.Post> posts = posts();
		assertEquals(1, posts.size());
		assertEquals("99!", posts.get(0).title);
		assertEquals("99 Attack!", posts.get(0).description);
		assertTrue(posts.get(0).big);
		assertTrue(fieldNames(embed(posts.get(0))).contains("Level up"));
	}

	@Test
	public void aCombatLevelOnTheSameTickJoinsTheLevelUp()
	{
		settings = discord(EnumSet.of(GroupEvent.Type.LEVEL_UP, GroupEvent.Type.COMBAT_LEVEL));
		router.route(GroupEvent.of(GroupEvent.Type.LEVEL_UP, NOW, "Alex").text("Attack").number(60).build(), true,
			settings, items);
		router.route(GroupEvent.of(GroupEvent.Type.COMBAT_LEVEL, NOW + 600, "Alex").number(70).build(), true,
			settings, items);
		List<DiscordMerge.Post> posts = posts();
		assertEquals(1, posts.size());
		assertEquals("Combat level", posts.get(0).title);
	}

	// ---- Deaths ----

	@Test
	public void aDeathAndItsValueLostPostOnce()
	{
		settings = discord(EnumSet.of(GroupEvent.Type.DEATH, GroupEvent.Type.DEATH_VALUE));
		router.route(GroupEvent.of(GroupEvent.Type.DEATH, now, "Alex").text("Vorkath").location("Ungael").world(302)
			.build(), true, settings, items);
		assertTrue("the death waits for its value", batch.due(now + 600).isEmpty());
		now += 6_000;
		router.route(GroupEvent.of(GroupEvent.Type.DEATH_VALUE, now, "Alex").items(items(FakeItems.WHIP, 1))
			.number(1_500_000).build(), true, settings, items);
		assertEquals("the value closes the moment", 1, done.size());
		List<DiscordMerge.Post> posts = posts();
		assertEquals(1, posts.size());
		DiscordMerge.Post post = posts.get(0);
		assertEquals("Died", post.title);
		List<String> names = fieldNames(embed(post));
		assertTrue(names.toString(), names.contains("Lost on death"));
		assertTrue(names.toString(), names.contains("Value"));
		assertTrue(names.toString(), names.contains("World"));
	}

	@Test
	public void aDeathWithValueLostOffDoesNotWaitForIt()
	{
		settings = discord(EnumSet.of(GroupEvent.Type.DEATH));
		router.route(GroupEvent.of(GroupEvent.Type.DEATH, now, "Alex").build(), true, settings, items);
		assertEquals(1, batch.due(now + 1_200).size());
	}

	// ---- Drops ----

	@Test
	public void aDropUniqueCollectionLogAndPetOnOneKillPostOnce()
	{
		settings = discord(EnumSet.of(GroupEvent.Type.DROP, GroupEvent.Type.UNIQUE, GroupEvent.Type.CLOG,
			GroupEvent.Type.PET));
		router.route(GroupEvent.drop(now, "Alex", "Vorkath", items(FakeItems.WHIP, 1)), true, settings, items);
		router.route(GroupEvent.of(GroupEvent.Type.UNIQUE, now, "Alex").text("Vorkath")
			.items(items(FakeItems.WHIP, 1)).build(), true, settings, items);
		router.route(GroupEvent.clog(now, "Alex", "Abyssal whip"), true, settings, items);
		router.route(GroupEvent.of(GroupEvent.Type.PET, now, "Alex").build(), true, settings, items);
		List<DiscordMerge.Post> posts = posts();
		assertEquals(1, posts.size());
		DiscordMerge.Post post = posts.get(0);
		assertEquals("Pet", post.title);
		assertTrue(post.big);
		List<String> names = fieldNames(embed(post));
		assertTrue(names.toString(), names.containsAll(java.util.Arrays.asList("Valuable drop",
			"New collection log slot", "Untradeable unique", "Value")));
	}

	@Test
	public void twoKillsAreTwoPosts()
	{
		settings = discord(EnumSet.of(GroupEvent.Type.DROP));
		router.route(GroupEvent.drop(now, "Alex", "Vorkath", items(FakeItems.WHIP, 1)), true, settings, items);
		router.route(GroupEvent.drop(now + 600, "Alex", "Vorkath", items(FakeItems.WHIP, 1)), true, settings, items);
		assertEquals(2, posts().size());
	}

	// ---- Everything else ----

	@Test
	public void aKindWithNoPartnerIsNotHeld()
	{
		assertNull(DiscordMerge.family(GroupEvent.Type.ONLINE));
		assertNull(DiscordMerge.family(GroupEvent.Type.QUEST));
	}

	@Test
	public void aSingleEventPostReadsAsBefore()
	{
		GroupEvent pet = GroupEvent.of(GroupEvent.Type.PET, NOW, "Alex").build();
		settings = discord(EnumSet.of(GroupEvent.Type.PET));
		DiscordMerge.Post post = DiscordMerge.merge(Collections.singletonList(pet), settings, words);
		assertEquals(DiscordWebhook.payload(gson, pet, items, DiscordStyle.EMBED, "<@1>", false),
			DiscordWebhook.payload(gson, post, words, DiscordStyle.EMBED, "<@1>", false));
	}

	@Test
	public void aLogoutPostsAsOfflineNotOnline()
	{
		// #11's fix: the type alone would title a logout "Online".
		GroupEvent logout = GroupEvent.of(GroupEvent.Type.ONLINE, NOW, "Alex").text("offline").world(303).build();
		assertEquals("Offline", DiscordMerge.single(logout, words).title);
		JsonObject embed = new JsonParser().parse(DiscordWebhook.payload(gson, logout, items, DiscordStyle.EMBED, "",
			false)).getAsJsonObject().getAsJsonArray("embeds").get(0).getAsJsonObject();
		assertEquals("Alex: Offline", embed.get("title").getAsString());
	}

	@Test
	public void theThumbnailIsTheStackOfThePostsOwnItems()
	{
		settings = discord(STORAGE_ALL);
		visit(change(items(FakeItems.COINS, 40_000), items(), items(FakeItems.COINS, 5)));
		String url = embed(posts().get(0)).getAsJsonObject("thumbnail").get("url").getAsString();
		assertTrue(url, url.endsWith("/" + StackIcons.variant(FakeItems.COINS, 40_000) + ".png"));
	}

	@Test
	public void plainTextCarriesTheMergedFacts()
	{
		settings = discord(EnumSet.of(GroupEvent.Type.DEATH, GroupEvent.Type.DEATH_VALUE));
		router.route(GroupEvent.of(GroupEvent.Type.DEATH, now, "Alex").build(), true, settings, items);
		router.route(GroupEvent.of(GroupEvent.Type.DEATH_VALUE, now, "Alex").number(2_000).build(), true, settings,
			items);
		String json = DiscordWebhook.payload(gson, posts().get(0), words, DiscordStyle.PLAIN, "", false);
		String content = new JsonParser().parse(json).getAsJsonObject().get("content").getAsString();
		assertTrue(content, content.startsWith("Alex: died; lost about "));
	}
}
