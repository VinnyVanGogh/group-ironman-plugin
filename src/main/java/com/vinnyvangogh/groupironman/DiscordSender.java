package com.vinnyvangogh.groupironman;

import java.io.IOException;
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
 * <p>{@link #pump} is called on a timer; it takes the next post the
 * {@link DiscordQueue} allows and hands it to the {@link Transport}, which in
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
		void done(int status, String retryAfter);
	}

	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final MediaType PNG = MediaType.parse("image/png");

	private final DiscordQueue queue = new DiscordQueue();
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
		queue.offer(new DiscordQueue.Post(url, json, png));
	}

	/** Sends the next post if the queue allows one now. Any thread; called by the timer. */
	void pump()
	{
		long now = clock.getAsLong();
		DiscordQueue.Post post = queue.next(now, DiscordQueue.quiet(hour.getAsInt(), quietStart, quietEnd));
		if (post == null)
		{
			return;
		}
		transport.send(post, (status, retryAfter) ->
		{
			if (status < 200 || status >= 300)
			{
				log.debug("discord webhook post failed: {}", status == 0 ? "network error" : "HTTP " + status);
			}
			queue.done(status, DiscordQueue.retryAfterMs(retryAfter), clock.getAsLong());
		});
	}

	int queued()
	{
		return queue.size();
	}

	void clear()
	{
		queue.clear();
	}

	/** The client's transport: RuneLite's own OkHttpClient, asynchronous. */
	static Transport okHttp(OkHttpClient http)
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
			Request request = new Request.Builder().url(post.url).post(body).build();
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
						done.done(r.code(), r.header("Retry-After"));
					}
				}
			});
		};
	}
}
