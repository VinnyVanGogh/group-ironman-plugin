// One group's hub: everything the group's Durable Object does, without the Workers runtime, so
// it is tested under plain Node against a real SQLite database (test/hub.test.ts). index.ts
// adapts it to the Durable Object: SQL storage, hibernatable WebSockets and alarms.
//
// Cost rules (Board 2026-11-18, free plan): nothing here keeps the object awake (no timers, no
// pending I/O), so it hibernates between messages; in-memory state is never relied on; outgoing
// messages are free, so the server pushes; every storage write is deliberate and counted.

import { fromBase64url, GROUP_AUTH } from "./keys.ts";
import {
	batchFrames,
	type Checked,
	LIVE_WRITE_MS,
	nameKey,
	parseClientFrame,
	type Relayed,
	validName,
} from "./protocol.ts";

export interface Sql {
	exec(query: string, ...params: unknown[]): { toArray(): Record<string, unknown>[] };
}

/** Per-socket state; the Durable Object keeps it with serializeAttachment (2 KB max), so it survives hibernation. */
export interface Attachment {
	who: string;
	name: string;
	/** UTC day the counters below are for. */
	day: string;
	frames: number;
	writes: number;
	/** Token bucket for the per-socket rate limit. */
	tokens: number;
	tokensAt: number;
}

export interface Conn {
	send(text: string): void;
	close(code: number, reason: string): void;
	attachment(): Attachment | null;
	attach(a: Attachment): void;
}

export interface Host {
	sql: Sql;
	conns(): Conn[];
	now(): number;
	/** Schedules the retention alarm unless one is already set. */
	ensureAlarm(at: number): void;
	setAlarm(at: number): void;
	/** Deletes every row and the alarm (ctx.storage.deleteAll). */
	wipe(): void;
	pepper: string;
}

/** Per group per UTC day. Past FRAMES the socket closes 4029 and clients fall back to Party until 00:00 UTC. */
export const QUOTA = { frames: 20_000, writes: 3_000, connects: 300 };
export const MAX_MEMBERS = 8;
export const RATE = { burst: 20, refillMs: 2_000 };
export const DAY_MS = 24 * 60 * 60 * 1000;
export const SNAPSHOT_DAYS = 30;
export const IDLE_GROUP_DAYS = 90;

export const CLOSE = { replaced: 4001, quota: 4029, deleted: 4010, bad: 4400 };

export type Admit =
	| { ok: true; created: boolean }
	| { ok: false; status: number; error: string; retryAfter?: number };

const encoder = new TextEncoder();

export async function hmacHex(pepper: string, data: Uint8Array): Promise<string> {
	const key = await crypto.subtle.importKey("raw", encoder.encode(pepper), { name: "HMAC", hash: "SHA-256" }, false,
		["sign"]);
	const mac = new Uint8Array(await crypto.subtle.sign("HMAC", key, data));
	return Array.from(mac, (b) => b.toString(16).padStart(2, "0")).join("");
}

/** Compares two strings in time that depends only on their length. */
export function sameText(a: string, b: string): boolean {
	if (a.length !== b.length) {
		return false;
	}
	let diff = 0;
	for (let i = 0; i < a.length; i++) {
		diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
	}
	return diff === 0;
}

export function utcDay(ms: number): string {
	return new Date(ms).toISOString().slice(0, 10);
}

/** Seconds until the next 00:00 UTC, when free-plan limits and these quotas reset. */
export function secondsToMidnight(ms: number): number {
	return Math.max(1, Math.ceil((DAY_MS - (ms % DAY_MS)) / 1000));
}

interface GroupRow {
	auth: string;
	active: number;
	qday: string;
	qframes: number;
	qwrites: number;
	qconns: number;
}

export class Hub {
	private readonly host: Host;

