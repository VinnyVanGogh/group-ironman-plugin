package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.DiscordWebhookTest.URL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Every other kind of post grouped per member like level-ups (Board 2026-11-15), through the real sender with a fake
 * transport and clock: storage visits net out, other kinds stack cards, and a restart keeps editing.
 */
public class EventPostsTest
{
	private static final long SEC = TimeUnit.SECONDS.toMillis(1);
	private static final long MIN = TimeUnit.MINUTES.toMillis(1);
	private static final long WINDOW = TimeUnit.MINUTES.toMillis(30);
	private static final int ROPE = 954;
	private static final ItemLookup LOOKUP = new ItemLookup()
	{
		@Override
		public String name(int id)
		{
			return id == ROPE ? "Rope" : "item " + id;
		}

		@Override
		public long price(int id)
		{
			return id == ROPE ? 20 : 100;
		}
	};

	private final Gson gson = new Gson();
	private long now = 1_000_000;
	private final FakeTransport fake = new FakeTransport(() -> now);
	private final List<DiscordQueue.Post> sent = fake.sent;
	private final DiscordSender sender = fake.sender;
	private DiscordStyle style = DiscordStyle.EMBED;
	private EventPosts posts = client();

	/** A client's grouped posts; a second one is the same client after a restart. */
	private EventPosts client()
	{
		return new EventPosts(sender, gson, () -> now, () -> style, () -> LOOKUP);
	}

	/** A storage visit's post, as {@link DiscordMerge#merge} words it. */
	private static DiscordMerge.Post visit(String member, Map<Integer, Integer> took, Map<Integer, Integer> put)
	{
		GroupEvent summary = GroupEvent.of(GroupEvent.Type.STORAGE_SUMMARY, 0, member).taken(took).items(put)
			.world(330).build();
		StringBuilder text = new StringBuilder("group storage:");
		DiscordMerge.moved(text, "took", took, Long.MAX_VALUE, LOOKUP);
		DiscordMerge.moved(text, "deposited", put, Long.MAX_VALUE, LOOKUP);
		return new DiscordMerge.Post(summary, "Group storage visit", text.toString(), put, Collections.emptyList(),
			false);
	}

	private static DiscordMerge.Post drop(String member, String npc)
	{
		return DiscordMerge.single(GroupEvent.drop(0, member, npc, Collections.singletonMap(ROPE, 1)), LOOKUP);
	}

	private static DiscordMerge.Post diary(String member, String text)
	{
		return DiscordMerge.single(GroupEvent.of(GroupEvent.Type.DIARY, 0, member).text(text).build(), LOOKUP);
	}

	/** A moment into the posts, as the engine sends it: its usual post first, "first N" here to tell them apart. */
	private void add(DiscordMerge.Post post, String first)
	{
		add(post, first, false);
	}

	private void add(DiscordMerge.Post post, String first, boolean pings)
	{
		EventThreads.Moment m = EventPosts.moment(gson, URL, post, LOOKUP, null, "Iron Pals", pings);
		assertNotNull(m);
		posts.add(m, first, null, WINDOW);
	}

	/** The engine's once-a-second tick: owed edits, then the next post. */
	private void tick()
	{
		posts.tick();
		sender.pump();
	}

	private void reply(int status, String messageId)
	{
		fake.reply(status, messageId);
	}

	/** Easy Varrock posted as m1, then Easy Falador 15 s later, its edit sent and not yet answered. */
	private void twoDiaries()
	{
		add(diary("Alex", "Easy Varrock"), "first");
		tick();
		reply(200, "m1");
		now += 15 * SEC;
		add(diary("Alex", "Easy Falador"), "second");
		tick();
	}

	private static JsonArray embeds(DiscordQueue.Post post)
	{
		return new Gson().fromJson(post.json, JsonObject.class).getAsJsonArray("embeds");
	}

	private static JsonObject top(DiscordQueue.Post post)
	{
		return embeds(post).get(0).getAsJsonObject();
	}

	private static String field(JsonObject embed, String name)
	{
		for (JsonElement f : embed.getAsJsonArray("fields"))
		{
			if (f.getAsJsonObject().get("name").getAsString().equals(name))
			{
				return f.getAsJsonObject().get("value").getAsString();
			}
		}
		return null;
	}

	@Test
	public void twoStorageVisitsTwoMinutesApartAreOnePostEditedWithTheNetAmountsAndValue()
	{
		add(visit("Alex", Collections.singletonMap(ROPE, 2), Collections.emptyMap()), "first visit");
		tick();
		assertEquals("first visit", sent.get(0).json);
		assertNull(sent.get(0).edit);
		reply(200, "m1");

		now += 2 * MIN;
		add(visit("Alex", Collections.emptyMap(), Collections.singletonMap(ROPE, 10)), "second visit");
		tick();

		assertEquals("one post, then one edit of it", 2, sent.size());
		DiscordQueue.Post edit = sent.get(1);
		assertEquals("m1", edit.edit);
		JsonObject card = top(edit);
		assertEquals("took 2, then deposited 10: 8 in", "group storage: deposited 8 x Rope",
			card.get("description").getAsString());
		assertEquals("Alex: " + EventText.title(GroupEvent.Type.STORAGE_DEPOSIT), card.get("title").getAsString());
		assertEquals("8 x 20 gp", "160 gp", field(card, "Value"));
		assertEquals("deposited 10 x Rope\ntook 2 x Rope", field(card, "Visits, newest first"));
		assertEquals("one card, not one per visit", 1, embeds(edit).size());
		assertFalse("an edit sends no username", new Gson().fromJson(edit.json, JsonObject.class).has("username"));
	}

