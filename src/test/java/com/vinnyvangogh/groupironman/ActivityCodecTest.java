package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The live activity message and the four activity events on the wire (docs/plans/activities.md §5, §7 item 1),
 * including what a client from before this slice makes of them.
 */
public class ActivityCodecTest
{
	private static final Gson GSON = new Gson();

	static ActivityState inferno(long at)
	{
		return new ActivityState.Builder("inferno", at).name("The Inferno").mode("solo").phase("wave 42/69")
			.board("in The Inferno, wave 40").wave(42).max(69).startedAt(at - 3_600_000).elapsedMs(3_000_000)
			.build();
	}

	@Test
	public void roundTrips()
	{
		ActivityState back = ActivityCodec.decode(GSON, ActivityCodec.encode(GSON, inferno(5_000)));
		assertNotNull(back);
		assertEquals("inferno", back.id);
		assertEquals("The Inferno", back.name);
		assertEquals("wave 42/69", back.phase);
		assertEquals(42, back.wave);
		assertEquals(69, back.max);
		assertEquals(3_000_000, back.elapsedMs);
		assertEquals(5_000, back.at);
		assertTrue(back.bossing());
		assertEquals("The Inferno - wave 42/69 - solo", back.cardLine());
	}

	@Test
	public void clearFrameDecodesAsNotInAnActivity()
	{
		String json = ActivityCodec.encode(GSON, new ActivityState.Builder("", 9).build());
		assertEquals("{\"v\":1,\"k\":\"activity\",\"at\":9,\"id\":\"\"}", json);
		ActivityState back = ActivityCodec.decode(GSON, json);
		assertNotNull(back);
		assertFalse(back.active());
	}

	@Test
	public void stripsMarkupAndControlCharactersAndClipsStrings()
	{
		String json = "{\"v\":1,\"k\":\"activity\",\"at\":1,\"id\":\"inferno\",\"name\":\"<html><b>Inferno</b>\\u0007\","
			+ "\"mods\":\"" + repeat("x", 500) + "\",\"phase\":\"wave\\n1\"}";
		ActivityState back = ActivityCodec.decode(GSON, json);
		assertNotNull(back);
		assertFalse(back.name, back.name.contains("<"));
		assertFalse(back.name.contains("\u0007"));
		assertEquals(ActivityCodec.MAX_MODS, back.mods.length());
		assertEquals("wave1", back.phase);
	}

	@Test
	public void clampsNumbers()
	{
		String json = "{\"v\":1,\"k\":\"activity\",\"at\":1,\"id\":\"inferno\",\"name\":\"The Inferno\","
			+ "\"wave\":-5,\"max\":99999999,\"elapsedMs\":1e30}";
		ActivityState back = ActivityCodec.decode(GSON, json);
		assertNotNull(back);
		assertEquals(0, back.wave);
		assertEquals(ActivityCodec.MAX_WAVE, back.max);
		assertEquals(ActivityCodec.MAX_ELAPSED_MS, back.elapsedMs);
	}

	@Test
	public void rejectsMalformed()
	{
		assertNull(ActivityCodec.decode(GSON, null));
		assertNull(ActivityCodec.decode(GSON, "not json"));
		assertNull(ActivityCodec.decode(GSON, "[1,2]"));
		assertNull("no version", ActivityCodec.decode(GSON, "{\"k\":\"activity\",\"id\":\"\"}"));
		assertNull("no id", ActivityCodec.decode(GSON, "{\"v\":1,\"k\":\"activity\"}"));
		assertNull("bad id", ActivityCodec.decode(GSON, "{\"v\":1,\"k\":\"activity\",\"id\":\"../x\",\"name\":\"a\"}"));
		assertNull("no name", ActivityCodec.decode(GSON, "{\"v\":1,\"k\":\"activity\",\"id\":\"inferno\"}"));
		assertNull("another kind", ActivityCodec.decode(GSON, "{\"v\":1,\"k\":\"stats\",\"id\":\"\"}"));
		String huge = "{\"v\":1,\"k\":\"activity\",\"id\":\"inferno\",\"name\":\"" + repeat("y", ActivityCodec.MAX_CHARS + 1) + "\"}";
		assertNull("oversized", ActivityCodec.decode(GSON, huge));
	}

