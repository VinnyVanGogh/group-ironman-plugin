package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.ItemID;

/**
 * Every Easter egg line, in one place so they are easy to edit. {@link EasterEggs} decides when one applies and
 * picks a phrasing; a new phrasing is one more string in its array, a new item is one more {@link #ITEMS} row.
 *
 * <p>These post into shared Discord servers and go through Plugin Hub review: keep them light. No self-harm or
 * suicide wording, nothing aimed at a person. {@code EasterEggsTest} checks the length and a list of banned words.
 */
final class EasterEggLines
{
	/** A death that cost 0 gp. */
	static final String[] FREE_TRIP = {
		"Free trip to Lumbridge, no charge.",
		"Nothing to lose, nothing lost. Suspiciously intentional.",
	};
	/** A death where all that was lost is a spade, maybe with clue items. */
	static final String[] CLUE_HUNTERS = {
		"Those damn clue hunters.",
	};
	static final String[] PET = {
		"Congrats, you can stop now.",
		"Look who followed you home.",
	};
	/** A "valuable" drop that is only Bones: the drop threshold is set very low. */
	static final String[] BONES = {
		"Bones. Truly valuable.",
		"Someone check the valuable drop setting.",
	};
	/** A group storage deposit worth at least the 'Legend at' value. */
	static final String[] LEGEND = {
		"Legend of the group.",
	};
	/** A group storage withdrawal worth more than the 'Freeloader at' value. */
	static final String[] FREELOADER = {
		"Freeloader detected.",
	};

	/** Drops by item id, first row wins when a drop has several. */
	static final Map<Integer, String[]> ITEMS;

	static
	{
		Map<Integer, String[]> items = new LinkedHashMap<>();
		items.put(ItemID.TWISTED_BOW, lines("Ok, you can log off now.", "Somebody frame this one."));
		items.put(ItemID.SCYTHE_OF_VITUR_UNCHARGED, lines("The Theatre says thanks for playing."));
		items.put(ItemID.TUMEKENS_SHADOW_UNCHARGED, lines("The shadow has chosen you."));
		items.put(ItemID.DRAGON_WARHAMMER, lines("Defence levels everywhere just got nervous."));
		items.put(ItemID.ELDER_MAUL, lines("Bonk."));
		items.put(ItemID.DRAGON_PICKAXE, lines("Rocks across Gielinor fear you now."));
		items.put(ItemID.DRACONIC_VISAGE, lines("A face only a dragon could love."));
		items.put(ItemID.ZENYTE_SHARD, lines("One shard closer to a very shiny necklace."));
		items.put(ItemID.DRAGON_CHAINBODY, lines("Chainbody? In this economy?"));
		items.put(ItemID.ABYSSAL_WHIP, lines("Whip it. Whip it good."));
		items.put(ItemID.DRAGON_MED_HELM, lines("The legendary med helm.", "Every adventurer's first big drop."));
		ITEMS = Collections.unmodifiableMap(items);
	}

	private EasterEggLines()
	{
	}

	private static String[] lines(String... lines)
	{
		return lines;
	}

	/** Every line, for the test that keeps them short and kind. */
	static List<String> all()
	{
		List<String> out = new ArrayList<>();
		for (String[] lines : Arrays.asList(FREE_TRIP, CLUE_HUNTERS, PET, BONES, LEGEND, FREELOADER))
		{
			out.addAll(Arrays.asList(lines));
		}
		for (String[] lines : ITEMS.values())
		{
			out.addAll(Arrays.asList(lines));
		}
		return out;
	}
}