	@Test
	public void storageThatEvensOutSaysNothingMovedOverall()
	{
		add(visit("Alex", Collections.singletonMap(ROPE, 5), Collections.emptyMap()), "first");
		tick();
		reply(200, "m1");
		now += MIN;
		add(visit("Alex", Collections.emptyMap(), Collections.singletonMap(ROPE, 5)), "second");
		tick();
		assertEquals("group storage: nothing moved overall", top(sent.get(1)).get("description").getAsString());
	}

	@Test
	public void storageVisitsThirtyOneMinutesApartAreTwoPosts()
	{
		add(visit("Alex", Collections.singletonMap(ROPE, 2), Collections.emptyMap()), "first visit");
		tick();
		reply(200, "m1");
		now += 31 * MIN;
		add(visit("Alex", Collections.emptyMap(), Collections.singletonMap(ROPE, 10)), "second visit");
		tick();
		assertEquals(2, sent.size());
		assertNull("past the window: a new post", sent.get(1).edit);
		assertEquals("second visit", sent.get(1).json);
	}

	@Test
	public void differentMembersNeverMerge()
	{
		add(visit("Alex", Collections.singletonMap(ROPE, 2), Collections.emptyMap()), "alex");
		tick();
		reply(200, "m1");
		now += MIN;
		add(visit("Sam", Collections.emptyMap(), Collections.singletonMap(ROPE, 10)), "sam");
		tick();
		assertEquals(2, sent.size());
		assertNull(sent.get(1).edit);
		assertEquals("sam", sent.get(1).json);
	}

	@Test
	public void differentKindsOfOneMemberNeverMerge()
	{
		add(visit("Alex", Collections.singletonMap(ROPE, 2), Collections.emptyMap()), "storage");
		tick();
		reply(200, "m1");
		now += MIN;
		add(drop("Alex", "Goblin"), "drop");
		tick();
		assertEquals(2, sent.size());
		assertNull(sent.get(1).edit);
		assertEquals("drop", sent.get(1).json);
	}

	@Test
	public void diariesStackOneCardEachNewestFirst()
	{
		add(diary("Alex", "Easy Varrock"), "first");
		tick();
		reply(200, "m1");
		for (String area : new String[]{"Easy Lumbridge", "Easy Falador", "Easy Ardougne"})
		{
			now += 15 * SEC;
			add(diary("Alex", area), area);
			tick();
			reply(200, null);
		}
		assertEquals("one post and three edits", 4, sent.size());
		JsonArray cards = embeds(sent.get(3));
		assertEquals(4, cards.size());
		assertTrue(cards.get(0).getAsJsonObject().get("description").getAsString().contains("Easy Ardougne"));
		assertTrue(cards.get(3).getAsJsonObject().get("description").getAsString().contains("Easy Varrock"));
	}

	@Test
	public void quickEventsFoldIntoOneEdit()
	{
		add(diary("Alex", "Easy Varrock"), "first");
		tick();
		reply(200, "m1");
		now += 15 * SEC;
		add(diary("Alex", "Easy Lumbridge"), "x");
		add(diary("Alex", "Easy Falador"), "y");
		tick();
		assertEquals("both in one edit", 2, sent.size());
		assertEquals(3, embeds(sent.get(1)).size());
	}

	@Test
	public void theEleventhEventStartsASecondMessage()
	{
		add(drop("Alex", "Goblin 1"), "first 1");
		tick();
		reply(200, "m1");
		for (int i = 2; i <= 10; i++)
		{
			now += 15 * SEC;
			add(drop("Alex", "Goblin " + i), "first " + i);
			tick();
			reply(200, null);
		}
		assertEquals("one post, nine edits", 10, sent.size());
		assertEquals(10, embeds(sent.get(9)).size());
		now += 15 * SEC;
		add(drop("Alex", "Goblin 11"), "first 11");
		tick();
		DiscordQueue.Post eleventh = sent.get(10);
		assertNull("Discord's 10 embeds are full: a new message", eleventh.edit);
		assertEquals("first 11", eleventh.json);
	}

