package com.vinnyvangogh.groupironman;

import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The bundled equip requirements table that "who benefits most" checks members' levels against. */
public class ItemRequirementsTest
{
	@Test
	public void theBundledTableKnowsTheAbyssalWhipNeeds70Attack()
	{
		assertEquals(Collections.singletonMap("Attack", 70), ItemRequirements.bundled().of(4151));
	}

	@Test
	public void anItemWithTwoRequirementsKeepsBoth()
	{
		assertEquals(60, (int) ItemRequirements.bundled().of(12954).get("Attack"));
		assertEquals(60, (int) ItemRequirements.bundled().of(12954).get("Defence"));
	}

	@Test
	public void anItemNotInTheTableHasNoKnownRequirements()
	{
		assertTrue(ItemRequirements.bundled().of(-5).isEmpty());
	}

	@Test
	public void parseSkipsCommentsAndMalformedLines()
	{
		ItemRequirements r = ItemRequirements.parse("# note\n1|Thing|Attack=5|Magic=7\nbad line\n2|X|Attack=zz\n"
			+ "3|Y|Nonsense=4\n4|Z|Defence=-1\n");
		assertEquals(2, r.of(1).size());
		assertEquals(7, (int) r.of(1).get("Magic"));
		assertEquals(1, r.size());
	}
}
