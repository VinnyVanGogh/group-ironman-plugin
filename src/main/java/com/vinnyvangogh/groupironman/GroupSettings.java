package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * One version of the group's shared settings, signed by the leader or an
 * editor, with a short history of who changed what and when.
 *
 * <p><b>Last writer wins, by version.</b> Each save is the previous version
 * plus one. Two editors saving at once both make the same version; every
 * client then keeps the one {@link #newerThan} picks (later save time, then
 * the larger signature), so all clients settle on the same document whatever
 * order they hear them in. The losing edit is not merged; its author sees the
 * winner replace it and can save again.
 */
final class GroupSettings
{
	/** History entries kept: enough to see the last few changes, small enough for one party message. */
	static final int MAX_HISTORY = 12;
	/** A save touching more than this many settings is one history line ("changed 14 settings"). */
	static final int MAX_LINES_PER_SAVE = 4;
	/** The history key of a save summarised as a count. */
	static final String MANY = "*";

	/** One line of history. For {@link #MANY}, {@code to} is how many settings changed. */
	static final class Change
	{
		final long at;
		final String by;
		final String key;
		final long from;
		final long to;

		Change(long at, String by, String key, long from, long to)
		{
			this.at = at;
			this.by = by;
			this.key = key;
			this.from = from;
			this.to = to;
		}

		String canonical()
		{
			return GroupSigner.canonical(at, by, key, from, to);
		}
	}

	final long version;
	final long at;
	final String by;
	final String byKey;
	/** Known keys only, clamped: see {@link GroupSettingsSchema#sanitize}. */
	final Map<String, Long> values;
	/** Newest first. */
	final List<Change> history;
	final String signature;

	GroupSettings(long version, long at, String by, String byKey, Map<String, Long> values, List<Change> history,
		String signature)
	{
		this.version = version;
		this.at = at;
		this.by = by;
		this.byKey = byKey;
		this.values = Collections.unmodifiableMap(new TreeMap<>(values));
		this.history = Collections.unmodifiableList(new ArrayList<>(
			history.subList(0, Math.min(MAX_HISTORY, history.size()))));
		this.signature = signature;
	}

	/**
	 * The version after {@code previous} (null for the group's first), with
	 * {@code values}, signed by {@code by}'s {@code id}. Whether {@code by} may
	 * sign it is the caller's check ({@link GroupGovernance}).
	 */
	static GroupSettings next(GroupSettings previous, Map<String, Long> values, String by, GroupSigner.Identity id,
		long now)
	{
		Map<String, Long> clean = GroupSettingsSchema.sanitize(values);
		List<Change> history = new ArrayList<>();
		history.addAll(changes(previous, clean, by, now));
		if (previous != null)
		{
			history.addAll(previous.history);
		}
		long version = previous == null ? 1 : previous.version + 1;
		long at = previous == null ? now : Math.max(now, previous.at);
		return sign(version, at, by, id, clean, history);
	}

	/** The same values as a new version signed by {@code id}: after the editor who saved them is revoked. */
	GroupSettings resign(String leader, GroupSigner.Identity id, long now)
	{
		List<Change> history = new ArrayList<>();
		history.add(new Change(Math.max(now, at), leader, "resigned:" + by, 0, 0));
		history.addAll(this.history);
		return sign(version + 1, Math.max(now, at), leader, id, values, history);
	}

	private static GroupSettings sign(long version, long at, String by, GroupSigner.Identity id,
		Map<String, Long> values, List<Change> history)
	{
		GroupSettings unsigned = new GroupSettings(version, at, by, id.publicKey(), values, history, "");
		return new GroupSettings(version, at, by, id.publicKey(), values, unsigned.history,
			id.sign(unsigned.canonical()));
	}

	private static List<Change> changes(GroupSettings previous, Map<String, Long> values, String by, long now)
	{
		if (previous == null)
		{
			return Collections.singletonList(new Change(now, by, MANY, 0, values.size()));
		}
		List<Change> out = new ArrayList<>();
		for (Map.Entry<String, Long> e : values.entrySet())
		{
			Long before = previous.values.get(e.getKey());
			if (!e.getValue().equals(before))
			{
				out.add(new Change(now, by, e.getKey(), before == null ? 0 : before, e.getValue()));
			}
		}
		if (out.size() > MAX_LINES_PER_SAVE)
		{
			return Collections.singletonList(new Change(now, by, MANY, 0, out.size()));
		}
		return out;
	}

	String canonical()
	{
		StringBuilder vals = new StringBuilder();
		for (Map.Entry<String, Long> e : values.entrySet())
		{
			vals.append(GroupSigner.canonical(e.getKey(), e.getValue()));
		}
		StringBuilder hist = new StringBuilder();
		for (Change c : history)
		{
			hist.append(c.canonical());
		}
		return GroupSigner.canonical("gimu-settings", 1, version, at, by, byKey, vals, hist);
	}

	/** Signed by the key it names; whether that key may edit is {@link GroupRoles#mayEdit}. */
	boolean verify()
	{
		return version >= 1 && !by.isEmpty() && GroupSigner.verify(byKey, canonical(), signature);
	}

	/** Same values as {@code other} (or both have none). */
	boolean sameValues(Map<String, Long> other)
	{
		return Objects.equals(values, GroupSettingsSchema.sanitize(other));
	}

	/** The total order every client uses to pick one document: version, then save time, then signature. */
	boolean newerThan(GroupSettings other)
	{
		if (other == null)
		{
			return true;
		}
		if (version != other.version)
		{
			return version > other.version;
		}
		if (at != other.at)
		{
			return at > other.at;
		}
		return signature.compareTo(other.signature) > 0;
	}
}
