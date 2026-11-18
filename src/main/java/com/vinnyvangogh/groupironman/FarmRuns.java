package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/**
 * Timers for this player's herb, tree and Hespori patches and bird houses.
 *
 * <p>Follows the core Time Tracking plugin (runelite-client, master, read
 * 2026-11-09, BSD 2-Clause; see THIRD_PARTY_NOTICES.md): the growth values and
 * cycles are {@code PatchImplementation} and {@code Produce}, the places are
 * {@code FarmingWorld}, the ready time is {@code FarmingTracker.getTickTime},
 * and the bird house rules are {@code BirdHouseTracker}.
 *
 * <p>Two things are not modelled. Time Tracking learns each account's growth
 * offset by watching a patch change, and without it a crop is called ready at
 * the unshifted cycle, which is no earlier than the true one. And a crop can
 * catch disease out of sight, so "ready" means it will have finished growing
 * if it stayed healthy, as in Time Tracking.
 *
 * <p>Pure: the client's values come in as arguments and events leave through
 * one consumer that a kind's switch guards. Client thread only.
 */
final class FarmRuns
{
	/** A patch's reading is rechecked this often while the player stands near it, as Time Tracking does. */
	static final long REFRESH_SECONDS = 5 * 60L;
	private static final int HERB_STAGES = 5;
	private static final int HERB_MINUTES = 20;
	private static final int TREE_MINUTES = 40;
	private static final int HESPORI_STAGES = 4;
	private static final int HESPORI_MINUTES = 640;
	private static final int BIRD_SECONDS = 50 * 60;
	/** Nine kinds of bird house, three values each ({@code BirdHouseState}). */
	private static final int BIRD_MAX_VALUE = 27;
	/** Dropping from three or four houses to none only happens when the varps have not arrived yet. */
	private static final int BIRD_MAX_REMOVED = 2;
	private static final int[] HERB_STARTS = {4, 11, 18, 25, 32, 39, 46, 53, 60, 68, 75, 82, 89, 96, 103, 192};
	private static final int[][] TREES = {{8, 5}, {15, 7}, {24, 9}, {35, 11}, {48, 13}};
	private static final int HESPORI_START = 4;
	private static final int PLANE_GROUND = 0;
	private static final List<Integer> FOSSIL_ISLAND = Arrays.asList(14650, 14651, 14652, 14906, 14907, 15162, 15163);
	private static final Bounds ANYWHERE = (x, y, plane) -> true;

	enum Kind
	{
		HERB("Herbs", GroupEvent.Type.FARMING),
		TREE("Trees", GroupEvent.Type.FARMING),
		HESPORI("Hespori", GroupEvent.Type.BIRDHOUSE),
		BIRD("Bird houses", GroupEvent.Type.BIRDHOUSE);

		final String label;
		final GroupEvent.Type type;

		Kind(String label, GroupEvent.Type type)
		{
			this.label = label;
			this.type = type;
		}
	}

	@FunctionalInterface
	private interface Bounds
	{
		boolean contains(int x, int y, int plane);
	}

	private static final class Patch
	{
		final String key;
		final String place;
		final int variable;
		final Kind kind;
		final Bounds bounds;

		Patch(String place, int region, int variable, Kind kind, Bounds bounds)
		{
			this.key = region + "." + variable;
			this.place = place;
			this.variable = variable;
			this.kind = kind;
			this.bounds = bounds;
		}
	}

	/** A crop still growing, and when it will have finished. */
	static final class Crop
	{
		final int stage;
		final int stages;
		private final int minutes;

		private Crop(int stage, int stages, int minutes)
		{
			this.stage = stage;
			this.stages = stages;
			this.minutes = minutes;
		}

		long readyAt(long seen)
		{
			if (minutes == 0)
			{
				return seen + BIRD_SECONDS;
			}
			long cycle = minutes * 60L;
			return seen - seen % cycle + (stages - 1 - stage) * cycle;
		}
	}

	private static final class Timer
	{
		final Patch patch;
		final int value;
		long seen;
		long readyAt;
		boolean fired;

		Timer(Patch patch, int value, long seen, long readyAt, boolean fired)
		{
			this.patch = patch;
			this.value = value;
			this.seen = seen;
			this.readyAt = readyAt;
			this.fired = fired;
		}
	}

	private static final Map<Integer, List<Patch>> PATCHES = new HashMap<>();
	private static final Map<String, Patch> BY_KEY = new HashMap<>();
	private static final List<Patch> BIRDS = new ArrayList<>();

