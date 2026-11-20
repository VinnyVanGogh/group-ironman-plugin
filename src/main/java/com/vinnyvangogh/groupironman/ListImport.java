package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A list pasted from the clipboard, as text, CSV or JSON, turned into items for a new shopping list or request.
 *
 * <p>Two steps, so each is testable without a client. {@link #parse} reads the text into rows and reports every
 * line it cannot read; {@link #resolve} matches the rows to items and merges duplicates. Nothing is dropped
 * silently: a line that cannot be read or matched is named in {@link Preview#unmatched}, and what the paste or the
 * list's size cut off is counted.
 *
 * <p>Text: one item per line, "500 Shark", "500x Shark", "Shark x500", "Shark, 500". CSV: item,qty[,note], with or
 * without a header. JSON: an array of {name|id, qty, quest?, requirement?}, an array of strings, an object with
 * an "items" array, or an object of name to quantity.
 */
final class ListImport
{
	/** Characters read from a paste; a longer one is cut (text) or refused (JSON). */
	static final int MAX_CHARS = 20_000;
	/** Rows read from a paste. */
	static final int MAX_ROWS = 100;

	private static final String NUM = "-?\\d[\\d.]*[kKmM]?";
	private static final Pattern LEADING = Pattern.compile("^(" + NUM + ")(?:\\s*[xX×]\\s+|[xX×]\\s*|\\s+)(.+)$");
	private static final Pattern TRAILING = Pattern.compile("^(.+?)(?:\\s+[xX]\\s*|\\s*×\\s*)(" + NUM + ")$");
	private static final Pattern GROUPED = Pattern.compile("(?<![\\d.,])(\\d{1,3}(?:,\\d{3})+)(?![\\d,])");
	private static final Pattern NUMBER = Pattern.compile("^" + NUM + "$");
	private static final Pattern NOTED = Pattern.compile("\\s*\\(noted\\)\\s*$", Pattern.CASE_INSENSITIVE);
	private static final Set<String> NAME_HEADERS = Set.of("item", "items", "name", "item name", "id");
	private static final Set<String> QTY_HEADERS = Set.of("qty", "quantity", "amount", "count", "number");

	private ListImport()
	{
	}

	/** One line or object of the paste. */
	static final class Row
	{
		/** What to look up; empty when {@link #id} is given. */
		final String name;
		/** An item id from JSON or an "id" column; 0 when the row has a name. */
		final int id;
		final int qty;
		/** The note, quest or requirement column; shown in the preview, not kept on the list. */
		final String note;
		/** How to refer to the row when it cannot be matched. */
		final String line;

		Row(String name, int id, int qty, String note, String line)
		{
			this.name = name;
			this.id = id;
			this.qty = qty;
			this.note = note;
			this.line = line;
		}
	}

	/** Rows read, lines that could not be, and whether the paste was cut. */
	static final class Parsed
	{
		final List<Row> rows = new ArrayList<>();
		final List<String> bad = new ArrayList<>();
		boolean truncated;

		private boolean full()
		{
			return rows.size() >= MAX_ROWS;
		}

		private void add(Row r)
		{
			if (full())
			{
				truncated = true;
			}
			else
			{
				rows.add(r);
			}
		}

		private void reject(String line)
		{
			if (bad.size() >= MAX_ROWS)
			{
				truncated = true;
			}
			else
			{
				bad.add(line.length() > ShoppingList.MAX_TEXT ? line.substring(0, ShoppingList.MAX_TEXT) : line);
			}
		}
	}

	/** What a new list would get, before anything is created. */
	static final class Preview
	{
		/** Item id to total quantity, merged, at most {@link ShoppingList#MAX_ITEMS}. */
		final Map<Integer, Integer> items = new LinkedHashMap<>();
		final Map<Integer, String> names = new LinkedHashMap<>();
		/** Lines that could not be read or matched, to fix or drop. */
		final List<String> unmatched = new ArrayList<>();
		/** Rows folded into an earlier row for the same item. */
		int merged;
		/** Distinct items left out because a list holds at most {@link ShoppingList#MAX_ITEMS}. */
		int overflow;
		/** The paste had more than {@link #MAX_CHARS} characters or {@link #MAX_ROWS} rows. */
		boolean truncated;
	}

	/** Reads {@code text}; never throws, never null. */
	static Parsed parse(Gson gson, String text)
	{
		Parsed out = new Parsed();
		String s = text == null ? "" : text.trim();
		if (s.isEmpty())
		{
			return out;
		}
		if (s.startsWith("[") || s.startsWith("{"))
		{
			parseJson(gson, s, out);
		}
		else
		{
			parseLines(s, out);
		}
		return out;
	}

	/** The distinct names of the rows that need an item search, in order. */
	static List<String> names(Parsed p)
	{
		Set<String> out = new LinkedHashSet<>();
		for (Row r : p.rows)
		{
			if (r.id == 0)
			{
				out.add(r.name);
			}
		}
		return new ArrayList<>(out);
	}

	/**
	 * Matches rows to items. {@code byName} answers a row's name (null when no item has it), {@code nameOf} an id's
	 * display name.
	 */
	static Preview resolve(Parsed p, Function<String, ShoppingTab.Hit> byName, IntFunction<String> nameOf)
	{
		Preview out = new Preview();
		out.truncated = p.truncated;
		Set<Integer> dropped = new LinkedHashSet<>();
		for (Row r : p.rows)
		{
			int id = r.id;
			String name = null;
			if (id == 0)
			{
				ShoppingTab.Hit hit = byName.apply(r.name);
				id = hit == null ? 0 : hit.id;
				name = hit == null ? null : hit.name;
			}
			else if (id > 0 && id < ItemCounts.MAX_ITEM_ID)
			{
				name = nameOf.apply(id);
			}
			if (name == null || id <= 0 || id >= ItemCounts.MAX_ITEM_ID)
			{
				out.unmatched.add(r.line);
				continue;
			}
			Integer have = out.items.get(id);
			if (have != null)
			{
				out.merged++;
				out.items.put(id, (int) Math.min((long) have + r.qty, ShoppingList.MAX_QUANTITY));
			}
			else if (out.items.size() >= ShoppingList.MAX_ITEMS)
			{
				dropped.add(id);
			}
			else
			{
				out.items.put(id, r.qty);
				out.names.put(id, name);
			}
		}
		out.overflow = dropped.size();
		out.unmatched.addAll(p.bad);
		return out;
	}

	/**
	 * The item an exact name stands for: the same text, then the same ignoring case, spacing and a "(noted)" tail.
	 * A partial name is never guessed.
	 */
	static ShoppingTab.Hit pick(String name, List<ShoppingTab.Hit> candidates)
	{
		String exact = name == null ? "" : name.trim();
		String loose = searchTerm(name).toLowerCase(Locale.ROOT);
		if (loose.isEmpty())
		{
			return null;
		}
		for (ShoppingTab.Hit h : candidates)
		{
			if (h.name.equals(exact))
			{
				return h;
			}
		}
		for (ShoppingTab.Hit h : candidates)
		{
			if (searchTerm(h.name).toLowerCase(Locale.ROOT).equals(loose))
			{
				return h;
			}
		}
		return null;
	}

	/** The name as an item search wants it: single spaces, no "(noted)". */
	static String searchTerm(String name)
	{
		String s = name == null ? "" : name.replace(' ', ' ').trim().replaceAll("\\s+", " ");
		return NOTED.matcher(s).replaceFirst("").trim();
	}

	// ---- text and CSV ----

	private static void parseLines(String text, Parsed out)
	{
		String s = text;
		if (s.length() > MAX_CHARS)
		{
			out.truncated = true;
			int cut = s.lastIndexOf('\n', MAX_CHARS);
			s = s.substring(0, cut > 0 ? cut : MAX_CHARS);
		}
		boolean first = true;
		boolean idColumn = false;
		for (String raw : s.split("\\r?\\n|\\r"))
		{
			String line = raw.trim();
			if (line.isEmpty() || line.startsWith("#") || line.startsWith("//"))
			{
				continue;
			}
			List<String> cells = cells(line);
			if (first)
			{
				first = false;
				if (isHeader(cells))
				{
					idColumn = "id".equals(cells.get(0).toLowerCase(Locale.ROOT));
					continue;
				}
			}
			Row row = cells.size() > 1 ? fromCells(cells, line, idColumn) : fromText(cells.get(0), line);
			if (row == null)
			{
				out.reject(line);
			}
			else
			{
				out.add(row);
			}
		}
	}

	private static boolean isHeader(List<String> cells)
	{
		return cells.size() > 1 && NAME_HEADERS.contains(cells.get(0).toLowerCase(Locale.ROOT))
			&& QTY_HEADERS.contains(cells.get(1).toLowerCase(Locale.ROOT));
	}

	/** Splits at commas, semicolons and tabs outside quotes; "1,500" with a thousands comma stays whole. */
	private static List<String> cells(String line)
	{
		String s = line.indexOf('"') >= 0 || line.indexOf('\t') >= 0 || line.indexOf(';') >= 0 ? line : ungroup(line);
		List<String> out = new ArrayList<>();
		StringBuilder cell = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < s.length(); i++)
		{
			char c = s.charAt(i);
			if (c == '"')
			{
				quoted = !quoted;
			}
			else if (!quoted && (c == ',' || c == ';' || c == '\t'))
			{
				out.add(cell.toString().trim());
				cell.setLength(0);
			}
			else
			{
				cell.append(c);
			}
		}
		out.add(cell.toString().trim());
		return out;
	}

	private static String ungroup(String line)
	{
		Matcher m = GROUPED.matcher(line);
		StringBuffer sb = new StringBuffer();
		while (m.find())
		{
			m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1).replace(",", "")));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	private static Row fromCells(List<String> cells, String line, boolean idColumn)
	{
		int nameAt = -1;
		int qtyAt = -1;
		for (int i = 0; i < cells.size(); i++)
		{
			String c = cells.get(i);
			if (c.isEmpty())
			{
				continue;
			}
			if (idColumn && i == 0 && NUMBER.matcher(c).matches() && !c.contains(".") && !c.matches(".*[kKmM]"))
			{
				nameAt = 0;
			}
			else if (!NUMBER.matcher(c).matches())
			{
				nameAt = nameAt < 0 ? i : nameAt;
			}
			else if (qtyAt < 0)
			{
				qtyAt = i;
			}
		}
		if (nameAt < 0)
		{
			return null;
		}
		String note = note(cells, nameAt, qtyAt);
		if (idColumn && nameAt == 0 && NUMBER.matcher(cells.get(0)).matches())
		{
			int qty = qtyAt < 0 ? 1 : quantity(cells.get(qtyAt));
			return qty <= 0 ? null : new Row("", id(cells.get(0)), qty, note, line);
		}
		if (qtyAt < 0)
		{
			Row text = fromText(cells.get(nameAt), line);
			return text == null ? null : new Row(text.name, 0, text.qty, note, line);
		}
		int qty = quantity(cells.get(qtyAt));
		return qty <= 0 ? null : new Row(cells.get(nameAt), 0, qty, note, line);
	}

	/** An item id as typed, uncapped: out of int range becomes a number no item has. */
	private static int id(String text)
	{
		try
		{
			BigDecimal value = new BigDecimal(text.trim());
			return value.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0 ? Integer.MAX_VALUE
				: value.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) < 0 ? Integer.MIN_VALUE : value.intValue();
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	private static String note(List<String> cells, int nameAt, int qtyAt)
	{
		List<String> rest = new ArrayList<>();
		for (int i = 0; i < cells.size(); i++)
		{
			if (i != nameAt && i != qtyAt && !cells.get(i).isEmpty())
			{
				rest.add(cells.get(i));
			}
		}
		return String.join(", ", rest);
	}

	/** One item on a line: "500 Shark", "500x Shark", "Shark x500", or just "Shark". Null for a quantity of 0 or less. */
	private static Row fromText(String text, String line)
	{
		String s = text.replace("\"", "").trim();
		String name = s;
		int qty = 1;
		Matcher lead = LEADING.matcher(s);
		Matcher trail = TRAILING.matcher(s);
		if (lead.matches())
		{
			qty = quantity(lead.group(1));
			name = lead.group(2).trim();
		}
		else if (trail.matches())
		{
			qty = quantity(trail.group(2));
			name = trail.group(1).trim();
		}
		return name.isEmpty() || qty <= 0 ? null : new Row(name, 0, qty, "", line);
	}

	/** A quantity as typed ("500", "1.5k", "2m"), capped at {@link ShoppingList#MAX_QUANTITY}; 0 or less is no quantity. */
	private static int quantity(String text)
	{
		String t = text.trim();
		BigDecimal factor = BigDecimal.ONE;
		char last = t.isEmpty() ? ' ' : Character.toLowerCase(t.charAt(t.length() - 1));
		if (last == 'k' || last == 'm')
		{
			factor = BigDecimal.valueOf(last == 'k' ? 1_000L : 1_000_000L);
			t = t.substring(0, t.length() - 1);
		}
		try
		{
			BigDecimal value = new BigDecimal(t).multiply(factor);
			return value.compareTo(BigDecimal.valueOf(ShoppingList.MAX_QUANTITY)) > 0 ? ShoppingList.MAX_QUANTITY
				: value.intValue();
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	// ---- JSON ----

	private static void parseJson(Gson gson, String text, Parsed out)
	{
		if (text.length() > MAX_CHARS)
		{
			out.truncated = true;
			out.reject("The pasted JSON is longer than " + MAX_CHARS + " characters.");
			return;
		}
		JsonElement root;
		try
		{
			root = gson.fromJson(text, JsonElement.class);
		}
		catch (RuntimeException e)
		{
			root = null;
		}
		if (root == null)
		{
			out.reject("The pasted JSON could not be read.");
			return;
		}
		if (root.isJsonArray())
		{
			root.getAsJsonArray().forEach(e -> jsonRow(e, out));
		}
		else if (root.isJsonObject())
		{
			jsonObject(root.getAsJsonObject(), out);
		}
		else
		{
			out.reject("The pasted JSON is not a list.");
		}
	}

	private static void jsonObject(JsonObject o, Parsed out)
	{
		for (String key : new String[] {"items", "list", "needs"})
		{
			if (o.has(key) && o.get(key).isJsonArray())
			{
				o.get(key).getAsJsonArray().forEach(e -> jsonRow(e, out));
				return;
			}
		}
		for (Map.Entry<String, JsonElement> e : o.entrySet())
		{
			JsonElement v = e.getValue();
			int qty = v.isJsonPrimitive() ? quantity(v.getAsString()) : 0;
			if (qty > 0)
			{
				out.add(new Row(e.getKey().trim(), 0, qty, "", e.getKey()));
			}
			else
			{
				out.reject(e.getKey());
			}
		}
	}

	private static void jsonRow(JsonElement e, Parsed out)
	{
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString())
		{
			Row row = fromText(e.getAsString(), e.getAsString());
			if (row == null)
			{
				out.reject(e.getAsString());
			}
			else
			{
				out.add(row);
			}
			return;
		}
		if (!e.isJsonObject())
		{
			out.reject(e.toString());
			return;
		}
		JsonObject o = e.getAsJsonObject();
		String name = text(o, "name", "item", "itemName");
		JsonElement idValue = o.get("id");
		int id = idValue != null && idValue.isJsonPrimitive() && NUMBER.matcher(idValue.getAsString().trim()).matches()
			? id(idValue.getAsString()) : 0;
		JsonElement q = first(o, "qty", "quantity", "amount", "count");
		int qty = q == null ? 1 : number(q);
		String label = !name.isEmpty() ? name : id != 0 ? "id " + id : e.toString();
		if (qty <= 0 || name.isEmpty() && id == 0)
		{
			out.reject(label);
			return;
		}
		List<String> notes = new ArrayList<>();
		for (String key : new String[] {"quest", "requirement", "note"})
		{
			String n = text(o, key);
			if (!n.isEmpty())
			{
				notes.add(n);
			}
		}
		out.add(new Row(id != 0 ? "" : name, id, qty, String.join(" · ", notes), label));
	}

	private static JsonElement first(JsonObject o, String... keys)
	{
		for (String k : keys)
		{
			if (o.has(k) && !o.get(k).isJsonNull())
			{
				return o.get(k);
			}
		}
		return null;
	}

	private static String text(JsonObject o, String... keys)
	{
		JsonElement v = first(o, keys);
		return v != null && v.isJsonPrimitive() ? v.getAsString().trim() : "";
	}

	/** A JSON number or numeric string as a quantity or id; 0 when it is neither. */
	private static int number(JsonElement v)
	{
		if (v == null || !v.isJsonPrimitive())
		{
			return 0;
		}
		JsonPrimitive p = v.getAsJsonPrimitive();
		String s = p.getAsString().trim();
		return NUMBER.matcher(s).matches() ? quantity(s) : 0;
	}
}
