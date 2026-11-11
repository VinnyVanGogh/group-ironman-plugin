package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * {@link LevelThreads}' decisions sent through the {@link DiscordSender}: a streak's first post (kept by its
 * message id), its owed edits, and the new post that replaces a message Discord no longer has. Any thread.
 */
final class LevelPosts
{
	private final LevelThreads threads = new LevelThreads();
	private final DiscordSender sender;
	private final Gson gson;
	private final LongSupplier clock;
	private final Supplier<DiscordStyle> style;
	private final Supplier<ItemLookup> lookup;

	LevelPosts(DiscordSender sender, Gson gson, LongSupplier clock, Supplier<DiscordStyle> style,
		Supplier<ItemLookup> lookup)
	{
		this.sender = sender;
		this.gson = gson;
		this.clock = clock;
		this.style = style;
		this.lookup = lookup;
	}

	/**
	 * A level-up into its skill's streak. A new streak posts {@code firstJson} (the usual level-up post) and keeps
	 * the message; a joined one is edited by {@link #tick} in time.
	 */
	void level(String url, GroupEvent level, long window, byte[] png, String firstJson)
	{
		LevelThreads.Streak fresh = threads.level(level, url, clock.getAsLong(), window, png);
		if (fresh != null)
		{
			post(fresh, url, firstJson, png);
		}
	}

	/** Once a second: the edits owed now. */
	void tick()
	{
		for (LevelThreads.Edit edit : threads.due(clock.getAsLong()))
		{
			String json = DiscordWebhook.levelsEdit(gson, edit.latest, edit.text, lookup.get(), style.get(),
				edit.png != null);
			sender.edit(edit.url, edit.messageId, json, edit.png, (status, id) -> refused(edit, status));
		}
	}

	/** A logout: streaks end, owed edits still go. */
	void close()
	{
		threads.close();
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
			post(again.streak, again.url, DiscordWebhook.levels(gson, again.latest, again.text, lookup.get(),
				style.get(), again.png != null), again.png);
		}
	}

	private void post(LevelThreads.Streak streak, String url, String json, byte[] png)
	{
		if (json == null)
		{
			threads.posted(streak, 0, null, clock.getAsLong());
			return;
		}
		sender.post(url, json, png, (status, id) -> threads.posted(streak, status, id, clock.getAsLong()));
	}
}
