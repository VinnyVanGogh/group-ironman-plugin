package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Pasting a list: text, CSV or JSON in, matched rows and unmatched lines out, nothing dropped silently. */
public class ListImportTest
{
	private static final Gson GSON = new Gson();
	private static final int SHARK = 385;
	private static final int PPOT = 2434;
	private static final int ROPE = 954;

	private static final List<ShoppingTab.Hit> CATALOG = Arrays.asList(new ShoppingTab.Hit(SHARK, "Shark"),
		new ShoppingTab.Hit(PPOT, "Prayer potion(4)"), new ShoppingTab.Hit(ROPE, "Rope"),
		new ShoppingTab.Hit(386, "Raw shark"));

	private static ListImport.Parsed parse(String text)
	{
		return ListImport.parse(GSON, text);
	}

	private static ListImport.Preview preview(String text)
	{
		return ListImport.resolve(parse(text), name -> ListImport.pick(name, CATALOG), id -> "item " + id);
	}

	private static void one(ListImport.Parsed p, String name, int qty)
	{
		assertEquals(1, p.rows.size());
		assertEquals(name, p.rows.get(0).name);
		assertEquals(qty, p.rows.get(0).qty);
		assertTrue(p.bad.isEmpty());
	}

	// ---- text: every spelling of a quantity ----

	@Test
	public void quantityFirst()
	{
		one(parse("500 Shark"), "Shark", 500);
		one(parse("500x Shark"), "Shark", 500);
		one(parse("500 x Shark"), "Shark", 500);
	}

	@Test
	public void quantityLast()
	{
		one(parse("Shark x500"), "Shark", 500);
		one(parse("Shark x 500"), "Shark", 500);
		one(parse("Shark, 500"), "Shark", 500);
		one(parse("Shark\t500"), "Shark", 500);
		one(parse("Shark;500"), "Shark", 500);
	}

	@Test
	public void noQuantityIsOne()
	{
		one(parse("Shark"), "Shark", 1);
	}

	@Test
	public void suffixesAndSeparators()
	{
		one(parse("1k Shark"), "Shark", 1000);
		one(parse("Shark x2.5k"), "Shark", 2500);
		one(parse("1,500 Shark"), "Shark", 1500);
		one(parse("900k Rope"), "Rope", 900_000);
		one(parse("2m Rope"), "Rope", ShoppingList.MAX_QUANTITY);
	}

	@Test
	public void aNameWithADigitInItIsNotAQuantity()
	{
		one(parse("Prayer potion(4)"), "Prayer potion(4)", 1);
		one(parse("Prayer potion(4) x10"), "Prayer potion(4)", 10);
	}

	@Test
	public void blankAndCommentLinesAreSkippedNotReported()
	{
		ListImport.Parsed p = parse("\n# my list\n// more\n  \nShark\r\n");
		one(p, "Shark", 1);
	}

	@Test
	public void quantityOverTheCapIsCapped()
	{
		one(parse("99999999999 Shark"), "Shark", ShoppingList.MAX_QUANTITY);
	}

	@Test
	public void zeroOrNegativeQuantityIsReported()
	{
		ListImport.Parsed p = parse("0 Shark\nShark x-5");
		assertTrue(p.rows.isEmpty());
		assertEquals(2, p.bad.size());
	}

	// ---- CSV ----

	@Test
	public void csvWithAndWithoutHeader()
	{
		ListImport.Parsed headed = parse("item,qty,note\nShark,500,for the fight caves\nRope,2,");
		assertEquals(2, headed.rows.size());
		assertEquals("Shark", headed.rows.get(0).name);
		assertEquals(500, headed.rows.get(0).qty);
		assertEquals("for the fight caves", headed.rows.get(0).note);
		assertEquals(2, headed.rows.get(1).qty);
		assertTrue(headed.bad.isEmpty());

		ListImport.Parsed bare = parse("Shark,500\nRope,2");
		assertEquals(2, bare.rows.size());
		assertEquals(500, bare.rows.get(0).qty);
	}

	@Test
	public void csvQuotedNameWithAComma()
	{
		ListImport.Parsed p = parse("\"Shark, cooked\",5");
		one(p, "Shark, cooked", 5);
	}

	@Test
	public void csvQuantityColumnFirst()
	{
		one(parse("500,Shark"), "Shark", 500);
	}

	// ---- JSON ----

	@Test
	public void jsonByName()
	{
		ListImport.Parsed p = parse("[{\"name\":\"Shark\",\"qty\":500,\"quest\":\"Fight Caves\"},{\"name\":\"Rope\"}]");
		assertEquals(2, p.rows.size());
		assertEquals("Shark", p.rows.get(0).name);
		assertEquals(500, p.rows.get(0).qty);
		assertEquals("Fight Caves", p.rows.get(0).note);
		assertEquals(1, p.rows.get(1).qty);
	}

	@Test
	public void jsonById()
	{
		ListImport.Parsed p = parse("[{\"id\":385,\"qty\":3,\"requirement\":\"cooked\"}]");
		assertEquals(1, p.rows.size());
		assertEquals(385, p.rows.get(0).id);
		assertEquals(3, p.rows.get(0).qty);
		assertEquals("cooked", p.rows.get(0).note);
	}

