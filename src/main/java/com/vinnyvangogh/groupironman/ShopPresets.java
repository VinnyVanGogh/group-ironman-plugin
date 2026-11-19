package com.vinnyvangogh.groupironman;

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Shopping tab's prebuilt lists: one per build milestone, each naming the
 * quests it takes. A member's list is then those quests and every
 * prerequisite they have not done ({@link ShopNeeds}).
 *
 * <p>Read from {@code shopping-presets.json}, which {@code
 * scripts/gen-quest-items.py} also reads, so the bundled quest data always
 * carries every quest a preset reaches. The first build is the 1 Defence pure
 * (Board 2026-11-14); {@code QuestDataTest} fails if any quest a preset reaches
 * rewards Defence experience or needs Defence above 1.
 */
final class ShopPresets
{
	static final String RESOURCE = "shopping-presets.json";

	@SerializedName("presets")
	private List<Preset> presets;

	/** One milestone. */
	static final class Preset
	{
		@SerializedName("id")
		private String id;

		@SerializedName("name")
		private String name;

		@SerializedName("build")
		private String build;

		@SerializedName("note")
		private String note;

		@SerializedName("quests")
		private List<String> quests;

		@SerializedName("choose")
		private Choice choose;

		/** Stable: lists name their preset by this on the wire. */
		String id()
		{
			return id == null ? "" : id;
		}

		String name()
		{
			return name == null ? id() : name;
		}

		String build()
		{
			return build == null ? "" : build;
		}

		/** Shown under the list, e.g. which skill to pick for a reward; empty for none. */
		String note()
		{
			return note == null ? "" : note;
		}

		/** Always taken. */
		List<String> quests()
		{
			return quests == null ? Collections.emptyList() : quests;
		}

		/** Some of a set to pick from (Mithril gloves: 5 of 6 subquests), or null. */
		Choice choice()
		{
			return choose;
		}

		/**
		 * The quests this list is for: {@link #quests()} plus the picks. Picks
		 * that are not options are dropped; too few or too many fall back to the
		 * defaults, so a list never asks for the wrong number.
		 */
		List<String> roots(Collection<String> picks)
		{
			Set<String> out = new LinkedHashSet<>(quests());
			if (choose != null)
			{
				out.addAll(choose.valid(picks));
			}
			return new ArrayList<>(out);
		}
	}

	/** Pick {@code count} of {@code from}; {@code defaults} until the player changes them. */
	static final class Choice
	{
		@SerializedName("count")
		private int count;

		@SerializedName("from")
		private List<String> from;

		@SerializedName("defaults")
		private List<String> defaults;

		int count()
		{
			return count;
		}

		List<String> from()
		{
			return from == null ? Collections.emptyList() : from;
		}

		List<String> defaults()
		{
			return defaults == null ? Collections.emptyList() : defaults;
		}

		/** {@code picks} when it is exactly {@link #count} options, otherwise the defaults. */
		List<String> valid(Collection<String> picks)
		{
			if (picks == null)
			{
				return defaults();
			}
			Set<String> kept = new LinkedHashSet<>();
			for (String option : from())
			{
				if (picks.contains(option))
				{
					kept.add(option);
				}
			}
			return kept.size() == count ? new ArrayList<>(kept) : defaults();
		}
	}

	List<Preset> presets()
	{
		return presets == null ? Collections.emptyList() : presets;
	}

	/** One preset by id, or null (a list from a newer build naming one this build lacks). */
	Preset preset(String id)
	{
		for (Preset p : presets())
		{
			if (p.id().equals(id))
			{
				return p;
			}
		}
		return null;
	}

	static ShopPresets load(Gson gson)
	{
		try (InputStream in = ShopPresets.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				return new ShopPresets();
			}
			ShopPresets out = gson.fromJson(new InputStreamReader(in, Charsets.UTF_8), ShopPresets.class);
			return out == null ? new ShopPresets() : out;
		}
		catch (IOException | JsonParseException e)
		{
			return new ShopPresets();
		}
	}
}
