package com.vinnyvangogh.groupironman;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The panel's "Turn on sharing" card (Board 2026-11-13): a member who pastes
 * the group key is one click from fully set up, instead of ticking every Share
 * switch by hand. Nothing turns on without the click.
 *
 * <p>The card opens for a key when that key is pasted, or when sync is on with
 * a key and every Share switch is off. It stays open for that key while it
 * still has a button to offer; "Not now" closes it until the key changes. Swing
 * thread only.
 */
final class SharingPrompt
{
	/**
	 * What "Turn on sharing" sets to true, in the settings' own order: Sync with my group and every Share switch.
	 * Existing keys, read here and never renamed (ConfigKeysTest freezes them).
	 */
	static final List<String> SHARING_KEYS = Collections.unmodifiableList(Arrays.asList("groupSync",
		"groupShareBank", "groupShareStorage", "groupShareStats", "groupShareCarried", "groupShareExtras",
		"groupShareDrops", "groupShareEvents"));
	/** What "Post my events to Discord" sets to true. */
	static final String DISCORD_KEY = "discordEnabled";

	static final String TEXT = "Your group sees only what you share. 'Turn on sharing' ticks Sync with my group and"
		+ " every Share switch: bank, group storage changes, stats, inventory and equipment, pouch, quiver and vault,"
		+ " drops and collection log, and group events. Untick any of them later in settings.";

	/** The settings the card reads, captured with each redraw. */
	static final class Settings
	{
		/** The group key as saved, normalised; empty without one. */
		final String key;
		final boolean sync;
		/** At least one Share switch is on. */
		final boolean anyShare;
		/** Sync and every Share switch are on: nothing left for "Turn on sharing" to do. */
		final boolean allSharing;
		final boolean discordOn;
		/** A Discord webhook to post to: one of this player's own. */
		final boolean webhook;

		Settings(String key, boolean sync, boolean anyShare, boolean allSharing, boolean discordOn, boolean webhook)
		{
			this.key = key == null ? "" : key;
			this.sync = sync;
			this.anyShare = anyShare;
			this.allSharing = allSharing;
			this.discordOn = discordOn;
			this.webhook = webhook;
		}

		static Settings of(GroupIronmanConfig c)
		{
			boolean[] shares = {c.groupShareBank(), c.groupShareStorage(), c.groupShareStats(), c.groupShareCarried(),
				c.groupShareExtras(), c.groupShareDrops(), c.groupShareEvents()};
			boolean any = false;
			boolean all = true;
			for (boolean s : shares)
			{
				any |= s;
				all &= s;
			}
			return new Settings(GroupKey.normalize(c.groupKey()), c.groupSync(), any, all && c.groupSync(),
				c.discordEnabled(), anyWebhook(Webhooks.of(c)));
		}

		private static boolean anyWebhook(Webhooks hooks)
		{
			for (Webhook w : Webhook.values())
			{
				if (hooks.url(w) != null)
				{
					return true;
				}
			}
			return false;
		}
	}

	/** What the card shows: which of its two buttons. */
	static final class Card
	{
		final boolean sharing;
		final boolean discord;

		Card(boolean sharing, boolean discord)
		{
			this.sharing = sharing;
			this.discord = discord;
		}
	}

	/** "Turn on sharing": exactly {@link #SHARING_KEYS}, each set to true through {@code set}. */
	static void turnOnSharing(BiConsumer<String, Object> set)
	{
		for (String key : SHARING_KEYS)
		{
			set.accept(key, true);
		}
	}

	/** "Post my events to Discord": the Post to Discord switch only. */
	static void postToDiscord(BiConsumer<String, Object> set)
	{
		set.accept(DISCORD_KEY, true);
	}

	/** The key the card is open for; null when closed. */
	private String openFor;
	/** The key "Not now" was pressed for; null when never. */
	private String dismissedFor;

	/** A successful "Paste group key": opens the card for that key. */
	void pasted(String key)
	{
		if (key != null && !key.isEmpty())
		{
			openFor = key;
		}
	}

	/** "Not now": closed until the key changes. */
	void notNow(String key)
	{
		dismissedFor = key;
		openFor = null;
	}

	/** The card to draw now, or null for none. */
	Card card(Settings s)
	{
		if (s.key.isEmpty() || s.key.equals(dismissedFor))
		{
			return null;
		}
		if (s.sync && !s.anyShare)
		{
			openFor = s.key;
		}
		if (!s.key.equals(openFor))
		{
			return null;
		}
		Card card = new Card(!s.allSharing, s.webhook && !s.discordOn);
		if (!card.sharing && !card.discord)
		{
			// Done: a later change to one switch does not bring it back.
			openFor = null;
			return null;
		}
		return card;
	}
}
