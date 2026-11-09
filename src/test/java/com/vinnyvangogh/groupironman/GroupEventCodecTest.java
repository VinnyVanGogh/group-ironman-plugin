package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static com.vinnyvangogh.groupironman.EventFixtures.sample;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Typed events on the party wire: round trip, the storage kinds stay home, a 1.0.0 payload still reads. */
public class GroupEventCodecTest
{
	private final Gson gson = new Gson();

	@Test
	public void everyTravellingKindRoundTripsWithItsFields()
	{
		List<GroupEvent> out = new ArrayList<>();
		for (GroupEvent.Type t : GroupEvent.Type.values())
		{
			out.add(sample(t).toBuilder().world(330).location("Lumbridge").detail("Dharok's 2/4").build());
		}
		List<GroupEvent> back = new ArrayList<>();
		for (String payload : GroupCodec.encodeEvents(gson, out))
		{
			back.addAll(GroupCodec.decode(gson, payload).events);
		}
		long travelling = out.stream().filter(e -> e.type.travels()).count();
		assertEquals(travelling, back.size());
		for (GroupEvent e : back)
		{
			assertTrue(e.type + " travels", e.type.travels());
			assertEquals("the receiver names the member", "", e.member);
			GroupEvent sent = out.stream().filter(o -> o.type == e.type).findFirst().orElseThrow(AssertionError::new);
			assertTrue(e.type + " same", sent.withMember("").sameAs(e));
			assertEquals(330, e.world);
			assertEquals("Lumbridge", e.location);
			assertEquals("Dharok's 2/4", e.detail);
		}
	}

	@Test
	public void aVersionOnePayloadStillReads()
	{
		String v1 = "{\"v\":1,\"k\":\"events\",\"e\":[{\"t\":\"d\",\"ts\":5,\"s\":\"Vorkath\",\"i\":[4151,1]},"
			+ "{\"t\":\"c\",\"ts\":6,\"s\":\"Draconic visage\"},{\"t\":\"zz\",\"ts\":7,\"s\":\"from the future\"}]}";
		List<GroupEvent> events = GroupCodec.decode(gson, v1).events;
		assertEquals("the unknown kind is skipped", 2, events.size());
		assertEquals(GroupEvent.Type.DROP, events.get(0).type);
		assertEquals(GroupEvent.Type.CLOG, events.get(1).type);
	}

	@Test
	public void aDropStillLooksAsOneDotZeroSentIt()
	{
		String json = GroupCodec.encodeEvents(gson, Collections.singletonList(
			GroupEvent.drop(NOW, "Me", "Vorkath", EventFixtures.items(FakeItems.WHIP, 1)))).get(0);
		assertTrue(json, json.contains("{\"t\":\"d\",\"ts\":" + NOW + ",\"s\":\"Vorkath\",\"i\":[4151,1]}"));
	}

	@Test
	public void groupEventsGoOnlyWithTheirSwitchAndNeverTheDropsOrStorage()
	{
		List<GroupEvent> all = new ArrayList<>(EventFixtures.samples().values());
		assertTrue(GroupOutbox.groupEvents(gson, false, all).isEmpty());
		List<GroupEvent> sent = new ArrayList<>();
		for (String payload : GroupOutbox.groupEvents(gson, true, all))
		{
			sent.addAll(GroupCodec.decode(gson, payload).events);
		}
		assertTrue(sent.stream().noneMatch(e -> e.type.logged() || !e.type.travels()));
		assertEquals(all.stream().filter(e -> e.type.travels() && !e.type.logged()).count(), sent.size());
	}
}
