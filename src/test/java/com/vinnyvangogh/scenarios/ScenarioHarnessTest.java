package com.vinnyvangogh.scenarios;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.party.events.UserPart;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** The harness itself: parsing, refusing what it does not know, firing in order, and checking outputs. */
public class ScenarioHarnessTest
{
	private final EventKinds kinds = EventKinds.core().constant("BANK", 12).constant("SHARK", 385);

	private Scenario parse(String json)
	{
		return ScenarioParser.parse(json, "test.json", kinds);
	}

	private static String refusal(Runnable r)
	{
		try
		{
			r.run();
		}
		catch (ScenarioException e)
		{
			return e.getMessage();
		}
		fail("expected a ScenarioException");
		return null;
	}

	@Test
	public void parsesNameDescriptionStepsWaitsAndExpectations()
	{
		Scenario s = parse("{\"name\":\"Visit\",\"description\":\"Open and close\",\"steps\":["
			+ "{\"kind\":\"widgetLoaded\",\"group\":\"BANK\"},"
			+ "{\"kind\":\"widgetClosed\",\"group\":12,\"wait\":600,\"note\":\"close it\"}],"
			+ "\"expect\":[{\"probe\":\"log\",\"contains\":\"closed\"},{\"probe\":\"log\",\"count\":1}]}");
		assertEquals("Visit", s.name);
		assertEquals("Open and close", s.description);
		assertEquals(2, s.steps.size());
		assertEquals("widgetLoaded", s.steps.get(0).kind);
		assertEquals(0, s.steps.get(0).waitMs);
		assertEquals(600, s.steps.get(1).waitMs);
		assertEquals("close it", s.steps.get(1).note);
		assertEquals(2, s.expect.size());
		assertEquals("log", s.expect.get(0).probe);
	}

	@Test
	public void anUnknownKindIsRefusedNamingTheFileTheStepAndTheKnownKinds()
	{
		String msg = refusal(() -> parse("{\"name\":\"x\",\"steps\":[{\"kind\":\"chat\",\"type\":\"GAMEMESSAGE\","
			+ "\"message\":\"hi\"},{\"kind\":\"teleport\"}]}"));
		assertTrue(msg, msg.contains("test.json"));
		assertTrue(msg, msg.contains("step 2"));
		assertTrue(msg, msg.contains("unknown kind 'teleport'"));
		assertTrue("lists what is known: " + msg, msg.contains("stat") && msg.contains("widgetLoaded"));
	}

	@Test
	public void aMissingOrWrongFieldIsRefusedAtParseNotHalfWayThroughARun()
	{
		String missing = refusal(() -> parse("{\"name\":\"x\",\"steps\":[{\"kind\":\"stat\",\"skill\":\"PRAYER\"}]}"));
		assertTrue(missing, missing.contains("step 1 (stat)") && missing.contains("missing field 'level'"));

		String badEnum = refusal(() -> parse("{\"name\":\"x\",\"steps\":[{\"kind\":\"stat\",\"skill\":\"PRAYR\","
			+ "\"level\":5}]}"));
		assertTrue(badEnum, badEnum.contains("'PRAYR'") && badEnum.contains("PRAYER"));

		String badConstant = refusal(() -> parse("{\"name\":\"x\",\"steps\":[{\"kind\":\"widgetLoaded\","
			+ "\"group\":\"NOPE\"}]}"));
		assertTrue(badConstant, badConstant.contains("unknown constant 'NOPE'") && badConstant.contains("BANK"));
	}

	@Test
	public void aScenarioNeedsANameAndAtLeastOneStep()
	{
		assertTrue(refusal(() -> parse("{\"steps\":[{\"kind\":\"tick\"}]}")).contains("'name'"));
		assertTrue(refusal(() -> parse("{\"name\":\"x\",\"steps\":[]}")).contains("no steps"));
		assertTrue(refusal(() -> parse("{\"name\":\"x\",\"steps\":[{\"type\":\"chat\"}]}")).contains("no 'kind'"));
		assertTrue(refusal(() -> parse("not json")).contains("test.json"));
	}

	@Test
	public void anExpectationNeedsAProbeAndOneCheck()
	{
		String msg = refusal(() -> parse("{\"name\":\"x\",\"steps\":[{\"kind\":\"tick\"}],"
			+ "\"expect\":[{\"probe\":\"log\"}]}"));
		assertTrue(msg, msg.contains("expect 1") && msg.contains("contains, absent or count"));
	}

