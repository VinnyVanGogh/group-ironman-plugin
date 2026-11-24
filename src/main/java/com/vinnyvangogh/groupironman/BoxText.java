package com.vinnyvangogh.groupironman;

import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * The short form of an event for the on-screen Group box: an icon, the member, a few words and a short time,
 * instead of the full sentence the panel and Discord use ({@link EventText}). Pure, so every type at every size
 * is tested without a client. What fits and what is cut is {@link OverlayStyle.EventRow}'s business.
 */
final class BoxText
{
	/** The small built-in pictures, for events that have no skill or item to show. */
	enum Glyph
	{
		ONLINE, OFFLINE, DEATH, QUEST, DIARY, CLOG, PET, BEST, KILLS, COMBAT
	}

	/**
	 * One picture: a skill ({@link #skill}), an item ({@link #itemId}) or a {@link #glyph}. An item may also carry a
	 * glyph, drawn while the item's picture is not ready.
	 */
	static final class Icon
	{
		final String skill;
		final int itemId;
		final Glyph glyph;

		private Icon(String skill, int itemId, Glyph glyph)
		{
			this.skill = skill;
			this.itemId = itemId;
			this.glyph = glyph;
		}

		static Icon skill(String name)
		{
			return new Icon(name, -1, null);
		}

		static Icon item(int id)
		{
			return new Icon(null, id, null);
		}

		/** The item when its id is known, else the glyph alone. */
		static Icon item(int id, Glyph fallback)
		{
			return new Icon(null, id < 0 ? -1 : id, fallback);
		}

		static Icon glyph(Glyph glyph)
		{
			return new Icon(null, -1, glyph);
		}
	}

	/** One event line, split so the overlay can cut the detail and never the member or the time. */
	static final class Line
	{
		/** Null when icons are off or the event has none. */
		final Icon icon;
		final String member;
		final String detail;
		/** Empty when times are off. */
		final String ago;

		Line(Icon icon, String member, String detail, String ago)
		{
			this.icon = icon;
			this.member = member;
			this.detail = detail;
			this.ago = ago;
		}
	}

	/** What one type says: the picture, and the words with the picture and without it. */
	private static final class Part
	{
		final Icon icon;
		final String withIcon;
		final String textOnly;

		Part(Icon icon, String withIcon, String textOnly)
		{
			this.icon = icon;
			this.withIcon = withIcon;
			this.textOnly = textOnly;
		}
	}

	private interface Fmt
	{
		Part apply(GroupEvent e, ItemLookup lookup, int tier);
	}

	private static final Map<GroupEvent.Type, Fmt> FORMS = new EnumMap<>(GroupEvent.Type.class);

	static
	{
		FORMS.put(GroupEvent.Type.LEVEL_UP, BoxText::level);
		FORMS.put(GroupEvent.Type.MAXED, (e, l, t) -> "total".equalsIgnoreCase(e.text)
			? skill("Overall", "maxed", "maxed") : skill(e.text, "99", "99 " + e.text));
		FORMS.put(GroupEvent.Type.XP_MILESTONE, (e, l, t) -> skill(e.text, l.amount(e.number) + " xp",
			e.text + " " + l.amount(e.number) + " xp"));
		FORMS.put(GroupEvent.Type.TOTAL_LEVEL, (e, l, t) -> skill("Overall", Long.toString(e.number), "total " + e.number));
		FORMS.put(GroupEvent.Type.COMBAT_LEVEL, (e, l, t) -> glyph(Glyph.COMBAT, (t == 0 ? "cmb " : "combat ") + e.number));
		FORMS.put(GroupEvent.Type.QUEST, (e, l, t) -> glyph(Glyph.QUEST, e.text, "quest: " + e.text));
		FORMS.put(GroupEvent.Type.QUEST_POINTS, (e, l, t) -> glyph(Glyph.QUEST, e.number + " QP"));
		FORMS.put(GroupEvent.Type.DIARY, (e, l, t) -> glyph(Glyph.DIARY, e.text, "diary: " + e.text));
		FORMS.put(GroupEvent.Type.CLOG, (e, l, t) -> named(Glyph.CLOG, e, l, e.text, "log: " + e.text));
		FORMS.put(GroupEvent.Type.PERSONAL_BEST, (e, l, t) -> glyph(Glyph.BEST, e.text, "PB: " + e.text));
		FORMS.put(GroupEvent.Type.KC_MILESTONE, (e, l, t) -> glyph(Glyph.KILLS, e.number + " " + e.text));
		FORMS.put(GroupEvent.Type.PET, (e, l, t) -> named(Glyph.PET, e, l, t == 0 || e.text.isEmpty() ? "pet" : "pet " + e.text));
		FORMS.put(GroupEvent.Type.DEATH, (e, l, t) -> glyph(Glyph.DEATH,
			t == 2 && !e.location.isEmpty() ? "died " + e.location : "died"));
		FORMS.put(GroupEvent.Type.DEATH_VALUE, (e, l, t) -> glyph(Glyph.DEATH, "-" + l.amount(e.number),
			"lost " + l.amount(e.number)));
		FORMS.put(GroupEvent.Type.HC_LIFE_LOST, (e, l, t) -> glyph(Glyph.DEATH, "life lost"));
		FORMS.put(GroupEvent.Type.ONLINE, (e, l, t) -> "offline".equals(e.text)
			? glyph(Glyph.OFFLINE, "offline") : glyph(Glyph.ONLINE, t == 0 && e.world > 0 ? "W" + e.world : "online" + world(e), "online" + world(e)));
		FORMS.put(GroupEvent.Type.BACK, (e, l, t) -> glyph(Glyph.ONLINE, "back " + e.number + "d"));
		FORMS.put(GroupEvent.Type.SLAYER, (e, l, t) -> slayer(e));
		FORMS.put(GroupEvent.Type.DROP, BoxText::drop);
		FORMS.put(GroupEvent.Type.UNIQUE, BoxText::drop);
	}

