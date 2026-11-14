package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Posts queued events to Discord, one at a time, off the client thread.
 *
 * <p>{@link #pump} is called on a timer; for each webhook URL it takes the next
 * post that URL's {@link DiscordQueue} allows and hands it to the {@link Transport}, which in
 * the client is RuneLite's injected {@link OkHttpClient} with
 * {@code enqueue}, so nothing blocks any thread of ours (AGENTS.md, HTTP).
 *
 * <p><b>The webhook URL is a secret</b>: anyone with it can post to the
 * channel. It is never logged, here or in the transport; a failure logs only
 * the status or the exception's class.
 */
@Slf4j
final class DiscordSender
{
	interface Transport
	{
		/** Sends the post and calls {@code done} once with the status (0 for a network failure) and Retry-After. */
		void send(DiscordQueue.Post post, Done done);
	}

	interface Done
	{
		/** {@code messageId}: the created message's id, read only when the post asked for it; else null. */
		void done(int status, String retryAfter, String messageId);

		default void done(int status, String retryAfter)
		{
			done(status, retryAfter, null);
		}
	}

	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final MediaType PNG = MediaType.parse("image/png");

	/**
	 * One queue per webhook URL: Discord rate-limits each webhook on its own, so a busy or 429'd Drops webhook
	 * never holds back the Deaths one, and each keeps its own order and per-minute cap.
	 */
	private final Map<String, DiscordQueue> queues = new ConcurrentHashMap<>();
	/** Replies whose outcome is being handed to the post's owner right now ({@link #queued}). */
	private final AtomicInteger settling = new AtomicInteger();
	private final Transport transport;
	private final LongSupplier clock;
	private final IntSupplier hour;
	private volatile int quietStart;
	private volatile int quietEnd;

	DiscordSender(Transport transport, LongSupplier clock, IntSupplier hour)
	{
		this.transport = transport;
		this.clock = clock;
		this.hour = hour;
	}

	void quietHours(int start, int end)
	{
		quietStart = start;
		quietEnd = end;
	}

	/** Queues a post. A null URL (none set, or not a Discord webhook) means no post and no network at all. */
	void post(String url, String json, byte[] png)
	{
		if (url == null || json == null)
		{
			return;
		}
		offer(new DiscordQueue.Post(url, json, png));
	}

	/** Queues a new post whose final outcome, and the created message's id, go to {@code result}. */
	void post(String url, String json, byte[] png, DiscordQueue.Result result)
	{
		if (url != null && json != null)
		{
			offer(new DiscordQueue.Post(url, json, png, null, result));
		}
	}

	/** Queues an in-place edit of {@code messageId}, sent by {@code url}'s webhook; the outcome goes to {@code result}. */
	void edit(String url, String messageId, String json, byte[] png, DiscordQueue.Result result)
	{
		if (url != null && json != null && messageId != null)
		{
			offer(new DiscordQueue.Post(url, json, png, messageId, result));
		}
	}

	/** A post a full queue pushed out is told so, outside the queue's lock: its owner may queue another. */
	private void offer(DiscordQueue.Post post)
	{
		DiscordQueue queue = queues.computeIfAbsent(post.url, u -> new DiscordQueue());
		for (DiscordQueue.Post dropped : queue.offer(post))
		{
			dropped.result.done(DiscordQueue.DROPPED, null);
		}
	}

	/** Sends each webhook's next post, where its queue allows one now. Any thread; called by the timer. */
	void pump()
	{
		long now = clock.getAsLong();
		boolean quiet = DiscordQueue.quiet(hour.getAsInt(), quietStart, quietEnd);
		for (DiscordQueue queue : queues.values())
		{
			DiscordQueue.Post post = queue.next(now, quiet);
			if (post != null)
			{
				send(queue, post);
			}
		}
	}

	private void send(DiscordQueue queue, DiscordQueue.Post post)
	{
		transport.send(post, (status, retryAfter, messageId) ->
		{
			if (status < 200 || status >= 300)
			{
				log.debug("discord webhook {} failed: {}", post.edit == null ? "post" : "edit",
					status == 0 ? "network error" : "HTTP " + status);
			}
			// Counted in queued() from leaving the queue until its owner has heard the outcome, so nobody sees an
			// empty sender while a reply's message id is still on its way to its owner.
			settling.incrementAndGet();
			try
			{
				boolean again = queue.done(status, DiscordQueue.retryAfterMs(retryAfter), clock.getAsLong());
				if (!again && post.result != null)
				{
					post.result.done(status, messageId);
				}
			}
			finally
			{
				settling.decrementAndGet();
			}
		});
	}

	/**
	 * Where a post goes. A new post that wants its message id waits for it ({@code ?wait=true}); an edit goes to
	 * {@code /messages/<id>} under the webhook (Discord "Edit Webhook Message",
	 * {@code PATCH /webhooks/{id}/{token}/messages/{message.id}}). A query the webhook already has (a
	 * {@code thread_id}) is kept, after the path, and {@code wait} joins it rather than starting a second one.
	 */
	static String target(DiscordQueue.Post post)
	{
		if (post.edit == null && post.result == null)
		{
			return post.url;
		}
		int q = post.url.indexOf('?');
		String path = q < 0 ? post.url : post.url.substring(0, q);
		String query = q < 0 ? "" : post.url.substring(q + 1);
		if (path.endsWith("/"))
		{
			path = path.substring(0, path.length() - 1);
		}
		if (post.edit != null)
		{
			path += "/messages/" + post.edit;
		}
		else
		{
			query = query.isEmpty() ? "wait=true" : query + "&wait=true";
		}
		return query.isEmpty() ? path : path + "?" + query;
	}

	/** The message id in a {@code ?wait=true} reply's message object; null when there is none. */
	static String messageId(Gson gson, String body)
	{
		try
		{
			JsonElement reply = gson.fromJson(body, JsonElement.class);
			if (reply == null || !reply.isJsonObject())
			{
				return null;
			}
			JsonObject o = reply.getAsJsonObject();
			return o.has("id") && o.get("id").isJsonPrimitive() ? o.get("id").getAsString() : null;
		}
		catch (JsonParseException | IllegalStateException e)
		{
			return null;
		}
	}

	int queued()
	{
		return queues.values().stream().mapToInt(DiscordQueue::size).sum() + settling.get();
	}

	void clear()
	{
		queues.values().forEach(DiscordQueue::clear);
	}

	/** The client's transport: RuneLite's own OkHttpClient, asynchronous. */
	static Transport okHttp(OkHttpClient http, Gson gson)
	{
		return (post, done) ->
		{
			RequestBody body;
			if (post.png == null)
			{
				body = RequestBody.create(JSON, post.json);
			}
			else
			{
				body = new MultipartBody.Builder().setType(MultipartBody.FORM)
					.addFormDataPart("payload_json", post.json)
					.addFormDataPart("files[0]", DiscordWebhook.SCREENSHOT, RequestBody.create(PNG, post.png))
					.build();
			}
			Request.Builder builder = new Request.Builder().url(target(post));
			Request request = (post.edit == null ? builder.post(body) : builder.patch(body)).build();
			boolean wantsId = post.edit == null && post.result != null;
			http.newCall(request).enqueue(new Callback()
			{
				@Override
				public void onFailure(Call call, IOException e)
				{
					// The class only: some exceptions carry the URL in their message.
					log.debug("discord webhook unreachable: {}", e.getClass().getSimpleName());
					done.done(0, null);
				}

				@Override
				public void onResponse(Call call, Response response)
				{
					try (Response r = response)
					{
						done.done(r.code(), r.header("Retry-After"), wantsId ? id(r) : null);
					}
				}

				/** The posted message's id; null when unreadable (the post still went, so it is not retried). */
				private String id(Response r)
				{
					try
					{
						if (!r.isSuccessful())
						{
							return null;
						}
						String id = r.body() == null ? null : messageId(gson, r.body().string());
						if (id == null)
						{
							// No URL: the status and content type say enough. Without the id the streak cannot be
							// edited, so the skill's next level posts anew.
							log.debug("discord webhook reply has no message id: HTTP {} {}", r.code(),
								r.header("Content-Type"));
						}
						return id;
					}
					catch (IOException e)
					{
						log.debug("discord webhook reply unreadable: {}", e.getClass().getSimpleName());
						return null;
					}
				}
			});
		};
	}
}
