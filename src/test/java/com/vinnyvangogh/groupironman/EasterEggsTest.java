package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static com.vinnyvangogh.groupironman.EventFixtures.items;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class EasterEggsTest
{
	private static final int CLUE_HARD = 2722;
	private static final int WHIP = FakeItems.WHIP;
	private static final EasterEggs.Settings ON = new EasterEggs.Settings(true, 1_000_000, 1_000_000);
	private static final EasterEggs.Settings OFF = new EasterEggs.Settings(false, 1_000_000, 1_000_000);

	private final Lookup lookup = new Lookup();
	private final Gson gson = new Gson();

	// ---- the toggle ----

	@Test
	public void toggleOffMeansNoLine()
	{
		assertNull(EasterEggs.line(pet(), lookup, OFF));
		assertNull(EasterEggs.line(deathValue(items(), 0), lookup, OFF));
		assertNull(EasterEggs.line(deposit(5_000_000), lookup, OFF));
	}

	@Test
	public void anEventNoRuleKnowsHasNoLine()
	{
		assertNull(EasterEggs.line(GroupEvent.of(GroupEvent.Type.LEVEL_UP, NOW, "Alex").text("Attack").number(50)
			.build(), lookup, ON));
		assertNull("a whip lost on death is not a drop", EasterEggs.line(deathValue(items(WHIP, 1), 1_500_000),
			lookup, ON));
	}

	// ---- deaths ----

	@Test
	public void aZeroGpDeathWithTheToggleOffPostsNoValueLine()
	{
		GroupEvent free = deathValue(items(), 0);
		assertNull("nothing to say, nothing posted", DiscordWebhook.words(free, lookup, null));
		assertNull(DiscordWebhook.payload(gson, free, lookup, DiscordStyle.EMBED, "", false, null));
		assertNull(DiscordWebhook.payload(gson, free, lookup, DiscordStyle.PLAIN, "", false, null));
		assertNull("the old entry point too", DiscordWebhook.payload(gson, free, lookup, DiscordStyle.EMBED, "",
			false));
	}

	@Test
	public void aZeroGpDeathWithTheToggleOnPostsOnlyTheEgg()
	{
		GroupEvent free = deathValue(items(), 0);
		String egg = EasterEggs.line(free, lookup, ON);
		assertTrue(egg, Arrays.asList(EasterEggLines.FREE_TRIP).contains(egg));
		JsonObject embed = embed(DiscordWebhook.payload(gson, free, lookup, DiscordStyle.EMBED, "", false, egg));
		assertEquals(egg, embed.get("description").getAsString());
		assertFalse("no value field", embed.has("fields") && embed.toString().contains("\"Value\""));
		assertFalse(embed.toString().contains("0 gp"));
	}

	@Test
	public void aMergedDeathDropsTheZeroGpLineAndTakesTheEgg()
	{
		GroupEvent died = GroupEvent.of(GroupEvent.Type.DEATH, NOW, "Alex").text("Goblin").location("Lumbridge")
			.build();
		List<GroupEvent> moment = Arrays.asList(died, deathValue(items(), 0));
		DiscordMerge.Post post = DiscordMerge.merge(moment,
			EventFixtures.settings(EventFixtures.all(), EventFixtures.none()), lookup);
		assertEquals(died, post.lead);
		assertTrue("no 'lost about 0 gp' field", post.extras.isEmpty());
		String egg = EasterEggs.line(moment, lookup, ON);
		assertTrue(egg, Arrays.asList(EasterEggLines.FREE_TRIP).contains(egg));
		assertNull(EasterEggs.line(moment, lookup, OFF));
		JsonObject embed = embed(DiscordWebhook.payload(gson, post, lookup, DiscordStyle.EMBED, "", false, egg));
		assertEquals("died at Lumbridge, killed by Goblin\n" + egg, embed.get("description").getAsString());
		String plain = new JsonParser().parse(DiscordWebhook.payload(gson, post, lookup, DiscordStyle.PLAIN, "",
			false, egg)).getAsJsonObject().get("content").getAsString();
		assertEquals("Alex: died at Lumbridge, killed by Goblin\n" + egg, plain);
	}

	@Test
	public void aDeathThatCostSomethingKeepsItsValueLine()
	{
		String words = DiscordWebhook.words(deathValue(items(WHIP, 1), 1_500_000), lookup, null);
		assertTrue(words, words.startsWith("lost about 1.5M gp on death"));
	}

	@Test
	public void aSpadeOnlyDeathBlamesTheClueHunters()
	{
		assertEquals(EasterEggLines.CLUE_HUNTERS[0], EasterEggs.line(deathValue(items(ItemID.SPADE, 1), 3),
			lookup, ON));
		assertEquals("spade and a clue", EasterEggLines.CLUE_HUNTERS[0],
			EasterEggs.line(deathValue(items(ItemID.SPADE, 1, CLUE_HARD, 1), 3), lookup, ON));
		assertEquals("a spade worth nothing is still a spade", EasterEggLines.CLUE_HUNTERS[0],
			EasterEggs.line(deathValue(items(ItemID.SPADE, 1), 0), lookup, ON));
	}

	@Test
	public void aSpadeWithSomethingElseIsNotAClueDeath()
	{
		assertNull(EasterEggs.line(deathValue(items(ItemID.SPADE, 1, WHIP, 1), 1_500_003), lookup, ON));
		assertNull("a clue without the spade", EasterEggs.line(deathValue(items(CLUE_HARD, 1), 5), lookup, ON));
	}

	// ---- drops ----

	@Test
	public void aTableItemInADropGetsItsLine()
	{
		GroupEvent tbow = GroupEvent.of(GroupEvent.Type.DROP, NOW, "Alex").text("Great Olm")
			.items(items(ItemID.TWISTED_BOW, 1, ItemID.BONES, 1)).build();
		String egg = EasterEggs.line(tbow, lookup, ON);
		assertTrue(egg, rowFor(ItemID.TWISTED_BOW).contains(egg));
	}

	@Test
	public void aRaidUniqueMatchesTheTableByName()
	{
		GroupEvent purple = GroupEvent.of(GroupEvent.Type.RAID_UNIQUE, NOW, "Alex").text("Twisted bow").build();
		String egg = EasterEggs.line(purple, lookup, ON);
		assertTrue(egg, rowFor(ItemID.TWISTED_BOW).contains(egg));
	}

	@Test
	public void aPetSaysYouCanStop()
	{
		String egg = EasterEggs.line(pet(), lookup, ON);
		assertTrue(egg, Arrays.asList(EasterEggLines.PET).contains(egg));
	}

	@Test
	public void aBonesOnlyValuableDropIsCalledOut()
	{
		GroupEvent bones = GroupEvent.drop(NOW, "Alex", "Goblin", items(ItemID.BONES, 1));
		assertTrue(Arrays.asList(EasterEggLines.BONES_ONLY).contains(EasterEggs.line(bones, lookup, ON)));
	}

	// ---- group storage ----

	@Test
	public void aDepositAtTheLegendValueIsLegendary()
	{
		assertLegend(EasterEggs.line(deposit(1_000_000), lookup, ON));
		assertNull("just under", EasterEggs.line(deposit(999_999), lookup, ON));
	}

	@Test
	public void aWithdrawalAtTheFreeloaderValueIsDetected()
	{
		assertFreeloader(EasterEggs.line(withdrawal(1_000_001), lookup, ON));
		assertFreeloader(EasterEggs.line(withdrawal(1_000_000), lookup, ON));
		assertNull("just under", EasterEggs.line(withdrawal(999_999), lookup, ON));
	}

	/** The Board's live test: Legend and Freeloader both at 10,000, 10K coins in and then out. */
	@Test
	public void tenThousandInAndOutAtTenThousandGetsBothEggs()
	{
		EasterEggs.Settings tenK = new EasterEggs.Settings(true, 10_000, 10_000);
		assertLegend(EasterEggs.line(deposit(10_000), lookup, tenK));
		assertFreeloader(EasterEggs.line(withdrawal(10_000), lookup, tenK));
		assertNull(EasterEggs.line(deposit(9_999), lookup, tenK));
		assertNull(EasterEggs.line(withdrawal(9_999), lookup, tenK));
	}

	@Test
	public void aVisitSummaryUsesBothSides()
	{
		GroupEvent took = GroupEvent.of(GroupEvent.Type.STORAGE_SUMMARY, NOW, "Alex").items(coins(10))
			.taken(coins(2_000_000)).build();
		assertFreeloader(EasterEggs.line(took, lookup, ON));
		GroupEvent gave = GroupEvent.of(GroupEvent.Type.STORAGE_SUMMARY, NOW, "Alex").items(coins(3_000_000))
			.taken(coins(2_000_000)).build();
		assertLegend("gave more than took", EasterEggs.line(gave, lookup, ON));
		GroupEvent atButGaveMore = GroupEvent.of(GroupEvent.Type.STORAGE_SUMMARY, NOW, "Alex")
			.items(coins(1_500_000)).taken(coins(1_000_000)).build();
		assertLegend("took exactly the value but gave more back", EasterEggs.line(atButGaveMore, lookup, ON));
	}

	@Test
	public void aZeroThresholdTurnsThatEggOff()
	{
		EasterEggs.Settings zero = new EasterEggs.Settings(true, 0, 0);
		assertNull(EasterEggs.line(deposit(50_000_000), lookup, zero));
		assertNull(EasterEggs.line(withdrawal(50_000_000), lookup, zero));
	}

	// ---- the pick ----

	@Test
	public void aFixedSeedPicksTheSamePhrasing()
	{
		GroupEvent free = deathValue(items(), 0);
		String first = EasterEggs.line(free, lookup, ON, 42L);
		for (int i = 0; i < 5; i++)
		{
			assertEquals(first, EasterEggs.line(free, lookup, ON, 42L));
		}
		assertEquals("the event's own seed by default", EasterEggs.line(free, lookup, ON, EasterEggs.seed(free)),
			EasterEggs.line(free, lookup, ON));
		Set<String> seen = new HashSet<>();
		for (long seed = 0; seed < 64; seed++)
		{
			seen.add(EasterEggs.line(free, lookup, ON, seed));
		}
		assertEquals("every phrasing gets a turn", new HashSet<>(Arrays.asList(EasterEggLines.FREE_TRIP)), seen);
	}

	@Test
	public void legendAndFreeloaderRotateStablyWithAFixedSeed()
	{
		assertRotates(deposit(1_000_000), EasterEggLines.LEGEND);
		assertRotates(withdrawal(1_000_000), EasterEggLines.FREELOADER);
		assertTrue("several phrasings each", EasterEggLines.LEGEND.length >= 4
			&& EasterEggLines.FREELOADER.length >= 4);
	}

	@Test
	public void anEmojiLineSurvivesTheEmbedAndPlainPayloads()
	{
		int checked = 0;
		for (String egg : EasterEggLines.all())
		{
			if (egg.codePoints().noneMatch(Character::isSupplementaryCodePoint))
			{
				continue;
			}
			checked++;
			GroupEvent in = deposit(1_000_000);
			JsonObject embed = embed(DiscordWebhook.payload(gson, in, lookup, DiscordStyle.EMBED, "", false, egg));
			assertTrue(embed.get("description").getAsString(), embed.get("description").getAsString().endsWith(egg));
			String plain = new JsonParser().parse(DiscordWebhook.payload(gson, in, lookup, DiscordStyle.PLAIN, "",
				false, egg)).getAsJsonObject().get("content").getAsString();
			assertTrue(plain, plain.endsWith(egg));
			assertTrue("an emoji is a whole surrogate pair", egg.codePoints()
				.noneMatch(c -> Character.getType(c) == Character.SURROGATE));
		}
		assertTrue("the Legend and Freeloader emoji lines are checked", checked >= 2);
	}

	// ---- the post keeps its facts ----

	@Test
	public void theEggGoesUnderTheFactsNotInPlaceOfThem()
	{
		GroupEvent drop = GroupEvent.of(GroupEvent.Type.DROP, NOW, "Alex").text("Vorkath").items(items(WHIP, 1))
			.number(120).build();
		JsonObject embed = embed(DiscordWebhook.payload(gson, drop, lookup, DiscordStyle.EMBED, "", false, "Bonk."));
		assertEquals("Vorkath dropped 1 x Abyssal whip, kill count 120\nBonk.", embed.get("description").getAsString());
		assertEquals("1.5M (1,500,000) gp", embed.getAsJsonArray("fields").get(0).getAsJsonObject().get("value").getAsString());
		JsonObject plain = new JsonParser().parse(DiscordWebhook.payload(gson, drop, lookup, DiscordStyle.PLAIN, "",
			false, "Bonk.")).getAsJsonObject();
		assertEquals("Alex: Vorkath dropped 1 x Abyssal whip, kill count 120\nBonk.", plain.get("content").getAsString());
	}

	@Test
	public void everyLineIsShortAndKind()
	{
		List<String> banned = Arrays.asList("suicide", "kill yourself", "kys", "self-harm", "self harm", "die irl");
		for (String line : EasterEggLines.all())
		{
			assertFalse("empty line", line.trim().isEmpty());
			assertTrue(line, line.length() <= 80);
			for (String word : banned)
			{
				assertFalse(line, line.toLowerCase(Locale.ROOT).contains(word));
			}
		}
		assertTrue("about ten items to start", EasterEggLines.ITEMS.size() >= 10);
	}

	// ---- fixtures ----

	private void assertRotates(GroupEvent e, String[] lines)
	{
		String first = EasterEggs.line(e, lookup, ON, 42L);
		for (int i = 0; i < 5; i++)
		{
			assertEquals(first, EasterEggs.line(e, lookup, ON, 42L));
		}
		Set<String> seen = new HashSet<>();
		for (long seed = 0; seed < 64; seed++)
		{
			seen.add(EasterEggs.line(e, lookup, ON, seed));
		}
		assertEquals("every phrasing gets a turn", new HashSet<>(Arrays.asList(lines)), seen);
	}

	private static void assertLegend(String egg)
	{
		assertLegend(null, egg);
	}

	private static void assertLegend(String why, String egg)
	{
		assertTrue(why + ": " + egg, Arrays.asList(EasterEggLines.LEGEND).contains(egg));
	}

	private static void assertFreeloader(String egg)
	{
		assertTrue(String.valueOf(egg), Arrays.asList(EasterEggLines.FREELOADER).contains(egg));
	}

	private static List<String> rowFor(int id)
	{
		String[] lines = EasterEggLines.ITEMS.get(id);
		assertNotNull("no row for " + id, lines);
		return Arrays.asList(lines);
	}

	private static Map<Integer, Integer> coins(int gp)
	{
		return items(ItemID.COINS, gp);
	}

	private static GroupEvent pet()
	{
		return GroupEvent.of(GroupEvent.Type.PET, NOW, "Alex").build();
	}

	private static GroupEvent deathValue(Map<Integer, Integer> lost, long value)
	{
		return GroupEvent.of(GroupEvent.Type.DEATH_VALUE, NOW, "Alex").items(lost).number(value)
			.location("Lumbridge").detail("gravestone: 15 min").build();
	}

	private static GroupEvent deposit(int gp)
	{
		return GroupEvent.of(GroupEvent.Type.STORAGE_DEPOSIT, NOW, "Alex").items(coins(gp)).build();
	}

	private static GroupEvent withdrawal(int gp)
	{
		return GroupEvent.of(GroupEvent.Type.STORAGE_WITHDRAWAL, NOW, "Alex").items(coins(gp)).build();
	}

	private static JsonObject embed(String json)
	{
		assertNotNull(json);
		return new JsonParser().parse(json).getAsJsonObject().getAsJsonArray("embeds").get(0).getAsJsonObject();
	}

	/** The ids the eggs key on, from the one table that holds them. */
	private static final class ItemID
	{
		static final int SPADE = EasterEggLines.SPADE;
		static final int BONES = EasterEggLines.BONES;
		static final int TWISTED_BOW = EasterEggLines.TWISTED_BOW;
		static final int COINS = 995;
	}

	/** FakeItems plus the items the eggs look at. */
	private static final class Lookup implements ItemLookup
	{
		private final FakeItems base = new FakeItems();
		private final Map<Integer, String> names = new HashMap<>();
		private final Map<Integer, Long> prices = new HashMap<>();

		Lookup()
		{
			add(ItemID.SPADE, "Spade", 3);
			add(CLUE_HARD, "Clue scroll (hard)", 0);
			add(ItemID.BONES, "Bones", 100);
			add(ItemID.TWISTED_BOW, "Twisted bow", 1_500_000_000L);
			add(ItemID.COINS, "Coins", 1);
		}

		private void add(int id, String name, long price)
		{
			names.put(id, name);
			prices.put(id, price);
		}

		@Override
		public String name(int id)
		{
			return names.containsKey(id) ? names.get(id) : base.name(id);
		}

		@Override
		public long price(int id)
		{
			return prices.containsKey(id) ? prices.get(id) : base.price(id);
		}
	}
}
