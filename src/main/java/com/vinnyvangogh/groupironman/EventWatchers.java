package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import net.runelite.api.gameval.VarbitID;

/**
 * Part C's detectors: what the game told this client, turned into at most one
 * event per occurrence. Pure: the client's values come in as arguments, and
 * events leave through one consumer that a kind's switch guards. Client
 * thread only.
 *
 * <p>Variables are polled, and the first poll after a login is only a
 * baseline, so a login's burst of values is never read as progress.
 */
final class EventWatchers
{
	/** Stands in for the skull icon among the polled variables. Negative, so no varbit can share it. */
	static final int SKULL = -1;
	static final int TICKS_PER_MINUTE = 100;
	/** 15 minutes (https://oldschool.runescape.wiki/w/Gravestone, 2026-11-09). Ticks only pass while logged in, as the grave's timer does. */
	static final int GRAVE_TICKS = 15 * TICKS_PER_MINUTE;
	/** Ticks after a death before what is still carried is counted: the respawn has moved the rest by then. */
	static final int DEATH_SETTLE_TICKS = 10;

	private static final Map<Integer, String> DIARIES = new HashMap<>();

	static
	{
		tier("Ardougne", VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE, VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE,
			VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE, VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE);
		tier("Desert", VarbitID.DESERT_DIARY_EASY_COMPLETE, VarbitID.DESERT_DIARY_MEDIUM_COMPLETE,
			VarbitID.DESERT_DIARY_HARD_COMPLETE, VarbitID.DESERT_DIARY_ELITE_COMPLETE);
		tier("Falador", VarbitID.FALADOR_DIARY_EASY_COMPLETE, VarbitID.FALADOR_DIARY_MEDIUM_COMPLETE,
			VarbitID.FALADOR_DIARY_HARD_COMPLETE, VarbitID.FALADOR_DIARY_ELITE_COMPLETE);
		tier("Fremennik", VarbitID.FREMENNIK_DIARY_EASY_COMPLETE, VarbitID.FREMENNIK_DIARY_MEDIUM_COMPLETE,
			VarbitID.FREMENNIK_DIARY_HARD_COMPLETE, VarbitID.FREMENNIK_DIARY_ELITE_COMPLETE);
		tier("Kandarin", VarbitID.KANDARIN_DIARY_EASY_COMPLETE, VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE,
			VarbitID.KANDARIN_DIARY_HARD_COMPLETE, VarbitID.KANDARIN_DIARY_ELITE_COMPLETE);
		tier("Karamja", VarbitID.ATJUN_EASY_DONE, VarbitID.ATJUN_MED_DONE, VarbitID.ATJUN_HARD_DONE,
			VarbitID.KARAMJA_DIARY_ELITE_COMPLETE);
		tier("Kourend", VarbitID.KOUREND_DIARY_EASY_COMPLETE, VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE,
			VarbitID.KOUREND_DIARY_HARD_COMPLETE, VarbitID.KOUREND_DIARY_ELITE_COMPLETE);
		tier("Lumbridge", VarbitID.LUMBRIDGE_DIARY_EASY_COMPLETE, VarbitID.LUMBRIDGE_DIARY_MEDIUM_COMPLETE,
			VarbitID.LUMBRIDGE_DIARY_HARD_COMPLETE, VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE);
		tier("Morytania", VarbitID.MORYTANIA_DIARY_EASY_COMPLETE, VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE,
			VarbitID.MORYTANIA_DIARY_HARD_COMPLETE, VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE);
		tier("Varrock", VarbitID.VARROCK_DIARY_EASY_COMPLETE, VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE,
			VarbitID.VARROCK_DIARY_HARD_COMPLETE, VarbitID.VARROCK_DIARY_ELITE_COMPLETE);
		tier("Western", VarbitID.WESTERN_DIARY_EASY_COMPLETE, VarbitID.WESTERN_DIARY_MEDIUM_COMPLETE,
			VarbitID.WESTERN_DIARY_HARD_COMPLETE, VarbitID.WESTERN_DIARY_ELITE_COMPLETE);
		tier("Wilderness", VarbitID.WILDERNESS_DIARY_EASY_COMPLETE, VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE,
			VarbitID.WILDERNESS_DIARY_HARD_COMPLETE, VarbitID.WILDERNESS_DIARY_ELITE_COMPLETE);
	}

	private final Consumer<GroupEvent> out;
	private final Predicate<GroupEvent.Type> wanted;
	private final Map<Integer, Integer> last = new HashMap<>();
	private final Map<String, Long> clueCounts = new LinkedHashMap<>();
	private boolean baselined;
	private boolean specialHeader;
	private String pendingClue;
	private String raid = "";
	private long raidKc;
	private String lastKcName = "";
	private long lastKcCount = -1;
	private Map<Integer, Integer> carriedAtDeath;
	private int settle;
	private int graveLeft;
	private boolean graveSeen;
	/** What the death cost, once counted: the grave's contents, as far as this client knows. */
	private Map<Integer, Integer> graveItems = Collections.emptyMap();
	private String graveWhere = "";
	private int graveWorld;
	private String deathWhere = "";
	private int deathWorld;

