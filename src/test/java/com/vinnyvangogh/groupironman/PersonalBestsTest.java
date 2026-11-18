package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Boss personal bests shared with the group: hostile input first, then beaten detection, then the wire. */
public class PersonalBestsTest
{
	private static PersonalBests pbs(Object... kv)
	{
		Map<String, Double> m = new HashMap<>();
		for (int i = 0; i < kv.length; i += 2)
		{
			m.put((String) kv[i], ((Number) kv[i + 1]).doubleValue());
		}
		return PersonalBests.of(m);
	}

	@Test
	public void hostileValuesAreDropped()
	{
		PersonalBests p = pbs("Zulrah", 0, "Vorkath", -5, "Nex", Double.NaN, "Hydra", Double.POSITIVE_INFINITY,
			"Cerb", PersonalBests.MAX_SECONDS + 1, "", 30, "Good", 61.2);
		assertEquals(Collections.singleton("Good"), p.all().keySet());
	}

	@Test
	public void bossCountIsCapped()
	{
		Map<String, Double> m = new HashMap<>();
		for (int i = 0; i < PersonalBests.MAX_BOSSES + 50; i++)
		{
			m.put("boss" + i, 10.0 + i);
		}
		assertEquals(PersonalBests.MAX_BOSSES, PersonalBests.of(m).all().size());
	}

	@Test
	public void beatenListsOnlyFasterTimes()
	{
		PersonalBests before = pbs("Zulrah", 70.2, "Vorkath", 90);
		PersonalBests after = pbs("Zulrah", 65.0, "Vorkath", 95, "Nex", 200);
		List<PersonalBests.Beaten> b = after.beaten(before);
		assertEquals(2, b.size());
		assertEquals("Nex", b.get(0).boss);
		assertNull(b.get(0).previous);
		assertEquals("Zulrah", b.get(1).boss);
		assertEquals(70.2, b.get(1).previous, 0.001);
		assertTrue(before.beaten(before).isEmpty());
	}

	@Test
	public void formatsMinutesAndHours()
	{
		assertEquals("0:45.6", PersonalBests.format(45.6));
		assertEquals("1:05.0", PersonalBests.format(65));
		assertEquals("1:02:03", PersonalBests.format(3723));
	}

	@Test
	public void wireRoundTripsAndSurvivesGarbage()
	{
		Gson gson = new Gson();
		PersonalBests p = pbs("Zulrah", 65.4, "Nex", 200);
		assertEquals(p, MemberProgressCodec.decodeBests(gson, MemberProgressCodec.encode(gson, p)));
		assertNull(MemberProgressCodec.decodeBests(gson, "{\"v\":1,\"k\":\"pbs\",\"b\":\"nope\"}"));
		assertNull(MemberProgressCodec.decodeBests(gson, "not json"));
		assertTrue(MemberProgressCodec.isProgress(MemberProgressCodec.encode(gson, p)));
	}
}