	static
	{
		herb("Ardougne", VarbitID.FARMING_TRANSMIT_D, 10548);
		patch("Catherby", VarbitID.FARMING_TRANSMIT_D, Kind.HERB, FarmRuns::catherbyGround, 11062, 11061, 11318, 11317);
		herb("Civitas illa Fortis", VarbitID.FARMING_TRANSMIT_D, 6192, 6447, 6448, 6449, 6191, 6193);
		// Not on the region's edge: south of here is Port Sarim's spirit tree patch.
		patch("Falador", VarbitID.FARMING_TRANSMIT_D, Kind.HERB, (x, y, plane) -> y >= 3272, 12083);
		herb("Kourend", VarbitID.FARMING_TRANSMIT_D, 6967, 6711);
		herb("Morytania", VarbitID.FARMING_TRANSMIT_D, 14391, 14390);
		herb("Harmony", VarbitID.FARMING_TRANSMIT_B, 15148);
		herb("Troll Stronghold", VarbitID.FARMING_TRANSMIT_A, 11321);
		herb("Weiss", VarbitID.FARMING_TRANSMIT_A, 11325);
		herb("Farming Guild", VarbitID.FARMING_TRANSMIT_E, 4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667);

		tree("Auburnvale", VarbitID.FARMING_TRANSMIT_A, 5427, 5428, 5684);
		tree("Falador", VarbitID.FARMING_TRANSMIT_A, 11828, 12084);
		tree("Gnome Stronghold", VarbitID.FARMING_TRANSMIT_A, 9781, 9782, 9526, 9525);
		tree("Lumbridge", VarbitID.FARMING_TRANSMIT_A, 12594, 12850);
		tree("Taverley", VarbitID.FARMING_TRANSMIT_A, 11573, 11829);
		tree("Varrock", VarbitID.FARMING_TRANSMIT_A, 12854, 12853);
		tree("Farming Guild", VarbitID.FARMING_TRANSMIT_G, 4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667);

		patch("Farming Guild", VarbitID.FARMING_TRANSMIT_J, Kind.HESPORI, ANYWHERE, 5021);

		bird("Mushroom Meadow (North)", VarPlayerID.BIRDHOUSE_TRANSMIT_A);
		bird("Mushroom Meadow (South)", VarPlayerID.BIRDHOUSE_TRANSMIT_B);
		bird("Verdant Valley (Northeast)", VarPlayerID.BIRDHOUSE_TRANSMIT_C);
		bird("Verdant Valley (Southwest)", VarPlayerID.BIRDHOUSE_TRANSMIT_D);
	}

	private static boolean catherbyGround(int x, int y, int plane)
	{
		// Upstairs in this corner the same variables mean another patch.
		if (x >= 2816 && y < 3456)
		{
			return x < 2840 && y >= 3440 && plane == PLANE_GROUND;
		}
		return true;
	}

	private static void herb(String place, int variable, int... regions)
	{
		patch(place, variable, Kind.HERB, ANYWHERE, regions);
	}

	private static void tree(String place, int variable, int... regions)
	{
		patch(place, variable, Kind.TREE, ANYWHERE, regions);
	}

	private static void patch(String place, int variable, Kind kind, Bounds bounds, int... regions)
	{
		Patch patch = new Patch(place, regions[0], variable, kind, bounds);
		BY_KEY.put(patch.key, patch);
		for (int region : regions)
		{
			PATCHES.computeIfAbsent(region, r -> new ArrayList<>()).add(patch);
		}
	}

	private static void bird(String place, int varp)
	{
		Patch patch = new Patch(place, FOSSIL_ISLAND.get(1), varp, Kind.BIRD, ANYWHERE);
		BY_KEY.put(patch.key, patch);
		BIRDS.add(patch);
	}

	private final Consumer<GroupEvent> out;
	private final Predicate<GroupEvent.Type> wanted;
	private final Map<String, Timer> timers = new TreeMap<>();
	private boolean dirty;

	FarmRuns(Consumer<GroupEvent> out, Predicate<GroupEvent.Type> wanted)
	{
		this.out = out;
		this.wanted = wanted;
	}

	/** The crop for a patch variable's value; null for weeds, a ready crop, disease, or anything else not growing. */
	static Crop crop(Kind kind, int value)
	{
		switch (kind)
		{
			case HERB:
				return herbCrop(value);
			case TREE:
				return treeCrop(value);
			case HESPORI:
				return value >= HESPORI_START && value < HESPORI_START + HESPORI_STAGES - 1
					? new Crop(value - HESPORI_START, HESPORI_STAGES, HESPORI_MINUTES) : null;
			default:
				return value > 0 && value <= BIRD_MAX_VALUE && value % 3 == 0 ? new Crop(0, 1, 0) : null;
		}
	}

	private static Crop herbCrop(int value)
	{
		for (int start : HERB_STARTS)
		{
			if (value >= start && value < start + HERB_STAGES - 1)
			{
				return new Crop(value - start, HERB_STAGES, HERB_MINUTES);
			}
		}
		return null;
	}

	private static Crop treeCrop(int value)
	{
		for (int[] tree : TREES)
		{
			int start = tree[0];
			int stages = tree[1];
			if (value >= start && value < start + stages - 1)
			{
				return new Crop(value - start, stages, TREE_MINUTES);
			}
		}
		return null;
	}

