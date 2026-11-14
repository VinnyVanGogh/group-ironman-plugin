package com.vinnyvangogh.scenarios;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.party.events.UserPart;

/**
 * RuneLite events that are plain values, so a scenario can build them with no game behind them. Each is built
 * with the constructor or setters RuneLite's own code uses (Lombok {@code @Value} / {@code @Data} classes in
 * runelite-api and runelite-client); see TESTING.md for the table of what can and cannot be faked.
 *
 * <p>Not here, on purpose: {@code ActorDeath} and {@code NpcLootReceived} need a live {@code Actor} / {@code NPC},
 * and plugins compare them to {@code client.getLocalPlayer()}; a stand-in would never be "you". Fake those at the
 * plugin's own seam (a plugin kind) instead.
 */
final class CoreKinds
{
	private static final String NO_TICKS = "a fake GameTick would run every plugin an extra tick; the game ticks for "
		+ "you in the client";
	private static final String NO_GAME_STATE = "a fake game state change would make every plugin think you logged "
		+ "in or out";

	private CoreKinds()
	{
	}

	static void register(EventKinds kinds)
	{
		kinds.add("chat", f -> new ChatMessage(null, f.enumValue("type", ChatMessageType.class), f.string("name", ""),
			f.string("message"), f.string("sender", null), f.integer("timestamp", 0)));
		kinds.add("stat", f ->
		{
			int level = f.integer("level");
			return new StatChanged(f.enumValue("skill", Skill.class), f.integer("xp", 0), level,
				f.integer("boosted", level));
		});
		kinds.add("container", f -> new ItemContainerChanged(f.integer("id"),
			new FakeItemContainer(f.integer("id"), items(f.items("items")))));
		kinds.add("widgetLoaded", f ->
		{
			WidgetLoaded e = new WidgetLoaded();
			e.setGroupId(f.integer("group"));
			return e;
		});
		kinds.add("widgetClosed", f -> new WidgetClosed(f.integer("group"), f.integer("modalMode", 0),
			f.bool("unload", true)));
		kinds.add("varbit", f -> varbit(f.integer("varbit"), -1, f.integer("value")));
		kinds.add("varp", f -> varbit(-1, f.integer("varp"), f.integer("value")));
		kinds.add("userPart", f -> new UserPart(f.longValue("memberId", 0)));
		kinds.add("tick", EventFactory.headless(NO_TICKS, f -> new GameTick()));
		kinds.add("gameState", EventFactory.headless(NO_GAME_STATE, f ->
		{
			GameStateChanged e = new GameStateChanged();
			e.setGameState(f.enumValue("state", GameState.class));
			return e;
		}));
	}

	private static VarbitChanged varbit(int varbitId, int varpId, int value)
	{
		VarbitChanged e = new VarbitChanged();
		e.setVarbitId(varbitId);
		e.setVarpId(varpId);
		e.setValue(value);
		return e;
	}

	private static List<Item> items(Map<Integer, Integer> counts)
	{
		List<Item> out = new ArrayList<>();
		counts.forEach((id, qty) -> out.add(new Item(id, qty)));
		return out;
	}
}
