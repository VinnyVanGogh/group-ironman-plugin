package com.vinnyvangogh.groupironman;

import java.util.*;
import java.util.List;

/**
 * What one member needs for a preset: the item lines of the preset's quests
 * and every prerequisite they have not finished, merged by item.
 *
 * <p>Ported from Quest Path's {@code GroupShoppingNeeds} and {@code
 * ItemSplit} line rules (questpath-plugin ee638ff): optional lines, wiki lines
 * Quest Path could not match to an item, and category lines ("any pickaxe")
 * are not shopped for. A line with no stated count asks for one, and says
 * "at least" ({@link Need#stated()}).
 *
 * <p>A prerequisite only needs to be started (the wiki's "Started X") is
 * skipped once the member has started it. A finished quest is skipped with
 * everything behind it: the game would not have let them finish it otherwise.
 */
final class ShopNeeds
{
	private ShopNeeds()
	{
	}

	/** One item, across every quest that asks for it. */
	static final class Need
	{
		final int itemId;
		final String name;
		final long count;
		final boolean stated;
		final boolean obtainable;
		final Set<String> quests;

		Need(int itemId, String name, long count, boolean stated, boolean obtainable, Set<String> quests)
		{
			this.itemId = itemId;
			this.name = name;
			this.count = count;
			this.stated = stated;
			this.obtainable = obtainable;
			this.quests = Collections.unmodifiableSet(quests);
		}

		Need plus(Need other)
		{
			Set<String> q = new TreeSet<>(quests);
			q.addAll(other.quests);
			return new Need(itemId, name, count + other.count, stated && other.stated, obtainable && other.obtainable,
				q);
		}
	}

	/** The quests still to do for {@code roots}, prerequisites first. Roots the data lacks are skipped. */
	static List<QuestData.Quest> questsToDo(QuestData data, Collection<String> roots, QuestProgress progress)
	{
		Set<Long> seen = new LinkedHashSet<>();
		List<QuestData.Quest> out = new ArrayList<>();
		for (String root : roots)
		{
			QuestData.Quest q = data.quest(root);
			if (q != null)
			{
				visit(data, q, false, progress, seen, out);
			}
		}
		return out;
	}

	private static void visit(QuestData data, QuestData.Quest quest, boolean startedEnough, QuestProgress progress,
		Set<Long> seen, List<QuestData.Quest> out)
	{
		if (progress.finished(quest.name()) || startedEnough && progress.started(quest.name()) || !seen.add(quest.id()))
		{
			return;
		}
		for (long pre : quest.prerequisites())
		{
			QuestData.Quest p = data.quest(pre);
			if (p != null)
			{
				visit(data, p, quest.startedOnly(pre), progress, seen, out);
			}
		}
		out.add(quest);
	}

	/** Every quest {@code roots} reach, done or not: what the bundled data must carry. */
	static List<QuestData.Quest> closure(QuestData data, Collection<String> roots)
	{
		return questsToDo(data, roots, QuestProgress.UNKNOWN);
	}

	/** Whether a line is one to shop for; see the class doc. */
	static boolean shoppable(QuestData.Item item)
	{
		return !item.optional() && !item.unresolved() && !item.grouped() && !item.name().isEmpty()
			&& item.itemId() > 0;
	}

	/** One member's needs for {@code roots}, by item id, in quest order. */
	static Map<Integer, Need> forMember(QuestData data, Collection<String> roots, QuestProgress progress)
	{
		Map<Integer, Need> out = new LinkedHashMap<>();
		for (QuestData.Quest quest : questsToDo(data, roots, progress))
		{
			for (QuestData.Item item : quest.items())
			{
				if (!shoppable(item))
				{
					continue;
				}
				boolean stated = item.quantity() > 0;
				Need need = new Need(item.itemId(), item.name(), stated ? item.quantity() : 1, stated,
					item.obtainable(), Collections.singleton(quest.name()));
				out.merge(item.itemId(), need, Need::plus);
			}
		}
		return out;
	}
}