	private BoxText()
	{
	}

	/** The event as a box line at this size; icons and times as the settings say. */
	static Line format(GroupEvent e, ItemLookup lookup, BoxSize size, boolean icons, BoxTimes times, long now)
	{
		Fmt form = FORMS.get(e.type);
		Part part = form == null ? new Part(null, EventText.describe(e, lookup), EventText.describe(e, lookup))
			: form.apply(e, lookup, size.tier);
		boolean pictured = icons && part.icon != null;
		return new Line(pictured ? part.icon : null, e.member, pictured ? part.withIcon : part.textOnly,
			times.of(now, e.at));
	}

	/** The level before a level-up's first: what it knew, or one below. */
	private static long from(GroupEvent e)
	{
		return e.previous > 0 && e.previous < e.number ? e.previous : e.number - 1;
	}

	/**
	 * Consecutive level-ups of one skill by one member (newest first, as {@link ToastQueue#recent} gives them)
	 * become one event: the newest level, from the level before the oldest, at the newest time. Anything else,
	 * including a level-up with another event between, is kept as it is.
	 */
	static java.util.List<GroupEvent> stack(java.util.List<GroupEvent> newestFirst)
	{
		java.util.List<GroupEvent> out = new java.util.ArrayList<>();
		for (GroupEvent e : newestFirst)
		{
			GroupEvent last = out.isEmpty() ? null : out.get(out.size() - 1);
			if (last != null && e.type == GroupEvent.Type.LEVEL_UP && last.type == GroupEvent.Type.LEVEL_UP
				&& e.member.equals(last.member) && e.text.equals(last.text) && e.number <= from(last) + 1)
			{
				out.set(out.size() - 1, last.toBuilder().previous(Math.min(from(e), from(last))).build());
			}
			else
			{
				out.add(e);
			}
		}
		return out;
	}

	/** "Thieving 44": one level; "44 to 46" (compact: "+2") for a stack. */
	private static Part level(GroupEvent e, ItemLookup lookup, int tier)
	{
		long gained = e.number - from(e);
		if (gained <= 1)
		{
			return skill(e.text, Long.toString(e.number), e.text + " " + e.number);
		}
		// "to", not an arrow: the RuneScape font the banner is drawn in has no glyph for one and shows a box.
		String span = (from(e) + 1) + " to " + e.number;
		String plus = "+" + gained;
		return skill(e.text, tier == 0 ? plus : span, e.text + " " + (tier == 0 ? plus : span));
	}

	private static Part skill(String name, String withIcon, String textOnly)
	{
		return new Part(Icon.skill(name), withIcon, textOnly);
	}

	private static Part glyph(Glyph glyph, String text)
	{
		return new Part(Icon.glyph(glyph), text, text);
	}

	private static Part glyph(Glyph glyph, String withIcon, String textOnly)
	{
		return new Part(Icon.glyph(glyph), withIcon, textOnly);
	}

	private static Part named(Glyph glyph, GroupEvent e, ItemLookup lookup, String text)
	{
		return named(glyph, e, lookup, text, text);
	}

	/**
	 * A collection log slot or a pet: the item's own picture (its id carried on the event, or found by the name the
	 * game's line gave), the glyph until then.
	 */
	private static Part named(Glyph glyph, GroupEvent e, ItemLookup lookup, String withIcon, String textOnly)
	{
		int id = e.items.isEmpty() ? lookup.itemId(e.text) : e.items.keySet().iterator().next();
		return new Part(Icon.item(id, glyph), withIcon, textOnly);
	}

	private static String world(GroupEvent e)
	{
		return e.world > 0 ? " W" + e.world : "";
	}

	/** "19 Icefiends" for a new task, "done Icefiends" for a finished one; an older client's wording as it came. */
	private static Part slayer(GroupEvent e)
	{
		String what = e.number > 0 ? e.number + " " + e.text : "done " + e.text;
		return skill("Slayer", what, "Slayer " + what);
	}

	/** The item that is worth the most, so a drop's picture is the part that mattered; null when it has none. */
	private static Integer top(GroupEvent e, ItemLookup lookup)
	{
		Integer best = null;
		long bestValue = -1;
		for (Entry<Integer, Integer> i : e.items.entrySet())
		{
			long value = lookup.price(i.getKey()) * i.getValue();
			if (value > bestValue)
			{
				best = i.getKey();
				bestValue = value;
			}
		}
		return best;
	}

	/**
	 * "Draconic visage 1.2M": the item, and at the smallest size only its value (the picture says what it is).
	 * A drop with no item left says the source.
	 */
	private static Part drop(GroupEvent e, ItemLookup lookup, int tier)
	{
		Integer id = top(e, lookup);
		if (id == null)
		{
			return new Part(null, e.text, e.text);
		}
		String name = lookup.name(id);
		long value = EventText.value(e.items, lookup);
		String worth = value > 0 ? " " + lookup.amount(value) : "";
		String withIcon = tier == 0 && value > 0 ? lookup.amount(value) : tier == 2 ? name + worth : name;
		return new Part(Icon.item(id), withIcon, tier == 0 ? name : name + worth);
	}
}
