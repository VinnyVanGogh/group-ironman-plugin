package com.vinnyvangogh.groupironman;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.StringJoiner;

/**
 * One member's own opt-outs, applied on top of the group's settings and never
 * overridden by them: "Don't post my events", per-kind "don't post", and
 * overlay mutes.
 *
 * <p>"Post" means this member's own events to Discord (instant and digest):
 * the only posts a client ever makes. Overlay mutes hide a kind's banner on
 * this client only. The two posting choices travel in the member's announce so
 * the leader and editors can see a read-only grid; mutes stay local.
 */
final class MemberPrefs
{
	static final MemberPrefs NONE = new MemberPrefs(false, EnumSet.noneOf(GroupEvent.Type.class),
		EnumSet.noneOf(GroupEvent.Type.class));

	/** "Don't post my events": nothing of this member's goes to Discord. */
	final boolean all;
	final Set<GroupEvent.Type> noPost;
	final Set<GroupEvent.Type> mute;

	MemberPrefs(boolean all, Set<GroupEvent.Type> noPost, Set<GroupEvent.Type> mute)
	{
		this.all = all;
		this.noPost = Collections.unmodifiableSet(copy(noPost));
		this.mute = Collections.unmodifiableSet(copy(mute));
	}

	static MemberPrefs of(GroupIronmanConfig config)
	{
		return new MemberPrefs(config.eventOptOutAll(), parse(config.eventOptOutTypes()),
			parse(config.overlayMuteTypes()));
	}

	/** Whether this member's own events of {@code type} may be posted. */
	boolean posts(GroupEvent.Type type)
	{
		return !all && !noPost.contains(type);
	}

	/** Kinds by wire code, comma separated; unknown codes are skipped. */
	static Set<GroupEvent.Type> parse(String codes)
	{
		Set<GroupEvent.Type> out = EnumSet.noneOf(GroupEvent.Type.class);
		if (codes == null)
		{
			return out;
		}
		for (String code : codes.split(","))
		{
			GroupEvent.Type t = GroupEvent.Type.of(code.trim());
			if (t != null)
			{
				out.add(t);
			}
		}
		return out;
	}

	static String format(Set<GroupEvent.Type> types)
	{
		StringJoiner out = new StringJoiner(",");
		for (GroupEvent.Type t : copy(types))
		{
			out.add(t.code);
		}
		return out.toString();
	}

	private static Set<GroupEvent.Type> copy(Set<GroupEvent.Type> types)
	{
		return types.isEmpty() ? EnumSet.noneOf(GroupEvent.Type.class) : EnumSet.copyOf(types);
	}
}