	@Test
	public void stepsFireInOrderAndEachWaitComesBeforeItsStep()
	{
		Scenario s = parse("{\"name\":\"x\",\"steps\":["
			+ "{\"kind\":\"chat\",\"type\":\"GAMEMESSAGE\",\"message\":\"one\"},"
			+ "{\"kind\":\"chat\",\"type\":\"GAMEMESSAGE\",\"message\":\"two\",\"wait\":1000},"
			+ "{\"kind\":\"chat\",\"type\":\"GAMEMESSAGE\",\"message\":\"three\",\"wait\":5}]}");
		List<String> seen = new ArrayList<>();
		ScenarioRun run = new ScenarioRun(s, kinds, e -> seen.add("post " + ((ChatMessage) e).getMessage()));
		run.runAll(ms -> seen.add("wait " + ms));
		assertEquals(Arrays.asList("post one", "wait 1000", "post two", "wait 5", "post three"), seen);
		assertFalse(run.hasNext());
	}

	@Test
	public void stepByStepReportsWhatFiredAndStopsAtTheEnd()
	{
		Scenario s = parse("{\"name\":\"x\",\"steps\":[{\"kind\":\"tick\"},{\"kind\":\"widgetClosed\","
			+ "\"group\":\"BANK\",\"wait\":600}]}");
		List<Object> posted = new ArrayList<>();
		ScenarioRun run = new ScenarioRun(s, kinds, posted::add);
		assertEquals(0, run.nextWait());
		ScenarioRun.Fired first = run.step();
		assertEquals(1, first.number);
		assertEquals("tick", first.step.kind);
		assertEquals(600, run.nextWait());
		ScenarioRun.Fired second = run.step();
		assertSame(posted.get(1), second.event);
		assertEquals(12, ((WidgetClosed) second.event).getGroupId());
		assertNull("nothing left", run.step());
	}

	@Test
	public void aHeadlessOnlyStepIsSkippedLiveAndSaysWhy()
	{
		Scenario s = parse("{\"name\":\"x\",\"steps\":[{\"kind\":\"tick\"},{\"kind\":\"chat\","
			+ "\"type\":\"GAMEMESSAGE\",\"message\":\"hi\"}]}");
		List<Object> posted = new ArrayList<>();
		ScenarioRun run = new ScenarioRun(s, kinds, posted::add).live(true);
		ScenarioRun.Fired tick = run.step();
		assertNull(tick.event);
		assertTrue(tick.skipped, tick.skipped.contains("headless"));
		run.step();
		assertEquals(1, posted.size());
		assertTrue(posted.get(0) instanceof ChatMessage);
	}

	@Test
	public void coreKindsBuildTheRuneLiteEventsTheirFieldsDescribe()
	{
		Scenario s = parse("{\"name\":\"x\",\"steps\":["
			+ "{\"kind\":\"chat\",\"type\":\"CLAN_GIM_MESSAGE\",\"name\":\"Alex\",\"message\":\"hello\"},"
			+ "{\"kind\":\"stat\",\"skill\":\"PRAYER\",\"level\":44,\"xp\":50000},"
			+ "{\"kind\":\"container\",\"id\":93,\"items\":{\"SHARK\":3,\"995\":100}},"
			+ "{\"kind\":\"varbit\",\"varbit\":4070,\"value\":2},"
			+ "{\"kind\":\"varp\",\"varp\":101,\"value\":7},"
			+ "{\"kind\":\"gameState\",\"state\":\"LOGIN_SCREEN\"},"
			+ "{\"kind\":\"userPart\",\"memberId\":42}]}");
		List<Object> posted = new ArrayList<>();
		new ScenarioRun(s, kinds, posted::add).runAll(ms ->
		{
		});

		ChatMessage chat = (ChatMessage) posted.get(0);
		assertEquals(ChatMessageType.CLAN_GIM_MESSAGE, chat.getType());
		assertEquals("Alex", chat.getName());
		assertEquals("hello", chat.getMessage());

		StatChanged stat = (StatChanged) posted.get(1);
		assertEquals(Skill.PRAYER, stat.getSkill());
		assertEquals(44, stat.getLevel());
		assertEquals("boosted defaults to the level", 44, stat.getBoostedLevel());
		assertEquals(50000, stat.getXp());

		ItemContainerChanged inv = (ItemContainerChanged) posted.get(2);
		assertEquals(93, inv.getContainerId());
		assertEquals(3, inv.getItemContainer().count(385));
		assertEquals(100, inv.getItemContainer().count(995));
		assertEquals(Arrays.asList(new Item(385, 3), new Item(995, 100)),
			Arrays.asList(inv.getItemContainer().getItems()));

		VarbitChanged varbit = (VarbitChanged) posted.get(3);
		assertEquals(4070, varbit.getVarbitId());
		assertEquals(-1, varbit.getVarpId());
		assertEquals(2, varbit.getValue());
		assertEquals(101, ((VarbitChanged) posted.get(4)).getVarpId());

		assertEquals(GameState.LOGIN_SCREEN, ((GameStateChanged) posted.get(5)).getGameState());
		assertEquals(42, ((UserPart) posted.get(6)).getMemberId());
	}

