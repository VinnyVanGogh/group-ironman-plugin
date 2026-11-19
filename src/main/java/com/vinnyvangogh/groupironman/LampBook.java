package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntFunction;

/**
 * XP lamps and skilling books, as events: one when a member gets one ({@link GroupEvent.Type#LAMP_BOOK}, read from
 * what their inventory gained) and one when they use it ({@link GroupEvent.Type#LAMP_USED}: the skill and the XP, read
 * from the first XP gain soon after they rubbed or read it). Pure, so detection is tested without a client; the live
 * reads are in {@link GroupEventEngine}. Informational only: nothing here acts in the game.
 */
final class LampBook
{
	/** How long after rubbing or reading one the next XP gain is taken to be its XP. */
	static final long USE_WINDOW_MS = 20_000;

	/** The menu options that use a lamp or a book. "Use" is left out: that is using it on something else. */
	private static final String[] USE_OPTIONS = {"rub", "read", "study", "squeeze"};

	private final Map<Integer, Integer> held = new TreeMap<>();
	private boolean baselined;
	private final Map<String, Long> xp = new HashMap<>();
	private long armedAt = -1;
	private String armedItem = "";

	/**
	 * Whether the name is an XP lamp or a skilling book: the antique lamp, the genie's lamp, the books of knowledge
	 * and arcane knowledge and the Tome of experience. Exact names, so a "Lamp" in a different sense (a light source,
	 * a "Lamp post") is not one; the genie's is the plain "Lamp".
	 */
	static boolean isLampOrBook(String name)
	{
		if (name == null)
		{
			return false;
		}
		String n = plain(name).toLowerCase(Locale.ROOT);
		return n.equals("antique lamp") || n.equals("lamp") || n.equals("book of knowledge")
			|| n.equals("book of arcane knowledge") || n.equals("tome of experience")
			|| n.startsWith("tome of experience (") || n.endsWith(" xp lamp");
	}

	/** A menu target or item name without its colour tags and surrounding spaces. */
	static String plain(String name)
	{
		return name == null ? "" : name.replaceAll("<[^>]*>", "").trim();
	}

	/**
	 * The member's inventory now. An event per lamp or book whose count went up since the last one. The first one
	 * after {@link #reset} is only the baseline, so a login does not announce what was already carried; {@code quiet}
	 * (the bank or a trade is open: it was moved, not received) moves the baseline without an event.
	 */
	List<GroupEvent> inventory(Map<Integer, Integer> inventory, IntFunction<String> names, long now, String member,
		boolean quiet)
	{
		Map<Integer, Integer> now_ = new TreeMap<>();
		for (Map.Entry<Integer, Integer> e : inventory.entrySet())
		{
			if (e.getValue() > 0 && isLampOrBook(names.apply(e.getKey())))
			{
				now_.put(e.getKey(), e.getValue());
			}
		}
		List<GroupEvent> out = new ArrayList<>();
		if (baselined && !quiet)
		{
			for (Map.Entry<Integer, Integer> e : now_.entrySet())
			{
				int gained = e.getValue() - held.getOrDefault(e.getKey(), 0);
				if (gained > 0)
				{
					Map<Integer, Integer> items = new TreeMap<>();
					items.put(e.getKey(), gained);
					out.add(GroupEvent.of(GroupEvent.Type.LAMP_BOOK, now, member).text(plain(names.apply(e.getKey())))
						.items(items).number(gained).build());
				}
			}
		}
		held.clear();
		held.putAll(now_);
		baselined = true;
		return out;
	}

	/** A menu click: rubbing or reading a lamp or book starts the window its XP is looked for in. */
	void clicked(String option, String target, long now)
	{
		if (option == null || !isLampOrBook(target))
		{
			return;
		}
		String o = option.trim().toLowerCase(Locale.ROOT);
		for (String use : USE_OPTIONS)
		{
			if (o.equals(use))
			{
				armedAt = now;
				armedItem = plain(target);
				return;
			}
		}
	}

	/**
	 * A skill's XP now. The first gain inside the window after {@link #clicked} is the lamp's or book's: the skill, the
	 * XP in {@link GroupEvent#number} and the item in {@link GroupEvent#detail}. Null otherwise. A skill's first
	 * value (a login) is only its baseline, and a window is used once.
	 */
	GroupEvent stat(String skill, long xpNow, long now, String member)
	{
		Long before = xp.put(skill, xpNow);
		if (armedAt < 0)
		{
			return null;
		}
		if (now - armedAt > USE_WINDOW_MS || now < armedAt)
		{
			armedAt = -1;
			return null;
		}
		if (before == null || xpNow <= before)
		{
			return null;
		}
		String item = armedItem;
		armedAt = -1;
		return GroupEvent.of(GroupEvent.Type.LAMP_USED, now, member).text(skill).number(xpNow - before).detail(item)
			.build();
	}

	/** Logout or an account switch: the next inventory and every skill's next XP are baselines again. */
	void reset()
	{
		held.clear();
		xp.clear();
		baselined = false;
		armedAt = -1;
		armedItem = "";
	}
}
