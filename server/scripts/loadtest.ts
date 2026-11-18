// Load test and end-to-end smoke for the live path, against `wrangler dev` or a deployed Worker.
//
//   node scripts/loadtest.ts [baseUrl] [members] [minutes] [frameEverySeconds]
//   node scripts/loadtest.ts http://127.0.0.1:8799 5 1 5
//
// Opens one socket per member for a random throwaway group, sends a stats frame every few seconds
// per member (the Board's "at most one message per member per few seconds"), and checks that every
// frame reaches every other member. Prints the counts the README's budget table is built from.
// Against a deployed Worker, read the account's Durable Objects and D1 metrics before and after the
// run to answer whether DO SQLite rows written share D1's 100k/day meter (README, "Measure on deploy").

import { randomBytes } from "node:crypto";
import { request as httpRequest } from "node:http";
import { request as httpsRequest } from "node:https";
import { groupAuth, groupId } from "../src/keys.ts";

const base = process.argv[2] ?? "http://127.0.0.1:8799";
const members = Number(process.argv[3] ?? 5);
const minutes = Number(process.argv[4] ?? 1);
const every = Number(process.argv[5] ?? 5);

interface Counts {
	received: Map<string, number>;
	pongs: number;
}

async function main(): Promise<void> {
	const health = await fetch(base + "/v1/health");
	console.log("health", health.status, await health.text());
	if (!health.ok) {
		process.exit(1);
	}
	const key = randomBytes(16).toString("hex");
	const gid = await groupId(key);
	const auth = await groupAuth(key);
	const wsBase = base.replace(/^http/, "ws") + `/v1/groups/${gid}/live`;

	const names = Array.from({ length: members }, (_, i) => "Load" + i);
	const counts: Counts = { received: new Map(names.map((n) => [n, 0])), pongs: 0 };
	const sockets: WebSocket[] = [];
	for (const name of names) {
		sockets.push(await openMember(wsBase, auth, name, counts));
	}

	// A wrong auth after creation must be refused before any socket exists.
	const wrong = await upgradeStatus(base + `/v1/groups/${gid}/live`, "A".repeat(43), "Mallory");
	console.log("wrong auth ->", wrong.status);

	const rounds = Math.max(1, Math.floor((minutes * 60) / every));
	const sent = await sendRounds(sockets, rounds);
	for (const ws of sockets) {
		ws.close(1000, "done");
	}
	await new Promise((res) => setTimeout(res, 500));

	const ok = report(counts, wrong.status, rounds, sent - rounds) && wrong.status === 401;
	const upgrades = sockets.length;
	const billedDo = upgrades + Math.ceil(sent / 20);
	console.log(JSON.stringify({ members, minutes, every, workerRequests: upgrades + 2, frames: sent, billedDoRequests: billedDo }));
	console.log(ok ? "LOADTEST OK" : "LOADTEST FAILED");
	process.exit(ok ? 0 : 1);
}

/** One member's socket, counting what it receives; resolves once open. */
async function openMember(wsBase: string, auth: string, name: string, counts: Counts): Promise<WebSocket> {
	// Node's WebSocket takes headers through the undici-specific init (RuneLite's OkHttp sends them too).
	const ws = new WebSocket(wsBase, { headers: { Authorization: "Bearer " + auth, "X-Gimu-Name": name } } as never);
	ws.addEventListener("message", (e) => {
		if (e.data === "pong") {
			counts.pongs++;
			return;
		}
		const f = JSON.parse(String(e.data)) as { t: string; m?: unknown[] };
		if (f.t === "b" && f.m) {
			counts.received.set(name, (counts.received.get(name) ?? 0) + f.m.length);
		}
	});
	await new Promise<void>((resolve, reject) => {
		ws.addEventListener("open", () => resolve(), { once: true });
		ws.addEventListener("error", () => reject(new Error("socket failed for " + name)), { once: true });
	});
	return ws;
}

/** One stats frame per member per round, and one ping; returns the frames sent. */
async function sendRounds(sockets: WebSocket[], rounds: number): Promise<number> {
	let sent = 0;
	for (let r = 0; r < rounds; r++) {
		for (const ws of sockets) {
			const payload = JSON.stringify({ v: 1, k: "stats", ts: Date.now(), s: { attack: r } });
			ws.send(JSON.stringify({ t: "b", d: [payload] }));
			sent++;
		}
		sockets[0].send("ping");
		await new Promise((res) => setTimeout(res, every * 1000));
	}
	return sent;
}

/** Prints what each member got; false when a frame was lost or a ping went unanswered. */
function report(counts: Counts, wrongStatus: number, rounds: number, expectedEach: number): boolean {
	let ok = counts.pongs === rounds;
	console.log(`auto-response pongs: ${counts.pongs} of ${rounds}; wrong auth status ${wrongStatus}`);
	for (const [name, n] of counts.received) {
		// Stored state on join can add a few more; fewer means a frame was lost.
		ok &&= n >= expectedEach;
		console.log(`received ${name}: ${n} (expected >= ${expectedEach})`);
	}
	return ok;
}

// The HTTP status of an upgrade attempt (fetch refuses an Upgrade header, so this uses node:http).
function upgradeStatus(url: string, auth: string, name: string): Promise<{ status: number }> {
	return new Promise((resolve, reject) => {
		const req = (url.startsWith("https") ? httpsRequest : httpRequest)(url, {
			headers: {
				Connection: "Upgrade",
				Upgrade: "websocket",
				"Sec-WebSocket-Version": "13",
				"Sec-WebSocket-Key": randomBytes(16).toString("base64"),
				Authorization: "Bearer " + auth,
				"X-Gimu-Name": name,
			},
		});
		req.on("response", (res) => {
			res.resume();
			resolve({ status: res.statusCode ?? 0 });
		});
		req.on("upgrade", (_res, socket) => {
			socket.destroy();
			resolve({ status: 101 });
		});
		req.on("error", reject);
		req.end();
	});
}

void main();
