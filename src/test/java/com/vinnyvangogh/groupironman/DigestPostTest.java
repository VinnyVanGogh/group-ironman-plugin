package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** The daily digest's posts: netted storage, grouped levels, whole requests, Discord's limits kept by splitting. */
public class DigestPostTest
{
	private static final int COINS = 995;
	private static final int SHARK = 385;
	private static final int WHIP = 4151;
	private final Gson gson = new Gson();

	private static final ItemLookup LOOKUP = new ItemLookup()
	{
		@Override
		public String name(int id)
		{
			switch (id)
			{
				case COINS:
					return "Coins";
				case SHARK:
					return "Shark";
				case WHIP:
					return "Abyssal whip";
				default:
					return "Thammaron's sceptre number " + id;
			}
		}

		@Override
		public long price(int id)
		{
			return id == COINS ? 1 : id == SHARK ? 1_000 : id == WHIP ? 1_500_000 : 10;
		}
	};

	private static Digest.Entry storage(GroupEvent.Type type, long at, String member, Map<Integer, Integer> items,
		Map<Integer, Integer> taken)
	{
		return Digest.Entry.of(GroupEvent.of(type, at, member).items(items).taken(taken).build(), "said");
	}

	private static Digest.Entry visit(long at, String member, Map<Integer, Integer> in, Map<Integer, Integer> out)
	{
		return storage(GroupEvent.Type.STORAGE_SUMMARY, at, member, in, out);
	}

	private static Map<Integer, Integer> one(int id, int qty)
	{
		return Collections.singletonMap(id, qty);
	}

	private static Map<Integer, Integer> none()
	{
		return Collections.emptyMap();
	}

	private static Map<GroupEvent.Category, List<Digest.Entry>> held(GroupEvent.Category c, Digest.Entry... entries)
	{
		Map<GroupEvent.Category, List<Digest.Entry>> out = new EnumMap<>(GroupEvent.Category.class);
		out.put(c, new ArrayList<>(Arrays.asList(entries)));
		return out;
	}

	@Test
	public void takingCoinsAndPuttingThemBackIsOneRoundTripNotTwoBigMoves()
	{
		List<DigestPost.Section> s = DigestPost.sections(held(GroupEvent.Category.STORAGE,
			// The live big withdrawal beside its visit's summary: counted once.
			storage(GroupEvent.Type.STORAGE_WITHDRAWAL, 1, "GroupPNK", one(COINS, 799_999), none()),
			visit(1, "GroupPNK", none(), one(COINS, 799_999)),
			visit(2, "GroupPNK", one(COINS, 799_999), none())), LOOKUP, false);
		assertEquals(1, s.size());
		assertEquals(Collections.singletonList("Moved 799K x Coins and put them back"), s.get(0).members.get("GroupPNK"));
		assertEquals("a round trip moves no gp", "", s.get(0).summary);
	}

	@Test
	public void hiddenRoundTripsLeaveAnEmptySectionOut()
	{
		List<DigestPost.Section> s = DigestPost.sections(held(GroupEvent.Category.STORAGE,
			visit(1, "GroupPNK", none(), one(COINS, 799_999)),
			visit(2, "GroupPNK", one(COINS, 799_999), none())), LOOKUP, true);
		assertTrue(s.isEmpty());
		assertTrue(DigestPost.payloads(gson, s, DiscordStyle.EMBED, null, "f", 1L).isEmpty());
	}

	@Test
	public void bigMovesUseNetFigures()
	{
		List<DigestPost.Section> s = DigestPost.sections(held(GroupEvent.Category.STORAGE,
			visit(1, "Alex", one(SHARK, 10), none()),
			visit(2, "Alex", none(), one(SHARK, 4)),
			visit(3, "Alex", none(), one(WHIP, 1)),
			// Took the last whip and kept it: still true. The last shark came back: no longer true.
			storage(GroupEvent.Type.STORAGE_LAST, 3, "Alex", one(WHIP, 1), none()),
			visit(4, "Bob", one(SHARK, 1), none())), LOOKUP, false);
		DigestPost.Section storage = s.get(0);
		assertEquals(Arrays.asList("Put in 6 x Shark (6,000 gp)", "Took out 1 x Abyssal whip (1.5M gp)", "Said"),
			storage.members.get("Alex"));
		assertEquals(Collections.singletonList("Put in 1 x Shark (1,000 gp)"), storage.members.get("Bob"));
		assertEquals("Net in 7,000 gp · Net out 1.5M gp", storage.summary);
		assertEquals("https://static.runelite.net/cache/item/icon/4151.png", storage.thumbnail);
	}

