package com.vinnyvangogh.groupironman;

import java.util.*;
import java.util.List;
import java.util.function.Function;
import net.runelite.api.*;

/**
 * This player's progress in the bundled quests, read from the game: the quest
 * data names quests as the wiki does, and the game's {@link Quest} by its
 * {@link Quest#getName()}, which {@code QuestDataTest} holds to match for
 * every quest a preset reaches.
 */
final class QuestStates
{
	private static final Map<String, Quest> BY_NAME = new HashMap<>();

	static
	{
		for (Quest q : Quest.values())
		{
			BY_NAME.putIfAbsent(normal(q.getName()), q);
		}
	}

	private QuestStates()
	{
	}

	private static String normal(String name)
	{
		return name.replace('’', '\'').trim().toLowerCase(Locale.ROOT);
	}

	/**
	 * The game's quest for a data name, or null. The wiki names Recipe for Disaster's parts as pages,
	 * "Recipe for Disaster/Freeing the Mountain Dwarf"; the game as "Recipe for Disaster - Mountain Dwarf".
	 */
	static Quest quest(String name)
	{
		if (name == null)
		{
			return null;
		}
		Quest q = BY_NAME.get(normal(name));
		return q != null ? q : BY_NAME.get(normal(gameName(name)));
	}

	static String gameName(String wikiName)
	{
		int slash = wikiName.indexOf('/');
		if (slash < 0)
		{
			return wikiName;
		}
		String part = wikiName.substring(slash + 1);
		for (String verb : new String[]{"Freeing the ", "Freeing ", "Defeating the "})
		{
			if (part.startsWith(verb))
			{
				part = part.substring(verb.length());
				break;
			}
		}
		// The wiki frees "the Goblin generals"; the game names them.
		if ("Goblin generals".equals(part))
		{
			part = "Wartface & Bentnoze";
		}
		return wikiName.substring(0, slash) + " - " + part;
	}

	/** Every quest worth reading: those the data carries, and those any carried shop is behind. */
	static Set<String> wanted(QuestData data)
	{
		Set<String> out = new LinkedHashSet<>();
		for (QuestData.Quest q : data.quests())
		{
			out.add(q.name());
		}
		for (QuestData.Shop s : data.shops())
		{
			if (s.reachability() != null)
			{
				for (Long id : s.reachability().requiredQuestIds())
				{
					String name = id == null ? null : data.questName(id);
					if (name != null)
					{
						out.add(name);
					}
				}
			}
		}
		return out;
	}

	/** Client thread when {@code state} asks the game. A name the game does not know is left not started. */
	static QuestProgress read(Collection<String> names, Function<Quest, QuestState> state)
	{
		List<String> finished = new ArrayList<>();
		List<String> started = new ArrayList<>();
		for (String name : names)
		{
			Quest q = quest(name);
			QuestState s = q == null ? null : state.apply(q);
			if (s == QuestState.FINISHED)
			{
				finished.add(name);
			}
			else if (s == QuestState.IN_PROGRESS)
			{
				started.add(name);
			}
		}
		return QuestProgress.of(finished, started);
	}
}
