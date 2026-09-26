package com.vinnyvangogh.buildguard;

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * One experience lamp: an item that, when rubbed, opens the game's
 * skill-choice interface and grants experience in whichever skill is picked.
 *
 * <p>A port of questpath's {@code internal/domain/lamp.go}. Every figure it
 * carries is sourced in that repository's {@code RESEARCH.md} §24, which
 * records the wiki page and revision id behind each one — including the two
 * multipliers for the level-scaling lamps, taken from the wiki's own
 * {@code Module:Lamp calculator} rather than from its prose.
 *
 * <p>Keyed by the game's own item id, never by the interface's title. The
 * title is the same for every lamp and the amount is not: an "Antique lamp"
 * is 300 experience from X Marks the Spot and 100,000 from Desert Treasure
 * II, and the interface cannot tell you which one you are holding.
 *
 * <p>{@link #modelled()} false is a real state and not a failure. An
 * unmodelled lamp still opens the interface normally and still greys every
 * NO_XP skill — that rule does not depend on the amount — but it must never
 * produce a ranking. A guessed ranking is the most expensive thing this
 * feature could do, because a lamp's destination cannot be taken back.
 */
class Lamp
{
	@SerializedName("item_id")
	private int itemId;

	@SerializedName("name")
	private String name;

	@SerializedName("source")
	private String source;

	@SerializedName("modelled")
	private boolean modelled;

	@SerializedName("xp")
	private double xp;

	/**
	 * The multiplier for a level-scaling lamp: experience is this times the
	 * current level of the skill chosen, with no offset and no rounding step.
	 * Zero means the lamp grants a fixed amount.
	 *
	 * <p>A genie Lamp is 10 and a Book of Knowledge is 15. Getting these the
	 * wrong way round produces a ranking that still looks entirely plausible,
	 * which is why the shared vectors carry a case for each.
	 */
	@SerializedName("xp_per_level")
	private int xpPerLevel;

	@SerializedName("min_level")
	private int minLevel;

	/** The skills the lamp restricts itself to; empty means any skill. */
	@SerializedName("skills")
	private List<String> skills;

	Lamp()
	{
	}

	private Lamp(int itemId, String name, boolean modelled)
	{
		this.itemId = itemId;
		this.name = name;
		this.modelled = modelled;
	}

	/**
	 * A lamp the bundled catalogue has never heard of.
	 *
	 * <p>Named rather than represented by null, because null at a call site
	 * becomes "skip the advisor entirely" within one refactor — and skipping
	 * it is exactly wrong. The NO_XP greying still has to happen.
	 */
	static Lamp unknown(int itemId, String name)
	{
		return new Lamp(itemId, name, false);
	}

	int itemId()
	{
		return itemId;
	}

	String name()
	{
		return name == null ? "" : name;
	}

	/** Where the lamp comes from, for a display that needs to disambiguate. */
	String source()
	{
		return source == null ? "" : source;
	}

	boolean modelled()
	{
		return modelled;
	}

	int minLevel()
	{
		return minLevel;
	}

	/** Whether this lamp's experience depends on the level of the skill chosen. */
	boolean scaling()
	{
		return xpPerLevel > 0;
	}

	int xpPerLevel()
	{
		return xpPerLevel;
	}

	List<String> skills()
	{
		return skills == null ? Collections.emptyList() : skills;
	}

	/**
	 * Whether this lamp may be pointed at the skill at all, before any build
	 * rule is consulted. A lamp with no skill list allows everything — which
	 * is the wiki's claim about the lamp, not a licence to ignore the
	 * account's own rules, which bind either way.
	 */
	boolean allows(String skill)
	{
		if (skills().isEmpty())
		{
			return true;
		}
		for (String allowed : skills())
		{
			if (allowed.equalsIgnoreCase(skill))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The experience this lamp grants in a skill currently at the given
	 * level, or -1 when the catalogue has no figure for it.
	 *
	 * <p>-1 rather than 0 deliberately: a zero-experience lamp and one the
	 * catalogue cannot price are different claims, and only one of them is ever
	 * true. Callers test {@link #modelled()} and must render the second as
	 * "amount unknown".
	 */
	double xpAt(int level)
	{
		if (!modelled)
		{
			return -1;
		}
		if (scaling())
		{
			return (double) xpPerLevel * level;
		}
		return xp;
	}

	/** A short description of what the lamp is worth, for a tooltip. */
	String describeAmount()
	{
		if (!modelled)
		{
			return "amount unknown";
		}
		if (scaling())
		{
			return xpPerLevel + " xp per level";
		}
		return XpTable.format((long) xp) + " xp";
	}

	/** Lowercase name, for matching against a skill list. */
	static String normalise(String skill)
	{
		return skill == null ? "" : skill.toLowerCase(Locale.ROOT);
	}
}