	@Test
	public void jsonShapes()
	{
		assertEquals(2, parse("{\"items\":[{\"name\":\"Shark\",\"qty\":1},{\"name\":\"Rope\",\"qty\":1}]}").rows.size());
		assertEquals(2, parse("[\"Shark\",\"Rope\"]").rows.size());
		ListImport.Parsed map = parse("{\"Shark\":500,\"Rope\":2}");
		assertEquals(2, map.rows.size());
		assertEquals(500, map.rows.get(0).qty);
	}

	@Test
	public void jsonProblemsAreReportedNotThrown()
	{
		ListImport.Parsed p = parse("[{\"qty\":5},{\"name\":\"Shark\",\"qty\":\"lots\"},7,{\"name\":\"Rope\",\"qty\":2}]");
		assertEquals(1, p.rows.size());
		assertEquals("Rope", p.rows.get(0).name);
		assertEquals(3, p.bad.size());
	}

	@Test
	public void brokenJsonIsOneProblemNotACrash()
	{
		ListImport.Parsed p = parse("[{\"name\":\"Shark\"");
		assertTrue(p.rows.isEmpty());
		assertFalse(p.bad.isEmpty());
	}

	// ---- resolving names ----

	@Test
	public void exactThenCaseInsensitiveThenNoted()
	{
		assertEquals(SHARK, ListImport.pick("Shark", CATALOG).id);
		assertEquals(SHARK, ListImport.pick("sHaRk", CATALOG).id);
		assertEquals(SHARK, ListImport.pick("  shark   ", CATALOG).id);
		assertEquals(SHARK, ListImport.pick("Shark (noted)", CATALOG).id);
		assertEquals(PPOT, ListImport.pick("prayer potion(4)", CATALOG).id);
	}

	@Test
	public void aPartialNameIsNotGuessed()
	{
		assertNull(ListImport.pick("Prayer", CATALOG));
		assertNull(ListImport.pick("Lobster", Collections.<ShoppingTab.Hit>emptyList()));
	}

	@Test
	public void unknownNamesAreReportedNotDropped()
	{
		ListImport.Preview p = preview("500 Shark\n3 Lobstr\nbogus line x");
		assertEquals(1, p.items.size());
		assertEquals(500, (int) p.items.get(SHARK));
		assertEquals(Arrays.asList("3 Lobstr", "bogus line x"), p.unmatched);
	}

	@Test
	public void duplicatesAreMergedAndCounted()
	{
		ListImport.Preview p = preview("500 Shark\nshark x100\nRope\nShark, 1");
		assertEquals(2, p.items.size());
		assertEquals(601, (int) p.items.get(SHARK));
		assertEquals(2, p.merged);
		assertTrue(p.unmatched.isEmpty());
	}

	@Test
	public void mergedQuantityStaysCapped()
	{
		ListImport.Preview p = preview("1m Shark\n1m Shark");
		assertEquals(ShoppingList.MAX_QUANTITY, (int) p.items.get(SHARK));
	}

	@Test
	public void anIdAndItsNameMerge()
	{
		ListImport.Preview p = preview("[{\"id\":385,\"qty\":2},{\"name\":\"Shark\",\"qty\":3}]");
		assertEquals(5, (int) p.items.get(SHARK));
		assertEquals(1, p.merged);
	}

	@Test
	public void anImpossibleIdIsUnmatched()
	{
		ListImport.Preview p = preview("[{\"id\":-4},{\"id\":999999999}]");
		assertTrue(p.items.isEmpty());
		assertEquals(2, p.unmatched.size());
	}

	@Test
	public void mixedNoteTravelsToThePreviewOnlyForUnmatched()
	{
		ListImport.Preview p = preview("Shark,1,note");
		assertEquals(1, p.items.size());
	}

	// ---- caps ----

	@Test
	public void aHugePasteIsCappedAndSaysSo()
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < ListImport.MAX_ROWS + 50; i++)
		{
			sb.append("Item").append(i).append('\n');
		}
		ListImport.Parsed p = parse(sb.toString());
		assertEquals(ListImport.MAX_ROWS, p.rows.size());
		assertTrue(p.truncated);
	}

	@Test
	public void aHugeCharacterCountIsCapped()
	{
		StringBuilder sb = new StringBuilder("Shark x1");
		while (sb.length() < ListImport.MAX_CHARS * 3)
		{
			sb.append(' ');
		}
		sb.append("\nRope");
		ListImport.Parsed p = parse(sb.toString());
		assertTrue(p.truncated);
		ListImport.Parsed json = parse("[" + String.join(",", Collections.nCopies(ListImport.MAX_CHARS, "\"Shark\"")) + "]");
		assertTrue(json.truncated);
		assertTrue(json.rows.size() <= ListImport.MAX_ROWS);
	}

	@Test
	public void moreItemsThanAListHoldsAreCutAndReported()
	{
		List<ShoppingTab.Hit> many = new ArrayList<>();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < ShoppingList.MAX_ITEMS + 5; i++)
		{
			many.add(new ShoppingTab.Hit(1000 + i, "Thing" + i));
			sb.append("Thing").append(i).append('\n');
		}
		ListImport.Preview p = ListImport.resolve(parse(sb.toString()), n -> ListImport.pick(n, many), id -> "x");
		assertEquals(ShoppingList.MAX_ITEMS, p.items.size());
		assertEquals(5, p.overflow);
	}

	@Test
	public void emptyAndNullPastesGiveNothing()
	{
		assertTrue(parse("").rows.isEmpty());
		assertTrue(parse(null).rows.isEmpty());
		assertTrue(parse("   \n \n").rows.isEmpty());
		assertEquals("shark", "SHARK".toLowerCase(Locale.ROOT));
	}
}
