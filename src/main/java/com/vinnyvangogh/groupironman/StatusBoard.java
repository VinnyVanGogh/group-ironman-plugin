package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The live online board (Board 2026-11-10): one message in the Group channel, one line per member, edited on
 * every login, logout and hop instead of a post for each.
 *
 * <p>Discord's webhook API (discord/discord-api-docs 36adecb, developers/resources/webhook.mdx): Execute Webhook
 * with {@code ?wait=true} "returns the created message body", whose {@code id} is kept; Edit Webhook Message is
 * {@code PATCH /webhooks/{id}/{token}/messages/{message.id}} and "edits a previously-sent webhook message from
 * the same token". It takes {@code content}, {@code embeds} and {@code allowed_mentions}, not {@code username}.
 *
 * <ul>
 * <li>The first send for a webhook posts the board and keeps its id ({@link #takeSaved}, this client's config
 * only, never the party). Every later send edits it; an edit Discord answers 404 (deleted) posts a new one.</li>
 * <li>At most one send per {@link #MIN_GAP_MS}, and none in quiet hours. Only the newest lines are ever sent,
 * and only when they changed.</li>
 * <li>"Last seen" is Discord's own relative timestamp ({@code <t:...:R>}), so the board stays right as time
 * passes without an edit.</li>
 * <li>Requests go through the {@link DiscordSender}'s queue like every post (its edit path for an edit), so the
 * board shares the queue's {@link DiscordQueue#PER_MINUTE} limit, its 429 and retry handling, and its
 * {@code thread_id}-keeping URLs. One request is with the queue at a time.</li>
 * </ul>
 *
 * The send state is guarded by this object: the timer sends, OkHttp's thread answers.
 */
final class StatusBoard
{
	static final long MIN_GAP_MS = TimeUnit.SECONDS.toMillis(10);
	static final String TITLE = "Group status";
	static final String ONLINE = "🟢";
	static final String OFFLINE = "🔴";
	static final String UNKNOWN = "⚪";
	/** After a member the game shows but whose client never reported: they do not run the plugin. */
	static final String NO_PLUGIN = " *(no plugin)*";
	private static final String SEP = "\t";

	/** This client's own player, which it knows better than the party does. */
	static final class Self
	{
		final String name;
		final boolean online;
		final int world;
		/** When it logged out; 0 while online. */
		final long seenAt;

		Self(String name, boolean online, int world, long seenAt)
		{
			this.name = name;
			this.online = online;
			this.world = world;
			this.seenAt = seenAt;
		}
	}

	/** One request: the board posted, or the one already posted edited. */
	static final class Send
	{
		final String url;
		/** Null to post a new board. */
		final String messageId;
		final String lines;
		final long changedAt;

		Send(String url, String messageId, String lines, long changedAt)
		{
			this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
			this.messageId = messageId;
			this.lines = lines;
			this.changedAt = changedAt;
		}

		boolean create()
		{
			return messageId == null;
		}
	}

	private String url;
	private String messageId;
	private boolean idChanged;
	private String lines;
	private long changedAt;
	private String sentLines;
	private long lastTry = Long.MIN_VALUE / 2;
	private long notBefore;
	private Send inFlight;

	/**
	 * The webhook the board lives on, null when there is none (Discord off, another mode). {@code saved} is
	 * {@link #takeSaved}'s line, read only when the webhook changes; a different webhook starts a new board.
	 */
	synchronized void target(String url, Supplier<String> saved)
	{
		if (url == null ? this.url == null : url.equals(this.url))
		{
			return;
		}
		this.url = url;
		messageId = url == null ? null : savedId(url, saved.get());
		sentLines = null;
		inFlight = null;
	}

	/** The board's lines now; the edit time moves only when they change. */
	synchronized void show(String now, long at)
	{
		if (!now.equals(lines))
		{
			lines = now;
			changedAt = at;
		}
	}

	/** The request to make now, or null: nothing new, one in flight, quiet hours, the gap or a Retry-After. */
	synchronized Send next(long now, boolean quiet)
	{
		if (url == null || lines == null || lines.equals(sentLines) || inFlight != null || quiet || now < notBefore
			|| now - lastTry < MIN_GAP_MS)
		{
			return null;
		}
		lastTry = now;
		inFlight = new Send(url, messageId, lines, changedAt);
		return inFlight;
	}

	/** How {@code send} went: an HTTP status (0 for none), the new message's id for a post, a 429's wait. */
	synchronized void done(Send send, int status, String newId, long retryAfterMs, long now)
	{
		if (send != inFlight)
		{
			// The webhook changed while it was out.
			return;
		}
		inFlight = null;
		if (status >= 200 && status < 300)
		{
			sent(send, newId);
		}
		else if (status == 404 && !send.create())
		{
			// Deleted in Discord: the next send posts a new board.
			messageId = null;
			idChanged = true;
		}
		else if (status == 429)
		{
			notBefore = now + (retryAfterMs >= 0 ? retryAfterMs : MIN_GAP_MS);
		}
		else if (status >= 400)
		{
			// A bad or deleted webhook: retrying cannot fix it, so wait for the lines to change.
			sentLines = send.lines;
		}
	}

	/** Holding the lock: Discord took {@code send}; a post's answer carries the board's id. */
	private void sent(Send send, String newId)
	{
		sentLines = send.lines;
		if (send.create() && newId != null)
		{
			messageId = newId;
			idChanged = true;
		}
	}

	/** The line to save when the board's id changed since the last call, "" to forget it, else null. */
	synchronized String takeSaved()
	{
		if (!idChanged)
		{
			return null;
		}
		idChanged = false;
		return messageId == null || url == null ? "" : tag(url) + SEP + messageId;
	}

	/** The webhook is kept only as a hash, so the saved line can tell a changed webhook without holding it. */
	private static String tag(String url)
	{
		return Integer.toHexString(url.hashCode());
	}

	private static String savedId(String url, String saved)
	{
		String[] f = saved == null ? new String[0] : saved.split(SEP, -1);
		return f.length == 2 && f[0].equals(tag(url)) && f[1].matches("\\d+") ? f[1] : null;
	}

	// ---- What it says ----

	/** {@link #lines(GroupMembers, Map, Map, Self, GamePresence, long)} without the game's Group channel. */
	static String lines(GroupMembers roster, Map<String, Long> heard, Map<String, MemberStats> stats, Self self,
		long now)
	{
		return lines(roster, heard, stats, self, GamePresence.UNKNOWN, now);
	}

	/**
	 * One line per member: the game's roster when known, else everyone heard from or in the channel. When the
	 * game says ({@link GamePresence}, the in-game Group channel) a member is online or offline, that is the line,
	 * with " (no plugin)" for a member whose client was never heard from. Otherwise a member is online while their
	 * client is heard from ({@link Presence}), offline with when it last was, and unknown when it never was.
	 * Empty when there is no one to list.
	 */
	static String lines(GroupMembers roster, Map<String, Long> heard, Map<String, MemberStats> stats, Self self,
		GamePresence game, long now)
	{
		Map<String, Long> heardBy = new HashMap<>();
		heard.forEach((name, at) -> heardBy.put(GroupMembers.key(name), at));
		Map<String, Integer> worldBy = new HashMap<>();
		stats.forEach((name, s) -> worldBy.put(GroupMembers.key(name), s.world()));
		List<String> out = new ArrayList<>();
		for (String name : names(roster, heard, self, game))
		{
			String key = GroupMembers.key(name);
			if (self != null && key.equals(GroupMembers.key(self.name)))
			{
				out.add(self.online ? online(name, self.world) : offline(name, self.seenAt));
				continue;
			}
			out.add(member(name, heardBy.getOrDefault(key, 0L), worldBy.get(key), game, now));
		}
		return String.join("\n", out);
	}

	/** Another member's line: the game first, then their client, else unknown. */
	private static String member(String name, long heardAt, Integer statsWorld, GamePresence game, long now)
	{
		boolean plugin = heardAt > 0 || statsWorld != null;
		int lastWorld = statsWorld == null ? 0 : statsWorld;
		String tag = plugin ? "" : NO_PLUGIN;
		switch (game.state(name))
		{
			case ONLINE:
				return online(name, game.world(name) > 0 ? game.world(name) : lastWorld) + tag;
			case OFFLINE:
				return offline(name, heardAt) + tag;
			default:
				break;
		}
		if (Presence.online(heardAt, now))
		{
			return online(name, lastWorld);
		}
		return plugin ? offline(name, heardAt) : UNKNOWN + " " + bold(name) + " unknown";
	}

	private static Set<String> names(GroupMembers roster, Map<String, Long> heard, Self self, GamePresence game)
	{
		if (roster.known())
		{
			return roster.names();
		}
		Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		names.addAll(heard.keySet());
		names.addAll(game.names());
		if (self != null && !self.name.isEmpty())
		{
			names.add(self.name);
		}
		return new LinkedHashSet<>(names);
	}

	private static String online(String name, int world)
	{
		return ONLINE + " " + bold(name) + " online" + (world > 0 ? " (world " + world + ")" : "");
	}

	private static String offline(String name, long seenAt)
	{
		return OFFLINE + " " + bold(name) + " offline" + (seenAt > 0 ? " (last seen " + when(seenAt) + ")" : "");
	}

	/** Discord's relative timestamp: "5 minutes ago", kept current by Discord itself. */
	private static String when(long at)
	{
		return "<t:" + at / 1000 + ":R>";
	}

	private static String bold(String name)
	{
		return "**" + name.replaceAll("([\\\\*_~`|>])", "\\\\$1") + "**";
	}

	/**
	 * The request body. Only the lines and the edit time go in: never the webhook. A new board is posted under
	 * {@code username}, the Group poster name ({@link PosterNames}), none when null; an edit takes no username.
	 */
	static String payload(Gson gson, Send send, DiscordStyle style, String username)
	{
		String text = send.lines + "\nUpdated " + when(send.changedAt);
		JsonObject o = new JsonObject();
		if (send.create() && username != null)
		{
			o.addProperty("username", username);
		}
		JsonObject allowed = new JsonObject();
		allowed.add("parse", new JsonArray());
		o.add("allowed_mentions", allowed);
		JsonArray embeds = new JsonArray();
		if (style == DiscordStyle.PLAIN)
		{
			o.addProperty("content", "**" + TITLE + "**\n" + text);
		}
		else
		{
			JsonObject embed = new JsonObject();
			embed.addProperty("title", TITLE);
			embed.addProperty("description", text);
			embed.addProperty("color", GroupEvent.Category.GROUP.colour);
			JsonObject footer = new JsonObject();
			footer.addProperty("text", DiscordWebhook.USERNAME);
			embed.add("footer", footer);
			embeds.add(embed);
			if (!send.create())
			{
				// An edit after a switch from plain text clears the old text.
				o.addProperty("content", "");
			}
		}
		if (!send.create() || embeds.size() > 0)
		{
			// An edit after a switch to plain text clears the old embed.
			o.add("embeds", embeds);
		}
		return gson.toJson(o);
	}

	/** Queues {@code send} with the sender: a post that waits for its id, or an edit; the outcome to {@link #done}. */
	void send(DiscordSender sender, Send send, String json, LongSupplier clock)
	{
		DiscordQueue.Result result = (status, id) -> done(send, status, id, -1, clock.getAsLong());
		if (send.create())
		{
			sender.post(send.url, json, null, result);
		}
		else
		{
			sender.edit(send.url, send.messageId, json, null, result);
		}
	}
}
