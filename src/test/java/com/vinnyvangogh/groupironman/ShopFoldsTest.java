package com.vinnyvangogh.groupironman;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** What the Shopping tab remembers as open, per character. */
public class ShopFoldsTest
{
	/** The RuneLite profile of whoever is logged in, as a plain value. */
	private static final class Profile implements ShopFolds.Store
	{
		String value;

		@Override
		public String load()
		{
			return value;
		}

		@Override
		public void save(String v)
		{
			value = v;
		}
	}

	@Test
	public void everythingStartsClosed()
	{
		ShopFolds f = new ShopFolds(new Profile());
		assertFalse(f.open(ShopFolds.list("abc")));
		assertFalse(f.open(ShopFolds.request("abc")));
	}

	@Test
	public void openingIsSavedAndReadBackByANewTab()
	{
		Profile p = new Profile();
		new ShopFolds(p).toggle(ShopFolds.list("abc"), Collections.singleton(ShopFolds.list("abc")));
		assertEquals("l:abc", p.value);
		assertTrue("a restarted client reads it back", new ShopFolds(p).open(ShopFolds.list("abc")));
	}

	@Test
	public void closingTheLastOneClearsTheSavedValue()
	{
		Profile p = new Profile();
		ShopFolds f = new ShopFolds(p);
		f.toggle("l:abc", Collections.singleton("l:abc"));
		f.toggle("l:abc", Collections.singleton("l:abc"));
		assertFalse(f.open("l:abc"));
		assertNull(p.value);
	}

	@Test
	public void goneListsAreDroppedOnTheNextChange()
	{
		Profile p = new Profile();
		p.value = "l:gone,l:kept";
		ShopFolds f = new ShopFolds(p);
		f.toggle("r:new", Arrays.asList("l:kept", "r:new"));
		assertEquals("l:kept,r:new", p.value);
	}

	@Test
	public void anotherCharactersSavedValueIsReadWhenItChanges()
	{
		Profile p = new Profile();
		ShopFolds f = new ShopFolds(p);
		f.toggle("l:mine", Collections.singleton("l:mine"));
		p.value = "l:theirs";
		assertTrue(f.open("l:theirs"));
		assertFalse(f.open("l:mine"));
	}

	@Test
	public void keysDoNotCollideAcrossKinds()
	{
		assertEquals("i:l:abc:954", ShopFolds.item(ShopFolds.list("abc"), 954));
		assertEquals("d:r:abc", ShopFolds.done(ShopFolds.request("abc")));
	}
}
