package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.GameState;
import net.runelite.api.gameval.VarbitID;
import org.junit.Before;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Board 2026-11-15: switching accounts posted the other account's long-done diaries (Karamja medium, Lumbridge
 * easy, Varrock easy, Ardougne easy) and a "Total level 700" with three level-ups. Each test drives the engine's
 * order of events (game states, StatChanged, ticks with their variable polls) through the real tracker and
 * watchers, as {@link GroupEventEngine} wires them: nothing earned before a login ever comes out.
 */
public class LoginGateTest
{
	private static final long A = 111L;
	private static final long B = 222L;
	/** Skills enough to judge a total: the tracker is built for exactly these. */
	private static final String[] SKILLS = {"Firemaking", "Crafting", "Smithing", "Attack"};
	private static final int[] DIARIES = {VarbitID.ATJUN_MED_DONE, VarbitID.LUMBRIDGE_DIARY_EASY_COMPLETE,
		VarbitID.VARROCK_DIARY_EASY_COMPLETE, VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE};

	private final List<GroupEvent> out = new ArrayList<>();
	private final Map<Integer, Integer> vars = new HashMap<>();
	private ProgressTracker progress;
	private EventWatchers watchers;
	private LoginBurst ownBurst;
	private LoginGate gate;
	private long hash = LoginGate.NO_ACCOUNT;
	private boolean loginPending;

	@Before
	public void setUp()
	{
		progress = new ProgressTracker(SKILLS.length);
		watchers = new EventWatchers(out::add, t -> true);
		ownBurst = new LoginBurst(LoginBurst.ACHIEVEMENTS::contains);
		gate = new LoginGate(progress, watchers, ownBurst, new LoginBurst(t -> true));
	}

	// ---- The engine's handlers, in its order ----

	private void state(GameState state)
	{
		if (LoginGate.endsSession(state))
		{
			gate.startOver();
		}
		if (state == GameState.LOGGED_IN)
		{
			loginPending = true;
		}
	}

	private void stat(String skill, int level)
	{
		gate.account(hash, NOW);
		out.addAll(progress.skill(skill, level, (long) level * 1000, NOW, "Ship Wrek"));
	}

	private void tick()
	{
		gate.account(hash, NOW);
		if (loginPending)
		{
			loginPending = false;
			gate.login(NOW);
		}
		if (gate.tick())
		{
			watchers.poll(id -> vars.getOrDefault(id, 0), NOW, "Ship Wrek");
		}
	}

	private void ticks(int n)
	{
		for (int i = 0; i < n; i++)
		{
			tick();
		}
	}

	/** A logged in for a while: low on the three skills, no diaries, and every value settled. */
	private void accountAPlayed()
	{
		hash = A;
		state(GameState.LOGGING_IN);
		state(GameState.LOADING);
		state(GameState.LOGGED_IN);
		stats(60, 60, 50, 70);
		ticks(LoginGate.SETTLE_TICKS + 5);
		assertTrue(gate.settled());
		assertTrue(out.isEmpty());
	}

	private void stats(int... levels)
	{
		for (int i = 0; i < SKILLS.length; i++)
		{
			stat(SKILLS[i], levels[i]);
		}
	}

	/** B's values as the game sends them: the levels the Board's post showed, its four done diaries. */
	private void accountBArrives()
	{
		hash = B;
		stats(65, 65, 53, 70);
		for (int id : DIARIES)
		{
			vars.put(id, 1);
		}
	}

	private void assertNothingPosted()
	{
		ticks(LoginGate.SETTLE_TICKS + 5);
		assertTrue("old achievements posted: " + out, out.isEmpty());
	}

	// ---- Account switches post nothing ----

	@Test
	public void logoutThenAnotherAccountWithDoneDiariesPostsNothing()
	{
		accountAPlayed();
		state(GameState.LOGIN_SCREEN);
		hash = LoginGate.NO_ACCOUNT;
		state(GameState.LOGGING_IN);
		state(GameState.LOADING);
		state(GameState.LOGGED_IN);
		accountBArrives();
		assertNothingPosted();
	}

	@Test
	public void aWorldHopIntoAnotherAccountPostsNothing()
	{
		accountAPlayed();
		state(GameState.HOPPING);
		state(GameState.LOADING);
		state(GameState.LOGGED_IN);
		accountBArrives();
		assertNothingPosted();
	}

	@Test
	public void aLostConnectionThenAnotherAccountPostsNothing()
	{
		accountAPlayed();
		state(GameState.CONNECTION_LOST);
		state(GameState.LOGGED_IN);
		accountBArrives();
		assertNothingPosted();
	}

	@Test
	public void aSwitchNoGameStateShowedPostsNothing()
	{
		// The old engine's path: the only reset sat at the end of the LOGIN_SCREEN branch, so any switch that did
		// not reach it (or a handler that threw before it) left A's values as B's baseline.
		accountAPlayed();
		accountBArrives();
		assertNothingPosted();
	}

