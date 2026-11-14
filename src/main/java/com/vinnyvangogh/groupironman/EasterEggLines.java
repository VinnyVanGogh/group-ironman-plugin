package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
		"Ironman death insurance: own nothing.",
	};
	/** A death where all that was lost is a spade, maybe with clue items. */
	static final String[] CLUE_HUNTERS = {
		"Those clue hunters again.",
	};
	static final String[] PET = {
		"Congrats, you can stop now.",
		"Look who followed you home.",
		"Pet! Now pretend you were going for it.",
	};

	// Community memes, each tied to one event the plugin already posts. Real people only in widely known,
	// affectionate memes (B0aty's 73, Zezima), and never aimed at the player in the group.
	/** A level up to 73 in any skill: the B0aty 73 meme. */
	static final String[] LEVEL_73 = {
		"73. B0aty would approve.",
		"Seventy-three. A B0aty classic.",
	};
	/** A level up to 92: 92 is halfway to 99 in XP. */
	static final String[] LEVEL_92 = {
		"92: halfway to 99.",
		"Halfway to 99. Only the other half to go.",
	};
	/** A 99 in one skill. */
	static final String[] SKILL_99 = {
		"Cape on, emote out.",
		"99! Time to stand in a bank and emote.",
	};
	/** Maxed: every skill at 99. */
	static final String[] MAXED_TOTAL = {
		"Move over, Zezima.",
		"Maxed. Zezima would be impressed.",
	};
	/** Combat level 126, the highest there is. */
	static final String[] COMBAT_126 = {
		"126. Max combat, max swagger.",
	};
	/** Total level 2000. */
	static final String[] TOTAL_2000 = {
		"Welcome to the 2K total club.",
	};
	/** 200M XP in a skill, the most it can hold. */
	static final String[] XP_200M = {
		"200M. The XP counter has nowhere left to go.",
	};
	/** Quest completions, keyed by the quest name as posted. */
	static final String[] COOKS_ASSISTANT = {
		"The Duke gets his birthday cake after all.",
	};
	static final String[] DRAGON_SLAYER = {
		"Rune platebody unlocked. Elvarg had it coming.",
	};
	static final String[] RECIPE_FOR_DISASTER = {
		"Barrows gloves! An ironman rite of passage.",
	};
	/** A death to TzTok-Jad. */
	static final String[] JAD = {
		"Jad claims another fire cape hopeful.",
		"Pray mage, pray range, try again.",
	};
	/** A dry streak post: the "sit" meme, kindly. */
	static final String[] SIT = {
		"Sit. (Affectionately.)",
		"The RNG says sit. Keep going.",
	};
	/** A gold ring put in group storage: "buying gf". */
	static final String[] BUYING_GF = {
		"Buying gf, paying in gold rings.",
	};
	/** A "valuable" drop that is only Bones: the drop threshold is set very low. */
	static final String[] BONES_ONLY = {
		"Bones. Truly valuable.",
		"Someone check the valuable drop setting.",
	};
	// Emoji are written as Java unicode escapes (surrogate pairs) so the source compiles the same whatever the build host's encoding.
	/** A group storage deposit worth at least the 'Legend at' value. */
	static final String[] LEGEND = {
		"Feeding the group like a true ironmom/irondad. 🫶", // U+1FAF6 heart hands
		"Gringotts thanks you for your service.",
		"Generosity level: 99.",
		"The group storage is eating good tonight.",
	};
	/** A group storage withdrawal worth at least the 'Freeloader at' value. */
	static final String[] FREELOADER = {
		"Gringotts is watching you. 👀", // U+1F440 eyes
		"Borrowing, not stealing. Right?",
		"The group storage will remember this.",
		"Taking from the pile like a true group ironman.",
	};

	/** Drops by item id, first row wins when a drop has several. */
	// Item ids, as on the OSRS Wiki item infobox and static.runelite.net/cache/item/icon/<id>.png.
	static final int SPADE = 952;
	static final int BONES = 526;
	static final int TWISTED_BOW = 20997;
	static final int SCYTHE_OF_VITUR_UNCHARGED = 22486;
	static final int TUMEKENS_SHADOW_UNCHARGED = 27277;
	static final int DRAGON_WARHAMMER = 13576;
	static final int ELDER_MAUL = 21003;
	static final int DRAGON_PICKAXE = 11920;
	static final int DRACONIC_VISAGE = 11286;
	static final int ZENYTE_SHARD = 19529;
	static final int DRAGON_CHAINBODY = 3140;
	static final int ABYSSAL_WHIP = 4151;
	static final int DRAGON_MED_HELM = 1149;
	static final int GOLD_RING = 1635;

	static final Map<Integer, String[]> ITEMS;

	static
	{
		Map<Integer, String[]> items = new LinkedHashMap<>();
		items.put(TWISTED_BOW, lines("Ok, you can log off now.", "Somebody frame this one."));
		items.put(SCYTHE_OF_VITUR_UNCHARGED, lines("The Theatre says thanks for playing."));
		items.put(TUMEKENS_SHADOW_UNCHARGED, lines("The shadow has chosen you."));
		items.put(DRAGON_WARHAMMER, lines("Defence levels everywhere just got nervous."));
		items.put(ELDER_MAUL, lines("Bonk."));
		items.put(DRAGON_PICKAXE, lines("Rocks across Gielinor fear you now."));
		items.put(DRACONIC_VISAGE, lines("A face only a dragon could love."));
		items.put(ZENYTE_SHARD, lines("One shard closer to a very shiny necklace."));
		items.put(DRAGON_CHAINBODY, lines("Chainbody? In this economy?"));
		items.put(ABYSSAL_WHIP, lines("Whip it. Whip it good."));
		items.put(DRAGON_MED_HELM, lines("The legendary med helm.", "Every adventurer's first big drop."));
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
		for (String[] lines : Arrays.asList(FREE_TRIP, CLUE_HUNTERS, PET, BONES_ONLY, LEGEND, FREELOADER, LEVEL_73,
			LEVEL_92, SKILL_99, MAXED_TOTAL, COMBAT_126, TOTAL_2000, XP_200M, COOKS_ASSISTANT, DRAGON_SLAYER,
			RECIPE_FOR_DISASTER, JAD, SIT, BUYING_GF))
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
