import assert from "node:assert/strict";
import { test } from "node:test";
import { CLOSE, DAY_MS, Hub, MAX_MEMBERS, QUOTA, RATE, secondsToMidnight } from "../src/hub.ts";
import { groupAuth } from "../src/keys.ts";
import { LIVE_WRITE_MS } from "../src/protocol.ts";
import { FakeConn, FakeHost } from "./fakes.ts";

const KEY = "0123456789abcdef0123456789abcdef";
const OTHER_KEY = "ffffffffffffffffffffffffffffffff";

const stats = (n: number) => JSON.stringify({ v: 1, k: "stats", ts: n, s: { attack: n } });
const bank = (p: number, n: number, ts = 1) => JSON.stringify({ v: 1, k: "bank", ts, p, n, i: { "995": p + 1 } });
const event = (ts: number) => JSON.stringify({ v: 1, k: "events", e: [{ t: "drop", ts }] });

async function setup() {
	const host = new FakeHost();
	const hub = new Hub(host);
	const auth = await groupAuth(KEY);
	async function join(name: string, a = auth): Promise<FakeConn> {
		const admit = await hub.admit(a, name);
		assert.equal(admit.ok, true, JSON.stringify(admit));
		const c = new FakeConn();
		host.open.push(c);
		hub.open(c, name);
		return c;
	}
	return { host, hub, auth, join };
}

// ---- adversarial first ----

test("a wrong group auth is refused with 401 once the group exists", async () => {
	const { hub, join } = await setup();
	await join("Alice");
	const wrong = await hub.admit(await groupAuth(OTHER_KEY), "Mallory");
	assert.deepEqual(wrong, { ok: false, status: 401, error: "auth" });
});

test("malformed auth and names are refused before any crypto or storage", async () => {
	const { hub, host } = await setup();
	assert.equal((await hub.admit(null, "Alice")).ok, false);
	assert.equal((await hub.admit("short", "Alice")).ok, false);
	const auth = await groupAuth(KEY);
	for (const bad of [null, "", "thirteenchars", "<img src=x>", "a/b"]) {
		const r = await hub.admit(auth, bad);
		assert.deepEqual(r, { ok: false, status: 400, error: "name" }, String(bad));
	}
	assert.equal(host.sql.rowsWritten, 0, "nothing stored for refused connects");
});

test("the group auth is stored only as an HMAC under the pepper", async () => {
	const { host, auth, join } = await setup();
	await join("Alice");
	const rows = host.sql.db.prepare("SELECT auth FROM grp").all() as { auth: string }[];
	assert.equal(rows.length, 1);
	assert.notEqual(rows[0].auth, auth);
	assert.match(rows[0].auth, /^[0-9a-f]{64}$/);
});

test("a second socket for the same name replaces the first, which is closed 4001", async () => {
	const { join } = await setup();
	const first = await join("Alice");
	const second = await join("alice");
	assert.equal(first.closedWith, CLOSE.replaced);
	assert.equal(second.closedWith, null);
});

test("a group takes at most MAX_MEMBERS names", async () => {
	const { hub, auth, join } = await setup();
	for (let i = 0; i < MAX_MEMBERS; i++) {
		await join("Member" + i);
	}
	assert.deepEqual(await hub.admit(auth, "Extra"), { ok: false, status: 403, error: "full" });
	assert.equal((await hub.admit(auth, "Member3")).ok, true, "a known member still gets in");
});

test("malformed payloads are dropped and never relayed or stored", async () => {
	const { host, hub, join } = await setup();
	const alice = await join("Alice");
	const bob = await join("Bob");
	bob.clear();
	const bad = [
		"not json",
		JSON.stringify({ v: 0, k: "stats" }),
		JSON.stringify({ v: 1, k: "BANK" }),
		JSON.stringify({ v: 1, k: "bank", p: 3, n: 2 }),
		JSON.stringify({ v: 1, k: "bank", p: 0, n: 17 }),
		JSON.stringify([1, 2]),
		"x".repeat(6001),
		42,
	];
	hub.message(alice, JSON.stringify({ t: "b", d: bad }));
	assert.deepEqual(bob.relayed(), []);
	assert.equal((host.sql.db.prepare("SELECT COUNT(*) AS n FROM snap").get() as { n: number }).n, 0);
	hub.message(alice, "{");
	assert.deepEqual(alice.frames().at(-1), { t: "e", c: "bad" });
	hub.message(alice, JSON.stringify({ t: "b", d: Array(33).fill(stats(1)) }));
	assert.deepEqual(alice.frames().at(-1), { t: "e", c: "bad" }, "batch over 32 refused");
});

test("a frame from a socket the hub never opened closes it", async () => {
	const { hub } = await setup();
	const stray = new FakeConn();
	hub.message(stray, JSON.stringify({ t: "b", d: [stats(1)] }));
	assert.equal(stray.closedWith, CLOSE.bad);
});

