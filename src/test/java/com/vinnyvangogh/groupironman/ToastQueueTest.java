package com.vinnyvangogh.groupironman;

import java.util.List;
import org.junit.Test;
import static com.vinnyvangogh.groupironman.EventFixtures.NOW;
import static com.vinnyvangogh.groupironman.EventFixtures.sample;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ToastQueueTest
{
	private static final long DURATION = 8_000;
	private final ToastQueue q = new ToastQueue();

	@Test
	public void aBannerShowsThenExpires()
	{
		q.add(sample(GroupEvent.Type.PET), NOW);
		assertEquals(1, q.visible(NOW + DURATION - 1, DURATION, 3, e -> true).size());
		assertTrue(q.visible(NOW + DURATION, DURATION, 3, e -> true).isEmpty());
		assertEquals("the group box still lists it", 1, q.recent(5).size());
	}

	@Test
	public void bannersStackNewestFirstUpToTheMax()
	{
		q.add(sample(GroupEvent.Type.PET), NOW);
		q.add(sample(GroupEvent.Type.DEATH), NOW + 1);
		q.add(sample(GroupEvent.Type.QUEST), NOW + 2);
		q.add(sample(GroupEvent.Type.LEVEL_UP), NOW + 3);
		List<ToastQueue.Toast> shown = q.visible(NOW + 4, DURATION, 3, e -> true);
		assertEquals(3, shown.size());
		assertEquals(GroupEvent.Type.LEVEL_UP, shown.get(0).event.type);
		assertEquals(GroupEvent.Type.DEATH, shown.get(2).event.type);
	}

	@Test
	public void aSituationFilterHidesAKindWithoutLosingIt()
	{
		q.add(sample(GroupEvent.Type.PET), NOW);
		q.add(sample(GroupEvent.Type.DEATH), NOW);
		List<ToastQueue.Toast> shown = q.visible(NOW, DURATION, 3, e -> e.type != GroupEvent.Type.DEATH);
		assertEquals(1, shown.size());
		assertEquals(2, q.visible(NOW, DURATION, 3, e -> true).size());
	}

	@Test
	public void atMostSixBannersAMinuteAndTheRestCountAsMore()
	{
		long duration = 60_000;
		for (int i = 0; i < 10; i++)
		{
			assertEquals("banner " + i, i < ToastQueue.MAX_PER_WINDOW, q.add(sample(GroupEvent.Type.PET), NOW + i));
		}
		assertEquals(ToastQueue.MAX_PER_WINDOW, q.visible(NOW + 10, duration, 20, e -> true).size());
		assertEquals(4, q.held(NOW + 10, DURATION));
		assertEquals("the group box still lists every one", 10, q.recent(20).size());
		assertEquals("+4 more in the Log", ToastQueue.heldLine(4));
	}

	@Test
	public void theLimitIsASlidingMinute()
	{
		for (int i = 0; i < ToastQueue.MAX_PER_WINDOW; i++)
		{
			q.add(sample(GroupEvent.Type.PET), NOW);
		}
		assertFalse(q.add(sample(GroupEvent.Type.PET), NOW + ToastQueue.RATE_WINDOW_MS - 1));
		assertTrue(q.add(sample(GroupEvent.Type.PET), NOW + ToastQueue.RATE_WINDOW_MS));
	}

	@Test
	public void theMoreLineClearsAfterItsTimeOrADismiss()
	{
		for (int i = 0; i < ToastQueue.MAX_PER_WINDOW + 2; i++)
		{
			q.add(sample(GroupEvent.Type.PET), NOW);
		}
		assertEquals(2, q.held(NOW + DURATION - 1, DURATION));
		assertEquals(0, q.held(NOW + DURATION, DURATION));

		q.add(sample(GroupEvent.Type.PET), NOW + 1);
		assertEquals(1, q.held(NOW + 2, DURATION));
		q.dismissAll();
		assertEquals(0, q.held(NOW + 3, DURATION));
	}

	@Test
	public void keepsABoundedHistory()
	{
		for (int i = 0; i < ToastQueue.MAX_KEPT + 10; i++)
		{
			q.add(sample(GroupEvent.Type.PET), NOW + i);
		}
		assertEquals(ToastQueue.MAX_KEPT, q.recent(100).size());
	}
}
