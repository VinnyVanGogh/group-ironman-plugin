package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every Easter egg line, read once from {@code easter-eggs.txt} beside this class so they are easy to edit.
 * {@link EasterEggs} decides when one applies and picks a phrasing; a new phrasing is one more line in the file,
 * a new item one more line under its id. A missing file leaves every egg empty, so none is posted.
 *
 * <p>These post into shared Discord servers and go through Plugin Hub review: keep them light. No self-harm or
 * suicide wording, nothing aimed at a person. {@code EasterEggsTest} checks the length and a list of banned words.
 */
final class EasterEggLines
{
	/** Every line of the file by its name, both in file order: {@code NAME=line}, one phrasing per line. */
	private static final Map<String, List<String>> FILE = new LinkedHashMap<>();

	static
	{
		for (String line : RegionNames.resource("easter-eggs.txt").split("\n"))
		{
			int eq = line.indexOf('=');
			if (eq > 0 && !line.startsWith("#"))
			{
				FILE.computeIfAbsent(line.substring(0, eq).trim(), k -> new ArrayList<>()).add(line.substring(eq + 1).trim());
			}
		}
	}

	/** A death that cost 0 gp. */
	static final String[] FREE_TRIP = lines("FREE_TRIP");
	/** A death where all that was lost is a spade, maybe with clue items. */
	static final String[] CLUE_HUNTERS = lines("CLUE_HUNTERS");
	static final String[] PET = lines("PET");
	/** A level up to 73 in any skill: the B0aty 73 meme. */
	static final String[] LEVEL_73 = lines("LEVEL_73");
	/** A level up to 92: 92 is halfway to 99 in XP. */
	static final String[] LEVEL_92 = lines("LEVEL_92");
	/** A level up to 67 in any skill, or combat level 67: the "six seven" meme. Any emoji goes last. */
	static final String[] LEVEL_67 = lines("LEVEL_67");
	/** A level up to 69 in any skill, or combat level 69. Any emoji goes last. */
	static final String[] LEVEL_69 = lines("LEVEL_69");
	/** A 99 in one skill. */
	static final String[] SKILL_99 = lines("SKILL_99");
	/** Maxed: every skill at 99. */
	static final String[] MAXED_TOTAL = lines("MAXED_TOTAL");
	/** Combat level 126, the highest there is. */
	static final String[] COMBAT_126 = lines("COMBAT_126");
	/** Total level 2000. */
	static final String[] TOTAL_2000 = lines("TOTAL_2000");
	/** 200M XP in a skill, the most it can hold. */
	static final String[] XP_200M = lines("XP_200M");
	/** Quest completions, keyed by the quest name as posted. */
	static final String[] COOKS_ASSISTANT = lines("COOKS_ASSISTANT");
	static final String[] DRAGON_SLAYER = lines("DRAGON_SLAYER");
	static final String[] RECIPE_FOR_DISASTER = lines("RECIPE_FOR_DISASTER");
	/** A death to TzTok-Jad. */
	static final String[] JAD = lines("JAD");
	/** A dry streak post: the "sit" meme, kindly. */
	static final String[] SIT = lines("SIT");
	/** A gold ring put in group storage: "buying gf". */
	static final String[] BUYING_GF = lines("BUYING_GF");
	/** A "valuable" drop that is only Bones: the drop threshold is set very low. */
	static final String[] BONES_ONLY = lines("BONES_ONLY");
	/** A group storage deposit worth at least the 'Legend at' value. */
	static final String[] LEGEND = lines("LEGEND");
	/** A group storage withdrawal worth at least the 'Freeloader at' value. */
	static final String[] FREELOADER = lines("FREELOADER");

	// Item ids, as on the OSRS Wiki item infobox and static.runelite.net/cache/item/icon/<id>.png.
	static final int SPADE = 952;
	static final int BONES = 526;
	static final int TWISTED_BOW = 20997;
	static final int GOLD_RING = 1635;

	/** Drops by item id, the file's numbered lines, first row wins when a drop has several. */
	static final Map<Integer, String[]> ITEMS;

	static
	{
		Map<Integer, String[]> items = new LinkedHashMap<>();
		FILE.forEach((name, lines) ->
		{
			if (name.matches("\\d{1,9}"))
			{
				items.put(Integer.parseInt(name), lines.toArray(new String[0]));
			}
		});
		ITEMS = Collections.unmodifiableMap(items);
	}

	private EasterEggLines()
	{
	}

	/** The phrasings named {@code name}; none when the file lacks it. */
	private static String[] lines(String name)
	{
		return FILE.getOrDefault(name, Collections.emptyList()).toArray(new String[0]);
	}

	/** Every line, for the test that keeps them short and kind. */
	static List<String> all()
	{
		List<String> out = new ArrayList<>();
		FILE.values().forEach(out::addAll);
		return out;
	}
}