test("the per-socket rate limit drops frames past the burst and refills over time", async () => {
	const { host, hub, join } = await setup();
	const alice = await join("Alice");
	const bob = await join("Bob");
	bob.clear();
	for (let i = 0; i < RATE.burst + 5; i++) {
		hub.message(alice, JSON.stringify({ t: "b", d: [event(i)] }));
	}
	assert.equal(bob.relayed().length, RATE.burst);
	assert.deepEqual(alice.frames().at(-1), { t: "e", c: "rate" });
	host.time += RATE.refillMs;
	hub.message(alice, JSON.stringify({ t: "b", d: [event(999)] }));
	assert.equal(bob.relayed().length, RATE.burst + 1);
});

test("past the group's daily frame quota the socket closes 4029; other groups are unaffected", async () => {
	const a = await setup();
	const b = await setup();
	const alice = await a.join("Alice");
	const att = alice.attachment()!;
	att.frames = QUOTA.frames;
	alice.attach(att);
	a.hub.message(alice, JSON.stringify({ t: "b", d: [stats(1)] }));
	assert.equal(alice.closedWith, CLOSE.quota);
	const carol = await b.join("Carol");
	b.hub.message(carol, JSON.stringify({ t: "b", d: [stats(1)] }));
	assert.equal(carol.closedWith, null);
});

test("past the daily connect quota new sockets get 429 with Retry-After to 00:00 UTC", async () => {
	const { host, hub, auth, join } = await setup();
	await join("Alice");
	host.sql.db.prepare("UPDATE grp SET qconns = ?").run(QUOTA.connects);
	const r = await hub.admit(auth, "Alice");
	assert.deepEqual(r, { ok: false, status: 429, error: "quota", retryAfter: secondsToMidnight(host.time) });
	host.time += DAY_MS;
	assert.equal((await hub.admit(auth, "Alice")).ok, true, "a new UTC day resets it");
});

test("past the daily write quota payloads are still relayed but no longer stored", async () => {
	const { host, hub, join } = await setup();
	const alice = await join("Alice");
	const bob = await join("Bob");
	bob.clear();
	host.sql.db.prepare("UPDATE grp SET qwrites = ?").run(QUOTA.writes);
	hub.message(alice, JSON.stringify({ t: "b", d: [bank(0, 1)] }));
	assert.equal(bob.relayed().length, 1);
	assert.equal((host.sql.db.prepare("SELECT COUNT(*) AS n FROM snap").get() as { n: number }).n, 0);
});

test("delete: any member wipes the group and every socket is closed 4010", async () => {
	const { host, hub, join } = await setup();
	const alice = await join("Alice");
	const bob = await join("Bob");
	hub.message(alice, JSON.stringify({ t: "b", d: [bank(0, 1)] }));
	hub.message(bob, JSON.stringify({ t: "del" }));
	assert.equal(host.wiped, 1);
	assert.equal(alice.closedWith, CLOSE.deleted);
	assert.equal(bob.closedWith, CLOSE.deleted);
	for (const t of ["grp", "members", "snap"]) {
		assert.equal((host.sql.db.prepare(`SELECT COUNT(*) AS n FROM ${t}`).get() as { n: number }).n, 0, t);
	}
});

test("retention: snapshots go after 30 days, the whole group after 90 idle days", async () => {
	const { host, hub, join } = await setup();
	const alice = await join("Alice");
	hub.message(alice, JSON.stringify({ t: "b", d: [bank(0, 1)] }));
	hub.closed(alice);
	alice.closedWith = 1000;
	host.time += 31 * DAY_MS;
	hub.alarm();
	assert.equal((host.sql.db.prepare("SELECT COUNT(*) AS n FROM snap").get() as { n: number }).n, 0);
	assert.equal(host.wiped, 0);
	assert.equal(host.alarmAt, host.time + DAY_MS, "next sweep tomorrow");
	host.time += 60 * DAY_MS;
	hub.alarm();
	assert.equal(host.wiped, 1);
});

// ---- happy paths ----

test("join: the first member creates the group, later members are told who is online", async () => {
	const { join } = await setup();
	const alice = await join("Alice");
	assert.deepEqual(alice.frames()[0], { t: "hi", n: [] });
	const bob = await join("Bob");
	assert.deepEqual(bob.frames()[0], { t: "hi", n: ["Alice"] });
	assert.deepEqual(alice.frames().at(-1), { t: "j", n: "Bob" }, "Alice resends her live state on this");
});

test("payloads fan out to the other members only, under the sender's name", async () => {
	const { hub, join } = await setup();
	const alice = await join("Alice");
	const bob = await join("Bob");
	const carol = await join("Carol");
	bob.clear();
	carol.clear();
	alice.clear();
	hub.message(alice, JSON.stringify({ t: "b", d: [stats(5), event(1)] }));
	assert.deepEqual(bob.relayed(), [["Alice", stats(5)], ["Alice", event(1)]]);
	assert.deepEqual(carol.relayed(), bob.relayed());
	assert.deepEqual(alice.relayed(), [], "no echo");
});