	EventWatchers(Consumer<GroupEvent> out, Predicate<GroupEvent.Type> wanted)
	{
		this.out = out;
		this.wanted = wanted;
	}

	private static void tier(String area, int easy, int medium, int hard, int elite)
	{
		DIARIES.put(easy, area + " easy");
		DIARIES.put(medium, area + " medium");
		DIARIES.put(hard, area + " hard");
		DIARIES.put(elite, area + " elite");
	}

	private void emit(GroupEvent event)
	{
		if (wanted.test(event.type))
		{
			out.accept(event);
		}
	}

	/** Another account may log in next: every baseline starts over. */
	void reset()
	{
		last.clear();
		clueCounts.clear();
		baselined = false;
		specialHeader = false;
		pendingClue = null;
		raid = "";
		raidKc = 0;
		lastKcName = "";
		lastKcCount = -1;
		carriedAtDeath = null;
		settle = 0;
		graveLeft = 0;
		graveSeen = false;
		graveItems = Collections.emptyMap();
	}

	// ---- Chat ----

	/** One game line. {@code group}: other members of this group; {@code present}: players in the instance now. */
	void chat(String message, long now, String me, Set<String> group, Collection<String> present)
	{
		raidLine(message, now, me, group, present);
		EventText.Count clue = EventChat.clue(message);
		if (clue != null)
		{
			clueCounts.put(clue.name, clue.count);
			pendingClue = clue.name;
			return;
		}
		String best = EventChat.personalBest(message);
		if (best != null)
		{
			emit(GroupEvent.of(GroupEvent.Type.PERSONAL_BEST, now, me).text(best).build());
			return;
		}
		String[] task = EventChat.combatTask(message);
		if (task != null)
		{
			emit(GroupEvent.of(GroupEvent.Type.COMBAT_TASK, now, me).text(task[1]).detail(task[0]).build());
			return;
		}
		slayer(message, now, me);
	}

	private void slayer(String message, long now, String me)
	{
		long done = EventChat.slayerDone(message);
		if (done >= 0)
		{
			emit(GroupEvent.of(GroupEvent.Type.SLAYER, now, me).text("task done").number(done).build());
			return;
		}
		EventText.Count assigned = EventChat.slayerNew(message);
		if (assigned != null)
		{
			emit(GroupEvent.of(GroupEvent.Type.SLAYER, now, me).text("new task: " + assigned.name)
				.number(assigned.count).build());
		}
	}

	private void raidLine(String message, long now, String me, Set<String> group, Collection<String> present)
	{
		if (EventChat.isSpecialLootHeader(message))
		{
			specialHeader = true;
			return;
		}
		String[] loot = EventChat.raidSpecial(message);
		if (loot == null && specialHeader)
		{
			loot = EventChat.raidDash(message);
		}
		specialHeader = false;
		// A teammate's purple is theirs to post (BRIEF.md #18).
		if (loot != null && loot[0].equalsIgnoreCase(me))
		{
			emit(GroupEvent.of(GroupEvent.Type.RAID_UNIQUE, now, me).text(loot[1]).number(raidKc)
				.detail(raidDetail(me, group, present)).build());
		}
	}

	/** "Theatre of Blood; with Sam +1 other": names only for this group's members; anyone else is only counted. */
	private String raidDetail(String me, Set<String> group, Collection<String> present)
	{
		List<String> named = new ArrayList<>();
		int others = 0;
		for (String player : present)
		{
			if (player.equalsIgnoreCase(me))
			{
				continue;
			}
			if (group.contains(player))
			{
				named.add(player);
			}
			else
			{
				others++;
			}
		}
		String team = String.join(", ", named);
		if (others > 0)
		{
			team += (team.isEmpty() ? "" : " ") + "+" + others + (others == 1 ? " other" : " others");
		}
		String with = team.isEmpty() ? "" : "with " + team;
		return raid.isEmpty() || with.isEmpty() ? raid + with : raid + "; " + with;
	}

	/** The raid's own count line, which comes before its loot line. */
	void raidCount(String name, long count)
	{
		raid = name;
		raidKc = count;
	}

	/** The clue reward screen: at most one event for each completed-count line. */
	void clueReward(Map<Integer, Integer> items, long value, long now, String me)
	{
		if (pendingClue == null)
		{
			return;
		}
		String tier = pendingClue;
		pendingClue = null;
		StringBuilder counts = new StringBuilder();
		for (Map.Entry<String, Long> e : clueCounts.entrySet())
		{
			counts.append(counts.length() == 0 ? "" : ", ").append(e.getKey()).append(' ').append(e.getValue());
		}
		emit(GroupEvent.of(GroupEvent.Type.CLUE, now, me).text(tier).items(items).number(value)
			.detail(counts.toString()).build());
	}

