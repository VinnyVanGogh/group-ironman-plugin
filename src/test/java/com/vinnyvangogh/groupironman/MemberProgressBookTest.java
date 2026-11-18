package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The per-member store: hostile input first, then first-sight versus beaten, then send-on-change. */
public class MemberProgressBookTest
{
	private final Gson gson = new Gson();
	private final List<String> sent = new ArrayList<>();
	private final MemberProgressBook book = new MemberProgressBook(gson, sent::add);

	private static PersonalBests pbs(String boss, double secs)
	{
		Map<String, Double> m = new HashMap<>();
		m.put(boss, secs);
		return PersonalBests.of(m);
	}

	private String wire(PersonalBests p)
	{
		return MemberProgressCodec.encode(gson, p);
	}

	@Test
	public void unreadablePayloadsReturnNull()
	{
		assertNull(book.receive("GIM1", "not json"));
		assertNull(book.receive("GIM1", "{\"v\":1,\"k\":\"pbs\",\"b\":5}"));
		assertNull(book.receive(null, wire(pbs("Zulrah", 60))));
	}

	@Test
	public void memberCountIsCapped()
	{
		for (int i = 0; i < MemberProgressBook.MAX_MEMBERS + 20; i++)
		{
			book.receive("M" + i, wire(pbs("Zulrah", 60)));
		}
		assertEquals(MemberProgressBook.MAX_MEMBERS, book.allBests().size());
		// A known member can still update at the cap.
		assertNotNull(book.receive("M0", wire(pbs("Zulrah", 50))));
	}

	@Test
	public void firstSightIsNotBeatenButFasterTimeIs()
	{
		assertTrue(book.receive("GIM1", wire(pbs("Zulrah", 60))).isEmpty());
		List<PersonalBests.Beaten> b = book.receive("GIM1", wire(pbs("Zulrah", 55)));
		assertEquals(1, b.size());
		assertEquals("Zulrah", b.get(0).boss);
		assertEquals(55.0, book.bests("GIM1").all().get("Zulrah"), 0.0);
	}

	@Test
	public void achievementsStored()
	{
		AchievementState a = AchievementState.of(12, Collections.singleton("Cook's Assistant"),
			Collections.<String>emptyList(), Collections.<String, Integer>emptyMap());
		assertTrue(book.receive("GIM2", MemberProgressCodec.encode(gson, a)).isEmpty());
		assertEquals(12, book.achievements("GIM2").questPoints());
		assertNull(book.achievements("nobody"));
	}

	@Test
	public void publishSendsOnlyWhatChanged()
	{
		PersonalBests p = pbs("Zulrah", 60);
		book.publish(p, null);
		book.publish(p, null);
		assertEquals(1, sent.size());
		book.publish(pbs("Zulrah", 59), null);
		assertEquals(2, sent.size());
		book.resendAll();
		book.publish(pbs("Zulrah", 59), null);
		assertEquals(3, sent.size());
	}

	@Test
	public void forgetDropsAMember()
	{
		book.receive("GIM1", wire(pbs("Zulrah", 60)));
		book.forget("GIM1");
		assertTrue(book.bests("GIM1").all().isEmpty());
	}
}
