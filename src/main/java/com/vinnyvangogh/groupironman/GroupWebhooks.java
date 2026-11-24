package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The leader's Discord webhooks, shared with the group (Board 2026-11-13: "Share webhooks with the group"). One
 * version, signed by the leader only (never an editor), carrying the seven webhooks' URLs and poster-name choices
 * while sharing is on, and nothing once it is turned off.
 *
 * <p><b>Who can read it.</b> Anyone in the group's party, which is anyone holding the group key: the same trust as
 * the key itself. It is never written into a member's RuneLite config, only kept in memory and in the group cache
 * file, and never logged: {@link #toString} names no URL.
 *
 * <p><b>Versions.</b> Each save is at least the previous version plus one, and at least the save time, so a leader
 * whose cache was lost still publishes a version newer than what members hold. Turning sharing off is a new version
 * with no webhooks, which is how members' copies are cleared on the next sync.
 */
final class GroupWebhooks
{
	/** Longer than any Discord webhook link; a longer field is dropped as not one. */
	static final int MAX_URL = 300;
	/**
	 * The signed form this build reads and writes. A build that changes it must send its number as {@code cv}, so
	 * this one can say "update to get shared webhooks" ({@link GovernanceCodec}) instead of refusing in silence.
	 */
	static final int FORMAT = 1;

	final String group;
	final long version;
	final long at;
	final String by;
	final String byKey;
	final boolean shared;
	/** Empty when not {@link #shared}; otherwise all seven, each URL a Discord webhook link or empty. */
	final Map<Webhook, Webhooks.Setting> settings;
	final String signature;
	/**
	 * Each webhook entry as the leader signed it, in the order it arrived; null for a document made here. A newer
	 * build may share a webhook or a poster-name choice this one does not know: it is left out of {@link #settings}
	 * but kept here, so the signature still checks and the webhooks this build knows still reach the member.
	 */
	private final List<String[]> signedEntries;

	GroupWebhooks(String group, long version, long at, String by, String byKey, boolean shared,
		Map<Webhook, Webhooks.Setting> settings, String signature)
	{
		this(group, version, at, by, byKey, shared, settings, signature, null);
	}

	/** As read from the party or the cache: {@code signedEntries} is every entry as sent ({@link #entries}). */
	GroupWebhooks(String group, long version, long at, String by, String byKey, boolean shared,
		Map<Webhook, Webhooks.Setting> settings, String signature, List<String[]> signedEntries)
	{
		this.signedEntries = signedEntries == null ? null : Collections.unmodifiableList(new ArrayList<>(signedEntries));
		this.group = group;
		this.version = version;
		this.at = at;
		this.by = by;
		this.byKey = byKey;
		this.shared = shared;
		Map<Webhook, Webhooks.Setting> s = new EnumMap<>(Webhook.class);
		if (shared)
		{
			s.putAll(settings);
		}
		this.settings = Collections.unmodifiableMap(s);
		this.signature = signature;
	}

	/**
	 * The version after {@code previous} (null for the first): {@code own}'s seven webhooks when {@code shared},
	 * none when not. Whether {@code by} may sign it is the caller's check ({@link GroupGovernance}).
	 */
	static GroupWebhooks next(GroupWebhooks previous, String group, boolean shared, Webhooks own, String by,
		GroupSigner.Identity id, long now)
	{
		Map<Webhook, Webhooks.Setting> s = new EnumMap<>(Webhook.class);
		if (shared)
		{
			for (Webhook w : Webhook.values())
			{
				s.put(w, clean(own.settings().get(w)));
			}
		}
		long version = Math.max(now, previous == null ? 1 : previous.version + 1);
		long at = previous == null ? now : Math.max(now, previous.at);
		GroupWebhooks unsigned = new GroupWebhooks(group, version, at, by, id.publicKey(), shared, s, "");
		return new GroupWebhooks(group, version, at, by, id.publicKey(), shared, s, id.sign(unsigned.canonical()));
	}

	/** A webhook as it travels: a URL only when it is a Discord webhook link, a name cut to Discord's limit. */
	static Webhooks.Setting clean(Webhooks.Setting s)
	{
		if (s == null)
		{
			return new Webhooks.Setting("", DiscordPosterName.WEBHOOK, "");
		}
		String url = s.url.length() <= MAX_URL && DiscordWebhook.valid(s.url) ? DiscordWebhook.clean(s.url) : "";
		return new Webhooks.Setting(url, s.mode, PosterNames.clean(s.name));
	}

	/** True when sharing {@code shared} with {@code own}'s webhooks would publish exactly what this holds. */
	boolean same(boolean shared, Webhooks own)
	{
		if (shared != this.shared)
		{
			return false;
		}
		if (!shared)
		{
			return true;
		}
		for (Webhook w : Webhook.values())
		{
			Webhooks.Setting mine = clean(own.settings().get(w));
			Webhooks.Setting held = settings.get(w);
			if (held == null || !mine.url.equals(held.url) || mine.mode != held.mode || !mine.name.equals(held.name))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Every webhook entry as it travels and is signed, in order: {webhook, URL, poster-name choice, Custom name}.
	 * As received when read from the party or the cache, so a newer build's entries go back out unchanged.
	 */
	List<String[]> entries()
	{
		if (signedEntries != null)
		{
			return signedEntries;
		}
		List<String[]> out = new ArrayList<>();
		for (Map.Entry<Webhook, Webhooks.Setting> e : settings.entrySet())
		{
			Webhooks.Setting s = e.getValue();
			out.add(new String[]{e.getKey().name(), s.url, s.mode.name(), s.name});
		}
		return out;
	}

	String canonical()
	{
		StringBuilder hooks = new StringBuilder();
		for (String[] e : entries())
		{
			hooks.append(GroupSigner.canonical(e[0], e[1], e[2], e[3]));
		}
		return GroupSigner.canonical("gimu-webhooks", FORMAT, group, version, at, by, byKey, shared ? 1 : 0, hooks);
	}

	/** Signed by the key it names; whether that key is the leader's is {@link GroupGovernance}'s check. */
	boolean verify()
	{
		return version >= 1 && !by.isEmpty() && GroupSigner.verify(byKey, canonical(), signature);
	}

	/** The total order every client uses to pick one document: version, then save time, then signature. */
	boolean newerThan(GroupWebhooks other)
	{
		return other == null || GroupSettings.newer(version, at, signature, other.version, other.at, other.signature);
	}

	@Override
	public String toString()
	{
		// Never a URL, even in a debug log.
		return "GroupWebhooks[v" + version + " by " + by + (shared ? ", shared" : ", not shared") + "]";
	}
}