	constructor(host: Host) {
		this.host = host;
		const sql = host.sql;
		// No indexes beyond the primary keys: each index is another row written per write.
		sql.exec(`CREATE TABLE IF NOT EXISTS grp (
			id INTEGER PRIMARY KEY CHECK (id = 1), auth TEXT NOT NULL, created INTEGER NOT NULL,
			active INTEGER NOT NULL, qday TEXT NOT NULL, qframes INTEGER NOT NULL DEFAULT 0,
			qwrites INTEGER NOT NULL DEFAULT 0, qconns INTEGER NOT NULL DEFAULT 0)`);
		sql.exec(`CREATE TABLE IF NOT EXISTS members (
			who TEXT PRIMARY KEY, name TEXT NOT NULL, last_seen INTEGER NOT NULL)`);
		sql.exec(`CREATE TABLE IF NOT EXISTS snap (
			who TEXT NOT NULL, slot TEXT NOT NULL, payload TEXT NOT NULL, at INTEGER NOT NULL,
			PRIMARY KEY (who, slot))`);
	}

	private group(): GroupRow | null {
		const rows = this.host.sql.exec("SELECT auth, active, qday, qframes, qwrites, qconns FROM grp WHERE id = 1")
			.toArray();
		return rows.length === 0 ? null : (rows[0] as unknown as GroupRow);
	}

	/** Today's group totals: what closed sessions left in the group row plus every open socket's own counters. */
	usage(): { frames: number; writes: number; connects: number } {
		const g = this.group();
		const today = utcDay(this.host.now());
		const fresh = g !== null && g.qday === today;
		let frames = fresh ? g.qframes : 0;
		let writes = fresh ? g.qwrites : 0;
		for (const c of this.host.conns()) {
			const a = c.attachment();
			if (a && a.day === today) {
				frames += a.frames;
				writes += a.writes;
			}
		}
		return { frames, writes, connects: fresh ? g.qconns : 0 };
	}

	/**
	 * Before the upgrade: the group auth must match the one the group was created with (the first
	 * member to connect creates it), the name must be a RuneScape name, and the day's connects and
	 * member count must have room. 401 for a wrong auth, never "no such group".
	 */
	async admit(auth: string | null, name: string | null): Promise<Admit> {
		if (auth === null || !GROUP_AUTH.test(auth)) {
			return { ok: false, status: 401, error: "auth" };
		}
		if (!validName(name)) {
			return { ok: false, status: 400, error: "name" };
		}
		const mac = await hmacHex(this.host.pepper, fromBase64url(auth));
		// From here on nothing awaits, so two first connects cannot both create the group.
		const now = this.host.now();
		const g = this.group();
		if (g === null) {
			this.host.sql.exec("INSERT INTO grp (id, auth, created, active, qday) VALUES (1, ?, ?, ?, ?)", mac, now, now,
				utcDay(now));
			this.host.ensureAlarm(now + DAY_MS);
			return { ok: true, created: true };
		}
		if (!sameText(g.auth, mac)) {
			return { ok: false, status: 401, error: "auth" };
		}
		if (this.usage().connects >= QUOTA.connects) {
			return { ok: false, status: 429, error: "quota", retryAfter: secondsToMidnight(now) };
		}
		const who = nameKey(name);
		const known = this.host.sql.exec("SELECT who FROM members WHERE who = ? OR last_seen > ?", who,
			now - IDLE_GROUP_DAYS * DAY_MS).toArray();
		if (!known.some((r) => r.who === who) && known.length >= MAX_MEMBERS) {
			return { ok: false, status: 403, error: "full" };
		}
		return { ok: true, created: false };
	}