	/** Reads the variables that mean something where the player is standing. */
	void observe(int region, int x, int y, int plane, IntUnaryOperator varbits, IntUnaryOperator varps, long now)
	{
		for (Patch patch : PATCHES.getOrDefault(region, Collections.emptyList()))
		{
			if (patch.bounds.contains(x, y, plane) && wanted.test(patch.kind.type))
			{
				record(patch, varbits.applyAsInt(patch.variable), now);
			}
		}
		if (plane == PLANE_GROUND && FOSSIL_ISLAND.contains(region) && wanted.test(GroupEvent.Type.BIRDHOUSE))
		{
			birds(varps, now);
		}
	}

	private void birds(IntUnaryOperator varps, long now)
	{
		int removed = 0;
		for (Patch patch : BIRDS)
		{
			if (varps.applyAsInt(patch.variable) <= 0 && timers.containsKey(patch.key))
			{
				removed++;
			}
		}
		if (removed > BIRD_MAX_REMOVED)
		{
			return;
		}
		for (Patch patch : BIRDS)
		{
			record(patch, varps.applyAsInt(patch.variable), now);
		}
	}

	private void record(Patch patch, int value, long now)
	{
		Timer timer = timers.get(patch.key);
		if (timer != null && timer.value == value)
		{
			refresh(timer, now);
			return;
		}
		Crop crop = crop(patch.kind, value);
		if (crop != null)
		{
			timers.put(patch.key, new Timer(patch, value, now, crop.readyAt(now), false));
			dirty = true;
		}
		else if (timers.remove(patch.key) != null)
		{
			dirty = true;
		}
	}

	private void refresh(Timer timer, long now)
	{
		if (timer.fired || timer.patch.kind == Kind.BIRD || now - timer.seen < REFRESH_SECONDS)
		{
			return;
		}
		Crop crop = crop(timer.patch.kind, timer.value);
		timer.seen = now;
		timer.readyAt = crop.readyAt(now);
		dirty = true;
	}

	/** Fires whatever has finished, one event per kind, once each. */
	void due(long now, String me)
	{
		Map<Kind, List<Timer>> ready = new EnumMap<>(Kind.class);
		for (Timer timer : timers.values())
		{
			if (!timer.fired && timer.readyAt <= now)
			{
				ready.computeIfAbsent(timer.patch.kind, k -> new ArrayList<>()).add(timer);
			}
		}
		if (birdStillGrowing(now))
		{
			ready.remove(Kind.BIRD);
		}
		ready.forEach((kind, done) -> fire(kind, done, now, me));
	}

	/** Bird houses are one job: Time Tracking reports them when the last has finished. */
	private boolean birdStillGrowing(long now)
	{
		return timers.values().stream().anyMatch(t -> t.patch.kind == Kind.BIRD && !t.fired && t.readyAt > now);
	}

	private void fire(Kind kind, List<Timer> done, long now, String me)
	{
		List<String> places = new ArrayList<>();
		for (Timer timer : done)
		{
			timer.fired = true;
			places.add(timer.patch.place);
		}
		dirty = true;
		if (wanted.test(kind.type))
		{
			out.accept(GroupEvent.of(kind.type, now * 1000L, me).text(kind.label).number(done.size())
				.detail(kind == Kind.BIRD ? "" : places(places)).build());
		}
	}

	/** The places, or a count when they would not fit in an event's text. */
	static String places(List<String> places)
	{
		String joined = String.join(", ", places);
		return joined.length() <= GroupEvent.MAX_TEXT ? joined : places.size() + " patches";
	}

	/** True once, after a change worth saving. */
	boolean takeDirty()
	{
		boolean was = dirty;
		dirty = false;
		return was;
	}

	String save()
	{
		StringBuilder saved = new StringBuilder();
		for (Timer t : timers.values())
		{
			saved.append(t.patch.key).append('=').append(t.value).append(',').append(t.seen).append(',')
				.append(t.readyAt).append(',').append(t.fired ? 1 : 0).append(';');
		}
		return saved.toString();
	}

	/** Restores saved timers; anything that does not read back is dropped. */
	static FarmRuns parse(String saved, Consumer<GroupEvent> out, Predicate<GroupEvent.Type> wanted)
	{
		FarmRuns farm = new FarmRuns(out, wanted);
		if (saved != null)
		{
			for (String entry : saved.split(";"))
			{
				farm.restore(entry);
			}
		}
		return farm;
	}

	private void restore(String entry)
	{
		String[] parts = entry.split("=", 2);
		Patch patch = BY_KEY.get(parts[0]);
		if (patch == null || parts.length != 2)
		{
			return;
		}
		String[] fields = parts[1].split(",");
		if (fields.length != 4)
		{
			return;
		}
		try
		{
			timers.put(patch.key, new Timer(patch, Integer.parseInt(fields[0]), Long.parseLong(fields[1]),
				Long.parseLong(fields[2]), "1".equals(fields[3])));
		}
		catch (NumberFormatException e)
		{
			// A damaged entry is the same as none: the next visit records it again.
		}
	}
}