	@Test
	public void levelUpsGroupPerMemberAndSkill()
	{
		List<DigestPost.Section> s = DigestPost.sections(held(GroupEvent.Category.PROGRESS,
			level("GroupPNK", "Magic", 62, 61),
			level("Bob", "Attack", 41, 0),
			level("GroupPNK", "Magic", 63, 62),
			level("GroupPNK", "Prayer", 7, 3),
			Digest.Entry.of(GroupEvent.of(GroupEvent.Type.QUEST, 9, "Bob").text("Cook's Assistant").build(),
				"completed Cook's Assistant")), LOOKUP, false);
		DigestPost.Section p = s.get(0);
		assertEquals(Arrays.asList("GroupPNK", "Bob"), new ArrayList<>(p.members.keySet()));
		assertEquals(Arrays.asList("Magic 61 → 63 (+2)", "Prayer 3 → 7 (+4)"), p.members.get("GroupPNK"));
		assertEquals(Arrays.asList("Attack 40 → 41", "Completed Cook's Assistant"), p.members.get("Bob"));
		assertEquals("7 levels gained", p.summary);
		assertEquals("no third-party host", null, p.thumbnail);
	}

	private static Digest.Entry level(String member, String skill, long level, long previous)
	{
		return Digest.Entry.of(GroupEvent.of(GroupEvent.Type.LEVEL_UP, level, member).text(skill).number(level)
			.previous(previous).build(), skill + " level " + level);
	}

	@Test
	public void aRequestListsItsItemsInFullThenPlusMoreAndATotal()
	{
		Map<Integer, Integer> items = new LinkedHashMap<>();
		for (int i = 0; i < GroupEvent.MAX_DROP_ITEMS; i++)
		{
			items.put(20_000 + i, 2);
		}
		Digest.Entry made = Digest.Entry.of(GroupEvent.of(GroupEvent.Type.REQUEST, 1, "Alice").text("Gear")
			.items(items).number(RequestBoard.KIND_MADE).build(), "made a request, 'Gear': 2 Thammaron's scept");
		String line = DigestPost.sections(held(GroupEvent.Category.GROUP, made), LOOKUP, false).get(0).members
			.get("Alice").get(0);
		assertTrue(line, line.startsWith("Made a request, 'Gear': 2 x Thammaron's sceptre number 20000, "));
		assertTrue(line, line.contains("2 x Thammaron's sceptre number 20024 +15 more (80 items, 800 gp)"));
		assertFalse(line.contains("20025"));
	}