	/** A kill count line. The game can repeat one, so the same boss and count is one milestone. */
	void kill(String name, long count, int every, long now, String me)
	{
		boolean repeat = name.equalsIgnoreCase(lastKcName) && count == lastKcCount;
		lastKcName = name;
		lastKcCount = count;
		if (!repeat && every > 0 && count > 0 && count % every == 0)
		{
			emit(GroupEvent.of(GroupEvent.Type.KC_MILESTONE, now, me).text(name).number(count).build());
		}
	}

	// ---- Variables ----

	/** Every polled variable that went up since the last poll, as events. The first poll only records. */
	void poll(IntUnaryOperator read, long now, String me)
	{
		Map<Integer, Boolean> rose = new TreeMap<>();
		for (int id : polled())
		{
			int value = read.applyAsInt(id);
			Integer before = last.put(id, value);
			rose.put(id, baselined && before != null && value > before);
		}
		if (!baselined)
		{
			baselined = true;
			return;
		}
		fire(rose, read, now, me);
	}

	private static List<Integer> polled()
	{
		List<Integer> ids = new ArrayList<>(DIARIES.keySet());
		ids.add(SKULL);
		ids.add(VarbitID.INSIDE_WILDERNESS);
		ids.add(VarbitID.GIM_HARDCORE_DEATHS);
		return ids;
	}

	private void fire(Map<Integer, Boolean> rose, IntUnaryOperator read, long now, String me)
	{
		for (Map.Entry<Integer, String> diary : DIARIES.entrySet())
		{
			if (rose.get(diary.getKey()))
			{
				emit(GroupEvent.of(GroupEvent.Type.DIARY, now, me).text(diary.getValue()).build());
			}
		}
		if (rose.get(VarbitID.INSIDE_WILDERNESS))
		{
			emit(GroupEvent.of(GroupEvent.Type.WILDERNESS, now, me).text("entered the Wilderness").build());
		}
		if (rose.get(SKULL))
		{
			emit(GroupEvent.of(GroupEvent.Type.WILDERNESS, now, me).text("got skulled").build());
		}
		if (rose.get(VarbitID.GIM_HARDCORE_DEATHS))
		{
			int lives = read.applyAsInt(VarbitID.GIM_GROUPSIZE) - read.applyAsInt(VarbitID.GIM_HARDCORE_DEATHS);
			emit(GroupEvent.of(GroupEvent.Type.HC_LIFE_LOST, now, me).number(Math.max(0, lives)).build());
		}
	}

	// ---- Death and the gravestone ----

	/** The local player died, holding {@code carried}. Starts the value count and the gravestone's timer. */
	void died(Map<Integer, Integer> carried, String where, int world)
	{
		carriedAtDeath = new TreeMap<>(carried);
		settle = DEATH_SETTLE_TICKS;
		deathWhere = where;
		deathWorld = world;
		graveLeft = GRAVE_TICKS;
		graveSeen = false;
		graveItems = Collections.emptyMap();
		graveWhere = where;
		graveWorld = world;
	}

	/** Each tick after a death: once settled, what no longer is carried is what the death cost. */
	void deathTick(Map<Integer, Integer> carriedNow, ItemLookup lookup, long now, String me)
	{
		if (carriedAtDeath == null || --settle > 0)
		{
			return;
		}
		Map<Integer, Integer> lost = new TreeMap<>();
		for (Map.Entry<Integer, Integer> e : carriedAtDeath.entrySet())
		{
			int gone = e.getValue() - carriedNow.getOrDefault(e.getKey(), 0);
			if (gone > 0)
			{
				lost.put(e.getKey(), gone);
			}
		}
		carriedAtDeath = null;
		if (graveLeft > 0)
		{
			graveItems = lost;
			// Nothing left the player (nothing to lose, or a safe death): the game spawns no grave to warn about.
			if (lost.isEmpty())
			{
				graveLeft = 0;
			}
		}
		emit(GroupEvent.of(GroupEvent.Type.DEATH_VALUE, now, me).items(lost).number(EventText.value(lost, lookup))
			.location(deathWhere).world(deathWorld).detail("gravestone: 15 min").build());
	}

	/**
	 * Each tick while a grave may be out. {@code visible} is the game's grave varbit: once it has been seen on,
	 * its going off means the grave was collected. If it is never seen on, no grave spawned (a safe or instanced
	 * death) and nothing is warned (Board 2026-11-10: only warn when a gravestone exists for that death).
	 */
	void graveTick(int visible, int warnMinutes, long now, String me)
	{
		if (graveLeft <= 0)
		{
			return;
		}
		if (visible != 0)
		{
			graveSeen = true;
		}
		else if (graveSeen)
		{
			graveLeft = 0;
			return;
		}
		graveLeft--;
		if (graveLeft == warnMinutes * TICKS_PER_MINUTE)
		{
			graveLeft = 0;
			if (!graveSeen)
			{
				return;
			}
			emit(GroupEvent.of(GroupEvent.Type.GRAVESTONE_EXPIRING, now, me).number(warnMinutes).location(graveWhere)
				.world(graveWorld).items(graveItems).build());
		}
	}

	/** True while a grave is out and its warning has not gone yet. */
	boolean graveOut()
	{
		return graveLeft > 0;
	}
}
