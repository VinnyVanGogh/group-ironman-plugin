package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.http.api.item.ItemPrice;

/**
 * The game-facing side of the Compare tab: builds what it shows from the roster and the shared progress, reads item
 * data from RuneLite's {@link ItemManager}, and looks hiscores up with RuneLite's own {@link HiscoreClient} (the
 * lookup the base Hiscore plugin uses, off the client thread, a few names at a time, only when a button asks).
 */
final class CompareSupport
{
	private CompareSupport()
	{
	}

	/** Members' figures for Compare: levels they share (hiscore levels for one who does not), QP, bests. */
	static List<GroupCompare.Figures> figures(GroupRoster roster, Collection<String> names, ProgressView progress)
	{
		Map<String, MemberStats> stats = roster.allStats();
		List<GroupCompare.Figures> out = new ArrayList<>();
		for (String name : names)
		{
			Map<String, Integer> levels = new TreeMap<>();
			MemberStats s = stats.get(name);
			HiscoreBook.Entry h = roster.hiscores().entry(name);
			if (s != null)
			{
				s.skills().forEach((skill, l) -> levels.put(skill, l.real));
			}
			else if (h != null)
			{
				levels.putAll(h.levels);
			}
			AchievementState a = progress.achievements(name);
			out.add(new GroupCompare.Figures(name, levels, progress.bests(name), a == null ? -1 : a.questPoints(),
				h == null ? null : h.scores));
		}
		return out;
	}

	/** Each member's banked experience from the last bank they closed plus what they carry; empty when neither shared. */
	static Map<String, Map<String, Double>> banked(GroupRoster roster, Collection<String> names)
	{
		Map<String, BankSnapshot> banks = roster.banks();
		Map<String, GroupCodec.Carried> carried = roster.allCarried();
		Map<String, Map<String, Double>> out = new java.util.LinkedHashMap<>();
		for (String name : names)
		{
			BankSnapshot bank = banks.get(name);
			GroupCodec.Carried c = carried.get(name);
			if (bank != null || c != null)
			{
				out.put(name, BankedXp.bundled().xp(BankedXp.combine(bank == null ? null : bank.items(),
					c == null ? null : c.inventory)));
			}
		}
		return out;
	}

	/** What the shared group storage would give, once. */
	static Map<String, Double> storageBanked(GroupRoster roster)
	{
		return BankedXp.bundled().xp(roster.storageItems());
	}

	/** What the item ranking reads of each member: levels, worn items, carried items. */
	static List<BenefitFinder.Member> members(GroupRoster roster, Collection<String> names)
	{
		Map<String, MemberStats> stats = roster.allStats();
		Map<String, GroupCodec.Carried> carried = roster.allCarried();
		List<BenefitFinder.Member> out = new ArrayList<>();
		for (String name : names)
		{
			Map<String, Integer> levels = new HashMap<>();
			MemberStats s = stats.get(name);
			if (s != null)
			{
				s.skills().forEach((skill, l) -> levels.put(skill, l.real));
			}
			GroupCodec.Carried c = carried.get(name);
			out.add(new BenefitFinder.Member(name, levels,
				c == null ? Collections.<Integer>emptySet() : c.worn.keySet(),
				c == null ? Collections.<Integer>emptySet() : c.inventory.keySet(), c != null));
		}
		return out;
	}

	/** Item data from the item manager. Call on the client thread: item compositions may only be read there. */
	static BenefitFinder.Source source(ItemManager items)
	{
		return new BenefitFinder.Source()
		{
			@Override
			public int find(String query)
			{
				for (ItemPrice p : items.search(query))
				{
					return p.getId();
				}
				return -1;
			}

			@Override
			public String name(int id)
			{
				return items.getItemComposition(id).getName();
			}

			@Override
			public int slot(int id)
			{
				ItemStats s = items.getItemStats(id);
				return s == null || s.getEquipment() == null ? -1 : s.getEquipment().getSlot();
			}

			@Override
			public Map<String, Integer> stats(int id)
			{
				ItemStats s = items.getItemStats(id);
				return s == null ? Collections.emptyMap() : statsOf(s.getEquipment());
			}

			@Override
			public Map<String, Integer> requirements(int id)
			{
				// RuneLite carries no level requirements: they come from the bundled table, empty when not in it.
				return ItemRequirements.bundled().of(id);
			}
		};
	}

	/** The equipment bonuses under {@link ItemBenefit#ALL_STATS} keys; melee attack is the best of the three styles. */
	static Map<String, Integer> statsOf(ItemEquipmentStats e)
	{
		Map<String, Integer> out = new HashMap<>();
		if (e == null)
		{
			return out;
		}
		out.put("melee_attack", Math.max(e.getAstab(), Math.max(e.getAslash(), e.getAcrush())));
		out.put("melee_strength", e.getStr());
		out.put("ranged_attack", e.getArange());
		out.put("ranged_strength", e.getRstr());
		out.put("magic_attack", e.getAmagic());
		out.put("magic_damage", (int) e.getMdmg());
		out.put("defence", e.getDstab() + e.getDslash() + e.getDcrush() + e.getDmagic() + e.getDrange());
		out.put("prayer", e.getPrayer());
		return out;
	}

	/**
	 * Summed hiscore experience per skill for {@code names}, on OkHttp's threads. A player not on the hiscores
	 * counts as nothing; when none could be read at all the result is null.
	 */
	static void groupExperience(HiscoreClient client, List<String> names, Consumer<Map<String, Long>> done)
	{
		if (names.isEmpty())
		{
			done.accept(null);
			return;
		}
		Map<String, Long> sum = new TreeMap<>();
		AtomicInteger left = new AtomicInteger(names.size());
		AtomicInteger read = new AtomicInteger();
		for (String name : names)
		{
			client.lookupAsync(name, HiscoreEndpoint.NORMAL).whenComplete((result, error) ->
			{
				if (error == null && result != null)
				{
					synchronized (sum)
					{
						add(sum, result);
					}
					read.incrementAndGet();
				}
				if (left.decrementAndGet() == 0)
				{
					done.accept(read.get() == 0 ? null : sum);
				}
			});
		}
	}

	private static void add(Map<String, Long> sum, HiscoreResult result)
	{
		if (result.getSkills() == null)
		{
			return;
		}
		for (Map.Entry<HiscoreSkill, net.runelite.client.hiscore.Skill> e : result.getSkills().entrySet())
		{
			String skill = HiscoreBook.skillName(e.getKey());
			if (skill != null && e.getValue() != null && e.getValue().getExperience() > 0)
			{
				sum.merge(skill, e.getValue().getExperience(), Long::sum);
			}
		}
	}
}