	@Test
	public void emptySectionsAreLeftOutAndEachSectionIsAnEmbed()
	{
		Map<GroupEvent.Category, List<Digest.Entry>> held = new EnumMap<>(GroupEvent.Category.class);
		held.put(GroupEvent.Category.DROPS, Collections.singletonList(Digest.Entry.of(
			GroupEvent.drop(1, "Alex", "Kraken", one(WHIP, 1)), "Kraken dropped 1 x Abyssal whip")));
		held.put(GroupEvent.Category.DEATHS, new ArrayList<>());
		held.put(GroupEvent.Category.GROUP, Collections.singletonList(Digest.Entry.of(
			GroupEvent.of(GroupEvent.Type.BACK, 2, "Bob").build(), "is back")));
		List<DigestPost.Section> sections = DigestPost.sections(held, LOOKUP, false);
		List<String> posts = DigestPost.payloads(gson, sections, DiscordStyle.EMBED, "Varrock Herald", "the footer",
			LocalDateTime.of(2026, 11, 24, 20, 0).toInstant(ZoneOffset.UTC).toEpochMilli());
		assertEquals(1, posts.size());
		JsonObject o = gson.fromJson(posts.get(0), JsonObject.class);
		assertEquals(0, o.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").size());
		JsonArray embeds = o.getAsJsonArray("embeds");
		assertEquals(2, embeds.size());
		JsonObject drops = embeds.get(0).getAsJsonObject();
		assertEquals("💰 Drops", drops.get("title").getAsString());
		assertEquals(GroupEvent.Category.DROPS.colour, drops.get("color").getAsInt());
		assertEquals("1 event · 1.5M gp", drops.get("description").getAsString());
		assertNotNull(drops.getAsJsonObject("thumbnail"));
		JsonObject field = drops.getAsJsonArray("fields").get(0).getAsJsonObject();
		assertEquals("Alex", field.get("name").getAsString());
		assertEquals("Kraken dropped 1 x Abyssal whip", field.get("value").getAsString());
		assertFalse("the footer goes on the last embed only", drops.has("footer"));
		assertEquals("the footer", embeds.get(1).getAsJsonObject().getAsJsonObject("footer").get("text")
			.getAsString());
		assertEquals("2026-11-24T20:00:00Z", drops.get("timestamp").getAsString());
	}

	@Test
	public void theFooterSaysTheRangeAndTheNextDigest()
	{
		long from = LocalDateTime.of(2026, 11, 23, 20, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
		long to = LocalDateTime.of(2026, 11, 24, 20, 5).toInstant(ZoneOffset.UTC).toEpochMilli();
		long next = Digest.nextDaily(to, 20, ZoneOffset.UTC);
		assertEquals("Daily digest · Nov 23, 20:00 to Nov 24, 20:05 · next Nov 25, 20:00",
			DigestPost.footer(from, to, next, ZoneOffset.UTC));
	}

	/** A day long enough to need every kind of split: long lines, many members, many sections. */
	private Map<GroupEvent.Category, List<Digest.Entry>> aHugeDay()
	{
		Map<GroupEvent.Category, List<Digest.Entry>> held = new EnumMap<>(GroupEvent.Category.class);
		for (GroupEvent.Category c : GroupEvent.Category.values())
		{
			if (c == GroupEvent.Category.STORAGE || c == GroupEvent.Category.PROGRESS)
			{
				continue;
			}
			List<Digest.Entry> entries = new ArrayList<>();
			for (int i = 0; i < Digest.MAX_LINES; i++)
			{
				StringBuilder line = new StringBuilder("event " + i + ":");
				for (int w = 0; w < 60 + i * 3; w++)
				{
					line.append(" word").append(i).append('_').append(w);
				}
				entries.add(Digest.Entry.of(GroupEvent.of(GroupEvent.Type.ANNOUNCEMENT, i, "Member" + (i / 10))
					.text("x").build(), line.toString()));
			}
			held.put(c, entries);
		}
		return held;
	}

	@Test
	public void aHugeDayKeepsEveryDiscordLimitAndNeverCutsAWord()
	{
		Map<GroupEvent.Category, List<Digest.Entry>> held = aHugeDay();
		List<String> expected = new ArrayList<>();
		for (List<Digest.Entry> entries : held.values())
		{
			for (Digest.Entry e : entries)
			{
				expected.addAll(Arrays.asList(("E" + e.line.substring(1)).split(" ")));
			}
		}
		List<DigestPost.Section> sections = DigestPost.sections(held, LOOKUP, false);
		List<String> posts = DigestPost.payloads(gson, sections, DiscordStyle.EMBED, null, "footer text", 1L);
		assertTrue("a day this long takes several posts", posts.size() > 1);
		List<String> words = new ArrayList<>();
		for (String post : posts)
		{
			JsonArray embeds = gson.fromJson(post, JsonObject.class).getAsJsonArray("embeds");
			assertTrue(embeds.size() <= DigestPost.EMBEDS);
			int total = 0;
			for (JsonElement el : embeds)
			{
				JsonObject embed = el.getAsJsonObject();
				int size = embed.get("title").getAsString().length();
				size += embed.has("description") ? embed.get("description").getAsString().length() : 0;
				size += embed.has("footer") ? embed.getAsJsonObject("footer").get("text").getAsString().length() : 0;
				JsonArray fields = embed.getAsJsonArray("fields");
				assertTrue(fields.size() <= DigestPost.FIELDS);
				for (JsonElement f : fields)
				{
					String name = f.getAsJsonObject().get("name").getAsString();
					String value = f.getAsJsonObject().get("value").getAsString();
					assertTrue(value.length() <= DigestPost.FIELD_VALUE);
					assertTrue(name.length() <= DigestPost.FIELD_NAME);
					size += name.length() + value.length();
					words.addAll(Arrays.asList(value.split("[ \n]")));
				}
				total += size;
			}
			assertTrue("message of " + total, total <= DigestPost.MESSAGE_CHARS);
		}
		assertEquals("every word, whole and in order", expected, words);
	}

	@Test
	public void plainStyleKeepsUnderTwoThousandAndNeverCutsAWord()
	{
		Map<GroupEvent.Category, List<Digest.Entry>> held = aHugeDay();
		List<String> posts = DigestPost.payloads(gson, DigestPost.sections(held, LOOKUP, false), DiscordStyle.PLAIN,
			null, "footer text", 1L);
		StringBuilder all = new StringBuilder();
		for (String post : posts)
		{
			String content = gson.fromJson(post, JsonObject.class).get("content").getAsString();
			assertTrue(content.length() <= DigestPost.CONTENT);
			all.append(all.length() == 0 ? "" : "\n").append(content);
		}
		assertTrue("the last word of the longest line is there", all.toString().contains("word49_206"));
		assertTrue(all.toString().endsWith("footer text"));
		for (String word : all.toString().split("[ \n]"))
		{
			assertFalse(word, word.startsWith("wor") && !word.matches("word\\d+_\\d+"));
		}
	}

	@Test
	public void wrapBreaksOnlyBetweenWords()
	{
		assertEquals(Arrays.asList("aaa bbb", "ccc"), DigestPost.wrap("aaa bbb ccc", 8));
		assertEquals(Collections.singletonList("short"), DigestPost.wrap("short", 8));
		assertEquals("a single word longer than the limit is the only cut", Arrays.asList("abcdefgh", "ij"),
			DigestPost.wrap("abcdefghij", 8));
	}
}