	@Test
	public void widgetEventsCarryTheirGroup()
	{
		Scenario s = parse("{\"name\":\"x\",\"steps\":[{\"kind\":\"widgetLoaded\",\"group\":\"BANK\"}]}");
		List<Object> posted = new ArrayList<>();
		new ScenarioRun(s, kinds, posted::add).step();
		assertEquals(12, ((WidgetLoaded) posted.get(0)).getGroupId());
	}

	@Test
	public void theFakeContainerAnswersLikeTheGamesOne()
	{
		FakeItemContainer c = new FakeItemContainer(93, Arrays.asList(new Item(385, 3), new Item(995, 100)));
		assertEquals(93, c.getId());
		assertEquals(2, c.size());
		assertEquals(2, c.count());
		assertTrue(c.contains(995));
		assertFalse(c.contains(1));
		assertEquals(1, c.find(995));
		assertEquals(-1, c.find(1));
		assertEquals(new Item(385, 3), c.getItem(0));
		assertNull(c.getItem(5));
		assertEquals(0, new FakeItemContainer(1, Collections.emptyList()).count());
	}

	@Test
	public void expectationsPassAndFailWithMessagesThatSayWhatWasSeen()
	{
		Scenario s = parse("{\"name\":\"x\",\"steps\":[{\"kind\":\"tick\"}],\"expect\":["
			+ "{\"probe\":\"log\",\"contains\":\"deposited 5\"},"
			+ "{\"probe\":\"log\",\"absent\":\"took\"},"
			+ "{\"probe\":\"log\",\"count\":3}]}");
		Probes probes = new Probes().add("log", () -> Arrays.asList("You deposited 5 Trout", "You took 2 Coal"));
		List<String> failures = Expectations.check(s, probes);
		assertEquals(2, failures.size());
		assertTrue(failures.get(0), failures.get(0).contains("expect 2")
			&& failures.get(0).contains("'took'") && failures.get(0).contains("You took 2 Coal"));
		assertTrue(failures.get(1), failures.get(1).contains("has 2 lines, expected count 3"));

		Probes right = new Probes().add("log", () -> Arrays.asList("You deposited 5 Trout", "Alex looked", "Sam looked"));
		assertTrue(Expectations.check(s, right).isEmpty());
	}

	@Test
	public void anUnknownProbeIsAFailureNamingTheKnownOnes()
	{
		Scenario s = parse("{\"name\":\"x\",\"steps\":[{\"kind\":\"tick\"}],\"expect\":["
			+ "{\"probe\":\"discord\",\"count\":0}]}");
		List<String> failures = Expectations.check(s, new Probes().add("log", Collections::emptyList));
		assertEquals(1, failures.size());
		assertTrue(failures.get(0), failures.get(0).contains("unknown probe 'discord'") && failures.get(0).contains("log"));
	}

	@Test
	public void aPluginKindRegistersBesideTheCoreOnesAndCannotReplaceThem()
	{
		EventKinds mine = EventKinds.core().add("hello", f -> "hello " + f.string("who"));
		Scenario s = ScenarioParser.parse("{\"name\":\"x\",\"steps\":[{\"kind\":\"hello\",\"who\":\"Sam\"}]}",
			"t.json", mine);
		List<Object> posted = new ArrayList<>();
		new ScenarioRun(s, mine, posted::add).step();
		assertEquals("hello Sam", posted.get(0));
		assertTrue(refusal(() -> mine.add("chat", f -> "x")).contains("already registered"));
	}

	@Test
	public void scenariosLoadFromTheClasspathDirectorySortedByFile()
	{
		List<Scenario> all = ScenarioParser.loadAll("harness-fixtures", kinds);
		assertEquals(2, all.size());
		assertEquals("Alpha", all.get(0).name);
		assertEquals("Beta", all.get(1).name);
		assertEquals("a-alpha.json", all.get(0).source);
	}

	@Test
	public void aMissingScenarioDirectoryIsRefusedClearly()
	{
		assertTrue(refusal(() -> ScenarioParser.loadAll("no-such-dir", kinds)).contains("no-such-dir"));
	}
}
