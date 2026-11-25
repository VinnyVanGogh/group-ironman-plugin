package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The player's own "Bring this" table: their edits to the starting rules ({@link GearChecks#RULES}), their own
 * places, and which sub-fights they skip. Kept as JSON in one hidden config key
 * ({@link GroupIronmanConfig#KEY_GEAR_BOOK}); an empty or unreadable key is the starting table unchanged. Never
 * sent to the group. Every method is synchronized: the panel edits it on the Swing thread and region entry reads
 * it on the client thread.
 */
final class GearBook
{
	static final int MAX_CUSTOM = 30;
	static final int MAX_LINES = 40;
	static final int MAX_NAMES = 20;
	static final int MAX_TEXT = 60;
	static final String CUSTOM_PREFIX = "custom-";

	/** One line as saved. Plain fields so Gson needs nothing special. */
	static final class LineSpec
	{
		String label;
		List<String> names;
		String need;
		int count = 1;
		String kind;
		boolean optional;
		String fight;
	}

	/** One rule as saved. */
	static final class RuleSpec
	{
		String id;
		String title;
		int[] regions;
		List<LineSpec> lines;
		/** An edited starting rule: {@link #fingerprint} of the shipped list it was edited from. */
		String base;
	}

	/** The whole key. */
	static final class Data
	{
		int v = 1;
		Map<String, RuleSpec> edits = new LinkedHashMap<>();
		List<RuleSpec> custom = new ArrayList<>();
		Map<String, List<String>> skips = new LinkedHashMap<>();
	}

	private final Map<String, GearChecks.Rule> edits = new LinkedHashMap<>();
	/**
	 * Per edited starting rule, the shipped list it was edited from. An edit is a full copy, so a later fix to
	 * {@link GearChecks#RULES} never reaches it; a differing fingerprint lets the panel say so and offer a reset.
	 */
	private final Map<String, String> bases = new LinkedHashMap<>();
	private final List<GearChecks.Rule> custom = new ArrayList<>();
	private final Map<String, Set<String>> skips = new LinkedHashMap<>();

	/** The starting table, nothing edited. */
	static GearBook empty()
	{
		return new GearBook();
	}

	/** Reads the key; blank, broken or a later version's JSON it cannot read gives the starting table. */
	static GearBook parse(Gson gson, String json)
	{
		GearBook book = new GearBook();
		if (json == null || json.trim().isEmpty())
		{
			return book;
		}
		Data data;
		try
		{
			data = gson.fromJson(json, Data.class);
		}
		catch (JsonParseException | IllegalStateException e)
		{
			return book;
		}
		if (data == null)
		{
			return book;
		}
		if (data.edits != null)
		{
			for (Map.Entry<String, RuleSpec> e : data.edits.entrySet())
			{
				GearChecks.Rule base = GearChecks.byId(e.getKey());
				GearChecks.Rule r = base == null ? null : toRule(e.getValue(), base.id, false);
				if (r != null)
				{
					book.edits.put(base.id, r.withTitle(base.title).withRegions(base.regions));
					String from = e.getValue().base;
					// No fingerprint: saved before they were kept, so take the shipped list as the one edited.
					book.bases.put(base.id, from == null || from.isEmpty() ? fingerprint(base) : from);
				}
			}
		}
		if (data.custom != null)
		{
			for (RuleSpec spec : data.custom)
			{
				String id = spec == null ? null : spec.id;
				if (id == null || !id.startsWith(CUSTOM_PREFIX) || book.find(id) != null
					|| book.custom.size() >= MAX_CUSTOM)
				{
					continue;
				}
				GearChecks.Rule r = toRule(spec, id, true);
				if (r != null)
				{
					book.custom.add(r);
				}
			}
		}
		if (data.skips != null)
		{
			for (Map.Entry<String, List<String>> e : data.skips.entrySet())
			{
				if (e.getKey() == null || e.getValue() == null)
				{
					continue;
				}
				Set<String> fights = new LinkedHashSet<>();
				for (String f : e.getValue())
				{
					String t = clean(f);
					if (!t.isEmpty())
					{
						fights.add(t);
					}
				}
				if (!fights.isEmpty())
				{
					book.skips.put(e.getKey(), fights);
				}
			}
			for (String id : new ArrayList<>(book.skips.keySet()))
			{
				book.pruneSkips(id);
			}
		}
		return book;
	}

	synchronized String toJson(Gson gson)
	{
		Data data = new Data();
		for (GearChecks.Rule r : edits.values())
		{
			RuleSpec spec = toSpec(r);
			spec.base = bases.get(r.id);
			data.edits.put(r.id, spec);
		}
		for (GearChecks.Rule r : custom)
		{
			data.custom.add(toSpec(r));
		}
		for (Map.Entry<String, Set<String>> e : skips.entrySet())
		{
			data.skips.put(e.getKey(), new ArrayList<>(e.getValue()));
		}
		return gson.toJson(data);
	}

	/** Nothing edited, added or skipped: the key can stay unset. */
	synchronized boolean isEmpty()
	{
		return edits.isEmpty() && custom.isEmpty() && skips.isEmpty();
	}

	/** Every rule in selector order: the starting table with edits applied, then the player's own places. */
	synchronized List<GearChecks.Rule> rules()
	{
		List<GearChecks.Rule> out = new ArrayList<>();
		for (GearChecks.Rule r : GearChecks.RULES)
		{
			out.add(edits.getOrDefault(r.id, r));
		}
		out.addAll(custom);
		return out;
	}

	synchronized GearChecks.Rule byId(String id)
	{
		return find(id);
	}

	private GearChecks.Rule find(String id)
	{
		for (GearChecks.Rule r : custom)
		{
			if (r.id.equals(id))
			{
				return r;
			}
		}
		GearChecks.Rule base = GearChecks.byId(id);
		return base == null ? null : edits.getOrDefault(id, base);
	}

	/** The rule for {@code region}: a player's own place first, so they can take over a starting one's region. */
	synchronized GearChecks.Rule forRegion(int region)
	{
		GearChecks.Rule own = GearChecks.forRegion(custom, region);
		if (own != null)
		{
			return own;
		}
		GearChecks.Rule base = GearChecks.forRegion(region);
		return base == null ? null : edits.getOrDefault(base.id, base);
	}

	/** A starting rule the player has changed: the panel offers "Reset to default". */
	synchronized boolean edited(String id)
	{
		return edits.containsKey(id);
	}

	/**
	 * An edited starting rule whose shipped list has changed since the player edited it: the panel offers
	 * "Reset to default" or "Keep mine".
	 */
	synchronized boolean shippedChanged(String id)
	{
		GearChecks.Rule base = GearChecks.byId(id);
		return base != null && edits.containsKey(id) && !fingerprint(base).equals(bases.get(id));
	}

	/** "Keep mine": the player has seen the shipped change and keeps their edit; stops {@link #shippedChanged}. */
	synchronized void keepMine(String id)
	{
		GearChecks.Rule base = GearChecks.byId(id);
		if (base != null && edits.containsKey(id))
		{
			bases.put(id, fingerprint(base));
		}
	}

	/** Saves {@code r} over the rule with its id; false for an unknown id. */
	synchronized boolean put(GearChecks.Rule r)
	{
		List<GearChecks.Req> lines = r.reqs.size() > MAX_LINES ? r.reqs.subList(0, MAX_LINES) : r.reqs;
		for (int i = 0; i < custom.size(); i++)
		{
			if (custom.get(i).id.equals(r.id))
			{
				custom.set(i, new GearChecks.Rule(r.id, cleanTitle(r.title), r.regions, lines, true));
				pruneSkips(r.id);
				return true;
			}
		}
		GearChecks.Rule base = GearChecks.byId(r.id);
		if (base == null)
		{
			return false;
		}
		edits.put(base.id, new GearChecks.Rule(base.id, base.title, base.regions, lines, false));
		// Kept from the first edit: a later edit must not hide a shipped change the player has not answered.
		bases.putIfAbsent(base.id, fingerprint(base));
		pruneSkips(base.id);
		return true;
	}

	/** Puts a starting rule back as shipped. */
	synchronized void reset(String id)
	{
		edits.remove(id);
		bases.remove(id);
		pruneSkips(id);
	}

	/** A new place of the player's own, triggered by {@code region} when it is above 0; null when the book is full. */
	synchronized GearChecks.Rule addCustom(String title, int region)
	{
		if (custom.size() >= MAX_CUSTOM)
		{
			return null;
		}
		int n = 1;
		while (find(CUSTOM_PREFIX + n) != null)
		{
			n++;
		}
		GearChecks.Rule r = new GearChecks.Rule(CUSTOM_PREFIX + n, cleanTitle(title),
			region > 0 ? new int[] {region} : new int[0], Collections.emptyList(), true);
		custom.add(r);
		return r;
	}

	/** Removes a place of the player's own; starting rules are reset, never removed. */
	synchronized boolean remove(String id)
	{
		boolean removed = custom.removeIf(r -> r.id.equals(id));
		if (removed)
		{
			skips.remove(id);
		}
		return removed;
	}

	synchronized Set<String> skipped(String id)
	{
		Set<String> s = skips.get(id);
		return s == null ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(s));
	}

	synchronized void skip(String id, String fight, boolean on)
	{
		Set<String> s = skips.computeIfAbsent(id, k -> new LinkedHashSet<>());
		if (on)
		{
			s.add(clean(fight));
		}
		else
		{
			s.removeIf(f -> f.equalsIgnoreCase(fight));
		}
		if (s.isEmpty())
		{
			skips.remove(id);
		}
		pruneSkips(id);
	}

	/**
	 * Drops skips no line of the rule names any more (a fight edited away, a rule gone), so a stale skip never
	 * comes back to hide a line the player adds later.
	 */
	private void pruneSkips(String id)
	{
		Set<String> s = skips.get(id);
		if (s == null)
		{
			return;
		}
		GearChecks.Rule r = find(id);
		if (r != null)
		{
			List<String> fights = r.fights();
			s.removeIf(f -> fights.stream().noneMatch(f::equalsIgnoreCase));
		}
		if (r == null || s.isEmpty())
		{
			skips.remove(id);
		}
	}

	/**
	 * A stable summary of a rule's lines (String#hashCode is fixed by the language spec), so the saved value
	 * still compares equal after a client restart or update when the shipped list did not change.
	 */
	static String fingerprint(GearChecks.Rule r)
	{
		StringBuilder sb = new StringBuilder();
		for (GearChecks.Req q : r.reqs)
		{
			sb.append(q.label).append('\u0001').append(String.join("\u0002", q.names)).append('\u0001')
				.append(q.need.name()).append('\u0001').append(q.count).append('\u0001').append(q.kind.name())
				.append('\u0001').append(q.optional).append('\u0001').append(q.fight).append('\u0003');
		}
		return Integer.toHexString(sb.toString().hashCode()) + "-" + r.reqs.size();
	}

	private static String clean(String s)
	{
		if (s == null)
		{
			return "";
		}
		String t = s.trim();
		return t.length() > MAX_TEXT ? t.substring(0, MAX_TEXT) : t;
	}

	private static String cleanTitle(String s)
	{
		String t = clean(s);
		return t.isEmpty() ? "My list" : t;
	}

	private static GearChecks.Rule toRule(RuleSpec spec, String id, boolean isCustom)
	{
		if (spec == null)
		{
			return null;
		}
		List<GearChecks.Req> reqs = new ArrayList<>();
		if (spec.lines != null)
		{
			for (LineSpec l : spec.lines)
			{
				GearChecks.Req r = toReq(l);
				if (r != null && reqs.size() < MAX_LINES)
				{
					reqs.add(r);
				}
			}
		}
		int[] regions = spec.regions == null ? new int[0] : spec.regions;
		return new GearChecks.Rule(id, cleanTitle(spec.title), regions, reqs, isCustom);
	}

	private static GearChecks.Req toReq(LineSpec l)
	{
		if (l == null)
		{
			return null;
		}
		String label = clean(l.label);
		List<String> names = new ArrayList<>();
		if (l.names != null)
		{
			for (String n : l.names)
			{
				String t = clean(n);
				if (!t.isEmpty() && names.size() < MAX_NAMES)
				{
					names.add(t);
				}
			}
		}
		GearChecks.Kind kind = parse(GearChecks.Kind.values(), l.kind, GearChecks.Kind.NONE);
		if (label.isEmpty() || names.isEmpty() && kind == GearChecks.Kind.NONE)
		{
			return null;
		}
		return new GearChecks.Req(label, names, parse(GearChecks.Need.values(), l.need, GearChecks.Need.CARRIED),
			l.count, kind, l.optional, clean(l.fight));
	}

	private static <E extends Enum<E>> E parse(E[] values, String name, E fallback)
	{
		if (name == null)
		{
			return fallback;
		}
		for (E e : values)
		{
			if (e.name().equals(name.toUpperCase(Locale.ROOT)))
			{
				return e;
			}
		}
		return fallback;
	}

	private static RuleSpec toSpec(GearChecks.Rule r)
	{
		RuleSpec s = new RuleSpec();
		s.id = r.id;
		s.title = r.title;
		s.regions = r.regions.clone();
		s.lines = new ArrayList<>();
		for (GearChecks.Req q : r.reqs)
		{
			LineSpec l = new LineSpec();
			l.label = q.label;
			l.names = new ArrayList<>(q.names);
			l.need = q.need.name();
			l.count = q.count;
			l.kind = q.kind.name();
			l.optional = q.optional;
			l.fight = q.fight;
			s.lines.add(l);
		}
		return s;
	}
}