	@Test
	public void anUnknownCategoryIsBossing()
	{
		String json = "{\"v\":1,\"k\":\"activity\",\"at\":1,\"id\":\"tempoross\",\"name\":\"Tempoross\",\"cat\":\"x\"}";
		assertTrue(ActivityCodec.decode(GSON, json).bossing());
		String mini = "{\"v\":1,\"k\":\"activity\",\"at\":1,\"id\":\"ba\",\"name\":\"BA\",\"cat\":\"minigame\"}";
		assertFalse(ActivityCodec.decode(GSON, mini).bossing());
	}

	// ---- Older clients ----

	@Test
	public void anOlderClientsDataDecoderReturnsNullForTheActivityKind()
	{
		// GroupCodec.decode is what every client before this slice runs first; null sends it to notData, which
		// logs one debug line and drops it.
		assertNull(GroupCodec.decode(GSON, ActivityCodec.encode(GSON, inferno(1))));
		assertNull(GroupCodec.decode(GSON, ActivityCodec.encode(GSON, ActivityState.NONE)));
	}

	@Test
	public void activityEventsTravelAndAnOlderClientSkipsOnlyThem()
	{
		List<GroupEvent> events = Arrays.asList(
			GroupEvent.of(GroupEvent.Type.DEATH, 1, "Ember Fox").location("Lumbridge").build(),
			GroupEvent.of(GroupEvent.Type.ACTIVITY_WAVE, 2, "Ember Fox").text("The Inferno").number(60)
				.detail("wave 60/69").build(),
			GroupEvent.of(GroupEvent.Type.ACTIVITY_DEATH, 3, "Ember Fox").text("The Inferno").number(63).build(),
			GroupEvent.of(GroupEvent.Type.LEVEL_UP, 4, "Ember Fox").text("Attack").number(70).build());
		List<String> payloads = GroupOutbox.groupEvents(GSON, true, events);
		assertEquals(1, payloads.size());
		GroupCodec.Decoded now = GroupCodec.decode(GSON, payloads.get(0));
		assertEquals("this build reads all four", 4, now.events.size());
		assertEquals(GroupEvent.Type.ACTIVITY_WAVE, now.events.get(1).type);
		assertEquals(60, now.events.get(1).number);

		// An older build knows no "aw" or "ad": each is skipped on its own, its neighbours kept.
		String older = payloads.get(0).replace("\"aw\"", "\"zz\"").replace("\"ad\"", "\"zy\"");
		GroupCodec.Decoded old = GroupCodec.decode(GSON, older);
		assertEquals(2, old.events.size());
		assertEquals(GroupEvent.Type.DEATH, old.events.get(0).type);
		assertEquals(GroupEvent.Type.LEVEL_UP, old.events.get(1).type);
	}

	@Test
	public void anOlderMembersOptOutListSkipsTheNewCodes()
	{
		assertEquals(Collections.singleton(GroupEvent.Type.PET), MemberPrefs.parse("pt,zz"));
		assertTrue(MemberPrefs.parse("as,aw").contains(GroupEvent.Type.ACTIVITY_WAVE));
	}

	@Test
	public void isActivityLooksOnlyForTheKind()
	{
		assertTrue(ActivityCodec.isActivity(ActivityCodec.encode(GSON, inferno(1))));
		assertFalse(ActivityCodec.isActivity("{\"v\":1,\"k\":\"stats\"}"));
		assertFalse(ActivityCodec.isActivity(null));
	}

	static String repeat(String s, int n)
	{
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < n; i++)
		{
			b.append(s);
		}
		return b.toString();
	}
}