	@Test
	public void aSwitchWhoseStatsComeBeforeTheAccountIsKnownPostsNothing()
	{
		accountAPlayed();
		// Mid-login the client knows no account yet; B's stats are already arriving.
		hash = LoginGate.NO_ACCOUNT;
		stats(65, 65, 53, 70);
		hash = B;
		state(GameState.LOGGED_IN);
		for (int id : DIARIES)
		{
			vars.put(id, 1);
		}
		assertNothingPosted();
	}

	@Test
	public void varbitsArrivingTicksLatePostNothing()
	{
		accountAPlayed();
		state(GameState.LOGIN_SCREEN);
		hash = B;
		state(GameState.LOGGING_IN);
		state(GameState.LOGGED_IN);
		stats(65, 65, 53, 70);
		// The first polls read 0 for every diary: the game has not sent them yet.
		ticks(LoginGate.VAR_WAIT_TICKS + 3);
		for (int id : DIARIES)
		{
			vars.put(id, 1);
		}
		assertNothingPosted();
	}

	@Test
	public void statsSentTwiceAtLoginPostNothing()
	{
		hash = B;
		state(GameState.LOGGING_IN);
		state(GameState.LOGGED_IN);
		// A default value first, then the real one a moment later.
		stats(1, 1, 1, 1);
		tick();
		stats(65, 65, 53, 70);
		assertNothingPosted();
	}

	// ---- Real progress still posts ----

	@Test
	public void aDiaryCompletedMidSessionStillPosts()
	{
		accountAPlayed();
		vars.put(VarbitID.VARROCK_DIARY_EASY_COMPLETE, 1);
		tick();
		assertEquals(1, out.size());
		assertEquals(GroupEvent.Type.DIARY, out.get(0).type);
		assertEquals("Varrock easy", out.get(0).text);
	}

	@Test
	public void aLevelAndTotalMidSessionStillPost()
	{
		accountAPlayed();
		// 60 + 60 + 50 + 70 = 240; Firemaking to 120 crosses 300.
		stat("Firemaking", 120);
		Map<GroupEvent.Type, Long> kinds = new LinkedHashMap<>();
		out.forEach(e -> kinds.put(e.type, e.number));
		assertEquals(Long.valueOf(120), kinds.get(GroupEvent.Type.LEVEL_UP));
		assertEquals(Long.valueOf(300), kinds.get(GroupEvent.Type.TOTAL_LEVEL));
	}

	@Test
	public void theNewAccountsOwnProgressPostsOnceSettled()
	{
		accountAPlayed();
		state(GameState.LOGIN_SCREEN);
		hash = B;
		state(GameState.LOGGING_IN);
		state(GameState.LOGGED_IN);
		accountBArrives();
		assertNothingPosted();
		vars.put(VarbitID.FALADOR_DIARY_EASY_COMPLETE, 1);
		stat("Smithing", 54);
		tick();
		assertEquals(2, out.size());
	}

	@Test
	public void aProfileChangeMidSessionReBaselines()
	{
		accountAPlayed();
		// The RuneLite profile changed under the same account hash: its values are a new baseline.
		gate.rebaseline(NOW);
		stats(65, 65, 53, 70);
		for (int id : DIARIES)
		{
			vars.put(id, 1);
		}
		assertNothingPosted();
	}

	@Test
	public void aHopKeepsTheGraveTimerAnAccountSwitchDoesNot()
	{
		accountAPlayed();
		watchers.died(new HashMap<>(), "Lumbridge", 301);
		state(GameState.HOPPING);
		state(GameState.LOADING);
		state(GameState.LOGGED_IN);
		ticks(2);
		assertTrue("same account, same grave", watchers.graveOut());
		hash = B;
		tick();
		assertTrue("another account's grave is not this one's", !watchers.graveOut());
	}

	@Test
	public void theLoginScreenHopsAndLostConnectionsEndASessionLoadingDoesNot()
	{
		assertTrue(LoginGate.endsSession(GameState.LOGIN_SCREEN));
		assertTrue(LoginGate.endsSession(GameState.LOGGING_IN));
		assertTrue(LoginGate.endsSession(GameState.HOPPING));
		assertTrue(LoginGate.endsSession(GameState.CONNECTION_LOST));
		assertTrue(!LoginGate.endsSession(GameState.LOADING));
		assertTrue(!LoginGate.endsSession(GameState.LOGGED_IN));
	}

	@Test
	public void aLoginOpensTheBurstWindow()
	{
		accountAPlayed();
		GroupEvent diary = GroupEvent.of(GroupEvent.Type.DIARY, NOW, "Ship Wrek").text("Varrock easy").build();
		assertTrue("held in the login's first minute", ownBurst.hold(diary, NOW + 1));
	}
}