	@Test
	public void aRestartMidWindowKeepsEditingTheSameMessage()
	{
		add(visit("Alex", Collections.singletonMap(ROPE, 2), Collections.emptyMap()), "first visit");
		tick();
		reply(200, "m1");
		String saved = posts.saved();
		assertNotNull(saved);
		assertFalse("one line per message", saved.contains("\n"));
		assertFalse("never the webhook's token", saved.contains("AbC-def_GHI123"));

		now += 5 * MIN;
		posts = client();
		posts.restore(saved, WINDOW);
		add(visit("Alex", Collections.emptyMap(), Collections.singletonMap(ROPE, 10)), "second visit");
		tick();
		assertEquals(2, sent.size());
		assertEquals("m1", sent.get(1).edit);
		assertEquals("the saved visit counts", "group storage: deposited 8 x Rope",
			top(sent.get(1)).get("description").getAsString());
	}

	@Test
	public void aRestoredMessagePastTheWindowIsDropped()
	{
		add(diary("Alex", "Easy Varrock"), "first");
		tick();
		reply(200, "m1");
		String saved = posts.saved();
		now += 31 * MIN;
		posts = client();
		posts.restore(saved, WINDOW);
		assertEquals("dropped from the file", "", posts.saved());
		add(diary("Alex", "Easy Falador"), "again");
		tick();
		assertNull(sent.get(1).edit);
	}

	@Test
	public void anUnreadableSavedLineIsDropped()
	{
		posts.restore("not json\n{\"v\":99}", WINDOW);
		assertEquals("", posts.saved());
	}

	@Test
	public void aPostThatPingsStartsItsOwnMessage()
	{
		add(drop("Alex", "Goblin"), "first");
		tick();
		reply(200, "m1");
		now += MIN;
		add(drop("Alex", "Vorkath"), "pet ping", true);
		tick();
		assertEquals(2, sent.size());
		assertNull("an edit never pings: a new post", sent.get(1).edit);
		assertEquals("pet ping", sent.get(1).json);
	}

	@Test
	public void anEditDiscordRefusesIsPostedAnewWithEveryCard()
	{
		twoDiaries();
		reply(404, null);
		sender.pump();
		DiscordQueue.Post again = sent.get(2);
		assertNull(again.edit);
		assertEquals(2, embeds(again).size());
		assertEquals("Iron Pals", new Gson().fromJson(again.json, JsonObject.class).get("username").getAsString());
	}

	@Test
	public void plainStyleIsOneLinePerCardNewestFirst()
	{
		style = DiscordStyle.PLAIN;
		twoDiaries();
		String content = new Gson().fromJson(sent.get(1).json, JsonObject.class).get("content").getAsString();
		String[] lines = content.split("\n");
		assertEquals(2, lines.length);
		assertTrue(lines[0].startsWith("Alex: ") && lines[0].contains("Easy Falador"));
		assertTrue(lines[1].contains("Easy Varrock"));
	}

	@Test
	public void thePictureIsTheNewestEventsOnTheTopCardOnly()
	{
		EventThreads.Moment first = EventPosts.moment(gson, URL, diary("Alex", "Easy Varrock"), LOOKUP, null, null,
			false);
		posts.add(first, "first", new byte[]{1}, WINDOW);
		tick();
		reply(200, "m1");
		now += 15 * SEC;
		EventThreads.Moment second = EventPosts.moment(gson, URL, diary("Alex", "Easy Falador"), LOOKUP, null, null,
			false);
		posts.add(second, "second", new byte[]{2}, WINDOW);
		tick();
		DiscordQueue.Post edit = sent.get(1);
		assertEquals(2, edit.png[0]);
		assertTrue(top(edit).has("image"));
		assertFalse(embeds(edit).get(1).getAsJsonObject().has("image"));
		assertTrue("the new picture replaces the old", edit.json.contains("\"attachments\""));
	}

	@Test
	public void onlineGravestoneAndTheWeeklyRecapNeverGroup()
	{
		assertNull(EventThreads.family(GroupEvent.Type.ONLINE));
		assertNull(EventThreads.family(GroupEvent.Type.BACK));
		assertNull(EventThreads.family(GroupEvent.Type.GRAVESTONE_EXPIRING));
		assertNull(EventThreads.family(GroupEvent.Type.WEEKLY_RECAP));
		assertEquals("diaries, quests and achievements share one", EventThreads.family(GroupEvent.Type.DIARY),
			EventThreads.family(GroupEvent.Type.QUEST));
		assertEquals(EventThreads.family(GroupEvent.Type.DIARY), EventThreads.family(GroupEvent.Type.COMBAT_TASK));
		assertEquals(EventThreads.STORAGE, EventThreads.family(GroupEvent.Type.STORAGE_SUMMARY));
		assertEquals("SLAYER", EventThreads.family(GroupEvent.Type.SLAYER));
	}

	@Test
	public void theWindowDefaultsTo30AndLevelUpsKeepTheirOwnSetting()
	{
		GroupIronmanConfig config = new GroupIronmanConfig()
		{
		};
		assertEquals(30, config.discordGroupPosts());
		assertEquals("level-ups unchanged", 60, config.discordCombineLevels());
	}
}
