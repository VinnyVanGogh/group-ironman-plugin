package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.List;

/**
 * A member's prayer switch and poison, venom and disease, as the game's own varps and varbit tell it
 * ({@code VarbitID.PRAYER_ALLACTIVE}, {@code VarPlayerID.POISON}, {@code VarPlayerID.DISEASE}), the same source
 * the base client's Poison plugin reads.
 *
 * <p>What the client exposes, and what it does not:
 * <ul>
 * <li>Prayer: whether any prayer is switched on. Which ones is not shared.</li>
 * <li>Poison: the varp counts the poison's remaining hits down by one per hit, a hit every 30 game ticks, so
 * the time left is {@code hits * 18 s}. An estimate, not a game timer.</li>
 * <li>Venom: the varp has no end time, so the state is shown with no time left.</li>
 * <li>Disease: a flag only, no duration.</li>
 * </ul>
 * A sender from before this existed shares none of it: {@link #UNKNOWN}, which draws nothing, as against
 * {@link #NONE}, which says the member is clear.
 */
final class MemberStatus
{
	/** Seconds one poison hit lasts: 30 game ticks of 0.6 s. */
	static final int POISON_HIT_SECONDS = 18;
	/** The poison varp's value from this one up is venom, the way the base client's Poison plugin reads it. */
	static final int VENOM_THRESHOLD = 1_000_000;
	/** Most hits a poison can have left; a decoded number above it is not believed. */
	static final int MAX_POISON_HITS = 1000;

	/** Not shared by this sender. */
	static final MemberStatus UNKNOWN = new MemberStatus(false, false, 0, false, false, "");
	/** Shared: prayer off, nothing wrong. */
	static final MemberStatus NONE = new MemberStatus(true, false, 0, false, false, "");
	/** Longest pet name kept; a decoded name past it is cut, not believed. */
	static final int MAX_PET_NAME = 40;

	private final boolean known;
	private final boolean prayerOn;
	private final int poisonHits;
	private final boolean venom;
	private final boolean disease;
	/** The member's follower pet by name, "" when none out or not shared. */
	private final String pet;

	private MemberStatus(boolean known, boolean prayerOn, int poisonHits, boolean venom, boolean disease, String pet)
	{
		this.known = known;
		this.prayerOn = prayerOn;
		this.poisonHits = poisonHits;
		this.venom = venom;
		this.disease = disease;
		this.pet = pet;
	}

	/** This status with the pet the member has out; a null, blank or over-long name is tidied, never trusted. */
	MemberStatus withPet(String name)
	{
		String clean = name == null ? "" : name.replaceAll("<[^>]*>", "").trim();
		if (clean.length() > MAX_PET_NAME)
		{
			clean = clean.substring(0, MAX_PET_NAME);
		}
		return new MemberStatus(known, prayerOn, poisonHits, venom, disease, clean);
	}

	/** The pet's name, or "" when no pet is out (or the sender does not share it). */
	String pet()
	{
		return pet;
	}

	/** @param poisonHits poison hits left, 0 for none; ignored while {@code venom}. */
	static MemberStatus of(boolean prayerOn, int poisonHits, boolean venom, boolean disease)
	{
		int hits = venom ? 0 : Math.max(0, Math.min(MAX_POISON_HITS, poisonHits));
		return new MemberStatus(true, prayerOn, hits, venom, disease, "");
	}

	/** From the raw game values: the prayer bitmask, the poison varp and the disease varp. */
	static MemberStatus fromGame(int prayerMask, int poisonVarp, int diseaseVarp)
	{
		boolean venom = poisonVarp >= VENOM_THRESHOLD;
		// A negative poison varp is the immunity after an antipoison, not poison.
		int hits = venom || poisonVarp <= 0 ? 0 : poisonVarp;
		return of(prayerMask != 0, hits, venom, diseaseVarp > 0);
	}

	boolean known()
	{
		return known;
	}

	boolean prayerOn()
	{
		return prayerOn;
	}

	boolean poisoned()
	{
		return poisonHits > 0;
	}

	int poisonHits()
	{
		return poisonHits;
	}

	boolean venomed()
	{
		return venom;
	}

	boolean diseased()
	{
		return disease;
	}

	/** Estimated seconds of poison left; 0 when not poisoned. */
	int poisonSeconds()
	{
		return poisonHits * POISON_HIT_SECONDS;
	}

	/** "Poisoned, about 2m 10s left", "Venomed (time left not shown by the game)", "Diseased (...)"; empty when clear. */
	List<String> lines()
	{
		List<String> out = new ArrayList<>();
		if (venom)
		{
			out.add("Venomed (time left: the game does not say)");
		}
		else if (poisonHits > 0)
		{
			out.add("Poisoned, about " + duration(poisonSeconds()) + " left (estimated from the poison's hits)");
		}
		if (disease)
		{
			out.add("Diseased (time left: the game does not say)");
		}
		return out;
	}

	/** The short chips drawn on a card: "Poisoned 2m10s", "Venomed", "Diseased". */
	List<String> chips()
	{
		List<String> out = new ArrayList<>();
		if (venom)
		{
			out.add("Venomed");
		}
		else if (poisonHits > 0)
		{
			out.add("Poisoned ~" + duration(poisonSeconds()).replace(" ", ""));
		}
		if (disease)
		{
			out.add("Diseased");
		}
		return out;
	}

	static String duration(int seconds)
	{
		int m = seconds / 60;
		int s = seconds % 60;
		return m == 0 ? s + "s" : s == 0 ? m + "m" : m + "m " + s + "s";
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof MemberStatus))
		{
			return false;
		}
		MemberStatus m = (MemberStatus) o;
		return known == m.known && prayerOn == m.prayerOn && poisonHits == m.poisonHits && venom == m.venom
			&& disease == m.disease && pet.equals(m.pet);
	}

	@Override
	public int hashCode()
	{
		return (known ? 1 : 0) + (prayerOn ? 2 : 0) + (venom ? 4 : 0) + (disease ? 8 : 0) + poisonHits * 16 + pet.hashCode() * 31;
	}
}