	/** After the socket is accepted: replaces this member's older socket, then sends who is online and the stored state. */
	open(conn: Conn, name: string): void {
		const now = this.host.now();
		const who = nameKey(name);
		for (const other of this.host.conns()) {
			const a = other.attachment();
			if (other !== conn && a && a.who === who) {
				// One live socket per member: a second client for the same name (or a forger) takes over, loudly.
				other.close(CLOSE.replaced, "replaced");
			}
		}
		const today = utcDay(now);
		conn.attach({ who, name, day: today, frames: 0, writes: 0, tokens: RATE.burst, tokensAt: now });
		this.host.sql.exec(
			"INSERT INTO members (who, name, last_seen) VALUES (?, ?, ?) ON CONFLICT (who) DO UPDATE SET name = ?, last_seen = ?",
			who, name, now, name, now);
		this.foldGroup(now, 0, 2, 1);

		const online = this.others(conn).map((c) => (c.attachment() as Attachment).name);
		conn.send(JSON.stringify({ t: "hi", n: online }));
		for (const frame of batchFrames(this.stored(who))) {
			conn.send(frame);
		}
		this.broadcast(conn, JSON.stringify({ t: "j", n: name }));
	}

	private stored(except: string): Relayed[] {
		const names = new Map<string, string>();
		for (const r of this.host.sql.exec("SELECT who, name FROM members").toArray()) {
			names.set(r.who as string, r.name as string);
		}
		const out: Relayed[] = [];
		for (const r of this.host.sql.exec("SELECT who, payload FROM snap WHERE who != ? ORDER BY who, slot", except)
			.toArray()) {
			const name = names.get(r.who as string);
			if (name !== undefined) {
				out.push({ f: name, d: r.payload as string });
			}
		}
		return out;
	}

	message(conn: Conn, text: string): void {
		const a = conn.attachment();
		if (a === null) {
			conn.close(CLOSE.bad, "no session");
			return;
		}
		const now = this.host.now();
		const today = utcDay(now);
		if (a.day !== today) {
			// A socket open across midnight: yesterday's counts go to yesterday's total, which is then reset.
			a.day = today;
			a.frames = 0;
			a.writes = 0;
		}
		a.tokens = Math.min(RATE.burst, a.tokens + (now - a.tokensAt) / RATE.refillMs);
		a.tokensAt = now;
		if (a.tokens < 1) {
			conn.attach(a);
			conn.send(JSON.stringify({ t: "e", c: "rate" }));
			return;
		}
		// usage() reads this socket's stored attachment, which does not have this frame yet.
		if (this.usage().frames >= QUOTA.frames) {
			conn.attach(a);
			conn.close(CLOSE.quota, "quota");
			return;
		}
		a.tokens -= 1;
		a.frames += 1;
		const frame = parseClientFrame(text);
		if (frame === null) {
			conn.attach(a);
			conn.send(JSON.stringify({ t: "e", c: "bad" }));
			return;
		}
		if (frame.t === "del") {
			this.deleteGroup();
			return;
		}
		if (frame.d.length > 0) {
			const relayed = frame.d.map((c) => ({ f: a.name, d: c.payload }));
			for (const other of this.others(conn)) {
				for (const out of batchFrames(relayed)) {
					other.send(out);
				}
			}
			a.writes += this.persist(a, frame.d, frame.persist, now);
		}
		conn.attach(a);
	}

	/** Keeps the latest of each stored kind for this member. Returns the rows written. */
	private persist(a: Attachment, items: Checked[], force: boolean, now: number): number {
		if (this.usage().writes >= QUOTA.writes) {
			// Still relayed live; only the copy for members who log in later is skipped until 00:00 UTC.
			return 0;
		}
		let written = 0;
		for (const c of items) {
			if (c.slot === null || !this.worthWriting(a.who, c, force, now)) {
				continue;
			}
			this.host.sql.exec(
				"INSERT INTO snap (who, slot, payload, at) VALUES (?, ?, ?, ?) ON CONFLICT (who, slot) DO UPDATE SET payload = ?, at = ?",
				a.who, c.slot, c.payload, now, c.payload, now);
			written += 1 + (c.parts === undefined ? 0 : this.dropBankPartsFrom(a.who, c.parts));
		}
		return written;
	}

