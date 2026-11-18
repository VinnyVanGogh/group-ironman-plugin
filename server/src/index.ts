// The Worker and the group's Durable Object. All logic is in hub.ts; this file only adapts it to
// the Workers runtime: one hibernatable WebSocket per member, one SQLite-backed object per group.

import { DurableObject } from "cloudflare:workers";
import { type Attachment, type Conn, Hub } from "./hub.ts";
import { GROUP_ID } from "./keys.ts";

export interface Env {
	GROUPS: DurableObjectNamespace<GroupHub>;
	/** HMAC key for stored group auths (wrangler secret put PEPPER). */
	PEPPER: string;
	/** Global kill switch: anything but "true" refuses new sockets with 503; clients stay on Party. */
	SYNC_ENABLED: string;
}

const LIVE = /^\/v1\/groups\/([a-z2-7]{26})\/live$/;

function json(status: number, body: unknown, headers: Record<string, string> = {}): Response {
	return new Response(JSON.stringify(body), {
		status,
		headers: { "content-type": "application/json", "cache-control": "no-store", ...headers },
	});
}

export default {
	async fetch(request: Request, env: Env): Promise<Response> {
		const url = new URL(request.url);
		if (url.pathname === "/v1/health") {
			return json(200, { ok: true, sync: env.SYNC_ENABLED === "true", protocol: 1 });
		}
		const m = LIVE.exec(url.pathname);
		if (m === null || !GROUP_ID.test(m[1])) {
			return json(404, { error: "not found" });
		}
		if (request.headers.get("Upgrade")?.toLowerCase() !== "websocket") {
			return json(426, { error: "websocket" });
		}
		if (env.SYNC_ENABLED !== "true") {
			return json(503, { error: "disabled" }, { "retry-after": "1800" });
		}
		if (!env.PEPPER) {
			return json(503, { error: "not configured" }, { "retry-after": "1800" });
		}
		// One object per group, named by the group id: the key itself never reaches the server.
		return env.GROUPS.getByName(m[1]).fetch(request);
	},
} satisfies ExportedHandler<Env>;

class Socket implements Conn {
	readonly ws: WebSocket;

	constructor(ws: WebSocket) {
		this.ws = ws;
	}

	send(text: string): void {
		try {
			this.ws.send(text);
		} catch {
			// Closing already: webSocketClose tidies up.
		}
	}

	close(code: number, reason: string): void {
		try {
			this.ws.close(code, reason);
		} catch {
			// Already closed.
		}
	}

	attachment(): Attachment | null {
		return (this.ws.deserializeAttachment() as Attachment | null) ?? null;
	}

	attach(a: Attachment): void {
		this.ws.serializeAttachment(a);
	}
}

export class GroupHub extends DurableObject<Env> {
	private readonly hub: Hub;
	private readonly sockets = new WeakMap<WebSocket, Socket>();

	constructor(ctx: DurableObjectState, env: Env) {
		super(ctx, env);
		// Answered by the runtime without waking the object (no duration charge). The plugin sends
		// protocol pings, which are free outright; this serves any client that can only send text.
		ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair("ping", "pong"));
		const storage = ctx.storage;
		this.hub = new Hub({
			sql: storage.sql,
			conns: () => ctx.getWebSockets().map((ws) => this.socket(ws)),
			now: () => Date.now(),
			ensureAlarm: (at) => {
				void storage.getAlarm().then((current) => current === null ? storage.setAlarm(at) : undefined);
			},
			setAlarm: (at) => {
				void storage.setAlarm(at);
			},
			wipe: () => {
				void storage.deleteAlarm();
				void storage.deleteAll();
			},
			pepper: env.PEPPER,
		});
	}

	private socket(ws: WebSocket): Socket {
		let s = this.sockets.get(ws);
		if (s === undefined) {
			s = new Socket(ws);
			this.sockets.set(ws, s);
		}
		return s;
	}

	async fetch(request: Request): Promise<Response> {
		const auth = request.headers.get("Authorization")?.replace(/^Bearer /, "") ?? null;
		const name = request.headers.get("X-Gimu-Name");
		const admit = await this.hub.admit(auth, name);
		if (!admit.ok) {
			const headers: Record<string, string> = { "content-type": "application/json", "cache-control": "no-store" };
			if (admit.retryAfter !== undefined) {
				headers["retry-after"] = String(admit.retryAfter);
			}
			return new Response(JSON.stringify({ error: admit.error }), { status: admit.status, headers });
		}
		const [client, server] = Object.values(new WebSocketPair());
		this.ctx.acceptWebSocket(server);
		this.hub.open(this.socket(server), name as string);
		return new Response(null, { status: 101, webSocket: client });
	}

	async webSocketMessage(ws: WebSocket, message: string | ArrayBuffer): Promise<void> {
		if (typeof message !== "string") {
			ws.close(4400, "text only");
			return;
		}
		this.hub.message(this.socket(ws), message);
	}

	async webSocketClose(ws: WebSocket): Promise<void> {
		this.hub.closed(this.socket(ws));
	}

	async webSocketError(ws: WebSocket): Promise<void> {
		// webSocketClose follows and does the bookkeeping once.
		this.socket(ws).close(1011, "error");
	}

	async alarm(): Promise<void> {
		this.hub.alarm();
	}
}
