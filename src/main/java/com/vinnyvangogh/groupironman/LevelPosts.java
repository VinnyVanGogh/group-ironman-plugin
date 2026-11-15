package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * {@link LevelThreads}' decisions sent through the {@link DiscordSender}: a member's message's first post (kept by
 * its message id), its owed edits, and the new post that replaces a message Discord no longer has. Any thread.
 */
final class LevelPosts
{
	/** 'Combine level-ups' before Board 2026-11-14 made it 60: what an install that had the plugin keeps. */
	static final int OLD_DEFAULT_WINDOW = 30;

	private final LevelThreads threads = new LevelThreads();
	private final DiscordSender sender;
	private final Gson gson;
	private final LongSupplier clock;
	private final Supplier<DiscordStyle> style;
	/** The Progress poster name ({@link PosterNames}); null for the webhook's own. */
	private final Supplier<String> poster;

	LevelPosts(DiscordSender sender, Gson gson, LongSupplier clock, Supplier<DiscordStyle> style,
		Supplier<String> poster)
	{
		this.sender = sender;
		this.gson = gson;
		this.clock = clock;
		this.style = style;
		this.poster = poster;
	}

	/**
	 * The 'Combine level-ups' value to write once for this install, or null to leave it: an install that ran the
	 * plugin before ({@code upgrading}) and never set the value keeps the old default, so only new installs get 60
	 * (Board 2026-11-14). A value already stored, whatever it is, is the member's own and stays.
	 */
	static Integer windowToKeep(boolean upgrading, String stored)
	{
		return upgrading && stored == null ? OLD_DEFAULT_WINDOW : null;
	}

	/** One level-up into its member's message; see {@link #level(String, List, long, byte[], String)}. */
	void level(String url, GroupEvent level, long window, byte[] png, String firstJson)
	{
		level(url, Collections.singletonList(level), window, png, firstJson);
	}

	/**
	 * One moment's level-ups, all of one member, into that member's message. A new message with one card posts
	 * {@code firstJson} (the usual post for the moment, with its Easter egg and where it was) when there is one;
	 * one with more cards posts the cards. A joined one is edited by {@link #tick} in time.
	 */
	void level(String url, List<GroupEvent> levels, long window, byte[] png, String firstJson)
	{
		LevelThreads.Edit fresh = threads.level(levels, url, clock.getAsLong(), window, png);
		if (fresh != null)
		{
			post(fresh, fresh.cards.size() == 1 && firstJson != null ? firstJson : cards(fresh));
		}
	}

	/** Once a second: the edits owed now. */
	void tick()
	{
		for (LevelThreads.Edit edit : threads.due(clock.getAsLong()))
		{
			String json = DiscordWebhook.levelsEdit(gson, edit.member, edit.cards, style.get(), edit.png != null);
			sender.edit(edit.url, edit.messageId, json, edit.png, (status, id) -> refused(edit, status));
		}
	}

	/** A logout: streaks end, owed edits still go. */
	void close()
	{
		threads.close();
	}

	/** The open streaks to keep for a restart when they changed since the last call, else null ({@link LevelThreads#saved}). */
	String saved()
	{
		return threads.saved();
	}

	/** Streaks an earlier run {@link #saved}, back as open while inside {@code window} of their first post. */
	void restore(String text, long window)
	{
		threads.restore(text, clock.getAsLong(), window);
	}

	void clear()
	{
		threads.clear();
	}

	private void refused(LevelThreads.Edit edit, int status)
	{
		LevelThreads.Edit again = threads.edited(edit.streak, status);
		if (again != null)
		{
			post(again, cards(again));
		}
	}

	/** The message as a new post: every card, under the Progress poster name. */
	private String cards(LevelThreads.Edit m)
	{
		return DiscordWebhook.levels(gson, m.member, m.cards, style.get(), m.png != null, poster.get());
	}

	private void post(LevelThreads.Edit m, String json)
	{
		if (json == null)
		{
			threads.posted(m.streak, 0, null, clock.getAsLong());
			return;
		}
		sender.post(m.url, json, m.png, (status, id) -> threads.posted(m.streak, status, id, clock.getAsLong()));
	}
}
