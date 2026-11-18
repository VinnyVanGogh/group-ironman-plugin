package com.vinnyvangogh.groupironman;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** One sample event of every type, with the fields its detector fills in, and settings built by hand. */
final class EventFixtures
{
	static final long NOW = 1_700_000_000_000L;

	private EventFixtures()
	{
	}

	static Map<Integer, Integer> items(int... idQty)
	{
		Map<Integer, Integer> out = new TreeMap<>();
		for (int i = 0; i + 1 < idQty.length; i += 2)
		{
			out.put(idQty[i], idQty[i + 1]);
		}
		return out;
	}

	/** A valid event of each type by "Alex", each passing the default thresholds of {@link #settings}. */
	static Map<GroupEvent.Type, GroupEvent> samples()
	{
		Map<GroupEvent.Type, GroupEvent> out = new EnumMap<>(GroupEvent.Type.class);
		for (GroupEvent.Type t : GroupEvent.Type.values())
		{
			out.put(t, sample(t));
		}
		return out;
	}

	static GroupEvent sample(GroupEvent.Type type)
	{
		GroupEvent.Builder b = GroupEvent.of(type, NOW, "Alex");
		switch (type.need)
		{
			case ITEMS:
				b.text("Vorkath").items(items(FakeItems.WHIP, 1)).number(2_000_000);
				break;
			case TEXT:
				b.text("Attack").number(80);
				break;
			default:
				b.number(5);
				break;
		}
		return b.build();
	}

	static EventSettings settings(Set<GroupEvent.Type> discord, Set<GroupEvent.Type> overlay)
	{
		return new EventSettings(discord, overlay, 1_000_000, 1_000_000, 50, 7, false);
	}

	static Set<GroupEvent.Type> all()
	{
		return EnumSet.allOf(GroupEvent.Type.class);
	}

	static Set<GroupEvent.Type> none()
	{
		return EnumSet.noneOf(GroupEvent.Type.class);
	}
}
