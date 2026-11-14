package com.vinnyvangogh.groupironman;

import com.vinnyvangogh.scenarios.EventFactory;
import com.vinnyvangogh.scenarios.EventKinds;
import com.vinnyvangogh.scenarios.Fields;
import java.util.HashSet;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;

/**
 * GIM Utilities' scenario vocabulary: RuneLite's core kinds, the interface and item names its scenarios use, and
 * three kinds of its own for what cannot be posted as a RuneLite event (see TESTING.md).
 */
final class GimEventKinds
{
	private static final String RIG_ONLY = "reaches GimScenarioRig only; the live plugin reads this from the client";

	private GimEventKinds()
	{
	}

	static EventKinds kinds()
	{
		EventKinds kinds = EventKinds.core()
			.constant("SHARED_BANK", InterfaceID.SHARED_BANK)
			.constant("BANKMAIN", InterfaceID.BANKMAIN)
			.constant("INV_GROUP_TEMP", InventoryID.INV_GROUP_TEMP)
			.constant("SHARK", FakeItems.SHARK)
			.constant("COAL", FakeItems.COAL)
			.constant("PRAYER_POT_4", FakeItems.PRAYER_POT_4)
			.constant("WHIP", FakeItems.WHIP)
			.constant("COINS", FakeItems.COINS)
			.constant("LOBSTER", FakeItems.LOBSTER)
			.constant("SUPER_RESTORE_4", FakeItems.SUPER_RESTORE_4);
		kinds.add("groupEvent", EventFactory.headless(RIG_ONLY, GimEventKinds::groupEvent));
		kinds.add("groupChannel", EventFactory.headless(RIG_ONLY, f -> new GimScenarioRig.Channel(
			f.named("online"), new HashSet<>(f.strings("plugin")), f.strings("roster"))));
		kinds.add("settingsSave", EventFactory.headless(RIG_ONLY, f -> new GimScenarioRig.SettingsSave(
			f.string("member"), f.longs("values"))));
		return kinds;
	}

	/**
	 * A drop, death or any other detected event of the plugin's own, as the engine builds it once a detector has
	 * fired. The detectors themselves read the client (an NPC, the local player) and are not faked.
	 */
	private static GroupEvent groupEvent(Fields f)
	{
		GroupEvent.Builder b = GroupEvent.of(f.enumValue("type", GroupEvent.Type.class), EventFixtures.NOW,
			f.string("member"));
		if (f.has("text"))
		{
			b.text(f.string("text"));
		}
		if (f.has("items"))
		{
			b.items(f.items("items"));
		}
		if (f.has("number"))
		{
			b.number(f.longValue("number", 0));
		}
		if (f.has("location"))
		{
			b.location(f.string("location"));
		}
		if (f.has("world"))
		{
			b.world(f.integer("world"));
		}
		return b.build();
	}
}
