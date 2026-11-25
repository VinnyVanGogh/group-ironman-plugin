package com.vinnyvangogh.groupironman;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * The weekly recap: when it is due, and one player's week tallied for it. The daily digest itself (each
 * category's lines, held and posted once a day) is Discord Relay's: GIM marks which events go in it
 * ({@code digest} on the {@code event} message, docs/contracts/discord-relay-v1.md).
 */
final class Digest
{
	private Digest()
	{
	}

	/** This Monday's recap time has passed and the last recap was before it. A first run ({@code last} 0) only baselines. */
	static boolean weeklyDue(long last, long now, int hour, ZoneId zone)
	{
		if (last <= 0)
		{
			return false;
		}
		ZonedDateTime monday = Instant.ofEpochMilli(now).atZone(zone)
			.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).withHour(hour).withMinute(0).withSecond(0)
			.withNano(0);
		long at = monday.toInstant().toEpochMilli();
		return now >= at && last < at;
	}

	/** One player's week: XP since the first reading, and counts. Saved per account. */
	static final class Week
	{
		private long xpStart = -1;
		private long xpNow;
		private int drops;
		private int kills;
		private int deposits;
		private int withdrawals;

		private Week()
		{
		}

		void xp(long total)
		{
			if (xpStart < 0)
			{
				xpStart = total;
			}
			xpNow = total;
		}

		void drop()
		{
			drops++;
		}

		void kill()
		{
			kills++;
		}

		void storage(boolean deposit)
		{
			if (deposit)
			{
				deposits++;
			}
			else
			{
				withdrawals++;
			}
		}

		GroupEvent recap(long at, String member)
		{
			return GroupEvent.of(GroupEvent.Type.WEEKLY_RECAP, at, member)
				.text(drops + " drops, " + kills + " kills, " + deposits + " deposits, " + withdrawals + " withdrawals")
				.number(xpStart < 0 ? 0 : Math.max(0, xpNow - xpStart))
				.build();
		}

		void startOver()
		{
			xpStart = xpNow;
			drops = 0;
			kills = 0;
			deposits = 0;
			withdrawals = 0;
		}

		String save()
		{
			return xpStart + "," + xpNow + "," + drops + "," + kills + "," + deposits + "," + withdrawals;
		}

		static Week parse(String saved)
		{
			Week w = new Week();
			String[] p = (saved == null ? "" : saved).split(",");
			if (p.length != 6)
			{
				return w;
			}
			try
			{
				w.xpStart = Long.parseLong(p[0]);
				w.xpNow = Long.parseLong(p[1]);
				w.drops = Integer.parseInt(p[2]);
				w.kills = Integer.parseInt(p[3]);
				w.deposits = Integer.parseInt(p[4]);
				w.withdrawals = Integer.parseInt(p[5]);
				return w;
			}
			catch (NumberFormatException e)
			{
				return new Week();
			}
		}
	}
}
