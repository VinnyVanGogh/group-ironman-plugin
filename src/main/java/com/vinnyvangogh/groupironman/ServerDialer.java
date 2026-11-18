package com.vinnyvangogh.groupironman;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * {@link ServerLink}'s socket over RuneLite's injected {@link OkHttpClient} ({@code newWebSocket}, AGENTS.md HTTP &
 * JSON): OkHttp's own threads, never the client thread. Protocol pings every 30 s keep the connection through
 * home routers; Cloudflare answers them without waking the group's Durable Object.
 */
final class ServerDialer implements ServerLink.Dialer
{
	private final OkHttpClient http;

	ServerDialer(OkHttpClient http)
	{
		this.http = http.newBuilder().pingInterval(30, TimeUnit.SECONDS).build();
	}

	@Override
	public ServerLink.Socket dial(String url, String auth, String name, ServerLink.Events events)
	{
		Request request = new Request.Builder()
			.url(url.replaceFirst("^wss:", "https:").replaceFirst("^ws:", "http:"))
			.header("Authorization", "Bearer " + auth)
			.header("X-Gimu-Name", name)
			.build();
		Socket socket = new Socket();
		socket.ws = http.newWebSocket(request, new WebSocketListener()
		{
			@Override
			public void onOpen(WebSocket webSocket, Response response)
			{
				events.opened(socket);
			}

			@Override
			public void onMessage(WebSocket webSocket, String text)
			{
				events.text(socket, text);
			}

			@Override
			public void onClosing(WebSocket webSocket, int code, String reason)
			{
				webSocket.close(1000, null);
				events.closed(socket, code);
			}

			@Override
			public void onFailure(WebSocket webSocket, Throwable t, Response response)
			{
				int status = response == null ? 0 : response.code();
				events.failed(socket, status, retryAfterMs(response));
			}
		});
		return socket;
	}

	/** Retry-After in seconds, as the server sends it on 429 and 503; 0 when absent. */
	static long retryAfterMs(Response response)
	{
		String value = response == null ? null : response.header("Retry-After");
		if (value == null)
		{
			return 0;
		}
		try
		{
			return Math.max(0, Math.min(Long.parseLong(value.trim()), 24 * 60 * 60)) * 1000;
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	private static final class Socket implements ServerLink.Socket
	{
		private volatile WebSocket ws;

		@Override
		public boolean send(String text)
		{
			WebSocket w = ws;
			return w != null && w.send(text);
		}

		@Override
		public void close(int code, String reason)
		{
			WebSocket w = ws;
			if (w != null)
			{
				w.close(code, reason);
			}
		}
	}
}
