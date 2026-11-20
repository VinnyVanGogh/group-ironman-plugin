package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * {@link EventThreads}' decisions sent through the {@link DiscordSender}: a member's message's first post (the
 * moment's usual post, kept by its message id), its owed edits, and the new post that replaces a message Discord no
 * longer has. Any thread.
 */
final class EventPosts extends EditedPosts
{
	private static final String VISIT = "group storage:";

	private final EventThreads threads;
	private final DiscordSender sender;
	private final Gson gson;
	private final Supplier<DiscordStyle> style;
	/** Names and prices, amounts read the Discord way: a storage card's net lines and value. */
	private final Supplier<ItemLookup> words;

	EventPosts(DiscordSender sender, Gson gson, LongSupplier clock, Supplier<DiscordStyle> style,
		Supplier<ItemLookup> words)
	{
		this(new EventThreads(gson), sender, gson, clock, style, words);
	}

	private EventPosts(EventThreads threads, DiscordSender sender, Gson gson, LongSupplier clock,
		Supplier<DiscordStyle> style, Supplier<ItemLookup> words)
	{
		super(threads, clock);
		this.threads = threads;
		this.sender = sender;
		this.gson = gson;
		this.style = style;
		this.words = words;
	}

	/**
	 * A moment as a card: its post in both styles (so a style change mid-message still has its words), with the
	 * Easter egg, never a mention or a picture. Null when its kind never groups, or it has nothing to say. Client
	 * thread, as every post's words are built there.
	 */
	static EventThreads.Moment moment(Gson gson, String url, DiscordMerge.Post post, ItemLookup lookup, String egg,
		String username, boolean pings)
	{
		String family = EventThreads.family(post.lead.type);
		String embed = DiscordWebhook.payload(gson, post, lookup, DiscordStyle.EMBED, null, false, egg, null);
		if (family == null || embed == null)
		{
			return null;
		}
		String plain = DiscordWebhook.payload(gson, post, lookup, DiscordStyle.PLAIN, null, false, egg, null);
		EventThreads.Card card = new EventThreads.Card(DiscordWebhook.firstEmbed(gson, embed),
			DiscordWebhook.content(gson, plain));
		String move = post.description.startsWith(VISIT) ? post.description.substring(VISIT.length()).trim()
			: post.description;
		return new EventThreads.Moment(family, url, username, pings, card, post.lead, post.extras, move);
	}

	/**
	 * A moment into its member's message, {@code png} its picture or null. A new message posts {@code firstJson},
	 * the moment's usual post; a joined one is edited by {@link #tick} in time.
	 */
	void add(EventThreads.Moment moment, String firstJson, byte[] png, long window)
	{
		EventThreads.Edit fresh = threads.add(moment, png, clock.getAsLong(), window);
		if (fresh != null)
		{
			post(fresh, firstJson);
		}
	}

	/** Once a second: the edits owed now. */
	void tick()
	{
		for (EventThreads.Edit edit : threads.due(clock.getAsLong()))
		{
			JsonObject o = message(edit);
			if (o == null)
			{
				// Nothing to say: the message ends as a dropped edit would, and the next moment posts anew.
				threads.edited(edit.feed, DiscordQueue.DROPPED);
				continue;
			}
			String json = DiscordWebhook.asEdit(gson, o, edit.png != null);
			sender.edit(edit.url, edit.messageId, json, edit.png, (status, id) -> refused(edit, status));
		}
	}

	private void refused(EventThreads.Edit edit, int status)
	{
		EventThreads.Edit again = threads.edited(edit.feed, status);
		if (again != null)
		{
			JsonObject o = message(again);
			post(again, o == null ? null : gson.toJson(o));
		}
	}

	private void post(EventThreads.Edit m, String json)
	{
		if (json == null)
		{
			threads.posted(m.feed, 0, null, clock.getAsLong());
			return;
		}
		sender.post(m.url, json, m.png, (status, id) -> threads.posted(m.feed, status, id, clock.getAsLong()));
	}

	/** The whole message as a new post's body: the cards, or storage's net card. Null when there is nothing. */
	private JsonObject message(EventThreads.Edit m)
	{
		if (!m.storage)
		{
			return DiscordWebhook.stack(gson, m.cards, style.get(), m.png != null, m.username,
				m.chronological);
		}
		ItemLookup lookup = words.get();
		String json = DiscordWebhook.payload(gson, net(m, lookup), lookup, style.get(), null, m.png != null, null,
			m.username);
		return json == null ? null : gson.fromJson(json, JsonObject.class);
	}

	/**
	 * Group storage over every visit in the message as one post: each item's amount summed ("took 2 x Rope" then
	 * "deposited 10 x Rope" reads "deposited 8 x Rope"), the Value of what moved overall, then the visits, newest
	 * first, and the newest visit's minimum and last-one lines.
	 */
	static DiscordMerge.Post net(EventThreads.Edit m, ItemLookup lookup)
	{
		Map<Integer, Integer> took = new TreeMap<>();
		Map<Integer, Integer> put = new TreeMap<>();
		m.net.forEach((id, n) -> (n < 0 ? took : put).put(id, (int) Math.min(Integer.MAX_VALUE, Math.abs(n))));
		StringBuilder text = new StringBuilder(VISIT);
		// No "(big ...)" marks: those are about one move, and these are sums.
		DiscordMerge.moved(text, "took", took, Long.MAX_VALUE, lookup);
		DiscordMerge.moved(text, "deposited", put, Long.MAX_VALUE, lookup);
		if (took.isEmpty() && put.isEmpty())
		{
			text.append(" nothing moved overall");
		}
		Map<Integer, Integer> all = new TreeMap<>(took);
		all.putAll(put);
		List<String[]> extras = new ArrayList<>();
		if (m.moves.size() > 1)
		{
			String visits = String.join("\n", m.moves);
			extras.add(new String[]{"Visits, newest first", visits.length() > DiscordMerge.MAX_FIELD
				? visits.substring(0, DiscordMerge.MAX_FIELD) : visits});
		}
		extras.addAll(m.extras);
		GroupEvent lead = m.lead.event();
		return new DiscordMerge.Post(lead, DiscordMerge.visitTitle(!put.isEmpty(), !took.isEmpty()), text.toString(),
			all, extras, false, put, took);
	}
}
