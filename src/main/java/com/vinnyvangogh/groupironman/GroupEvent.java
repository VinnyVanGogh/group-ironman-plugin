package com.vinnyvangogh.groupironman;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * One group event: a member's drop, collection log slot, pet, death, storage
 * visit, level, quest, login and so on. The one model every output reads: the
 * "since you last played" log (drops and collection log slots only), the
 * Discord webhook and the in-game overlays.
 *
 * <p>Fields per type: {@link #text} is the NPC, item, skill, quest or killer;
 * {@link #items} the items (a drop, a storage deposit); {@link #taken} only a
 * storage summary's withdrawals; {@link #number} the KC, level, XP, total,
 * QP, value, world or days, as {@link Type} says; {@link #detail} a short
 * extra line (Barrows set progress, a storage minimum).
 *
 * <p>{@link #at} is the sender's clock and is never adjusted on receipt: the
 * same event is resent whenever someone joins the party, and the time is what
 * lets {@link DropLog} recognise it.
 */
final class GroupEvent
{
	/** Which Discord webhook override an event goes to, and its embed colour. */
	enum Category
	{
		DROPS(0xF1C40F), DEATHS(0xC0392B), STORAGE(0xB8860B), PROGRESS(0x2ECC71), GROUP(0x3498DB),
		ACTIVITIES(0x9B59B6);

		final int colour;

		Category(int colour)
		{
			this.colour = colour;
		}
	}

	/** What an event must carry to be worth showing. */
	enum Need
	{
		ITEMS, TEXT, NOTHING
	}

	/**
	 * The kinds. {@code code} is the wire and cache tag: {@code d} and {@code c}
	 * are the 1.0.0 ones, and a 1.0.0 build skips any code it does not know.
	 * Every newer code has two letters, so none is a one-letter code a test or
	 * an older build treats as unknown.
	 * Storage kinds have no code that is ever sent: every receiver derives them
	 * from the storage change it already gets.
	 */
	enum Type
	{
		DROP("d", Category.DROPS, Need.ITEMS, false),
		CLOG("c", Category.DROPS, Need.TEXT, false),
		PET("pt", Category.DROPS, Need.NOTHING, true),
		UNIQUE("un", Category.DROPS, Need.ITEMS, false),
		DEATH("dt", Category.DEATHS, Need.NOTHING, false),
		STORAGE_DEPOSIT("sd", Category.STORAGE, Need.ITEMS, false),
		STORAGE_WITHDRAWAL("sw", Category.STORAGE, Need.ITEMS, false),
		STORAGE_SUMMARY("ss", Category.STORAGE, Need.NOTHING, false),
		STORAGE_MINIMUM("sm", Category.STORAGE, Need.TEXT, false),
		STORAGE_LAST("sl", Category.STORAGE, Need.ITEMS, false),
		LEVEL_UP("lv", Category.PROGRESS, Need.TEXT, false),
		MAXED("mx", Category.PROGRESS, Need.TEXT, true),
		XP_MILESTONE("xp", Category.PROGRESS, Need.TEXT, false),
		COMBAT_LEVEL("cb", Category.PROGRESS, Need.NOTHING, false),
		TOTAL_LEVEL("tl", Category.PROGRESS, Need.NOTHING, false),
		QUEST("qc", Category.PROGRESS, Need.TEXT, false),
		QUEST_POINTS("qp", Category.PROGRESS, Need.NOTHING, false),
		ONLINE("on", Category.GROUP, Need.TEXT, false),
		BACK("bk", Category.GROUP, Need.NOTHING, false),
		BARROWS("br", Category.ACTIVITIES, Need.ITEMS, false),
		RAID_UNIQUE("ru", Category.DROPS, Need.TEXT, true),
		CLUE("cl", Category.ACTIVITIES, Need.TEXT, false),
		DRY_STREAK("dr", Category.DROPS, Need.TEXT, false),
		DEATH_VALUE("dv", Category.DEATHS, Need.NOTHING, false),
		GRAVESTONE_EXPIRING("ge", Category.DEATHS, Need.NOTHING, false),
		HC_LIFE_LOST("hl", Category.DEATHS, Need.NOTHING, true),
		WILDERNESS("wl", Category.GROUP, Need.TEXT, false),
		DIARY("di", Category.PROGRESS, Need.TEXT, false),
		COMBAT_TASK("ca", Category.PROGRESS, Need.TEXT, false),
		KC_MILESTONE("km", Category.ACTIVITIES, Need.TEXT, false),
		PERSONAL_BEST("pb", Category.ACTIVITIES, Need.TEXT, false),
		WEEKLY_RECAP("wr", Category.GROUP, Need.TEXT, false),
		SLAYER("sy", Category.ACTIVITIES, Need.TEXT, false),
		FARMING("fr", Category.ACTIVITIES, Need.TEXT, false),
		BIRDHOUSE("bh", Category.ACTIVITIES, Need.TEXT, false);

		private static final Map<String, Type> BY_CODE = new HashMap<>();

		static
		{
			for (Type t : values())
			{
				BY_CODE.put(t.code, t);
			}
		}

		final String code;
		final Category category;
		final Need need;
		/** Worth the configured Discord mention. */
		final boolean big;

		Type(String code, Category category, Need need, boolean big)
		{
			this.code = code;
			this.category = category;
			this.need = need;
			this.big = big;
		}

		/** Null for a code this build does not know. */
		static Type of(String code)
		{
			return BY_CODE.get(code);
		}

		/** Storage kinds are derived on every client from the storage change; they never travel. */
		boolean travels()
		{
			return category != Category.STORAGE;
		}

		/** The "since you last played" log keeps these two only, as 1.0.0 did. */
		boolean logged()
		{
			return this == DROP || this == CLOG;
		}
	}

	/** Longest NPC or item name kept. The longest real ones are under 40. */
	static final int MAX_TEXT = 64;
	/** Most distinct items kept from one drop. */
	static final int MAX_DROP_ITEMS = 40;
	/** {@link #number} of a death that cost a Hardcore Group Ironman group a life. */
	static final long LIFE_LOST = 1;

	final Type type;
	final long at;
	final String member;
	/** The NPC for a drop, the item name for a collection log slot; see the class javadoc for the rest. */
	final String text;
	/** Item id to quantity; empty when the type has none. */
	final Map<Integer, Integer> items;
	/** A storage summary's withdrawals; empty for everything else. */
	final Map<Integer, Integer> taken;
	final long number;
	final String detail;
	/** The world the member was on, 0 when not known. */
	final int world;
	/** Where it happened, a region name; empty when not known. */
	final String location;

	private GroupEvent(Builder b)
	{
		this.type = b.type;
		this.at = b.at;
		this.member = b.member == null ? "" : b.member.trim();
		this.text = clip(b.text);
		this.items = capped(b.items);
		this.taken = capped(b.taken);
		this.number = b.number;
		this.detail = clip(b.detail);
		this.world = Math.max(0, b.world);
		this.location = clip(b.location);
	}

	private static String clip(String text)
	{
		String t = text == null ? "" : text.trim();
		return t.length() > MAX_TEXT ? t.substring(0, MAX_TEXT) : t;
	}

	private static Map<Integer, Integer> capped(Map<Integer, Integer> items)
	{
		Map<Integer, Integer> kept = new TreeMap<>();
		if (items != null)
		{
			for (Map.Entry<Integer, Integer> e : items.entrySet())
			{
				if (kept.size() >= MAX_DROP_ITEMS)
				{
					break;
				}
				kept.put(e.getKey(), e.getValue());
			}
		}
		return Collections.unmodifiableMap(kept);
	}

	static Builder of(Type type, long at, String member)
	{
		return new Builder(type, at, member);
	}

	static GroupEvent drop(long at, String member, String npc, Map<Integer, Integer> items)
	{
		return of(Type.DROP, at, member).text(npc).items(items).build();
	}

	static GroupEvent clog(long at, String member, String item)
	{
		return of(Type.CLOG, at, member).text(item).build();
	}

	Builder toBuilder()
	{
		return of(type, at, member).text(text).items(items).taken(taken).number(number).detail(detail)
			.world(world).location(location);
	}

	GroupEvent withMember(String name)
	{
		return toBuilder().member(name).build();
	}

	/** Worth showing: a member, and what its type needs. */
	boolean isValid()
	{
		switch (type.need)
		{
			case ITEMS:
				// A drop names its NPC, as in 1.0.0; storage kinds have no text.
				return !items.isEmpty() && (type != Type.DROP || !text.isEmpty());
			case TEXT:
				return !text.isEmpty();
			default:
				return true;
		}
	}

	/** Worth the Discord mention: a pet, a 99 or max total, a Hardcore group life lost. */
	boolean isBig()
	{
		return type.big || type == Type.DEATH && number == LIFE_LOST;
	}

	/** The same event seen twice, e.g. once live and once in a resend. */
	boolean sameAs(GroupEvent o)
	{
		return type == o.type && at == o.at && member.equalsIgnoreCase(o.member)
			&& text.toLowerCase(Locale.ROOT).equals(o.text.toLowerCase(Locale.ROOT)) && items.equals(o.items)
			&& number == o.number;
	}

	/** "Vorkath dropped 2 x Dragon bones" or "new collection log slot, Draconic visage". */
	String describe(ItemLookup lookup)
	{
		return EventText.describe(this, lookup);
	}

	static final class Builder
	{
		private final Type type;
		private final long at;
		private String member;
		private String text;
		private Map<Integer, Integer> items;
		private Map<Integer, Integer> taken;
		private long number;
		private String detail;
		private int world;
		private String location;

		private Builder(Type type, long at, String member)
		{
			this.type = type;
			this.at = at;
			this.member = member;
		}

		Builder member(String v)
		{
			member = v;
			return this;
		}

		Builder text(String v)
		{
			text = v;
			return this;
		}

		Builder items(Map<Integer, Integer> v)
		{
			items = v;
			return this;
		}

		Builder taken(Map<Integer, Integer> v)
		{
			taken = v;
			return this;
		}

		Builder number(long v)
		{
			number = v;
			return this;
		}

		Builder detail(String v)
		{
			detail = v;
			return this;
		}

		Builder world(int v)
		{
			world = v;
			return this;
		}

		Builder location(String v)
		{
			location = v;
			return this;
		}

		GroupEvent build()
		{
			return new GroupEvent(this);
		}
	}
}
