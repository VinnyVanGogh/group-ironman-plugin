package com.vinnyvangogh.buildguard;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class BankAssemblerTest
{
	private static Map<Integer, Integer> one(int id, int qty)
	{
		Map<Integer, Integer> m = new TreeMap<>();
		m.put(id, qty);
		return m;
	}

	private static GroupCodec.BankPart part(long ts, int p, int n, int id)
	{
		return new GroupCodec.BankPart(ts, p, n, one(id, 1));
	}

	@Test
	public void partsCanArriveInAnyOrder()
	{
		BankAssembler a = new BankAssembler();
		assertNull(a.accept(1, part(10, 2, 3, 300)));
		assertNull(a.accept(1, part(10, 0, 3, 100)));
		BankSnapshot done = a.accept(1, part(10, 1, 3, 200));
		assertNotNull(done);
		assertEquals(3, done.items().size());
		assertEquals(10, done.takenAt());
	}

	@Test
	public void aDuplicatePartDoesNotCompleteEarly()
	{
		BankAssembler a = new BankAssembler();
		assertNull(a.accept(1, part(10, 0, 2, 100)));
		assertNull(a.accept(1, part(10, 0, 2, 100)));
		assertNotNull(a.accept(1, part(10, 1, 2, 200)));
	}

	@Test
	public void aNewerSnapshotDiscardsAnUnfinishedOlderOne()
	{
		BankAssembler a = new BankAssembler();
		assertNull(a.accept(1, part(10, 0, 2, 100)));
		assertNull(a.accept(1, part(20, 0, 2, 500)));
		// The old snapshot's second part arrives late and must not mix in.
		assertNull(a.accept(1, part(10, 1, 2, 200)));
		BankSnapshot done = a.accept(1, part(20, 1, 2, 600));
		assertEquals(20, done.takenAt());
		assertEquals(Collections.singleton(500), Collections.singleton(done.items().keySet().iterator().next()));
		assertEquals(2, done.items().size());
	}

	@Test
	public void membersAreAssembledSeparately()
	{
		BankAssembler a = new BankAssembler();
		assertNull(a.accept(1, part(10, 0, 2, 100)));
		assertNotNull(a.accept(2, part(10, 0, 1, 100)));
		assertNotNull(a.accept(1, part(10, 1, 2, 200)));
	}
}