test("a member who logs in later gets the others' stored banks, settings and last stats", async () => {
	const { hub, join } = await setup();
	const alice = await join("Alice");
	const roles = JSON.stringify({ v: 1, k: "roles", l: "Alice" });
	hub.message(alice, JSON.stringify({ t: "b", d: [bank(0, 2), bank(1, 2), stats(3), roles, event(1)] }));
	hub.closed(alice);
	alice.closedWith = 1000;
	const bob = await join("Bob");
	const got = bob.relayed().map(([f, d]) => f + ":" + JSON.parse(d).k).sort();
	assert.deepEqual(got, ["Alice:bank", "Alice:bank", "Alice:roles", "Alice:stats"], "events are relayed live only");
});

test("a smaller bank drops the parts left from the bigger one", async () => {
	const { host, hub, join } = await setup();
	const alice = await join("Alice");
	hub.message(alice, JSON.stringify({ t: "b", d: [bank(0, 3), bank(1, 3), bank(2, 3)] }));
	hub.message(alice, JSON.stringify({ t: "b", d: [bank(0, 1, 2)] }));
	const slots = (host.sql.db.prepare("SELECT slot FROM snap").all() as { slot: string }[]).map((r) => r.slot);
	assert.deepEqual(slots, ["bank:0"]);
});

test("member leaving is broadcast, but not when a replacing socket is still open", async () => {
	const { hub, join } = await setup();
	const alice = await join("Alice");
	const bob = await join("Bob");
	const alice2 = await join("Alice");
	bob.clear();
	hub.closed(alice);
	assert.deepEqual(bob.frames(), [], "Alice is still here on her new socket");
	alice2.closedWith = 1000;
	hub.closed(alice2);
	assert.deepEqual(bob.frames(), [{ t: "l", n: "Alice" }]);
});

// ---- the write budget (Board 2026-11-18) ----

test("live kinds are written at most once per member and kind per 5 minutes, plus once at logout", async () => {
	const { host, hub, join } = await setup();
	const alice = await join("Alice");
	const before = host.sql.rowsWritten;
	// One stats change every 5 s for 10 minutes: 120 frames.
	for (let i = 0; i < 120; i++) {
		host.time += 5_000;
		hub.message(alice, JSON.stringify({ t: "b", d: [stats(i)] }));
	}
	const writes = host.sql.rowsWritten - before;
	assert.ok(writes <= 3, `stats written ${writes} times in 10 minutes`);
	hub.message(alice, JSON.stringify({ t: "b", d: [stats(1000)], p: 1 }));
	const stored = host.sql.db.prepare("SELECT payload FROM snap WHERE slot = 'stats'").get() as { payload: string };
	assert.equal(stored.payload, stats(1000), "the logout flush keeps the final state");
});

test("an unchanged durable payload is not written again", async () => {
	const { host, hub, join } = await setup();
	const alice = await join("Alice");
	hub.message(alice, JSON.stringify({ t: "b", d: [bank(0, 1)] }));
	const before = host.sql.rowsWritten;
	host.time += LIVE_WRITE_MS;
	hub.message(alice, JSON.stringify({ t: "b", d: [bank(0, 1)] }));
	assert.equal(host.sql.rowsWritten, before);
});

test("budget: a 5-member group playing 4 h a day stays inside the documented per-group numbers", async () => {
	const { host, hub, join } = await setup();
	const names = ["Alice", "Bob", "Carol", "Dave", "Erin"];
	const startWrites = host.sql.rowsWritten;
	let frames = 0;
	let connects = 0;
	for (const name of names) {
		// Two sessions of 2 h each.
		for (let session = 0; session < 2; session++) {
			const c = await join(name);
			connects++;
			for (let t = 0; t < 2 * 60 * 60 * 1000; t += 5_000) {
				host.time += 5_000 / names.length;
				// Worst realistic case: stats change every 5 s; carried every 30 s; a bank close every 10 min.
				const d = [stats(t)];
				if (t % 30_000 === 0) {
					d.push(JSON.stringify({ v: 1, k: "carried", ts: t, i: { "1": t } }));
				}
				if (t % 600_000 === 0) {
					d.push(bank(0, 1, t));
				}
				hub.message(c, JSON.stringify({ t: "b", d }));
				frames++;
			}
			hub.message(c, JSON.stringify({ t: "b", d: [stats(-1)], p: 1 }));
			frames++;
			hub.closed(c);
			c.closedWith = 1000;
		}
	}
	const writes = host.sql.rowsWritten - startWrites;
	const doRequests = connects + Math.ceil(frames / 20);
	// docs/BUDGET.md quotes these; keep them in step.
	assert.ok(frames <= 14_500, `frames ${frames}`);
	assert.ok(doRequests <= 750, `billed DO requests ${doRequests}`);
	assert.ok(writes <= 1_000, `rows written ${writes}`);
	assert.ok(writes < QUOTA.writes && frames < QUOTA.frames, "inside the per-group quotas");
	console.log(`budget: frames=${frames} billedDoRequests=${doRequests} rowsWritten=${writes} connects=${connects}`);
});