	/** False for a payload identical to the stored one, or a live kind written less than LIVE_WRITE_MS ago. */
	private worthWriting(who: string, c: Checked, force: boolean, now: number): boolean {
		const old = this.host.sql.exec("SELECT payload, at FROM snap WHERE who = ? AND slot = ?", who, c.slot).toArray()[0];
		if (old === undefined) {
			return true;
		}
		if (old.payload === c.payload) {
			return false;
		}
		return !c.live || force || now - (old.at as number) >= LIVE_WRITE_MS;
	}

	/** A smaller bank than last time: parts past its end belong to the old one. Returns the rows deleted. */
	private dropBankPartsFrom(who: string, parts: number): number {
		const where = "FROM snap WHERE who = ? AND slot LIKE 'bank:%' AND CAST(substr(slot, 6) AS INTEGER) >= ?";
		const gone = this.host.sql.exec("SELECT slot " + where, who, parts).toArray().length;
		if (gone > 0) {
			this.host.sql.exec("DELETE " + where, who, parts);
		}
		return gone;
	}

	closed(conn: Conn): void {
		const a = conn.attachment();
		if (a === null) {
			return;
		}
		const now = this.host.now();
		const stillHere = this.host.conns().some((c) => c !== conn && c.attachment()?.who === a.who);
		if (this.group() === null) {
			return;
		}
		this.host.sql.exec("UPDATE members SET last_seen = ? WHERE who = ?", now, a.who);
		this.foldGroup(now, a.day === utcDay(now) ? a.frames : 0, (a.day === utcDay(now) ? a.writes : 0) + 2, 0);
		if (!stillHere) {
			this.broadcast(conn, JSON.stringify({ t: "l", n: a.name }));
		}
	}

	/** Adds to today's totals in the group row, and marks the group active. One row written. */
	private foldGroup(now: number, frames: number, writes: number, connects: number): void {
		const today = utcDay(now);
		this.host.sql.exec(
			`UPDATE grp SET active = ?,
				qframes = CASE WHEN qday = ? THEN qframes + ? ELSE ? END,
				qwrites = CASE WHEN qday = ? THEN qwrites + ? ELSE ? END,
				qconns = CASE WHEN qday = ? THEN qconns + ? ELSE ? END,
				qday = ? WHERE id = 1`,
			now, today, frames, frames, today, writes, writes, today, connects, connects, today);
	}

	/** "Delete my group's data": any member, everything, and every socket is told and closed. */
	deleteGroup(): void {
		for (const c of this.host.conns()) {
			c.close(CLOSE.deleted, "deleted");
		}
		this.host.wipe();
	}

	/** Daily retention (Board 2026-11-11): snapshots 30 days, idle groups 90 days. */
	alarm(): void {
		const now = this.host.now();
		const g = this.group();
		if (g === null) {
			return;
		}
		const open = this.host.conns().length > 0;
		if (!open && now - g.active > IDLE_GROUP_DAYS * DAY_MS) {
			this.host.wipe();
			return;
		}
		this.host.sql.exec("DELETE FROM snap WHERE at < ?", now - SNAPSHOT_DAYS * DAY_MS);
		const connected = new Set(this.host.conns().map((c) => c.attachment()?.who));
		for (const r of this.host.sql.exec("SELECT who FROM members WHERE last_seen < ?",
			now - IDLE_GROUP_DAYS * DAY_MS).toArray()) {
			if (!connected.has(r.who as string)) {
				this.host.sql.exec("DELETE FROM members WHERE who = ?", r.who);
				this.host.sql.exec("DELETE FROM snap WHERE who = ?", r.who);
			}
		}
		this.host.setAlarm(now + DAY_MS);
	}

	private others(conn: Conn): Conn[] {
		return this.host.conns().filter((c) => c !== conn && c.attachment() !== null);
	}

	private broadcast(from: Conn, text: string): void {
		for (const c of this.others(from)) {
			c.send(text);
		}
	}
}
