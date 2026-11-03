package com.vinnyvangogh.buildguard;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Decides which group payloads this client is willing to send, and is the
 * only place that decides it, the same way {@link PartyOutbox} does for the
 * build.
 *
 * <p>Each feature has its own switch and each is off by default. A switch that
 * is off means nothing of that kind leaves this client: not an empty payload,
 * no message at all.
 */
final class GroupOutbox
{
	static final class Switches
	{
		final boolean bank;
		final boolean storage;
		final boolean stats;
		final boolean carried;

		Switches(boolean bank, boolean storage, boolean stats, boolean carried)
		{
			this.bank = bank;
			this.storage = storage;
			this.stats = stats;
			this.carried = carried;
		}

		boolean any()
		{
			return bank || storage || stats || carried;
		}
	}

	private GroupOutbox()
	{
	}

	static List<String> bank(Gson gson, Switches switches, BankSnapshot bank)
	{
		if (!switches.bank || bank == null)
		{
			return Collections.emptyList();
		}
		return GroupCodec.encodeBank(gson, bank);
	}

	/** Null when storage sharing is off or the visit changed nothing. */
	static String storage(Gson gson, Switches switches, StorageChange change)
	{
		if (!switches.storage || change == null || change.isEmpty())
		{
			return null;
		}
		return GroupCodec.encodeStorage(gson, change);
	}

	static String stats(Gson gson, Switches switches, MemberStats stats)
	{
		if (!switches.stats || stats == null)
		{
			return null;
		}
		return GroupCodec.encodeStats(gson, stats);
	}

	static String carried(Gson gson, Switches switches, Map<Integer, Integer> inventory, Map<Integer, Integer> worn)
	{
		if (!switches.carried)
		{
			return null;
		}
		return GroupCodec.encodeCarried(gson, inventory, worn);
	}
}
